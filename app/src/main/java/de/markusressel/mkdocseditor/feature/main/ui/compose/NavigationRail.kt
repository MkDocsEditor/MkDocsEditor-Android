package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.main.ui.NavItem

@Composable
fun MkDocsEditorNavigationRail(
    navItems: List<NavItem>,
    selectedNavItem: NavItem,
    onItemSelected: (NavItem) -> Unit,
) {
    NavigationRail(
        modifier = Modifier.fillMaxHeight(),
    ) {
        val primaryItems by remember { derivedStateOf { navItems.filterIsInstance<NavItem.Primary>() } }
        val secondaryItems by remember { derivedStateOf { navItems.filterIsInstance<NavItem.Secondary>() } }

        AppIconImage()

        Column(
            modifier = Modifier
                .padding(vertical = 8.dp)
                .weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            PrimaryNavRailItems(
                items = primaryItems,
                selectedNavItem = selectedNavItem,
                onItemSelected = onItemSelected
            )
        }

        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            SecondaryNavRailItems(
                items = secondaryItems,
                selectedNavItem = selectedNavItem,
                onItemSelected = onItemSelected
            )
        }
    }
}


@Composable
private fun ColumnScope.PrimaryNavRailItems(
    items: List<NavItem.Primary>,
    selectedNavItem: NavItem,
    onItemSelected: (NavItem.Primary) -> Unit
) {
    for (item in items) {
        val icon = when (item) {
            is NavItem.FileBrowser -> Icons.Default.Home
            is NavItem.Settings -> Icons.Default.Settings
        }
        val label = stringResource(
            id = when (item) {
                is NavItem.FileBrowser -> R.string.bottom_navigation_item_files
                is NavItem.Settings -> R.string.bottom_navigation_item_settings
            }
        )

        NavigationRailItem(
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            label = {
                Text(
                    text = label,
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

@Composable
private fun ColumnScope.SecondaryNavRailItems(
    items: List<NavItem.Secondary>,
    selectedNavItem: NavItem,
    onItemSelected: (NavItem.Secondary) -> Unit
) {
    for (item in items) {
        val icon = when (item) {
            is NavItem.About -> Icons.Default.Info
        }
        val label = stringResource(
            id = when (item) {
                is NavItem.About -> R.string.bottom_navigation_item_about
            }
        )

        NavigationRailItem(
            icon = {
                Icon(
                    imageVector = icon,
                    contentDescription = "",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            label = {
                Text(
                    text = label,
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
