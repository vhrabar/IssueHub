package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueActor
import com.github.vhrabar.issuehub.model.IssueDetail
import com.github.vhrabar.issuehub.model.IssueDevelopment
import com.github.vhrabar.issuehub.model.IssueProjectItem
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.JBColor
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.SwingConstants

internal class IssueSidebarPanel : JBPanel<IssueSidebarPanel>(VerticalLayout(JBUI.scale(SECTION_GAP))) {
    /**
     * Rebuilds rather than repaints: a label keeps whatever icon it was handed, so a downloaded
     * avatar only reaches the screen if the rows are made again.
     */
    private val avatarLoader = AvatarLoader(::render)

    private var issue: Issue? = null
    private var detail: IssueDetail? = null

    /** True between asking the provider for the detail and being given it. */
    private var loading = false

    init {
        border = JBUI.Borders.empty(10)
        isOpaque = false
        preferredSize = Dimension(JBUI.scale(WIDTH), 0)
        minimumSize = Dimension(JBUI.scale(MIN_WIDTH), 0)
    }

    /**
     * Shows what the list row already knows while the provider is asked for the rest.
     *
     * Labels, assignees and the milestone are on the row, so those sections are right from the
     * first frame; only the two that need a second request are left saying so.
     */
    fun showLoading(issue: Issue) {
        this.issue = issue
        this.detail = null
        loading = true
        render()
    }

    fun show(
        issue: Issue,
        detail: IssueDetail?,
    ) {
        this.issue = issue
        this.detail = detail
        loading = false
        render()
    }

    private fun render() {
        removeAll()
        issue?.let { issue ->
            add(assigneesSection(issue))
            add(labelsSection(issue))
            add(projectsSection())
            add(milestoneSection(issue))
            add(developmentSection())
        }
        revalidate()
        repaint()
    }

    private fun assigneesSection(issue: Issue): JComponent =
        section(
            IssueHubBundle["detail.sidebar.assignees"],
            issue.assignees.map(::actorRow).ifEmpty { listOf(mutedRow(IssueHubBundle["detail.sidebar.noAssignees"])) },
        )

    private fun labelsSection(issue: Issue): JComponent =
        section(
            IssueHubBundle["detail.sidebar.labels"],
            issue.labels
                .map { label ->
                    row(label.name, IssueLabelIcon(labelTint(label.color, UIUtil.getPanelBackground())))
                }.ifEmpty { listOf(mutedRow(IssueHubBundle["detail.sidebar.noLabels"])) },
        )

    private fun milestoneSection(issue: Issue): JComponent =
        section(
            IssueHubBundle["detail.sidebar.milestone"],
            listOf(
                issue.milestone
                    ?.let { row(it.title, IssueEventIcon(IssueEventIcon.Kind.MILESTONE)) }
                    ?: mutedRow(IssueHubBundle["detail.sidebar.noMilestone"]),
            ),
        )

    /**
     * Each board the issue is on, followed by that board's own columns as they stand for it —
     * status, size, estimate, dates, whatever the board defines — rather than a fixed set we'd have
     * to guess at.
     */
    private fun projectsSection(): JComponent {
        val projects = detail?.projects
        val rows =
            when {
                projects == null -> listOf(mutedRow(pendingText(IssueHubBundle["detail.sidebar.projectsUnavailable"])))
                projects.isEmpty() -> listOf(mutedRow(IssueHubBundle["detail.sidebar.noProjects"]))
                else -> projects.flatMap(::projectRows)
            }
        return section(IssueHubBundle["detail.sidebar.projects"], rows)
    }

    private fun projectRows(project: IssueProjectItem): List<JComponent> =
        buildList {
            add(
                project.url?.let { url -> linkRow(project.title, url, null) }
                    ?: row(project.title, null).apply { font = JBFont.label().asBold() },
            )
            project.fields.forEach { add(fieldRow(it.name, it.value)) }
        }

