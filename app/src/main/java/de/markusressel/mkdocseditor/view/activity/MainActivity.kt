package de.markusressel.mkdocseditor.view.activity

import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.activity.base.DaggerSupportActivityBase

class MainActivity : DaggerSupportActivityBase() {
    override val style: Int
        get() = DEFAULT
    override val layoutRes: Int
        get() = R.layout.activity_main

}
