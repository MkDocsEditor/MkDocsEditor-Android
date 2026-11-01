package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.pow

/**
 * The colors of the markdown editor and of code in the preview.
 *
 * Inspired by the IntelliJ themes ("IntelliJ Light", and the dark New UI / Darcula look), with
 * own values tuned for WCAG AA. The web client uses the same palette (`src/plugins/codeColors.ts`
 * of MkDocsEditor-Web), so both clients look alike; keep them in sync. Every text color has a
 * contrast of at least 4.5:1 against [background] and [codeBackground], see `EditorPaletteTest`.
 */
data class EditorPalette(
    /** the background of the editor and the preview (the app's surface) */
    val background: Color,
    /** the background of inline code and code blocks */
    val codeBackground: Color,
    /** the background of the own text selection */
    val selection: Color,
    /** the line numbers */
    val lineNumber: Color,

    /** plain text */
    val text: Color,
    /** comments, quotes, horizontal rules and fence lines */
    val comment: Color,
    /** keywords; list and quote markers, task boxes */
    val keyword: Color,
    /** strings; inline code, attribute values */
    val string: Color,
    val number: Color,
    /** function and method names */
    val function: Color,
    /** properties, keys and variables */
    val property: Color,
    /** built-in types and constants */
    val constant: Color,
    /** annotations, decorators, admonition markers, front matter */
    val annotation: Color,
    /** HTML and XML tag names */
    val tag: Color,
    /** HTML and XML attribute names */
    val attribute: Color,
    /** markdown headings */
    val heading: Color,
    /** markdown link text */
    val link: Color,
    /** URLs and link targets */
    val url: Color,
    /** diff: added and removed lines */
    val inserted: Color,
    val deleted: Color,
) {
    /** all colors that are used for text, by name */
    val textColors: Map<String, Color>
        get() = mapOf(
            "lineNumber" to lineNumber,
            "text" to text,
            "comment" to comment,
            "keyword" to keyword,
            "string" to string,
            "number" to number,
            "function" to function,
            "property" to property,
            "constant" to constant,
            "annotation" to annotation,
            "tag" to tag,
            "attribute" to attribute,
            "heading" to heading,
            "link" to link,
            "url" to url,
            "inserted" to inserted,
            "deleted" to deleted,
        )

    companion object {
        val Light = EditorPalette(
            background = Color(0xFFFDFDF5),
            codeBackground = Color(0xFFF2F3EA),
            selection = Color(0xFFC8DEF9),
            lineNumber = Color(0xFF6B6F66),
            text = Color(0xFF1A1C18),
            comment = Color(0xFF676B62),
            keyword = Color(0xFF2340A8),
            string = Color(0xFF1A7A2D),
            number = Color(0xFF1259C3),
            function = Color(0xFF00657D),
            property = Color(0xFF8A1F96),
            constant = Color(0xFF7B3AB0),
            annotation = Color(0xFF776500),
            tag = Color(0xFF2340A8),
            attribute = Color(0xFF1F5BC8),
            heading = Color(0xFF2340A8),
            link = Color(0xFF1D4FC4),
            url = Color(0xFF1A7A2D),
            inserted = Color(0xFF1A7A2D),
            deleted = Color(0xFFB3261E),
        )

        val Dark = EditorPalette(
            background = Color(0xFF1A1C18),
            codeBackground = Color(0xFF252722),
            selection = Color(0xFF263B57),
            lineNumber = Color(0xFF8A8F85),
            text = Color(0xFFE3E3DC),
            comment = Color(0xFF8F948C),
            keyword = Color(0xFFD4895A),
            string = Color(0xFF79B46F),
            number = Color(0xFF33B0C0),
            function = Color(0xFF5AA9F0),
            property = Color(0xFFC98BC6),
            constant = Color(0xFFB39DDB),
            annotation = Color(0xFFBDB76A),
            tag = Color(0xFFD6B974),
            attribute = Color(0xFFB0BCC8),
            heading = Color(0xFFE8A36A),
            link = Color(0xFF6FB2F2),
            url = Color(0xFF79B46F),
            inserted = Color(0xFF79B46F),
            deleted = Color(0xFFEF7F7A),
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
