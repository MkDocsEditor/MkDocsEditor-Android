package de.markusressel.mkdocseditor.ui.activity

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.Navigator
import cafe.adriel.voyager.transitions.SlideTransition
import de.markusressel.mkdocseditor.auth.link.AuthLinkParser
import de.markusressel.mkdocseditor.auth.link.ConnectLinkParser
import de.markusressel.mkdocseditor.auth.link.JoinLinkParser
import de.markusressel.mkdocseditor.feature.join.ui.compose.AccessRequestsScreen
import de.markusressel.mkdocseditor.feature.join.ui.compose.JoinLinkScreen
import de.markusressel.mkdocseditor.feature.auth.onboarding.ui.compose.ConnectLinkScreen
import de.markusressel.mkdocseditor.feature.auth.common.ui.compose.LocalAuthFlowExit
import de.markusressel.mkdocseditor.feature.auth.link.ui.PendingAuthLinkViewModel
import de.markusressel.mkdocseditor.feature.auth.link.ui.compose.AuthLinkScreen
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.flowWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.feature.main.ui.DevicePosture
import de.markusressel.mkdocseditor.feature.main.ui.compose.MainScreen
import de.markusressel.mkdocseditor.feature.main.ui.isBookPosture
import de.markusressel.mkdocseditor.feature.main.ui.isSeparating
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The screens of a link opened from outside the app, on top of everything else
 */
@Composable
private fun AuthLinkFlow(link: String, onExit: () -> Unit) {
    BackHandler(onBack = onExit)
    CompositionLocalProvider(LocalAuthFlowExit provides onExit) {
        Surface(modifier = Modifier.fillMaxSize()) {
            key(link) {
                val first = when {
                    link.startsWith(ACCESS_REQUESTS_OF_BACKEND) -> AccessRequestsScreen(link.removePrefix(ACCESS_REQUESTS_OF_BACKEND).toLong())
                    ConnectLinkParser.isConnectLink(link) -> ConnectLinkScreen(link)
                    JoinLinkParser.parse(link) != null -> JoinLinkScreen(link)
                    else -> AuthLinkScreen(linkText = link)
                }
                Navigator(first) { navigator ->
                    SlideTransition(navigator)
                }
            }
        }
    }
}

/** not a link: what [PendingAuthLinkViewModel] keeps for the list of access requests of a server */
private const val ACCESS_REQUESTS_OF_BACKEND = "access-requests:"

@AndroidEntryPoint
class MainActivityCompose : AppCompatActivity() {

    private val pendingAuthLink: PendingAuthLinkViewModel by viewModels()

    @OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            handleAuthLink(intent)
        }

        /**
         * Flow of [DevicePosture] that emits every time there's a change in the windowLayoutInfo
         */
        val devicePostureFlow = WindowInfoTracker.getOrCreate(this).windowLayoutInfo(this)
            .flowWithLifecycle(this.lifecycle)
            .map { layoutInfo ->
                val foldingFeature =
                    layoutInfo.displayFeatures
                        .filterIsInstance<FoldingFeature>()
                        .firstOrNull()
                when {
                    isBookPosture(foldingFeature) ->
                        DevicePosture.BookPosture(foldingFeature.bounds)

                    isSeparating(foldingFeature) ->
                        DevicePosture.Separating(foldingFeature.bounds, foldingFeature.orientation)

                    else -> DevicePosture.NormalPosture
                }
            }
            .stateIn(
                scope = lifecycleScope,
                started = SharingStarted.Eagerly,
                initialValue = DevicePosture.NormalPosture
            )

        setContent {
            MkDocsEditorTheme {
                val windowSize = calculateWindowSizeClass(this)
                val devicePosture by devicePostureFlow.collectAsState()
                MainScreen(
                    windowSize = windowSize.widthSizeClass,
                    devicePosture = devicePosture,
                )

                val authLink by pendingAuthLink.link.collectAsState()
                authLink?.let { link ->
                    AuthLinkFlow(link = link, onExit = pendingAuthLink::close)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthLink(intent)
    }

    private fun handleAuthLink(intent: Intent?) {
        val adminOf = intent?.getLongExtra(EXTRA_ACCESS_REQUESTS_OF_BACKEND, -1L) ?: -1L
        if (adminOf >= 0) {
            intent?.removeExtra(EXTRA_ACCESS_REQUESTS_OF_BACKEND)
            pendingAuthLink.open("$ACCESS_REQUESTS_OF_BACKEND$adminOf")
            return
        }
        if (intent?.action != Intent.ACTION_VIEW) return
        val data = intent.dataString ?: return
        if (AuthLinkParser.parse(data) != null || ConnectLinkParser.isConnectLink(data) || JoinLinkParser.parse(data) != null) {
            pendingAuthLink.open(data)
        }
        // the link token must not stay in the intent (recents, recreation)
        intent.data = null
    }

    private fun requestPermission() {
        if (BuildConfig.DEBUG) {
            Dexter.withContext(this)
                .withPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                .withListener(object : PermissionListener {
                    override fun onPermissionGranted(response: PermissionGrantedResponse?) {}
                    override fun onPermissionRationaleShouldBeShown(
                        permission: PermissionRequest?,
                        token: PermissionToken?
                    ) {
                    }

                    override fun onPermissionDenied(response: PermissionDeniedResponse?) {}
                }).check()
        }
    }

    companion object {
        /** from the notification about new access requests: the server whose list to show */
        const val EXTRA_ACCESS_REQUESTS_OF_BACKEND = "access_requests_of_backend"
    }
}
