package de.markusressel.mkdocseditor.view.activity.base

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.View
import com.github.ajalt.timberkt.Timber
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.DividerDrawerItem
import com.mikepenz.materialdrawer.model.PrimaryDrawerItem
import com.mikepenz.materialdrawer.model.ProfileDrawerItem
import com.mikepenz.materialdrawer.model.SecondaryDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.common.android.isTablet
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.About
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.Settings
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.Tree
import de.markusressel.mkdocseditor.navigation.DrawerMenuItem
import de.markusressel.mkdocseditor.navigation.Navigator
import de.markusressel.mkdocseditor.view.fragment.FileBrowserFragment
import de.markusressel.mkdocseditor.view.fragment.preferences.PreferencesFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.view_toolbar.*
import java.util.*

/**
 * A base activity that provides the full navigation navigationDrawer implementation
 *
 * Created by Markus on 07.01.2018.
 */
abstract class NavigationDrawerActivity : DaggerSupportActivityBase() {

    override val layoutRes: Int
        get() = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super
                .onCreate(savedInstanceState)

        navigator
                .activity = this

        val menuItemList = initDrawerMenuItems()
        val accountHeader = initAccountHeader()

        val builder = DrawerBuilder()
                .withActivity(this)
                .withAccountHeader(accountHeader)
                .withDrawerItems(menuItemList)
                .withCloseOnClick(false)
                .withToolbar(toolbar)
                .withSavedInstance(savedInstanceState)

        val navigationDrawer: Drawer
        if (isTablet()) {
            navigationDrawer = builder
                    .buildView()

            drawerLayoutParent
                    .visibility = View
                    .VISIBLE
            drawerDividerView
                    .visibility = View
                    .VISIBLE

            drawerLayoutParent
                    .addView(navigationDrawer.slider, 0)
        } else {
            drawerLayoutParent
                    .visibility = View
                    .GONE
            drawerDividerView
                    .visibility = View
                    .GONE

            navigationDrawer = builder
                    .build()
        }

        navigator
                .drawer = navigationDrawer

        if (savedInstanceState == null) {
            navigator
                    .initDrawer()
        }
    }

    override fun onStart() {
        super
                .onStart()

        //        Bus
        //                .observe<ThemeChangedEvent>()
        //                .subscribe {
        //                    restartActivity()
        //                }
        //                .registerInBus(this)
    }

    private fun restartActivity() {
        navigator
                .startActivity(this, Navigator.NavigationPages.Main, Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
        navigator
                .navigateTo(DrawerItemHolder.Settings)
    }

    private fun initAccountHeader(): AccountHeader {
        val profiles: MutableList<IProfile<*>> = getProfiles()

        return AccountHeaderBuilder()
                .withActivity(this)
                //                .withProfiles(profiles)
                //                .withCloseDrawerOnProfileListClick(false)
                //                .withCurrentProfileHiddenInList(true)
                //                .withHeaderBackground()
                .withDividerBelowHeader(true)
                .withOnAccountHeaderListener { _, profile, current ->
                    Timber
                            .d { "Pressed profile: '$profile' with current: '$current'" }
                    false
                }

                .build()
    }

    private fun getProfiles(): MutableList<IProfile<*>> {
        val profiles: MutableList<IProfile<*>> = LinkedList()

        // TODO: implement different servers as profiles

        profiles
                .add(ProfileDrawerItem().withName("Markus Ressel").withEmail("mail@markusressel.de").withIcon(R.mipmap.ic_launcher))

        profiles
                .add(ProfileDrawerItem().withName("Iris Haderer").withEmail("").withIcon(R.mipmap.ic_launcher))

        return profiles
    }

    private fun initDrawerMenuItems(): MutableList<IDrawerItem<*, *>> {
        val menuItemList: MutableList<IDrawerItem<*, *>> = LinkedList()

        val clickListener = Drawer
                .OnDrawerItemClickListener { _, _, drawerItem ->

                    if (drawerItem.identifier == navigator.currentState.drawerMenuItem.identifier) {
                        Timber
                                .d { "Closing navigationDrawer because the clicked item (${drawerItem.identifier}) is the currently active page" }
                        if (!isTablet()) {

                            navigator
                                    .drawer
                                    .closeDrawer()
                        }
                        return@OnDrawerItemClickListener true
                    }

                    val drawerMenuItem = DrawerItemHolder
                            .fromId(drawerItem.identifier)

                    drawerMenuItem
                            ?.navigationPage
                            ?.let {
                                if (it.fragment != null) {
                                    navigator
                                            .navigateTo(drawerMenuItem)
                                } else {
                                    navigator
                                            .startActivity(this, it)
                                }

                                if (drawerItem.isSelectable) {
                                    // set new title
                                    setTitle(drawerMenuItem.title)
                                }

                                if (!isTablet()) {
                                    navigator
                                            .drawer
                                            .closeDrawer()
                                }
                                return@OnDrawerItemClickListener true
                            }

                    false
                }



        listOf(Tree)
                .forEach {
                    menuItemList
                            .add(createPrimaryMenuItem(it, clickListener))
                }

        menuItemList
                .add(DividerDrawerItem())

        menuItemList
                .add(createPrimaryMenuItem(Settings, clickListener))

        menuItemList
                .add(createSecondaryMenuItem(About, clickListener))

        return menuItemList
    }

    private fun createPrimaryMenuItem(menuItem: DrawerMenuItem, clickListener: Drawer.OnDrawerItemClickListener): PrimaryDrawerItem {
        return PrimaryDrawerItem()
                .withName(menuItem.title)
                .withIdentifier(menuItem.identifier)
                .withIcon(menuItem.getIcon(iconHandler))
                .withSelectable(menuItem.selectable)
                .withOnDrawerItemClickListener(clickListener)
    }

    private fun createSecondaryMenuItem(menuItem: DrawerMenuItem, clickListener: Drawer.OnDrawerItemClickListener): SecondaryDrawerItem {
        return SecondaryDrawerItem()
                .withName(menuItem.title)
                .withIdentifier(menuItem.identifier)
                .withIcon(menuItem.getIcon(iconHandler))
                .withSelectable(menuItem.selectable)
                .withOnDrawerItemClickListener(clickListener)
    }

    override fun onBackPressed() {
        if (navigator.drawer.isDrawerOpen) {
            navigator
                    .drawer
                    .closeDrawer()
            return
        }

        // special case for preferences
        val currentFragment: androidx.fragment.app.Fragment? = supportFragmentManager
                .findFragmentByTag(navigator.currentState.drawerMenuItem.navigationPage.tag)
        if (currentFragment is PreferencesFragment && currentFragment.isVisible) {
            if (currentFragment.onBackPressed()) {
                return
            }
        }

        // special case for list
        if (currentFragment is FileBrowserFragment && currentFragment.isVisible) {
            if (currentFragment.onBackPressed()) {
                return
            }
        }

        val previousPage = navigator
                .navigateBack()
        if (previousPage != null) {

            navigator
                    .drawer
                    .setSelection(previousPage.drawerMenuItem.identifier, false)
            return
        }

        super
                .onBackPressed()
    }
}