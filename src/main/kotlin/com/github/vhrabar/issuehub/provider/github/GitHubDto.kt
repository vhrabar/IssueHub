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
internal data class GitHubPullRequestRefDto(
    val url: String? = null,
)

/** Wire model for the GitHub REST API */
@Serializable
internal data class GitHubIssueDto(
    val number: Int,
    val title: String,
    val state: String,
    val body: String? = null,
    val labels: List<GitHubLabelDto> = emptyList(),
    val assignee: GitHubUserDto? = null,
    val user: GitHubUserDto? = null,
    val comments: Int = 0,
    @SerialName("html_url") val htmlUrl: String,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,

    @SerialName("pull_request") val pullRequest: GitHubPullRequestRefDto? = null,
) {
    val isPullRequest: Boolean get() = pullRequest != null
}

