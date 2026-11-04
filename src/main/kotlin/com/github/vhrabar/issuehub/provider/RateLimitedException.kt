package com.github.vhrabar.issuehub.provider

import java.time.Instant

/**
 * The tracker is refusing requests until [resetAt], because too many were sent.
 *
 * Kept apart from other failures so the UI can say when to try again rather than show a raw
 * error, and apart from [AuthenticationRequiredException] because the token itself is fine.
 */
class RateLimitedException(
    /** When the tracker will answer again. */
    val resetAt: Instant,
    /** Whether the request carried a token; anonymous callers get a far smaller allowance. */
    val authenticated: Boolean,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
