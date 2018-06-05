package de.markusressel.mkdocseditor.view.activity

import android.content.Context
import android.os.Bundle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.activity.base.DaggerSupportActivityBase
import de.markusressel.mkdocseditor.view.fragment.DocumentsFragment
import javax.inject.Inject

class MainActivity : DaggerSupportActivityBase() {

    @Inject
    lateinit var context: Context

    override val style: Int
        get() = DEFAULT
    override val layoutRes: Int
        get() = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super
                .onCreate(savedInstanceState)

        val existingFragment = supportFragmentManager.findFragmentByTag("itemtree")
        val fragment = existingFragment ?: DocumentsFragment()

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.contentLayout, fragment, "itemtree")
                .commit()

        //        restClient
        //                .getItemTree()
        //                .subscribeOn(Schedulers.io())
        //                .observeOn(AndroidSchedulers.mainThread())
        //                .subscribeBy(onSuccess = {
        //                    textView
        //                            .setText(it.name, TextView.BufferType.SPANNABLE)
        //                            .toString()
        //                }, onError = {
        //                toast(it.prettyPrint(), Toast.LENGTH_LONG)
        //                })


        //        restClient.getDocument("9360119919153839349")
        //                .subscribeOn(Schedulers.io())
        //                .observeOn(AndroidSchedulers.mainThread())
        //                .subscribeBy(
        //                        onSuccess = {
        //                            textView.text = it.toString()
        //                        },
        //                        onError = {
        //                toast(it.prettyPrint(), Toast.LENGTH_LONG)
        //                        }
        //                )
    }

}
