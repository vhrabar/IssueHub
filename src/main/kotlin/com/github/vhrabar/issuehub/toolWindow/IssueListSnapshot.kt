package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.provider.AuthenticationRequiredException
import com.github.vhrabar.issuehub.provider.NetworkUnavailableException
import com.github.vhrabar.issuehub.provider.RateLimitedException
import java.time.Duration
import java.time.Instant

/**
 * The last list the provider served, with every page loaded for it so far.
 *
 * Held in memory only: issue text from a private repository has no business being left on disk.
 */
internal data class IssueListSnapshot(
    /** The repository it came from, since a settings page can change that under an unchanged query. */
    val source: String?,
    /** The query it answers, with [IssueQuery.page] at the last page loaded. */
    val query: IssueQuery,
    val issues: List<Issue>,
    val nextPage: Int?,
    val totalCount: Int?,
    val fetchedAt: Instant,
) {
    fun matches(
        source: String?,
        query: IssueQuery,
    ): Boolean = this.source == source && this.query.copy(page = 1) == query.copy(page = 1)
}

/**
 * Whether a list already on screen should survive a failed reload, with a notice above it.
 *
 * Being offline or rate limited says nothing about the rows, so they stay. A rejected token does,
 * since the rows may be ones the user can no longer read. Any other failure is shown in their place
 * when the user asked for the reload, but isn't worth losing the list over when nobody did.
 */
internal fun keepsRowsOn(
    error: Throwable,
    background: Boolean,
): Boolean =
    when (error) {
        is AuthenticationRequiredException -> false
        is NetworkUnavailableException, is RateLimitedException -> true
        else -> background
    }

/**
 * Whether a timer tick should reload the list.
 *
 * Not while nobody can see it or something is already loading, and not before a rate limit lifts,
 * since asking early only gets refused again. Nor once the user has loaded further pages: reloading
 * reads the first page only, so it would take the rest away from under them.
 */
internal fun shouldAutoRefresh(
    intervalMinutes: Int,
    lastAttemptAt: Instant?,
    now: Instant,
    rateLimitedUntil: Instant?,
    visible: Boolean,
    busy: Boolean,
    pagesLoaded: Int,
): Boolean {
    if (intervalMinutes <= 0 || !visible || busy || pagesLoaded > 1) return false
    if (rateLimitedUntil != null && now.isBefore(rateLimitedUntil)) return false
    return lastAttemptAt == null || Duration.between(lastAttemptAt, now) >= Duration.ofMinutes(intervalMinutes.toLong())
}
