// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

/** A seek gesture commits once on release and is cancelled if its track changes. */
class PlaybackProgress(private val seek: (Long) -> Unit) : JComponent() {
    companion object { const val FIXED_WIDTH = 276 }
    private var snapshot = PlaybackSnapshot(null, null, 0, false, PlaybackStatus.IDLE)
    private var gestureTrack: Track? = null
    private var preview: Long? = null
    val dragging get() = preview != null
    init {
        isOpaque = false
        accessibleContext?.accessibleName = "播放进度"
        val mouse = object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1 || !isEnabled) return
                gestureTrack = snapshot.track; preview = positionAt(e.x); repaint()
            }
            override fun mouseDragged(e: MouseEvent) {
                if (dragging) { preview = positionAt(e.x); repaint() }
            }
            override fun mouseReleased(e: MouseEvent) {
                if (e.button != MouseEvent.BUTTON1 || !dragging) return
                val target = positionAt(e.x)
                val valid = gestureTrack == snapshot.track && isEnabled
                preview = null; gestureTrack = null
                if (valid) seek(target)
                repaint()
            }
        }
        addMouseListener(mouse); addMouseMotionListener(mouse)
    }
    fun update(value: PlaybackSnapshot) {
        if (snapshot.track != value.track || value.metadata.durationMs <= 0 || value.status == PlaybackStatus.IDLE) {
            preview = null; gestureTrack = null
        }
        snapshot = value
        isEnabled = value.track != null && value.metadata.durationMs > 0 &&
            value.status != PlaybackStatus.IDLE && value.status != PlaybackStatus.ENDED
        cursor = Cursor.getPredefinedCursor(if (isEnabled) Cursor.HAND_CURSOR else Cursor.DEFAULT_CURSOR)
        toolTipText = if (isEnabled) "点击或拖动跳转" else "无法读取本地歌曲时长，进度控制不可用"
    }
    private fun positionAt(x: Int): Long = (((x - 48).toDouble() / (width - 96).coerceAtLeast(1))
        .coerceIn(0.0, 1.0) * snapshot.metadata.durationMs).toLong()

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val duration = snapshot.metadata.durationMs
            val position = (preview ?: snapshot.positionMs).coerceIn(0, duration.coerceAtLeast(0))
            val span = (width - 96).coerceAtLeast(1)
            val filled = if (duration > 0) (span * (position.toDouble() / duration)).toInt() else 0
            g.color = Color(72, 77, 89); g.fillRoundRect(48, 12, span, 4, 4, 4)
            g.color = if (isEnabled) foreground ?: Color.WHITE else Color.GRAY
            g.fillRoundRect(48, 12, filled, 4, 4, 4)
            if (isEnabled) g.fillOval(45 + filled, 9, 10, 10)
            g.font = Font("Dialog", Font.PLAIN, 10)
            g.drawString(time(if (duration > 0) position else snapshot.positionMs), 0, 18)
            val end = if (duration > 0) time(duration) else "--:--"
            g.drawString(end, width - g.fontMetrics.stringWidth(end), 18)
        } finally { g.dispose() }
    }
    private fun time(ms: Long): String = "%d:%02d".format(ms.coerceAtLeast(0) / 60000, ms.coerceAtLeast(0) / 1000 % 60)
}
