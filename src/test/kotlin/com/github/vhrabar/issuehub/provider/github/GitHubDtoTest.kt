package com.github.vhrabar.issuehub.provider.github

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubDtoTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `deserializes a full issue and ignores unknown keys`() {
        val body =
            """
            {
              "number": 42,
              "title": "Something is broken",
              "state": "open",
              "body": "steps to reproduce",
              "labels": [{"name": "bug", "color": "d73a4a"}],
              "assignee": {"login": "octocat"},
              "html_url": "https://github.com/o/r/issues/42",
              "created_at": "2026-07-19T10:00:00Z",
              "updated_at": "2026-07-20T10:00:00Z",
              "unexpected_field": 123
            }
            """.trimIndent()

        val dto = json.decodeFromString<GitHubIssueDto>(body)

        assertEquals(42, dto.number)
        assertEquals("Something is broken", dto.title)
        assertEquals("open", dto.state)
        assertEquals("steps to reproduce", dto.body)
        assertEquals(listOf(GitHubLabelDto("bug", "d73a4a")), dto.labels)
        assertEquals("octocat", dto.assignee?.login)
        assertEquals("https://github.com/o/r/issues/42", dto.htmlUrl)
        assertEquals("2026-07-20T10:00:00Z", dto.updatedAt)
        assertFalse(dto.isPullRequest)
    }

    @Test
    fun `deserializes issue with only required fields`() {
        val body =
            """
            {
              "number": 1,
              "title": "Minimal",
              "state": "closed",
              "html_url": "https://github.com/o/r/issues/1",
              "created_at": "2026-07-19T10:00:00Z",
              "updated_at": "2026-07-20T10:00:00Z"
            }
            """.trimIndent()

        val dto = json.decodeFromString<GitHubIssueDto>(body)

        assertNull(dto.body)
        assertNull(dto.assignee)
        assertTrue(dto.labels.isEmpty())
        assertFalse(dto.isPullRequest)
    }

    @Test
    fun `detects pull requests via the pull_request field`() {
        val body =
            """
            {
              "number": 7,
              "title": "A PR",
              "state": "open",
              "html_url": "https://github.com/o/r/pull/7",
              "created_at": "2026-07-19T10:00:00Z",
              "updated_at": "2026-07-20T10:00:00Z",
              "pull_request": {"url": "https://api.github.com/o/r/pulls/7"}
            }
            """.trimIndent()

        val dto = json.decodeFromString<GitHubIssueDto>(body)

        assertTrue(dto.isPullRequest)
    }
}
