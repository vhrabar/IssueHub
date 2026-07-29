package com.github.vhrabar.issuehub.model

/** Provider-neutral issue states */
enum class IssueState { OPEN, CLOSED, OTHER }

data class IssueLabel(
    val name: String,
    val color: String? = null,
)

/**
 * A milestone an issue can belong to.
 *
 * [number] is the provider's own identifier
 */
data class IssueMilestone(
    val number: Int,
    val title: String,
)

/**
 * Generalized issue actor,  author, an assignee, or the actor behind a
 * timeline event. [login] identifies the account and is what filters match on;
 * [avatarUrl] is null when the provider doesn't publish a picture.
 */
data class IssueActor(
    val login: String,
    val avatarUrl: String? = null,
)

/** Provider-neutral repr. of tracked issues */
data class Issue(
    val id: Int,
    val displayNumber: String,
    val title: String,
    val state: IssueState,
    val body: String? = null,
    val labels: List<IssueLabel> = emptyList(),
    val assignee: IssueActor? = null,
    val milestone: IssueMilestone? = null,
    val author: IssueActor? = null,
    val commentCount: Int = 0,
    val url: String,
    val createdAt: String,
    val updatedAt: String,
)

/**
 * All Issue comps
 *
 * [bodyHtml] is the description already rendered to HTML by the provider
 * It is null when the provider only hands back source text
 */
data class IssueDetail(
    val issue: Issue,
    val bodyHtml: String? = null,
)

/**
 * One entry in an issue's history
 */
sealed interface IssueTimelineItem {
    val actor: IssueActor?
    val at: String

    /** Someone wrote a comment. [bodyHtml] follows the same rules as [IssueDetail.bodyHtml]. */
    data class Comment(
        override val actor: IssueActor?,
        override val at: String,
        val body: String? = null,
        val bodyHtml: String? = null,
        val url: String? = null,
        val edited: Boolean = false,
    ) : IssueTimelineItem

    /** The issue was closed or reopened; [reason] is the provider's wording, when it gives one. */
    data class StateChange(
        override val actor: IssueActor?,
        override val at: String,
        val state: IssueState,
        val reason: String? = null,
    ) : IssueTimelineItem

    data class LabelChange(
        override val actor: IssueActor?,
        override val at: String,
        val label: IssueLabel,
        val added: Boolean,
    ) : IssueTimelineItem

    data class AssigneeChange(
        override val actor: IssueActor?,
        override val at: String,
        val assignee: IssueActor,
        val added: Boolean,
    ) : IssueTimelineItem

    data class MilestoneChange(
        override val actor: IssueActor?,
        override val at: String,
        val milestone: IssueMilestone,
        val added: Boolean,
    ) : IssueTimelineItem

    data class Renamed(
        override val actor: IssueActor?,
        override val at: String,
        val from: String,
        val to: String,
    ) : IssueTimelineItem

    /** Another issue or pull request linked to this one. */
    data class CrossReferenced(
        override val actor: IssueActor?,
        override val at: String,
        val displayNumber: String,
        val title: String,
        val url: String,
        val isPullRequest: Boolean,
    ) : IssueTimelineItem

    /** A commit referenced the issue. */
    data class Referenced(
        override val actor: IssueActor?,
        override val at: String,
        val commitSha: String,
        val commitUrl: String? = null,
    ) : IssueTimelineItem

    /**
     * An entry we can't be modeled. [kind] keeps the provider's own name for it
     */
    data class Unknown(
        override val actor: IssueActor?,
        override val at: String,
        val kind: String,
    ) : IssueTimelineItem
}
