package de.markusressel.mkdocseditor.application

import androidx.emoji.bundled.BundledEmojiCompatConfig
import androidx.emoji.text.EmojiCompat
import com.eightbitlab.rxbus.Bus
import com.eightbitlab.rxbus.registerInBus
import dagger.android.AndroidInjector
import dagger.android.DaggerApplication
import de.markusressel.mkdocseditor.BuildConfig
import de.markusressel.mkdocseditor.dagger.DaggerAppComponent
import de.markusressel.mkdocseditor.event.BasicAuthPasswordChangedEvent
import de.markusressel.mkdocseditor.event.BasicAuthUserChangedEvent
import de.markusressel.mkdocseditor.event.HostChangedEvent
import de.markusressel.mkdocsrestclient.BasicAuthConfig
import de.markusressel.mkdocsrestclient.MkDocsRestClient
import timber.log.Timber
import javax.inject.Inject

/**
 * Created by Markus on 20.12.2017.
 */
class App : DaggerApplicationBase() {

    @Inject
    internal lateinit var restClient: MkDocsRestClient

    override fun applicationInjector(): AndroidInjector<out DaggerApplication> {
        return DaggerAppComponent.builder().create(this)
    }

    override fun onCreate() {
        super
                .onCreate()
        // register app lifecycle
        registerActivityLifecycleCallbacks(AppLifecycleTracker())

        // Clear DB entirely
//        BoxStore.deleteAllFiles(applicationContext, null)

        plantTimberTrees()

        createListeners()

        initializeEmojiCompat()
    }

    private fun plantTimberTrees() {
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
    }

    private fun createListeners() {
        Bus.observe<HostChangedEvent>().subscribe {
            restClient.setHostname(it.host)
        }.registerInBus(this)

        Bus.observe<BasicAuthUserChangedEvent>().subscribe {
            val oldConfig = restClient.getBasicAuthConfig()
            restClient.setBasicAuthConfig(BasicAuthConfig(it.user, oldConfig?.password ?: ""))
        }.registerInBus(this)

        Bus.observe<BasicAuthPasswordChangedEvent>().subscribe {
            val oldConfig = restClient.getBasicAuthConfig()
            restClient.setBasicAuthConfig(
                    BasicAuthConfig(oldConfig?.username ?: "", it.password))
        }.registerInBus(this)
    }

    private fun initializeEmojiCompat() {
        val config = BundledEmojiCompatConfig(this)
        EmojiCompat.init(config)
    }

}
