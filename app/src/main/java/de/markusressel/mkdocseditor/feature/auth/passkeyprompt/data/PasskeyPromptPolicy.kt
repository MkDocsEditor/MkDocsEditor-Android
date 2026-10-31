package de.markusressel.mkdocseditor.feature.auth.passkeyprompt.data

import de.markusressel.mkdocseditor.auth.api.PasskeyInfo
import java.time.Duration
import java.time.Instant

/**
 * When to suggest "Sign in with a passkey next time" (docs/auth.md "Passkey right after
 * sign-in"): after a sign-in when the account has no passkey this device can use, and as a
 * reminder card on the account screen after "Later", at most every [REMIND_INTERVAL]
 */
object PasskeyPromptPolicy {

    val REMIND_INTERVAL: Duration = Duration.ofDays(3)

    const val BITWARDEN_PACKAGE = "com.x8bit.bitwarden"

    /**
     * Android apps of the passkey providers the backend names (from the AAGUID). Google
     * Password Manager is part of Play services; the others need their app.
     */
    private val providerPackages = mapOf(
        "Bitwarden" to BITWARDEN_PACKAGE,
        "1Password" to "com.onepassword.android",
        "Dashlane" to "com.dashlane",
        "Proton Pass" to "proton.android.pass",
    )
    private const val GOOGLE_PASSWORD_MANAGER = "Google Password Manager"

    /** providers that never sync to Android */
    private val desktopOnlyProviders = setOf("iCloud Keychain", "Windows Hello", "Chrome on Mac", "KeePassXC")

    /** the Android packages to look for, also listed under <queries> in the manifest */
    val knownPackages: Collection<String> get() = providerPackages.values

    /**
     * @param registeredHere ids of passkeys created on this device
     * @param isInstalled whether an app package is installed
     */
    fun hasUsablePasskey(
        passkeys: List<PasskeyInfo>,
        registeredHere: Set<String>,
        isInstalled: (String) -> Boolean,
    ): Boolean = passkeys.any { passkey ->
        val provider = passkey.provider
        val providerPackage = provider?.let { providerPackages[it] }
        when {
            passkey.id in registeredHere -> true
            provider == GOOGLE_PASSWORD_MANAGER -> true
            provider in desktopOnlyProviders -> false
            providerPackage != null -> isInstalled(providerPackage)
            // an unknown synced passkey may well be in a provider on this phone: do not nag
            else -> passkey.backedUp
        }
    }

    /**
     * Right after a sign-in: once per device and backend; after "Later" only the reminder card
     * on the account screen asks again
     */
    fun shouldOfferAfterSignIn(
        passkeysEnabled: Boolean,
        passkeys: List<PasskeyInfo>,
        state: PasskeyPromptState,
        isInstalled: (String) -> Boolean,
    ): Boolean = passkeysEnabled && state.skippedAt == null &&
        !hasUsablePasskey(passkeys, state.registeredHere, isInstalled)

    /**
     * The reminder card on the account screen: only after "Later", and at most once per
     * [REMIND_INTERVAL], counted from "Later" or the last "Not now"
     */
    fun shouldRemind(
        state: PasskeyPromptState,
        now: Instant,
        passkeysEnabled: Boolean,
        hasUsablePasskey: Boolean,
    ): Boolean {
        val skipped = state.skippedAt ?: return false
        if (!passkeysEnabled || hasUsablePasskey) return false
        val last = maxOf(skipped, state.reminderDismissedAt ?: skipped)
        return Duration.between(last, now) >= REMIND_INTERVAL
    }
}

/**
 * What this device remembers about the passkey suggestion of one backend
 */
data class PasskeyPromptState(
    val skippedAt: Instant? = null,
    val reminderDismissedAt: Instant? = null,
    val registeredHere: Set<String> = emptySet(),
)
