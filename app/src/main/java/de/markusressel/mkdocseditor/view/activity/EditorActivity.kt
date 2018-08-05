package de.markusressel.mkdocseditor.view.activity

import android.content.Context
import android.content.Intent
import android.os.Bundle
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.view.activity.base.DaggerSupportActivityBase
import de.markusressel.mkdocseditor.view.fragment.CodeEditorFragment
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
        val name = intent
                .getStringExtra(KEY_NAME)

        supportActionBar
                ?.title = name

        val existingFragment = supportFragmentManager
                .findFragmentByTag("editor")
        val fragment = existingFragment ?: CodeEditorFragment.newInstance(id)

        supportFragmentManager
                .beginTransaction()
                .replace(R.id.contentLayout, fragment, "editor")
                .commit()
    }

    companion object {

        private const val KEY_ID = "KEY_ID"
        private const val KEY_NAME = "KEY_NAME"

        fun getNewInstanceIntent(context: Context, id: String, name: String): Intent {
            val intent = Intent(context, EditorActivity::class.java)
            intent
                    .flags = Intent
                    .FLAG_ACTIVITY_NEW_TASK
            intent
                    .putExtra(KEY_ID, id)
            intent
                    .putExtra(KEY_NAME, name)
            return intent
        }

    }

}
