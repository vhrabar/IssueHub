package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.AssigneeFilter
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueSortDirection
import com.github.vhrabar.issuehub.model.IssueSortField
import com.github.vhrabar.issuehub.model.IssueStateFilter
import com.github.vhrabar.issuehub.model.MilestoneFilter
import com.github.vhrabar.issuehub.provider.NetworkUnavailableException
import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpTimeoutException
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.time.Instant
import kotlin.collections.filterNot

class GitHubApiException(
    message: String,
    /** The HTTP status GitHub answered with; null when the request never got that far. */
    val status: Int? = null,
    /** When GitHub will answer again; set only when the refusal was a rate limit. */
    val rateLimitResetAt: Instant? = null,
) : Exception(message)

/**
 * When a refused request may be retried, or null when the refusal wasn't a rate limit.
 *
 * GitHub answers an exhausted allowance with 403 or 429 and `X-RateLimit-Remaining: 0`, and a
 * secondary limit (too many requests too fast) with `Retry-After`, or with neither header and only
 * a message saying so. A plain 403 is a permission problem, which retrying won't fix.
 */
internal fun rateLimitResetAt(
    status: Int,
    header: (String) -> String?,
    body: String,
    now: Instant,
): Instant? {
    if (status != 403 && status != 429) return null
    header("Retry-After")?.trim()?.toLongOrNull()?.let { return now.plusSeconds(it) }
    val exhausted = header("X-RateLimit-Remaining")?.trim() == "0"
    if (exhausted) {
        header("X-RateLimit-Reset")?.trim()?.toLongOrNull()?.let { return Instant.ofEpochSecond(it) }
    }
    return if (exhausted || status == 429 || body.contains("rate limit", ignoreCase = true)) {
        // GitHub's advice when it names no time: wait at least a minute.
        now.plusSeconds(60)
    } else {
        null
    }
}

/**
 * What a request that never got an HTTP answer ran into, worded for the tool window.
 *
 * The JDK client reports a timeout as [HttpTimeoutException] (connect timeouts included, as its
 * subclass), and everything else, from a refused connection to a host name that didn't resolve,
 * as some other [IOException], often with the real reason only in its cause. None of those is
 * worth telling apart for the user: the server can't be reached, and retrying later may help.
 */
internal fun classifyNetworkFailure(
    error: IOException,
    host: String?,
): NetworkUnavailableException {
    val server = host ?: "GitHub"
    return if (error is HttpTimeoutException) {
        NetworkUnavailableException(NetworkUnavailableException.Kind.TIMEOUT, host, "$server didn't answer in time.", error)
    } else {
        NetworkUnavailableException(
            NetworkUnavailableException.Kind.OFFLINE,
            host,
            "Couldn't reach $server. Check your network connection.",
            error,
        )
    }
}

/** How many times a request is sent before a transient failure is given up on: once, plus two retries. */
internal const val MAX_ATTEMPTS = 2

/** The pause before the retry; long enough for a proxy or load balancer hiccup to clear. */
internal val RETRY_DELAY: Duration = Duration.ofSeconds(2)

/**
 * Whether a request that ended with [status], or never got an answer because of [failure], is
 * worth sending again.
 *
 * Only failures that say nothing about the request itself qualify: a gateway that couldn't reach
 * GitHub (502, 504), GitHub briefly unavailable (503), or no answer in time. Being offline isn't
 * retried, since a second later the network is just as gone, and neither is any 4xx: a rate limit,
 * a bad token or a missing repository won't change by asking again.
 */
internal fun isTransient(
    status: Int?,
    failure: NetworkUnavailableException? = null,
): Boolean = failure?.kind == NetworkUnavailableException.Kind.TIMEOUT || status in TRANSIENT_STATUSES

private val TRANSIENT_STATUSES = setOf(502, 503, 504)

/** The account behind a token, as `/user` and its headers describe it. */
internal data class GitHubViewer(
    val login: String,
    /** Null when GitHub doesn't say, which is how a fine-grained token always answers. */
    val scopes: List<String>?,
)

/** `X-OAuth-Scopes` is a comma-separated list, and empty for a token that was granted none. */
internal fun parseScopes(header: String): List<String> = header.split(",").map { it.trim() }.filter { it.isNotEmpty() }

