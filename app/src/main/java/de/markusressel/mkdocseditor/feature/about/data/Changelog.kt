package de.markusressel.mkdocseditor.feature.about.data

import android.content.Context
import com.github.ajalt.timberkt.Timber
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonClass
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import java.io.IOException

/**
 * What this build contains: its commit and the pull requests merged before it, newest first. Generated from git at
 * build time (`GenerateChangelog` in the app's build file); the web client has the same format.
 *
 * @param commit short hash, null for a build without git
 */
@JsonClass(generateAdapter = true)
internal data class Changelog(
    val commit: String? = null,
    val changes: List<ChangelogEntry> = emptyList(),
)

/**
 * @param number of the pull request
 * @param date the day it was merged, `YYYY-MM-DD`
 */
@JsonClass(generateAdapter = true)
internal data class ChangelogEntry(
    val number: Int,
    val title: String,
    val date: String,
)

private val changelogAdapter: JsonAdapter<Changelog> by lazy { Moshi.Builder().build().adapter(Changelog::class.java) }

internal fun parseChangelog(json: String): Changelog = try {
    changelogAdapter.fromJson(json) ?: Changelog()
} catch (e: JsonDataException) {
    Timber.w(e) { "The changelog of this build can't be read" }
    Changelog()
} catch (e: IOException) {
    Timber.w(e) { "The changelog of this build can't be read" }
    Changelog()
}

internal fun Context.readChangelog(): Changelog = try {
    parseChangelog(assets.open("changelog.json").bufferedReader().use { it.readText() })
} catch (e: IOException) {
    Timber.w(e) { "This build has no changelog" }
    Changelog()
}

internal fun ChangelogEntry.url() = "https://github.com/MkDocsEditor/MkDocsEditor-Android/pull/$number"
