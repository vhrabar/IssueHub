package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueActor
import com.github.vhrabar.issuehub.model.IssueDetail
import com.github.vhrabar.issuehub.model.IssueDevelopment
import com.github.vhrabar.issuehub.model.IssueFilterOptions
import com.github.vhrabar.issuehub.model.IssueLabel
import com.github.vhrabar.issuehub.model.IssueLinkedBranch
import com.github.vhrabar.issuehub.model.IssueLinkedPullRequest
import com.github.vhrabar.issuehub.model.IssueMilestone
import com.github.vhrabar.issuehub.model.IssueProjectField
import com.github.vhrabar.issuehub.model.IssueProjectItem
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueState
import com.github.vhrabar.issuehub.model.IssueTimelineItem
import com.github.vhrabar.issuehub.model.PullRequestState
import com.github.vhrabar.issuehub.provider.AccountVerification
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.github.vhrabar.issuehub.settings.IssueHubAccounts
import com.intellij.openapi.project.Project
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.map

private fun GitHubUserDto.toActor(): IssueActor = IssueActor(login, avatarUrl)

internal fun GitHubIssueDto.toIssue(): Issue =
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
        // `assignees` is the whole list and `assignee` the first of it; older payloads only carry the latter.
        assignees = assignees.ifEmpty { listOfNotNull(assignee) }.map { it.toActor() },
        milestone = milestone?.let { IssueMilestone(it.number, it.title) },
        author = user?.toActor(),
        commentCount = comments,
        url = htmlUrl,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )

internal fun GitHubDevelopmentDto.toDevelopment(): IssueDevelopment =
    IssueDevelopment(
        pullRequests =
            pullRequests?.nodes.orEmpty().filterNotNull().map { pr ->
                IssueLinkedPullRequest(
                    displayNumber = "#${pr.number}",
                    title = pr.title,
                    url = pr.url,
                    state =
                        when {
                            pr.state.equals("MERGED", ignoreCase = true) -> PullRequestState.MERGED
                            pr.state.equals("CLOSED", ignoreCase = true) -> PullRequestState.CLOSED
                            pr.isDraft -> PullRequestState.DRAFT
                            else -> PullRequestState.OPEN
                        },
                )
            },
        branches =
            branches?.nodes.orEmpty().filterNotNull().mapNotNull { branch ->
                val name = branch.ref?.name ?: return@mapNotNull null
                // GraphQL gives the repository, not the branch page; the web address is derived from it.
                val repositoryUrl = branch.ref.repository?.url
                IssueLinkedBranch(name, repositoryUrl?.let { "$it/tree/$name" })
            },
    )

internal fun GitHubProjectItemsDto.toProjectItems(): List<IssueProjectItem> =
    projectItems?.nodes.orEmpty().filterNotNull().mapNotNull { item ->
        val project = item.project ?: return@mapNotNull null
        IssueProjectItem(
            title = project.title?.takeIf { it.isNotBlank() } ?: return@mapNotNull null,
            url = project.url,
            fields =
                item.fieldValues
                    ?.nodes
                    .orEmpty()
                    .filterNotNull()
                    .mapNotNull { it.toProjectField() },
        )
    }

/**
 * A board cell as one name/value pair, or null when there is nothing to show: a field type the
 * query doesn't ask about arrives as an empty object, and an unset field carries no value.
 */
private fun GitHubProjectFieldValueDto.toProjectField(): IssueProjectField? {
    val name = field?.name?.takeIf { it.isNotBlank() } ?: return null
    val value =
        text?.takeIf { it.isNotBlank() }
            ?: this.name?.takeIf { it.isNotBlank() }
            ?: title?.takeIf { it.isNotBlank() }
            ?: date?.takeIf { it.isNotBlank() }
            ?: number?.let { formatFieldNumber(it) }
            ?: return null
    return IssueProjectField(name, value)
}

/** Sizes and estimates are whole numbers far more often than not, and read better without the `.0`. */
private fun formatFieldNumber(value: Double): String {
    val whole = value.toLong()
    return if (value == whole.toDouble()) whole.toString() else value.toString()
}

private val IGNORED_TIMELINE_EVENTS = setOf("subscribed", "unsubscribed", "mentioned")

