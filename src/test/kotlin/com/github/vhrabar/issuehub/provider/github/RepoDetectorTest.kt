package com.github.vhrabar.issuehub.provider.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepoDetectorTest {
    @Test
    fun `parses ssh remote`() {
        assertEquals(
            RepoCoordinates("octocat", "hello-world"),
            RepoDetector.parseGitHubUrl("git@github.com:octocat/hello-world.git"),
        )
    }

    @Test
    fun `parses https remote with git suffix`() {
        assertEquals(
            RepoCoordinates("octocat", "hello-world"),
            RepoDetector.parseGitHubUrl("https://github.com/octocat/hello-world.git"),
        )
    }

    @Test
    fun `parses https remote without git suffix`() {
        assertEquals(
            RepoCoordinates("octocat", "hello-world"),
            RepoDetector.parseGitHubUrl("https://github.com/octocat/hello-world"),
        )
    }

    @Test
    fun `parses https remote with trailing slash and whitespace`() {
        assertEquals(
            RepoCoordinates("octocat", "hello-world"),
            RepoDetector.parseGitHubUrl("  https://github.com/octocat/hello-world/  "),
        )
    }

    @Test
    fun `ignores extra path segments`() {
        assertEquals(
            RepoCoordinates("octocat", "hello-world"),
            RepoDetector.parseGitHubUrl("https://github.com/octocat/hello-world/issues"),
        )
    }

    @Test
    fun `rejects non github hosts`() {
        assertNull(RepoDetector.parseGitHubUrl("git@gitlab.com:octocat/hello-world.git"))
        assertNull(RepoDetector.parseGitHubUrl("https://bitbucket.org/octocat/hello-world.git"))
    }

    @Test
    fun `rejects url without repository name`() {
        assertNull(RepoDetector.parseGitHubUrl("https://github.com/octocat"))
    }

    @Test
    fun `rejects github look-alike host`() {
        // The host boundary must be respected: github.com.evil.com is NOT github.com.
        assertNull(RepoDetector.parseGitHubUrl("https://github.com.evil.com/octocat/hello-world"))
    }
}
