package de.markusressel.mkdocseditor.feature.filebrowser.domain

import de.markusressel.mkdocseditor.feature.filebrowser.data.FolderContent

internal enum class NewItemNameProblem { Blank, NotAllowed, AlreadyExists }

/**
 * Checks the name of a new item before it goes to the server. The allowed characters are the rules of `ValidateName` of
 * the backend.
 *
 * @return what is wrong with the name, null if nothing is
 */
internal fun newItemNameProblem(name: String, folder: FolderContent?): NewItemNameProblem? {
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> NewItemNameProblem.Blank
        trimmed.startsWith(".") || trimmed.any { it in "/\\\u0000" } -> NewItemNameProblem.NotAllowed
        folder?.hasItemNamed(trimmed) == true -> NewItemNameProblem.AlreadyExists
        else -> null
    }
}
