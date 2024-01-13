package de.markusressel.mkdocseditor.ui.fragment.base

import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import com.mikepenz.iconics.typeface.IIcon

/**
 * Created by Markus on 13.02.2018.
 */
data class FabConfig<FabId>(val left: List<Fab<FabId>> = emptyList(), val right: List<Fab<FabId>> = emptyList()) {

    data class Fab<FabId>(
        val id: FabId,
        @StringRes val description: Int,
        val icon: IIcon,
        @ColorRes val color: Int? = null,
    )

}