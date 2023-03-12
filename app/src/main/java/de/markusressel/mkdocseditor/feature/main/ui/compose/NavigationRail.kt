package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.markusressel.mkdocseditor.feature.main.ui.NavItem

@Composable
fun MkDocsEditorNavigationRail(
    navItems: List<NavItem>,
    selectedNavItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
    onToggleMenu: () -> Unit,
) {
    NavigationRail(modifier = Modifier.fillMaxHeight()) {

        NavigationRailItem(
            selected = false,
            onClick = onToggleMenu,
            icon = {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        )

        for (item in navItems) {
            NavigationRailItem(
                icon = {
                    Icon(
                        imageVector = when (item) {
                            is NavItem.FileBrowser -> Icons.Default.Home
                            is NavItem.Settings -> Icons.Default.Settings
                        },
                        contentDescription = "",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                label = {
                    Text(
                        text = when (item) {
                            is NavItem.FileBrowser -> "Files"
                            is NavItem.Settings -> "Settings"
                        },
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