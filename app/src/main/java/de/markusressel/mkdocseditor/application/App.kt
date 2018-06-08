package de.markusressel.mkdocseditor.application

import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.dagger.DaggerAppComponent
import timber.log.Timber

/**
 * Created by Markus on 20.12.2017.
 */
class App : DaggerApplicationBase() {

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerAppComponent
                .builder()
                .create(this)
    }

    override fun onCreate() {
        super
                .onCreate()
        // register app lifecycle
        registerActivityLifecycleCallbacks(AppLifecycleTracker())

        // Clear DB entirely
        //        BoxStore
        //                .deleteAllFiles(applicationContext, null)

        plantTimberTrees()
    }

    private fun plantTimberTrees() {
        if (BuildConfig.DEBUG) {
            Timber
                    .plant(Timber.DebugTree())
        }
    }

}
