package de.markusressel.mkdocseditor.auth.domain

import android.accounts.Account
import android.accounts.AccountManager
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GetAccountsUseCase @Inject constructor(
    private val accountManager: AccountManager
) {
    operator fun invoke(): List<Account> {
        return accountManager.getAccountsByType("com.google").asList()
    }
}