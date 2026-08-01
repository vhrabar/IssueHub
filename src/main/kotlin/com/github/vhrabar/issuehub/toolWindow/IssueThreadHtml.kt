package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.model.IssueState
import com.github.vhrabar.issuehub.model.IssueTimelineItem
import com.intellij.util.text.DateFormatUtil
import java.time.Instant

/**
 * The contents of one thread card as an HTML document, for the platform's own HTML pane.
 *
 * Nothing here names the account: the card puts that beside the text, so the lines read as
 * "commented on 3 Jul" rather than repeating a login the reader already has in view.
 *
 * [muted] is a CSS colour for everything that isn't the text people wrote themselves.
 */
internal fun cardContentHtml(
    card: IssueThreadCard,
    muted: String,
): String =
    card.items
        .mapIndexed { index, item -> item.toHtml(muted, opening = card.opensTheIssue && index == 0) }
        .joinToString(separator = "\n", prefix = "<html><body>", postfix = "</body></html>")

private fun IssueTimelineItem.toHtml(
    muted: String,
    opening: Boolean,
): String {
    val on = formatAt(at)
    return when (this) {
        is IssueTimelineItem.Comment -> commentHtml(on, muted, opening)

        is IssueTimelineItem.StateChange ->
            event(
                if (state == IssueState.CLOSED) ThreadIcons.CLOSED else ThreadIcons.REOPENED,
                when {
                    state != IssueState.CLOSED -> IssueHubBundle["detail.timeline.reopened", on]
                    reason.isNullOrBlank() -> IssueHubBundle["detail.timeline.closed", on]
                    else -> IssueHubBundle["detail.timeline.closedAs", on, humanize(reason)]
                },
                muted,
            )

        is IssueTimelineItem.LabelChange ->
            event(
                ThreadIcons.label(label),
                if (added) {
                    IssueHubBundle["detail.timeline.labeled", on, escapeHtml(label.name)]
                } else {
                    IssueHubBundle["detail.timeline.unlabeled", on, escapeHtml(label.name)]
                },
                muted,
            )

        is IssueTimelineItem.AssigneeChange ->
            event(
                ThreadIcons.ASSIGNEE,
                if (added) {
                    IssueHubBundle["detail.timeline.assigned", on, escapeHtml(assignee.login)]
                } else {
                    IssueHubBundle["detail.timeline.unassigned", on, escapeHtml(assignee.login)]
                },
                muted,
            )

        is IssueTimelineItem.MilestoneChange ->
            event(
                ThreadIcons.MILESTONE,
                if (added) {
                    IssueHubBundle["detail.timeline.milestoned", on, escapeHtml(milestone.title)]
                } else {
                    IssueHubBundle["detail.timeline.demilestoned", on, escapeHtml(milestone.title)]
                },
                muted,
            )

        is IssueTimelineItem.Renamed ->
            event(
                ThreadIcons.RENAME,
                IssueHubBundle["detail.timeline.renamed", on, escapeHtml(from), escapeHtml(to)],
                muted,
            )

        is IssueTimelineItem.CrossReferenced ->
            event(
                ThreadIcons.REFERENCE,
                IssueHubBundle[
                    "detail.timeline.crossReferenced",
                    on,
                    link(url, "${escapeHtml(displayNumber)} ${escapeHtml(title)}"),
                ],
                muted,
            )

        is IssueTimelineItem.Referenced ->
            event(
                ThreadIcons.COMMIT,
                IssueHubBundle[
                    "detail.timeline.referenced",
                    on,
                    commitUrl?.let { link(it, escapeHtml(shortSha)) } ?: escapeHtml(shortSha),
                ],
                muted,
            )

        is IssueTimelineItem.Unknown ->
            event(ThreadIcons.OTHER, IssueHubBundle["detail.timeline.other", on, humanize(kind)], muted)
    }
}

/** A muted "commented on …" line, then the comment itself in whatever form the provider sent. */
private fun IssueTimelineItem.Comment.commentHtml(
    on: String,
    muted: String,
    opening: Boolean,
): String {
    val header =
        if (opening) IssueHubBundle["detail.timeline.opened", on] else IssueHubBundle["detail.timeline.commented", on]
    val icon = if (opening) ThreadIcons.OPENED else ThreadIcons.COMMENT
    val edit = if (edited) " · ${IssueHubBundle["detail.timeline.edited"]}" else ""
    val rendered =
        bodyHtml?.takeIf { it.isNotBlank() }
            // Without the detail request all we have is the Markdown source, which goes out as-is.
            ?: body?.takeIf { it.isNotBlank() }?.let { "<pre>${escapeHtml(it)}</pre>" }
            ?: muted(
                if (opening) IssueHubBundle["detail.noDescription"] else IssueHubBundle["detail.timeline.emptyComment"],
                muted,
            )
    // Indented to start under its own header rather than out beside the icons.
    return event(icon, header + edit, muted) + """<div style="margin-left:${ICON_INDENT}px">$rendered</div>"""
}

/** A history line: its icon, then the muted sentence describing what happened. */
private fun event(
    icon: String,
    text: String,
    muted: String,
): String = muted("""<icon src="$icon">&nbsp;$text""", muted)

/** Roughly an icon plus its trailing space, so text lines up whether or not it carries one. */
private const val ICON_INDENT = 20

private val IssueTimelineItem.Referenced.shortSha: String get() = commitSha.take(SHORT_SHA_LENGTH)

private const val SHORT_SHA_LENGTH = 7

private fun muted(
    text: String,
    color: String,
): String = """<div style="color:$color">$text</div>"""

private fun link(
    url: String,
    text: String,
): String = """<a href="${escapeHtml(url)}">$text</a>"""

/** Providers name their event kinds and close reasons in snake or kebab case. */
private fun humanize(value: String): String = escapeHtml(value.replace('_', ' ').replace('-', ' '))

/** Falls back to the raw stamp when a provider hands back something we can't parse. */
private fun formatAt(at: String): String = runCatching { DateFormatUtil.formatDate(Instant.parse(at).toEpochMilli()) }.getOrDefault(at)

internal fun escapeHtml(text: String): String =
    text
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
