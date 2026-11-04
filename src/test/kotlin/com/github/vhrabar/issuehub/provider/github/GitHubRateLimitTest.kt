package com.github.vhrabar.issuehub.provider.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class GitHubRateLimitTest {
    private val now = Instant.parse("2026-10-26T12:00:00Z")

    private fun resetAt(
        status: Int,
        headers: Map<String, String> = emptyMap(),
        body: String = "{}",
    ) = rateLimitResetAt(status, headers::get, body, now)

    @Test
    fun `an exhausted allowance resets when github says`() {
        assertEquals(
            Instant.ofEpochSecond(1_792_000_000),
            resetAt(403, mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1792000000")),
        )
        assertEquals(
            Instant.ofEpochSecond(1_792_000_000),
            resetAt(429, mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1792000000")),
        )
    }

    /** Secondary limits name a delay rather than a time, and it wins over the primary reset. */
    @Test
    fun `retry-after counts from now`() {
        assertEquals(
            now.plusSeconds(30),
            resetAt(403, mapOf("Retry-After" to "30", "X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1792000000")),
        )
    }

    @Test
    fun `a limit with no time given waits a minute`() {
        assertEquals(now.plusSeconds(60), resetAt(429))
        assertEquals(
            now.plusSeconds(60),
            resetAt(403, body = """{"message": "You have exceeded a secondary rate limit."}"""),
        )
        assertEquals(now.plusSeconds(60), resetAt(403, mapOf("X-RateLimit-Remaining" to "0")))
    }

    /** A token without access to the repository is not something waiting fixes. */
    @Test
    fun `a plain 403 is not a rate limit`() {
        assertNull(resetAt(403, mapOf("X-RateLimit-Remaining" to "4999", "X-RateLimit-Reset" to "1792000000")))
        assertNull(resetAt(403, body = """{"message": "Resource not accessible by personal access token"}"""))
    }

    @Test
    fun `other statuses are never rate limits`() {
        assertNull(resetAt(401, mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "1792000000")))
        assertNull(resetAt(404, body = "rate limit"))
        assertNull(resetAt(500, mapOf("Retry-After" to "30")))
    }

    @Test
    fun `a rate limit is reported as one, with or without a token`() {
        val error = GitHubApiException("GitHub rate limit exceeded (403).", 403, now)

        val withToken = error.asRateLimit(hasToken = true)
        assertEquals(now, withToken?.resetAt)
        assertTrue(withToken!!.authenticated)
        assertFalse(error.asRateLimit(hasToken = false)!!.authenticated)
    }

    /** An anonymous 403 used to ask for an account; when it is a rate limit, the reset time says more. */
    @Test
    fun `a rate limit is not an authentication failure`() {
        val error = GitHubApiException("GitHub rate limit exceeded (403).", 403, now)
        assertNull(error.asAuthenticationFailure(hasToken = false))
        assertNull(error.asAuthenticationFailure(hasToken = true))
    }

    @Test
    fun `other failures are not rate limits`() {
        assertNull(GitHubApiException("Access forbidden (403).", 403).asRateLimit(hasToken = true))
    }
}
