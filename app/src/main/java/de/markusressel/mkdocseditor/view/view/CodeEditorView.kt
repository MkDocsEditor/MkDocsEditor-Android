package de.markusressel.mkdocseditor.view.view

import android.content.Context
import android.graphics.Rect
import android.support.annotation.StringRes
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.MotionEvent
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.widget.toast
import com.github.ajalt.timberkt.Timber
import com.jakewharton.rxbinding2.widget.RxTextView
import com.otaliastudios.zoom.ZoomLayout
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.prettyPrint
import de.markusressel.mkdocseditor.syntaxhighlighter.SyntaxHighlighter
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


class CodeEditorView : ZoomLayout {

    private lateinit var contentLayout: LinearLayout
    lateinit var lineNumberView: TextView
    lateinit var editTextView: CodeEditText

    var moveWithCursorEnabled = false

    private var currentLineCount = -1

    constructor(context: Context) : super(context) {
        initialize(null)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize(attrs)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize(attrs)
    }

    private fun initialize(attrs: AttributeSet?) {
        readParameters(attrs)

        inflateViews(LayoutInflater.from(context))
        addView(contentLayout)

        setListeners()

        editTextView
                .post {
                    editTextView
                            .setSelection(0)
                }
    }


    private fun readParameters(attrs: AttributeSet?) {

    }

    private fun inflateViews(inflater: LayoutInflater) {
        contentLayout = inflater.inflate(R.layout.view_code_editor__inner_layout, null) as LinearLayout
        lineNumberView = contentLayout.findViewById(R.id.codeLinesView) as TextView
        editTextView = contentLayout.findViewById(R.id.codeEditText) as CodeEditText

        post {
            val displayMetrics = context
                    .resources
                    .displayMetrics

            contentLayout
                    .minimumHeight = displayMetrics
                    .heightPixels
            contentLayout
                    .minimumWidth = displayMetrics
                    .widthPixels
        }
    }

    private fun setListeners() {
        setOnTouchListener { view, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_MOVE -> {
                    moveWithCursorEnabled = false
                }
            }
            false
        }

        editTextView
                .setOnClickListener {
                    moveWithCursorEnabled = true
                }

        Observable
                .interval(250, TimeUnit.MILLISECONDS)
                .filter { moveWithCursorEnabled }
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    moveScreenWithCursorIfNecessary()
                }, onError = {
                    Timber
                            .e(it) { "Error moving screen with cursor" }
                })

        RxTextView
                .textChanges(editTextView)
                .debounce(50, TimeUnit.MILLISECONDS)
                .filter {
                    editTextView.lineCount != currentLineCount
                }
                .subscribeOn(Schedulers.computation())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    updateLineNumbers(editTextView.lineCount)
                }, onError = {
                    context
                            ?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
                })
    }

    private fun moveScreenWithCursorIfNecessary() {
        val pos = editTextView
                .selectionStart
        val layout = editTextView
                .layout

        if (layout != null) {
            val line = layout
                    .getLineForOffset(pos)
            val baseline = layout
                    .getLineBaseline(line)
            val ascent = layout
                    .getLineAscent(line)
            val x = layout
                    .getPrimaryHorizontal(pos)
            val y = (baseline + ascent)
                    .toFloat()

            val zoomLayoutRect = Rect()
            getLocalVisibleRect(zoomLayoutRect)

            val transformedX = x * realZoom + panX * realZoom + lineNumberView.width * realZoom
            val transformedY = y * realZoom + panY * realZoom

            if (!zoomLayoutRect.contains(transformedX.roundToInt(), transformedY.roundToInt())) {

                var newX = panX
                var newY = panY

                if (transformedX < zoomLayoutRect.left || transformedX > zoomLayoutRect.right) {
                    newX = -x
                }

                if (transformedY < zoomLayoutRect.top || transformedY > zoomLayoutRect.bottom) {
                    newY = -y
                }

                moveTo(zoom, newX, newY, false)
            }
        }
    }

    private fun updateLineNumbers(lines: Int) {
        currentLineCount = lines

        val linesToDraw = if (lines < MIN_LINES) {
            MIN_LINES
        } else {
            lines
        }

        val sb = StringBuilder()
        for (i in 1..linesToDraw) {
            sb
                    .append("$i:\n")
        }
        lineNumberView
                .text = sb
                .toString()
    }

    /**
     * Set the text in the editor
     */
    fun setText(text: CharSequence) {
        editTextView
                .setText(text, TextView.BufferType.EDITABLE)
        editTextView
                .refreshSyntaxHighlighting()
    }

    /**
     * Set the text in the editor
     */
    @Suppress("unused")
    fun setText(@StringRes text: Int) {
        editTextView
                .setText(text, TextView.BufferType.EDITABLE)
        editTextView
                .refreshSyntaxHighlighting()
    }

    /**
     * Set the syntax highlighter to use for this CodeEditor
     */
    @Suppress("unused")
    fun setSyntaxHighlighter(syntaxHighlighter: SyntaxHighlighter) {
        editTextView
                .syntaxHighlighter = syntaxHighlighter
    }

    companion object {
        const val MIN_LINES = 1
    }

}
