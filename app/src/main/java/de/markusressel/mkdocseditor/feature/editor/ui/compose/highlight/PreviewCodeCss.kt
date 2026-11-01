package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

/**
 * CSS for inline code and code blocks in the markdown preview, using the colors of an
 * [EditorPalette]. Code blocks are highlighted with highlight.js, so the token classes are
 * the `hljs-*` classes; the web client maps them to the same colors.
 */
internal fun previewCodeCss(palette: EditorPalette): String = with(palette) {
    """
    code, pre {
      font-family: monospace;
      background: ${codeBackground.toHex()};
      border-radius: 4px;
    }
    code { color: ${code.toHex()}; padding: 0.1em 0.3em; }
    pre { padding: 0.75em 1em; overflow-x: auto; }
    pre code { color: ${text.toHex()}; padding: 0; background: transparent; }
    .hljs-keyword, .hljs-selector-tag, .hljs-literal, .hljs-section, .hljs-doctag { color: ${heading.toHex()}; }
    .hljs-string, .hljs-regexp, .hljs-addition, .hljs-template-tag { color: ${code.toHex()}; }
    .hljs-number, .hljs-bullet, .hljs-link, .hljs-symbol { color: ${image.toHex()}; }
    .hljs-title, .hljs-title.function_, .hljs-built_in { color: ${link.toHex()}; }
    .hljs-attr, .hljs-attribute, .hljs-variable, .hljs-template-variable, .hljs-property, .hljs-params { color: ${emphasis.toHex()}; }
    .hljs-type, .hljs-tag, .hljs-name, .hljs-selector-class, .hljs-selector-id, .hljs-title.class_ { color: ${html.toHex()}; }
    .hljs-comment, .hljs-quote, .hljs-deletion { color: ${quote.toHex()}; font-style: italic; }
    .hljs-meta, .hljs-meta .hljs-keyword { color: ${meta.toHex()}; }
    .hljs-emphasis { font-style: italic; }
    .hljs-strong { font-weight: bold; }
    """.trimIndent()
}
