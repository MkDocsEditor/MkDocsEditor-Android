package de.markusressel.mkdocseditor.view

import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.dagger.DaggerSupportActivityBase

class MainActivity : DaggerSupportActivityBase() {
    override val style: Int
        get() = DEFAULT
    override val layoutRes: Int
        get() = R.layout.activity_main

}
