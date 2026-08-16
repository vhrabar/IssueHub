package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueActor
import com.github.vhrabar.issuehub.model.IssueDetail
import com.github.vhrabar.issuehub.model.IssueDevelopment
import com.github.vhrabar.issuehub.model.IssueLabel
import com.github.vhrabar.issuehub.model.IssueLinkedBranch
import com.github.vhrabar.issuehub.model.IssueLinkedPullRequest
import com.github.vhrabar.issuehub.model.IssueMilestone
import com.github.vhrabar.issuehub.model.IssueProjectField
import com.github.vhrabar.issuehub.model.IssueProjectItem
import com.github.vhrabar.issuehub.model.IssueState
import com.github.vhrabar.issuehub.model.PullRequestState
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Container
import javax.swing.AbstractButton
import javax.swing.JLabel

class IssueSidebarPanelTest : BasePlatformTestCase() {
    fun testSectionsFollowGitHubsOwnOrder() {
        val panel = IssueSidebarPanel().apply { show(issue(), detail()) }

        assertEquals(
            listOf("Assignees", "Labels", "Projects", "Milestone", "Development"),
            panel.components.map { (it as Container).components.first().text() },
        )
    }

    fun testEverySectionNamesWhatTheIssueCarries() {
        val panel = IssueSidebarPanel().apply { show(issue(), detail()) }

        val texts = panel.texts()
        // Both assignees, not just the one the list row has room for.
        assertTrue(texts.containsAll(listOf("mia", "adam")))
        assertTrue(texts.contains("bug"))
        assertTrue(texts.contains("v1.0"))
        // The board, then the board's own columns as they stand for this issue.
        assertTrue(texts.containsAll(listOf("Roadmap", "Status", "In Progress", "Size", "3")))
        assertTrue(texts.containsAll(listOf("#42 Add the sidebar", "42-sidebar")))
    }

    fun testAnIssueWithNothingOnItSaysSoPerSection() {
        val panel = IssueSidebarPanel().apply { show(issue().copy(labels = emptyList(), assignees = emptyList(), milestone = null), null) }

        val texts = panel.texts()
        assertTrue(texts.contains("No one assigned"))
        assertTrue(texts.contains("No milestone"))
    }

    /** A section the token couldn't read is not the same as one with nothing in it. */
    fun testSectionsThatCouldNotBeReadAreNotPassedOffAsEmpty() {
        val unreadable = IssueSidebarPanel().apply { show(issue(), IssueDetail(issue())) }.texts()
        val empty =
            IssueSidebarPanel()
                .apply { show(issue(), IssueDetail(issue(), development = IssueDevelopment(), projects = emptyList())) }
                .texts()

        assertTrue(unreadable.contains("Needs a 'read:project' token"))
        assertTrue(unreadable.contains("Needs a token to see linked work"))
        assertTrue(empty.contains("No branches or pull requests"))
        assertFalse(empty.contains("Needs a token to see linked work"))
    }

    /** While the detail request is out, those two sections are unknown rather than unavailable. */
    fun testLoadingIsNotReportedAsAMissingScope() {
        val texts = IssueSidebarPanel().apply { showLoading(issue()) }.texts()

        assertEquals(2, texts.count { it == "Loading…" })
        assertTrue(texts.contains("mia"))
    }

    private fun issue() =
        Issue(
            id = 17,
            displayNumber = "#17",
            title = "Filter issues",
            state = IssueState.OPEN,
            labels = listOf(IssueLabel("bug", "d73a4a")),
            assignees = listOf(IssueActor("mia"), IssueActor("adam")),
            milestone = IssueMilestone(1, "v1.0"),
            author = IssueActor("octocat"),
            url = "https://github.test/octocat/hello-world/issues/17",
            createdAt = "2026-07-01T00:00:00Z",
            updatedAt = "2026-07-02T00:00:00Z",
        )

    private fun detail() =
        IssueDetail(
            issue = issue(),
            development =
                IssueDevelopment(
                    pullRequests =
                        listOf(
                            IssueLinkedPullRequest(
                                displayNumber = "#42",
                                title = "Add the sidebar",
                                url = "https://github.test/octocat/hello-world/pull/42",
                                state = PullRequestState.OPEN,
                            ),
                        ),
                    branches = listOf(IssueLinkedBranch("42-sidebar", "https://github.test/octocat/hello-world/tree/42-sidebar")),
                ),
            projects =
                listOf(
                    IssueProjectItem(
                        title = "Roadmap",
                        url = "https://github.test/orgs/octocat/projects/3",
                        fields = listOf(IssueProjectField("Status", "In Progress"), IssueProjectField("Size", "3")),
                    ),
                ),
        )

    /** Every piece of text on the panel, whichever kind of component happens to be showing it. */
    private fun Container.texts(): List<String> =
        components.flatMap { component ->
            listOfNotNull(component.text()) + if (component is Container) component.texts() else emptyList()
        }

    private fun java.awt.Component.text(): String? =
        when (this) {
            is JLabel -> text
            is AbstractButton -> text
            else -> null
        }
}
