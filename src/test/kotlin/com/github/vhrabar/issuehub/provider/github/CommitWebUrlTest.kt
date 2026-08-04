package com.github.vhrabar.issuehub.provider.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CommitWebUrlTest {
    @Test
    fun `the api host and plural path become the browsable commit page`() {
        assertEquals(
            "https://github.com/octocat/hello-world/commit/0123456789abcdef",
            commitWebUrl("https://api.github.com/repos/octocat/hello-world/commits/0123456789abcdef"),
        )
    }

    /** A commit referencing an issue often lives in a fork, so the repo comes from the URL itself. */
    @Test
    fun `the owner and name are taken from the address, not assumed`() {
        assertEquals(
            "https://github.com/contributor/hello-world/commit/abc123",
            commitWebUrl("https://api.github.com/repos/contributor/hello-world/commits/abc123"),
        )
    }

    /** Enterprise installs serve the API from a path rather than an `api.` host. */
    @Test
    fun `an enterprise api path is stripped`() {
        assertEquals(
            "https://ghe.example.com/octocat/hello-world/commit/abc123",
            commitWebUrl("https://ghe.example.com/api/v3/repos/octocat/hello-world/commits/abc123"),
        )
    }

    @Test
    fun `an address we don't recognise yields no link at all`() {
        assertNull(commitWebUrl("https://api.github.com/octocat/hello-world/commits/abc123"))
        assertNull(commitWebUrl("https://api.github.com/repos/octocat/hello-world/pulls/21"))
        assertNull(commitWebUrl(""))
    }
}
