package de.markusressel.mkdocseditor.view.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.os.Bundle
import android.view.*
import androidx.annotation.CallSuper
import androidx.databinding.DataBindingUtil
import androidx.databinding.ViewDataBinding
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import androidx.lifecycle.observe
import com.github.ajalt.timberkt.Timber
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.snackbar.Snackbar.LENGTH_SHORT
import com.mikepenz.iconics.typeface.library.materialdesigniconic.MaterialDesignIconic
import com.otaliastudios.zoom.ZoomEngine
import com.trello.rxlifecycle2.LifecycleProvider
import com.trello.rxlifecycle2.android.FragmentEvent
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.commons.android.core.runOnUiThread
import de.markusressel.commons.android.material.snack
import de.markusressel.kodeeditor.library.view.CodeEditorLayout
import de.markusressel.kodeeditor.library.view.SelectionChangedListener
import de.markusressel.kodehighlighter.language.markdown.MarkdownRuleBook
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.data.persistence.entity.DocumentEntity_
import de.markusressel.mkdocseditor.databinding.FragmentEditorBinding
import de.markusressel.mkdocseditor.network.ChromeCustomTabManager
import de.markusressel.mkdocseditor.network.NetworkManager
import de.markusressel.mkdocseditor.view.activity.base.OfflineModeManager
import de.markusressel.mkdocseditor.view.component.LoadingComponent
import de.markusressel.mkdocseditor.view.component.OptionsMenuComponent
import de.markusressel.mkdocseditor.view.fragment.base.DaggerSupportFragmentBase
import de.markusressel.mkdocseditor.view.viewmodel.CodeEditorViewModel
import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.Disposable
import io.reactivex.rxkotlin.subscribeBy
import io.reactivex.schedulers.Schedulers
import java.util.*
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Created by Markus on 07.01.2018.
 */
@AndroidEntryPoint
class CodeEditorFragment : DaggerSupportFragmentBase(), SelectionChangedListener {

    override val layoutRes: Int
        get() = R.layout.fragment_editor

    @Inject
    lateinit var chromeCustomTabManager: ChromeCustomTabManager

    @Inject
    lateinit var networkManager: NetworkManager

    private lateinit var codeEditorLayout: CodeEditorLayout

    private val safeArgs: CodeEditorFragmentArgs by lazy {
        CodeEditorFragmentArgs.fromBundle(requireArguments())
    }

    private val documentId: String
        get() {
            return safeArgs.documentId
        }

    private var noConnectionSnackbar: Snackbar? = null

    private val viewModel: CodeEditorViewModel by lazy { ViewModelProviders.of(this).get(CodeEditorViewModel::class.java) }

    @Inject
    lateinit var offlineModeManager: OfflineModeManager

    private val loadingComponent by lazy { LoadingComponent(this) }

    private val optionsMenuComponent: OptionsMenuComponent by lazy {
        OptionsMenuComponent(this, optionsMenuRes = R.menu.options_menu_editor, onCreateOptionsMenu = { menu: Menu?, menuInflater: MenuInflater? ->
            // set refresh icon
            menu?.findItem(R.id.refresh)?.apply {
                val refreshIcon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_refresh)
                icon = refreshIcon
            }

            // set "edit" icon
            menu?.findItem(R.id.edit)?.apply {
                icon = if (viewModel.preferencesHolder.codeEditorAlwaysOpenEditModePreference.persistedValue) {
                    iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_eye)
                } else {
                    iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_edit)
                }
            }

