package de.markusressel.mkdocseditor.view.activity

import android.os.Bundle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.activity.base.DaggerSupportActivityBase
import de.markusressel.rest.MkDocsRestApiClient

class MainActivity : DaggerSupportActivityBase() {
    override val style: Int
        get() = DEFAULT
    override val layoutRes: Int
        get() = R.layout.activity_main


    override fun onCreate(savedInstanceState: Bundle?) {
        super
                .onCreate(savedInstanceState)
        val client = MkDocsRestApiClient()
        
    }

}
