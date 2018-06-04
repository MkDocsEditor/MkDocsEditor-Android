package de.markusressel.mkdocseditor.view.view

import android.content.Context
import android.support.v7.widget.AppCompatEditText
import android.util.AttributeSet

class MarkdownEditText : AppCompatEditText {

    private val syntaxHighlighter = MarkdownSyntaxHighlighter()

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun refreshSyntaxHighlighting() {
        syntaxHighlighter
                .highlight(text)
    }

}