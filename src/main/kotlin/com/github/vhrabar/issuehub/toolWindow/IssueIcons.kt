package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.model.IssueLabel
import com.github.vhrabar.issuehub.model.IssueState
import com.github.vhrabar.issuehub.model.PullRequestState
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Arc2D
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Line2D
import java.awt.geom.Path2D
import java.awt.geom.RoundRectangle2D
import javax.swing.Icon

/** GitHub-ish state colors, tuned per popular theme so they stay legible on dark and on ligh omes. */
private val OPEN_COLOR = JBColor(Color(0x1A7F37), Color(0x3FB950))
private val CLOSED_COLOR = JBColor(Color(0x8250DF), Color(0xA371F7))
private val OTHER_COLOR = JBColor(Color(0x6E7781), Color(0x8B949E))

/** A closed pull request is red where a closed issue is purple; purple is what merged means here. */
private val MERGED_COLOR = JBColor(Color(0x8250DF), Color(0xA371F7))
private val PR_CLOSED_COLOR = JBColor(Color(0xCF222E), Color(0xF85149))

internal val IssueState.dotColor: Color
    get() =
        when (this) {
            IssueState.OPEN -> OPEN_COLOR
            IssueState.CLOSED -> CLOSED_COLOR
            IssueState.OTHER -> OTHER_COLOR
        }

internal val PullRequestState.dotColor: Color
    get() =
        when (this) {
            PullRequestState.OPEN -> OPEN_COLOR
            PullRequestState.DRAFT -> OTHER_COLOR
            PullRequestState.MERGED -> MERGED_COLOR
            PullRequestState.CLOSED -> PR_CLOSED_COLOR
        }

/** Fallback for labels the API returned without a color. */
private val NEUTRAL_LABEL = JBColor(Color(0x9AA7B0), Color(0x6C707E))

/** GitHub picks label colors against a white page, so lift them when the surface is dark. */
internal fun labelTint(
    color: String?,
    background: Color,
): Color {
    val base = color?.let { ColorUtil.fromHex(it, null) } ?: NEUTRAL_LABEL
    return if (ColorUtil.isDark(background)) ColorUtil.brighter(base, 1) else base
}

/**
 * The icons the thread puts in front of its history lines.
 *
 * An HTML pane can't be handed a painted [Icon], only a string it resolves through a callback, so
 * every icon needs a name — including the label pennant, whose colour rides along in its key.
 */
internal object ThreadIcons {
    const val COMMENT = "comment"
    const val OPENED = "opened"
    const val CLOSED = "closed"
    const val REOPENED = "reopened"
    const val ASSIGNEE = "assignee"
    const val MILESTONE = "milestone"
    const val RENAME = "rename"
    const val REFERENCE = "reference"
    const val COMMIT = "commit"
    const val OTHER = "other"

    private const val LABEL_PREFIX = "label:"

    /** Keyed by colour, so the pennant in the history matches the chip in the header. */
    fun label(label: IssueLabel): String = LABEL_PREFIX + label.color.orEmpty()

    /** Null for a name we don't know, which the pane renders as nothing at all. */
    fun resolve(key: String): Icon? =
        when (key) {
            COMMENT -> {
                IssueEventIcon(IssueEventIcon.Kind.COMMENT)
            }

            OPENED, REOPENED -> {
                IssueStateIcon(IssueState.OPEN)
            }

            CLOSED -> {
                IssueStateIcon(IssueState.CLOSED)
            }

            ASSIGNEE -> {
                IssueEventIcon(IssueEventIcon.Kind.ASSIGNEE)
            }

            MILESTONE -> {
                IssueEventIcon(IssueEventIcon.Kind.MILESTONE)
            }

            RENAME -> {
                IssueEventIcon(IssueEventIcon.Kind.RENAME)
            }

            REFERENCE -> {
                IssueEventIcon(IssueEventIcon.Kind.REFERENCE)
            }

            COMMIT -> {
                IssueEventIcon(IssueEventIcon.Kind.COMMIT)
            }

            OTHER -> {
                IssueEventIcon(IssueEventIcon.Kind.OTHER)
            }

            else -> {
                key
                    .takeIf { it.startsWith(LABEL_PREFIX) }
                    ?.let { IssueLabelIcon(labelTint(it.removePrefix(LABEL_PREFIX).ifEmpty { null }, UIUtil.getPanelBackground())) }
            }
        }
}

/**
 * The glyphs in front of the thread's history lines, in the same muted grey as the text they
 * introduce.
 *
 * Drawn rather than loaded: a platform icon resolves its image lazily and leaves the line blank
 * until something repaints it, and a thread is painted once and then sits still.
 */
