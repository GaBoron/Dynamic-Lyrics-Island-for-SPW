// SPDX-License-Identifier: GPL-3.0-only
// Visual adaptation of Dynamic Lyrics Island by Lyricify / WXRIW; see NOTICE.
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.*
import javax.swing.*
import kotlin.math.sin

interface PlaybackActions { fun previous(); fun toggle(); fun next() }

class IslandPanel(private val actions: PlaybackActions) : JPanel(null) {
    var settings = IslandSettings()
    var snapshot = PlaybackSnapshot(null, null, 0, false, PlaybackStatus.IDLE)
    var expanded = false
    var transition = 1.0
    var outgoing: PlaybackSnapshot? = null
    private val bands = FloatArray(4)
    fun updateSpectrum(levels: FloatArray, dt: Double) {
        for (i in bands.indices) {
            val target = levels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val speed = if (target > bands[i]) 28 else 9
            bands[i] += (target - bands[i]) * (1 - kotlin.math.exp(-dt * speed)).toFloat()
        }
    }
    private val previous = control("上一首", "|◀") { actions.previous() }
    private val play = control("播放或暂停", "▶") { actions.toggle() }
    private val next = control("下一首", "▶|") { actions.next() }

    init { isOpaque = false; getAccessibleContext().accessibleName = "SPW 灵动词岛" }
    private fun control(label: String, glyph: String, clicked: () -> Unit) = JButton(glyph).apply {
        toolTipText = label; accessibleContext.accessibleName = label
        isFocusable = false; isContentAreaFilled = false; isBorderPainted = false
        margin = Insets(0, 0, 0, 0)
        foreground = Color(223, 228, 237); font = Font("Dialog", Font.PLAIN, 16)
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addActionListener { clicked() }; this@IslandPanel.add(this)
    }
    fun desiredSize(availableWidth: Int): Dimension {
        return IslandTextBlock(snapshot, settings).size(minOf(settings.maxWidth, availableWidth), expanded)
    }
    override fun doLayout() {
        val buttons = listOf(previous, play, next)
        buttons.forEachIndexed { index, button ->
            button.isVisible = expanded
            button.setBounds(width / 2 - 81 + index * 56, height - 39, 50, 30)
        }
        play.text = if (snapshot.playing) "Ⅱ" else "▶"
        play.toolTipText = if (snapshot.playing) "暂停" else "播放"
    }
    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            val shape = IslandGeometry.silhouette(width, height, settings.notch)
            g.color = Color(7, 8, 12, settings.opacity * 255 / 100); g.fill(shape)
            g.color = Color(255, 255, 255, 19); g.draw(shape)
            g.clip(shape)
            val block = IslandTextBlock(snapshot, settings)
            val lyricAreaHeight = (height - if (expanded) IslandTextBlock.EXPANDED_HEIGHT else 0).toFloat()
            val animation = if (settings.reducedMotion) 1.0 else transition
            drawIndicator(g, 30, (lyricAreaHeight / 2).toInt())
            IslandLyricsPainter.draw(g, snapshot, outgoing, settings, width, lyricAreaHeight, animation)
            drawStatus(g, (lyricAreaHeight / 2).toInt())
            if (expanded) {
                val label = listOfNotNull(snapshot.track?.title, snapshot.track?.artist).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { "在 SPW 中播放音乐" }
                LyricPainter.draw(g, label, emptyList(), 0, 42f, height - 48f, width - 84f,
                    block.mainFont.deriveFont(12f), false, Color(147, 156, 174))
            }
        } finally { g.dispose() }
    }
    private fun drawIndicator(g: Graphics2D, x: Int, centerY: Int) {
        g.color = Color(132, 216, 188)
        for (i in bands.indices) {
            val barHeight = 2 + (bands[i] * 24).toInt()
            g.fillRoundRect(x - 11 + i * 6, centerY - barHeight / 2, 3, barHeight, 3, 3)
        }
    }
    private fun drawStatus(g: Graphics2D, centerY: Int) {
        if (snapshot.line == null && snapshot.playing) {
            for (i in 0..2) {
                val a = if (settings.reducedMotion) 150 else (150 + 90 * sin(snapshot.positionMs / 350.0 - i)).toInt()
                g.color = Color(190, 204, 221, a); g.fillOval(width - 38 + i * 7, centerY - 2, 4, 4)
            }
        } else {
            g.color = Color(115, 131, 149)
            if (snapshot.playing) g.fillOval(width - 32, centerY - 3, 6, 6)
            else { g.fillRoundRect(width - 35, centerY - 5, 3, 10, 2, 2); g.fillRoundRect(width - 29, centerY - 5, 3, 10, 2, 2) }
        }
    }
}
