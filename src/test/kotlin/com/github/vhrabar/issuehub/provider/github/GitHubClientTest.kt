package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueStateFilter
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class GitHubClientTest {
    private lateinit var server: HttpServer
    private lateinit var client: GitHubClient

    /** Records what the last request looked like. */
    private var lastPath: String? = null
    private var lastAuthHeader: String? = null
    private var lastApiVersionHeader: String? = null

    private var responseStatus = 200
    private var responseBody = "[]"
    private var responseLink: String? = null

    private val repo = RepoCoordinates("octocat", "hello-world")

    @Before
    fun setUp() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/") { exchange ->
            lastPath = exchange.requestURI.toString()
            lastAuthHeader = exchange.requestHeaders.getFirst("Authorization")
            lastApiVersionHeader = exchange.requestHeaders.getFirst("X-GitHub-Api-Version")
            responseLink?.let { exchange.responseHeaders.add("Link", it) }
            respond(exchange, responseStatus, responseBody)
        }
        server.start()
        client = GitHubClient(baseUrl = "http://127.0.0.1:${server.address.port}")
    }

    @After
    fun tearDown() {
        server.stop(0)
    }

    private fun respond(
        exchange: HttpExchange,
        status: Int,
        body: String,
    ) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        exchange.sendResponseHeaders(status, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun issueJson(
        number: Int,
        isPr: Boolean = false,
    ): String {
        val prField = if (isPr) """, "pull_request": {"url": "http://x"}""" else ""
        return """
            {
              "number": $number,
              "title": "Issue $number",
              "state": "open",
              "html_url": "https://github.com/octocat/hello-world/issues/$number",
              "created_at": "2026-07-19T10:00:00Z",
              "updated_at": "2026-07-20T10:00:00Z"$prField
            }
            """.trimIndent()
    }

    @Test
    fun `filters out pull requests`() {
        responseBody = "[${issueJson(1)}, ${issueJson(2, isPr = true)}, ${issueJson(3)}]"

        val result = runBlocking { client.fetchIssues(repo, token = null, query = IssueQuery()) }

        assertEquals(listOf(1, 3), result.issues.map { it.number })
    }

    /** A page that lost its pull requests is short, but GitHub still says another one follows. */
    @Test
    fun `reports a next page from the link header even when pull requests made the page short`() {
        responseBody = "[${issueJson(1)}, ${issueJson(2, isPr = true)}]"
        responseLink =
            "<https://api.github.com/repositories/1/issues?page=2>; rel=\"next\", " +
            "<https://api.github.com/repositories/1/issues?page=4>; rel=\"last\""

        val result = runBlocking { client.fetchIssues(repo, token = null, query = IssueQuery(limit = 2)) }

        assertEquals(listOf(1), result.issues.map { it.number })
        assertTrue(result.hasNext)
    }

    @Test
    fun `the last page has no next`() {
        responseBody = "[${issueJson(1)}]"
        responseLink = "<https://api.github.com/repositories/1/issues?page=1>; rel=\"first\""

        val result = runBlocking { client.fetchIssues(repo, token = null, query = IssueQuery(page = 2)) }

        assertFalse(result.hasNext)
        assertTrue(lastPath!!, lastPath!!.contains("page=2"))
    }

    @Test
    fun `search results carry their total and the page asked for`() {
        responseBody = """{"total_count": 120, "items": [${issueJson(7)}]}"""
        responseLink = "<https://api.github.com/search/issues?q=x&page=3>; rel=\"next\""

        val result = runBlocking { client.fetchIssues(repo, token = null, query = IssueQuery(text = "crash", page = 2)) }

        assertEquals(listOf(7), result.issues.map { it.number })
        assertEquals(120, result.totalCount)
        assertTrue(result.hasNext)
        assertTrue(lastPath!!, lastPath!!.startsWith("/search/issues") && lastPath!!.contains("&page=2"))
    }

    @Test
    fun `builds request path with repo, state and per_page`() {
        runBlocking { client.fetchIssues(repo, token = null, query = IssueQuery(state = IssueStateFilter.CLOSED, limit = 25)) }

        val path = requireNotNull(lastPath) { "no request captured" }
        assertTrue(path, path.startsWith("/repos/octocat/hello-world/issues"))
        assertTrue(path, path.contains("state=closed"))
        assertTrue(path, path.contains("per_page=25"))
        assertEquals("2026-03-10", lastApiVersionHeader)
    }

    @Test
    fun `sends authorization header when token present`() {
        runBlocking { client.fetchIssues(repo, token = "secret-token", query = IssueQuery()) }
        assertEquals("Bearer secret-token", lastAuthHeader)
    }

    @Test
    fun `omits authorization header when token is null or blank`() {
        runBlocking { client.fetchIssues(repo, token = null, query = IssueQuery()) }
        assertNull(lastAuthHeader)

        runBlocking { client.fetchIssues(repo, token = "  ", query = IssueQuery()) }
        assertNull(lastAuthHeader)
    }

    @Test
    fun `maps error status codes to descriptive exceptions`() {
        assertErrorContains(401, "401")
        assertErrorContains(403, "403")
        assertErrorContains(404, "404")
        assertErrorContains(500, "500")
    }

    private fun assertErrorContains(
        status: Int,
        expectedFragment: String,
    ) {
        responseStatus = status
        responseBody = "{}"
        try {
            runBlocking { client.fetchIssues(repo, token = null, query = IssueQuery()) }
            fail("Expected GitHubApiException for status $status")
        } catch (e: GitHubApiException) {
            assertTrue(e.message, e.message?.contains(expectedFragment) == true)
            assertEquals(status, e.status)
        } finally {
            responseStatus = 200
            responseBody = "[]"
        }
    }
}
