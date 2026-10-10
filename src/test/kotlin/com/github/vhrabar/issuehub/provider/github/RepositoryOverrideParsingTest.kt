package com.github.vhrabar.issuehub.provider.github

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepositoryOverrideParsingTest {
    private val enterpriseHosts = setOf("ghe.example.com")

    @Test
    fun `owner and name alone mean github dot com`() {
        assertEquals(RepoCoordinates("octocat", "hello-world"), RepoDetector.parseRepository(" octocat/hello-world "))
    }

    @Test
    fun `a host in front names an enterprise repository`() {
        assertEquals(
            RepoCoordinates("team", "service", "ghe.example.com"),
            RepoDetector.parseRepository("ghe.example.com/team/service", enterpriseHosts),
        )
        assertEquals(RepoCoordinates("octocat", "hello-world"), RepoDetector.parseRepository("github.com/octocat/hello-world"))
    }

    @Test
    fun `pasted urls work as they do for remotes`() {
        assertEquals(
            RepoCoordinates("octocat", "hello-world"),
            RepoDetector.parseRepository("https://github.com/octocat/hello-world.git"),
        )
        assertEquals(
            RepoCoordinates("team", "service", "ghe.example.com"),
            RepoDetector.parseRepository("git@ghe.example.com:team/service.git", enterpriseHosts),
        )
    }

    @Test
    fun `incomplete or unknown repositories are refused`() {
        assertNull(RepoDetector.parseRepository("octocat"))
        assertNull(RepoDetector.parseRepository("octocat/"))
        assertNull(RepoDetector.parseRepository("a/b/c/d"))
        assertNull(RepoDetector.parseRepository("ghe.example.com/team/service"))
    }

    @Test
    fun `the host is read off every form that names one`() {
        assertEquals("ghe.example.com", RepoDetector.hostOf("ghe.example.com/team/service"))
        assertEquals("ghe.example.com", RepoDetector.hostOf("https://user@GHE.example.com:8443/team/service"))
        assertEquals("ghe.example.com", RepoDetector.hostOf("git@ghe.example.com:team/service.git"))
        assertNull(RepoDetector.hostOf("octocat/hello-world"))
    }
}
