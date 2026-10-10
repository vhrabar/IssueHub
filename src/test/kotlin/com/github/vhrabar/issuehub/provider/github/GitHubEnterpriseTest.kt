package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.settings.IssueHubAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubEnterpriseTest {
    private val enterpriseHosts = setOf("ghe.example.com")

    @Test
    fun `an enterprise remote is recognised once its host has an account`() {
        val expected = RepoCoordinates("team", "service", "ghe.example.com")
        assertEquals(expected, RepoDetector.parseGitHubUrl("git@ghe.example.com:team/service.git", enterpriseHosts))
        assertEquals(expected, RepoDetector.parseGitHubUrl("https://ghe.example.com/team/service.git", enterpriseHosts))
        assertEquals(expected, RepoDetector.parseGitHubUrl("ssh://git@ghe.example.com:2222/team/service.git", enterpriseHosts))
        assertEquals(expected, RepoDetector.parseGitHubUrl("https://user@GHE.example.com/team/service", enterpriseHosts))
    }

    @Test
    fun `an enterprise remote without an account is not taken for github`() {
        assertNull(RepoDetector.parseGitHubUrl("git@ghe.example.com:team/service.git"))
    }

    @Test
    fun `github dot com is recognised whatever accounts exist`() {
        assertEquals(
            RepoCoordinates("octocat", "hello-world"),
            RepoDetector.parseGitHubUrl("ssh://git@github.com/octocat/hello-world.git", enterpriseHosts),
        )
    }

    @Test
    fun `local paths are not remotes`() {
        assertNull(RepoDetector.parseGitHubUrl("/srv/git/github.com:team/service.git", enterpriseHosts))
        assertNull(RepoDetector.parseGitHubUrl("../service", enterpriseHosts))
    }

    @Test
    fun `server urls map to the host a remote names`() {
        assertEquals("github.com", GitHubIssueProvider.webHost("https://api.github.com"))
        assertEquals("ghe.example.com", GitHubIssueProvider.webHost("https://ghe.example.com/api/v3"))
        assertEquals("ghe.example.com", GitHubIssueProvider.webHost("https://ghe.example.com/api/v3/"))
    }

    @Test
    fun `each repository gets the account on its own server`() {
        val cloud = account("cloud", "https://api.github.com")
        val enterprise = account("work", "https://ghe.example.com/api/v3")
        val accounts = listOf(enterprise, cloud)

        assertEquals(cloud, GitHubIssueProvider.accountFor(RepoCoordinates("octocat", "hello-world"), accounts))
        assertEquals(enterprise, GitHubIssueProvider.accountFor(RepoCoordinates("team", "service", "ghe.example.com"), accounts))
    }

    @Test
    fun `a github dot com repository never borrows an enterprise token`() {
        val enterprise = account("work", "https://ghe.example.com/api/v3")
        assertNull(GitHubIssueProvider.accountFor(RepoCoordinates("octocat", "hello-world"), listOf(enterprise)))
    }

    private fun account(
        id: String,
        serverUrl: String,
    ) = IssueHubAccount(id, GitHubIssueProvider.PROVIDER_IDENTIFIER, serverUrl, login = id)
}
