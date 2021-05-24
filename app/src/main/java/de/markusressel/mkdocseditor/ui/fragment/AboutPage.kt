package de.markusressel.mkdocseditor.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.mikepenz.aboutlibraries.LibsBuilder
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.ui.fragment.base.DaggerSupportFragmentBase

/**
 * The page presenting details about this app.
 *
 * The AboutLibraries library has no built in fragment class that can be referenced directly.
 * To work with the android navigation library this is necessary though. Using a
 * TabNavigationFragment as the base class for this page is a (when looking at sourc code)
 * simple workaround for this problem.
 */
@AndroidEntryPoint
class AboutPage : DaggerSupportFragmentBase() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view = inflater.inflate(R.layout.fragment_about_page, container, false)

        val themeVal = preferencesHolder.themePreference.persistedValue

//        val aboutLibTheme: ActivityStyle
//        aboutLibTheme = if (themeVal == context.getString(R.string.theme_light_value)) {
//            Libs.ActivityStyle.LIGHT_DARK_TOOLBAR
//        } else {
//            Libs.ActivityStyle.DARK
//        }

        val fragment = LibsBuilder()
            .withAboutIconShown(true)
            .withAboutVersionShown(true)
            .withAboutAppName(getString(R.string.app_name))
            .withAboutDescription(getString(R.string.app_description))
//                .withActivityStyle(aboutLibTheme)
//                .withActivityColor(
//                        Colors(ContextCompat.getColor(context, R.color.primary),
//                                ContextCompat.getColor(context, R.color.primary_dark)))
            .withActivityTitle(requireContext().getString(R.string.menu_item_about))
            .supportFragment()

        val t = childFragmentManager.beginTransaction()
        t.replace(R.id.contentLayout, fragment)
        t.commit()

        return view
    }

}