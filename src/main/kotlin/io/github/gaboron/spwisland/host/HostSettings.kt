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
        accepted = decode()
        manager.addConfigChangeListener("island.json", listener)
        // SPW can fail to register its watcher when a plugin's data directory does not yet exist.
        // Re-read the actual file independently; notifications remain a fast path only.
        poller.scheduleWithFixedDelay({ runCatching { refresh() }.onFailure {
            System.err.println("[SPW Island] Settings sync: ${it.message}")
        } }, 250, 250, TimeUnit.MILLISECONDS)
    }

    override fun read(): IslandSettings = synchronized(lock) { accepted ?: decode() }
    private fun decode(): IslandSettings = IslandSettings(
        enabled = config.get("enabled", true), translation = config.get("translation", true),
        karaoke = config.get("karaoke", true), hidePaused = config.get("hide_paused", false),
        hideFullscreen = config.get("hide_fullscreen", true), clickThrough = config.get("click_through", false),
        reducedMotion = config.get("reduced_motion", false), notch = config.get("shape", "pill") == "notch",
        fontFamily = config.get("font_family", "Microsoft YaHei UI").take(100).ifBlank { "Dialog" },
        fontSize = number("font_size", 22, 14, 42), maxWidth = number("max_width", 640, 280, 1200),
        opacity = number("opacity", 96, 35, 100), offsetMs = number("offset_ms", 0, -2000, 2000),
        screen = config.get("screen", ""),
        centerX = number("center_x", Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE).takeUnless { it == Int.MIN_VALUE },
        top = number("top", Int.MIN_VALUE, Int.MIN_VALUE, Int.MAX_VALUE).takeUnless { it == Int.MIN_VALUE }
    )
    internal fun refresh() {
        val notify = synchronized(lock) {
            if (closed || !Files.exists(config.getConfigPath())) return
            val bytes = Files.readAllBytes(config.getConfigPath())
            if (bytes.isEmpty() || fingerprint?.contentEquals(bytes) == true) return
            // Failed/partial writes must not replace the last usable snapshot with defaults.
            if (!config.reload()) return
            val value = decode()
            fingerprint = bytes
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
    override fun set(key: String, value: Any) = update { it.set(key, value) }
    override fun savePosition(screen: String, centerX: Int, top: Int) = update {
        it.set("screen", screen); it.set("center_x", centerX); it.set("top", top)
    }
    override fun resetPosition() = update {
        it.set("screen", ""); it.set("center_x", Int.MIN_VALUE); it.set("top", Int.MIN_VALUE)
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
