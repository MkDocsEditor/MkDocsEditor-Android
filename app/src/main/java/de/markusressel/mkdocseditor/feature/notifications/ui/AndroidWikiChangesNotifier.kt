package de.markusressel.mkdocseditor.feature.notifications.ui

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.notifications.data.NotificationPermission
import de.markusressel.mkdocseditor.feature.notifications.data.WikiChangesChannel
import de.markusressel.mkdocseditor.feature.notifications.domain.PageChange
import de.markusressel.mkdocseditor.feature.notifications.domain.WikiChangesNotifier
import de.markusressel.mkdocseditor.ui.activity.MainActivityCompose
import javax.inject.Inject

/**
 * One notification per changed page, in a group per server. A page that changes again replaces its notification.
 */
internal class AndroidWikiChangesNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
    private val permission: NotificationPermission,
) : WikiChangesNotifier {

    override fun show(backendId: Long, changes: List<PageChange>) {
        if (!permission.isGranted()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        WikiChangesChannel.create(context)
        val tag = "$TAG_PREFIX$backendId"
        val group = "$GROUP_PREFIX$backendId"

        changes.forEach { change ->
            val title = titleOf(change)
            val text = change.headings.joinToString(HEADING_SEPARATOR)
            val id = change.documentId.hashCode()
            val open = openIntent(context, backendId, change)
            val builder = NotificationCompat.Builder(context, WikiChangesChannel.ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setWhen(change.time.time)
                .setShowWhen(true)
                .setGroup(group)
                .setContentIntent(PendingIntent.getActivity(context, tag.hashCode() * 31 + id, open, PENDING_FLAGS))
                .setAutoCancel(true)
            if (text.isNotEmpty()) builder.setContentText(text).setStyle(NotificationCompat.BigTextStyle().bigText(text))
            manager.notify(tag, id, builder.build())
        }

        // the group also holds the pages of earlier checks that are still shown
        val shown = manager.activeNotifications.filter { it.tag == tag && it.id != SUMMARY_ID }
        val lines = shown.mapNotNull { it.notification.extras.getCharSequence(NotificationCompat.EXTRA_TITLE) }
        val summaryText = context.resources.getQuantityString(R.plurals.wiki_changes_summary, shown.size, shown.size)
        val inbox = NotificationCompat.InboxStyle().setSummaryText(summaryText)
        lines.forEach { inbox.addLine(it) }
        val openApp = Intent(context, MainActivityCompose::class.java)
        manager.notify(
            tag,
            SUMMARY_ID,
            NotificationCompat.Builder(context, WikiChangesChannel.ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(summaryText)
                .setStyle(inbox)
                .setGroup(group)
                .setGroupSummary(true)
                .setContentIntent(PendingIntent.getActivity(context, tag.hashCode(), openApp, PENDING_FLAGS))
                .setAutoCancel(true)
                .build()
        )
    }

    /** "Anna and Ben changed Shopping" */
    private fun titleOf(change: PageChange): String {
        val authors = change.authors
        return when (authors.size) {
            1 -> context.getString(R.string.wiki_change_title_one, authors[0], change.pageName)
            2 -> context.getString(R.string.wiki_change_title_two, authors[0], authors[1], change.pageName)
            else -> context.resources.getQuantityString(
                R.plurals.wiki_change_title_more, authors.size - 1, authors[0], authors.size - 1, change.pageName,
            )
        }
    }

    private companion object {
        const val TAG_PREFIX = "wiki-changes:"
        const val GROUP_PREFIX = "de.markusressel.mkdocseditor.WIKI_CHANGES:"
        const val SUMMARY_ID = 0
        const val HEADING_SEPARATOR = " · "
        const val PENDING_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}

/** opens the page of [change], at the first of its headings that the page still has */
internal fun openIntent(context: Context, backendId: Long, change: PageChange): Intent =
    Intent(context, MainActivityCompose::class.java)
        .putExtra(MainActivityCompose.EXTRA_OPEN_DOCUMENT_BACKEND, backendId)
        .putExtra(MainActivityCompose.EXTRA_OPEN_DOCUMENT, change.documentId)
        .putStringArrayListExtra(MainActivityCompose.EXTRA_OPEN_DOCUMENT_HEADINGS, ArrayList(change.headings))
