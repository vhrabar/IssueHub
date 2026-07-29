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
