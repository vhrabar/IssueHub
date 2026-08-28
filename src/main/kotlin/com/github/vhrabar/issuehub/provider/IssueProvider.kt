package com.github.vhrabar.issuehub.provider

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueDetail
import com.github.vhrabar.issuehub.model.IssueFilterOptions
import com.github.vhrabar.issuehub.model.IssueQuery
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project

interface IssueProvider {
    val identifier: String
    val displayName: String

    /**
     * The API root this provider talks to when the user hasn't named a server.
     *
     * An account always carries a server, even for a provider with only one, so that adding a
     * self-hosted or Enterprise instance later changes settings rather than every call site.
     */
    val defaultServerUrl: String get() = ""

    /** whether this provider can serve the givenn project */
    fun isApplicable(project: Project): Boolean

    /**
     * Asks [serverUrl] who [token] belongs to, and what it is allowed to do.
     *
     * Throws when the token is refused — that is the answer the settings page shows. Providers that
     * can't check a token are the ones that don't override this, and their accounts stay unverified.
     */
    suspend fun verifyToken(
        serverUrl: String,
        token: String,
    ): AccountVerification? = null

    /**
     * Where the user creates a token for [serverUrl], if the provider has such a page.
     *
     * Providers spell this differently and put it in different places, so the settings page links
     * to it rather than trying to explain where to look.
     */
    fun tokenPageUrl(serverUrl: String): String? = null

    /** One line on what a token needs to carry, shown next to the field it is pasted into. */
    fun tokenHint(): String? = null

    /**
     * Accounts the IDE itself already holds for this provider, offered as an alternative to pasting
     * a token. Empty when there are none, or when the IDE has no such notion for this provider.
     */
    suspend fun importableAccounts(): List<ImportableAccount> = emptyList()

    /** HR desc of src, or null */
    fun sourceLabel(project: Project): String?

    /** fetch issues for [project] */
    suspend fun fetchIssues(
        project: Project,
        query: IssueQuery,
    ): List<Issue>

    /** values the filter UI can offer; empty when the provider can't enumerate them */
    suspend fun fetchFilterOptions(project: Project): IssueFilterOptions = IssueFilterOptions()

    /**
     * The full issue behind a list row, for the detail view.
     *
     * Null when the provider can't serve one, so the caller falls back to the row it already
     * has instead of failing.
     */
    suspend fun fetchIssueDetail(
        project: Project,
        issue: Issue,
    ): IssueDetail? = null

    companion object {
        val EP_NAME: ExtensionPointName<IssueProvider> =
            ExtensionPointName.create("com.github.vhrabar.issuehub.issueProvider")

        /** First provider that applies to [project], or null if none is configured. */
        fun firstApplicable(project: Project): IssueProvider? = EP_NAME.extensionList.firstOrNull { it.isApplicable(project) }
    }
}
