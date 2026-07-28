package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueFilterOptions
import com.github.vhrabar.issuehub.model.IssueLabel
import com.github.vhrabar.issuehub.model.IssueMilestone
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueState
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.github.vhrabar.issuehub.settings.IssueHubSecrets
import com.intellij.openapi.project.Project
import kotlin.collections.map

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
        milestone = milestone?.let { IssueMilestone(it.number, it.title) },
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
        return client.fetchIssues(repo, token, query).map { it.toIssue() }
    }

    /**
     * Each dimension is fetched independently: `/assignees` needs push access and 403s for
     * read-only tokens, and losing the assignee dropdown shouldn't cost us labels too.
     */
    override suspend fun fetchFilterOptions(project: Project): IssueFilterOptions {
        val repo = RepoDetector.detect(project) ?: return IssueFilterOptions()
        val token = IssueHubSecrets.getToken(identifier)
        val assignees = runCatching { client.fetchAssignableUsers(repo, token).map { it.login } }.getOrDefault(emptyList())
        return IssueFilterOptions(
            labels =
                runCatching { client.fetchLabels(repo, token).map { IssueLabel(it.name, it.color) } }
                    .getOrDefault(emptyList()),
            assignees = assignees,
            milestones =
                runCatching { client.fetchMilestones(repo, token).map { IssueMilestone(it.number, it.title) } }
                    .getOrDefault(emptyList()),
            // GitHub has no "issue authors" endpoint; collaborators are the closest cheap stand-in.
            authors = assignees,
        )
    }

    companion object {
        const val PROVIDER_IDENTIFIER = "github"
    }
}
