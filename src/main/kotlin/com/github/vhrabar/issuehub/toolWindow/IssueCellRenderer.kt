package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueState
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import javax.swing.JList

/** Renders an [Issue] as "#number  Title" with a state-tinted number. */
internal class IssueCellRenderer : ColoredListCellRenderer<Issue>() {
    override fun customizeCellRenderer(
        list: JList<out Issue>,
        value: Issue?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        value ?: return
        val numberAttrs = when (value.state) {

            IssueState.CLOSED -> SimpleTextAttributes.GRAYED_ATTRIBUTES
            else -> SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES
        }
        append(value.displayNumber, numberAttrs)
        append("  ")
        append(value.title, SimpleTextAttributes.REGULAR_ATTRIBUTES)
        value.assignee?.let { append("  @$it", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES) }
    }
}