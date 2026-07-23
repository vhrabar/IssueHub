package com.github.vhrabar.issuehub.toolWindow

import com.github.vhrabar.issuehub.model.IssueState
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.geom.Area
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import javax.swing.Icon

/** GitHub-ish state colors, tuned per popular theme so they stay legible on dark and on ligh omes. */
private val OPEN_COLOR = JBColor(Color(0x1A7F37), Color(0x3FB950))
private val CLOSED_COLOR = JBColor(Color(0x8250DF), Color(0xA371F7))
private val OTHER_COLOR = JBColor(Color(0x6E7781), Color(0x8B949E))

internal val IssueState.dotColor: Color
    get() =
        when (this) {
            IssueState.OPEN -> OPEN_COLOR
            IssueState.CLOSED -> CLOSED_COLOR
            IssueState.OTHER -> OTHER_COLOR
        }

/** A filled circle marking issue state, sized to sit on the title baseline. */
internal class IssueStateIcon(
    private val state: IssueState,
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
            val d = JBUI.scale(DOT).toDouble()
            val offset = (JBUI.scale(SIZE) - d) / 2
            g2.color = state.dotColor
            g2.fill(Ellipse2D.Double(x + offset, y + offset, d, d))
        } finally {
            g2.dispose()
        }
    }

    private companion object {
        const val SIZE = 12
        const val DOT = 8
    }
}

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

/** A downloaded avatar image, clipped to a circle so it matches the initials fallback. */
internal class CircularAvatarIcon(
    private val image: java.awt.Image,
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
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            val size = JBUI.scale(SIZE)
            g2.clip = Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble())
            g2.drawImage(image, x, y, size, size, null)
        } finally {
            g2.dispose()
        }
    }

    companion object {
        const val SIZE = 16
    }
}

/**
 * Initials avatar for an account, drawn locally from the login.
 *.
 */
internal class IssueAvatarIcon(
    login: String,
) : Icon {
    private val initial = login.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?'
    private val background = PALETTE[Math.floorMod(login.hashCode(), PALETTE.size)]

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
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)

            val size = JBUI.scale(SIZE)
            g2.color = background
            g2.fill(Ellipse2D.Double(x.toDouble(), y.toDouble(), size.toDouble(), size.toDouble()))

            g2.color = JBColor.WHITE
            g2.font = JBUI.Fonts.label(FONT_SIZE).asBold()
            val text = initial.toString()
            val metrics = g2.fontMetrics
            g2.drawString(
                text,
                x + (size - metrics.stringWidth(text)) / 2f,
                y + (size - metrics.height) / 2f + metrics.ascent,
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
