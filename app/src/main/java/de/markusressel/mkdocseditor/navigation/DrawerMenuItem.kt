package de.markusressel.mkdocseditor.navigation

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import de.markusressel.mkdocseditor.view.IconHandler
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by Markus on 08.01.2018.
 */
data class DrawerMenuItem(@StringRes val title: Int, val icon: IIcon? = null, @DrawableRes val drawableRes: Int? = null, val selectable: Boolean, val navigationPage: NavigationPage) {

    val identifier: Long = Companion
            .identifier
            .getAndAdd(1)

    /**
     * Get the icon for this DrawerMenuItem
     */
    fun getIcon(iconHandler: IconHandler): Drawable {
        icon
                ?.let {
                    return iconHandler
                            .getNavigationIcon(icon)
                }

        drawableRes
                ?.let {
                    val drawable = iconHandler
                            .context
                            .getDrawable(drawableRes)
                    val color = iconHandler
                            .themeHelper
                            .getThemeAttrColor(iconHandler.context, android.R.attr.textColorPrimary)
                    drawable
                            .setColorFilter(color, PorterDuff.Mode.SRC_ATOP)

                    return drawable
                }

        return IconicsDrawable(iconHandler.context)
    }

    companion object {
        val identifier: AtomicLong = AtomicLong(1)
    }
}