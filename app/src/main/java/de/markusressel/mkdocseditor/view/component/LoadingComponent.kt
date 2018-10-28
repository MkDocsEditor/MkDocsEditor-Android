package de.markusressel.mkdocseditor.view.component

import android.content.Context
import android.os.Bundle
import androidx.annotation.CallSuper
import androidx.annotation.StringRes
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import com.afollestad.materialdialogs.MaterialDialog
import com.github.ajalt.timberkt.Timber
import com.jakewharton.rxbinding2.view.RxView
import com.trello.rxlifecycle2.kotlin.bindToLifecycle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.extensions.common.prettyPrint
import de.markusressel.mkdocseditor.view.fragment.base.LifecycleFragmentBase

/**
 * Created by Markus on 15.02.2018.
 */
class LoadingComponent(hostFragment: LifecycleFragmentBase, val onShowContent: ((animated: Boolean) -> Unit)? = null,
                       /**
                        * Called when the error screen is shown
                        */
                       val onShowError: ((message: String, t: Throwable?) -> Unit)? = null,
                       /**
                        * Called when the error is clicked
                        * Show a sophisticated error screen here
                        */
                       val onErrorClicked: ((message: String, t: Throwable?) -> Unit)? = null) : FragmentComponent(hostFragment) {

    protected lateinit var loadingLayout: ViewGroup

    protected lateinit var errorLayout: ViewGroup
    protected var contentView: ViewGroup? = null

    lateinit var errorDescription: TextView


    fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        contentView = container

        val rootView = createWrapperLayout()
        loadingLayout = rootView
                .findViewById(R.id.layoutLoading)
        errorLayout = rootView
                .findViewById(R.id.layoutError)
        errorDescription = errorLayout
                .findViewById(R.id.errorDescription)

        return rootView
    }

    private fun createWrapperLayout(): ViewGroup {
        val baseLayout = FrameLayout(context)

        // attach the original content view
        contentView
                ?.let {
                    baseLayout
                            .addView(contentView)
                }

        // inflate "layout_loading" and "layout_error" layouts and attach it to a newly created layout
        val layoutInflater = LayoutInflater
                .from(context)
        layoutInflater.inflate(R.layout.layout_error, baseLayout, true) as ViewGroup
        layoutInflater.inflate(R.layout.layout_loading, baseLayout, true) as ViewGroup

        return baseLayout
    }

    /**
     * Show layout_loading animation
     */
    @CallSuper
    fun showLoading() {
        fadeView(loadingLayout, 1f)
    }

    /**
     * Show the actual page content
     */
    @CallSuper
    fun showContent(animated: Boolean = true) {
        setViewVisibility(errorLayout, View.GONE)

        contentView
                ?.let {
                    if (animated) {
                        fadeView(it, 1f)
                    } else {
                        setViewVisibility(it, View.VISIBLE)
                    }
                }

        if (animated) {
            fadeView(loadingLayout, 0f)
        } else {
            setViewVisibility(loadingLayout, View.GONE)
        }

        onShowContent
                ?.let {
                    it(animated)
                }
    }

    /**
     * Show an error screen
     *
     * @param message the message to show
     */
    fun showError(@StringRes message: Int) {
        showError(fragment.getString(message))
    }

    /**
     * Show an error screen
     *
     * @param message the message to show
     */
    fun showError(message: String) {
        showError(message, null)
    }

    /**
     * Show an error screen
     *
     * @param throwable the exception that was raised
     */
    fun showError(throwable: Throwable) {
        showError(R.string.exception_raised, throwable)
    }

    private fun showError(@StringRes message: Int, throwable: Throwable? = null) {
        showError(fragment.getString(message), throwable)
    }

    private fun showError(message: String, throwable: Throwable? = null) {
        throwable
                ?.let {
                    Timber
                            .e(throwable) { message }
                }
        val errorDescriptionText: CharSequence = throwable?.let {
            var text = ""

            if (message.isNotEmpty()) {
                text += "$message\n\n"
            }

            text += "${throwable.javaClass.simpleName}\n"

            throwable
                    .message
                    ?.let {
                        if (it.isNotEmpty()) text += it
                    }

            text
        } ?: message

        errorDescription
                .text = errorDescriptionText

        RxView
                .clicks(errorLayout)
                .bindToLifecycle(errorLayout)
                .subscribe {
                    onErrorClicked
                            ?.let {
                                it(message, throwable)
                                return@subscribe
                            }

                    val contentText = throwable?.let {
                        message + "\n\n\n" + throwable.prettyPrint()
                    } ?: message

                    MaterialDialog(context as Context)
                            .show {
                                title(R.string.error)
                                message(text = contentText)
                                positiveButton(res = android.R.string.ok)
                            }
                }

        setViewVisibility(errorLayout, View.VISIBLE)
        contentView
                ?.let {
                    fadeView(it, 0f)
                }
        fadeView(loadingLayout, 0f)

        onShowError
                ?.let {
                    it(message, throwable)
                }
    }

    private fun setViewVisibility(view: View, visibility: Int) {
        view
                .visibility = visibility
    }

    private fun fadeView(view: View, alpha: Float) {
        val interpolator = when {
            alpha > 0 -> DecelerateInterpolator()
            else -> LinearInterpolator()
        }

        val duration = when {
            alpha >= 1 -> FADE_IN_DURATION_MS
            alpha <= 0 -> FADE_OUT_DURATION_MS
            else -> FADE_DURATION_MS
        }

        view
                .animate()
                .alpha(alpha)
                .setDuration(duration)
                .setInterpolator(interpolator)
                .withStartAction {
                    if (alpha > 0) {
                        view
                                .alpha = 0f
                        view
                                .visibility = View
                                .VISIBLE
                    }
                }
                .withEndAction {
                    if (alpha <= 0) {
                        view
                                .visibility = View
                                .GONE
                    }
                }
    }

    companion object {
        const val FADE_DURATION_MS = 300L
        const val FADE_IN_DURATION_MS = 400L
        const val FADE_OUT_DURATION_MS = FADE_IN_DURATION_MS / 2
    }

}