            // set open in browser icon
            menu?.findItem(R.id.open_in_browser)?.apply {
                val openInBrowserIcon = iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_open_in_browser)
                icon = openInBrowserIcon
                if (viewModel.preferencesHolder.webUriPreference.persistedValue.isBlank()) {
                    isVisible = false
                    isEnabled = false
                }

            }
        }, onOptionsMenuItemClicked = {
            val webBaseUri = viewModel.preferencesHolder.webUriPreference.persistedValue

            when (it.itemId) {
                R.id.open_in_browser -> {
                    if (webBaseUri.isBlank()) {
                        return@OptionsMenuComponent false
                    }

                    val documentEntity = documentPersistenceManager
                            .standardOperation()
                            .query {
                                equal(DocumentEntity_.id, documentId)
                            }.findUnique()

                    documentEntity?.let { document ->
                        val host = viewModel.preferencesHolder.webUriPreference.persistedValue

                        val pagePath = when {
                            document.url == "index/" -> ""
                            else -> document.url // this value is already url encoded
                        }

                        val url = "$host/$pagePath"
                        chromeCustomTabManager.openChromeCustomTab(url)
                    }
                    true
                }
                R.id.edit -> {
                    codeEditorLayout.editable = !codeEditorLayout.editable
                    it.icon = if (codeEditorLayout.editable) {
                        iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_eye)
                    } else {
                        iconHandler.getOptionsMenuIcon(MaterialDesignIconic.Icon.gmi_edit)
                    }
                    true
                }
                else -> false
            }
        })
    }

    override fun initComponents(context: Context) {
        super.initComponents(context)
        loadingComponent
        optionsMenuComponent
    }

    override fun createViewDataBinding(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): ViewDataBinding? {
        val binding: FragmentEditorBinding = DataBindingUtil.inflate(layoutInflater, layoutRes, container, false)
        viewModel.documentId.value = documentId

        binding.let {
            it.lifecycleOwner = this
            it.viewModel = viewModel
        }

        return binding
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        super.onCreateOptionsMenu(menu, inflater)
        optionsMenuComponent.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item) || optionsMenuComponent.onOptionsItemSelected(item)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel.editable.observe(this) {
            codeEditorLayout.editable = it
        }

        viewModel.connected.observe(this) { connected ->
            if (connected) {
                watchTextChanges()

                runOnUiThread {
                    codeEditorLayout.snack(R.string.connected, Snackbar.LENGTH_SHORT)
                }
            } else {
                noConnectionSnackbar?.dismiss()

                runOnUiThread {
                    codeEditorLayout.editable = false
                }

                textDisposable?.dispose()

            }

            runOnUiThread {
                viewModel.offlineModeEnabled.value = !connected || offlineModeManager.isEnabled()
//                viewModel.offlineModeEnabled.value = !viewModel.documentSyncManager.isConnected || offlineModeManager.isEnabled()
            }
        }

        viewModel.loading.observe(this) {
            if (it) {
                loadingComponent.showLoading()
            } else {
                loadingComponent.showContent(animated = true)
            }
        }

        viewModel.textChange.observe(this) {
            val oldSelectionStart = codeEditorLayout.codeEditorView.codeEditText.selectionStart
            val oldSelectionEnd = codeEditorLayout.codeEditorView.codeEditText.selectionEnd
            viewModel.currentText = it.newText

            // set new cursor position
            val newSelectionStart = calculateNewSelectionIndex(oldSelectionStart, it.patches)
                    .coerceIn(0, viewModel.currentText?.length)
            val newSelectionEnd = calculateNewSelectionIndex(oldSelectionEnd, it.patches)
                    .coerceIn(0, viewModel.currentText?.length)

            setEditorText(viewModel.currentText ?: "", newSelectionStart, newSelectionEnd)
        }
    }

    private var textDisposable: Disposable? = null

    private fun watchTextChanges() {
        textDisposable?.dispose()

        val syncInterval = viewModel.preferencesHolder.codeEditorSyncIntervalPreference.persistedValue
        textDisposable = Observable.interval(syncInterval, TimeUnit.MILLISECONDS)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .bindToLifecycle(this as LifecycleProvider<FragmentEvent>)
                .subscribeBy(onNext = {
                    viewModel.documentSyncManager.sync()
                }, onError = {
                    Timber.e(it)
                    viewModel.disconnect("Error in client sync code")
                    runOnUiThread {
                        codeEditorLayout.snack(R.string.sync_error, LENGTH_SHORT)
                    }
                })

//        textDisposable = RxTextView
//                .textChanges(codeEditorLayout.codeEditorView.codeEditText)
//                .skipInitialValue()
//                .debounce(100, TimeUnit.MILLISECONDS)
//                .subscribeOn(Schedulers.io())
//                .observeOn(AndroidSchedulers.mainThread())
//                .bindToLifecycle(this as LifecycleProvider<FragmentEvent>)
//                .subscribeBy(onNext = {
//                    sendPatchIfChanged()
//                }, onError = {
//                    context?.toast(it.prettyPrint(), Toast.LENGTH_LONG)
//                })
    }

    @SuppressLint("ClickableViewAccessibility")
    @CallSuper
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        codeEditorLayout = view.findViewById(R.id.codeEditorView)
        codeEditorLayout.minimapGravity = Gravity.BOTTOM or Gravity.END
        codeEditorLayout.languageRuleBook = MarkdownRuleBook()
        codeEditorLayout.codeEditorView.engine.addListener(object : ZoomEngine.Listener {
            override fun onIdle(engine: ZoomEngine) {
                viewModel.saveEditorState()
            }

            override fun onUpdate(engine: ZoomEngine, matrix: Matrix) {
//                val totalWidth = codeEditorLayout.contentLayout.width
//
//                val panX = engine.panX
//                val panY = engine.panY
//
//                val offsetX = engine.computeHorizontalScrollOffset()
//                val totalRangeX = engine.computeHorizontalScrollRange()
//                val percentage = engine.computeHorizontalScrollOffset().toFloat() / engine.computeHorizontalScrollRange()
//
//                val offsetXMaybe = totalRangeX * percentage
//                val offsetFromPan = -panX
//                val test = 1
            }
        })
        codeEditorLayout.codeEditorView.selectionChangedListener = this

        // disable user input by default, it will be enabled automatically once connected to the server
        // if not disabled in preferences
        codeEditorLayout.editable = false
    }

    /**
     * Set the editor content to the specified text.
     *
     * @param text the text to set
     * @param selectionStart optional selection start index
     * @param selectionEnd optional selection end index
     */
    private fun setEditorText(text: String, selectionStart: Int? = null, selectionEnd: Int? = null) {
        // we don't listen to selection changes when the text is changed via code
        // because the selection will be restored from persistence anyway
        // and the listener would override this
        val editor = codeEditorLayout.codeEditorView
        editor.selectionChangedListener = null
        editor.text = text
        selectionStart?.let {
            setEditorSelection(text.length, it, selectionEnd)
        }
        editor.selectionChangedListener = this
    }

    private fun setEditorSelection(maxIndex: Int, selectionStart: Int, selectionEnd: Int?) {
        val endIndex = selectionEnd ?: selectionStart
        codeEditorLayout.codeEditorView.codeEditText
                .setSelection(selectionStart.coerceIn(0, maxIndex), endIndex.coerceIn(0, maxIndex))
    }

    override fun onSelectionChanged(start: Int, end: Int, hasSelection: Boolean) {
        viewModel.saveEditorState()
    }

    /**
     * Calculates the positioning percentages for x and y axis
     *
     * @return a point with horizontal (x) and vertical (y) positioning percentages
     */
    private fun getCurrentPositionPercentage(): PointF {
        val engine = codeEditorLayout.codeEditorView.engine
        return PointF(engine.computeHorizontalScrollOffset().toFloat() / engine.computeHorizontalScrollRange(),
                engine.computeVerticalScrollOffset().toFloat() / engine.computeVerticalScrollRange())
    }

    /**
     * Takes a point with percentage values and returns a point with the actual absolute coordinates
     *
     * @return a point with horizontal (x) and vertical (y) absolute, scale-independent, positioning coordinate values
     */
    private fun computeAbsolutePosition(percentage: PointF): PointF {
        val engine = codeEditorLayout.codeEditorView.engine
        return PointF(-1 * percentage.x * (engine.computeHorizontalScrollRange() / engine.realZoom),
                -1 * percentage.y * (engine.computeVerticalScrollRange() / engine.realZoom)
        )
    }

    private fun calculateNewSelectionIndex(oldSelection: Int, patches: LinkedList<diff_match_patch.Patch>): Int {
        var newSelection = oldSelection

        var currentIndex: Int
        // calculate how many characters have been inserted before the cursor
        patches.forEach { patch ->
            currentIndex = patch.start1

            patch.diffs.forEach { diff ->
                when (diff.operation) {
                    diff_match_patch.Operation.INSERT -> {
                        if (currentIndex < newSelection) {
                            newSelection += diff.text.length
                        }
                    }
                    diff_match_patch.Operation.DELETE -> {
                        if (currentIndex < newSelection) {
                            newSelection -= diff.text.length
                        }
                    }
                    else -> {
                        currentIndex += diff.text.length
                    }
                }
            }
        }

        return newSelection
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val parent = super.onCreateView(inflater, container, savedInstanceState) as ViewGroup
        return loadingComponent.onCreateView(inflater, parent, savedInstanceState)
    }

    private val offlineModeObserver = Observer<Boolean> { enabled ->
        if (enabled) {
            viewModel.disconnect(reason = "Offline mode was activated")
            viewModel.loadTextFromPersistence()
        } else {
            viewModel.reconnectToServer()
        }
    }

    override fun onStart() {
        super.onStart()
        offlineModeManager.isEnabled.observe(viewLifecycleOwner, offlineModeObserver)
    }

    override fun onPause() {
        super.onPause()
        offlineModeManager.isEnabled.removeObserver(offlineModeObserver)
    }

}