package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.editor.IssueVirtualFile
import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueFilterOptions
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.optionsFrom
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.github.vhrabar.issuehub.provider.github.GitHubIssueProvider
import com.github.vhrabar.issuehub.settings.IssueHubSecrets
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
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
    // setDisposer has no property form: getDisposer is nullable, setDisposer is not
    @Suppress("UnstableApiUsage", "UsePropertyAccessSyntax")
    override fun createToolWindowContent(
        project: Project,
        toolWindow: ToolWindow,
    ) {
        val panel = IssueHubToolWindowPanel(project)
        toolWindow.component.putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true")

        val source = IssueProvider.firstApplicable(project)?.sourceLabel(project)
        val content =
            ContentFactory.getInstance().createContent(
                panel,
                source?.substringAfterLast('/') ?: IssueHubBundle["toolWindow.title"],
                false,
            )
        content.description = source
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project) = true

    private class IssueHubToolWindowPanel(
        private val project: Project,
    ) : JBPanel<IssueHubToolWindowPanel>(BorderLayout()),
        Disposable {
        private val listModel = DefaultListModel<Issue>()
        private val issueList =
            object : JBList<Issue>(listModel) {
                override fun getToolTipText(event: MouseEvent): String? {
                    val index = locationToIndex(event.point)
                    if (index < 0) return null
                    val bounds = getCellBounds(index, index)?.takeIf { it.contains(event.point) } ?: return null
                    val renderer =
                        cellRenderer.getListCellRendererComponent(
                            this,
                            model.getElementAt(index),
                            index,
                            false,
                            false,
                        ) as? JComponent ?: return null
                    renderer.bounds = bounds
                    layoutTree(renderer)
                    val target = SwingUtilities.getDeepestComponentAt(renderer, event.x - bounds.x, event.y - bounds.y)
                    return (target as? JComponent)?.toolTipText
                }
            }.apply {
                selectionMode = ListSelectionModel.SINGLE_SELECTION
                ToolTipManager.sharedInstance().registerComponent(this)
            }

        // Repaints the list once an avatar finishes downloading so the real picture replaces initials.
        private val avatarLoader = AvatarLoader(issueList::repaint)

        // CENTER swaps between a status message and the issue list.
        private val statusLabel = JBLabel(IssueHubBundle["toolWindow.placeholder"])
        private val cardLayout = CardLayout()
        private val center =
            JBPanel<JBPanel<*>>(cardLayout).apply {
                add(
                    JBPanel<JBPanel<*>>(BorderLayout()).apply {
                        border = JBUI.Borders.empty(10)
                        add(statusLabel, BorderLayout.NORTH)
                    },
                    STATUS_CARD,
                )
                // Rows ellipsize to the viewport width, so a horizontal scrollbar would never be useful.
                add(
                    JBScrollPane(issueList).apply {
                        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                        border = JBUI.Borders.empty()
                    },
                    LIST_CARD,
                )
            }

        /** Values the provider enumerated, and values merely seen on issues we've already loaded. */
        private var providerOptions = IssueFilterOptions()
        private var discoveredOptions = IssueFilterOptions()

        /** Guards against a slow response for an abandoned query overwriting a newer one. */
        private var requestId = 0

        private val filterBar =
            IssueFilterBar(this, buildActions()) { refresh(reloadOptions = false) }

        init {
            issueList.cellRenderer = IssueCellRenderer(avatarLoader)
            add(filterBar, BorderLayout.NORTH)
            add(center, BorderLayout.CENTER)

            issueList.addMouseListener(
                object : MouseAdapter() {
                    override fun mouseClicked(e: MouseEvent) {
                        if (e.clickCount == 2) {
                            issueList.selectedValue?.let(::openDetail)
                        }
                    }
                },
            )

            refresh(reloadOptions = true)
        }

        override fun dispose() = Unit

        /**
         * Opens [issue] in the editor area rather than inside this tool window, so the description gets
         * the full window width and can be split alongside code.
         */
        private fun openDetail(issue: Issue) = IssueVirtualFile.open(project, issue)

        private fun buildActions(): JBPanel<*> {
            val actions = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0))
            actions.add(
                JButton(IssueHubBundle["toolWindow.refresh"]).apply {
                    addActionListener { refresh(reloadOptions = true) }
                },
            )
            // TODO: temporary placeholder until a proper settings UI exists.
            actions.add(
                JButton(IssueHubBundle["toolWindow.addToken"]).apply {
                    addActionListener { promptForToken() }
                },
            )
            return actions
        }

        private fun showStatus(text: String) {
            statusLabel.text = text
            cardLayout.show(center, STATUS_CARD)
        }

        private fun showIssues(
            issues: List<Issue>,
            query: IssueQuery,
        ) {
            listModel.clear()
            if (issues.isEmpty()) {
                showStatus(IssueHubBundle[if (query.isFiltered) "toolWindow.emptyFiltered" else "toolWindow.empty"])
                return
            }
            issues.forEach(listModel::addElement)
            cardLayout.show(center, LIST_CARD)
        }

        /**
         * [reloadOptions] re-reads the label/assignee/milestone lists too; they barely ever change,
         * so filter and search changes skip that round trip and only re-run the query.
         */
        private fun refresh(reloadOptions: Boolean) {
            val provider = IssueProvider.firstApplicable(project)
            if (provider == null) {
                showStatus(IssueHubBundle["toolWindow.noProvider"])
                return
            }
            val query = filterBar.query
            val id = ++requestId
            showStatus(IssueHubBundle["toolWindow.loading"])
            // fetchIssues is a suspend fn doing network IO
            ApplicationManager.getApplication().executeOnPooledThread {
                val result = runCatching { runBlocking { provider.fetchIssues(project, query) } }
                val options =
                    if (reloadOptions) {
                        runCatching { runBlocking { provider.fetchFilterOptions(project) } }.getOrNull()
                    } else {
                        null
                    }
                ApplicationManager.getApplication().invokeLater {
                    if (id != requestId) return@invokeLater
                    options?.let { providerOptions = it }
                    result
                        .onSuccess { issues ->
                            discoveredOptions = discoveredOptions.mergedWith(optionsFrom(issues))
                            filterBar.setOptions(providerOptions.mergedWith(discoveredOptions))
                            showIssues(issues, query)
                        }.onFailure { showStatus(IssueHubBundle["toolWindow.error", it.message ?: it.toString()]) }
                }
            }
        }

        private fun promptForToken() {
            val token =
                Messages
                    .showPasswordDialog(
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
                    refresh(reloadOptions = true)
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
