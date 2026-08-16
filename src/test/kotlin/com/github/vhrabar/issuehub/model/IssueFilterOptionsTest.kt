package com.github.vhrabar.issuehub.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueFilterOptionsTest {
    @Test
    fun `only the default open view counts as unfiltered`() {
        assertFalse(IssueQuery().isFiltered)
        assertFalse(IssueQuery(sortField = IssueSortField.UPDATED).isFiltered)
        assertTrue(IssueQuery(text = "crash").isFiltered)
        assertTrue(IssueQuery(state = IssueStateFilter.ALL).isFiltered)
        assertTrue(IssueQuery(labels = setOf("bug")).isFiltered)
        assertTrue(IssueQuery(assignee = AssigneeFilter.Unassigned).isFiltered)
        assertTrue(IssueQuery(milestone = MilestoneFilter.None).isFiltered)
    }

    @Test
    fun `merging de-duplicates and orders case-insensitively`() {
        val provider =
            IssueFilterOptions(
                labels = listOf(IssueLabel("bug", "d73a4a")),
                assignees = listOf("Zoe", "adam"),
                milestones = listOf(IssueMilestone(1, "v1.0")),
                authors = listOf("adam"),
            )
        val discovered =
            IssueFilterOptions(
                labels = listOf(IssueLabel("bug", null), IssueLabel("Api", null)),
                assignees = listOf("adam", "mia"),
                milestones = listOf(IssueMilestone(1, "v1.0"), IssueMilestone(2, "Backlog")),
                authors = listOf("Zoe"),
            )

        val merged = provider.mergedWith(discovered)

        assertEquals(listOf("Api", "bug"), merged.labels.map { it.name })
        assertEquals("d73a4a", merged.labels.first { it.name == "bug" }.color)
        assertEquals(listOf("adam", "mia", "Zoe"), merged.assignees)
        assertEquals(listOf("Backlog", "v1.0"), merged.milestones.map { it.title })
        assertEquals(listOf("adam", "Zoe"), merged.authors)
    }

    @Test
    fun `loaded issues contribute the values they mention`() {
        val options = optionsFrom(listOf(issue(1, assignee = "mia", author = "adam"), issue(2, author = "mia")))

        assertEquals(listOf("bug"), options.labels.map { it.name })
        assertEquals(listOf("mia"), options.assignees)
        assertEquals(listOf("v1.0"), options.milestones.map { it.title })
        assertEquals(listOf("adam", "mia"), options.authors)
    }

    private fun issue(
        number: Int,
        assignee: String? = null,
        author: String? = null,
    ) = Issue(
        id = number,
        displayNumber = "#$number",
        title = "Issue $number",
        state = IssueState.OPEN,
        labels = listOf(IssueLabel("bug", "d73a4a")),
        assignees = listOfNotNull(assignee?.let { IssueActor(it) }),
        milestone = IssueMilestone(1, "v1.0"),
        author = author?.let { IssueActor(it) },
        url = "https://github.test/octocat/hello-world/issues/$number",
        createdAt = "2026-07-01T00:00:00Z",
        updatedAt = "2026-07-02T00:00:00Z",
    )
}
