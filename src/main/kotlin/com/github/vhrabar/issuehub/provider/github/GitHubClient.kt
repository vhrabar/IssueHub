package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.AssigneeFilter
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueSortDirection
import com.github.vhrabar.issuehub.model.IssueSortField
import com.github.vhrabar.issuehub.model.IssueStateFilter
import com.github.vhrabar.issuehub.model.MilestoneFilter
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.VisibleForTesting
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlin.collections.filterNot

class GitHubApiException(
    message: String,
) : Exception(message)

/** REST client based on teh the JDK [HttpClient] */
internal class GitHubClient(
    private val baseUrl: String = "https://api.github.com",
) {
    private val http: HttpClient =
        HttpClient
            .newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .build()

    private val json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    /**
     * Free-text search needs `/search/issues`; every other filter is expressible on the plain
     * issue list, which has the far more generous rate limit, so text decides the route.
     */
    suspend fun fetchIssues(
        repo: RepoCoordinates,
        token: String?,
        query: IssueQuery,
    ): List<GitHubIssueDto> {
        val issues =
            if (query.text.isBlank()) {
                get(listIssuesUri(repo, query), token) { json.decodeFromString<List<GitHubIssueDto>>(it) }
            } else {
                get(searchIssuesUri(repo, query), token) { json.decodeFromString<GitHubSearchResultDto>(it).items }
            }
        return issues.filterNot { it.isPullRequest }
    }

    /**
     * A single issue, asked for with the `full` media type so the response carries `body_html`
     * next to the Markdown source. The list endpoints deliberately stay on the default type:
     * rows only ever show the title, and rendered bodies would bloat every page.
     */
    suspend fun fetchIssue(
        repo: RepoCoordinates,
        token: String?,
        number: Int,
    ): GitHubIssueDto = get(issueUri(repo, number), token, ACCEPT_FULL) { json.decodeFromString<GitHubIssueDto>(it) }

    @VisibleForTesting
    fun issueUri(
        repo: RepoCoordinates,
        number: Int,
    ): URI = URI.create("$baseUrl/repos/${repo.owner}/${repo.name}/issues/$number")

    suspend fun fetchLabels(
        repo: RepoCoordinates,
        token: String?,
    ): List<GitHubLabelDto> =
        get(URI.create("$baseUrl/repos/${repo.owner}/${repo.name}/labels?per_page=$OPTIONS_PER_PAGE"), token) {
            json.decodeFromString<List<GitHubLabelDto>>(it)
        }

    suspend fun fetchMilestones(
        repo: RepoCoordinates,
        token: String?,
    ): List<GitHubMilestoneDto> =
        get(
            URI.create("$baseUrl/repos/${repo.owner}/${repo.name}/milestones?state=all&per_page=$OPTIONS_PER_PAGE"),
            token,
        ) { json.decodeFromString<List<GitHubMilestoneDto>>(it) }

    /** Users the repo can assign issues to; needs push access, so it 403s for most read-only tokens. */
    suspend fun fetchAssignableUsers(
        repo: RepoCoordinates,
        token: String?,
    ): List<GitHubUserDto> =
        get(URI.create("$baseUrl/repos/${repo.owner}/${repo.name}/assignees?per_page=$OPTIONS_PER_PAGE"), token) {
            json.decodeFromString<List<GitHubUserDto>>(it)
        }

    @VisibleForTesting
    fun listIssuesUri(
        repo: RepoCoordinates,
        query: IssueQuery,
    ): URI {
        val params =
            buildList {
                add("state" to query.state.listParam())
                add("sort" to query.sortField.apiParam())
                add("direction" to query.sortDirection.apiParam())
                add("per_page" to query.limit.toString())
                if (query.labels.isNotEmpty()) add("labels" to query.labels.joinToString(","))
                query.author?.let { add("creator" to it) }
                when (val assignee = query.assignee) {
                    null -> Unit
                    AssigneeFilter.Unassigned -> add("assignee" to "none")
                    is AssigneeFilter.User -> add("assignee" to assignee.login)
                }
                when (val milestone = query.milestone) {
                    null -> Unit
                    MilestoneFilter.None -> add("milestone" to "none")
                    is MilestoneFilter.Named -> add("milestone" to milestone.milestone.number.toString())
                }
            }.joinToString("&") { (name, value) -> "$name=${encode(value)}" }
        return URI.create("$baseUrl/repos/${repo.owner}/${repo.name}/issues?$params")
    }

    @VisibleForTesting
    fun searchIssuesUri(
        repo: RepoCoordinates,
        query: IssueQuery,
    ): URI {
        val terms =
            buildList {
                add("repo:${repo.owner}/${repo.name}")
                add("is:issue")
                query.state.searchTerm()?.let(::add)
                query.labels.forEach { add("label:${quote(it)}") }
                query.author?.let { add("author:$it") }
                when (val assignee = query.assignee) {
                    null -> Unit
                    AssigneeFilter.Unassigned -> add("no:assignee")
                    is AssigneeFilter.User -> add("assignee:${assignee.login}")
                }
                when (val milestone = query.milestone) {
                    null -> Unit
                    MilestoneFilter.None -> add("no:milestone")
                    is MilestoneFilter.Named -> add("milestone:${quote(milestone.milestone.title)}")
                }
                add(query.text.trim())
            }
        val params =
            "q=${encode(terms.joinToString(" "))}" +
                "&sort=${query.sortField.apiParam()}" +
                "&order=${query.sortDirection.apiParam()}" +
                "&per_page=${query.limit}"
        return URI.create("$baseUrl/search/issues?$params")
    }

    private suspend fun <T> get(
        uri: URI,
        token: String?,
        accept: String = ACCEPT_JSON,
        decode: (String) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val requestBuilder =
                HttpRequest
                    .newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", accept)
                    .header("X-GitHub-Api-Version", "2026-03-10")
                    .GET()
            if (!token.isNullOrBlank()) {
                requestBuilder.header("Authorization", "Bearer $token")
            }

            val response = http.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() !in 200..299) {
                thisLogger().warn("GitHub API returned ${response.statusCode()} for ${uri.path}")
                throw GitHubApiException(describeError(response.statusCode()))
            }

            decode(response.body())
        }

    private fun describeError(status: Int): String =
        when (status) {
            401 -> "Authentication failed (401). Check your GitHub token."
            403 -> "Access forbidden or rate limit exceeded (403)."
            404 -> "Repository not found (404). Check the owner/name and token scope."
            422 -> "GitHub rejected the search query (422)."
            else -> "GitHub API request failed with status $status."
        }

    private companion object {
        const val ACCEPT_JSON = "application/vnd.github+json"

        /** Adds `body_html` (and `body_text`) alongside the Markdown source. */
        const val ACCEPT_FULL = "application/vnd.github.full+json"

        /** Filter dropdowns list every value at once; GitHub caps a page at 100. */
        const val OPTIONS_PER_PAGE = 100

        fun encode(value: String): String = URLEncoder.encode(value, StandardCharsets.UTF_8)

        /** Label names and milestone titles may contain spaces, which would split the search term. */
        fun quote(value: String): String = "\"" + value.replace("\"", "") + "\""

        fun IssueStateFilter.listParam(): String =
            when (this) {
                IssueStateFilter.OPEN -> "open"
                IssueStateFilter.CLOSED -> "closed"
                IssueStateFilter.ALL -> "all"
            }

        fun IssueStateFilter.searchTerm(): String? =
            when (this) {
                IssueStateFilter.OPEN -> "is:open"
                IssueStateFilter.CLOSED -> "is:closed"
                IssueStateFilter.ALL -> null
            }

        fun IssueSortField.apiParam(): String =
            when (this) {
                IssueSortField.CREATED -> "created"
                IssueSortField.UPDATED -> "updated"
                IssueSortField.COMMENTS -> "comments"
            }

        fun IssueSortDirection.apiParam(): String =
            when (this) {
                IssueSortDirection.ASC -> "asc"
                IssueSortDirection.DESC -> "desc"
            }
    }
}
