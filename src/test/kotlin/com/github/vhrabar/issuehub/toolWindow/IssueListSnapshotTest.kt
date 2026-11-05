package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueStateFilter
import com.github.vhrabar.issuehub.provider.AuthenticationRequiredException
import com.github.vhrabar.issuehub.provider.NetworkUnavailableException
import com.github.vhrabar.issuehub.provider.RateLimitedException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class IssueListSnapshotTest {
    private val now = Instant.parse("2026-11-05T12:00:00Z")

    private fun snapshot(query: IssueQuery = IssueQuery()) = IssueListSnapshot("octocat/hello", query, emptyList(), null, null, now)

    private fun due(
        intervalMinutes: Int = 5,
        lastAttemptAt: Instant? = now.minusSeconds(300),
        rateLimitedUntil: Instant? = null,
        visible: Boolean = true,
        busy: Boolean = false,
        pagesLoaded: Int = 1,
    ) = shouldAutoRefresh(intervalMinutes, lastAttemptAt, now, rateLimitedUntil, visible, busy, pagesLoaded)

    @Test
    fun `a snapshot answers its query however many pages it holds`() {
        assertTrue(snapshot(IssueQuery(page = 3)).matches("octocat/hello", IssueQuery()))
    }

    @Test
    fun `a snapshot does not answer another query or repository`() {
        assertFalse(snapshot().matches("octocat/hello", IssueQuery(state = IssueStateFilter.ALL)))
        assertFalse(snapshot().matches("octocat/fork", IssueQuery()))
    }

    @Test
    fun `rows survive being offline or rate limited whoever asked`() {
        val offline = NetworkUnavailableException(NetworkUnavailableException.Kind.OFFLINE, "api.github.com", "offline")
        val limited = RateLimitedException(now, authenticated = true, message = "limited")
        listOf(true, false).forEach { background ->
            assertTrue(keepsRowsOn(offline, background))
            assertTrue(keepsRowsOn(limited, background))
        }
    }

    @Test
    fun `rows never survive a rejected token`() {
        val rejected = AuthenticationRequiredException(AuthenticationRequiredException.Reason.REJECTED, "rejected")
        assertFalse(keepsRowsOn(rejected, background = true))
        assertFalse(keepsRowsOn(rejected, background = false))
    }

    @Test
    fun `other failures only spare the rows when nobody asked for the reload`() {
        assertTrue(keepsRowsOn(IllegalStateException("500"), background = true))
        assertFalse(keepsRowsOn(IllegalStateException("500"), background = false))
    }

    @Test
    fun `a reload is due once the interval has passed`() {
        assertTrue(due())
        assertTrue(due(lastAttemptAt = null))
        assertFalse(due(lastAttemptAt = now.minusSeconds(299)))
    }

    @Test
    fun `no reload while off, hidden, busy or with further pages loaded`() {
        assertFalse(due(intervalMinutes = 0))
        assertFalse(due(visible = false))
        assertFalse(due(busy = true))
        assertFalse(due(pagesLoaded = 2))
    }

    @Test
    fun `no reload until a rate limit lifts`() {
        assertFalse(due(rateLimitedUntil = now.plusSeconds(1)))
        assertTrue(due(rateLimitedUntil = now))
    }
}
