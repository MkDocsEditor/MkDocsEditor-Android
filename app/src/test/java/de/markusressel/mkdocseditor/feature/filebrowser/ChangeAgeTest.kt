package de.markusressel.mkdocseditor.feature.filebrowser

import de.markusressel.mkdocseditor.R
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.listentry.ChangeAge
import de.markusressel.mkdocseditor.feature.filebrowser.ui.compose.listentry.changeAge
import java.util.Date
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChangeAgeTest {

    private val now = Date(1_000_000_000_000L)
    private val day = 24 * 60 * 60_000L

    private fun ageOf(ms: Long) = changeAge(Date(now.time - ms), now)

    @Test
    fun `the age uses the largest unit that fits`() {
        assertEquals(ChangeAge(R.plurals.change_age_minutes, 5), ageOf(5 * 60_000L))
        assertEquals(ChangeAge(R.plurals.change_age_hours, 3), ageOf(3 * 60 * 60_000L))
        assertEquals(ChangeAge(R.plurals.change_age_days, 2), ageOf(2 * day))
        assertEquals(ChangeAge(R.plurals.change_age_weeks, 2), ageOf(15 * day))
        assertEquals(ChangeAge(R.plurals.change_age_months, 3), ageOf(95 * day))
    }

    @Test
    fun `a change from just now or the future counts as one minute`() {
        assertEquals(ChangeAge(R.plurals.change_age_minutes, 1), ageOf(0))
        assertEquals(ChangeAge(R.plurals.change_age_minutes, 1), ageOf(-5 * 60_000L))
    }

    @Test
    fun `a change from a year ago or longer is not shown`() {
        assertEquals(ChangeAge(R.plurals.change_age_months, 12), ageOf(364 * day))
        assertNull(ageOf(365 * day))
    }
}
