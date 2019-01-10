package de.markusressel.mkdocseditor.view.activity.base

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.AppCompatImageView
import androidx.core.view.get
import androidx.navigation.Navigation
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import com.github.ajalt.timberkt.Timber
import com.mikepenz.materialdrawer.AccountHeader
import com.mikepenz.materialdrawer.AccountHeaderBuilder
import com.mikepenz.materialdrawer.Drawer
import com.mikepenz.materialdrawer.DrawerBuilder
import com.mikepenz.materialdrawer.model.*
import com.mikepenz.materialdrawer.model.interfaces.IDrawerItem
import com.mikepenz.materialdrawer.model.interfaces.IProfile
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.event.ThemeChangedEvent
import de.markusressel.mkdocseditor.extensions.common.android.isTablet
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.About
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.FileBrowser
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.OfflineMode
import de.markusressel.mkdocseditor.navigation.DrawerItemHolder.Settings
import de.markusressel.mkdocseditor.navigation.DrawerMenuItem
import de.markusressel.mkdocseditor.view.fragment.FileBrowserFragment
import de.markusressel.mkdocseditor.view.fragment.preferences.PreferencesFragment
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.view_toolbar.*
import java.util.*
import javax.inject.Inject

/**
 * A base activity that provides the full navigation navigationDrawer implementation
 *
 * Created by Markus on 07.01.2018.
 */
abstract class NavigationDrawerActivity : DaggerSupportActivityBase() {

    override val layoutRes: Int
        get() = R.layout.activity_main

    protected val navController by lazy { Navigation.findNavController(this, R.id.navHostFragment) }

    private lateinit var navigationDrawer: Drawer

    @Inject
    protected lateinit var offlineModeManager: OfflineModeManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        navController.addOnDestinationChangedListener { controller, destination, arguments ->
            // update selected drawer item accordingly
            DrawerItemHolder.fromId(destination.id)?.let {
                navigationDrawer.setSelection(it.id.toLong(), false)
            }
        }
    }

    override fun onStart() {
        super
                .onStart()

        Bus.observe<ThemeChangedEvent>()
                .subscribe {
                    recreate()
                }
                .registerInBus(this)
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
                        if (it.id == R.id.none) {
                            return@let
                        }

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

        menuItemList.addAll(
                arrayOf(
                        createPrimaryMenuItem(FileBrowser, clickListener),
                        DividerDrawerItem(),
                        createPrimaryMenuItem(Settings, clickListener),
                        createSecondaryMenuItem(About, clickListener),
                        DividerDrawerItem(),
                        createOfflineModeMenuItem(OfflineMode, clickListener,
                                defaultValue = offlineModeManager.isEnabled())))

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

    private fun createOfflineModeMenuItem(menuItem: DrawerMenuItem, clickListener: Drawer.OnDrawerItemClickListener, defaultValue: Boolean = false): IDrawerItem<*, *> {
        return SwitchDrawerItem()
                .withName(menuItem.title)
                .withIdentifier(menuItem.id.toLong())
                .withIcon(menuItem.getIcon(iconHandler))
                .withSelectable(menuItem.selectable)
                .withOnDrawerItemClickListener(clickListener)
                .withOnCheckedChangeListener { drawerItem, buttonView, isChecked ->
                    offlineModeManager.setEnabled(isChecked)

                    val parentView = buttonView.parent as ViewGroup
                    val iconView = parentView[0] as AppCompatImageView

                    iconView.setColorFilter(offlineModeManager.getColor())
                }.withChecked(defaultValue)
                .withIconColor(offlineModeManager.getColor())
                .withPostOnBindViewListener { drawerItem, view ->
                    val iconView = (view as ViewGroup)[0] as AppCompatImageView
                    iconView.setColorFilter(offlineModeManager.getColor())
                }
    }

    override fun onBackPressed() {
        if (navigationDrawer.isDrawerOpen) {
            navigationDrawer.closeDrawer()
            return
        }

        // pass onBack event to fragments
        val navHost = supportFragmentManager.findFragmentById(R.id.navHostFragment)
        val currentlyVisibleFragment = navHost?.childFragmentManager?.primaryNavigationFragment
        when (currentlyVisibleFragment) {
            is PreferencesFragment -> {
                if (currentlyVisibleFragment.onBackPressed()) {
                    return
                }
            }
            is FileBrowserFragment -> {
                if (currentlyVisibleFragment.onBackPressed()) {
                    return
                }
            }
        }

        if (navController.navigateUp()) {
            return
        }

        super.onBackPressed()
    }
}