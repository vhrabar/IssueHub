package com.github.vhrabar.issuehub.provider.github

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Wire model for the GraphQL API, which is where the sidebar's last two sections come from:
 * REST publishes neither the pull requests linked to an issue nor its place on a project board.
 *
 * Every node is optional. GraphQL answers a request it can only partly serve with the fields it
 * managed plus an `errors` entry for the rest, so a token that may not read projects still gets
 * its development links back.
 */
@Serializable
internal data class GitHubGraphQlResponse<T>(
    val data: T? = null,
    val errors: List<GitHubGraphQlErrorDto> = emptyList(),
)

@Serializable
internal data class GitHubGraphQlErrorDto(
    val message: String? = null,
    val type: String? = null,
)

@Serializable
internal data class GitHubRepositoryDataDto<T>(
    val repository: GitHubIssueHolderDto<T>? = null,
)

@Serializable
internal data class GitHubIssueHolderDto<T>(
    val issue: T? = null,
)

@Serializable
internal data class GitHubNodesDto<T>(
    val nodes: List<T?> = emptyList(),
)

@Serializable
internal data class GitHubDevelopmentDto(
    @SerialName("closedByPullRequestsReferences") val pullRequests: GitHubNodesDto<GitHubLinkedPullRequestDto>? = null,
    @SerialName("linkedBranches") val branches: GitHubNodesDto<GitHubLinkedBranchDto>? = null,
)

@Serializable
internal data class GitHubLinkedPullRequestDto(
    val number: Int,
    val title: String = "",
    val url: String,
    /** `OPEN`, `CLOSED` or `MERGED`; a draft is an open one with [isDraft] set. */
    val state: String? = null,
    val isDraft: Boolean = false,
)

@Serializable
internal data class GitHubLinkedBranchDto(
    val ref: GitHubRefDto? = null,
)

@Serializable
internal data class GitHubRefDto(
    val name: String? = null,
    val repository: GitHubRefRepositoryDto? = null,
)

@Serializable
internal data class GitHubRefRepositoryDto(
    val url: String? = null,
)

@Serializable
internal data class GitHubProjectItemsDto(
    @SerialName("projectItems") val projectItems: GitHubNodesDto<GitHubProjectItemDto>? = null,
)

@Serializable
internal data class GitHubProjectItemDto(
    val project: GitHubProjectDto? = null,
    val fieldValues: GitHubNodesDto<GitHubProjectFieldValueDto>? = null,
)

@Serializable
internal data class GitHubProjectDto(
    val title: String? = null,
    val url: String? = null,
)

/**
 * One cell of a project board, flattened across the value types GraphQL splits them into.
 *
 * The query asks for every kind we can display in the same selection set, so which property is
 * filled in says what kind of field it was: [text] for free text, [number] for a size or estimate,
 * [date] for a start or end date, [name] for a single-select, [title] for an iteration. Values of
 * a kind we don't ask about arrive as an empty object and carry no field name, which is what marks
 * them as nothing to show.
 */
@Serializable
internal data class GitHubProjectFieldValueDto(
    val field: GitHubProjectFieldDto? = null,
    val text: String? = null,
    val number: Double? = null,
    val date: String? = null,
    val name: String? = null,
    val title: String? = null,
)

@Serializable
internal data class GitHubProjectFieldDto(
    val name: String? = null,
)
