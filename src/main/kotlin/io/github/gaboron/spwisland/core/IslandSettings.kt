// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

enum class LeadingContent { SPECTRUM, COVER }

data class IslandSettings(
    val enabled: Boolean = true,
    val translation: Boolean = true,
    val karaoke: Boolean = true,
    val experimentalMultiLine: Boolean = false,
    val hidePaused: Boolean = false,
    val hideFullscreen: Boolean = true,
    val clickThrough: Boolean = false,
    val autoHideOnHover: Boolean = false,
    val lowPerformance: Boolean = false,
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
    val positionX: Int? = null,
    val positionY: Int? = null,
    val positionAnchor: IslandAnchor? = null,
    val legacyCenterX: Int? = null,
    val legacyTop: Int? = null
)

interface SettingsStore {
    fun read(): IslandSettings
    fun set(key: String, value: Any)
    fun savePosition(screen: String, x: Int, y: Int, anchor: IslandAnchor)
    fun resetPosition()
}
