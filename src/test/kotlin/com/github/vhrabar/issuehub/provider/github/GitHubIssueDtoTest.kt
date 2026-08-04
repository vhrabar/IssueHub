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
