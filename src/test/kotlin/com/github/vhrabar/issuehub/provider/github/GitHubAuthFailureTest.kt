package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.provider.AuthenticationRequiredException.Reason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubAuthFailureTest {
    private fun failure(status: Int?) = GitHubApiException("GitHub said no ($status).", status)

    @Test
    fun `a refused token asks for the account to be fixed`() {
        val auth = failure(401).asAuthenticationFailure(hasToken = true)
        assertEquals(Reason.REJECTED, auth?.reason)
        assertEquals("GitHub said no (401).", auth?.message)
    }

    @Test
    fun `a token without access is not a bad token`() {
        assertNull(failure(403).asAuthenticationFailure(hasToken = true))
        assertNull(failure(404).asAuthenticationFailure(hasToken = true))
    }

    @Test
    fun `anonymous refusals ask for an account`() {
        listOf(401, 403, 404).forEach { status ->
            assertEquals(Reason.MISSING, failure(status).asAuthenticationFailure(hasToken = false)?.reason)
        }
    }

    @Test
    fun `server errors and failures without a status stay ordinary errors`() {
        assertNull(failure(500).asAuthenticationFailure(hasToken = false))
        assertNull(failure(500).asAuthenticationFailure(hasToken = true))
        assertNull(failure(null).asAuthenticationFailure(hasToken = false))
    }
}
