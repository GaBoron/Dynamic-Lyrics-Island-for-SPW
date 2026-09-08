// SPDX-License-Identifier: GPL-3.0-only
// Visual adaptation of Dynamic Lyrics Island by Lyricify / WXRIW; see NOTICE.
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.*
import javax.swing.*
import kotlin.math.sin

interface PlaybackActions { fun previous(); fun toggle(); fun next(); fun seek(positionMs: Long) {} }

class IslandPanel(private val actions: PlaybackActions) : JPanel(null) {
    private val alphaMask = IslandAlphaMask()
    override fun paint(graphics: Graphics) = alphaMask.paint(graphics as Graphics2D, this) { super.paint(it) }
    var settings = IslandSettings()
    var snapshot = PlaybackSnapshot(null, null, 0, false, PlaybackStatus.IDLE)
    var expanded = false
    var expandUpward = false
    var expansion: Double? = null
    var transition = 1.0
    var outgoing: PlaybackSnapshot? = null
    val progress = PlaybackProgress(actions::seek).also { add(it) }
    private val bands = FloatArray(4)
    fun updateSpectrum(levels: FloatArray, dt: Double) {
        for (i in bands.indices) {
            val target = levels.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val speed = if (target > bands[i]) 28 else 9
            bands[i] += (target - bands[i]) * (1 - kotlin.math.exp(-dt * speed)).toFloat()
        }
    }
    private val previous = control("上一首", PlaybackIcon.PREVIOUS) { actions.previous() }
    private val play = control("播放或暂停", PlaybackIcon.PLAY) { actions.toggle() }
    private val next = control("下一首", PlaybackIcon.NEXT) { actions.next() }

    init { isOpaque = false; getAccessibleContext().accessibleName = "SPW 灵动词岛" }
    private fun control(label: String, glyph: Icon, clicked: () -> Unit) = JButton(glyph).apply {
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
    fun collapsedHeight(availableWidth: Int): Int =
        IslandTextBlock(snapshot, settings).size(minOf(settings.maxWidth, availableWidth), false).height
    override fun doLayout() {
        progress.update(snapshot)
        val controlsVisible = expanded && (expansion ?: 1.0) > .95
        progress.isVisible = controlsVisible
        progress.setBounds((width - PlaybackProgress.FIXED_WIDTH) / 2,
            if (expandUpward) 10 else height - 38, PlaybackProgress.FIXED_WIDTH, 28)
        progress.foreground = IslandPalette.from(settings, snapshot.metadata.coverRgb).lyric
        val buttons = listOf(previous, play, next)
        buttons.forEachIndexed { index, button ->
            button.isVisible = controlsVisible
            button.setBounds(width / 2 - 81 + index * 56, if (expandUpward) 42 else height - 72, 50, 30)
        }
        play.icon = if (snapshot.playing) PlaybackIcon.PAUSE else PlaybackIcon.PLAY
        play.toolTipText = if (snapshot.playing) "暂停" else "播放"
    }
    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            // A resized translucent surface must not retain pixels from the previous silhouette.
            val composite = g.composite
            g.composite = AlphaComposite.Clear
            g.fillRect(0, 0, width, height)
            g.composite = composite
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON)
            val shape = IslandGeometry.silhouette(width, height, settings.notch, settings.cornerRoundness)
            val palette = IslandPalette.from(settings, snapshot.metadata.coverRgb)
            val background = palette.background
            g.color = Color(background.red, background.green, background.blue, settings.opacity * 255 / 100)
            g.fill(shape)
            g.color = Color(255, 255, 255, 19); g.draw(shape)
            g.clip(shape)
            val block = IslandTextBlock(snapshot, settings)
            val reveal = expansion ?: if (expanded) 1.0 else 0.0
            val lyricAreaHeight = (height - reveal * IslandTextBlock.EXPANDED_HEIGHT).toFloat().coerceAtLeast(1f)
            val lyricAreaTop = if (expandUpward) height - lyricAreaHeight else 0f
            val animation = if (settings.reducedMotion) 1.0 else transition
            val sidePadding = (IslandTextBlock.EXPANDED_SIDE_PADDING * reveal).toFloat()
            val textInset = IslandTextBlock.INSET + sidePadding
            IslandLeadingContent.draw(g, settings.leadingContent, snapshot.metadata.cover,
                bands, (textInset - 24).toInt(), (lyricAreaTop + lyricAreaHeight / 2).toInt(),
                palette.spectrum)
            val lyrics = g.create() as Graphics2D
            try {
                lyrics.translate(0.0, lyricAreaTop.toDouble())
                IslandLyricsPainter.draw(lyrics, snapshot, outgoing, settings, width,
                    lyricAreaHeight, animation, textInset)
            } finally { lyrics.dispose() }
            drawStatus(g, (lyricAreaTop + lyricAreaHeight / 2).toInt(), (width - textInset + 24).toInt())
            if (expanded && reveal > .95) {
                val label = listOfNotNull(snapshot.track?.title, snapshot.track?.artist).filter { it.isNotBlank() }.joinToString(" · ")
                    .ifBlank { "在 SPW 中播放音乐" }
                LyricPainter.draw(g, label, emptyList(), 0, 42f + sidePadding,
                    if (expandUpward) 81f else height - 81f,
                    width - 84f - sidePadding * 2,
                    block.mainFont.deriveFont(12f), false, Color(147, 156, 174))
            }
        } finally { g.dispose() }
    }
    private fun drawStatus(g: Graphics2D, centerY: Int, centerX: Int) {
        if (snapshot.line == null && snapshot.playing) {
            for (i in 0..2) {
                val a = if (settings.reducedMotion) 150 else (150 + 90 * sin(snapshot.positionMs / 350.0 - i)).toInt()
                g.color = Color(190, 204, 221, a); g.fillOval(centerX - 8 + i * 7, centerY - 2, 4, 4)
            }
        } else {
            g.color = Color(115, 131, 149)
            if (snapshot.playing) g.fillOval(centerX - 3, centerY - 3, 6, 6)
            else { g.fillRoundRect(centerX - 6, centerY - 5, 3, 10, 2, 2); g.fillRoundRect(centerX, centerY - 5, 3, 10, 2, 2) }
        }
    }
}
