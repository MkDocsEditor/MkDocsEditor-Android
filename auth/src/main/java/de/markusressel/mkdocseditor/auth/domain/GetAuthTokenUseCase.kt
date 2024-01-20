package de.markusressel.mkdocseditor.auth.domain

import android.accounts.Account
import android.accounts.AccountManager
import android.accounts.AccountManagerCallback
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@Singleton
class GetAuthTokenUseCase @Inject constructor(
    private val accountManager: AccountManager
) {
    suspend operator fun invoke(activity: Activity, account: Account): String? {
        return suspendCoroutine {
            val options = Bundle()

            val onTokenAcquired = AccountManagerCallback<Bundle> { result ->
                // Get the result of the operation from the AccountManagerFuture.
                val bundle: Bundle = result.result

                val launch: Intent? = result.result.get(AccountManager.KEY_INTENT) as? Intent
                if (launch != null) {
                    activity.startActivityForResult(launch, 0)
                    it.resume(null)
                } else {
                    // The token is a named value in the bundle. The name of the value
                    // is stored in the constant AccountManager.KEY_AUTHTOKEN.
                    val token: String? = bundle.getString(AccountManager.KEY_AUTHTOKEN)

                    //            Your first request for an auth token might fail, for several reasons:
                    //
                    //            An error in the device or network caused AccountManager to fail.
                    //            The user decided not to grant your app access to the account.
                    //            The stored account credentials aren't sufficient to gain access to the account.
                    //            The cached auth token has expired.

                    it.resume(token)
                }
            }

            accountManager.getAuthToken(
                account,                     // Account retrieved using getAccountsByType()
                "Manage your tasks",            // Auth scope
                options,                        // Authenticator-specific options
                activity,                           // Your activity
                onTokenAcquired,              // Callback called when a token is successfully acquired
                Handler(Looper.getMainLooper())              // Callback called if an error occurs
            )
        }
    }
}