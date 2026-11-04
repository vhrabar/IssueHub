package com.github.vhrabar.issuehub.provider

/**
 * The tracker couldn't be reached at all: the request never got an HTTP answer.
 *
 * Kept apart from other failures so the UI can offer a retry instead of a raw socket error, and
 * apart from [AuthenticationRequiredException] and [RateLimitedException] because neither the
 * token nor the allowance is at fault; the same request may well succeed a moment later.
 */
class NetworkUnavailableException(
    val kind: Kind,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Kind {
        /** No connection to the server: no network, the host name didn't resolve, or the connection was refused or dropped. */
        OFFLINE,

        /** A connection was made or attempted, but the server didn't answer in time. */
        TIMEOUT,
    }
}
