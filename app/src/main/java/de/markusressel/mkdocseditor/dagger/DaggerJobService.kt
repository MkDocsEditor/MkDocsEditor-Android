package de.markusressel.mkdocseditor.dagger

import android.app.job.JobService
import dagger.android.AndroidInjection

/**
 * A dagger 2 field injection enabled [JobService]
 */
abstract class DaggerJobService : JobService() {

    override fun onCreate() {
        super.onCreate()
        AndroidInjection.inject(this)
    }

}