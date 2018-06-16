package de.markusressel.mkdocseditor.navigation

import de.markusressel.mkdocseditor.view.activity.MainActivity
import de.markusressel.mkdocseditor.view.fragment.DocumentsFragment
import de.markusressel.mkdocseditor.view.fragment.preferences.PreferencesFragment

/**
 * Created by Markus on 08.01.2018.
 */
object NavigationPageHolder {

    val Main: NavigationPage = NavigationPage(activityClass = MainActivity::class.java)

    val Tree: NavigationPage = NavigationPage(fragment = ::DocumentsFragment, tag = "DocumentsFragment")

    val Settings = NavigationPage(fragment = ::PreferencesFragment, tag = "PreferencesFragment")
    //    val Settings = NavigationPage(activityClass = PreferenceOverviewActivity::class.java)

    val About = NavigationPage()

}