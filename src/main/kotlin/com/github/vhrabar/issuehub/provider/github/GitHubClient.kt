package com.github.vhrabar.issuehub.provider.github

import com.intellij.openapi.diagnostic.thisLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URI
import kotlinx.serialization.json.Json
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import kotlin.collections.filterNot


class GitHubApiException(message: String) : Exception(message)



/** REST client based on teh the JDK [HttpClient] */
internal class GitHubClient(private val baseUrl:String = "https://api.github.com") {
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(20))
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    suspend fun fetchIssues(repo: RepoCoordinates, token: String?, state: String = "open", perPage: Int = 50):List<GitHubIssueDto> = withContext(Dispatchers.IO) {
        val uri = URI.create("$baseUrl/repos/${repo.owner}/${repo.name}/issues?state=$state&per_page=$perPage")
        val requestBuilder = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(30))
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2026-03-10")
            .GET()
        if (!token.isNullOrBlank()) {
            requestBuilder.header("Authorization", "Bearer $token")
        }

        val response = http.send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() !in 200..299) {
            thisLogger().warn("GitHub API returned ${response.statusCode()} for $repo")
            throw GitHubApiException(describeError(response.statusCode()))
        }

        json.decodeFromString<List<GitHubIssueDto>>(response.body())
            .filterNot { it.isPullRequest }
    }

    private fun describeError(status: Int): String = when (status) {
        401 -> "Authentication failed (401). Check your GitHub token."
        403 -> "Access forbidden or rate limit exceeded (403)."
        404 -> "Repository not found (404). Check the owner/name and token scope."
        else -> "GitHub API request failed with status $status."
    }
}