package de.markusressel.mkdocseditor.view

import android.app.Activity
import android.content.Context
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import de.markusressel.mkdocseditor.R
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for easy applying of themes
 *
 * Created by Markus on 20.12.2017.
 */
@Singleton
class ThemeHelper @Inject constructor(private var context: Context) {

    private val darkThemeValue: String by lazy {
        context
                .getString(R.string.theme_dark_value)
    }

    private val lightThemeValue: String by lazy {
        context
                .getString(R.string.theme_light_value)
    }

    //    fun applyPreferencesTheme(target: Activity, theme: String) {
    //        when (theme) {
    //            darkThemeValue -> setTheme(target, R.style.PreferenceActivityThemeDark)
    //            lightThemeValue -> setTheme(target, R.style.PreferenceActivityThemeLight)
    //            else -> setTheme(target, R.style.PreferenceActivityThemeDark)
    //        }
    //    }

    /**
     * Apply a Theme to an Activity
     *
     * @param activity Activity to apply theme on
     */
    fun applyTheme(activity: Activity, theme: String) {
        when (theme) {
            lightThemeValue -> setTheme(activity, R.style.AppThemeLight)
            darkThemeValue -> setTheme(activity, R.style.AppThemeDark)
            else -> setTheme(activity, R.style.AppThemeDark)
        }
    }

    /**
     * Apply a Theme to an Activity
     *
     * @param activity Activity to apply theme on
     */
    //    fun applyDialogTheme(activity: Activity, theme: String) {
    //        when (theme) {
    //            lightThemeValue -> setTheme(activity, R.style.AppDialogThemeLight)
    //            darkThemeValue -> setTheme(activity, R.style.AppDialogThemeDark)
    //            else -> setTheme(activity, R.style.AppDialogThemeDark)
    //        }
    //    }


    /**
     * Apply a Theme to a Fragment
     *
     * @param dialogFragment Fragment to apply theme on
     */
    //    fun applyDialogTheme(dialogFragment: DialogFragment, theme: String) {
    //        when (theme) {
    //            lightThemeValue -> dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.AppDialogThemeLight)
    //            darkThemeValue -> dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.AppDialogThemeDark)
    //            else -> dialogFragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.AppDialogThemeDark)
    //        }
    //    }

    private fun setTheme(activity: Activity, @StyleRes themeRes: Int) {
        activity.apply {
            applicationContext.setTheme(themeRes)
            setTheme(themeRes)
        }
    }

    /**
     * Get Color from Theme attribute
     *
     * @param context Activity context
     * @param attr    Attribute ressource ID
     *
     * @return Color as Int
     */
    @ColorInt
    fun getThemeAttrColor(context: Context, @AttrRes attr: Int): Int {
        val typedValue = TypedValue()
        if (context.theme.resolveAttribute(attr, typedValue, true)) {
            if (typedValue.type >= TypedValue.TYPE_FIRST_INT && typedValue.type <= TypedValue.TYPE_LAST_INT) {
                return typedValue.data
            } else if (typedValue.type == TypedValue.TYPE_STRING) {
                return ContextCompat.getColor(context, typedValue.resourceId)
            }
        }

        return 0
    }

    /**
     * Apply a Theme to a BottomSheetFragment
     *
     * @param fragment Fragment to apply theme on
     */
    //    fun applyTheme(fragment: BottomSheetDialogFragment, theme: String) {
    //        when (theme) {
    //            lightThemeValue -> fragment.setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.BottomSheetThemeLight)
    //            darkThemeValue -> fragment.setStyle(BottomSheetDialogFragment.STYLE_NORMAL, R.style.BottomSheetThemeDark)
    //            else -> fragment.setStyle(DialogFragment.STYLE_NORMAL, R.style.BottomSheetThemeDark)
    //        }
    //    }

}