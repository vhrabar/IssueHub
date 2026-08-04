package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.AssigneeFilter
import com.github.vhrabar.issuehub.model.IssueMilestone
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueSortDirection
import com.github.vhrabar.issuehub.model.IssueSortField
import com.github.vhrabar.issuehub.model.IssueStateFilter
import com.github.vhrabar.issuehub.model.MilestoneFilter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

class GitHubClientUriTest {
    private val client = GitHubClient(BASE_URL)
    private val repo = RepoCoordinates("octocat", "hello-world")

    @Test
    fun `default query asks for open issues, newest first`() {
        assertEquals(
            "$BASE_URL/repos/octocat/hello-world/issues?state=open&sort=created&direction=desc&per_page=50",
            client.listIssuesUri(repo, IssueQuery()).toString(),
        )
    }

    @Test
    fun `filters map onto issue list parameters`() {
        val uri = client.listIssuesUri(repo, filtered())

        assertEquals(
            "$BASE_URL/repos/octocat/hello-world/issues" +
                "?state=all&sort=comments&direction=asc&per_page=50" +
                "&labels=bug%2Chelp+wanted&creator=octocat&assignee=none&milestone=3",
            uri.toString(),
        )
    }

    @Test
    fun `free text switches to the search endpoint`() {
        val uri = client.searchIssuesUri(repo, filtered().copy(text = "  crash on save  "))

        assertEquals("/search/issues", uri.path)
        assertEquals(
            "repo:octocat/hello-world is:issue " +
                """label:"bug" label:"help wanted" author:octocat no:assignee milestone:"v1.0" """ +
                "crash on save",
            uri.decodedParam("q"),
        )
        // The search endpoint spells the direction differently from the list endpoint.
        assertEquals("comments", uri.decodedParam("sort"))
        assertEquals("asc", uri.decodedParam("order"))
    }

    @Test
    fun `search keeps the state term unless every state is wanted`() {
        assertEquals(
            "repo:octocat/hello-world is:issue is:open needle",
            client.searchIssuesUri(repo, IssueQuery(text = "needle")).decodedParam("q"),
        )
        assertEquals(
            "repo:octocat/hello-world is:issue is:closed needle",
            client
                .searchIssuesUri(repo, IssueQuery(text = "needle", state = IssueStateFilter.CLOSED))
                .decodedParam("q"),
        )
    }

    @Test
    fun `issue detail addresses a single issue by number`() {
        assertEquals(
            "$BASE_URL/repos/octocat/hello-world/issues/17",
            client.issueUri(repo, 17).toString(),
        )
    }

    @Test
    fun `the timeline is paged off the issue itself`() {
        assertEquals(
            "$BASE_URL/repos/octocat/hello-world/issues/17/timeline?per_page=100&page=2",
            client.timelineUri(repo, 17, 2).toString(),
        )
    }

    private fun filtered() =
        IssueQuery(
            state = IssueStateFilter.ALL,
            labels = setOf("bug", "help wanted"),
            assignee = AssigneeFilter.Unassigned,
            author = "octocat",
            milestone = MilestoneFilter.Named(IssueMilestone(3, "v1.0")),
            sortField = IssueSortField.COMMENTS,
            sortDirection = IssueSortDirection.ASC,
        )

    /** [URI.getQuery] decodes `+` as a literal plus, so split the raw query and decode by hand. */
    private fun URI.decodedParam(name: String): String =
        rawQuery
            .split("&")
            .first { it.startsWith("$name=") }
            .substringAfter("=")
            .let { URLDecoder.decode(it, StandardCharsets.UTF_8) }

    private companion object {
        const val BASE_URL = "https://api.github.test"
    }
}
