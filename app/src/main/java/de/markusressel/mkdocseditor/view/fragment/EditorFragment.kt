package de.markusressel.mkdocseditor.view.fragment

import android.annotation.SuppressLint
import android.arch.lifecycle.LifecycleOwner
import android.content.Context
import android.os.Bundle
import android.support.annotation.CallSuper
import android.view.*
import android.widget.TextView
import android.widget.Toast
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

    private var currentXPosition by savedInstanceState(0F)
    private var currentYPosition by savedInstanceState(0F)
    private var currentZoom by savedInstanceState(8F)

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

        zoomLayout = view.findViewById(R.id.zoom_container)
        linesTextView = view.findViewById(R.id.lines)
        editTextView = view.findViewById(R.id.md_editor)

        RxTextView
                .afterTextChangeEvents(editTextView)
                .debounce(50, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .map {
                    it to it.editable()!!.lines().size
                }
                .bindToLifecycle(this as LifecycleOwner)
                .subscribeBy(onNext = {
                    currentText = it.first.editable().toString()

                    // Line Numbers
                    val sb = StringBuilder()
                    for (i in 1..it.second) {
                        sb.append("$i:\n")
                    }
                    linesTextView.text = sb.toString()
                }, onError = {
                    context
                            ?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
                })

        RxTextView
                .afterTextChangeEvents(editTextView)
                .debounce(200, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this as LifecycleOwner)
                .subscribeBy(onNext = {
                    // syntax highlighting
                    editTextView
                            .refreshSyntaxHighlighting()
                }, onError = {
                    context
                            ?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
                })


        editTextView
                .setText(currentText, TextView.BufferType.EDITABLE)

        // zoom in
        zoomLayout.post {
            zoomLayout.moveTo(currentZoom, currentXPosition, currentYPosition, true)
        }

        // remember zoom and pan
        Observable.interval(1000, TimeUnit.MILLISECONDS)
                .bindToLifecycle(zoomLayout)
                .subscribeBy(onNext = {
                    currentXPosition = zoomLayout.panX
                    currentYPosition = zoomLayout.panY
                    currentZoom = zoomLayout.zoom
                })

        loadingComponent
                .showContent()
    }

    companion object {

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
