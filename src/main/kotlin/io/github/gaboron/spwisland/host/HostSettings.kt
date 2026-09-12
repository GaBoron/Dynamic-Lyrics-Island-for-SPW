// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.spwisland.host

import com.xuncorp.spw.workshop.api.config.ConfigManager
import com.xuncorp.spw.workshop.api.config.ConfigHelper
import io.github.gaboron.spwisland.core.*
import java.util.function.Consumer
import java.nio.file.Files
import kotlin.math.roundToInt
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class HostSettings(private val manager: ConfigManager, private val changed: () -> Unit) : SettingsStore, AutoCloseable {
    private val lock = Any()
    private var config = manager.getConfig("island.json")
    private var closed = false
    private var accepted: IslandSettings? = null
    private var fingerprint: ByteArray? = null
    private val poller = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "SPW Island settings sync").apply { isDaemon = true }
    }
    private val listener = Consumer<ConfigHelper> { updated ->
        val loaded = synchronized(lock) {
            // SPW's settings page writes through a different helper; notification values can be cached.
            if (closed || !updated.reload()) false else {
                config = updated; accepted = decode(); true
            }
        }
        if (loaded) changed()
    }
    init {
        migrateSettings()
        accepted = decode()
        manager.addConfigChangeListener("island.json", listener)
        // SPW can fail to register its watcher when a plugin's data directory does not yet exist.
        // Re-read the actual file independently; notifications remain a fast path only.
        poller.scheduleWithFixedDelay({ runCatching { refresh() }.onFailure {
            System.err.println("[SPW Island] Settings sync: ${it.message}")
        } }, 250, 250, TimeUnit.MILLISECONDS)
    }

    override fun read(): IslandSettings = synchronized(lock) { accepted ?: decode() }
    private fun migrateSettings() {
        // Native sliders use floating-point values. Persist whole-number settings as integers
        // so reopening SPW does not inherit noisy fractional values from the slider thumb.
        if (!Files.exists(config.getConfigPath()) || !config.reload()) return
        val migrated = normalizeIntegerSettings()
        if (migrated) check(config.save()) { "词岛旧设置迁移失败，请检查 SPW 配置目录权限。" }
    }
    private fun decode(): IslandSettings = IslandSettings(
        enabled = config.get("enabled", true), translation = config.get("translation", true),
        karaoke = config.get("karaoke", true),
        experimentalMultiLine = config.get("experimental_multi_line", false),
        hidePaused = config.get("hide_paused", false),
        hideFullscreen = config.get("hide_fullscreen", true), clickThrough = config.get("click_through", false),
        autoHideOnHover = config.get("auto_hide_on_hover", false),
        // Keep the original key so existing users retain their enabled setting after the rename.
        lowPerformance = config.get("reduced_motion", false), notch = config.get("shape", "pill") == "notch",
        cornerRoundness = number("corner_roundness", 60, 0, 100),
        lyricCoverColor = config.get("lyric_cover_color", false),
        backgroundCoverColor = config.get("background_cover_color", false),
        spectrumCoverColor = config.get("spectrum_cover_color", false),
        fixedWidth = config.get("fixed_width", false),
        leadingContent = when (config.get("leading_content", "spectrum")) {
            "cover" -> LeadingContent.COVER
            else -> LeadingContent.SPECTRUM
        },
        fontFamily = config.get("font_family", "Microsoft YaHei UI").take(100).ifBlank { "Dialog" },
        fontSize = number("font_size", 22, 14, 42), maxWidth = number("max_width", 640, 280, 1200),
        opacity = number("opacity", 96, 35, 100), offsetMs = number("offset_ms", 0, -2000, 2000),
        screen = config.get("screen", ""),
        positionX = optionalNumber("position_x"),
        positionY = optionalNumber("position_y"),
        positionAnchor = IslandAnchor.fromStorage(config.get("position_anchor", "")),
        legacyCenterX = optionalNumber("center_x"),
        legacyTop = optionalNumber("top")
    )
    internal fun refresh() {
        val notify = synchronized(lock) {
            if (closed || !Files.exists(config.getConfigPath())) return
            val bytes = Files.readAllBytes(config.getConfigPath())
            if (bytes.isEmpty() || fingerprint?.contentEquals(bytes) == true) return
            // Failed/partial writes must not replace the last usable snapshot with defaults.
            if (!config.reload()) return
            val normalized = normalizeIntegerSettings()
            if (normalized && !config.save()) return
            val value = decode()
            fingerprint = if (normalized) Files.readAllBytes(config.getConfigPath()) else bytes
            (value != accepted).also { accepted = value }
        }
        if (notify) changed()
    }
    private fun number(key: String, default: Int, min: Int, max: Int): Int {
        // Integer text fields coexist with numeric values saved by the original sliders.
        val text = config.get<Any>(key, "") as? String
        val value = text?.trim()?.toDoubleOrNull() ?: (config.get<Any>(key, default) as? Number)?.toDouble()
        return value?.takeIf { it.isFinite() }?.coerceIn(min.toDouble(), max.toDouble())?.roundToInt() ?: default
    }

    private fun optionalNumber(key: String): Int? =
        number(key, Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE).takeUnless { it == Int.MIN_VALUE }

    private fun normalizeIntegerSettings(): Boolean {
        var changed = false
        for ((key, limits) in INTEGER_SETTINGS) {
            val raw = config.get<Any>(key, "")
            val value = (raw as? Number)?.toDouble() ?: (raw as? String)?.trim()?.toDoubleOrNull() ?: continue
            val rounded = value.takeIf { it.isFinite() }
                ?.coerceIn(limits.first.toDouble(), limits.last.toDouble())?.roundToInt() ?: limits.default
            if (raw !is Number || raw.toDouble() != rounded.toDouble()) {
                config.set(key, rounded)
                changed = true
            }
        }
        return changed
    }
    override fun set(key: String, value: Any) = update { it.set(key, value) }
    override fun savePosition(screen: String, x: Int, y: Int, anchor: IslandAnchor) = update {
        it.set("screen", screen); it.set("position_x", x); it.set("position_y", y)
        it.set("position_anchor", anchor.storageName)
        // Clear legacy center/top storage after the first drag on the automatic anchor model.
        it.set("center_x", Int.MIN_VALUE); it.set("top", Int.MIN_VALUE); it.set("vertical_anchor", "free")
    }
    override fun resetPosition() = update {
        it.set("screen", ""); it.set("position_x", Int.MIN_VALUE); it.set("position_y", Int.MIN_VALUE)
        it.set("position_anchor", ""); it.set("center_x", Int.MIN_VALUE); it.set("top", Int.MIN_VALUE)
        it.set("vertical_anchor", "free")
    }
    private fun update(change: (ConfigHelper) -> Unit) {
        synchronized(lock) {
            if (closed) return
            // Preserve settings written by the host even if its notification has not arrived yet.
            if (Files.exists(config.getConfigPath())) check(config.reload()) { "词岛设置读取失败，未覆盖已有设置。" }
            change(config)
            check(config.save()) { "词岛设置保存失败，请检查 SPW 配置目录权限。" }
            accepted = decode()
            fingerprint = null
        }
        changed()
    }
    override fun close() {
        synchronized(lock) { closed = true }
        poller.shutdownNow()
        manager.removeConfigChangeListener(listener)
    }

    private data class IntegerLimits(val default: Int, val first: Int, val last: Int)

    private companion object {
        val INTEGER_SETTINGS = mapOf(
            "corner_roundness" to IntegerLimits(60, 0, 100),
            "font_size" to IntegerLimits(22, 14, 42),
            "max_width" to IntegerLimits(640, 280, 1200),
            "opacity" to IntegerLimits(96, 35, 100),
            "offset_ms" to IntegerLimits(0, -2000, 2000)
        )
    }
}
