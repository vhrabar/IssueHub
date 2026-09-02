package com.github.vhrabar.issuehub.provider

/**
 * An account the IDE is already signed in with, offered so the user doesn't have to create another
 * token by hand for something they're logged into anyway.
 *
 * The token is read while the list is built, so only build the list when the user asks for it.
 */
data class ImportableAccount(
    val login: String,
    val serverUrl: String,
    val token: String,
)
