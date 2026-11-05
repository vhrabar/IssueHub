package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.editor.IssueVirtualFile
import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueFilterOptions
import com.github.vhrabar.issuehub.model.IssuePage
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.optionsFrom
import com.github.vhrabar.issuehub.provider.AuthenticationRequiredException
import com.github.vhrabar.issuehub.provider.IssueProvider
import com.github.vhrabar.issuehub.provider.NetworkUnavailableException
import com.github.vhrabar.issuehub.provider.RateLimitedException
import com.github.vhrabar.issuehub.settings.IssueHubConfigurable
import com.github.vhrabar.issuehub.settings.IssueHubRepositoryConfigurable
import com.github.vhrabar.issuehub.settings.IssueHubSettings
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
import com.intellij.util.Alarm
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.runBlocking
import java.awt.BorderLayout
import java.awt.CardLayout
import java.awt.Component
import java.awt.Container
import java.awt.FlowLayout
import java.awt.Rectangle
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.time.Instant
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

        // Shown when there is something to do about the message, such as a settings page to open or a
        // request to retry, so the remedy is one click away.
        private var statusLinkAction: () -> Unit = {}
        private val statusLink = ActionLink("") { statusLinkAction() }.apply { isVisible = false }

        // Under the list: how much of the result is on screen, and the way to the next page.
        private val pageLabel = JBLabel().apply { foreground = UIUtil.getContextHelpForeground() }
        private val loadMoreLink = ActionLink(IssueHubBundle["toolWindow.loadMore"]) { loadMore() }
        private val pageFooter =
            JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))).apply {
                add(pageLabel)
                add(loadMoreLink)
            }

        // Above the list when a reload failed but the rows it would have replaced are still good.
        private val staleLabel = JBLabel().apply { foreground = UIUtil.getErrorForeground() }
        private val staleBar =
            JBPanel<JBPanel<*>>(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(4))).apply {
                isVisible = false
                add(staleLabel)
                add(ActionLink(IssueHubBundle["toolWindow.retry"]) { refresh(reloadOptions = true) })
            }

        // Rows ellipsize to the viewport width, so a horizontal scrollbar would never be useful.
        private val listScroll =
            JBScrollPane(issueList).apply {
                horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
                border = JBUI.Borders.empty()
            }
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
                add(
                    JBPanel<JBPanel<*>>(BorderLayout()).apply {
                        add(staleBar, BorderLayout.NORTH)
                        add(listScroll, BorderLayout.CENTER)
                        add(pageFooter, BorderLayout.SOUTH)
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

        /** The query on screen and the page after the last one appended; null once there are no more. */
        private var shownQuery: IssueQuery? = null
        private var nextPage: Int? = null
        private var totalCount: Int? = null
        private var loadingMore = false
        private var loadMoreError: String? = null

        /** What came back last, shown at once while the same list is read again. */
        private var lastResult: IssueListSnapshot? = null
        private var shownSource: String? = null
        private var refreshing = false
        private var staleError: String? = null

        /** When a timer tick may reload again; see [shouldAutoRefresh]. */
        private var lastAttemptAt: Instant? = null
        private var rateLimitedUntil: Instant? = null
        private val autoRefreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

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
            scheduleAutoRefresh()
        }

        override fun dispose() = Unit

        /** Ticks often and decides each time, so a changed interval needs no listener or restart. */
        private fun scheduleAutoRefresh() {
            autoRefreshAlarm.addRequest(
                {
                    autoRefreshTick()
                    scheduleAutoRefresh()
                },
                AUTO_REFRESH_TICK_MS,
            )
        }

        private fun autoRefreshTick() {
            val due =
                shouldAutoRefresh(
                    intervalMinutes = IssueHubSettings.getInstance().autoRefreshMinutes,
                    lastAttemptAt = lastAttemptAt,
                    now = Instant.now(),
                    rateLimitedUntil = rateLimitedUntil,
                    visible = isShowing,
                    busy = refreshing || loadingMore,
                    pagesLoaded = shownQuery?.page ?: 1,
                )
            // The label and milestone lists barely change, so a tick spends one request on the issues.
            if (due) refresh(reloadOptions = false, background = true)
        }

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
            lastResult = null
            refresh(reloadOptions = true)
        }

        /** Reloads on the way back, since naming a repository is what makes a provider apply. */
        private fun openRepositorySettings() {
            ShowSettingsUtil.getInstance().showSettingsDialog(project, IssueHubRepositoryConfigurable::class.java)
            lastResult = null
            refresh(reloadOptions = true)
        }

        /** [link] is the text and action of whatever would fix what [text] describes. */
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
            if (error is RateLimitedException) {
                listModel.clear()
                showStatus(
                    describe(error),
                    // Anonymous callers get 60 requests an hour; any account raises that to thousands.
                    if (error.authenticated) null else IssueHubBundle["toolWindow.auth.openSettings"] to ::openAccountSettings,
                )
                return
            }
            if (error is NetworkUnavailableException) {
                listModel.clear()
                // Nothing on a settings page fixes a dropped connection; trying again is the remedy.
                showStatus(
                    describe(error),
                    IssueHubBundle["toolWindow.retry"] to { refresh(reloadOptions = true) },
                )
                return
            }
            if (error !is AuthenticationRequiredException) {
                showStatus(IssueHubBundle["toolWindow.error", describe(error)])
                return
            }
            val key =
                when (error.reason) {
                    AuthenticationRequiredException.Reason.MISSING -> "toolWindow.auth.missing"
                    AuthenticationRequiredException.Reason.REJECTED -> "toolWindow.auth.rejected"
                }
            listModel.clear()
            lastResult = null
            showStatus(
                IssueHubBundle[key, provider.displayName],
                IssueHubBundle["toolWindow.auth.openSettings"] to ::openAccountSettings,
            )
        }

        private fun showIssues(
            issues: List<Issue>,
            query: IssueQuery,
        ) {
            if (issues.isEmpty()) {
                listModel.clear()
                showStatus(IssueHubBundle[if (query.isFiltered) "toolWindow.emptyFiltered" else "toolWindow.empty"])
                return
            }
            if (shownRows() != issues) replaceRows(issues, query)
            cardLayout.show(center, LIST_CARD)
        }

        private fun shownRows(): List<Issue> = (0 until listModel.size()).map { listModel[it] }

        /** A new answer to the same query keeps the user's place; a different query starts over. */
        private fun replaceRows(
            issues: List<Issue>,
            query: IssueQuery,
        ) {
            val sameList = shownQuery?.copy(page = 1) == query.copy(page = 1)
            val selectedId = issueList.selectedValue?.id
            val visible = Rectangle(listScroll.viewport.viewRect)
            listModel.clear()
            issues.forEach(listModel::addElement)
            if (!sameList) return
            selectedId?.let { id -> issueList.selectedIndex = issues.indexOfFirst { it.id == id } }
            // After the layout pass, once the list has its new height to scroll within.
            ApplicationManager.getApplication().invokeLater { issueList.scrollRectToVisible(visible) }
        }

        /** Puts [snapshot] on screen as though it had just been loaded. */
        private fun showSnapshot(snapshot: IssueListSnapshot) {
            showIssues(snapshot.issues, snapshot.query)
            shownQuery = snapshot.query
            nextPage = snapshot.nextPage
            totalCount = snapshot.totalCount
            loadMoreError = null
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
         *
         * A list already read for this query goes on screen straight away and is replaced when the
         * answer comes. [background] is for reloads nobody asked for, which don't put up the
         * loading message when there is no such list.
         */
        private fun refresh(
            reloadOptions: Boolean,
            background: Boolean = false,
        ) {
            lastAttemptAt = Instant.now()
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
            val source = provider.sourceLabel(project)
            val id = ++requestId
            loadingMore = false
            refreshing = true
            val cached = lastResult?.takeIf { it.matches(source, query) && it.issues.isNotEmpty() }
            when {
                cached != null -> showSnapshot(cached)
                !background -> showStatus(IssueHubBundle["toolWindow.loading"])
            }
            updatePageFooter()
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
                    refreshing = false
                    options?.let { providerOptions = it }
                    result
                        .onSuccess { page ->
                            rateLimitedUntil = null
                            shownSource = source
                            val issues = page.issues
                            discoveredOptions = discoveredOptions.mergedWith(optionsFrom(issues))
                            filterBar.setOptions(providerOptions.mergedWith(discoveredOptions))
                            showIssues(issues, query)
                            rememberPage(query, page)
                        }.onFailure { error ->
                            if (error is RateLimitedException) rateLimitedUntil = error.resetAt
                            if (cached != null && keepsRowsOn(error, background)) {
                                staleError = describe(error)
                            } else {
                                showFailure(provider, error)
                            }
                            updatePageFooter()
                        }
                }
            }
        }

        private fun rememberPage(
            query: IssueQuery,
            page: IssuePage,
        ) {
            shownQuery = query
            nextPage = if (page.hasMore) query.page + 1 else null
            totalCount = page.totalCount
            loadMoreError = null
            staleError = null
            lastResult = IssueListSnapshot(shownSource, query, shownRows(), nextPage, totalCount, Instant.now())
            updatePageFooter()
        }

        /**
         * Appends the next page of the query on screen. It shares [requestId] with [refresh] without
         * advancing it, so a refresh or a filter change started meanwhile wins and this result is dropped.
         */
        private fun loadMore() {
            val query = shownQuery ?: return
            val page = nextPage ?: return
            // A reload in flight replaces the rows, which would leave a gap before this page.
            if (loadingMore || refreshing) return
            val provider = IssueProvider.firstApplicable(project) ?: return
            val id = requestId
            loadingMore = true
            loadMoreError = null
            updatePageFooter()
            val pageQuery = query.copy(page = page)
            ApplicationManager.getApplication().executeOnPooledThread {
                val result = runCatching { runBlocking { provider.fetchIssues(project, pageQuery) } }
                ApplicationManager.getApplication().invokeLater {
                    if (id != requestId) return@invokeLater
                    loadingMore = false
                    result
                        .onSuccess { next ->
                            // Issues filed since the first page push older ones down a page, so the
                            // next page can repeat rows already in the list.
                            val shown = (0 until listModel.size()).mapTo(HashSet()) { listModel[it].id }
                            next.issues.filter { it.id !in shown }.forEach(listModel::addElement)
                            discoveredOptions = discoveredOptions.mergedWith(optionsFrom(next.issues))
                            filterBar.setOptions(providerOptions.mergedWith(discoveredOptions))
                            rememberPage(query.copy(page = page), next)
                        }.onFailure {
                            // The rows already loaded stay; only the footer says what went wrong.
                            loadMoreError = describe(it)
                            updatePageFooter()
                        }
                }
            }
        }

        /** One line on what went wrong; see [describeFailure]. */
        private fun describe(error: Throwable): String =
            describeFailure(error, IssueProvider.firstApplicable(project)?.displayName ?: IssueHubBundle["toolWindow.title"])

        private fun updatePageFooter() {
            val shown = listModel.size()
            val count =
                if (totalCount != null) {
                    IssueHubBundle["toolWindow.page.countOfTotal", shown, totalCount!!]
                } else {
                    IssueHubBundle["toolWindow.page.count", shown]
                }
            val fetchedAt = lastResult?.fetchedAt
            pageLabel.text =
                when {
                    loadMoreError != null -> IssueHubBundle["toolWindow.loadMore.error", loadMoreError!!]
                    refreshing -> IssueHubBundle["toolWindow.page.refreshing", count]
                    fetchedAt != null -> IssueHubBundle["toolWindow.page.updated", count, DateFormatUtil.formatTime(fetchedAt.toEpochMilli())]
                    else -> count
                }
            staleLabel.text = staleError.orEmpty()
            staleLabel.toolTipText = staleError
            staleBar.isVisible = staleError != null
            loadMoreLink.text =
                IssueHubBundle[
                    when {
                        loadingMore -> "toolWindow.loadMore.loading"
                        loadMoreError != null -> "toolWindow.loadMore.retry"
                        else -> "toolWindow.loadMore"
                    },
                ]
            loadMoreLink.isEnabled = !loadingMore && !refreshing
            loadMoreLink.isVisible = nextPage != null
            pageFooter.isVisible = nextPage != null || shown > 0
        }

        private companion object {
            const val STATUS_CARD = "status"
            const val LIST_CARD = "list"

            /** How often the timer looks at whether a reload is due, not how often one happens. */
            const val AUTO_REFRESH_TICK_MS = 60_000
        }
    }
}

/** Recursively runs each container's layout so a detached renderer tree has valid child bounds. */
private fun layoutTree(component: Component) {
    component.doLayout()
    if (component is Container) component.components.forEach(::layoutTree)
}
