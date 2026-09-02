package com.github.vhrabar.issuehub.settings

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.provider.AccountVerification
import com.github.vhrabar.issuehub.provider.ImportableAccount
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.listCellRenderer.textListCellRenderer
import com.intellij.util.ui.FormBuilder
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import javax.swing.JComponent
import javax.swing.ListSelectionModel

/** A token the user typed in, plus the result of checking it. */
internal data class EnteredAccount(
    val serverUrl: String,
    val token: String,
    val verification: AccountVerification?,
)

/**
 * Checks a token against the server behind a progress dialog.
 *
 * Synchronous on purpose: it runs from a dialog the user is sitting in front of, and the answer
 * decides whether that dialog can close. Rethrows whatever the provider threw, because that
 * message is the one worth showing.
 */
internal fun verifyWithProgress(
    provider: IssueProvider,
    serverUrl: String,
    token: String,
): AccountVerification? =
    ProgressManager.getInstance().runProcessWithProgressSynchronously(
        ThrowableComputable { runBlocking { provider.verifyToken(serverUrl, token) } },
        IssueHubBundle["settings.account.verifying"],
        true,
        null,
    )

/**
 * Server URL and token for one account.
 *
 * The token is checked before the dialog closes, so a typo shows up here instead of as an empty
 * issue list later on. Providers that can't check tokens just take it as given.
 */
internal class AddAccountDialog(
    private val provider: IssueProvider,
) : DialogWrapper(true) {
    private val serverField = JBTextField(provider.defaultServerUrl, FIELD_COLUMNS)
    private val tokenField = JBPasswordField().apply { columns = FIELD_COLUMNS }

    var entered: EnteredAccount? = null
        private set

    init {
        title = IssueHubBundle["settings.account.dialogTitle", provider.displayName]
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = tokenField

    override fun createCenterPanel(): JComponent =
        FormBuilder
            .createFormBuilder()
            .addLabeledComponent(IssueHubBundle["settings.account.server"], serverField)
            .addLabeledComponent(IssueHubBundle["settings.account.token"], tokenField)
            .apply {
                provider.tokenHint()?.let { hint ->
                    addComponentToRightColumn(
                        JBLabel(hint).apply {
                            foreground = UIUtil.getContextHelpForeground()
                            border = JBUI.Borders.emptyTop(4)
                        },
                    )
                }
                addComponentToRightColumn(
                    ActionLink(IssueHubBundle["settings.account.createToken"]) {
                        provider.tokenPageUrl(serverField.text.trim())?.let(BrowserUtil::browse)
                    },
                )
            }.panel

    override fun doOKAction() {
        val server = serverField.text.trim().ifBlank { provider.defaultServerUrl }
        val token = String(tokenField.password).trim()
        if (token.isBlank()) {
            setErrorText(IssueHubBundle["settings.account.tokenRequired"], tokenField)
            return
        }
        runCatching { verifyWithProgress(provider, server, token) }
            .onSuccess { verification ->
                entered = EnteredAccount(server, token, verification)
                super.doOKAction()
            }.onFailure {
                setErrorText(IssueHubBundle["settings.account.rejected", it.message ?: it.toString()], tokenField)
            }
    }

    private companion object {
        const val FIELD_COLUMNS = 32
    }
}

/** Lets the user pick one of the accounts the IDE is already signed in with. */
internal class ChooseIdeAccountDialog(
    accounts: List<ImportableAccount>,
) : DialogWrapper(true) {
    private val list =
        JBList(accounts).apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            selectedIndex = 0
            cellRenderer =
                textListCellRenderer<ImportableAccount> { "${it.login} — ${it.serverUrl}" }
        }

    val chosen: ImportableAccount? get() = list.selectedValue

    init {
        title = IssueHubBundle["settings.account.ideDialogTitle"]
        init()
    }

    override fun getPreferredFocusedComponent(): JComponent = list

    override fun createCenterPanel(): JComponent = list

    companion object {
        /**
         * Loads them behind a progress dialog: every token comes out of the credential store,
         * which may mean unlocking a keychain.
         */
        fun load(provider: IssueProvider): List<ImportableAccount> =
            runCatching {
                ProgressManager.getInstance().runProcessWithProgressSynchronously(
                    ThrowableComputable { runBlocking { provider.importableAccounts() } },
                    IssueHubBundle["settings.account.ideLoading"],
                    true,
                    null,
                )
            }.getOrDefault(emptyList())
    }
}
