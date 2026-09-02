package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.provider.IdeAccountImporter
import com.github.vhrabar.issuehub.provider.ImportableAccount
import com.intellij.openapi.components.service
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GHAccountManager

/**
 * GitHub accounts from *Settings | Version Control | GitHub*.
 *
 * The only file that touches the bundled GitHub plugin. It is registered from
 * `issuehub-withGitHubPlugin.xml`, which the IDE loads only when that plugin is there, so this
 * class is never reached — never even loaded — when it isn't. Its account API is internal and moves
 * around between releases, so callers treat any failure as "no accounts" and fall back to asking
 * for a token.
 */
internal class IdeGitHubAccounts : IdeAccountImporter {
    override val providerIdentifier: String = GitHubIssueProvider.PROVIDER_IDENTIFIER

    override suspend fun accounts(): List<ImportableAccount> {
        val manager = service<GHAccountManager>()
        return GHAccountsUtil.accounts.mapNotNull { account ->
            val token = manager.findCredentials(account) ?: return@mapNotNull null
            ImportableAccount(account.name, account.server.toApiUrl(), token)
        }
    }
}
