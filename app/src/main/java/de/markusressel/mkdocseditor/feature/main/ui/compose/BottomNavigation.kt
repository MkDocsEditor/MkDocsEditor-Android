package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.feature.main.ui.NavItem

@Composable
fun BottomBar(
    selectedNavItem: NavItem,
    navItems: List<NavItem>,
    onItemSelected: (NavItem) -> Unit,
) {
    BottomNavigation(elevation = 10.dp) {

        for (item in navItems)
            BottomNavigationItem(
                icon = {
                    Icon(imageVector = when (item) {
                        is NavItem.FileBrowser -> Icons.Default.Home
                        is NavItem.Settings -> Icons.Default.Settings
                    }, "")
                },
                label = {
                    Text(text = when (item) {
                        is NavItem.FileBrowser -> "Files"
                        is NavItem.Settings -> "Settings"
                    })
                },
                selected = selectedNavItem == item,
                onClick = {
                    onItemSelected(item)
                }
            )
    }
}