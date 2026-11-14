package de.markusressel.mkdocseditor.feature.history.domain

import de.markusressel.mkdocseditor.feature.editor.domain.ChangeLocation
import de.markusressel.mkdocseditor.feature.editor.domain.ChangedLines
import de.markusressel.mkdocsrestclient.history.DiffHunkModel
import de.markusressel.mkdocsrestclient.history.DiffOp
import de.markusressel.mkdocsrestclient.history.HistoryAuthorModel
import de.markusressel.mkdocsrestclient.history.HistoryEntryModel

/**
 * Where the editor goes for a hunk: its first changed lines, found again by heading and offset as for a change of the
 * feed. A backend without the offset only gives the section.
 */
internal fun DiffHunkModel.changeLocation(): ChangeLocation {
    val headings = listOfNotNull(heading)
    val offset = offset ?: return ChangeLocation(headings)
    var line = newStart
    var index = 0
    while (index < lines.size && lines[index].op == DiffOp.CONTEXT) {
        line++
        index++
    }
    var count = 0
    while (index < lines.size && lines[index].op != DiffOp.CONTEXT) {
        if (lines[index].op == DiffOp.ADDED) count++
        index++
    }
    return ChangeLocation(
        headings = headings,
        ranges = listOf(ChangedLines(line, count, heading, headingOccurrence, offset + (line - newStart))),
    )
}

/** The name to show for an author: the account's current name, else the one in the commit */
internal val HistoryAuthorModel.shownName: String
    get() = displayName?.takeIf { it.isNotBlank() } ?: username.takeIf { it.isNotBlank() } ?: name

/** All people of an entry without repeats, the author first */
internal val HistoryEntryModel.people: List<HistoryAuthorModel>
    get() = (listOf(author) + coAuthors).distinctBy { it.accountId.ifEmpty { it.email } }

/**
 * The people of an entry, each with the agents they made the changes with: "Bob via Claude Code, Anna". An agent
 * without its person among them, or from a backend that only sends token names, goes with the author.
 *
 * @return each person with the names of their agents
 */
internal val HistoryEntryModel.peopleWithAgents: List<Pair<HistoryAuthorModel, List<String>>>
    get() {
        val people = people
        val known = agents.filter { agent -> people.any { it.isAccount && it.accountId == agent.accountId } }
        val homeless = agentNames - known.map { it.name }.toSet()
        return people.mapIndexed { index, person ->
            val own = known.filter { person.isAccount && it.accountId == person.accountId }.map { it.name }
            person to (own + if (index == 0) homeless else emptyList()).distinct()
        }
    }

/**
 * The names of the agents of an entry. A backend before `agents` only has the token names.
 */
internal val HistoryEntryModel.agentNames: List<String>
    get() = agents.map { it.name }.ifEmpty { appTokens }.distinct()

/**
 * The message of a commit when it says more than the backend's standard "Update <path>"
 */
internal val HistoryEntryModel.notableMessage: String?
    get() = message.takeUnless { it.isBlank() || it == "Update $path" }
