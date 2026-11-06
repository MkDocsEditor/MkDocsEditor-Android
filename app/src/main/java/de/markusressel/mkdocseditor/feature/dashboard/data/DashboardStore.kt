package de.markusressel.mkdocseditor.feature.dashboard.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.markusressel.mkdocsrestclient.dashboard.DashboardSnapshotModel
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps the last pins and feed of each server and account in a file, so the dashboard opens at once and offline.
 * Personal pins differ between accounts, so each account has its own file.
 */
@Singleton
internal class DashboardStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun load(backendId: Long, username: String?): DashboardSnapshotModel? {
        val file = fileOf(backendId, username)
        if (!file.exists()) return null
        return try {
            DashboardSnapshotModel.parse(file.readText())
        } catch (e: IOException) {
            Timber.w(e, "Could not read the stored dashboard")
            null
        }
    }

    fun save(backendId: Long, username: String?, snapshot: DashboardSnapshotModel) {
        val file = fileOf(backendId, username)
        try {
            file.parentFile?.mkdirs()
            // a crash while writing must not leave half a file
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(snapshot.toJson())
            temporary.renameTo(file)
        } catch (e: IOException) {
            Timber.w(e, "Could not store the dashboard")
        }
    }

    private fun fileOf(backendId: Long, username: String?): File =
        File(dashboardDirectory(context), "$backendId-${hashOf(username.orEmpty())}.json")

    /** the username stays out of the file name */
    private fun hashOf(username: String): String =
        MessageDigest.getInstance("SHA-256").digest(username.toByteArray()).take(8).joinToString("") { "%02x".format(it) }
}

private fun dashboardDirectory(context: Context) = File(context.filesDir, "dashboard")

/** Deletes the stored dashboards of a removed server, of every account */
internal fun forgetDashboards(context: Context, backendId: Long) {
    dashboardDirectory(context).listFiles { file -> file.name.startsWith("$backendId-") }?.forEach { it.delete() }
}
