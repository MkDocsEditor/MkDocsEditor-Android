package de.markusressel.mkdocseditor.ui.navigation

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import com.mikepenz.iconics.IconicsDrawable
import com.mikepenz.iconics.typeface.IIcon
import de.markusressel.mkdocseditor.ui.IconHandler
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by Markus on 08.01.2018.
 */
data class DrawerMenuItem(
    @IdRes val id: Int,
    @StringRes val title: Int,
    val icon: IIcon? = null,
    @DrawableRes val drawableRes: Int? = null,
    val selectable: Boolean
) {

    /**
     * Get the icon for this DrawerMenuItem
     */
    fun getIcon(iconHandler: IconHandler): Drawable {
        icon?.let {
            return iconHandler.getNavigationIcon(icon)
        }

        drawableRes?.let {
            val drawable = ContextCompat.getDrawable(iconHandler.context, drawableRes)!!
            val color = iconHandler.themeHelper.getThemeAttrColor(
                iconHandler.context,
                android.R.attr.textColorPrimary
            )
            drawable.setColorFilter(color, PorterDuff.Mode.SRC_ATOP)
            return drawable
        }

        return IconicsDrawable(iconHandler.context)
    }

    companion object {
        val identifier: AtomicLong = AtomicLong(1)
    }
}