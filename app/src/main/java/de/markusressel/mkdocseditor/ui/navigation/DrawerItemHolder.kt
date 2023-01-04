package de.markusressel.mkdocseditor.ui.navigation

import com.github.ajalt.timberkt.Timber
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import de.markusressel.mkdocseditor.R

/**
 * Created by Markus on 08.01.2018.
 */
object DrawerItemHolder {

    val FileBrowser = DrawerMenuItem(
        id = R.id.fileBrowserPage,
        title = R.string.menu_item_file_browser,
        icon = MaterialDesignIconic.Icon.gmi_home,
        selectable = true
    )

    val About = DrawerMenuItem(
        id = R.id.aboutPage,
        title = R.string.menu_item_about,
        icon = MaterialDesignIconic.Icon.gmi_info,
        selectable = true
    )

    val OfflineMode = DrawerMenuItem(
        id = R.id.none,
        title = R.string.menu_item_offline_mode,
        icon = MaterialDesignIconic.Icon.gmi_airplanemode_active,
        selectable = false
    )

    fun fromId(drawerItemIdentifier: Int): DrawerMenuItem? {
        return when (drawerItemIdentifier) {
            FileBrowser.id -> FileBrowser
            About.id -> About
            else -> {
                Timber.w { "Unknown menu item identifier: $drawerItemIdentifier" }
                null
            }
        }
    }

}