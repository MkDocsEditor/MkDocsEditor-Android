package de.markusressel.mkdocseditor.view.view

import android.content.Context
import android.support.v7.widget.AppCompatEditText
import android.util.AttributeSet
import android.widget.Toast
import androidx.core.widget.toast
import com.jakewharton.rxbinding2.widget.RxTextView
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.mkdocseditor.extensions.prettyPrint
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit

class MarkdownEditText : AppCompatEditText {

    private val syntaxHighlighter = MarkdownSyntaxHighlighter()

    private var highlightingTimeout = 200L to TimeUnit.MILLISECONDS

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        RxTextView
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

    fun setHighlightingTimeout(timeout: Long, timeUnit: TimeUnit) {
        highlightingTimeout = timeout to timeUnit
    }

    fun refreshSyntaxHighlighting() {
        syntaxHighlighter
                .highlight(text)
    }

}