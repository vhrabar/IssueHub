package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueLabel
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueState
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.github.vhrabar.issuehub.settings.IssueHubSecrets
import com.intellij.openapi.project.Project
import kotlin.collections.map

private fun IssueState.toApiParam(): String =
    when (this) {
        IssueState.OPEN -> "open"
        IssueState.CLOSED -> "closed"
        IssueState.OTHER -> "all"
    }

private fun GitHubIssueDto.toIssue(): Issue =
    Issue(
        id = number,
        displayNumber = "#$number",
        title = title,
        state =
            when (state) {
                "open" -> IssueState.OPEN
                "closed" -> IssueState.CLOSED
                else -> IssueState.OTHER
            },
        body = body,
        labels = labels.map { IssueLabel(it.name, it.color) },
        assignee = assignee?.login,
        assigneeAvatarUrl = assignee?.avatarUrl,
        author = user?.login,
        authorAvatarUrl = user?.avatarUrl,
        commentCount = comments,
        url = htmlUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

class GitHubIssueProvider : IssueProvider {
    private var client = GitHubClient()

    override val identifier = PROVIDER_IDENTIFIER
    override val displayName = "GitHub"

    override fun isApplicable(project: Project): Boolean = RepoDetector.detect(project) != null

    override fun sourceLabel(project: Project): String? = RepoDetector.detect(project)?.toString()

    override suspend fun fetchIssues(
        project: Project,
        query: IssueQuery,
    ): List<Issue> {
        val repo = RepoDetector.detect(project) ?: return emptyList()
        val token = IssueHubSecrets.getToken(identifier)
        return client.fetchIssues(repo, token, query.state.toApiParam(), query.limit).map { it.toIssue() }
    }

    companion object {
        const val PROVIDER_IDENTIFIER = "github"
    }
}
