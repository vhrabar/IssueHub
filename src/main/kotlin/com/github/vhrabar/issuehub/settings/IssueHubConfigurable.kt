package com.github.vhrabar.issuehub.settings

import com.github.vhrabar.issuehub.IssueHubBundle
import com.intellij.openapi.options.Configurable
import javax.swing.JComponent

/** IssueHub's page under **Settings | Tools**. Just the accounts, for now. */
internal class IssueHubConfigurable : Configurable {
    private var panel: IssueHubSettingsPanel? = null

    override fun getDisplayName(): String = IssueHubBundle["settings.displayName"]

    override fun createComponent(): JComponent = IssueHubSettingsPanel().also { panel = it }

    override fun isModified(): Boolean = panel?.isModified() ?: false

    override fun apply() {
        panel?.apply()
    }

    override fun reset() {
        panel?.reset()
    }

    override fun disposeUIResources() {
        panel = null
    }
}
