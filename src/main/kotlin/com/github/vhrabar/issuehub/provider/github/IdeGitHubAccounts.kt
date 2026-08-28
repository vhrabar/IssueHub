package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.provider.ImportableAccount
import com.intellij.openapi.components.service
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager

/**
 * GitHub accounts from *Settings | Version Control | GitHub*.
 *
 * The only file that touches the bundled GitHub plugin, and we only get here after
 * [GitHubIssueProvider] has checked the plugin is installed. Its account API is internal and moves
 * around between releases, so callers treat any failure as "no accounts" and fall back to asking
 * for a token.
 */
internal object IdeGitHubAccounts {
    suspend fun accounts(): List<ImportableAccount> {
        val manager = service<GHAccountManager>()
        return GHAccountsUtil.accounts.mapNotNull { account ->
            val token = manager.findCredentials(account) ?: return@mapNotNull null
            ImportableAccount(account.name, account.server.toApiUrl(), token)
        }
    }
}
