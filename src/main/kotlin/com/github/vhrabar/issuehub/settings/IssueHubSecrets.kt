package com.github.vhrabar.issuehub.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores account tokens in the IDE's secure credential store.
 *
 * Keyed by account id rather than by provider, so one user can hold several accounts — a work
 * GitHub Enterprise host next to github.com, and later a GitLab beside both.
 */
object IssueHubSecrets {
    private fun attributes(key: String): CredentialAttributes = CredentialAttributes(generateServiceName("IssueHub", key))

    fun getToken(accountId: String): String? = PasswordSafe.instance.getPassword(attributes(accountId))?.takeIf { it.isNotBlank() }

    fun setToken(
        accountId: String,
        token: String?,
    ) {
        PasswordSafe.instance.setPassword(attributes(accountId), token?.takeIf { it.isNotBlank() })
    }

    /**
     * The single token versions before accounts kept per provider.
     *
     * Read once, when [IssueHubAccounts.adoptLegacyToken] turns it into a proper account, and
     * cleared afterwards so the old key doesn't outlive the account it became.
     */
    internal fun getLegacyToken(providerId: String): String? =
        PasswordSafe.instance.getPassword(attributes(legacyKey(providerId)))?.takeIf { it.isNotBlank() }

    internal fun clearLegacyToken(providerId: String) {
        PasswordSafe.instance.setPassword(attributes(legacyKey(providerId)), null)
    }

    private fun legacyKey(providerId: String) = "$providerId-token"
}
