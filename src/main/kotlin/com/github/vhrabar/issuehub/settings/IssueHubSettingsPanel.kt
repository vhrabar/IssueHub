package com.github.vhrabar.issuehub.settings

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.provider.AccountVerification
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.intellij.openapi.ui.Messages
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton
import javax.swing.JComponent

/**
 * The accounts page: one section per provider, each listing what is stored for it.
 *
 * Nothing in here is GitHub specific. Sections are built from the provider extensions, so a GitLab
 * provider shows up with its own default server, its own token page and its own required scopes
 * without this class being told about it.
 *
 * Edits are kept in memory until **Apply**, the way a settings page is expected to behave. Adding
 * an account does verify the token right away, since that request is the point of the dialog, but
 * nothing reaches storage until the page is applied.
 */
internal class IssueHubSettingsPanel : JBPanel<IssueHubSettingsPanel>(BorderLayout()) {
    /**
     * An account as the page currently sees it. [token] is set only for accounts added in this
     * sitting, and is what marks them as not yet saved; [verification] is the result of checking
     * that token.
     */
    private data class PendingAccount(
        val account: IssueHubAccount,
        val token: String? = null,
        val verification: AccountVerification? = null,
    )

    private val pending = mutableListOf<PendingAccount>()

    private val sections = JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(SECTION_GAP)))

    init {
        border = JBUI.Borders.empty(10)
        add(
            JBLabel(IssueHubBundle["settings.accounts.intro"]).apply {
                foreground = UIUtil.getContextHelpForeground()
                border = JBUI.Borders.emptyBottom(SECTION_GAP)
            },
            BorderLayout.NORTH,
        )
        add(sections, BorderLayout.CENTER)
        reset()
    }

    fun isModified(): Boolean {
        val stored = IssueHubAccounts.getInstance().accounts
        return pending.any { it.token != null } || stored.any { account -> pending.none { it.account.id == account.id } }
    }

    /** Removals first, so replacing an account on the same server doesn't clash with the old one. */
    fun apply() {
        val accounts = IssueHubAccounts.getInstance()
        accounts.accounts
            .filter { stored -> pending.none { it.account.id == stored.id } }
            .forEach(accounts::remove)
        pending.forEach { entry ->
            entry.token?.let { accounts.add(entry.account.providerId, entry.account.serverUrl, entry.account.login, it) }
        }
        reset()
    }

    fun reset() {
        pending.clear()
        IssueHubAccounts.getInstance().accounts.forEach { pending += PendingAccount(it) }
        render()
    }

    private fun render() {
        sections.removeAll()
        IssueProvider.EP_NAME.extensionList.forEach { sections.add(section(it)) }
        revalidate()
        repaint()
    }

    private fun section(provider: IssueProvider): JComponent =
        JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(ROW_GAP))).apply {
            add(
                JBLabel(provider.displayName).apply {
                    font = JBFont.label().asBold()
                    border =
                        JBUI.Borders.compound(
                            JBUI.Borders.customLineBottom(JBColor.border()),
                            JBUI.Borders.emptyBottom(ROW_GAP),
                        )
                },
            )
            val accounts = pending.filter { it.account.providerId == provider.identifier }
            if (accounts.isEmpty()) {
                add(JBLabel(IssueHubBundle["settings.accounts.none"]).apply { foreground = UIUtil.getContextHelpForeground() })
            } else {
                accounts.forEach { add(accountRow(it)) }
            }
            add(buttons(provider))
        }

    /** Login and server, what the token can do, and a link to remove it. */
    private fun accountRow(entry: PendingAccount): JComponent =
        JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(ROW_GAP), 0)).apply {
            border = JBUI.Borders.emptyTop(ROW_GAP)
            add(
                JBPanel<JBPanel<*>>(VerticalLayout(0)).apply {
                    val who = entry.account.login.ifBlank { IssueHubBundle["settings.account.unverified"] }
                    add(JBLabel("$who — ${entry.account.serverUrl}"))
                    add(
                        JBLabel(describe(entry)).apply {
                            foreground =
                                if (entry.verification
                                        ?.missingScopes
                                        .orEmpty()
                                        .isEmpty()
                                ) {
                                    UIUtil.getContextHelpForeground()
                                } else {
                                    // A missing scope only costs a feature, so warn about it instead of erroring.
                                    JBColor.namedColor("Component.warningFocusColor", JBColor.ORANGE)
                                }
                        },
                    )
                },
                BorderLayout.CENTER,
            )
            add(
                ActionLink(IssueHubBundle["settings.account.remove"]) {
                    pending.remove(entry)
                    render()
                },
                BorderLayout.EAST,
            )
        }

    private fun describe(entry: PendingAccount): String {
        val verification =
            entry.verification
                ?: return IssueHubBundle[if (entry.token == null) "settings.account.unverified" else "settings.account.pending"]
        verification.missingScopes.firstOrNull()?.let { return IssueHubBundle["settings.account.missingScope", it] }
        val granted = verification.grantedScopes ?: return IssueHubBundle["settings.account.scopesUnknown"]
        return IssueHubBundle["settings.account.scopes", granted.joinToString(", ").ifBlank { "—" }]
    }

    private fun buttons(provider: IssueProvider): JComponent =
        JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(ROW_GAP), JBUI.scale(ROW_GAP))).apply {
            add(
                JButton(IssueHubBundle["settings.account.add"]).apply {
                    addActionListener { addAccount(provider) }
                },
            )
            add(
                JButton(IssueHubBundle["settings.account.addFromIde"]).apply {
                    addActionListener { importAccount(provider) }
                },
            )
        }

    private fun addAccount(provider: IssueProvider) {
        val dialog = AddAccountDialog(provider)
        if (!dialog.showAndGet()) return
        val entered = dialog.entered ?: return
        accept(provider, entered.serverUrl, entered.verification?.login.orEmpty(), entered.token, entered.verification)
    }

    /**
     * Reuses an account the IDE is already signed in with. The IDE issued that token for its own
     * needs, so verify it like any other one; otherwise the user only finds out it can't read
     * project boards when a section turns up empty.
     */
    private fun importAccount(provider: IssueProvider) {
        val available = ChooseIdeAccountDialog.load(provider)
        if (available.isEmpty()) {
            Messages.showInfoMessage(this, IssueHubBundle["settings.account.ideNone"], IssueHubBundle["settings.account.ideDialogTitle"])
            return
        }
        val dialog = ChooseIdeAccountDialog(available)
        if (!dialog.showAndGet()) return
        val chosen = dialog.chosen ?: return
        val verification = verifyQuietly(provider, chosen.serverUrl, chosen.token)
        accept(provider, chosen.serverUrl, verification?.login ?: chosen.login, chosen.token, verification)
    }

    private fun verifyQuietly(
        provider: IssueProvider,
        serverUrl: String,
        token: String,
    ): AccountVerification? = runCatching { verifyWithProgress(provider, serverUrl, token) }.getOrNull()

    private fun accept(
        provider: IssueProvider,
        serverUrl: String,
        login: String,
        token: String,
        verification: AccountVerification?,
    ) {
        val duplicate =
            pending.any {
                it.account.providerId == provider.identifier && it.account.serverUrl == serverUrl && it.account.login == login
            }
        if (duplicate) {
            Messages.showInfoMessage(this, IssueHubBundle["settings.account.duplicate", login], provider.displayName)
            return
        }
        // Not saved yet, so there is no credential-store id. apply() generates the real one.
        pending += PendingAccount(IssueHubAccount(PENDING_ID, provider.identifier, serverUrl, login), token, verification)
        render()
    }

    private companion object {
        const val SECTION_GAP = 12
        const val ROW_GAP = 4

        /** Stand-in id for unsaved accounts. Nothing looks a pending account up by id. */
        const val PENDING_ID = ""
    }
}
