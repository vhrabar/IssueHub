package com.github.vhrabar.issuehub.provider

/**
 * The tracker wouldn't serve the request until the user sorts out an account.
 *
 * Kept apart from other failures so the UI can offer the accounts page instead of a raw error;
 * there is nothing to retry until the credentials change.
 */
class AuthenticationRequiredException(
    val reason: Reason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    enum class Reason {
        /** No account is set up, and the tracker won't answer anonymously. */
        MISSING,

        /** An account is set up, but the tracker refused its token: expired, revoked, or mistyped. */
        REJECTED,
    }
}
