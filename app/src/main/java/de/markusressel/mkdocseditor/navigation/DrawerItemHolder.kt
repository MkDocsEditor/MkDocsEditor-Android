package de.markusressel.mkdocseditor.navigation

import com.github.ajalt.timberkt.Timber
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import de.markusressel.mkdocseditor.R

/**
 * Created by Markus on 08.01.2018.
 */
object DrawerItemHolder {

    val FileBrowser = DrawerMenuItem(
            id = R.id.fileBrowserPage,
            title = R.string.menu_item_file_browser,
            icon = MaterialDesignIconic.Icon.gmi_home,
            selectable = true)

    val Settings = DrawerMenuItem(
            id = R.id.preferencesPage,
            title = R.string.menu_item_settings,
            icon = MaterialDesignIconic.Icon.gmi_settings,
            selectable = false)

    val About = DrawerMenuItem(
            id = R.id.aboutPage,
            title = R.string.menu_item_about,
            icon = MaterialDesignIconic.Icon.gmi_info,
            selectable = false)

    fun fromId(drawerItemIdentifier: Int): DrawerMenuItem? {
        return when (drawerItemIdentifier) {
            FileBrowser.id -> FileBrowser
            Settings.id -> Settings
            About.id -> About
            else -> {
                Timber
                        .w { "Unknown menu item identifier: $drawerItemIdentifier" }
                null
            }
        }
    }

}