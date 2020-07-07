package de.markusressel.mkdocseditor.view.viewmodel

import de.markusressel.mkdocsrestclient.sync.websocket.diff.diff_match_patch
import java.util.*

data class TextChangeEvent(val newText: String, val patches: LinkedList<diff_match_patch.Patch>)
