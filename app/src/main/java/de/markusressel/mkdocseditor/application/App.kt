package de.markusressel.mkdocseditor.application

import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import de.markusressel.mkdocseditor.dagger.DaggerAppComponent

/**
 * Created by Markus on 20.12.2017.
 */
class App : DaggerApplicationBase() {

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerAppComponent
                .builder()
                .create(this)
    }

}
