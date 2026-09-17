// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import com.sun.jna.Platform
import io.github.gaboron.spwisland.core.SettingsStore
/** Owns the shared menu model and translates native selections into plugin settings operations. */
internal class IslandMenuCommands(
    private val store: SettingsStore,
    private val showAbout: () -> Unit,
    private val openSource: () -> Unit
) {
    fun entries(): List<PopupMenuEntry> {
        val value = store.read()
        return buildList {
            add(title("常用设置"))
            add(toggle(LOW_PERFORMANCE, "低性能模式", value.lowPerformance))
            add(toggle(SHAPE, "顶部刘海", value.notch))
            add(toggle(TRANSLATION, "显示翻译", value.translation))
            add(toggle(KARAOKE, "逐字高亮", value.karaoke))
            if (Platform.isWindows()) {
                add(toggle(CLICK_THROUGH, "鼠标穿透", value.clickThrough))
                add(toggle(AUTO_HIDE, "悬停自动隐藏", value.autoHideOnHover))
            }
            add(separator())
            add(title("显示设置"))
            add(toggle(ENABLED, "显示词岛", value.enabled))
            add(toggle(MULTI_LINE, "实验性多行歌词", value.experimentalMultiLine))
            if (Platform.isWindows()) add(toggle(HIDE_FULLSCREEN, "全屏时隐藏", value.hideFullscreen))
            add(toggle(HIDE_PAUSED, "暂停时隐藏", value.hidePaused))
            add(separator())
            add(title("快捷操作"))
            add(action(RECOVER, if (Platform.isWindows()) "解除鼠标穿透并显示" else "显示词岛"))
            add(action(RESET_POSITION, "重置位置"))
            add(note("更多设置请前往 SPW 插件配置"))
            add(separator())
            add(action(ABOUT, "关于与许可"))
            add(action(SOURCE, "项目源代码（GitHub）"))
        }
    }

    fun execute(command: Int) {
        val value = store.read()
        when (command) {
            LOW_PERFORMANCE -> store.set("reduced_motion", !value.lowPerformance)
            SHAPE -> store.set("shape", if (value.notch) "pill" else "notch")
            TRANSLATION -> store.set("translation", !value.translation)
            KARAOKE -> store.set("karaoke", !value.karaoke)
            CLICK_THROUGH -> store.set("click_through", !value.clickThrough)
            AUTO_HIDE -> store.set("auto_hide_on_hover", !value.autoHideOnHover)
            ENABLED -> store.set("enabled", !value.enabled)
            MULTI_LINE -> store.set("experimental_multi_line", !value.experimentalMultiLine)
            HIDE_FULLSCREEN -> store.set("hide_fullscreen", !value.hideFullscreen)
            HIDE_PAUSED -> store.set("hide_paused", !value.hidePaused)
            RECOVER -> { store.set("click_through", false); store.set("enabled", true) }
            RESET_POSITION -> store.resetPosition()
            ABOUT -> showAbout()
            SOURCE -> openSource()
        }
    }

    private fun title(label: String) = PopupMenuEntry(PopupMenuKind.TITLE, label = label)
    private fun note(label: String) = PopupMenuEntry(PopupMenuKind.NOTE, label = label)
    private fun separator() = PopupMenuEntry(PopupMenuKind.SEPARATOR)
    private fun toggle(id: Int, label: String, selected: Boolean) =
        PopupMenuEntry(PopupMenuKind.TOGGLE, id, selected, label)
    private fun action(id: Int, label: String) = PopupMenuEntry(PopupMenuKind.ACTION, id, label = label)

    private companion object {
        const val LOW_PERFORMANCE = 101
        const val SHAPE = 102
        const val TRANSLATION = 103
        const val KARAOKE = 104
        const val CLICK_THROUGH = 105
        const val AUTO_HIDE = 106
        const val ENABLED = 107
        const val MULTI_LINE = 108
        const val HIDE_FULLSCREEN = 109
        const val HIDE_PAUSED = 110
        const val RECOVER = 201
        const val RESET_POSITION = 202
        const val ABOUT = 203
        const val SOURCE = 204
    }
}
