package de.markusressel.mkdocseditor.feature.notifications.domain

import de.markusressel.mkdocseditor.feature.notifications.data.WikiChangesMarks
import de.markusressel.mkdocsrestclient.dashboard.FeedEntryModel
import de.markusressel.mkdocsrestclient.dashboard.FeedPageModel
import de.markusressel.mkdocsrestclient.dashboard.PinsModel
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import javax.inject.Inject

/** The selected server as the check needs it */
internal interface WikiChangesServer {
    /** @return who is signed in on the selected server, null without a server or a session */
    suspend fun signedInAccount(): SignedInAccount?

    /** @return null for a server without pins */
    suspend fun pins(): PinsModel?

    /** @return null for a server without a feed */
    suspend fun feed(before: String?, limit: Int): FeedPageModel?
}

internal data class SignedInAccount(val backendId: Long, val accountId: String)

/** Shows what [CheckWikiChangesUseCase] found */
internal interface WikiChangesNotifier {
    fun show(backendId: Long, changes: List<PageChange>)
}

/**
 * Reads the feed of the selected server for changes by others to the pages the user cares about: those pinned for
 * everyone or for them, and those they edited. Runs in the background, about once an hour and after each push.
 */
internal class CheckWikiChangesUseCase @Inject constructor(
    private val server: WikiChangesServer,
    private val marks: WikiChangesMarks,
    private val notifier: WikiChangesNotifier,
) {
    /** @return false if the server could not be asked, so this should run again soon */
    suspend operator fun invoke(): Boolean = try {
        check()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Timber.w(e, "Could not check the wiki for changes")
        false
    }

    private suspend fun check() {
        val account = server.signedInAccount() ?: return
        val (backendId, accountId) = account
        val seenUntil = marks.seenUntil(backendId, accountId)
        val entries = readFeed(seenUntil) ?: return

        val edited = marks.editedPages(backendId, accountId) + editedBy(entries, accountId)
        marks.setEditedPages(backendId, accountId, edited)
        val newest = entries.maxOfOrNull { it.time.time }

        // the first check only learns where the feed is: everything in it happened before notifications were on
        if (seenUntil == null) {
            marks.setSeenUntil(backendId, accountId, newest ?: 0)
            return
        }

        val fresh = entries.filter { it.time.time > seenUntil }
        if (fresh.isEmpty()) return
        val pins = server.pins() ?: PinsModel()
        val watched = edited + (pins.family + pins.personal).map { it.documentId }
        val changes = findPageChanges(fresh, accountId, watched)
        if (changes.isNotEmpty()) notifier.show(backendId, changes)
        marks.setSeenUntil(backendId, accountId, maxOf(seenUntil, newest ?: seenUntil))
    }

    /**
     * Reads pages of the feed back to [seenUntil]. Without a mark it reads a few pages, to learn which pages this
     * account edited.
     *
     * @return newest first, null for a server without a feed
     */
    private suspend fun readFeed(seenUntil: Long?): List<FeedEntryModel>? {
        val entries = mutableListOf<FeedEntryModel>()
        var before: String? = null
        repeat(MAX_PAGES) {
            val page = server.feed(before, PAGE_SIZE) ?: return null
            entries += page.entries
            val oldest = page.entries.lastOrNull() ?: return entries
            if (seenUntil != null && oldest.time.time <= seenUntil) return entries
            before = page.next ?: return entries
        }
        return entries
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val MAX_PAGES = 3
    }
}
