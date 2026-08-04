package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.IssueMilestone
import com.github.vhrabar.issuehub.model.IssueState
import com.github.vhrabar.issuehub.model.IssueTimelineItem
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * GitHub squeezes every kind of history entry through one JSON shape, so the decoding and the
 * mapping back onto [IssueTimelineItem] are where a whole class of entries can silently vanish.
 */
class GitHubTimelineDtoTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Test
    fun `a comment keeps its rendered body and names its writer`() {
        val item =
            item(
                """
                {
                  "event": "commented",
                  "user": {"login": "octocat", "avatar_url": "https://avatars.test/octocat"},
                  "created_at": "2026-07-03T10:00:00Z",
                  "updated_at": "2026-07-03T10:00:00Z",
                  "body": "Still *broken*.",
                  "body_html": "<p>Still <em>broken</em>.</p>",
                  "html_url": "https://github.test/octocat/hello-world/issues/17#issuecomment-1"
                }
                """,
            ) as IssueTimelineItem.Comment

        assertEquals("octocat", item.actor?.login)
        assertEquals("2026-07-03T10:00:00Z", item.at)
        assertEquals("Still *broken*.", item.body)
        assertEquals("<p>Still <em>broken</em>.</p>", item.bodyHtml)
        // An unedited comment is stamped as updated the moment it is written.
        assertFalse(item.edited)
    }

    @Test
    fun `a later update marks a comment as edited`() {
        val item =
            item(
                """
                {
                  "event": "commented",
                  "user": {"login": "octocat"},
                  "created_at": "2026-07-03T10:00:00Z",
                  "updated_at": "2026-07-03T11:00:00Z",
                  "body": "Fixed the typo."
                }
                """,
            ) as IssueTimelineItem.Comment

        assertTrue(item.edited)
    }

    @Test
    fun `closing carries the reason and the actor who did it`() {
        val item =
            item(
                """
                {
                  "event": "closed",
                  "actor": {"login": "maintainer"},
                  "created_at": "2026-07-04T09:00:00Z",
                  "state_reason": "not_planned"
                }
                """,
            ) as IssueTimelineItem.StateChange

        assertEquals(IssueState.CLOSED, item.state)
        assertEquals("not_planned", item.reason)
        assertEquals("maintainer", item.actor?.login)
    }

    @Test
    fun `reopening comes back as a state change without a reason`() {
        val item =
            item("""{"event": "reopened", "actor": {"login": "octocat"}, "created_at": "2026-07-05T09:00:00Z"}""")
                as IssueTimelineItem.StateChange

        assertEquals(IssueState.OPEN, item.state)
        assertNull(item.reason)
    }

    @Test
    fun `label entries distinguish adding from removing`() {
        val added =
            item(
                """{"event": "labeled", "actor": {"login": "bot"}, "created_at": "2026-07-04T09:00:00Z", "label": {"name": "bug", "color": "d73a4a"}}""",
            ) as IssueTimelineItem.LabelChange
        val removed =
            item(
                """{"event": "unlabeled", "actor": {"login": "bot"}, "created_at": "2026-07-04T09:01:00Z", "label": {"name": "bug"}}""",
            ) as IssueTimelineItem.LabelChange

        assertTrue(added.added)
        assertEquals("bug", added.label.name)
        assertEquals("d73a4a", added.label.color)
        assertFalse(removed.added)
    }

    @Test
    fun `assignee entries name the assignee, not just the actor`() {
        val item =
            item(
                """
                {
                  "event": "assigned",
                  "actor": {"login": "maintainer"},
                  "assignee": {"login": "octocat"},
                  "created_at": "2026-07-04T09:00:00Z"
                }
                """,
            ) as IssueTimelineItem.AssigneeChange

        assertEquals("maintainer", item.actor?.login)
        assertEquals("octocat", item.assignee.login)
        assertTrue(item.added)
    }

    /** The timeline names a milestone by title only, so there is no number to map. */
    @Test
    fun `milestone entries fall back to the unknown number`() {
        val item =
            item(
                """{"event": "milestoned", "actor": {"login": "octocat"}, "created_at": "2026-07-04T09:00:00Z", "milestone": {"title": "v1.0"}}""",
            ) as IssueTimelineItem.MilestoneChange

        assertEquals("v1.0", item.milestone.title)
        assertEquals(IssueMilestone.NUMBER_UNKNOWN, item.milestone.number)
    }

    @Test
    fun `a rename keeps both titles`() {
        val item =
            item(
                """
                {
                  "event": "renamed",
                  "actor": {"login": "octocat"},
                  "created_at": "2026-07-04T09:00:00Z",
                  "rename": {"from": "Broke", "to": "Crash on save"}
                }
                """,
            ) as IssueTimelineItem.Renamed

        assertEquals("Broke", item.from)
        assertEquals("Crash on save", item.to)
    }

    @Test
    fun `a cross reference unwraps the issue it points at`() {
        val item =
            item(
                """
                {
                  "event": "cross-referenced",
                  "actor": {"login": "octocat"},
                  "created_at": "2026-07-04T09:00:00Z",
                  "source": {
                    "type": "issue",
                    "issue": {
                      "number": 21,
                      "title": "Follow-up fix",
                      "state": "open",
                      "html_url": "https://github.test/octocat/hello-world/pull/21",
                      "created_at": "2026-07-04T08:00:00Z",
                      "updated_at": "2026-07-04T08:30:00Z",
                      "pull_request": {"url": "https://api.github.test/repos/octocat/hello-world/pulls/21"}
                    }
                  }
                }
                """,
            ) as IssueTimelineItem.CrossReferenced

        assertEquals("#21", item.displayNumber)
        assertEquals("Follow-up fix", item.title)
        assertEquals("https://github.test/octocat/hello-world/pull/21", item.url)
        assertTrue(item.isPullRequest)
    }

    /** GitHub hands back the API address, which renders JSON; the link has to reach the web page. */
    @Test
    fun `a commit reference links to the commit page, not the API`() {
        val item =
            item(
                """
                {
                  "event": "referenced",
                  "actor": {"login": "octocat"},
                  "created_at": "2026-07-04T09:00:00Z",
                  "commit_id": "0123456789abcdef",
                  "commit_url": "https://api.github.com/repos/octocat/hello-world/commits/0123456789abcdef"
                }
                """,
            ) as IssueTimelineItem.Referenced

        assertEquals("0123456789abcdef", item.commitSha)
        assertEquals("https://github.com/octocat/hello-world/commit/0123456789abcdef", item.commitUrl)
    }

    @Test
    fun `a commit reference without a link still names the commit`() {
        val item =
            item("""{"event": "referenced", "created_at": "2026-07-04T09:00:00Z", "commit_id": "0123456789abcdef"}""")
                as IssueTimelineItem.Referenced

        assertEquals("0123456789abcdef", item.commitSha)
        assertNull(item.commitUrl)
    }

    /** An entry we have no case for still shows up, rather than being dropped on the floor. */
    @Test
    fun `an unmodelled entry keeps the provider's own name for it`() {
        val item =
            item("""{"event": "pinned", "actor": {"login": "octocat"}, "created_at": "2026-07-04T09:00:00Z"}""")
                as IssueTimelineItem.Unknown

        assertEquals("pinned", item.kind)
    }

    @Test
    fun `entries without a payload or a date are dropped`() {
        // `committed` entries are dated by the commit author instead of GitHub.
        assertNull(item("""{"event": "committed", "message": "Fix it"}"""))
        assertNull(item("""{"event": "labeled", "created_at": "2026-07-04T09:00:00Z"}"""))
        assertNull(item("""{"event": "renamed", "created_at": "2026-07-04T09:00:00Z"}"""))
    }

    @Test
    fun `a whole page decodes even when entries disagree about their fields`() {
        val page =
            json.decodeFromString<List<GitHubTimelineEventDto>>(
                """
                [
                  {"event": "labeled", "created_at": "2026-07-04T09:00:00Z", "label": {"name": "bug"}},
                  {"event": "commented", "created_at": "2026-07-04T10:00:00Z", "user": {"login": "octocat"}, "body": "Hi"},
                  {"event": "closed", "created_at": "2026-07-04T11:00:00Z", "actor": {"login": "maintainer"}}
                ]
                """.trimIndent(),
            )

        assertEquals(3, page.mapNotNull { it.toTimelineItem() }.size)
    }

    private fun item(payload: String): IssueTimelineItem? =
        json.decodeFromString<GitHubTimelineEventDto>(payload.trimIndent()).toTimelineItem()
}
