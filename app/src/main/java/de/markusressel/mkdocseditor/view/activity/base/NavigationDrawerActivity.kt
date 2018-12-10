package de.markusressel.mkdocseditor.view.activity.base

import android.os.Bundle
import android.view.View
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
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
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.FileBrowser
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.Settings
import de.markusressel.mkdocseditor.navigation.DrawerMenuItem
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

    protected val navController by lazy { Navigation.findNavController(this, R.id.navHostFragment) }
    protected lateinit var navigationDrawer: Drawer

    override fun onCreate(savedInstanceState: Bundle?) {
        super
                .onCreate(savedInstanceState)

        val menuItemList = initDrawerMenuItems()
        val accountHeader = initAccountHeader()

        val builder = DrawerBuilder()
                .withActivity(this)
                .withAccountHeader(accountHeader)
                .withDrawerItems(menuItemList)
                .withCloseOnClick(false)
                .withToolbar(toolbar)
                .withSavedInstance(savedInstanceState)

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

        val appBarConfiguration = AppBarConfiguration(
                navGraph = navController.graph,
                drawerLayout = navigationDrawer.drawerLayout)
        setupActionBarWithNavController(navController, appBarConfiguration)
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
        // TODO: replace this with navController stuff
//        navigator
//                .startActivity(this, Navigator.NavigationPages.Main, Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
//        navigator
//                .navigateTo(DrawerItemHolder.Settings)
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

        profiles.add(ProfileDrawerItem()
                .withName("Markus Ressel")
                .withEmail("mail@markusressel.de")
                .withIcon(R.mipmap.ic_launcher))

        profiles.add(ProfileDrawerItem()
                .withName("Max Mustermann")
                .withEmail("")
                .withIcon(R.mipmap.ic_launcher))

        return profiles
    }

    private fun initDrawerMenuItems(): MutableList<IDrawerItem<*, *>> {
        val menuItemList: MutableList<IDrawerItem<*, *>> = LinkedList()

        val clickListener = Drawer
                .OnDrawerItemClickListener { _, _, drawerItem ->
                    val drawerMenuItem = DrawerItemHolder
                            .fromId(drawerItem.identifier.toInt())

                    drawerMenuItem?.let {
                        navController.navigate(it.id)

                        if (drawerItem.isSelectable) {
                            // set new title
                            setTitle(drawerMenuItem.title)
                        }

                        if (!isTablet()) {
                            navigationDrawer.closeDrawer()
                        }
                        return@OnDrawerItemClickListener true
                    }

                    false
                }

        listOf(FileBrowser)
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
                .withIdentifier(menuItem.id.toLong())
                .withIcon(menuItem.getIcon(iconHandler))
                .withSelectable(menuItem.selectable)
                .withOnDrawerItemClickListener(clickListener)
    }

    private fun createSecondaryMenuItem(menuItem: DrawerMenuItem, clickListener: Drawer.OnDrawerItemClickListener): SecondaryDrawerItem {
        return SecondaryDrawerItem()
                .withName(menuItem.title)
                .withIdentifier(menuItem.id.toLong())
                .withIcon(menuItem.getIcon(iconHandler))
                .withSelectable(menuItem.selectable)
                .withOnDrawerItemClickListener(clickListener)
    }

    override fun onBackPressed() {
        if (navigationDrawer.isDrawerOpen) {
            navigationDrawer.closeDrawer()
            return
        }

        // special case for preferences
//        val currentFragment: androidx.fragment.app.Fragment? = supportFragmentManager
//                .findFragmentByTag(navigator.currentState.drawerMenuItem.navigationPage.tag)
//        if (currentFragment is PreferencesFragment && currentFragment.isVisible) {
//            if (currentFragment.onBackPressed()) {
//                return
//            }
//        }

        // special case for list
//        if (currentFragment is FileBrowserFragment && currentFragment.isVisible) {
//            if (currentFragment.onBackPressed()) {
//                return
//            }
//        }

        navController.navigateUp()

        // TODO: update drawer selection on back press
//        if (previousPage != null) {
//            navigationDrawer.setSelection(previousPage.drawerMenuItem.identifier, false)
//            return
//        }

        super.onBackPressed()
    }
}