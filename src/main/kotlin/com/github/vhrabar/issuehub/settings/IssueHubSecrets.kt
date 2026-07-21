package com.github.vhrabar.issuehub.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe

/**
 * Stores per-provider tokens in the IDE's secure credential store
 */
object IssueHubSecrets {

    private fun attributes(providerId: String): CredentialAttributes =
        CredentialAttributes(generateServiceName("IssueHub", "$providerId-token"))

    fun getToken(providerId: String): String? =
        PasswordSafe.instance.getPassword(attributes(providerId))?.takeIf { it.isNotBlank() }

    fun setToken(providerId: String, token: String?) {
        PasswordSafe.instance.setPassword(attributes(providerId), token?.takeIf { it.isNotBlank() })
    }
}
