package com.github.vhrabar.issuehub.provider.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class GitHubLabelDto(
    val name: String,
    val color: String? = null,
)

@Serializable
internal data class GitHubUserDto(
    val login: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
internal data class GitHubMilestoneDto(
    val number: Int,
    val title: String,
)

@Serializable
internal data class GitHubPullRequestRefDto(
    val url: String? = null,
)

/** `/search/issues` wraps the same issue objects in a result envelope. */
@Serializable
internal data class GitHubSearchResultDto(
    val items: List<GitHubIssueDto> = emptyList(),
)

/** Wire model for the GitHub REST API */
@Serializable
internal data class GitHubIssueDto(
    val number: Int,
    val title: String,
    val state: String,
    val body: String? = null,
    @SerialName("body_html") val bodyHtml: String? = null,
    val labels: List<GitHubLabelDto> = emptyList(),
    val assignee: GitHubUserDto? = null,
    val assignees: List<GitHubUserDto> = emptyList(),
    val milestone: GitHubMilestoneDto? = null,
    val user: GitHubUserDto? = null,
    val comments: Int = 0,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("pull_request") val pullRequest: GitHubPullRequestRefDto? = null,
) {
    val isPullRequest: Boolean get() = pullRequest != null
}

@Serializable
internal data class GitHubTimelineMilestoneDto(
    val title: String,
)

@Serializable
internal data class GitHubRenameDto(
    val from: String,
    val to: String,
)

@Serializable
internal data class GitHubTimelineSourceDto(
    val issue: GitHubIssueDto? = null,
)

/**
 * One entry of `/issues/{n}/timeline`.
 */
@Serializable
internal data class GitHubTimelineEventDto(
    val event: String? = null,
    val actor: GitHubUserDto? = null,
    val user: GitHubUserDto? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val body: String? = null,
    @SerialName("body_html") val bodyHtml: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val label: GitHubLabelDto? = null,
    val assignee: GitHubUserDto? = null,
    val milestone: GitHubTimelineMilestoneDto? = null,
    val rename: GitHubRenameDto? = null,
    val source: GitHubTimelineSourceDto? = null,
    @SerialName("commit_id") val commitId: String? = null,
    @SerialName("commit_url") val commitUrl: String? = null,
    @SerialName("state_reason") val stateReason: String? = null,
)
