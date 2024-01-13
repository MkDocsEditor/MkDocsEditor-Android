package de.markusressel.mkdocseditor.feature.filepicker.ui.compose

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun FilePickerComponent(
    mimeTypeFilter: String = "*/*",
    contract: ActivityResultContract<String, Uri?> = ActivityResultContracts.GetContent(),
    onResult: (Uri?) -> Unit,
) {
    val context = LocalContext.current
    val pickFileLauncher = rememberLauncherForActivityResult(
        contract = contract,
        onResult = onResult,
    )
    LaunchedEffect(context) {
        pickFileLauncher.launch(mimeTypeFilter)
    }
}
