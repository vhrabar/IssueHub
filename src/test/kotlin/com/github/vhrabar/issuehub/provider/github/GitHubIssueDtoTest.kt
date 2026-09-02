package com.github.vhrabar.issuehub.provider.github

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubIssueDtoTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `the full media type carries the rendered body alongside the source`() {
        val dto = json.decodeFromString<GitHubIssueDto>(issueJson(""""body_html": "<p>Renders <em>this</em>.</p>","""))

        assertEquals("Renders *this*.", dto.body)
        assertEquals("<p>Renders <em>this</em>.</p>", dto.bodyHtml)
    }

    /** List responses use the default media type, so rows decode with no rendered body at all. */
    @Test
    fun `a response without the rendered body still decodes`() {
        val dto = json.decodeFromString<GitHubIssueDto>(issueJson(""))

        assertEquals("Renders *this*.", dto.body)
        assertNull(dto.bodyHtml)
    }

    @Test
    fun `every assignee is kept, not just the first`() {
        val issue =
            json
                .decodeFromString<GitHubIssueDto>(
                    issueJson("""  "assignees": [{"login": "mia"}, {"login": "adam"}],"""),
                ).toIssue()

        assertEquals(listOf("mia", "adam"), issue.assignees.map { it.login })
        assertEquals("mia", issue.assignee?.login)
    }

    /** Older payloads name only the single `assignee`, which is still someone the issue is on. */
    @Test
    fun `a lone assignee field stands in for the list`() {
        val issue = json.decodeFromString<GitHubIssueDto>(issueJson("""  "assignee": {"login": "mia"},""")).toIssue()

        assertEquals(listOf("mia"), issue.assignees.map { it.login })
    }

    @Test
    fun `an issue nobody is on has no assignees`() {
        assertEquals(emptyList<String>(), json.decodeFromString<GitHubIssueDto>(issueJson("")).toIssue().assignees)
    }

    private fun issueJson(bodyHtml: String) =
        """
        {
          "number": 17,
          "title": "Filter issues",
          "state": "open",
          "body": "Renders *this*.",
          $bodyHtml
          "html_url": "https://github.test/octocat/hello-world/issues/17",
          "created_at": "2026-07-01T00:00:00Z",
          "updated_at": "2026-07-02T00:00:00Z"
        }
        """.trimIndent()
}
