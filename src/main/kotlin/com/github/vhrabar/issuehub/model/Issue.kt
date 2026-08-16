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
 * [number] is the provider's own identifier, or [NUMBER_UNKNOWN] when the milestone was named
 * by a source that doesn't publish one (history entries typically only carry the title).
 */
data class IssueMilestone(
    val number: Int,
    val title: String,
) {
    companion object {
        /** Providers number milestones from 1, so zero can't collide with a real one. */
        const val NUMBER_UNKNOWN = 0
    }
}

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
    val assignees: List<IssueActor> = emptyList(),
    val milestone: IssueMilestone? = null,
    val author: IssueActor? = null,
    val commentCount: Int = 0,
    val url: String,
    val createdAt: String,
    val updatedAt: String,
) {
    /** The one account a single-line view has room for; an issue can carry several. */
    val assignee: IssueActor? get() = assignees.firstOrNull()
}

/** How far along a pull request linked to an issue is; merged is not just another closed. */
enum class PullRequestState { OPEN, DRAFT, MERGED, CLOSED }

/** A pull request that closes the issue, as listed under GitHub's "Development" heading. */
data class IssueLinkedPullRequest(
    val displayNumber: String,
    val title: String,
    val url: String,
    val state: PullRequestState,
)

/** A branch opened for the issue, which usually precedes the pull request. */
data class IssueLinkedBranch(
    val name: String,
    val url: String? = null,
)

/**
 * What is linked to the issue for the work itself.
 *
 * Providers hand back null when they can't read it at all, which the view says out loud rather
 * than passing off as an issue nobody has started.
 */
data class IssueDevelopment(
    val pullRequests: List<IssueLinkedPullRequest> = emptyList(),
    val branches: List<IssueLinkedBranch> = emptyList(),
) {
    val isEmpty: Boolean get() = pullRequests.isEmpty() && branches.isEmpty()
}

/** One of a project's own columns as it stands for this issue, e.g. `Size` / `Large`. */
data class IssueProjectField(
    val name: String,
    val value: String,
)

/**
 * The issue's place on one project board: the board itself plus whichever of its fields
 * (status, size, estimate, dates, iteration…) have been filled in for this issue.
 */
data class IssueProjectItem(
    val title: String,
    val url: String? = null,
    val fields: List<IssueProjectField> = emptyList(),
)

/**
 * All Issue comps
 *
 * [bodyHtml] is the description already rendered to HTML by the provider
 * It is null when the provider only hands back source text
 *
 * [timeline] is everything that happened after the description, oldest first. Empty when the
 * provider can't serve a history, which is not the same as an issue nobody ever touched.
 *
 * [development] and [projects] are null, rather than empty, when the provider couldn't read them —
 * a token without the scope they need, typically.
 */
data class IssueDetail(
    val issue: Issue,
    val bodyHtml: String? = null,
    val timeline: List<IssueTimelineItem> = emptyList(),
    val development: IssueDevelopment? = null,
    val projects: List<IssueProjectItem>? = null,
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
