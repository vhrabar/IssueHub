package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueActor
import com.github.vhrabar.issuehub.model.IssueDetail
import com.github.vhrabar.issuehub.model.IssueLabel
import com.github.vhrabar.issuehub.model.IssueState
import com.github.vhrabar.issuehub.model.IssueTimelineItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IssueThreadTest {
    @Test
    fun `the description opens the thread as its author's comment`() {
        val cards = issueThread(issue(), IssueDetail(issue(), bodyHtml = "<p>Renders this.</p>"))

        assertEquals(1, cards.size)
        val opening = cards.single()
        assertEquals("octocat", opening.actor?.login)
        assertTrue(opening.opensTheIssue)
        val comment = opening.items.single() as IssueTimelineItem.Comment
        assertEquals("<p>Renders this.</p>", comment.bodyHtml)
        assertEquals("2026-07-01T00:00:00Z", comment.at)
    }

    @Test
    fun `an issue nobody touched is still one card`() {
        val cards = issueThread(issue(body = null), null)

        assertEquals(1, cards.size)
        assertNull((cards.single().items.single() as IssueTimelineItem.Comment).body)
    }

    @Test
    fun `a run of entries by one account collapses into a single card`() {
        val cards =
            issueThread(
                issue(),
                detail(
                    label("bot", "2026-07-02T09:00:00Z", "bug"),
                    label("bot", "2026-07-02T09:01:00Z", "triage"),
                    label("bot", "2026-07-02T09:02:00Z", "help wanted"),
                ),
            )

        // The description's card, then one card covering all three labels.
        assertEquals(2, cards.size)
        assertEquals("bot", cards[1].actor?.login)
        assertEquals(3, cards[1].items.size)
        assertFalse(cards[1].opensTheIssue)
    }

    @Test
    fun `entries by the description's author join its card`() {
        val cards = issueThread(issue(), detail(label("octocat", "2026-07-02T09:00:00Z", "bug")))

        assertEquals(1, cards.size)
        assertEquals(2, cards.single().items.size)
        assertTrue(cards.single().opensTheIssue)
    }

    /** Grouping must not reorder: a card covers one continuous stretch, not everything an account did. */
    @Test
    fun `an account coming back after someone else gets a second card`() {
        val cards =
            issueThread(
                issue(),
                detail(
                    label("bot", "2026-07-02T09:00:00Z", "bug"),
                    label("maintainer", "2026-07-02T10:00:00Z", "triage"),
                    label("bot", "2026-07-02T11:00:00Z", "wontfix"),
                ),
            )

        assertEquals(listOf("octocat", "bot", "maintainer", "bot"), cards.map { it.actor?.login })
    }

    @Test
    fun `only the first card opens the issue`() {
        val cards = issueThread(issue(), detail(label("bot", "2026-07-02T09:00:00Z", "bug")))

        assertEquals(listOf(true, false), cards.map { it.opensTheIssue })
    }

    private fun issue(body: String? = "Renders *this*.") =
        Issue(
            id = 17,
            displayNumber = "#17",
            title = "Filter issues",
            state = IssueState.OPEN,
            body = body,
            author = IssueActor("octocat"),
            url = "https://github.test/octocat/hello-world/issues/17",
            createdAt = "2026-07-01T00:00:00Z",
            updatedAt = "2026-07-02T00:00:00Z",
        )

    private fun detail(vararg timeline: IssueTimelineItem) = IssueDetail(issue(), timeline = timeline.toList())

    private fun label(
        who: String,
        at: String,
        name: String,
    ) = IssueTimelineItem.LabelChange(IssueActor(who), at, IssueLabel(name), added = true)
}
