package de.markusressel.mkdocseditor.feature.main.ui.compose

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.main.ui.NavItem
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme


@Preview
@Composable
fun NavigationDrawerContentPreview() {
    MkDocsEditorTheme {
        NavigationDrawerContent(
            navItems = listOf(
                NavItem.FileBrowser,
                NavItem.Settings,
            ),
            selectedDestination = NavItem.FileBrowser,
            onHamburgerIconClicked = {},
        )
    }
}

@Composable
internal fun NavigationDrawerContent(
    navItems: List<NavItem>,
    selectedDestination: NavItem,
    onHamburgerIconClicked: (NavItem) -> Unit,
) {
    Column(
        modifier = Modifier
            .sizeIn(maxWidth = 256.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = stringResource(id = R.string.app_name),
                style = MaterialTheme.typography.headlineLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
            )
            Text(
                text = stringResource(id = R.string.app_description),
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                fontStyle = FontStyle.Italic,
            )
        }

        Row {
            Divider(
                modifier = Modifier
                    .weight(1f)
                    .padding(
                        top = 16.dp,
                        bottom = 8.dp
                    ),
                color = MaterialTheme.colorScheme.onSecondary,
            )
        }

        for (item in navItems) {
            DrawerNavItem(
                modifier = Modifier.padding(
                    horizontal = 8.dp,
                ),
                item = item,
                selected = item == selectedDestination,
                onClick = onHamburgerIconClicked,
            )
        }
    }
}

@Composable
private fun DrawerNavItem(
    item: NavItem,
    selected: Boolean,
    onClick: (NavItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable {
                onClick(item)
            }
            .then(modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            modifier = Modifier
                .size(32.dp),
            imageVector = when (item) {
                is NavItem.FileBrowser -> Icons.Default.Home
                is NavItem.Settings -> Icons.Default.Settings
            },
            contentDescription = "",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Text(
            modifier = Modifier
                .padding(start = 8.dp),
            text = when (item) {
                is NavItem.FileBrowser -> "Files"
                is NavItem.Settings -> "Settings"
            },
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}