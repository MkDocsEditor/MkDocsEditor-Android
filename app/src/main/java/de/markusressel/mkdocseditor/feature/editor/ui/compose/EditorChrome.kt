package de.markusressel.mkdocseditor.feature.editor.ui.compose

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp

/**
 * Decides when the top app bar of the editor makes room for the text: it hides when the user
 * types with the soft keyboard open, and comes back when the user scrolls up or the keyboard
 * closes.
 */
internal class EditorChromeState(
    /** a screen so short (a phone in landscape) that the bar goes as soon as the keyboard opens */
    var shortScreen: Boolean = false,
) {

    private var hiddenForTyping by mutableStateOf(false)

    var keyboardVisible: Boolean = false
        set(value) {
            if (value && !field && shortScreen) hiddenForTyping = true
            field = value
            if (!value && hiddenForTyping) hiddenForTyping = false
        }

    val topBarVisible: Boolean
        get() = !hiddenForTyping

    /** the user changed the text */
    fun onTextEdited() {
        if (keyboardVisible) hiddenForTyping = true
    }

    /** the finger moved down over the text, which scrolls it up */
    fun onDraggedDown() {
        hiddenForTyping = false
    }
}

/**
 * Reports a downward drag without consuming it, so the editor below still scrolls
 */
internal fun Modifier.observeDragDown(onDraggedDown: () -> Unit): Modifier = pointerInput(Unit) {
    val threshold = 24.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var distance = 0f
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.firstOrNull { it.id == down.id }
            if (change == null || !change.pressed) break
            distance += change.positionChange().y
            if (distance > threshold) {
                onDraggedDown()
                break
            }
        }
    }
}
