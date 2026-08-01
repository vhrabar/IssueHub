package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueActor
import com.github.vhrabar.issuehub.model.IssueDetail
import com.github.vhrabar.issuehub.model.IssueFilterOptions
import com.github.vhrabar.issuehub.model.IssueLabel
import com.github.vhrabar.issuehub.model.IssueMilestone
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueState
import com.github.vhrabar.issuehub.model.IssueTimelineItem
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.github.vhrabar.issuehub.settings.IssueHubSecrets
import com.intellij.openapi.project.Project
import kotlin.collections.map

private fun GitHubUserDto.toActor(): IssueActor = IssueActor(login, avatarUrl)

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
        assignee = assignee?.toActor(),
        milestone = milestone?.let { IssueMilestone(it.number, it.title) },
        author = user?.toActor(),
        commentCount = comments,
        url = htmlUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

private val IGNORED_TIMELINE_EVENTS = setOf("subscribed", "unsubscribed", "mentioned")

internal fun GitHubTimelineEventDto.toTimelineItem(): IssueTimelineItem? {
    val at = createdAt ?: return null
    val who = (actor ?: user)?.toActor()
    return when (event) {
        "commented" ->
            IssueTimelineItem.Comment(
                actor = who,
                at = at,
                body = body,
                bodyHtml = bodyHtml,
                url = htmlUrl,
                edited = updatedAt != null && updatedAt != createdAt,
            )

        "closed" -> IssueTimelineItem.StateChange(who, at, IssueState.CLOSED, stateReason)
        "reopened" -> IssueTimelineItem.StateChange(who, at, IssueState.OPEN)

        "labeled", "unlabeled" ->
            label?.let { IssueTimelineItem.LabelChange(who, at, IssueLabel(it.name, it.color), added = event == "labeled") }

        "assigned", "unassigned" ->
            assignee?.let { IssueTimelineItem.AssigneeChange(who, at, it.toActor(), added = event == "assigned") }

        "milestoned", "demilestoned" ->
            milestone?.let {
                IssueTimelineItem.MilestoneChange(
                    actor = who,
                    at = at,
                    milestone = IssueMilestone(IssueMilestone.NUMBER_UNKNOWN, it.title),
                    added = event == "milestoned",
                )
            }

        "renamed" -> rename?.let { IssueTimelineItem.Renamed(who, at, it.from, it.to) }

        "cross-referenced" ->
            source?.issue?.let {
                IssueTimelineItem.CrossReferenced(
                    actor = who,
                    at = at,
                    displayNumber = "#${it.number}",
                    title = it.title,
                    url = it.htmlUrl,
                    isPullRequest = it.isPullRequest,
                )
            }

        "referenced" -> commitId?.let { IssueTimelineItem.Referenced(who, at, it, commitUrl?.let(::commitWebUrl)) }

        null -> null
        else -> IssueTimelineItem.Unknown(who, at, event)
    }
}

/**
 * Turns the API address of a commit into the page a browser can actually show:
 * `api.github.com/repos/OWNER/NAME/commits/SHA` is served as JSON, the commit people mean lives at
 * `github.com/OWNER/NAME/commit/SHA`.
 *
 * The owner and name come from the URL itself rather than the repo we're looking at, because a
 * commit that references an issue may well live in a fork. Enterprise installs put the API under
 * `HOST/api/v3` instead of an `api.` host, so both spellings are undone. Null when the address
 * isn't one we recognise, which leaves the entry showing a plain sha instead of a dead link.
 */
internal fun commitWebUrl(apiUrl: String): String? {
    val base = apiUrl.substringBefore(API_REPOS_PATH, missingDelimiterValue = "").ifEmpty { return null }
    val path = apiUrl.substringAfter(API_REPOS_PATH)
    if (API_COMMITS_PATH !in path) return null
    val host = base.removeSuffix("/api/v3").replace("://api.", "://")
    return "$host/${path.replaceFirst(API_COMMITS_PATH, WEB_COMMIT_PATH)}"
}

private const val API_REPOS_PATH = "/repos/"
private const val API_COMMITS_PATH = "/commits/"
private const val WEB_COMMIT_PATH = "/commit/"

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
     * Re-reads the issue rather than trusting the list row, then the history behind it.
     *
     * The history is a second request and is allowed to fail on its own: a rate-limited or
     * forbidden timeline shouldn't cost the user the description as well.
     */
    override suspend fun fetchIssueDetail(
        project: Project,
        issue: Issue,
    ): IssueDetail? {
        val repo = RepoDetector.detect(project) ?: return null
        val token = IssueHubSecrets.getToken(identifier)
        val dto = client.fetchIssue(repo, token, issue.id)
        val timeline =
            runCatching { client.fetchTimeline(repo, token, issue.id) }
                .getOrDefault(emptyList())
                .filterNot { it.event in IGNORED_TIMELINE_EVENTS }
                .mapNotNull { it.toTimelineItem() }
        return IssueDetail(issue = dto.toIssue(), bodyHtml = dto.bodyHtml, timeline = timeline)
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
