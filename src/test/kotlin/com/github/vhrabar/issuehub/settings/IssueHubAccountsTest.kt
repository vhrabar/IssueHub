package com.github.vhrabar.issuehub.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class IssueHubAccountsTest : BasePlatformTestCase() {
    private val accounts get() = IssueHubAccounts.getInstance()

    override fun tearDown() {
        try {
            accounts.accounts.forEach(accounts::remove)
            PasswordSafe.instance.setPassword(legacyAttributes(), null)
        } finally {
            super.tearDown()
        }
    }

    fun testAnAccountKeepsItsTokenUnderItsOwnId() {
        val account = accounts.add("github", SERVER, "octocat", "t0ken")

        assertEquals(listOf(account), accounts.accountsFor("github"))
        assertEquals("t0ken", accounts.token(account))
        assertEquals(account, accounts.defaultAccountFor("github"))
    }

    /** Two accounts on two servers is the case account-per-provider storage couldn't hold. */
    fun testAccountsOnDifferentServersDoNotDisplaceEachOther() {
        val hosted = accounts.add("github", SERVER, "octocat", "hosted")
        val enterprise = accounts.add("github", "https://github.corp.test/api/v3", "employee", "enterprise")

        assertEquals("hosted", accounts.token(hosted))
        assertEquals("enterprise", accounts.token(enterprise))
        assertEquals(2, accounts.accountsFor("github").size)
        // Providers work through the first until there is a way to choose per project.
        assertEquals(hosted, accounts.defaultAccountFor("github"))
    }

    fun testRemovingAnAccountTakesItsTokenWithIt() {
        val account = accounts.add("github", SERVER, "octocat", "t0ken")

        accounts.remove(account)

        assertEmpty(accounts.accountsFor("github"))
        assertNull(accounts.token(account))
    }

    fun testAReCheckedLoginReplacesTheOldOneWithoutTouchingTheToken() {
        val account = accounts.add("github", SERVER, "", "t0ken")

        accounts.update(account.copy(login = "octocat"))

        assertEquals("octocat", accounts.defaultAccountFor("github")?.login)
        assertEquals("t0ken", accounts.token(account))
    }

    /** An upgrade must not sign the user out: the token they pasted before accounts existed still works. */
    fun testATokenStoredBeforeAccountsBecomesOne() {
        PasswordSafe.instance.setPassword(legacyAttributes(), "legacy")

        val adopted = accounts.adoptLegacyToken("github", SERVER)

        assertNotNull(adopted)
        assertEquals("legacy", accounts.token(adopted!!))
        assertEquals(SERVER, adopted.serverUrl)
        // Unverified: the login is only learned by asking the server, which an upgrade shouldn't do.
        assertEquals("", adopted.login)
        assertNull(PasswordSafe.instance.getPassword(legacyAttributes()))
    }

    fun testTheLegacyTokenIsOnlyAdoptedOnce() {
        PasswordSafe.instance.setPassword(legacyAttributes(), "legacy")
        accounts.adoptLegacyToken("github", SERVER)

        assertNull(accounts.adoptLegacyToken("github", SERVER))
        assertEquals(1, accounts.accountsFor("github").size)
    }

    fun testNothingIsAdoptedWhenThereWasNoOldToken() {
        assertNull(accounts.adoptLegacyToken("github", SERVER))
    }

    private fun legacyAttributes() = CredentialAttributes(generateServiceName("IssueHub", "github-token"))

    private companion object {
        const val SERVER = "https://api.github.com"
    }
}
