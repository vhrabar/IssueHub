package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.github.vhrabar.issuehub.provider.github.GitHubIssueProvider
import com.github.vhrabar.issuehub.settings.IssueHubSecrets
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingUtilities
import javax.swing.ToolTipManager

class IssueHubToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = IssueHubToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, null, false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    private class IssueHubToolWindowPanel(private val project: Project) :
        JBPanel<IssueHubToolWindowPanel>(BorderLayout()) {

        private val listModel = DefaultListModel<Issue>()
        private val issueList = object : JBList<Issue>(listModel) {
            override fun getToolTipText(event: MouseEvent): String? {
                val index = locationToIndex(event.point)
                if (index < 0) return null
                val bounds = getCellBounds(index, index)?.takeIf { it.contains(event.point) } ?: return null
                val renderer = cellRenderer.getListCellRendererComponent(
                    this, model.getElementAt(index), index, false, false,
                ) as? JComponent ?: return null
                renderer.bounds = bounds
                layoutTree(renderer)
                val target = SwingUtilities.getDeepestComponentAt(renderer, event.x - bounds.x, event.y - bounds.y)
                return (target as? JComponent)?.toolTipText
            }
        }.apply {
            selectionMode = ListSelectionModel.SINGLE_SELECTION
            cellRenderer = IssueCellRenderer()
            ToolTipManager.sharedInstance().registerComponent(this)
        }

        // CENTER swaps between a status message and the issue list.
        private val statusLabel = JBLabel(IssueHubBundle["toolWindow.placeholder"])
        private val cardLayout = CardLayout()
        private val center = JBPanel<JBPanel<*>>(cardLayout).apply {
            add(JBPanel<JBPanel<*>>(BorderLayout()).apply {
                border = JBUI.Borders.empty(10)
                add(statusLabel, BorderLayout.NORTH)
            }, STATUS_CARD)
            // Rows ellipsize to the viewport width, so a horizontal scrollbar would never be useful.
            add(
                JBScrollPane(issueList).apply {
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                    border = JBUI.Borders.empty()
                },
                LIST_CARD,
            )
        }

        init {
            add(buildToolbar(), BorderLayout.NORTH)
            add(center, BorderLayout.CENTER)

            issueList.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.clickCount == 2) {
                        issueList.selectedValue?.url?.let { BrowserUtil.browse(it) }
                    }
                }
            })

            refresh()
        }

        private fun buildToolbar(): JBPanel<*> {
            val toolbar = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(4)))
            toolbar.border = JBUI.Borders.empty(4)
            toolbar.add(JButton(IssueHubBundle["toolWindow.refresh"]).apply {
                addActionListener { refresh() }
            })
            // TODO: temporary placeholder until a proper settings UI exists.
            toolbar.add(JButton(IssueHubBundle["toolWindow.addToken"]).apply {
                addActionListener { promptForToken() }
            })
            return toolbar
        }

        private fun showStatus(text: String) {
            statusLabel.text = text
            cardLayout.show(center, STATUS_CARD)
        }

        private fun showIssues(issues: List<Issue>) {
            listModel.clear()
            if (issues.isEmpty()) {
                showStatus(IssueHubBundle["toolWindow.empty"])
                return
            }
            issues.forEach(listModel::addElement)
            cardLayout.show(center, LIST_CARD)
        }

        private fun refresh() {
            val provider = IssueProvider.firstApplicable(project)
            if (provider == null) {
                showStatus(IssueHubBundle["toolWindow.noProvider"])
                return
            }
            showStatus(IssueHubBundle["toolWindow.loading"])
            // fetchIssues is a suspend fn doing network IO
            ApplicationManager.getApplication().executeOnPooledThread {
                val result = runCatching { runBlocking { provider.fetchIssues(project, IssueQuery()) } }
                ApplicationManager.getApplication().invokeLater {
                    result
                        .onSuccess { showIssues(it) }
                        .onFailure { showStatus(IssueHubBundle["toolWindow.error", it.message ?: it.toString()]) }
                }
            }
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
                    refresh()
                }
            }
        }

        private companion object {
            const val STATUS_CARD = "status"
            const val LIST_CARD = "list"
        }
    }
}

/** Recursively runs each container's layout so a detached renderer tree has valid child bounds. */
private fun layoutTree(component: Component) {
    component.doLayout()
    if (component is Container) component.components.forEach(::layoutTree)
}