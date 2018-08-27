package de.markusressel.mkdocseditor.extensions.common.android

import android.content.res.Resources
import android.util.TypedValue


fun Int.dpToPx(): Int {
    return this
            .toDouble()
            .dpToPx()
}

fun Double.dpToPx(): Int {
    return (this * Resources.getSystem().displayMetrics.density)
            .toInt()
}

fun Int.spToPx(): Float {
    return this
            .toFloat()
            .spToPx()
}

fun Float.spToPx(): Float {
    return TypedValue
            .applyDimension(TypedValue.COMPLEX_UNIT_SP, this, Resources.getSystem().displayMetrics)
}

fun Double.spToPx(): Float {
    return this
            .toFloat()
            .spToPx()
}
