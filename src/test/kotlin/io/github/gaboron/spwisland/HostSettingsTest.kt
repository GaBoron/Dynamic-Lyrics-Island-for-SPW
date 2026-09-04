// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.spwisland

import com.xuncorp.spw.workshop.api.config.*
import io.github.gaboron.spwisland.host.HostSettings
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Path
import java.util.function.Consumer

class HostSettingsTest {
    @Test fun acceptsHostSliderFloatsClampsAndUnsubscribes() {
        val fake = MemoryConfig()
        fake.values["font_size"] = 30.7; fake.values["max_width"] = 90000.0
        fake.values["opacity"] = Double.NaN; fake.values["offset_ms"] = -9000.0
        val settings = HostSettings(fake) {}
        assertEquals(31, settings.read().fontSize); assertEquals(1200, settings.read().maxWidth)
        assertEquals(96, settings.read().opacity); assertEquals(-2000, settings.read().offsetMs)
        assertEquals(1, fake.listeners.size)
        settings.close(); assertTrue(fake.listeners.isEmpty())
    }
    @Test fun positionResetAndSaveFailuresAreVisible() {
        val fake = MemoryConfig(); val settings = HostSettings(fake) {}
        settings.savePosition("monitor", -300, 30)
        assertEquals(-300, settings.read().centerX)
        settings.resetPosition(); assertNull(settings.read().centerX); assertNull(settings.read().top)
        fake.writable = false
        assertThrows(IllegalStateException::class.java) { settings.set("enabled", false) }
        settings.close()
    }
    @Test fun hostNotificationReloadsItsHelperBeforeReadingEverySetting() {
        val manager = MemoryConfig()
        var changes = 0
        val settings = HostSettings(manager) { changes++ }
        val saved = mapOf<String, Any>("enabled" to false, "translation" to false, "karaoke" to false,
            "hide_paused" to true, "hide_fullscreen" to false, "click_through" to true,
            "reduced_motion" to true, "shape" to "notch", "font_family" to "Malgun Gothic",
            "font_size" to "28", "max_width" to "820", "opacity" to "80", "offset_ms" to "-150")
        // Like SPW, the notification helper has stale values until reload is called.
        val incoming = MemoryConfig().apply { reloadValues = saved }
        manager.listeners.forEach { it.accept(incoming) }
        val value = settings.read()
        assertEquals(1, incoming.reloadCount); assertEquals(1, changes)
        assertFalse(value.enabled); assertFalse(value.translation); assertFalse(value.karaoke)
        assertTrue(value.hidePaused); assertFalse(value.hideFullscreen); assertTrue(value.clickThrough)
        assertTrue(value.reducedMotion); assertTrue(value.notch); assertEquals("Malgun Gothic", value.fontFamily)
        assertEquals(28, value.fontSize); assertEquals(820, value.maxWidth)
        assertEquals(80, value.opacity); assertEquals(-150, value.offsetMs)
        settings.set("enabled", true)
        assertEquals("28", incoming.values["font_size"])
        settings.close()
    }
    @Test fun localPositionSaveReloadsUnnotifiedHostSettings() {
        val path = Path.of("build/test-configs/island.json")
        java.nio.file.Files.createDirectories(path.parent)
        java.nio.file.Files.writeString(path, "{}")
        val fake = MemoryConfig(path).apply { reloadValues = mapOf("translation" to false, "font_size" to "34") }
        val settings = HostSettings(fake) {}
        settings.savePosition("display", 300, 0)
        assertFalse(settings.read().translation); assertEquals(34, settings.read().fontSize)
        assertEquals(1, fake.reloadCount)
        settings.close()
    }
}

internal class MemoryConfig(private val path: Path = Path.of("island.json")) : ConfigManager, ConfigHelper {
    val values = mutableMapOf<String, Any>()
    val listeners = mutableSetOf<Consumer<ConfigHelper>>()
    var writable = true
    var reloadValues: Map<String, Any>? = null
    var reloadCount = 0
    @Suppress("UNCHECKED_CAST") override fun <T> get(key: String, defaultValue: T): T = (values[key] ?: defaultValue) as T
    override fun set(key: String, value: Any) { values[key] = value }
    override fun save() = writable
    override fun reload(): Boolean {
        reloadCount++
        reloadValues?.let { values.clear(); values.putAll(it) }
        return true
    }
    override fun getConfigPath(): Path = path
    override fun getConfig() = this
    override fun getConfig(fileName: String) = this
    override fun addConfigChangeListener(listener: Consumer<ConfigHelper>) { listeners.add(listener) }
    override fun addConfigChangeListener(fileName: String, listener: Consumer<ConfigHelper>) { listeners.add(listener) }
    override fun removeConfigChangeListener(listener: Consumer<ConfigHelper>) { listeners.remove(listener) }
}
