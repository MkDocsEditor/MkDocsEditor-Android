package de.markusressel.mkdocseditor.feature.preferences.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import dagger.hilt.android.AndroidEntryPoint
import de.markusressel.kutepreferences.ui.theme.KutePreferencesTheme
import de.markusressel.kutepreferences.ui.views.KuteOverview
import de.markusressel.mkdocseditor.feature.theme.MkDocsEditorTheme
import de.markusressel.mkdocseditor.ui.fragment.base.DaggerSupportFragmentBase

@AndroidEntryPoint
class PreferencesFragment : DaggerSupportFragmentBase() {

    private val viewModel: PreferencesViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = ComposeView(requireContext()).apply {
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
        setContent {
            MkDocsEditorTheme {
                Surface {
                    KutePreferencesTheme {
                        val currentItems by viewModel.currentPreferenceItems.collectAsState(initial = emptyList())

                        KuteOverview(
                            modifier = Modifier
                                .padding(8.dp)
                                .fillMaxWidth(),
                            items = currentItems
                        )
                    }
                }
            }
        }
    }

    /**
     * Called when the user presses the back button
     *
     * @return true, if the back button event was consumed, false otherwise
     */
    fun onBackPressed() = viewModel.navigateUp()
}