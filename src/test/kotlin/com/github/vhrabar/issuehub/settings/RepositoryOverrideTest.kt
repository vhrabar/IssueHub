package com.github.vhrabar.issuehub.settings

import com.github.vhrabar.issuehub.provider.github.GitHubIssueProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** The light test project has no git remote, so whatever the provider finds came from the override. */
class RepositoryOverrideTest : BasePlatformTestCase() {
    private val settings get() = IssueHubProjectSettings.getInstance(project)
    private val accounts get() = IssueHubAccounts.getInstance()
    private val provider = GitHubIssueProvider()

    override fun tearDown() {
        try {
            settings.repositoryOverride = null
            accounts.accounts.forEach(accounts::remove)
        } finally {
            super.tearDown()
        }
    }

    fun testBlankMeansDetection() {
        settings.repositoryOverride = "   "
        assertNull(settings.repositoryOverride)
        assertFalse(provider.isApplicable(project))
    }

    fun testANamedRepositoryIsUsedInsteadOfTheRemote() {
        settings.repositoryOverride = " octocat/hello-world "
        assertTrue(provider.isApplicable(project))
        assertEquals("octocat/hello-world", provider.sourceLabel(project))
    }

    fun testAnEnterpriseRepositoryNeedsAnAccountOnItsServer() {
        val repository = "ghe.example.com/team/service"
        assertTrue(provider.checkRepository(repository)!!.contains("ghe.example.com"))

        accounts.add(GitHubIssueProvider.PROVIDER_IDENTIFIER, "https://ghe.example.com/api/v3", "employee", "t0ken")
        assertNull(provider.checkRepository(repository))

        settings.repositoryOverride = repository
        assertEquals("team/service", provider.sourceLabel(project))
    }

    fun testAnOverrideThatNoLongerParsesFallsBackToDetection() {
        settings.repositoryOverride = "ghe.example.com/team/service"
        assertFalse(provider.isApplicable(project))
    }

    fun testNonsenseIsExplained() {
        assertNotNull(provider.checkRepository("just-a-name"))
    }
}
