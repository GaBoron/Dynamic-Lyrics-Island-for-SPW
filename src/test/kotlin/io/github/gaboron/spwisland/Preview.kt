// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.ui.*
import java.awt.*
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import javax.swing.*

/** Standalone development preview. Not packaged in the plugin's classes/. */
fun main() {
    SwingUtilities.invokeLater {
        val timeline = PlaybackTimeline()
        var settings = IslandSettings(hideFullscreen = false)
        var window: IslandWindow? = null
        val store = object : SettingsStore {
            override fun read() = settings
            override fun set(key: String, value: Any) {
                settings = when (key) {
                    "enabled" -> settings.copy(enabled = value as Boolean)
                    "translation" -> settings.copy(translation = value as Boolean)
                    "karaoke" -> settings.copy(karaoke = value as Boolean)
                    "hide_paused" -> settings.copy(hidePaused = value as Boolean)
                    "hide_fullscreen" -> settings.copy(hideFullscreen = value as Boolean)
                    "click_through" -> settings.copy(clickThrough = value as Boolean)
                    "reduced_motion" -> settings.copy(reducedMotion = value as Boolean)
                    "shape" -> settings.copy(notch = value == "notch")
                    else -> settings
                }
                window?.reload()
            }
            override fun savePosition(screen: String, centerX: Int, top: Int) {
                settings = settings.copy(screen = screen, centerX = centerX, top = top); window?.reload()
            }
            override fun resetPosition() { settings = settings.copy(screen = "", centerX = null, top = null); window?.reload() }
        }
        val examples = listOf(
            samplePanel().snapshot.line!!,
            LyricLine(0, 6000, "风穿过城市的灯火", "A breeze through the city lights",
                listOf(Word(0, 700, "风"), Word(700, 2000, "穿过"), Word(2000, 4000, "城市的"), Word(4000, 6000, "灯火"))),
            LyricLine(0, 6500, "这是一行没有逐字时间的普通歌词", null, emptyList())
        )
        var index = 0
        fun select(direction: Int) {
            index = Math.floorMod(index + direction, examples.size)
            timeline.trackChanged(Track("预览 · 原创示例 ${index + 1}", "Dynamic Lyrics Island for SPW", "demo-$index"))
            timeline.positionChanged(0); timeline.lineChanged(examples[index])
            timeline.stateChanged(PlaybackStatus.READY); timeline.playingChanged(true)
        }
        val actions = object : PlaybackActions {
            override fun previous() = select(-1)
            override fun next() = select(1)
            override fun toggle() { timeline.playingChanged(!timeline.snapshot().playing) }
        }
        window = IslandWindow(timeline, store, actions, report = { error ->
            error.printStackTrace(); JOptionPane.showMessageDialog(null, error.message)
        })
        select(0)
        val heartbeat = Timer(1000) {
            val position = timeline.snapshot().positionMs
            if (position >= examples[index].endMs) select(1) else timeline.positionChanged(position)
        }.apply { start() }
        JFrame("词岛开发预览 · 不连接 SPW").apply {
            defaultCloseOperation = WindowConstants.DISPOSE_ON_CLOSE
            layout = FlowLayout(); setSize(600, 170); setLocationRelativeTo(null)
            add(JLabel("拖动／右击上方词岛可测试交互；关闭此窗口退出。"))
            fun button(label: String, action: () -> Unit) { add(JButton(label).apply { addActionListener { action() } }) }
            button("播放／暂停", actions::toggle); button("下一首", actions::next)
            button("跳到 3 秒") { timeline.seek(3000); timeline.lineChanged(examples[index]) }
            button("胶囊／刘海") { store.set("shape", if (settings.notch) "pill" else "notch") }
            button("找回词岛") { store.set("enabled", true); store.set("click_through", false); store.resetPosition() }
            addWindowListener(object : WindowAdapter() {
                override fun windowClosed(e: WindowEvent) { heartbeat.stop(); window?.close() }
            })
            isVisible = true
        }
    }
}
