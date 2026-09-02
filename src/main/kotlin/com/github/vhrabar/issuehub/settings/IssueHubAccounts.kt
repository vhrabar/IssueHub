@file:Suppress("UnstableApiUsage")

package com.github.vhrabar.issuehub.settings

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import java.util.UUID

/** Serialized form of an account. Tokens are not in here, they stay in the credential store. */
class AccountEntry : BaseState() {
    var id by string()
    var providerId by string()
    var serverUrl by string()
    var login by string()
}

class AccountsState : BaseState() {
    val accounts by list<AccountEntry>()

    fun changed() = incrementModificationCount()
}

/**
 * Every account IssueHub knows about, for all providers.
 *
 * Application-level, like the IDE's own VCS accounts: a token belongs to a user and a server, not
 * to whatever project happens to be open. Choosing which account a project uses is a separate
 * problem; for now it's always the first one registered for the provider.
 *
 * Nothing in here is GitHub specific. Providers pass their own id and server URL, so adding GitLab
 * or Jira needs no changes to this class.
 */
@Service(Service.Level.APP)
@State(name = "IssueHubAccounts", storages = [Storage("issuehub.xml")])
class IssueHubAccounts : SimplePersistentStateComponent<AccountsState>(AccountsState()) {
    val accounts: List<IssueHubAccount>
        get() = state.accounts.mapNotNull { it.toAccount() }

    fun accountsFor(providerId: String): List<IssueHubAccount> = accounts.filter { it.providerId == providerId }

    /** The account a provider should use. Per-project selection isn't implemented yet. */
    fun defaultAccountFor(providerId: String): IssueHubAccount? = accountsFor(providerId).firstOrNull()

    fun token(account: IssueHubAccount): String? = IssueHubSecrets.getToken(account.id)

    /** Saves [token] and returns the account it was stored under, with its generated id. */
    fun add(
        providerId: String,
        serverUrl: String,
        login: String,
        token: String,
    ): IssueHubAccount {
        val account = IssueHubAccount(UUID.randomUUID().toString(), providerId, serverUrl, login)
        state.accounts.add(account.toEntry())
        state.changed()
        IssueHubSecrets.setToken(account.id, token)
        return account
    }

    /** Updates an account (re-checked login, moved server) and leaves its token where it is. */
    fun update(account: IssueHubAccount) {
        val entry = state.accounts.firstOrNull { it.id == account.id } ?: return
        entry.providerId = account.providerId
        entry.serverUrl = account.serverUrl
        entry.login = account.login
        state.changed()
    }

    fun remove(account: IssueHubAccount) {
        state.accounts.removeAll { it.id == account.id }
        state.changed()
        IssueHubSecrets.setToken(account.id, null)
    }

    /**
     * Picks up a token stored before accounts existed, so upgrading the plugin doesn't silently
     * sign the user out.
     *
     * The provider passes its own server URL because this class doesn't know where any provider
     * points. The account stays unverified: getting the login means a request, and an upgrade is
     * not the place to make one.
     */
    fun adoptLegacyToken(
        providerId: String,
        serverUrl: String,
    ): IssueHubAccount? {
        if (accountsFor(providerId).isNotEmpty()) return null
        val legacy = IssueHubSecrets.getLegacyToken(providerId) ?: return null
        val account = add(providerId, serverUrl, login = "", token = legacy)
        IssueHubSecrets.clearLegacyToken(providerId)
        return account
    }

    private fun AccountEntry.toAccount(): IssueHubAccount? =
        IssueHubAccount(
            id = id ?: return null,
            providerId = providerId ?: return null,
            serverUrl = serverUrl ?: return null,
            login = login.orEmpty(),
        )

    private fun IssueHubAccount.toEntry(): AccountEntry =
        AccountEntry().also {
            it.id = id
            it.providerId = providerId
            it.serverUrl = serverUrl
            it.login = login
        }

    companion object {
        fun getInstance(): IssueHubAccounts = service()
    }
}
