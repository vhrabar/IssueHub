package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.model.Issue
import com.github.vhrabar.issuehub.model.IssueLabel
import com.github.vhrabar.issuehub.model.IssueState
import com.intellij.icons.AllIcons
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.util.text.DateFormatUtil
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Container
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Graphics2D
import java.awt.LayoutManager
import java.awt.image.BufferedImage
import java.io.File
import java.time.Instant
import javax.imageio.ImageIO
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Renders an [Issue] as "#number  Title" with a state-tinted number.
 * creation date, author status & labels
 * */

internal class IssueCellRenderer(
    private val avatarLoader: AvatarLoader,
) : JBPanel<IssueCellRenderer>(BorderLayout()),
    ListCellRenderer<Issue> {
    private val title = borderless()
    private val labelIcon = JBLabel()
    private val stateText = borderless()
    private val avatar = JBLabel()
    private val comments = borderless()
    private val meta = borderless()

    private val trailing =
        transparentPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(6), 0)).apply {
            add(stateText)
            add(avatar)
            add(comments)
        }

    private val titleRow =
        transparentPanel(TitleRowLayout(JBUI.scale(5))).apply {
            add(title)
            add(labelIcon)
        }

    private val topRow =
        transparentPanel(BorderLayout(JBUI.scale(8), 0)).apply {
            add(titleRow, BorderLayout.CENTER)
            add(trailing, BorderLayout.EAST)
        }

    /** Width the list can actually give this row; see [getPreferredSize]. */
    private var availableWidth = 0

    init {
        border = JBUI.Borders.empty(5, 8)
        add(topRow, BorderLayout.NORTH)
        add(meta, BorderLayout.CENTER)
    }

    /**
     * Rows must never ask for more width than the list has, otherwise [JList] sizes every cell to
     * the longest title and the scroll pane grows a horizontal scrollbar instead of ellipsizing.
     */
    override fun getPreferredSize(): Dimension {
        val size = super.getPreferredSize()
        return if (availableWidth > 0) Dimension(availableWidth, size.height) else size
    }

    override fun getListCellRendererComponent(
        list: JList<out Issue>,
        value: Issue?,
        index: Int,
        selected: Boolean,
        hasFocus: Boolean,
    ): Component {
        isOpaque = true
        background = UIUtil.getListBackground(selected, hasFocus)
        availableWidth = list.width - JBUI.scale(1) - (list.insets.left + list.insets.right)
        value ?: return this

        val regular = attributes(SimpleTextAttributes.REGULAR_ATTRIBUTES, selected, hasFocus)
        val grayed = attributes(SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES, selected, hasFocus)

        title.clear()
        title.icon = IssueStateIcon(value.state)
        title.iconTextGap = JBUI.scale(6)
        title.appendWithClipping(value.title, regular, EllipsisClipper)
        title.toolTipText = value.title

        renderLabels(value, selected, hasFocus)
        renderTrailing(value, grayed)

        meta.clear()
        meta.append(metaText(value), grayed)

        return this
    }

    /**
     * Labels collapse to a single tag icon tinted with the first label's color.
     */
    private fun renderLabels(
        value: Issue,
        selected: Boolean,
        hasFocus: Boolean,
    ) {
        val first = value.labels.firstOrNull()
        labelIcon.isVisible = first != null
        if (first == null) {
            labelIcon.icon = null
            labelIcon.toolTipText = null
            return
        }
        labelIcon.icon = IssueLabelIcon(labelTint(first, UIUtil.getListBackground(selected, hasFocus)))
        labelIcon.toolTipText = labelsTooltip(value.labels)
    }

    /**
     * A titled list with the tag-shaped swatch left of each label name. Swing renders tooltip HTML,
     * so each swatch is the row's own [IssueLabelIcon] baked to a PNG, tinted against the tooltip
     * background the same way the row icon is tinted.
     */
    private fun labelsTooltip(labels: List<IssueLabel>): String {
        val background = UIUtil.getToolTipBackground()
        // A borderless table keeps the swatch and name vertically centered against each row's height.
        val rows =
            labels.joinToString("") { label ->
                "<tr><td valign='middle'>${labelSwatch(labelTint(label, background))}</td>" +
                    "<td valign='middle'>&nbsp;${escapeHtml(label.name)}</td></tr>"
            }
        return "<html><b>${escapeHtml(IssueHubBundle["issue.labels.title"])}</b>" +
            "<table cellpadding='0' cellspacing='0'>$rows</table></html>"
    }

    /** The tag icon as an inline image, falling back to a colored square if it can't be baked. */
    private fun labelSwatch(color: Color): String =
        swatchImageUrl(color)?.let { """<img src="$it">""" }
            ?: """<span style="color:#${ColorUtil.toHex(color)};">&#9632;</span>"""

    /** GitHub picks label colors against a white page, so lift them when the surface is dark. */
    private fun labelTint(
        label: IssueLabel,
        background: Color,
    ): Color {
        val base = label.color?.let { ColorUtil.fromHex(it, null) } ?: NEUTRAL_LABEL
        return if (ColorUtil.isDark(background)) ColorUtil.brighter(base, 1) else base
    }

    private fun renderTrailing(
        value: Issue,
        grayed: SimpleTextAttributes,
    ) {
        stateText.clear()
        stateText.append(stateLabel(value.state), grayed)

        // Prefer the assignee; fall back to the author, keeping login and avatar url in step.
        val (account, avatarUrl) =
            if (value.assignee != null) {
                value.assignee to value.assigneeAvatarUrl
            } else {
                value.author to value.authorAvatarUrl
            }
        avatar.icon = account?.let { avatarLoader.avatar(avatarUrl, IssueAvatarIcon(it)) }
        avatar.toolTipText = value.assignee
            ?.let { IssueHubBundle["issue.assignedTo", it] }
            ?: value.author?.let { IssueHubBundle["issue.openedBy", it] }

        comments.clear()
        if (value.commentCount > 0) {
            comments.icon = AllIcons.General.Balloon
            comments.iconTextGap = JBUI.scale(3)
            comments.append(value.commentCount.toString(), grayed)
        } else {
            comments.icon = null
        }
    }

    private fun metaText(value: Issue): String {
        val created = formatDate(value.createdAt)
        return when {
            value.author != null && created != null ->
                IssueHubBundle["issue.meta.createdBy", value.displayNumber, created, value.author]
            created != null -> IssueHubBundle["issue.meta.created", value.displayNumber, created]
            value.author != null -> IssueHubBundle["issue.meta.by", value.displayNumber, value.author]
            else -> value.displayNumber
        }
    }

    private fun stateLabel(state: IssueState): String =
        when (state) {
            IssueState.OPEN -> IssueHubBundle["issue.state.open"]
            IssueState.CLOSED -> IssueHubBundle["issue.state.closed"]
            IssueState.OTHER -> IssueHubBundle["issue.state.other"]
        }

    private fun formatDate(timestamp: String): String? =
        runCatching { DateFormatUtil.formatDate(Instant.parse(timestamp).toEpochMilli()) }.getOrNull()

    private fun attributes(
        base: SimpleTextAttributes,
        selected: Boolean,
        hasFocus: Boolean,
    ): SimpleTextAttributes = if (selected) base.derive(-1, UIUtil.getListForeground(true, hasFocus), null, null) else base

    /**
     * Lays the title out left-to-right with its badges glued directly after it.
     */
    private class TitleRowLayout(
        private val gap: Int,
    ) : LayoutManager {
        override fun addLayoutComponent(
            name: String?,
            comp: Component,
        ) = Unit

        override fun removeLayoutComponent(comp: Component) = Unit

        override fun preferredLayoutSize(parent: Container): Dimension {
            val insets = parent.insets
            var width = 0
            var height = 0
            visible(parent).forEachIndexed { index, c ->
                if (index > 0) width += gap
                width += c.preferredSize.width
                height = maxOf(height, c.preferredSize.height)
            }
            return Dimension(width + insets.left + insets.right, height + insets.top + insets.bottom)
        }

        /** The title may collapse entirely; the row must never force the list wider. */
        override fun minimumLayoutSize(parent: Container) = Dimension(0, preferredLayoutSize(parent).height)

        override fun layoutContainer(parent: Container) {
            val components = visible(parent)
            if (components.isEmpty()) return

            val insets = parent.insets
            val available = parent.width - insets.left - insets.right
            val height = parent.height - insets.top - insets.bottom

            val title = components.first()
            val badges = components.drop(1)
            val badgesWidth = badges.sumOf { it.preferredSize.width + gap }
            val titleWidth = (available - badgesWidth).coerceIn(0, title.preferredSize.width)

            var x = insets.left
            title.setBounds(x, insets.top, titleWidth, height)
            x += titleWidth
            badges.forEach {
                x += gap
                val size = it.preferredSize
                it.setBounds(x, insets.top + (height - size.height) / 2, size.width, size.height)
                x += size.width
            }
        }

        private fun visible(parent: Container) = parent.components.filter { it.isVisible }
    }

    /** Trims an over-long fragment to the width the painter has left, with a trailing ellipsis. */
    private object EllipsisClipper : SimpleColoredComponent.FragmentTextClipper {
        private const val ELLIPSIS = "…"

        override fun clipText(
            component: SimpleColoredComponent,
            g2: Graphics2D,
            fragmentIndex: Int,
            text: String,
            availTextWidth: Int,
        ): String {
            val metrics = g2.fontMetrics
            if (availTextWidth <= 0) return ""
            if (metrics.stringWidth(text) <= availTextWidth) return text

            val budget = availTextWidth - metrics.stringWidth(ELLIPSIS)
            if (budget <= 0) return ELLIPSIS

            // Binary search the longest prefix that fits; clipText runs on every repaint.
            var low = 0
            var high = text.length
            while (low < high) {
                val mid = (low + high + 1) / 2
                if (metrics.stringWidth(text.substring(0, mid)) <= budget) low = mid else high = mid - 1
            }
            return text.take(low).trimEnd() + ELLIPSIS
        }
    }

    private companion object {
        /** Fallback for labels the API returned without a color. */
        val NEUTRAL_LABEL = JBColor(Color(0x9AA7B0), Color(0x6C707E))

        fun borderless() =
            SimpleColoredComponent().apply {
                isOpaque = false
                ipad = JBUI.emptyInsets()
                setMyBorder(null)
            }

        fun transparentPanel(layout: java.awt.LayoutManager) = JBPanel<JBPanel<*>>(layout).apply { isOpaque = false }

        /** Label names are arbitrary text; keep them from breaking the tooltip's HTML. */
        fun escapeHtml(text: String): String =
            text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")

        /** Baked tag swatches keyed by ARGB; Swing's tooltip HTML loads images by file URL. */
        private val swatchCache = HashMap<Int, String?>()

        /** Renders [IssueLabelIcon] to a cached temp PNG and returns its URL, or null on failure. */
        fun swatchImageUrl(color: Color): String? =
            swatchCache.getOrPut(color.rgb) {
                runCatching {
                    val icon = IssueLabelIcon(color)
                    val image = ImageUtil.createImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)
                    val g = image.createGraphics()
                    try {
                        icon.paintIcon(null, g, 0, 0)
                    } finally {
                        g.dispose()
                    }
                    val file = File.createTempFile("issuehub-label-", ".png").apply { deleteOnExit() }
                    ImageIO.write(image, "png", file)
                    file.toURI().toString()
                }.getOrNull()
            }
    }
}