internal class IssueEventIcon(
    private val kind: Kind,
    private val size: Int = SIZE,
) : Icon {
    enum class Kind { COMMENT, ASSIGNEE, MILESTONE, RENAME, REFERENCE, COMMIT, OTHER }

    override fun getIconWidth(): Int = JBUI.scale(size)

    override fun getIconHeight(): Int = JBUI.scale(size)

    override fun paintIcon(
        c: Component?,
        g: Graphics,
        x: Int,
        y: Int,
    ) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val s = JBUI.scale(size).toFloat()
            g2.color = UIUtil.getContextHelpForeground()
            g2.stroke = BasicStroke(JBUI.scale(1).toFloat() * STROKE, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
            when (kind) {
                Kind.COMMENT -> comment(g2, x, y, s)
                Kind.ASSIGNEE -> assignee(g2, x, y, s)
                Kind.MILESTONE -> milestone(g2, x, y, s)
                Kind.RENAME -> rename(g2, x, y, s)
                Kind.REFERENCE -> reference(g2, x, y, s)
                Kind.COMMIT -> commit(g2, x, y, s)
                Kind.OTHER -> other(g2, x, y, s)
            }
        } finally {
            g2.dispose()
        }
    }

    /** A speech balloon with a tail hanging off its bottom-left corner. */
    private fun comment(
        g2: Graphics2D,
        x: Int,
        y: Int,
        s: Float,
    ) {
        g2.fill(RoundRectangle2D.Float(x + 0.08f * s, y + 0.14f * s, 0.84f * s, 0.58f * s, 0.34f * s, 0.34f * s))
        g2.fill(
            Path2D.Float().apply {
                moveTo(x + 0.26f * s, y + 0.62f * s)
                lineTo(x + 0.26f * s, y + 0.92f * s)
                lineTo(x + 0.52f * s, y + 0.66f * s)
                closePath()
            },
        )
    }

    /** A head over shoulders. */
    private fun assignee(
        g2: Graphics2D,
        x: Int,
        y: Int,
        s: Float,
    ) {
        g2.fill(Ellipse2D.Float(x + 0.31f * s, y + 0.10f * s, 0.38f * s, 0.38f * s))
        g2.fill(Arc2D.Float(x + 0.12f * s, y + 0.54f * s, 0.76f * s, 0.62f * s, 0f, 180f, Arc2D.CHORD))
    }

    /** A pennant on a pole. */
    private fun milestone(
        g2: Graphics2D,
        x: Int,
        y: Int,
        s: Float,
    ) {
        g2.draw(Line2D.Float(x + 0.26f * s, y + 0.08f * s, x + 0.26f * s, y + 0.94f * s))
        g2.fill(
            Path2D.Float().apply {
                moveTo(x + 0.30f * s, y + 0.13f * s)
                lineTo(x + 0.88f * s, y + 0.32f * s)
                lineTo(x + 0.30f * s, y + 0.51f * s)
                closePath()
            },
        )
    }

    /** A pencil lying across the box. */
    private fun rename(
        g2: Graphics2D,
        x: Int,
        y: Int,
        s: Float,
    ) {
        g2.fill(
            Path2D.Float().apply {
                moveTo(x + 0.10f * s, y + 0.90f * s)
                lineTo(x + 0.24f * s, y + 0.60f * s)
                lineTo(x + 0.68f * s, y + 0.16f * s)
                lineTo(x + 0.86f * s, y + 0.34f * s)
                lineTo(x + 0.42f * s, y + 0.78f * s)
                closePath()
            },
        )
    }

    /** An arrow leaving the corner, for a reference that points somewhere else. */
    private fun reference(
        g2: Graphics2D,
        x: Int,
        y: Int,
        s: Float,
    ) {
        g2.draw(Line2D.Float(x + 0.16f * s, y + 0.84f * s, x + 0.72f * s, y + 0.28f * s))
        g2.fill(
            Path2D.Float().apply {
                moveTo(x + 0.88f * s, y + 0.12f * s)
                lineTo(x + 0.88f * s, y + 0.56f * s)
                lineTo(x + 0.44f * s, y + 0.12f * s)
                closePath()
            },
        )
    }

    /** A node on a branch line, the way a commit is drawn in a history graph. */
    private fun commit(
        g2: Graphics2D,
        x: Int,
        y: Int,
        s: Float,
    ) {
        g2.draw(Line2D.Float(x + 0.02f * s, y + 0.5f * s, x + 0.24f * s, y + 0.5f * s))
        g2.draw(Line2D.Float(x + 0.76f * s, y + 0.5f * s, x + 0.98f * s, y + 0.5f * s))
        g2.draw(Ellipse2D.Float(x + 0.26f * s, y + 0.26f * s, 0.48f * s, 0.48f * s))
    }

    /** An ellipsis, for something that happened that we have no better glyph for. */
    private fun other(
        g2: Graphics2D,
        x: Int,
        y: Int,
        s: Float,
    ) {
        val d = 0.2f * s
        listOf(0.12f, 0.4f, 0.68f).forEach { g2.fill(Ellipse2D.Float(x + it * s, y + 0.4f * s, d, d)) }
    }

    private companion object {
        const val SIZE = 13
        const val STROKE = 1.2f
    }
}

/** The box a state dot is centred in: the default suits label text, 16 matches an editor tab. */
private const val DOT_SIZE = 12

