package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.IssueHubBundle
import com.github.vhrabar.issuehub.model.IssueActor
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.Disposable
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.RoundedLineBorder
import com.intellij.ui.components.JBHtmlPane
import com.intellij.ui.components.JBHtmlPaneConfiguration
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import javax.swing.JComponent
import javax.swing.Scrollable
import javax.swing.SwingConstants
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent

/**
 * The issue as a stack of cards, one per run of history by the same account: the author's avatar
 * and name down the left, what they did and wrote to the right of it.
 *
 * Cards are Swing components rather than one big HTML document because the avatars are painted
 * icons, not images an HTML pane could fetch; each card still renders its own text through the
 * platform's HTML pane, which themes GitHub's markup to match the IDE.
 */
internal class IssueThreadPanel :
    JBPanel<IssueThreadPanel>(VerticalLayout(JBUI.scale(CARD_GAP))),
    Scrollable,
    Disposable {
    private val avatarLoader = AvatarLoader(::applyAvatars)

    /** Held so they can be disposed: an HTML pane owns platform resources the GC won't reclaim. */
    private val panes = mutableListOf<JBHtmlPane>()

    /**
     * Held so a downloaded avatar can replace the initials standing in for it. A repaint alone
     * wouldn't: the label keeps whatever icon it was given, so the picture would only turn up the
     * next time the thread was rebuilt.
     */
    private val avatars = mutableListOf<Pair<JBLabel, IssueActor?>>()

    init {
        border = JBUI.Borders.empty(CARD_GAP)
        isOpaque = false
        // Focusable so the scroll pane's own arrow-key bindings work the moment the tab opens.
        isFocusable = true
        addComponentListener(
            object : ComponentAdapter() {
                override fun componentResized(event: ComponentEvent) = remeasure()
            },
        )
    }

    fun show(cards: List<IssueThreadCard>) {
        disposePanes()
        avatars.clear()
        removeAll()
        val muted = ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground())
        cards.forEach { add(card(it, muted)) }
        revalidate()
        repaint()
        // Fresh cards start out with no width, so their first measurement is meaningless. Reloading
        // an issue leaves the panel the same size, so nothing else would ask them to measure again.
        SwingUtilities.invokeLater(::remeasure)
    }

    /** A pane only knows how tall its text is once it knows how wide it has been given. */
    private fun remeasure() {
        panes.forEach { it.invalidate() }
        revalidate()
    }

    /** Re-asks for every avatar, which is how a download that has just landed reaches the screen. */
    private fun applyAvatars() {
        avatars.forEach { (label, actor) ->
            label.icon = avatar(actor, label.text)
        }
        repaint()
    }

    private fun avatar(
        actor: IssueActor?,
        login: String,
    ) = avatarLoader.avatar(actor?.avatarUrl, IssueAvatarIcon(login, AVATAR_SIZE), AVATAR_SIZE)

    override fun dispose() = disposePanes()

    private fun disposePanes() {
        panes.forEach { it.dispose() }
        panes.clear()
    }

    private fun card(
        card: IssueThreadCard,
        muted: String,
    ): JComponent {
        val content =
            CardContentPane().apply {
                isEditable = false
                isOpaque = false
                border = JBUI.Borders.empty()
                // GitHub renders every reference as an absolute URL, so there is no base to resolve against.
                addHyperlinkListener { event ->
                    if (event.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                        event.url?.let { BrowserUtil.browse(it) }
                    }
                }
                text = cardContentHtml(card, muted)
            }
        panes += content

        return JBPanel<JBPanel<*>>(BorderLayout(JBUI.scale(CARD_GAP), 0)).apply {
            isOpaque = false
            border =
                JBUI.Borders.compound(
                    RoundedLineBorder(JBColor.border(), JBUI.scale(CARD_ARC), 1),
                    JBUI.Borders.empty(CARD_PADDING),
                )
            add(author(card), BorderLayout.WEST)
            add(content, BorderLayout.CENTER)
        }
    }

    /**
     * Avatar and login, in a column of fixed width so every card's text starts on the same line
     * however long the account names happen to be.
     */
    private fun author(card: IssueThreadCard): JComponent {
        val login = card.actor?.login ?: IssueHubBundle["detail.unknownAuthor"]
        val label =
            JBLabel(login).apply {
                font = JBFont.label().asBold()
                iconTextGap = JBUI.scale(6)
                toolTipText = login
            }
        avatars += label to card.actor
        label.icon = avatar(card.actor, login)
        return JBPanel<JBPanel<*>>(BorderLayout()).apply {
            isOpaque = false
            preferredSize = Dimension(JBUI.scale(AUTHOR_WIDTH), 0)
            minimumSize = preferredSize
            // Pinned to the top so a long comment doesn't push its author into the middle of the card.
            add(label, BorderLayout.NORTH)
        }
    }

    override fun getPreferredScrollableViewportSize(): Dimension = preferredSize

    override fun getScrollableUnitIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = JBUI.scale(SCROLL_UNIT)

    override fun getScrollableBlockIncrement(
        visibleRect: Rectangle,
        orientation: Int,
        direction: Int,
    ): Int = if (orientation == SwingConstants.VERTICAL) visibleRect.height else visibleRect.width

    /** Take the viewport's width rather than the widest line's, so the cards wrap instead of clipping. */
    override fun getScrollableTracksViewportWidth(): Boolean = true

    override fun getScrollableTracksViewportHeight(): Boolean = false

    /**
     * A pane that reports the height its text needs at the width it was given.
     *
     * A stacked layout asks how big a component wants to be before it hands out any width, and a
     * text pane left to itself answers with the width of its longest unwrapped line — which would
     * drag the card off the right edge instead of wrapping inside it.
     */
    private class CardContentPane : JBHtmlPane() {
        /** The pane resolves `<icon src="…">` through this, since it can't be handed a painted icon. */
        override fun initializePaneConfiguration(builder: JBHtmlPaneConfiguration.Builder) {
            super.initializePaneConfiguration(builder)
            builder.iconResolver(ThreadIcons::resolve)
        }

        override fun getPreferredSize(): Dimension {
            val preferred = super.getPreferredSize()
            return if (width > 0) Dimension(width, preferred.height) else preferred
        }
    }

    private companion object {
        const val CARD_GAP = 8
        const val CARD_PADDING = 10
        const val CARD_ARC = 10
        val AVATAR_SIZE = CircularAvatarIcon.MAX_SIZE
        const val AUTHOR_WIDTH = 150
        const val SCROLL_UNIT = 16
    }
}
