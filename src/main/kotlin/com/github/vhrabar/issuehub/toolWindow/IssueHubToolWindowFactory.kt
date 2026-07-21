package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.provider.github.GitHubIssueProvider
import com.github.vhrabar.issuehub.settings.IssueHubSecrets
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.JButton

class IssueHubToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = IssueHubToolWindowPanel()
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    private class IssueHubToolWindowPanel : JBPanel<IssueHubToolWindowPanel>(BorderLayout()) {
        init {
            border = JBUI.Borders.empty(10)
            add(JBLabel(IssueHubBundle["toolWindow.placeholder"]), BorderLayout.NORTH)

            // TODO: temporary placeholder until a proper settings UI exists.
            val actions = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, 0, 0))
            actions.add(JButton(IssueHubBundle["toolWindow.addToken"]).apply {
                addActionListener { promptForToken() }
            })
            add(actions, BorderLayout.CENTER)
        }

        private fun promptForToken() {
            val token = Messages.showPasswordDialog(
                IssueHubBundle["toolWindow.addToken.message"],
                IssueHubBundle["toolWindow.addToken.title"],
            )?.takeIf { it.isNotBlank() } ?: return

            ApplicationManager.getApplication().executeOnPooledThread {
                IssueHubSecrets.setToken(GitHubIssueProvider.PROVIDER_IDENTIFIER, token)
                ApplicationManager.getApplication().invokeLater {
                    Messages.showInfoMessage(
                        IssueHubBundle["toolWindow.addToken.saved"],
                        IssueHubBundle["toolWindow.addToken.title"],
                    )
                }
            }
        }
    }
}
