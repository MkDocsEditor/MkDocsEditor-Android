package de.markusressel.mkdocseditor.view.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.activity.base.DaggerSupportActivityBase
import de.markusressel.mkdocseditor.view.fragment.EditorFragment
import javax.inject.Inject

class EditorActivity : DaggerSupportActivityBase() {

    @Inject
    lateinit var context: Context

    override val style: Int
        get() = DEFAULT
    override val layoutRes: Int
        get() = R.layout.activity_main

    override fun onCreate(savedInstanceState: Bundle?) {
        super
                .onCreate(savedInstanceState)

        val id = intent
                .getStringExtra(KEY_ID)
        val content = intent
                .getStringExtra(KEY_CONTENT)

        val fragment = EditorFragment
                .newInstance(id, content)

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.contentLayout, fragment, null)
                .commit()
    }

    companion object {

        private const val KEY_ID = "KEY_ID"
        private const val KEY_CONTENT = "KEY_CONTENT"

        fun getNewInstanceIntent(context: Context, id: String, content: String): Intent {
            val intent = Intent(context, EditorActivity::class.java)
            intent
                    .flags = Intent
                    .FLAG_ACTIVITY_NEW_TASK
            intent
                    .putExtra(KEY_ID, id)
            intent
                    .putExtra(KEY_CONTENT, content)
            return intent
        }

    }

}
