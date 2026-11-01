package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EditorPaletteTest {

    private val palettes = mapOf("dark" to EditorPalette.Dark, "light" to EditorPalette.Light)

    @Test
    fun `contrast ratio matches the WCAG reference values`() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, contrastRatio(Color.White, Color.White), 0.01)
        // #767676 on white is the classic 4.54:1 example
        assertEquals(4.54, contrastRatio(Color(0xFF767676), Color.White), 0.01)
    }

    @Test
    fun `every text color meets WCAG AA on the editor and the code background`() {
        val report = StringBuilder()
        palettes.forEach { (name, palette) ->
            palette.textColors.forEach { (token, color) ->
                val onBackground = contrastRatio(color, palette.background)
                val onCode = contrastRatio(color, palette.codeBackground)
                report.appendLine(
                    "| $name | $token | ${color.toHex()} | ${"%.2f".format(onBackground)} | ${"%.2f".format(onCode)} |"
                )
                assertTrue(onBackground >= 4.5, "$name $token on background: $onBackground")
                assertTrue(onCode >= 4.5, "$name $token on code background: $onCode")
            }
        }
        println(report)
    }

    @Test
    fun `line numbers and selected text meet WCAG AA`() {
        palettes.forEach { (name, palette) ->
            assertTrue(contrastRatio(palette.lineNumber, palette.background) >= 4.5, "$name line numbers")
            assertTrue(contrastRatio(palette.text, palette.selection) >= 4.5, "$name selected text")
            assertTrue(contrastRatio(palette.cursor, palette.background) >= 4.5, "$name cursor")
        }
    }

    @Test
    fun `toHex formats rgb`() {
        assertEquals("#1A1C18", Color(0xFF1A1C18).toHex())
    }

    @Test
    fun `preview css uses the palette`() {
        val css = previewCodeCss(EditorPalette.Dark)
        assertTrue(css.contains("background: #24271F"))
        assertTrue(css.contains(".hljs-keyword"))
        assertTrue(css.contains("#E8A06E"))
    }
}
