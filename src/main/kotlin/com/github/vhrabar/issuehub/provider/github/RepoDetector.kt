package com.github.vhrabar.issuehub.provider.github

import com.intellij.openapi.project.Project
import java.io.File

/** Coordinates of a GitHub repository, and the web host it lives on: github.com or an Enterprise install. */
data class RepoCoordinates(
    val owner: String,
    val name: String,
    val host: String = RepoDetector.GITHUB_HOST,
) {
    override fun toString() = "$owner/$name"
}

/** dummy GH repo detector taht parser `.git/config` for remote URL */
object RepoDetector {
    const val GITHUB_HOST = "github.com"

    private val remoteUrlRegex = Regex("""url\s*=\s*(\S+)""")

    /**
     * The first remote that points at a GitHub server.
     *
     * A remote URL can't tell an Enterprise install from any other git host, so [knownHosts] names
     * the ones the user has an account on; github.com is always recognised.
     */
    fun detect(
        project: Project,
        knownHosts: Set<String> = emptySet(),
    ): RepoCoordinates? {
        val basePath = project.basePath ?: return null
        val config = File(basePath, ".git/config")
        if (!config.isFile) return null

        return config
            .readLines()
            .mapNotNull { remoteUrlRegex.find(it.trim())?.groupValues?.get(1) }
            .firstNotNullOfOrNull { parseGitHubUrl(it, knownHosts) }
    }

    /**
     * Handles the scp-like `git@HOST:owner/name.git` as well as `https://HOST/owner/name(.git)` and
     * `ssh://git@HOST:PORT/owner/name.git`. The host has to match exactly, so a look-alike such as
     * `github.com.example.org` isn't taken for GitHub.
     */
    fun parseGitHubUrl(
        url: String,
        knownHosts: Set<String> = emptySet(),
    ): RepoCoordinates? {
        val trimmed = url.trim().trimEnd('/').removeSuffix(".git")

        val (authority, path) =
            if ("://" in trimmed) {
                val rest = trimmed.substringAfter("://")
                rest.substringBefore('/') to rest.substringAfter('/', missingDelimiterValue = "")
            } else {
                // scp-like syntax; a slash before the colon would make it a local path instead
                val beforeColon = trimmed.substringBefore(':', missingDelimiterValue = "")
                if (beforeColon.isEmpty() || '/' in beforeColon) return null
                beforeColon to trimmed.substringAfter(':')
            }

        val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
        if (host != GITHUB_HOST && host !in knownHosts) return null

        val parts = path.trim('/').split('/')
        if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return RepoCoordinates(parts[0], parts[1], host)
    }
}
