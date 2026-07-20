package com.github.vhrabar.issuehub.provider

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueQuery
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface IssueProvider {
    val identifier: String
    val displayName: String

    /** whether this provider can serve the givenn project */
    fun isApplicable(project: Project): Boolean

    /** HR desc of src, or null */
    fun sourceLabel(project: Project): String?

    /** fetch issues for [project] */
    suspend fun fetchIssues(project: Project, query: IssueQuery): List<Issue>

    companion object {
        val EP_NAME: ExtensionPointName<IssueProvider> =
            ExtensionPointName.create("com.github.vhrabar.issuehub.issueProvider")

        /** First provider that applies to [project], or null if none is configured. */
        fun firstApplicable(project: Project): IssueProvider? =
            EP_NAME.extensionList.firstOrNull { it.isApplicable(project) }
    }
}