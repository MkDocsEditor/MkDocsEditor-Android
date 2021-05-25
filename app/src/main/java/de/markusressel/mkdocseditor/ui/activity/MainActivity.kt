package de.markusressel.mkdocseditor.ui.activity

import android.Manifest
import androidx.activity.viewModels
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.ui.activity.base.NavigationDrawerActivity

@AndroidEntryPoint
class MainActivity : NavigationDrawerActivity() {

    private val viewModel: MainViewModel by viewModels()

    override val style: Int
        get() = DEFAULT

    override fun onStart() {
        super.onStart()
        requestPermission()
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

}
