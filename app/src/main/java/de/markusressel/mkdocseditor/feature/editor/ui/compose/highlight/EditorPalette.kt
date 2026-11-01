package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

/**
 * The colors of the markdown editor and of code in the preview.
 *
 * The values are our own, inspired by the IntelliJ themes: Darcula and the New UI "Dark" theme
 * for [Dark], "IntelliJ Light" for [Light]. The web client uses the same palette, so both
 * clients look alike. Every text color has a contrast of at least 4.5:1 (WCAG AA) against
 * [background] and [codeBackground], see `EditorPaletteTest`.
 *
 * Code tokens in the preview reuse the markdown colors:
 * keyword = [heading], string = [code], number = [image], function = [link],
 * attribute and variable = [emphasis], type and tag = [html], comment = [quote].
 */
data class EditorPalette(
    /** the background of the editor and the preview */
    val background: Color,
    /** the background of inline code and code blocks */
    val codeBackground: Color,
    /** plain text */
    val text: Color,
    /** headings, list markers, horizontal rules; keywords in code */
    val heading: Color,
    /** bold and italic text; attributes and variables in code */
    val emphasis: Color,
    /** links; functions in code */
    val link: Color,
    /** images; numbers in code */
    val image: Color,
    /** inline code and code blocks; strings in code */
    val code: Color,
    /** block quotes and strikethrough; comments in code */
    val quote: Color,
    /** HTML tags; types and tags in code */
    val html: Color,
    /** admonitions, task list boxes, front matter; meta data in code */
    val meta: Color,
    /** the line numbers */
    val lineNumber: Color,
    /** the background of the own text selection */
    val selection: Color,
    /** the cursor */
    val cursor: Color,
) {
    /** all colors that are used for text, by name */
    val textColors: Map<String, Color>
        get() = mapOf(
            "text" to text,
            "heading" to heading,
            "emphasis" to emphasis,
            "link" to link,
            "image" to image,
            "code" to code,
            "quote" to quote,
            "html" to html,
            "meta" to meta,
        )

    companion object {
        val Dark = EditorPalette(
            background = Color(0xFF1A1C18),
            codeBackground = Color(0xFF24271F),
            text = Color(0xFFCBCEC5),
            heading = Color(0xFFE8A06E),
            emphasis = Color(0xFFD59AD8),
            link = Color(0xFF6CB2F7),
            image = Color(0xFF4EC3C9),
            code = Color(0xFF8BC890),
            quote = Color(0xFF9AA094),
            html = Color(0xFFE4C070),
            meta = Color(0xFFB9BF5E),
            lineNumber = Color(0xFF878D81),
            selection = Color(0xFF2E4560),
            cursor = Color(0xFFE3E3DC),
        )

        val Light = EditorPalette(
            background = Color(0xFFFDFDF5),
            codeBackground = Color(0xFFEEF0E5),
            text = Color(0xFF1A1C18),
            heading = Color(0xFFA3450C),
            emphasis = Color(0xFF86218C),
            link = Color(0xFF1A55C4),
            image = Color(0xFF00727C),
            code = Color(0xFF276F2E),
            quote = Color(0xFF5B6155),
            html = Color(0xFF7A5A00),
            meta = Color(0xFF5E6600),
            lineNumber = Color(0xFF666B60),
            selection = Color(0xFFCCDDF5),
            cursor = Color(0xFF1A1C18),
        )

        fun forDarkTheme(dark: Boolean) = if (dark) Dark else Light
    }
}

/**
 * The relative luminance of this color as defined by WCAG 2
 */
internal fun Color.wcagLuminance(): Double {
    fun channel(value: Float): Double {
        val c = value.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(red) + 0.7152 * channel(green) + 0.0722 * channel(blue)
}

/**
 * The WCAG 2 contrast ratio between two opaque colors, from 1 to 21
 */
internal fun contrastRatio(a: Color, b: Color): Double {
    val la = a.wcagLuminance()
    val lb = b.wcagLuminance()
    return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
}

/**
 * This color as "#RRGGBB"
 */
internal fun Color.toHex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)
