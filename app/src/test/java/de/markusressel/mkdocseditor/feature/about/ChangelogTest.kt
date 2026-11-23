package de.markusressel.mkdocseditor.feature.about

import de.markusressel.mkdocseditor.feature.about.data.Changelog
import de.markusressel.mkdocseditor.feature.about.data.ChangelogEntry
import de.markusressel.mkdocseditor.feature.about.data.parseChangelog
import kotlin.test.Test
import kotlin.test.assertEquals

class ChangelogTest {

    @Test
    fun `the changelog of a build is read`() {
        val json = """{"commit":"699a5bc","changes":[{"number":195,"title":"Fade in what others type","date":"2026-11-23"}]}"""
        assertEquals(
            Changelog("699a5bc", listOf(ChangelogEntry(195, "Fade in what others type", "2026-11-23"))),
            parseChangelog(json),
        )
    }

    @Test
    fun `a build without git has no commit and no changes`() {
        assertEquals(Changelog(), parseChangelog("""{"commit":null,"changes":[]}"""))
    }

    @Test
    fun `a changelog that can't be read is empty`() {
        assertEquals(Changelog(), parseChangelog("""{"changes":[{"number":"x"}]}"""))
    }
}
