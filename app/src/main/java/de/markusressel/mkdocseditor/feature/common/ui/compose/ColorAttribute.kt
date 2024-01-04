package de.markusressel.mkdocseditor.feature.common.ui.compose

import android.util.TypedValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource


@Composable
@ReadOnlyComposable
fun colorAttribute(attrColor: Int) = colorResource(TypedValue().apply {
    LocalContext.current.theme.resolveAttribute(
        attrColor,
        this,
        true
    )
}.resourceId)