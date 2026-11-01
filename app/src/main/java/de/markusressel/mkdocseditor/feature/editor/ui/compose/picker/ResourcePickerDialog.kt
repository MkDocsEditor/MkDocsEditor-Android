package de.markusressel.mkdocseditor.feature.editor.ui.compose.picker

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.editor.domain.PickerMarkdown
import de.markusressel.mkdocseditor.feature.filebrowser.data.ResourceKind
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiItem
import de.markusressel.mkdocseditor.feature.filebrowser.data.WikiTree
import de.markusressel.mkdocseditor.feature.filebrowser.data.resourceKind
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree.ThumbnailLoader
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.tree.WikiTreeBrowser

/**
 * Creates the content URI a camera app writes a new photo to
 */
fun interface PhotoUriFactory {
    fun create(): Uri
}

/**
 * Picks an image or file for the page: an existing one from anywhere in the wiki, a new upload
 * into a folder of choice (the page's folder by default), a new photo, or a web address. Inserts
 * `![alt](path)` for images and `[name](path)` for other files (like `ResourcePickerDialog.vue`
 * of the web client).
 *
 * @param selection the selected text, which becomes the alt text
 * @param imagesOnly whether the wiki tab starts with only images listed
 * @param uploading true while an upload runs
 * @param onUpload uploads files into a section and inserts the Markdown for them
 */
@Composable
internal fun ResourcePickerDialog(
    tree: WikiTree,
    documentId: String,
    selection: String,
    imagesOnly: Boolean,
    uploading: Boolean,
    thumbnailLoader: ThumbnailLoader?,
    photoUriFactory: PhotoUriFactory?,
    onInsert: (String) -> Unit,
    onUpload: (sectionId: String, uris: List<Uri>, altText: String) -> Unit,
    onDismiss: () -> Unit,
    startOnUpload: Boolean = false,
) {
    val document = tree.documentById(documentId) ?: return
    val fromPath = tree.pathOf(WikiItem.Document(document))
    val pageFolder = tree.parentOfDocument(documentId)?.id ?: tree.root.id

    var tab by rememberSaveable { mutableStateOf(if (startOnUpload) TAB_UPLOAD else TAB_WIKI) }
    var altText by rememberSaveable { mutableStateOf(selection) }
    var onlyImages by rememberSaveable { mutableStateOf(imagesOnly) }
    var browseSection by rememberSaveable { mutableStateOf(pageFolder) }
    var uploadSection by rememberSaveable { mutableStateOf(pageFolder) }
    var url by rememberSaveable { mutableStateOf("") }
    var pendingPhoto by rememberSaveable { mutableStateOf<Uri?>(null) }

    val chooseFiles = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) onUpload(uploadSection, uris, if (uris.size == 1) altText else "")
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingPhoto
        pendingPhoto = null
        if (saved && uri != null) onUpload(uploadSection, listOf(uri), altText)
    }

    PickerScaffold(
        title = stringResource(R.string.resource_picker_title),
        tabs = listOf(
            TAB_WIKI to stringResource(R.string.picker_tab_wiki),
            TAB_UPLOAD to stringResource(R.string.picker_tab_upload),
            TAB_WEB to stringResource(R.string.picker_tab_web),
        ),
        selectedTab = tab,
        onTabSelected = { tab = it },
        onDismiss = onDismiss,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            OutlinedTextField(
                value = altText,
                onValueChange = { altText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("resource-alt"),
                label = { Text(stringResource(R.string.resource_picker_alt)) },
                placeholder = { Text(stringResource(R.string.resource_picker_alt_hint)) },
                singleLine = true,
            )
            when (tab) {
                TAB_WIKI -> {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(R.string.resource_picker_images_only), modifier = Modifier.weight(1f))
                        Switch(
                            checked = onlyImages,
                            onCheckedChange = { onlyImages = it },
                            modifier = Modifier.testTag("resource-images-only"),
                        )
                    }
                    WikiTreeBrowser(
                        tree = tree,
                        sectionId = browseSection,
                        onSectionChange = { browseSection = it },
                        onPick = { item -> onInsert(PickerMarkdown.markdownLink(tree, fromPath, item, altText)) },
                        showDocuments = false,
                        filter = { item ->
                            !onlyImages || (item is WikiItem.Resource && item.resource.resourceKind() == ResourceKind.IMAGE)
                        },
                        searchPlaceholder = stringResource(R.string.resource_picker_search),
                        thumbnailLoader = thumbnailLoader,
                    )
                }

                TAB_UPLOAD -> {
                    val path = tree.section(uploadSection)?.let { tree.pathOf(it) } ?: ""
                    Text(
                        text = stringResource(
                            R.string.resource_picker_upload_into,
                            path.ifEmpty { stringResource(R.string.resource_picker_top_folder) },
                        ),
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .testTag("upload-folder"),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Button(
                            onClick = { chooseFiles.launch("*/*") },
                            enabled = !uploading,
                            modifier = Modifier.testTag("upload-choose"),
                        ) {
                            Icon(Icons.Outlined.Upload, contentDescription = null)
                            Text(stringResource(R.string.resource_picker_choose_files), modifier = Modifier.padding(start = 8.dp))
                        }
                        if (photoUriFactory != null) {
                            OutlinedButton(
                                onClick = {
                                    val uri = photoUriFactory.create()
                                    pendingPhoto = uri
                                    takePhoto.launch(uri)
                                },
                                enabled = !uploading,
                                modifier = Modifier.testTag("upload-photo"),
                            ) {
                                Icon(Icons.Outlined.PhotoCamera, contentDescription = null)
                                Text(stringResource(R.string.resource_picker_take_photo), modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                    if (uploading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth().testTag("upload-progress"))
                    }
                    Text(
                        text = stringResource(R.string.resource_picker_choose_folder),
                        modifier = Modifier.padding(top = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    WikiTreeBrowser(
                        tree = tree,
                        sectionId = uploadSection,
                        onSectionChange = { uploadSection = it },
                        onPick = {},
                        showDocuments = false,
                        showResources = false,
                        searchPlaceholder = stringResource(R.string.resource_picker_search_folders),
                    )
                }

                else -> {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                            .testTag("resource-url"),
                        label = { Text(stringResource(R.string.picker_address)) },
                        placeholder = { Text("https://") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.End) {
                        Button(
                            onClick = { onInsert(PickerMarkdown.webImage(url, altText)) },
                            enabled = url.isNotBlank(),
                            modifier = Modifier.testTag("resource-insert-web"),
                        ) {
                            Text(stringResource(R.string.picker_insert))
                        }
                    }
                }
            }
        }
    }
}

private const val TAB_WIKI = "wiki"
private const val TAB_UPLOAD = "upload"
private const val TAB_WEB = "web"
