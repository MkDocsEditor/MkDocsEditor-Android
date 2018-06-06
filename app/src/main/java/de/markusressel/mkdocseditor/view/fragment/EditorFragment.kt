package de.markusressel.mkdocseditor.view.fragment

import android.annotation.SuppressLint
import android.arch.lifecycle.LifecycleOwner
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.support.annotation.CallSuper
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.postDelayed
import androidx.core.widget.toast
import com.jakewharton.rxbinding2.widget.RxTextView
import com.mikepenz.material_design_iconic_typeface_library.MaterialDesignIconic
import com.otaliastudios.zoom.ZoomLayout
import com.trello.rxlifecycle2.android.lifecycle.kotlin.bindToLifecycle
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.prettyPrint
import de.markusressel.mkdocseditor.view.component.LoadingComponent
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.view.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocseditor.view.view.MarkdownEditText
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt


/**
 * Server Status fragment
 *
 * Created by Markus on 07.01.2018.
 */
class EditorFragment : DaggerSupportFragmentBase() {

    override val layoutRes: Int
        get() = R.layout.fragment_editor

    private lateinit var zoomLayout: ZoomLayout
    private lateinit var linesTextView: TextView
    private lateinit var editTextView: MarkdownEditText

    private var currentText: String by savedInstanceState("")
    private var currentLines: Int = -1

    private var currentXPosition by savedInstanceState(0F)
    private var currentYPosition by savedInstanceState(0F)
    private var currentZoom by savedInstanceState(9F)

    private val loadingComponent by lazy { LoadingComponent(this) }

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(this, optionsMenuRes = R.menu.options_menu_editor, onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
            // set refresh icon
            val refreshIcon = iconHandler
                    .getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
            menu
                    ?.findItem(R.id.refresh)
                    ?.icon = refreshIcon
        }, onOptionsMenuItemClicked = {
            when {
                it.itemId == R.id.refresh -> {
                    true
                }
                else -> false
            }
        })
    }

    override fun initComponents(context: Context) {
        super
                .initComponents(context)
        loadingComponent
        optionsMenuComponent
    }

    override fun onCreateOptionsMenu(menu: Menu?, inflater: MenuInflater?) {
        super
                .onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent
                .onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem?): Boolean {
        if (super.onOptionsItemSelected(item)) {
            return true
        }
        return optionsMenuComponent
                .onOptionsItemSelected(item)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val parent = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        return loadingComponent
                .onCreateView(inflater, parent, savedInstanceState)
    }

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super
                .onViewCreated(view, savedInstanceState)

        currentText = getString(R.string.markdown_demo_text)

        zoomLayout = view
                .findViewById(R.id.zoom_container)
        linesTextView = view
                .findViewById(R.id.lines)
        editTextView = view
                .findViewById(R.id.md_editor)

        RxTextView
                .textChanges(editTextView)
                .debounce(5, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map {
                    it to it.lines().size
                }
                .bindToLifecycle(this as LifecycleOwner)
                .subscribeBy(onNext = {
                    currentText = it
                            .first
                            .toString()

                    // Line Numbers
                    val newLineCount = it
                            .second
                    if (newLineCount != currentLines) {
                        updateLineNumbers(newLineCount)

                        // move view with the cursor
                        val changeInPixel = LINE_HIGHT_IN_PX * (newLineCount - currentLines) * -1
                        zoomLayout
                                .panBy(0F, changeInPixel, false)

                        currentLines = newLineCount
                    }

                }, onError = {
                    context
                            ?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
                })

        editTextView
                .setText(currentText, TextView.BufferType.EDITABLE)
        editTextView
                .post {
                    editTextView
                            .setSelection(0)
                }

        // zoom in
        zoomLayout
                .postDelayed(500) {
                    zoomLayout
                            .moveTo(currentZoom, currentXPosition, currentYPosition, true)

                    // remember zoom and pan
                    Observable
                            .interval(500, TimeUnit.MILLISECONDS)
                            .bindToLifecycle(zoomLayout)
                            .subscribeBy(onNext = {
                                moveScreenWithCursorIfNecessary()
                                currentXPosition = zoomLayout
                                        .panX
                                currentYPosition = zoomLayout
                                        .panY
                                currentZoom = zoomLayout
                                        .zoom
                            })
                }

        loadingComponent
                .showContent()
    }

    private fun updateLineNumbers(lines: Int) {
        val sb = StringBuilder()
        for (i in 1..lines) {
            sb
                    .append("$i:\n")
        }
        linesTextView
                .text = sb
                .toString()
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
            zoomLayout
                    .getLocalVisibleRect(zoomLayoutRect)

            val transformedX = x * zoomLayout.realZoom + zoomLayout.panX * zoomLayout.realZoom + linesTextView.width * zoomLayout.realZoom
            val transformedY = y * zoomLayout.realZoom + zoomLayout.panY * zoomLayout.realZoom

            if (!zoomLayoutRect.contains(transformedX.roundToInt(), transformedY.roundToInt())) {

                var newX = zoomLayout
                        .panX
                var newY = zoomLayout
                        .panY

                if (transformedX < zoomLayoutRect.left || transformedX > zoomLayoutRect.right) {
                    newX = -x
                }

                if (transformedY < zoomLayoutRect.top || transformedY > zoomLayoutRect.bottom) {
                    newY = -y
                }

                zoomLayout
                        .moveTo(currentZoom, newX, newY, false)
            }
        }
    }

    companion object {

        private const val LINE_HIGHT_IN_PX = 42F

        private const val KEY_ID = "KEY_ID"
        private const val KEY_CONTENT = "KEY_CONTENT"

        fun newInstance(id: String, content: String): EditorFragment {
            val fragment = EditorFragment()
            val bundle = Bundle()
            bundle
                    .putString(KEY_ID, id)
            bundle
                    .putString(KEY_CONTENT, content)

            fragment
                    .arguments = bundle

            return fragment
        }
    }
}
