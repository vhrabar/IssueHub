package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.provider.NetworkUnavailableException.Kind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.net.http.HttpConnectTimeoutException
import java.net.http.HttpTimeoutException
import java.nio.channels.UnresolvedAddressException

class GitHubNetworkFailureTest {
    @Test
    fun `a request timeout is a timeout`() {
        assertEquals(Kind.TIMEOUT, classifyNetworkFailure(HttpTimeoutException("request timed out"), "api.github.com").kind)
    }

    @Test
    fun `a connect timeout is a timeout`() {
        assertEquals(Kind.TIMEOUT, classifyNetworkFailure(HttpConnectTimeoutException("connect timed out"), "api.github.com").kind)
    }

    @Test
    fun `a refused connection is offline`() {
        assertEquals(Kind.OFFLINE, classifyNetworkFailure(ConnectException("Connection refused"), "api.github.com").kind)
    }

    /** The JDK client wraps an unresolvable host in a ConnectException with no message of its own. */
    @Test
    fun `an unresolvable host is offline`() {
        val wrapped = ConnectException().apply { initCause(UnresolvedAddressException()) }
        assertEquals(Kind.OFFLINE, classifyNetworkFailure(wrapped, "github.example.com").kind)
        assertEquals(Kind.OFFLINE, classifyNetworkFailure(UnknownHostException("github.example.com"), "github.example.com").kind)
    }

    @Test
    fun `any other io failure is offline`() {
        assertEquals(Kind.OFFLINE, classifyNetworkFailure(IOException("Connection reset"), "api.github.com").kind)
    }

    @Test
    fun `the message names the server and the cause is kept`() {
        val cause = ConnectException("Connection refused")
        val failure = classifyNetworkFailure(cause, "github.example.com")
        assertTrue(failure.message!!.contains("github.example.com"))
        assertSame(cause, failure.cause)
    }

    @Test
    fun `without a host the message names github`() {
        assertTrue(classifyNetworkFailure(IOException(), null).message!!.contains("GitHub"))
    }

    @Test
    fun `gateway and availability errors are retried`() {
        listOf(502, 503, 504).forEach { assertTrue("$it", isTransient(it)) }
    }

    /** Rate limits, bad tokens and missing repositories won't change by asking again. */
    @Test
    fun `client errors and other server errors are not retried`() {
        listOf(200, 400, 401, 403, 404, 422, 429, 500).forEach { assertFalse("$it", isTransient(it)) }
    }

    @Test
    fun `a timeout is retried but being offline is not`() {
        assertTrue(isTransient(null, classifyNetworkFailure(HttpTimeoutException("timed out"), "api.github.com")))
        assertFalse(isTransient(null, classifyNetworkFailure(ConnectException("Connection refused"), "api.github.com")))
    }
}
