package com.github.vhrabar.issuehub.settings

/**
 * A stored account: the provider it belongs to, the server it lives on, and who it is.
 *
 * [id] is the key the token is stored under and never changes. The login can change: we store an
 * account before verifying it, and someone can later paste a token belonging to a different user.
 * Keying credentials on the account rather than the login keeps the two from drifting apart.
 *
 * [serverUrl] is the API root, not the web address, so `https://api.github.com` or an Enterprise /
 * self-hosted equivalent. Providers that only ever talk to one server still fill it in, so callers
 * don't need a special case for the hosted one.
 */
data class IssueHubAccount(
    val id: String,
    val providerId: String,
    val serverUrl: String,
    /** Empty until the token has been checked against the server. */
    val login: String = "",
) {
    @Suppress("unused")
    val isVerified: Boolean get() = login.isNotBlank()
}
