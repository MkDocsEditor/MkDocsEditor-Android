package de.markusressel.mkdocseditor.navigation

import com.github.ajalt.timberkt.Timber
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import de.markusressel.mkdocseditor.R

/**
 * Created by Markus on 08.01.2018.
 */
object DrawerItemHolder {

    val Tree = DrawerMenuItem(title = R.string.menu_item_tree, icon = MaterialDesignIconic.Icon.gmi_home, selectable = true, navigationPage = NavigationPageHolder.Tree)

    val Settings = DrawerMenuItem(title = R.string.menu_item_settings, icon = MaterialDesignIconic.Icon.gmi_settings, selectable = false, navigationPage = NavigationPageHolder.Settings)

    val About = DrawerMenuItem(title = R.string.menu_item_about, icon = MaterialDesignIconic.Icon.gmi_info, selectable = false, navigationPage = NavigationPageHolder.About)

    fun fromId(drawerItemIdentifier: Long): DrawerMenuItem? {
        return when (drawerItemIdentifier) {
            Tree.identifier -> Tree
            Settings.identifier -> Settings
            About.identifier -> About
            else -> {
                Timber
                        .w { "Unknown menu item identifier: $drawerItemIdentifier" }
                null
            }
        }
    }

}