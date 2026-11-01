package de.markusressel.mkdocseditor.feature.auth.link.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.serialization.saved
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * The setup, invite, reset or connect link the activity was opened with.
 *
 * The link is removed from the intent, so without this its screens are gone after a rotation or
 * process death, along with what the user entered there. Saved state is only readable by the
 * system, and the screens of a link pasted inside the app keep it there as well.
 */
@HiltViewModel
internal class PendingAuthLinkViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private var savedLink by savedStateHandle.saved<String?> { null }

    private val _link = MutableStateFlow(savedLink)
    val link = _link.asStateFlow()

    fun open(link: String) = set(link)

    fun close() = set(null)

    private fun set(link: String?) {
        savedLink = link
        _link.value = link
    }
}
