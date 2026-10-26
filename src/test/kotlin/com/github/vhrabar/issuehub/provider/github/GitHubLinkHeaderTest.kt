package com.github.vhrabar.issuehub.provider.github

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubLinkHeaderTest {
    @Test
    fun `a next relation means another page follows`() {
        assertTrue(
            hasNextPage(
                "<https://api.github.com/repositories/1/issues?page=2>; rel=\"next\", " +
                    "<https://api.github.com/repositories/1/issues?page=5>; rel=\"last\"",
            ),
        )
    }

    @Test
    fun `the last page lists only first and prev`() {
        assertFalse(
            hasNextPage(
                "<https://api.github.com/repositories/1/issues?page=1>; rel=\"first\", " +
                    "<https://api.github.com/repositories/1/issues?page=4>; rel=\"prev\"",
            ),
        )
    }

    /** GitHub leaves the header out when everything fit on one page. */
    @Test
    fun `a missing header means a single page`() {
        assertFalse(hasNextPage(null))
        assertFalse(hasNextPage(""))
    }

    /** "next" inside the URL itself is not a relation. */
    @Test
    fun `only the rel parameter counts`() {
        assertFalse(hasNextPage("<https://api.github.com/next?page=2>; rel=\"prev\""))
    }

    @Test
    fun `malformed input is not a next page`() {
        assertFalse(hasNextPage("garbage"))
        assertFalse(hasNextPage("<https://api.github.com/issues?page=2>"))
    }
}
