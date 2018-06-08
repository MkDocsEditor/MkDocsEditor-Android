package de.markusressel.mkdocseditor.view.view

import android.content.Context
import android.support.v7.widget.AppCompatEditText
import android.util.AttributeSet
import android.widget.Toast
import androidx.core.widget.toast
import com.jakewharton.rxbinding2.widget.RxTextView
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.mkdocseditor.extensions.prettyPrint
import de.markusressel.mkdocseditor.syntaxhighlighter.SyntaxHighlighter
import de.markusressel.mkdocseditor.syntaxhighlighter.markdown.MarkdownSyntaxHighlighter
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class CodeEditText : AppCompatEditText {

    var syntaxHighlighter: SyntaxHighlighter = MarkdownSyntaxHighlighter()
    private var highlightingTimeout = 200L to TimeUnit.MILLISECONDS

    private var highlightingDisposable: Disposable? = null

    constructor(context: Context) : super(context) {
        reinit()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        reinit()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        reinit()
    }

    private fun reinit() {
        highlightingDisposable
                ?.dispose()

        highlightingDisposable = RxTextView
                .afterTextChangeEvents(this)
                .debounce(highlightingTimeout.first, highlightingTimeout.second)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    // syntax highlighting
                    refreshSyntaxHighlighting()
                }, onError = {
                    context
                            ?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
                })
    }

    /**
     * Set the timeout before new text is highlighted after the user has stopped typing
     *
     * @param timeout arbitrary value
     * @param timeUnit the timeunit to use
     */
    @Suppress("unused")
    fun setHighlightingTimeout(timeout: Long, timeUnit: TimeUnit) {
        highlightingTimeout = timeout to timeUnit
        reinit()
    }

    /**
     * Get the current timeout in milliseconds
     */
    @Suppress("unused")
    fun getHighlightingTimeout(): Long {
        return highlightingTimeout
                .second
                .toMillis(highlightingTimeout.first)
    }

    fun refreshSyntaxHighlighting() {
        syntaxHighlighter
                .highlight(text)
    }

}