package com.github.vhrabar.issuehub.provider.github

import com.github.vhrabar.issuehub.provider.IdeAccountImporter
import com.github.vhrabar.issuehub.provider.ImportableAccount
import com.intellij.collaboration.auth.PasswordSafeCredentialsRepository
import org.jetbrains.plugins.github.authentication.GHAccountsUtil
import org.jetbrains.plugins.github.authentication.accounts.GithubAccount

/**
 * GitHub accounts from *Settings | Version Control | GitHub*.
 *
 * The only file that touches the bundled GitHub plugin. It is registered from
 * `issuehub-withGitHubPlugin.xml`, which the IDE loads only when that plugin is there, so this
 * class is never reached — never even loaded — when it isn't.
 *
 * Everything the GitHub plugin offers for reading a token is internal API from 2026.3 on, so the
 * token is read the way that plugin's own account manager stores it: a platform
 * [PasswordSafeCredentialsRepository] under the service name [GITHUB_CREDENTIALS_SERVICE], keyed
 * by account. Both are public platform API. If the plugin ever stores tokens elsewhere, accounts
 * simply come back without one and are skipped, and the user pastes a token instead.
 */
internal class IdeGitHubAccounts : IdeAccountImporter {
    override val providerIdentifier: String = GitHubIssueProvider.PROVIDER_IDENTIFIER

    override suspend fun accounts(): List<ImportableAccount> {
        val credentials =
            PasswordSafeCredentialsRepository<GithubAccount, String>(
                GITHUB_CREDENTIALS_SERVICE,
                PasswordSafeCredentialsRepository.CredentialsMapper.Simple,
            )
        @Suppress("DEPRECATION") // still the only public way to list them
        return GHAccountsUtil.accounts.mapNotNull { account ->
            val token = credentials.retrieveCredentials(account)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            ImportableAccount(account.name, account.server.toApiUrl(), token)
        }
    }

    private companion object {
        /** What `GHAccountManager` passes to its credentials repository, 2025.3 through 2026.3. */
        const val GITHUB_CREDENTIALS_SERVICE = "GitHub"
    }
}
