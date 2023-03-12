package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
                    Icon(
                        imageVector = when (item) {
                            is NavItem.FileBrowser -> Icons.Default.Home
                            is NavItem.Settings -> Icons.Default.Settings
                            is NavItem.About -> Icons.Default.Info
                        },
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                label = {
                    Text(
                        text = stringResource(
                            id = when (item) {
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