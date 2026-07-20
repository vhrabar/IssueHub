package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.intellij.openapi.project.Project


class GithubIssueProvider: IssueProvider {
    private var client = null

    override val identifier = PROVIDER_IDENTIFIER
    override val displayName = "GitHub"

    override fun isApplicable(project: Project): Boolean = true

    override fun sourceLabel(project: Project): String? = null

    override suspend fun fetchIssues(project: Project, query: IssueQuery): List<Issue> {
       return emptyList()
    }


    companion object {
        const val PROVIDER_IDENTIFIER = "github"
    }
}