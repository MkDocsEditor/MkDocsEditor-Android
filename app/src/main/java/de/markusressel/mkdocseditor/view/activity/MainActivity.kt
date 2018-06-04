package de.markusressel.mkdocseditor.view.activity

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.TextView
import com.jakewharton.rxbinding2.widget.RxTextView
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.activity.base.DaggerSupportActivityBase
import de.markusressel.mkdocseditor.view.fragment.DocumentsFragment
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import io.reactivex.rxkotlin.subscribeBy
import kotlinx.android.synthetic.main.activity_main.*
import javax.inject.Inject

class MainActivity : DaggerSupportActivityBase() {

    @Inject
    lateinit var context: Context

    @Inject
    lateinit var restClient: MkDocsRestClient

    override val style: Int
        get() = DEFAULT
    override val layoutRes: Int
        get() = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super
                .onCreate(savedInstanceState)

        restClient
                .setHostname("10.0.2.2:8080")


        //        restClient
        //                .getItemTree()
        //                .subscribeOn(Schedulers.io())
        //                .observeOn(AndroidSchedulers.mainThread())
        //                .subscribeBy(onSuccess = {
        //                    textView
        //                            .setText(it.name, TextView.BufferType.SPANNABLE)
        //                            .toString()
        //                }, onError = {
        //                    textView
        //                            .text = it
        //                            .message
        //                })

        // TODO: Implement navigation and show list fragment
        var fragment = DocumentsFragment()


        RxTextView
                .afterTextChangeEvents(md_editor)
                //                //.debounce(500, TimeUnit.MILLISECONDS)
                //                .subscribeOn(Schedulers.io())
                //                .observeOn(AndroidSchedulers.mainThread())
                //                .bindToLifecycle(this)
                .subscribeBy(onNext = {
                    md_editor
                            .refreshSyntaxHighlighting()
                    Log
                            .d("bla", "highlighting")
                }, onError = {
                    Log
                            .d("bla", it.message)
                })

        md_editor
                .setText(R.string.markdown_demo_text, TextView.BufferType.EDITABLE)

        //        restClient.getDocument("9360119919153839349")
        //                .subscribeOn(Schedulers.io())
        //                .observeOn(AndroidSchedulers.mainThread())
        //                .subscribeBy(
        //                        onSuccess = {
        //                            textView.text = it.toString()
        //                        },
        //                        onError = {
        //                            textView.text = it.message
        //                        }
        //                )
    }

}
