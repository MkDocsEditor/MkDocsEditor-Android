package de.markusressel.mkdocseditor.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import com.mikepenz.iconics.IconicsColor
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.mikepenz.iconics.utils.color
import com.mikepenz.iconics.utils.paddingDp
import com.mikepenz.iconics.utils.sizeDp
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Helper class for access to often used icons
 *
 *
 * Created by Markus on 13.12.2015.
 */
@Singleton
class IconHandler @Inject constructor() {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var themeHelper: ThemeHelper

    /**
     * Get an icon suitable for the options menu
     *
     * @param icon the icon resource
     *
     * @return the icon
     */
    fun getOptionsMenuIcon(icon: IIcon): IconicsDrawable {
        //        val color = themeHelper
        //                .getThemeAttrColor(context, android.R.attr.textColorPrimary)
        val color = ContextCompat.getColor(context, android.R.color.white)

        val padding = when (icon) {
            MaterialDesignIconic.Icon.gmi_plus -> 5
            MaterialDesignIconic.Icon.gmi_reorder,
            MaterialDesignIconic.Icon.gmi_refresh -> 2
            else -> 0
        }

        return getIcon(icon, color, 24, padding)
    }

    /**
     * @return an icon for a preference
     */
    fun getPreferenceIcon(icon: IIcon): Drawable {
        val color = Color.WHITE
//        themeHelper.getThemeAttrColor(
//            context,
//            R.attr.kute_preferences__setting__default_icon_color
//        )
        return getIcon(icon, color = color, sizeDp = 36)
    }

    fun getIcon(
        icon: IIcon,
        @ColorInt color: Int,
        sizeDp: Int,
        paddingDp: Int = 0
    ) = IconicsDrawable(context, icon).apply {
        this.sizeDp = sizeDp
        this.paddingDp = paddingDp
        this.color = IconicsColor.colorInt(color)
    }

}
