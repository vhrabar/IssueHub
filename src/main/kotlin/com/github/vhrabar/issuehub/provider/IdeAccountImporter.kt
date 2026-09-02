package com.github.vhrabar.issuehub.provider

import com.intellij.openapi.extensions.ExtensionPointName

/**
 * Reads accounts out of an IDE feature that already holds them, on behalf of one provider.
 *
 */
interface IdeAccountImporter {
    /** [IssueProvider.identifier] of the provider these accounts belong to. */
    val providerIdentifier: String

    /** Reads the accounts, tokens included. Called only when the user asks to import one. */
    suspend fun accounts(): List<ImportableAccount>

    companion object {
        val EP_NAME: ExtensionPointName<IdeAccountImporter> =
            ExtensionPointName.create("com.github.vhrabar.issuehub.ideAccountImporter")

        fun forProvider(providerIdentifier: String): List<IdeAccountImporter> =
            EP_NAME.extensionList.filter { it.providerIdentifier == providerIdentifier }
    }
}
