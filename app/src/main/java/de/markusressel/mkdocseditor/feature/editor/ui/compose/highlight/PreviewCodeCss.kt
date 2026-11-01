package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

/**
 * CSS for inline code and code blocks in the markdown preview, using the colors of an
 * [EditorPalette]. Code blocks are highlighted with highlight.js; the classes map to the
 * palette like in the web client (`src/markdown/MarkdownPreview.vue` of MkDocsEditor-Web).
 */
internal fun previewCodeCss(palette: EditorPalette): String = with(palette) {
    """
    code {
      font-family: ui-monospace, "JetBrains Mono", Menlo, Consolas, monospace;
      font-size: 0.88em;
      color: ${text.toHex()};
      background: ${codeBackground.toHex()};
      padding: 0.1em 0.35em;
      border-radius: 4px;
    }
    pre {
      background: ${codeBackground.toHex()};
      padding: 12px 14px;
      border-radius: 6px;
      overflow-x: auto;
      line-height: 1.5;
    }
    pre code { background: none; padding: 0; color: ${text.toHex()}; }
    .hljs-comment, .hljs-quote { color: ${comment.toHex()}; font-style: italic; }
    .hljs-keyword, .hljs-selector-tag, .hljs-literal, .hljs-symbol, .hljs-bullet, .hljs-char.escape_, .hljs-doctag { color: ${keyword.toHex()}; }
    .hljs-string, .hljs-regexp, .hljs-template-tag { color: ${string.toHex()}; }
    .hljs-number { color: ${number.toHex()}; }
    .hljs-title.function_, .hljs-function .hljs-title { color: ${function.toHex()}; }
    .hljs-attr, .hljs-variable, .hljs-template-variable, .hljs-property, .hljs-selector-attr, .hljs-selector-class, .hljs-selector-id { color: ${property.toHex()}; }
    .hljs-built_in, .hljs-type, .hljs-variable.language_, .hljs-variable.constant_ { color: ${constant.toHex()}; }
    .hljs-meta, .hljs-meta .hljs-keyword { color: ${annotation.toHex()}; }
    .hljs-meta .hljs-string { color: ${string.toHex()}; }
    .hljs-tag, .hljs-name, .hljs-selector-pseudo { color: ${tag.toHex()}; }
    .hljs-tag .hljs-attr { color: ${attribute.toHex()}; }
    .hljs-tag .hljs-string { color: ${string.toHex()}; }
    .hljs-section { color: ${heading.toHex()}; font-weight: bold; }
    .hljs-link { color: ${url.toHex()}; text-decoration: underline; }
    .hljs-addition { color: ${inserted.toHex()}; }
    .hljs-deletion { color: ${deleted.toHex()}; }
    .hljs-emphasis { font-style: italic; }
    .hljs-strong { font-weight: bold; }
    """.trimIndent()
}