private const val DOT_RATIO = 2.0 / 3.0

/** A filled circle in [color], sized to sit on the baseline of the text beside it. */
internal open class DotIcon(
    private val color: Color,
    private val size: Int = DOT_SIZE,
) : Icon {
    override fun getIconWidth(): Int = JBUI.scale(size)

    override fun getIconHeight(): Int = JBUI.scale(size)

    override fun paintIcon(
        c: Component?,
        g: Graphics,
        x: Int,
        y: Int,
    ) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val d = JBUI.scale(size) * DOT_RATIO
            val offset = (JBUI.scale(size) - d) / 2
            g2.color = color
            g2.fill(Ellipse2D.Double(x + offset, y + offset, d, d))
        } finally {
            g2.dispose()
        }
    }
}

/** A dot marking issue state, in the same green and purple GitHub uses. */
internal class IssueStateIcon(
    state: IssueState,
    size: Int = DOT_SIZE,
) : DotIcon(state.dotColor, size)

/** A dot marking pull request state, which has a merged and a draft where an issue has neither. */
internal class PullRequestStateIcon(
    state: PullRequestState,
    size: Int = DOT_SIZE,
) : DotIcon(state.dotColor, size)

/**
 * A label/tag pennant filled with the label's own color.
 *
 */
internal class IssueLabelIcon(
    private val color: Color,
) : Icon {
    override fun getIconWidth(): Int = JBUI.scale(SIZE)

    override fun getIconHeight(): Int = JBUI.scale(SIZE)

    override fun paintIcon(
        c: Component?,
        g: Graphics,
        x: Int,
        y: Int,
    ) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val s = JBUI.scale(SIZE).toFloat()

            val body =
                Path2D.Float().apply {
                    moveTo(x + s * 0.06f, y + s * 0.50f)
                    lineTo(x + s * 0.36f, y + s * 0.18f)
                    lineTo(x + s * 0.94f, y + s * 0.18f)
                    lineTo(x + s * 0.94f, y + s * 0.82f)
                    lineTo(x + s * 0.36f, y + s * 0.82f)
                    closePath()
                }

            // Punch the eyelet out of the shape so it stays transparent on any row background.
            val shape = Area(body)
            val hole = s * 0.16f
            shape.subtract(Area(Ellipse2D.Float(x + s * 0.28f - hole / 2, y + s * 0.50f - hole / 2, hole, hole)))

            g2.color = color
            g2.fill(shape)
            g2.color = ColorUtil.darker(color, 2)
            g2.stroke = BasicStroke(JBUI.scale(1).toFloat())
            g2.draw(body)
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val SIZE = 13
    }
}

/**
 * A downloaded avatar image, clipped to a circle so it matches the initials fallback.
 *
 * [size] is the box it is drawn in: the default suits a list row, a thread card asks for more.
 */
internal class CircularAvatarIcon(
    private val image: java.awt.Image,
    private val size: Int = SIZE,
) : Icon {
    override fun getIconWidth(): Int = JBUI.scale(size)

    override fun getIconHeight(): Int = JBUI.scale(size)

    override fun paintIcon(
        c: Component?,
        g: Graphics,
        x: Int,
        y: Int,
    ) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val box = JBUI.scale(size)
            g2.clip = Ellipse2D.Double(x.toDouble(), y.toDouble(), box.toDouble(), box.toDouble())
            g2.drawImage(image, x, y, box, box, null)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val SIZE = 16

        /** The largest an avatar is ever drawn, which sets the resolution worth downloading. */
        const val MAX_SIZE = 24
    }
}

/**
 * Initials avatar for an account, drawn locally from the login.
 *.
 */
internal class IssueAvatarIcon(
    login: String,
    private val size: Int = SIZE,
) : Icon {
    private val initial = login.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    private val background = PALETTE[Math.floorMod(login.hashCode(), PALETTE.size)]

    override fun getIconWidth(): Int = JBUI.scale(size)

    override fun getIconHeight(): Int = JBUI.scale(size)

    override fun paintIcon(
        c: Component?,
        g: Graphics,
        x: Int,
        y: Int,
    ) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val box = JBUI.scale(size)
            g2.color = background
            g2.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), box.toDouble(), box.toDouble()))

            g2.color = JBColor.WHITE
            // The initial keeps its share of the circle however large the circle is asked to be.
            g2.font = JBUI.Fonts.label(FONT_SIZE * size / SIZE).asBold()
            val text = initial.toString()
            val metrics = g2.fontMetrics
            g2.drawString(
                text,
                x + (box - metrics.stringWidth(text)) / 2f,
                y + (box - metrics.height) / 2f + metrics.ascent,
            )
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val SIZE = 16
        const val FONT_SIZE = 9f

        /** Mid-tone hues that keep white initials readable in either theme (same in light and dark). */
        val PALETTE =
            listOf(0x4C7EBF, 0x3E8A6E, 0xA6683C, 0x8A5FB0, 0xBF5B5B, 0x3F8296)
                .map { JBColor(Color(it), Color(it)) }
    }
}
