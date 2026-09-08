// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

enum class LeadingContent { SPECTRUM, COVER }
enum class VerticalAnchor { FREE, TOP, BOTTOM }

data class IslandSettings(
    val enabled: Boolean = true,
    val translation: Boolean = true,
    val karaoke: Boolean = true,
    val hidePaused: Boolean = false,
    val hideFullscreen: Boolean = true,
    val clickThrough: Boolean = false,
    val reducedMotion: Boolean = false,
    val notch: Boolean = false,
    val cornerRoundness: Int = 60,
    val lyricCoverColor: Boolean = false,
    val backgroundCoverColor: Boolean = false,
    val spectrumCoverColor: Boolean = false,
    val fixedWidth: Boolean = false,
    val leadingContent: LeadingContent = LeadingContent.SPECTRUM,
    val fontFamily: String = "Microsoft YaHei UI",
    val fontSize: Int = 22,
    val maxWidth: Int = 640,
    val opacity: Int = 96,
    val offsetMs: Int = 0,
    val screen: String = "",
    val centerX: Int? = null,
    val top: Int? = null,
    val verticalAnchor: VerticalAnchor = VerticalAnchor.FREE
)

interface SettingsStore {
    fun read(): IslandSettings
    fun set(key: String, value: Any)
    fun savePosition(screen: String, centerX: Int, top: Int, verticalAnchor: VerticalAnchor)
    fun resetPosition()
}
