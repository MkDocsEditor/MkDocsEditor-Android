package de.markusressel.mkdocseditor.ui.fragment.base

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.mikepenz.iconics.typeface.IIcon

/**
 * Created by Markus on 13.02.2018.
 */
data class FabConfig(val left: List<Fab> = emptyList(), val right: List<Fab> = emptyList()) {

    data class Fab(
        val id: Int,
        @StringRes val description: Int,
        val icon: IIcon,
        @ColorRes val color: Int? = null,
    )

}