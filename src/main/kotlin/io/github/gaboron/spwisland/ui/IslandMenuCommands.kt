// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.SettingsStore
import io.github.gaboron.spwisland.platform.NativePopupMenu
import io.github.gaboron.spwisland.platform.NativePopupMenu.Entry
import io.github.gaboron.spwisland.platform.NativePopupMenu.Kind

/** Owns the shared menu model and translates native selections into plugin settings operations. */
internal class IslandMenuCommands(
    private val store: SettingsStore,
    private val showAbout: () -> Unit,
    private val openSource: () -> Unit
) {
    fun entries(): List<Entry> {
        val value = store.read()
        return listOf(
            title("常用设置"),
            toggle(LOW_PERFORMANCE, "低性能模式", value.lowPerformance),
            toggle(SHAPE, "顶部刘海", value.notch),
            toggle(TRANSLATION, "显示翻译", value.translation),
            toggle(KARAOKE, "逐字高亮", value.karaoke),
            toggle(CLICK_THROUGH, "鼠标穿透", value.clickThrough),
            toggle(AUTO_HIDE, "悬停自动隐藏", value.autoHideOnHover),
            separator(),
            title("显示设置"),
            toggle(ENABLED, "显示词岛", value.enabled),
            toggle(MULTI_LINE, "实验性多行歌词", value.experimentalMultiLine),
            toggle(HIDE_FULLSCREEN, "全屏时隐藏", value.hideFullscreen),
            toggle(HIDE_PAUSED, "暂停时隐藏", value.hidePaused),
            separator(),
            title("快捷操作"),
            action(RECOVER, "解除鼠标穿透并显示"),
            action(RESET_POSITION, "重置位置"),
            note("更多设置请前往 SPW 插件配置"),
            separator(),
            action(ABOUT, "关于与许可"),
            action(SOURCE, "项目源代码（GitHub）")
        )
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

    private fun title(label: String) = Entry(Kind.TITLE, label = label)
    private fun note(label: String) = Entry(Kind.NOTE, label = label)
    private fun separator() = Entry(Kind.SEPARATOR)
    private fun toggle(id: Int, label: String, selected: Boolean) = Entry(Kind.TOGGLE, id, selected, label)
    private fun action(id: Int, label: String) = Entry(Kind.ACTION, id, label = label)

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
