package de.markusressel.mkdocseditor.feature.editor.ui.compose.highlight

/**
 * The code colors of the markdown preview as CSS variables, from an [EditorPalette]. The rules
 * that use them are in `mkdocs-renderer.css` of MkDocsEditor-Web.
 */
internal fun previewCodeVariables(palette: EditorPalette): String = with(palette) {
    """
    --md-code-background: ${codeBackground.toHex()};
    --md-code-text: ${text.toHex()};
    --md-code-comment: ${comment.toHex()};
    --md-code-keyword: ${keyword.toHex()};
    --md-code-string: ${string.toHex()};
    --md-code-number: ${number.toHex()};
    --md-code-function: ${function.toHex()};
    --md-code-property: ${property.toHex()};
    --md-code-constant: ${constant.toHex()};
    --md-code-annotation: ${annotation.toHex()};
    --md-code-tag: ${tag.toHex()};
    --md-code-attribute: ${attribute.toHex()};
    --md-code-heading: ${heading.toHex()};
    --md-code-url: ${url.toHex()};
    --md-code-inserted: ${inserted.toHex()};
    --md-code-deleted: ${deleted.toHex()};
    """.trimIndent()
}
