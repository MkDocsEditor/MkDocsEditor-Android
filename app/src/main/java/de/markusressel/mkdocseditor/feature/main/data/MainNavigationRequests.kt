package de.markusressel.mkdocseditor.feature.main.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Requests from flows outside the tabs (onboarding, connect and invite links, login) to show
 * a tab of the main screen
 */
@Singleton
class MainNavigationRequests @Inject constructor() {

    private val _showFileBrowser = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val showFileBrowser: SharedFlow<Unit> = _showFileBrowser.asSharedFlow()

    /** after signing in to a server: its documents are what the user wants to see */
    fun showFileBrowser() {
        _showFileBrowser.tryEmit(Unit)
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Accessor {
        fun mainNavigationRequests(): MainNavigationRequests
    }
}

@Composable
fun rememberMainNavigationRequests(): MainNavigationRequests {
    val context = LocalContext.current.applicationContext
    return remember(context) {
        EntryPointAccessors.fromApplication(context, MainNavigationRequests.Accessor::class.java).mainNavigationRequests()
    }
}
