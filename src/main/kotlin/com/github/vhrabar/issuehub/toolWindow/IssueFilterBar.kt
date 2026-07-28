package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.model.AssigneeFilter
import com.github.vhrabar.issuehub.model.IssueFilterOptions
import com.github.vhrabar.issuehub.model.IssueQuery
import com.github.vhrabar.issuehub.model.IssueSortDirection
import com.github.vhrabar.issuehub.model.IssueSortField
import com.github.vhrabar.issuehub.model.IssueStateFilter
import com.github.vhrabar.issuehub.model.MilestoneFilter
import com.intellij.ide.DataManager
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.KeepPopupOnPerform
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.SearchTextField
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.WrapLayout
import java.awt.BorderLayout
import java.awt.FlowLayout
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

/**
 * Search field plus the filter and sort dropdowns above the issue list.
 *
 * Owns the current [query] and reports every change through [onQueryChanged]; it never touches
 * the list itself.
 */
internal class IssueFilterBar(
    parent: Disposable,
    trailing: JComponent,
    private val onQueryChanged: (IssueQuery) -> Unit,
) : JBPanel<IssueFilterBar>(VerticalLayout(JBUI.scale(4))) {
    var query: IssueQuery = IssueQuery()
        private set

    private var options = IssueFilterOptions()

    /** Re-renders each link's text after the query changes; filled in as the links are built. */
    private val linkUpdaters = mutableListOf<() -> Unit>()

    private val searchField =
        SearchTextField(false).apply {
            textEditor.emptyText.text = IssueHubBundle["filter.search.placeholder"]
        }

    // Every keystroke would otherwise cost a request against the rate-limited search endpoint.
    private val searchAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, parent)

    private val resetLink =
        ActionLink(IssueHubBundle["filter.reset"]) {
            searchField.text = ""
            updateQuery { IssueQuery(sortField = it.sortField, sortDirection = it.sortDirection, limit = it.limit) }
        }

    init {
        searchField.addDocumentListener(
            object : DocumentAdapter() {
                override fun textChanged(e: DocumentEvent) = scheduleSearch()
            },
        )
        searchField.addKeyboardListener(
            object : KeyAdapter() {
                override fun keyPressed(e: KeyEvent) {
                    if (e.keyCode == KeyEvent.VK_ENTER) {
                        searchAlarm.cancelAllRequests()
                        updateQuery { it }
                    }
                }
            },
        )

        add(
            JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(4), 0)).apply {
                add(searchField, BorderLayout.CENTER)
                add(trailing, BorderLayout.EAST)
            },
        )
        add(
            // Tool windows are narrow, so the row has to wrap rather than clip.
            JBPanel<JBPanel<*>>(WrapLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(2))).apply {
                add(stateLink())
                add(authorLink())
                add(assigneeLink())
                add(labelsLink())
                add(milestoneLink())
                add(sortLink())
                add(resetLink)
            },
        )
        border = JBUI.Borders.empty(4)
        refreshLinks()
    }

    /** Replaces the values offered by the dropdowns; the current selection is left untouched. */
    fun setOptions(options: IssueFilterOptions) {
        this.options = options
    }

    private fun scheduleSearch() {
        searchAlarm.cancelAllRequests()
        searchAlarm.addRequest({ updateQuery { it } }, SEARCH_DEBOUNCE_MS)
    }

    /** Folds [transform] and the live search text into the query, notifying only on a real change. */
    private fun updateQuery(transform: (IssueQuery) -> IssueQuery) {
        val updated = transform(query).copy(text = searchField.text.trim())
        if (updated == query) return
        query = updated
        refreshLinks()
        onQueryChanged(updated)
    }

    private fun refreshLinks() {
        linkUpdaters.forEach { it() }
        resetLink.isVisible = query.isFiltered
    }

    private fun stateLink() =
        choiceLink(
            name = IssueHubBundle["filter.state"],
            choices = { IssueStateFilter.entries.map { Choice(stateText(it), it) } },
            // ALL already is the "any" entry, and state is never unset.
            includeAny = false,
            selected = { query.state },
            display = ::stateText,
            onSelect = { picked -> updateQuery { it.copy(state = picked ?: IssueStateFilter.OPEN) } },
        )

    private fun authorLink() =
        choiceLink(
            name = IssueHubBundle["filter.author"],
            choices = { options.authors.map { Choice(it, it) } },
            selected = { query.author },
            display = { it },
            onSelect = { picked -> updateQuery { it.copy(author = picked) } },
        )

    private fun assigneeLink() =
        choiceLink(
            name = IssueHubBundle["filter.assignee"],
            choices = {
                listOf(Choice(IssueHubBundle["filter.assignee.unassigned"], AssigneeFilter.Unassigned as AssigneeFilter)) +
                    options.assignees.map { Choice(it, AssigneeFilter.User(it)) }
            },
            selected = { query.assignee },
            display = ::assigneeText,
            onSelect = { picked -> updateQuery { it.copy(assignee = picked) } },
        )

    private fun milestoneLink() =
        choiceLink(
            name = IssueHubBundle["filter.milestone"],
            choices = {
                listOf(Choice(IssueHubBundle["filter.milestone.none"], MilestoneFilter.None as MilestoneFilter)) +
                    options.milestones.map { Choice(it.title, MilestoneFilter.Named(it)) }
            },
            selected = { query.milestone },
            display = ::milestoneText,
            onSelect = { picked -> updateQuery { it.copy(milestone = picked) } },
        )

    /** Labels are the one dimension GitHub can intersect, so this popup stays open and multi-selects. */
    private fun labelsLink(): DropDownLink<String> =
        dropDownLink(IssueHubBundle["filter.label"], { labelsText() }) { host ->
            val group = DefaultActionGroup()
            group.add(
                toggle(IssueHubBundle["filter.any"], { query.labels.isEmpty() }) {
                    updateQuery { it.copy(labels = emptySet()) }
                },
            )
            options.labels.forEach { label ->
                group.add(
                    toggle(label.name, { label.name in query.labels }) {
                        updateQuery {
                            val labels = if (label.name in it.labels) it.labels - label.name else it.labels + label.name
                            it.copy(labels = labels)
                        }
                    },
                )
            }
            popup(IssueHubBundle["filter.label"], group, host)
        }

    private fun sortLink(): DropDownLink<String> =
        dropDownLink(IssueHubBundle["filter.sort"], { sortText() }) { host ->
            val group = DefaultActionGroup()
            IssueSortField.entries.forEach { field ->
                group.add(
                    toggle(sortFieldText(field), { query.sortField == field }, keepOpen = false) {
                        updateQuery { it.copy(sortField = field) }
                    },
                )
            }
            group.addSeparator()
            IssueSortDirection.entries.forEach { direction ->
                group.add(
                    toggle(sortDirectionText(direction), { query.sortDirection == direction }, keepOpen = false) {
                        updateQuery { it.copy(sortDirection = direction) }
                    },
                )
            }
            popup(IssueHubBundle["filter.sort"], group, host)
        }

    /** A single-select dropdown over [choices], with an "Any" entry that clears the dimension. */
    private fun <T : Any> choiceLink(
        name: String,
        choices: () -> List<Choice<T>>,
        selected: () -> T?,
        display: (T) -> String,
        onSelect: (T?) -> Unit,
        includeAny: Boolean = true,
    ): DropDownLink<String> =
        dropDownLink(name, { selected()?.let(display) ?: IssueHubBundle["filter.any"] }) { host ->
            val group = DefaultActionGroup()
            if (includeAny) {
                group.add(toggle(IssueHubBundle["filter.any"], { selected() == null }, keepOpen = false) { onSelect(null) })
            }
            choices().forEach { choice ->
                group.add(
                    toggle(choice.text, { selected() == choice.value }, keepOpen = false) { onSelect(choice.value) },
                )
            }
            popup(name, group, host)
        }

    /** Wires a link so its text tracks [value] whenever the query changes. */
    private fun dropDownLink(
        name: String,
        value: () -> String,
        popupBuilder: (DropDownLink<String>) -> JBPopup,
    ): DropDownLink<String> {
        val link = DropDownLink(name, popupBuilder)
        linkUpdaters += { link.text = IssueHubBundle["filter.chip", name, value()] }
        return link
    }

    private fun popup(
        title: String,
        group: ActionGroup,
        host: JComponent,
    ): JBPopup =
        JBPopupFactory.getInstance().createActionGroupPopup(
            title,
            group,
            DataManager.getInstance().getDataContext(host),
            JBPopupFactory.ActionSelectionAid.SPEEDSEARCH,
            true,
        )

    private fun toggle(
        text: String,
        selected: () -> Boolean,
        keepOpen: Boolean = true,
        onToggle: () -> Unit,
    ) = object : DumbAwareToggleAction(text) {
        init {
            templatePresentation.keepPopupOnPerform =
                if (keepOpen) KeepPopupOnPerform.Always else KeepPopupOnPerform.Never
        }

        override fun getActionUpdateThread() = ActionUpdateThread.EDT

        override fun isSelected(e: AnActionEvent) = selected()

        override fun setSelected(
            e: AnActionEvent,
            state: Boolean,
        ) = onToggle()
    }

    private fun labelsText(): String =
        when (query.labels.size) {
            0 -> IssueHubBundle["filter.any"]
            1 -> query.labels.first()
            else -> IssueHubBundle["filter.label.count", query.labels.size]
        }

    private fun sortText(): String =
        IssueHubBundle[
            "filter.sort.value",
            sortFieldText(query.sortField),
            sortDirectionText(query.sortDirection),
        ]

    private fun stateText(state: IssueStateFilter): String =
        when (state) {
            IssueStateFilter.OPEN -> IssueHubBundle["filter.state.open"]
            IssueStateFilter.CLOSED -> IssueHubBundle["filter.state.closed"]
            IssueStateFilter.ALL -> IssueHubBundle["filter.state.all"]
        }

    private fun assigneeText(filter: AssigneeFilter): String =
        when (filter) {
            AssigneeFilter.Unassigned -> IssueHubBundle["filter.assignee.unassigned"]
            is AssigneeFilter.User -> filter.login
        }

    private fun milestoneText(filter: MilestoneFilter): String =
        when (filter) {
            MilestoneFilter.None -> IssueHubBundle["filter.milestone.none"]
            is MilestoneFilter.Named -> filter.milestone.title
        }

    private fun sortFieldText(field: IssueSortField): String =
        when (field) {
            IssueSortField.CREATED -> IssueHubBundle["filter.sort.created"]
            IssueSortField.UPDATED -> IssueHubBundle["filter.sort.updated"]
            IssueSortField.COMMENTS -> IssueHubBundle["filter.sort.comments"]
        }

    private fun sortDirectionText(direction: IssueSortDirection): String =
        when (direction) {
            IssueSortDirection.ASC -> IssueHubBundle["filter.sort.asc"]
            IssueSortDirection.DESC -> IssueHubBundle["filter.sort.desc"]
        }

    private data class Choice<T : Any>(
        val text: String,
        val value: T,
    )

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400
    }
}