/** One page of issues, with pull requests already dropped. */
internal data class GitHubIssuePage(
    val issues: List<GitHubIssueDto>,
    val hasNext: Boolean,
    /** Only the search endpoint counts its matches; the plain issue list doesn't. */
    val totalCount: Int? = null,
)

/**
 * Whether a `Link` header points at a further page.
 *
 * GitHub lists `next`, `last`, `first` and `prev` as needed, each as `<url>; rel="name"`, and leaves
 * the header out entirely when everything fit on one page.
 */
internal fun hasNextPage(link: String?): Boolean =
    link
        ?.split(",")
        .orEmpty()
        .any { part -> part.substringAfter(";", "").split(";").any { it.trim() == "rel=\"next\"" } }

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
     *
     * Both endpoints say whether more pages follow in the `Link` header, which is the only reliable
     * signal: the issue list counts pull requests towards `per_page` and they are dropped here.
     */
    suspend fun fetchIssues(
        repo: RepoCoordinates,
        token: String?,
        query: IssueQuery,
    ): GitHubIssuePage {
        val search = query.text.isNotBlank()
        val response = getResponse(if (search) searchIssuesUri(repo, query) else listIssuesUri(repo, query), token)
        val hasNext = hasNextPage(response.headers().firstValue(LINK_HEADER).orElse(null))
        return if (search) {
            val result = json.decodeFromString<GitHubSearchResultDto>(response.body())
            GitHubIssuePage(result.items.filterNot { it.isPullRequest }, hasNext, result.totalCount)
        } else {
            val issues = json.decodeFromString<List<GitHubIssueDto>>(response.body())
            GitHubIssuePage(issues.filterNot { it.isPullRequest }, hasNext)
        }
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

    /**
     * Everything that happened to an issue after it was filed: comments, state changes, label and
     * assignee edits. One endpoint covers all of them, again on the `full` media type so comment
     * bodies arrive rendered.
     *
     * Pages are followed until one comes back short, up to [MAX_TIMELINE_PAGES]; a thread longer
     * than that is truncated rather than allowed to spend an unbounded number of requests.
     */
    suspend fun fetchTimeline(
        repo: RepoCoordinates,
        token: String?,
        number: Int,
    ): List<GitHubTimelineEventDto> =
        buildList {
            for (page in 1..MAX_TIMELINE_PAGES) {
                val batch =
                    get(timelineUri(repo, number, page), token, ACCEPT_FULL) {
                        json.decodeFromString<List<GitHubTimelineEventDto>>(it)
                    }
                addAll(batch)
                if (batch.size < TIMELINE_PER_PAGE) break
            }
        }

    @VisibleForTesting
    fun timelineUri(
        repo: RepoCoordinates,
        number: Int,
        page: Int,
    ): URI =
        URI.create(
            "$baseUrl/repos/${repo.owner}/${repo.name}/issues/$number/timeline" +
                "?per_page=$TIMELINE_PER_PAGE&page=$page",
        )

    /**
     * Who a token belongs to, and what it may do.
     *
     * The scopes ride on a response header rather than in the body, and a fine-grained token gets no
     * such header at all — GitHub doesn't publish that kind of permission — so [GitHubViewer.scopes]
     * comes back null there rather than empty.
     */
    suspend fun fetchViewer(token: String): GitHubViewer {
        val response = getResponse(URI.create("$baseUrl/user"), token)
        return GitHubViewer(
            login = json.decodeFromString<GitHubUserDto>(response.body()).login,
            scopes =
                response
                    .headers()
                    .firstValue(SCOPES_HEADER)
                    .orElse(null)
                    ?.let(::parseScopes),
        )
    }

    /**
     * The pull requests and branches opened for an issue, GitHub's "Development" section.
     */
    suspend fun fetchDevelopment(
        repo: RepoCoordinates,
        token: String?,
        number: Int,
    ): GitHubDevelopmentDto =
        graphQl(repo, token, number, DEVELOPMENT_QUERY) {
            json.decodeFromString<GitHubGraphQlResponse<GitHubRepositoryDataDto<GitHubDevelopmentDto>>>(it)
        }

    /**
     * The project boards the issue sits on, with the fields each board keeps for it.
     */
    suspend fun fetchProjectItems(
        repo: RepoCoordinates,
        token: String?,
        number: Int,
    ): GitHubProjectItemsDto =
        graphQl(repo, token, number, PROJECTS_QUERY) {
            json.decodeFromString<GitHubGraphQlResponse<GitHubRepositoryDataDto<GitHubProjectItemsDto>>>(it)
        }

    /**
     * The `/graphql` endpoint next to the REST base: `api.github.com/graphql` for github.com, and
     * `HOST/api/graphql` for an Enterprise install, whose REST lives under `HOST/api/v3`.
     */
    @VisibleForTesting
    fun graphQlUri(): URI =
        URI.create(
            if (baseUrl.endsWith(ENTERPRISE_REST_PATH)) {
                baseUrl.removeSuffix(ENTERPRISE_REST_PATH) + "/api/graphql"
            } else {
                "$baseUrl/graphql"
            },
        )

    /** Runs [query] for one issue and unwraps `data.repository.issue`, which is all any of them ask for. */
    private suspend fun <T> graphQl(
        repo: RepoCoordinates,
        token: String?,
        number: Int,
        query: String,
        decode: (String) -> GitHubGraphQlResponse<GitHubRepositoryDataDto<T>>,
    ): T {
        if (token.isNullOrBlank()) throw GitHubApiException("The GitHub GraphQL API needs a token.")
        val payload =
            buildJsonObject {
                put("query", query)
                putJsonObject("variables") {
                    put("owner", repo.owner)
                    put("name", repo.name)
                    put("number", number)
                }
            }
        val response = post(graphQlUri(), token, json.encodeToString(JsonObject.serializer(), payload), decode)
        return response.data?.repository?.issue
            ?: throw GitHubApiException(
                response.errors.firstOrNull()?.message ?: "GitHub GraphQL returned no data for issue #$number.",
            )
    }

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
                add("page" to query.page.toString())
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
                "&per_page=${query.limit}" +
                "&page=${query.page}"
        return URI.create("$baseUrl/search/issues?$params")
    }

    private suspend fun <T> get(
        uri: URI,
        token: String?,
        accept: String = ACCEPT_JSON,
        decode: (String) -> T,
    ): T = decode(getResponse(uri, token, accept).body())

    /**
     * The whole response, for the one call that cares about a header rather than the body: what a
     * token is allowed to do is in `X-OAuth-Scopes`, not in any JSON GitHub sends back.
     */
    private suspend fun getResponse(
        uri: URI,
        token: String?,
        accept: String = ACCEPT_JSON,
    ): HttpResponse<String> =
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

            val response = send(requestBuilder.build())
            if (response.statusCode() !in 200..299) {
                thisLogger().warn("GET:GitHub API returned ${response.statusCode()} for ${uri.path}")
                throw failure(response)
            }

            response
        }

    /** GraphQL is one POST to one address; the query travels in the body rather than the path. */
    private suspend fun <T> post(
        uri: URI,
        token: String?,
        body: String,
        decode: (String) -> T,
    ): T =
        withContext(Dispatchers.IO) {
            val request =
                HttpRequest
                    .newBuilder(uri)
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", ACCEPT_JSON)
                    .header("Content-Type", "application/json")
                    .header("X-GitHub-Api-Version", "2026-03-10")
                    .apply { if (!token.isNullOrBlank()) header("Authorization", "Bearer $token") }
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build()

            val response = send(request)
            if (response.statusCode() !in 200..299) {
                thisLogger().warn("POST:GitHub API returned ${response.statusCode()} for ${uri.path}")
                throw failure(response)
            }

            decode(response.body())
        }

    /**
     * Sends [request], turning a failure to reach the server into [NetworkUnavailableException],
     * and sends it once more after [RETRY_DELAY] when the first try failed in a way [isTransient]
     * says may clear by itself. Every request here only reads, GraphQL's POSTs included, so sending
     * one twice is harmless.
     */
    private suspend fun send(request: HttpRequest): HttpResponse<String> {
        var attempt = 1
        while (true) {
            val response =
                try {
                    http.send(request, HttpResponse.BodyHandlers.ofString())
                } catch (e: IOException) {
                    val failure = classifyNetworkFailure(e, request.uri().host)
                    thisLogger().info("${request.method()}:GitHub unreachable for ${request.uri().path} (attempt $attempt): $e")
                    if (attempt < MAX_ATTEMPTS && isTransient(null, failure)) {
                        attempt++
                        delay(RETRY_DELAY.toMillis())
                        continue
                    }
                    throw failure
                }
            if (attempt < MAX_ATTEMPTS && isTransient(response.statusCode())) {
                thisLogger().info("${request.method()}:GitHub answered ${response.statusCode()} for ${request.uri().path}, retrying")
                attempt++
                delay(RETRY_DELAY.toMillis())
                continue
            }
            return response
        }
    }

    private fun failure(response: HttpResponse<String>): GitHubApiException {
        val status = response.statusCode()
        val resetAt = rateLimitResetAt(status, { response.headers().firstValue(it).orElse(null) }, response.body(), Instant.now())
        return if (resetAt != null) {
            GitHubApiException("GitHub rate limit exceeded ($status).", status, resetAt)
        } else {
            GitHubApiException(describeError(status), status)
        }
    }

    private fun describeError(status: Int): String =
        when (status) {
            401 -> "Authentication failed (401). Check your GitHub token."
            403 -> "Access forbidden (403). Check the token's access to this repository."
            404 -> "Repository not found (404). Check the owner/name and token scope."
            422 -> "GitHub rejected the search query (422)."
            else -> "GitHub API request failed with status $status."
        }

    private companion object {
        const val ACCEPT_JSON = "application/vnd.github+json"

        /** Adds `body_html` (and `body_text`) alongside the Markdown source. */
        const val ACCEPT_FULL = "application/vnd.github.full+json"

        /** Where GitHub lists what a classic token was granted; absent for fine-grained ones. */
        const val SCOPES_HEADER = "X-OAuth-Scopes"

        /** Where GitHub says whether more pages follow; RFC 8288 format. */
        const val LINK_HEADER = "Link"

        /** Filter dropdowns list every value at once; GitHub caps a page at 100. */
        const val OPTIONS_PER_PAGE = 100

        const val TIMELINE_PER_PAGE = 100

        /** 500 entries is far past what anyone scrolls, and bounds the requests one issue can cost. */
        const val MAX_TIMELINE_PAGES = 5

        /** Enterprise serves REST under this suffix and GraphQL as a sibling of it. */
        const val ENTERPRISE_REST_PATH = "/api/v3"

        /** Sidebar sections list what they have; an issue with more linked than this is unusual. */
        const val GRAPHQL_NODES = 20

        /** A board can carry a good few columns, and we show whichever of them are filled in. */
        const val PROJECT_FIELDS = 30

        /**
         * `$` is the sigil for a GraphQL variable, so every one of them has to survive Kotlin's own
         * reading of the string.
         */
        const val DOLLAR = "$"

        val DEVELOPMENT_QUERY =
            """
            query(${DOLLAR}owner: String!, ${DOLLAR}name: String!, ${DOLLAR}number: Int!) {
              repository(owner: ${DOLLAR}owner, name: ${DOLLAR}name) {
                issue(number: ${DOLLAR}number) {
                  closedByPullRequestsReferences(first: $GRAPHQL_NODES, includeClosedPrs: true) {
                    nodes { number title url state isDraft }
                  }
                  linkedBranches(first: $GRAPHQL_NODES) {
                    nodes { ref { name repository { url } } }
                  }
                }
              }
            }
            """.trimIndent()

        /**
         * Every field type worth showing is asked for in one selection set, so a board's own columns
         * — status, size, estimate, start and target dates, iteration — come back whatever the user
         * called them. Types we don't list arrive as empty objects and are dropped on the way in.
         */
        val PROJECTS_QUERY =
            """
            query(${DOLLAR}owner: String!, ${DOLLAR}name: String!, ${DOLLAR}number: Int!) {
              repository(owner: ${DOLLAR}owner, name: ${DOLLAR}name) {
                issue(number: ${DOLLAR}number) {
                  projectItems(first: $GRAPHQL_NODES) {
                    nodes {
                      project { title url }
                      fieldValues(first: $PROJECT_FIELDS) {
                        nodes {
                          ... on ProjectV2ItemFieldTextValue { text field { ... on ProjectV2FieldCommon { name } } }
                          ... on ProjectV2ItemFieldNumberValue { number field { ... on ProjectV2FieldCommon { name } } }
                          ... on ProjectV2ItemFieldDateValue { date field { ... on ProjectV2FieldCommon { name } } }
                          ... on ProjectV2ItemFieldSingleSelectValue { name field { ... on ProjectV2FieldCommon { name } } }
                          ... on ProjectV2ItemFieldIterationValue { title field { ... on ProjectV2FieldCommon { name } } }
                        }
                      }
                    }
                  }
                }
              }
            }
            """.trimIndent()

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
