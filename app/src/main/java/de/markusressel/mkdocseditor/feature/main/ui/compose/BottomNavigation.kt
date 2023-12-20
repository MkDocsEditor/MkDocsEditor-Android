package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mikepenz.iconics.compose.Image
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.main.ui.NavItem

@Composable
fun BottomBar(
    selectedNavItem: NavItem,
    navItems: List<NavItem>,
    onItemSelected: (NavItem) -> Unit,
) {
    NavigationBar(modifier = Modifier.fillMaxWidth()) {
        for (item in navItems) {
            NavigationBarItem(
                icon = {
                    Image(
                        modifier = Modifier
                            .size(32.dp),
                        asset = when (item) {
                            is NavItem.BackendSelection -> MaterialDesignIconic.Icon.gmi_cloud
                            is NavItem.FileBrowser -> MaterialDesignIconic.Icon.gmi_file
                            is NavItem.Settings -> MaterialDesignIconic.Icon.gmi_settings
                            is NavItem.About -> MaterialDesignIconic.Icon.gmi_info
                        },
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurfaceVariant),
                    )
                },
                label = {
                    Text(
                        text = stringResource(
                            id = when (item) {
                                is NavItem.BackendSelection -> R.string.bottom_navigation_backend_selection
                                is NavItem.FileBrowser -> R.string.bottom_navigation_item_files
                                is NavItem.Settings -> R.string.bottom_navigation_item_settings
                                is NavItem.About -> R.string.bottom_navigation_item_about
                            }
                        ),
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                },
                selected = selectedNavItem == item,
                onClick = {
                    onItemSelected(item)
                }
            )
        }
    }
}