    /** The pull requests that will close the issue, and the branches opened for it. */
    private fun developmentSection(): JComponent {
        val development = detail?.development
        val rows =
            when {
                development == null -> listOf(mutedRow(pendingText(IssueHubBundle["detail.sidebar.developmentUnavailable"])))
                development.isEmpty -> listOf(mutedRow(IssueHubBundle["detail.sidebar.noDevelopment"]))
                else -> developmentRows(development)
            }
        return section(IssueHubBundle["detail.sidebar.development"], rows)
    }

    private fun developmentRows(development: IssueDevelopment): List<JComponent> =
        buildList {
            development.pullRequests.forEach { pr ->
                add(
                    linkRow(
                        text = "${pr.displayNumber} ${pr.title}",
                        url = pr.url,
                        icon = PullRequestStateIcon(pr.state),
                        tooltip = "${pr.displayNumber} ${pr.title}",
                    ),
                )
            }
            development.branches.forEach { branch ->
                val icon = IssueEventIcon(IssueEventIcon.Kind.COMMIT)
                add(branch.url?.let { linkRow(branch.name, it, icon) } ?: row(branch.name, icon))
            }
        }

    /** A section's own heading, underlined the width of the column, with its rows beneath it. */
    private fun section(
        heading: String,
        rows: List<JComponent>,
    ): JComponent =
        JBPanel<JBPanel<*>>(VerticalLayout(JBUI.scale(ROW_GAP))).apply {
            isOpaque = false
            add(
                JBLabel(heading).apply {
                    font = JBFont.medium().asBold()
                    foreground = UIUtil.getContextHelpForeground()
                    border =
                        JBUI.Borders.compound(
                            JBUI.Borders.customLineBottom(JBColor.border()),
                            JBUI.Borders.emptyBottom(ROW_GAP),
                        )
                },
            )
            rows.forEach { add(it) }
        }

    private fun actorRow(actor: IssueActor): JComponent =
        row(actor.login, avatarLoader.avatar(actor.avatarUrl, IssueAvatarIcon(actor.login)))

    private fun row(
        text: String,
        icon: javax.swing.Icon?,
    ): JBLabel =
        JBLabel(shorten(text)).apply {
            this.icon = icon
            iconTextGap = JBUI.scale(ICON_GAP)
            toolTipText = text
        }

    private fun mutedRow(text: String): JComponent = row(text, null).apply { foreground = UIUtil.getContextHelpForeground() }

    /** One of a board's columns: its name on the left, the issue's value for it on the right. */
    private fun fieldRow(
        name: String,
        value: String,
    ): JComponent =
        JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(ICON_GAP), 0)).apply {
            isOpaque = false
            add(
                JBLabel(name).apply { foreground = UIUtil.getContextHelpForeground() },
                BorderLayout.WEST,
            )
            add(
                JBLabel(shorten(value)).apply {
                    horizontalAlignment = SwingConstants.RIGHT
                    toolTipText = value
                },
                BorderLayout.CENTER,
            )
        }

    private fun linkRow(
        text: String,
        url: String,
        icon: javax.swing.Icon?,
        tooltip: String = text,
    ): JComponent =
        ActionLink(shorten(text)) { BrowserUtil.browse(url) }.apply {
            this.icon = icon
            iconTextGap = JBUI.scale(ICON_GAP)
            horizontalAlignment = SwingConstants.LEFT
            toolTipText = tooltip
        }

    /** While the detail request is still out, an empty section is unknown rather than unavailable. */
    private fun pendingText(unavailable: String): String = if (loading) IssueHubBundle["detail.sidebar.loading"] else unavailable

    /** The column is narrow and doesn't scroll sideways, so long titles are cut rather than clipped. */
    private fun shorten(text: String): String = StringUtil.shortenTextWithEllipsis(text, MAX_TEXT, 0, true)

    private companion object {
        const val WIDTH = 260
        const val MIN_WIDTH = 160
        const val SECTION_GAP = 14
        const val ROW_GAP = 4
        const val ICON_GAP = 6

        /** Roughly what the column fits; past it a title is cut rather than half-drawn at the edge. */
        const val MAX_TEXT = 40
    }
}
