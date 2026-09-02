package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueDetail
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.FlowLayout
import java.time.Instant
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ScrollPaneConstants

/**
 * The "main body" view of a single issue: title, metadata and the thread of activity below them.
 *
 * Opens against the list row it was launched from, so there is something on screen immediately,
 * then replaces it with the provider's fuller answer once that arrives.
 */
internal class IssueDetailPanel(
    private val project: Project,
    private var issue: Issue,
) : JBPanel<IssueDetailPanel>(BorderLayout()),
    Disposable {
    /**
     * Re-renders rather than repaints: the byline and assignee hold whatever icon they were given,
     * so a downloaded avatar only reaches the screen if we ask the loader for it again.
     */
    private val avatarLoader = AvatarLoader(::renderHeader)

    private val title =
        JBLabel().apply {
            font = JBFont.label().biggerOn(2f).asBold()
            icon = IssueStateIcon(issue.state)
            iconTextGap = JBUI.scale(6)
        }
    private val byline = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }

    /** Description and history, as one card per run of activity by the same account. */
    private val thread = IssueThreadPanel()

    /** Assignees, labels, projects, milestone and linked work, down the right the way GitHub has them. */
    private val sidebar = IssueSidebarPanel()

    private val statusLabel = JBLabel(IssueHubBundle["detail.loading"])
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
            add(
                JBScrollPane(thread).apply {
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                    border = JBUI.Borders.empty()
                },
                BODY_CARD,
            )
        }

    /** Guards against a slow reload landing after a newer one. */
    private var requestId = 0

    /**
     * Thread and sidebar side by side, on a divider the user can move and the IDE remembers, since
     * how much room a sidebar deserves depends on the boards a repository keeps.
     */
    private val body =
        OnePixelSplitter(false, SIDEBAR_PROPORTION_KEY, SIDEBAR_PROPORTION).apply {
            firstComponent = center
            secondComponent =
                JBScrollPane(sidebar).apply {
                    horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                    border = JBUI.Borders.empty()
                }
        }

    /** The thread, so an editor host can hand it the focus and arrow keys scroll straight away. */
    internal fun preferredFocusComponent(): JComponent = thread

    init {
        add(header(), BorderLayout.NORTH)
        add(body, BorderLayout.CENTER)
        renderHeader()
        refresh()
    }

    override fun dispose() = thread.dispose()

    private fun header(): JBPanel<*> =
        JBPanel<JBPanel<*>>(BorderLayout()).apply {
            border = JBUI.Borders.empty(8, 10, 4, 10)
            add(
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = false
                    add(title, BorderLayout.CENTER)
                    add(actions(), BorderLayout.EAST)
                },
                BorderLayout.NORTH,
            )
            add(byline, BorderLayout.CENTER)
        }

    private fun actions(): JBPanel<*> =
        JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.RIGHT, JBUI.scale(4), 0)).apply {
            isOpaque = false
            add(
                JButton(IssueHubBundle["detail.openInBrowser"]).apply {
                    addActionListener { BrowserUtil.browse(issue.url) }
                },
            )
            add(
                JButton(IssueHubBundle["detail.refresh"]).apply {
                    addActionListener { refresh() }
                },
            )
        }

    private fun renderHeader() {
        title.icon = IssueStateIcon(issue.state)
        title.text = issue.title
        title.toolTipText = issue.title

        val created = runCatching { DateFormatUtil.formatDate(Instant.parse(issue.createdAt).toEpochMilli()) }.getOrNull()
        byline.text =
            IssueHubBundle[
                "detail.byline",
                issue.displayNumber,
                issue.author?.login ?: IssueHubBundle["detail.unknownAuthor"],
                created ?: issue.createdAt,
                issue.commentCount,
            ]
        byline.icon = issue.author?.let { avatarLoader.avatar(it.avatarUrl, IssueAvatarIcon(it.login)) }
        byline.iconTextGap = JBUI.scale(6)

        revalidate()
        repaint()
    }

    /**
     * The provider re-reads the issue because only that response carries the rendered description;
     * a provider that can't serve details leaves us on the list row, which still has the source text.
     */
    private fun refresh() {
        val provider = IssueProvider.firstApplicable(project)
        if (provider == null) {
            showBody(null)
            return
        }
        val id = ++requestId
        statusLabel.text = IssueHubBundle["detail.loading"]
        cardLayout.show(center, STATUS_CARD)
        sidebar.showLoading(issue)

        ApplicationManager.getApplication().executeOnPooledThread {
            val result = runCatching { runBlocking { provider.fetchIssueDetail(project, issue) } }
            ApplicationManager.getApplication().invokeLater {
                if (id != requestId) return@invokeLater
                result
                    .onSuccess { detail ->
                        detail?.issue?.let { issue = it }
                        renderHeader()
                        showBody(detail)
                    }.onFailure {
                        statusLabel.text = IssueHubBundle["detail.error", it.message ?: it.toString()]
                        cardLayout.show(center, STATUS_CARD)
                        // The sidebar keeps what the list row knows rather than waiting on a request that failed.
                        sidebar.show(issue, null)
                    }
            }
        }
    }

    /**
     * The issue as cards. An issue with nothing in it still gets its opening card, which says so
     * where the description would be, rather than replacing the whole view with a message.
     */
    private fun showBody(detail: IssueDetail?) {
        thread.show(issueThread(issue, detail))
        sidebar.show(issue, detail)
        cardLayout.show(center, BODY_CARD)
    }

    private companion object {
        const val STATUS_CARD = "status"
        const val BODY_CARD = "body"

        /** Wide enough for the thread to keep its cards readable, with the column beside it. */
        const val SIDEBAR_PROPORTION = 0.75f
        const val SIDEBAR_PROPORTION_KEY = "IssueHub.detail.sidebar"
    }
}
