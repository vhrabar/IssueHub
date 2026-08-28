package com.github.vhrabar.issuehub.provider

/**
 * Result of checking a token against a server.
 *
 * [grantedScopes] is null when the server won't say what the token can do (GitHub doesn't report
 * scopes for fine-grained tokens). An empty list is not the same thing: "has no scopes" and "no
 * idea" need different messages in the UI.
 *
 * [missingScopes] is what the provider wanted and didn't get. A missing scope breaks a feature,
 * not the connection, so we list them rather than reject the account.
 */
data class AccountVerification(
    val login: String,
    val grantedScopes: List<String>? = null,
    val missingScopes: List<String> = emptyList(),
)
