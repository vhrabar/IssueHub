package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.editor.IssueVirtualFile
import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueFilterOptions
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.optionsFrom
import com.github.vhrabar.issuehub.provider.AuthenticationRequiredException
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.github.vhrabar.issuehub.settings.IssueHubConfigurable
import com.github.vhrabar.issuehub.settings.IssueHubRepositoryConfigurable
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.Content
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

        val content = ContentFactory.getInstance().createContent(panel, IssueHubBundle["toolWindow.title"], false)
        content.setDisposer(panel)
        toolWindow.contentManager.addContent(content)
        panel.content = content
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

        // Shown when the fix is on a settings page, so the way there is one click from the message.
        private var statusLinkAction: () -> Unit = {}
        private val statusLink = ActionLink("") { statusLinkAction() }.apply { isVisible = false }
        private val cardLayout = CardLayout()
        private val center =
            JBPanel<JBPanel<*>>(cardLayout).apply {
                add(
                    JBPanel<JBPanel<*>>(BorderLayout()).apply {
                        border = JBUI.Borders.empty(10)
                        add(
                            JBPanel<JBPanel<*>>(BorderLayout(0, JBUI.scale(6))).apply {
                                add(statusLabel, BorderLayout.NORTH)
                                add(statusLink, BorderLayout.WEST)
                            },
                            BorderLayout.NORTH,
                        )
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

        /** The tab this panel sits in. Its title follows the repository, which a settings page can change. */
        var content: Content? = null
            set(value) {
                field = value
                showSource(IssueProvider.firstApplicable(project))
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
            actions.add(
                JButton(IssueHubBundle["toolWindow.settings"]).apply {
                    addActionListener { openAccountSettings() }
                },
            )
            return actions
        }

        /** Reloads on the way back: the accounts page is where a token is added or dropped. */
        private fun openAccountSettings() {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, IssueHubConfigurable::class.java)
            refresh(reloadOptions = true)
        }

        /** Reloads on the way back, since naming a repository is what makes a provider apply. */
        private fun openRepositorySettings() {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, IssueHubRepositoryConfigurable::class.java)
            refresh(reloadOptions = true)
        }

        /** [link] is the text and target of a settings page that would fix what [text] describes. */
        private fun showStatus(
            text: String,
            link: Pair<String, () -> Unit>? = null,
        ) {
            statusLabel.text = text
            statusLink.isVisible = link != null
            link?.let { (label, action) ->
                statusLink.text = label
                statusLinkAction = action
            }
            cardLayout.show(center, STATUS_CARD)
        }

        private fun showFailure(
            provider: IssueProvider,
            error: Throwable,
        ) {
            if (error !is AuthenticationRequiredException) {
                showStatus(IssueHubBundle["toolWindow.error", error.message ?: error.toString()])
                return
            }
            val key =
                when (error.reason) {
                    AuthenticationRequiredException.Reason.MISSING -> "toolWindow.auth.missing"
                    AuthenticationRequiredException.Reason.REJECTED -> "toolWindow.auth.rejected"
                }
            listModel.clear()
            showStatus(
                IssueHubBundle[key, provider.displayName],
                IssueHubBundle["toolWindow.auth.openSettings"] to ::openAccountSettings,
            )
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

        /** Names the tab after the repository, with the full `owner/name` as its tooltip. */
        private fun showSource(provider: IssueProvider?) {
            val tab = content ?: return
            val source = provider?.sourceLabel(project)
            tab.displayName = source?.substringAfterLast('/') ?: IssueHubBundle["toolWindow.title"]
            tab.description = source
        }

        /**
         * [reloadOptions] re-reads the label/assignee/milestone lists too; they barely ever change,
         * so filter and search changes skip that round trip and only re-run the query.
         */
        private fun refresh(reloadOptions: Boolean) {
            val provider = IssueProvider.firstApplicable(project)
            showSource(provider)
            if (provider == null) {
                showStatus(
                    IssueHubBundle["toolWindow.noProvider"],
                    IssueHubBundle["toolWindow.repository.open"] to ::openRepositorySettings,
                )
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
                        }.onFailure { showFailure(provider, it) }
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
