package de.markusressel.mkdocseditor.view.activity

import android.content.Context
import android.os.Bundle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.activity.base.DaggerSupportActivityBase
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
        super.onCreate(savedInstanceState)

        restClient.setHostname("localhost:8080")
        restClient.setApiResource("/")


        restClient.getDocument("bla")
                .subscribeBy(
                        onSuccess = {
                            textView.text = it.toString()
                        },
                        onError = {
                            textView.text = it.message
                        }
                )
    }

}
