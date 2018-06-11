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

    constructor(context: Context) : super(context) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        initialize()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        initialize()
    }

    private fun initialize() {
        readParameters()

        createViews()
        addView(contentLayout)

        setListeners()

        editTextView
                .post {
                    editTextView
                            .setSelection(0)
                }
    }


    private fun readParameters() {

    }

    private fun createViews() {
        contentLayout = LinearLayout(context)
        contentLayout
                .orientation = LinearLayout
                .HORIZONTAL
        contentLayout
                .layoutParams = LinearLayout
                .LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        val inflater = LayoutInflater
                .from(context)

        lineNumberView = inflater.inflate(R.layout.view_code_editor__line_numbers, null) as TextView
        editTextView = inflater.inflate(R.layout.view_code_editor__edit_text, null) as CodeEditText

        contentLayout
                .addView(lineNumberView)
        contentLayout
                .addView(editTextView)
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
                    Timber.e(it) { "Error moving screen with cursor" }
                })

        RxTextView
                .textChanges(editTextView)
                .debounce(250, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
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
        val sb = StringBuilder()
        for (i in 1..lines) {
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
        updateLineNumbers(editTextView.lineCount)
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
        updateLineNumbers(editTextView.lineCount)
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

}
