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
        // Native sliders use numbers; migrate the previous text fields.
        if (!Files.exists(config.getConfigPath()) || !config.reload()) return
        var migrated = false
        for ((key, limits) in mapOf("font_size" to Triple(22, 14, 42), "max_width" to Triple(640, 280, 1200),
            "opacity" to Triple(96, 35, 100), "offset_ms" to Triple(0, -2000, 2000))) {
            if (config.get<Any>(key, "") is String) {
                config.set(key, number(key, limits.first, limits.second, limits.third))
                migrated = true
            }
        }
        migrated = snapCornerRoundness() || migrated
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
        centerX = number("center_x", Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE).takeUnless { it == Int.MIN_VALUE },
        top = number("top", Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE).takeUnless { it == Int.MIN_VALUE },
        verticalAnchor = when (config.get("vertical_anchor", "free")) {
            "top" -> VerticalAnchor.TOP
            "bottom" -> VerticalAnchor.BOTTOM
            else -> VerticalAnchor.FREE
        }
    )
    internal fun refresh() {
        val notify = synchronized(lock) {
            if (closed || !Files.exists(config.getConfigPath())) return
            val bytes = Files.readAllBytes(config.getConfigPath())
            if (bytes.isEmpty() || fingerprint?.contentEquals(bytes) == true) return
            // Failed/partial writes must not replace the last usable snapshot with defaults.
            if (!config.reload()) return
            val normalized = snapCornerRoundness()
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
    private fun snapCornerRoundness(): Boolean {
        val raw = config.get<Any>("corner_roundness", 60)
        val value = (raw as? Number)?.toDouble() ?: (raw as? String)?.trim()?.toDoubleOrNull() ?: return false
        val rounded = value.takeIf { it.isFinite() }?.coerceIn(0.0, 100.0)?.roundToInt() ?: 60
        if (raw is Number && raw.toDouble() == rounded.toDouble()) return false
        config.set("corner_roundness", rounded)
        return true
    }
    override fun set(key: String, value: Any) = update { it.set(key, value) }
    override fun savePosition(screen: String, centerX: Int, top: Int, verticalAnchor: VerticalAnchor) = update {
        it.set("screen", screen); it.set("center_x", centerX); it.set("top", top)
        it.set("vertical_anchor", verticalAnchor.name.lowercase())
    }
    override fun resetPosition() = update {
        it.set("screen", ""); it.set("center_x", Int.MIN_VALUE); it.set("top", Int.MIN_VALUE)
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
}
