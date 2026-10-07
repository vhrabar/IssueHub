package com.github.vhrabar.issuehub.provider.github

import com.intellij.openapi.project.Project
import java.io.File

/** Coordinates of a GitHub repository */
data class RepoCoordinates(
    val owner: String,
    val name: String,
) {
    override fun toString() = "$owner/$name"
}

/** dummy GH repo detector taht parser `.git/config` for remote URL */
object RepoDetector {
    private val remoteUrlRegex = Regex("""url\s*=\s*(\S+)""")

    fun detect(project: Project): RepoCoordinates? {
        val basePath = project.basePath ?: return null
        val config = File(basePath, ".git/config")
        if (!config.isFile) return null

        return config
            .readLines()
            .mapNotNull { remoteUrlRegex.find(it.trim())?.groupValues?.get(1) }
            .firstNotNullOfOrNull { parseGitHubUrl(it) }
    }

    /** Handles both `git@github.com:owner/name.git` and `https://github.com/owner/name(.git)`. */
    fun parseGitHubUrl(url: String): RepoCoordinates? {
        val withoutGit = url.trim().removeSuffix(".git")

        val hostIndex = withoutGit.indexOf(GITHUB_HOST)
        if (hostIndex < 0) return null

        val afterHost = withoutGit.substring(hostIndex + GITHUB_HOST.length)
        if (afterHost.isEmpty() || (afterHost[0] != ':' && afterHost[0] != '/')) return null

        val parts = afterHost.trim(':', '/').split('/')
        if (parts.size < 2 || parts[0].isBlank() || parts[1].isBlank()) return null
        return RepoCoordinates(parts[0], parts[1])
    }

    private const val GITHUB_HOST = "github.com"
}
