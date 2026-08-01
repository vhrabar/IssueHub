package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueDetail
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
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
    private val metaRow = JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2)))

    /** Description and history, as one card per run of activity by the same account. */
    private val thread = IssueThreadPanel()

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

    /** The thread, so an editor host can hand it the focus and arrow keys scroll straight away. */
    internal val preferredFocusComponent: JComponent get() = thread

    init {
        add(header(), BorderLayout.NORTH)
        add(center, BorderLayout.CENTER)
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
            add(
                JBPanel<JBPanel<*>>(BorderLayout()).apply {
                    isOpaque = false
                    add(byline, BorderLayout.NORTH)
                    add(metaRow, BorderLayout.CENTER)
                },
                BorderLayout.CENTER,
            )
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

        renderMeta()
        revalidate()
        repaint()
    }

    /** Labels, assignee and milestone as one wrapping row of chips. */
    private fun renderMeta() {
        metaRow.removeAll()
        metaRow.isOpaque = false

        issue.labels.forEach { label ->
            metaRow.add(
                JBLabel(label.name).apply {
                    icon = IssueLabelIcon(labelTint(label.color, UIUtil.getPanelBackground()))
                    iconTextGap = JBUI.scale(4)
                },
            )
        }
        metaRow.add(
            issue.assignee.let { assignee ->
                JBLabel(assignee?.let { IssueHubBundle["issue.assignedTo", it.login] } ?: IssueHubBundle["detail.unassigned"]).apply {
                    foreground = UIUtil.getContextHelpForeground()
                    icon = assignee?.let { avatarLoader.avatar(it.avatarUrl, IssueAvatarIcon(it.login)) }
                    iconTextGap = JBUI.scale(4)
                }
            },
        )
        metaRow.add(
            JBLabel(issue.milestone?.title ?: IssueHubBundle["detail.noMilestone"]).apply {
                foreground = UIUtil.getContextHelpForeground()
            },
        )
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
        cardLayout.show(center, BODY_CARD)
    }

    private companion object {
        const val STATUS_CARD = "status"
        const val BODY_CARD = "body"
    }
}
