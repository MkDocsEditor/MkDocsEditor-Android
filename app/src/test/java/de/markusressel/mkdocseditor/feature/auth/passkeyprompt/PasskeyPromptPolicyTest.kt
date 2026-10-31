package de.markusressel.mkdocseditor.feature.auth.passkeyprompt

import de.markusressel.mkdocseditor.auth.api.PasskeyInfo
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.data.PasskeyPromptPolicy
import de.markusressel.mkdocseditor.feature.auth.passkeyprompt.data.PasskeyPromptState
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PasskeyPromptPolicyTest {

    private val now = Instant.parse("2026-11-01T12:00:00Z")
    private val nothingInstalled: (String) -> Boolean = { false }
    private val bitwardenInstalled: (String) -> Boolean = { it == PasskeyPromptPolicy.BITWARDEN_PACKAGE }

    private fun passkey(id: String = "p1", provider: String? = null, backedUp: Boolean = false) =
        PasskeyInfo(id, "Passkey", provider = provider, backedUp = backedUp)

    private fun usable(vararg passkeys: PasskeyInfo, registeredHere: Set<String> = emptySet(), isInstalled: (String) -> Boolean = nothingInstalled) =
        PasskeyPromptPolicy.hasUsablePasskey(passkeys.toList(), registeredHere, isInstalled)

    @Test
    fun `no passkeys means none is usable`() {
        assertFalse(usable())
    }

    @Test
    fun `a passkey created on this device is usable`() {
        assertTrue(usable(passkey("p1", provider = "iCloud Keychain"), registeredHere = setOf("p1")))
    }

    @Test
    fun `Google Password Manager is on every Android phone`() {
        assertTrue(usable(passkey(provider = "Google Password Manager")))
    }

    @Test
    fun `a Bitwarden passkey counts only with Bitwarden installed`() {
        assertFalse(usable(passkey(provider = "Bitwarden")))
        assertTrue(usable(passkey(provider = "Bitwarden"), isInstalled = bitwardenInstalled))
    }

    @Test
    fun `desktop-only providers do not help on the phone`() {
        listOf("iCloud Keychain", "Windows Hello", "Chrome on Mac", "KeePassXC").forEach {
            assertFalse(usable(passkey(provider = it, backedUp = true)), it)
        }
    }

    @Test
    fun `an unknown provider counts when the passkey is synced`() {
        assertFalse(usable(passkey(provider = null, backedUp = false)))
        assertTrue(usable(passkey(provider = null, backedUp = true)))
        assertTrue(usable(passkey(provider = "Some Vault", backedUp = true)))
    }

    @Test
    fun `the suggestion after sign-in needs passkeys on the server and no usable passkey`() {
        val state = PasskeyPromptState()
        assertTrue(PasskeyPromptPolicy.shouldOfferAfterSignIn(true, emptyList(), state, nothingInstalled))
        assertFalse(PasskeyPromptPolicy.shouldOfferAfterSignIn(false, emptyList(), state, nothingInstalled))
        assertFalse(
            PasskeyPromptPolicy.shouldOfferAfterSignIn(true, listOf(passkey(provider = "Bitwarden")), state, bitwardenInstalled),
        )
        assertTrue(
            PasskeyPromptPolicy.shouldOfferAfterSignIn(true, listOf(passkey(provider = "Bitwarden")), state, nothingInstalled),
        )
    }

    @Test
    fun `after Later the suggestion does not come back after sign-in`() {
        val state = PasskeyPromptState(skippedAt = now)
        assertFalse(PasskeyPromptPolicy.shouldOfferAfterSignIn(true, emptyList(), state, nothingInstalled))
    }

    @Test
    fun `the reminder needs Later first`() {
        assertFalse(PasskeyPromptPolicy.shouldRemind(PasskeyPromptState(), now, true, false))
    }

    @Test
    fun `the reminder waits a few days after Later and after each Not now`() {
        val interval = PasskeyPromptPolicy.REMIND_INTERVAL
        val skipped = PasskeyPromptState(skippedAt = now - interval + Duration.ofMinutes(1))
        assertFalse(PasskeyPromptPolicy.shouldRemind(skipped, now, true, false))
        assertTrue(PasskeyPromptPolicy.shouldRemind(skipped.copy(skippedAt = now - interval), now, true, false))

        val dismissed = PasskeyPromptState(skippedAt = now - Duration.ofDays(30), reminderDismissedAt = now - Duration.ofDays(1))
        assertFalse(PasskeyPromptPolicy.shouldRemind(dismissed, now, true, false))
        assertTrue(PasskeyPromptPolicy.shouldRemind(dismissed, now + interval, true, false))
    }

    @Test
    fun `no reminder with a usable passkey or without passkeys on the server`() {
        val state = PasskeyPromptState(skippedAt = now - Duration.ofDays(30))
        assertFalse(PasskeyPromptPolicy.shouldRemind(state, now, true, hasUsablePasskey = true))
        assertFalse(PasskeyPromptPolicy.shouldRemind(state, now, passkeysEnabled = false, hasUsablePasskey = false))
    }
}
