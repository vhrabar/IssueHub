package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueActor
import com.github.vhrabar.issuehub.model.IssueDetail
import com.github.vhrabar.issuehub.model.IssueTimelineItem

/**
 * One card of the issue view: an unbroken run of history entries by the same account.
 *
 * Grouping is what keeps a long thread readable — a bot that stamps six labels in a row costs one
 * card, not six — and it is why the account is named once, by the card, instead of on every line.
 */
internal data class IssueThreadCard(
    val actor: IssueActor?,
    val items: List<IssueTimelineItem>,
    /** True for the card the issue opens with, whose first entry is the description itself. */
    val opensTheIssue: Boolean = false,
)

/**
 * The issue as a thread of cards: the description is the opening comment, the history follows.
 *
 * Runs are broken by author and never reordered, so a card always covers one continuous stretch
 * of the issue's life rather than everything one account ever did to it.
 */
internal fun issueThread(
    issue: Issue,
    detail: IssueDetail?,
): List<IssueThreadCard> {
    val opening =
        IssueTimelineItem.Comment(
            actor = issue.author,
            at = issue.createdAt,
            body = issue.body,
            bodyHtml = detail?.bodyHtml,
            url = issue.url,
        )
    val cards = mutableListOf<IssueThreadCard>()
    for (item in listOf(opening) + detail?.timeline.orEmpty()) {
        val last = cards.lastOrNull()
        if (last != null && last.actor?.login == item.actor?.login) {
            cards[cards.lastIndex] = last.copy(items = last.items + item)
        } else {
            cards += IssueThreadCard(item.actor, listOf(item), opensTheIssue = cards.isEmpty())
        }
    }
    return cards
}
