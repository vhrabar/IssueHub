package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.IssueActor
import com.github.vhrabar.issuehub.model.IssueLabel
import com.github.vhrabar.issuehub.model.IssueState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubIssueMappingTest {
    private fun dto(
        number: Int = 1,
        title: String = "Title",
        state: String = "open",
        body: String? = null,
        labels: List<GitHubLabelDto> = emptyList(),
        assignee: GitHubUserDto? = null,
    ) = GitHubIssueDto(
        number = number,
        title = title,
        state = state,
        body = body,
        labels = labels,
        assignee = assignee,
        htmlUrl = "https://github.com/o/r/issues/$number",
        createdAt = "2026-07-19T10:00:00Z",
        updatedAt = "2026-07-20T10:00:00Z",
    )

    @Test
    fun `maps a full dto to the provider-neutral issue`() {
        val issue =
            dto(
                number = 99,
                title = "Broken thing",
                state = "open",
                body = "details",
                labels = listOf(GitHubLabelDto("bug", "d73a4a")),
                assignee = GitHubUserDto("octocat"),
            ).toIssue()

        assertEquals(99, issue.id)
        assertEquals("#99", issue.displayNumber)
        assertEquals("Broken thing", issue.title)
        assertEquals(IssueState.OPEN, issue.state)
        assertEquals("details", issue.body)
        assertEquals(listOf(IssueLabel("bug", "d73a4a")), issue.labels)
        assertEquals(IssueActor("octocat", null), issue.assignee)
        assertEquals("https://github.com/o/r/issues/99", issue.url)
        assertEquals("2026-07-19T10:00:00Z", issue.createdAt)
        assertEquals("2026-07-20T10:00:00Z", issue.updatedAt)
    }

    @Test
    fun `maps closed and unknown states`() {
        assertEquals(IssueState.CLOSED, dto(state = "closed").toIssue().state)
        assertEquals(IssueState.OTHER, dto(state = "something-else").toIssue().state)
    }

    @Test
    fun `maps issue without assignee`() {
        assertNull(dto(assignee = null).toIssue().assignee)
    }
}