internal fun GitHubTimelineEventDto.toTimelineItem(): IssueTimelineItem? {
    val at = createdAt ?: return null
    val who = (actor ?: user)?.toActor()
    return when (event) {
        "commented" -> {
            IssueTimelineItem.Comment(
                actor = who,
                at = at,
                body = body,
                bodyHtml = bodyHtml,
                url = htmlUrl,
                edited = updatedAt != null && updatedAt != createdAt,
            )
        }

        "closed" -> {
            IssueTimelineItem.StateChange(who, at, IssueState.CLOSED, stateReason)
        }

        "reopened" -> {
            IssueTimelineItem.StateChange(who, at, IssueState.OPEN)
        }

        "labeled", "unlabeled" -> {
            label?.let { IssueTimelineItem.LabelChange(who, at, IssueLabel(it.name, it.color), added = event == "labeled") }
        }

        "assigned", "unassigned" -> {
            assignee?.let { IssueTimelineItem.AssigneeChange(who, at, it.toActor(), added = event == "assigned") }
        }

        "milestoned", "demilestoned" -> {
            milestone?.let {
                IssueTimelineItem.MilestoneChange(
                    actor = who,
                    at = at,
                    milestone = IssueMilestone(IssueMilestone.NUMBER_UNKNOWN, it.title),
                    added = event == "milestoned",
                )
            }
        }

        "renamed" -> {
            rename?.let { IssueTimelineItem.Renamed(who, at, it.from, it.to) }
        }

        "cross-referenced" -> {
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
        }

        "referenced" -> {
            commitId?.let { IssueTimelineItem.Referenced(who, at, it, commitUrl?.let(::commitWebUrl)) }
        }

        null -> {
            null
        }

        else -> {
            IssueTimelineItem.Unknown(who, at, event)
        }
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
    /** One client per server: a user can hold a github.com account and an Enterprise one at once. */
    private val clients = ConcurrentHashMap<String, GitHubClient>()

    override val identifier = PROVIDER_IDENTIFIER
    override val displayName = "GitHub"
    override val defaultServerUrl = DEFAULT_SERVER_URL

    override fun isApplicable(project: Project): Boolean = RepoDetector.detect(project) != null

    override fun sourceLabel(project: Project): String? = RepoDetector.detect(project)?.toString()

    /**
     * Confirms a token by asking who it belongs to, and reads what it may do off the response.
     *
     * A token that GitHub won't publish scopes for is not a token without permissions, so nothing is
     * reported missing there; the sidebar's Projects section will say for itself if it can't read.
     */
    override suspend fun verifyToken(
        serverUrl: String,
        token: String,
    ): AccountVerification {
        val viewer = client(serverUrl).fetchViewer(token)
        return AccountVerification(
            login = viewer.login,
            grantedScopes = viewer.scopes,
            missingScopes =
                viewer.scopes
                    ?.takeIf { granted -> PROJECT_SCOPES.none { it in granted } }
                    ?.let { listOf(PROJECT_SCOPES.first()) }
                    .orEmpty(),
        )
    }

    /** GitHub's token page, pre-filled with the scopes IssueHub asks for. */
    override fun tokenPageUrl(serverUrl: String): String =
        "${webUrl(serverUrl)}/settings/tokens/new?scopes=repo,read:project&description=IssueHub"

    override fun tokenHint(): String = IssueHubBundle["settings.github.tokenHint"]

    override suspend fun fetchIssues(
        project: Project,
        query: IssueQuery,
    ): List<Issue> {
        val repo = RepoDetector.detect(project) ?: return emptyList()
        val (client, token) = session()
        return client.fetchIssues(repo, token, query).map { it.toIssue() }
    }

    /**
     * Re-reads the issue rather than trusting the list row, then the history behind it, then the
     * two things only GraphQL knows: what is being built for the issue, and where it sits on a board.
     *
     * Every request after the first is allowed to fail on its own. A rate-limited timeline shouldn't
     * cost the user the description, and a token without `read:project` shouldn't cost them the
     * development links — a section that couldn't be read comes back null and says so, which is not
     * the same answer as an empty one.
     */
    override suspend fun fetchIssueDetail(
        project: Project,
        issue: Issue,
    ): IssueDetail? {
        val repo = RepoDetector.detect(project) ?: return null
        val (client, token) = session()
        val dto = client.fetchIssue(repo, token, issue.id)
        val timeline =
            runCatching { client.fetchTimeline(repo, token, issue.id) }
                .getOrDefault(emptyList())
                .filterNot { it.event in IGNORED_TIMELINE_EVENTS }
                .mapNotNull { it.toTimelineItem() }
        return IssueDetail(
            issue = dto.toIssue(),
            bodyHtml = dto.bodyHtml,
            timeline = timeline,
            development = runCatching { client.fetchDevelopment(repo, token, issue.id).toDevelopment() }.getOrNull(),
            projects = runCatching { client.fetchProjectItems(repo, token, issue.id).toProjectItems() }.getOrNull(),
        )
    }

    /**
     * Each dimension is fetched independently: `/assignees` needs push access and 403s for
     * read-only tokens, and losing the assignee dropdown shouldn't cost us labels too.
     */
    override suspend fun fetchFilterOptions(project: Project): IssueFilterOptions {
        val repo = RepoDetector.detect(project) ?: return IssueFilterOptions()
        val (client, token) = session()
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

    /**
     * The client and token to work through: whichever account is configured for GitHub, or an
     * unauthenticated client against github.com when there is none.
     *
     * Public repositories answer without a token, at a much lower rate limit, which is why the
     * absence of an account isn't an error here.
     */
    private fun session(): Pair<GitHubClient, String?> {
        val accounts = IssueHubAccounts.getInstance()
        val account = accounts.defaultAccountFor(identifier) ?: accounts.adoptLegacyToken(identifier, defaultServerUrl)
        return client(account?.serverUrl ?: defaultServerUrl) to account?.let { accounts.token(it) }
    }

    private fun client(serverUrl: String): GitHubClient = clients.getOrPut(serverUrl) { GitHubClient(serverUrl) }

    companion object {
        const val PROVIDER_IDENTIFIER = "github"

        const val DEFAULT_SERVER_URL = "https://api.github.com"

        /** Either spelling lets a token read project boards; `read:project` is the one to ask for. */
        private val PROJECT_SCOPES = listOf("read:project", "project")

        /**
         * The site behind an API root: `api.github.com` is served from `github.com`, and an
         * Enterprise install puts its API under `HOST/api/v3` and its pages at `HOST`.
         */
        internal fun webUrl(serverUrl: String): String =
            serverUrl
                .removeSuffix("/")
                .removeSuffix("/api/v3")
                .replace("://api.", "://")
    }
}
