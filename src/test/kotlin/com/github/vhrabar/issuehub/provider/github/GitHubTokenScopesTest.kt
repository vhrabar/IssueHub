package com.github.vhrabar.issuehub.provider.github

import org.junit.Assert.assertEquals
import org.junit.Test

class GitHubTokenScopesTest {
    @Test
    fun `the scopes header is a comma separated list`() {
        assertEquals(listOf("repo", "read:project", "read:org"), parseScopes("repo, read:project, read:org"))
    }

    /** A classic token granted nothing still sends the header, empty; that is not the same as absent. */
    @Test
    fun `an empty header means a token with no scopes at all`() {
        assertEquals(emptyList<String>(), parseScopes(""))
    }

    @Test
    fun `the token page belongs to the site, not to the api host`() {
        assertEquals(
            "https://github.com/settings/tokens/new?scopes=repo,read:project&description=IssueHub",
            GitHubIssueProvider().tokenPageUrl(GitHubIssueProvider.DEFAULT_SERVER_URL),
        )
    }

    /** Enterprise serves its API under `/api/v3` and its pages from the host itself. */
    @Test
    fun `an enterprise api root resolves back to its own site`() {
        assertEquals("https://github.corp.test", GitHubIssueProvider.webUrl("https://github.corp.test/api/v3"))
        assertEquals("https://github.com", GitHubIssueProvider.webUrl("https://api.github.com"))
    }
}
