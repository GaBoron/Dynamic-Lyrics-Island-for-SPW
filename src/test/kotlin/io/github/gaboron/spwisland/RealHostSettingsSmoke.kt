// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.spwisland

import com.xuncorp.spw.workshop.api.config.*
import io.github.gaboron.spwisland.host.HostSettings


import java.nio.file.*
import java.util.function.Consumer


/** Run with an installed SPW runtime/classpath; the fixture path must be a disposable build file. */
fun main(args: Array<String>) {
    val file = Path.of(args[1]).toAbsolutePath()
    check(file.startsWith(Path.of("build").toAbsolutePath()))
    Files.createDirectories(file.parent); Files.writeString(file, "{}")
    fun helper() = Class.forName(args[0]).getConstructor(Path::class.java).newInstance(file) as ConfigHelper
    val original = helper()
    val manager = object : ConfigManager {
        override fun getConfig() = original
        override fun getConfig(fileName: String) = original
        override fun addConfigChangeListener(listener: Consumer<ConfigHelper>) {}
        override fun addConfigChangeListener(fileName: String, listener: Consumer<ConfigHelper>) {}
        override fun removeConfigChangeListener(listener: Consumer<ConfigHelper>) {}
    }
    var changes = 0
    HostSettings(manager) { changes++ }.use { settings ->
        val external = helper()
        external.set("font_size", 31.0f); external.set("enabled", false); check(external.save())
        val deadline = System.nanoTime() + 2_000_000_000
        while (settings.read().enabled && System.nanoTime() < deadline) Thread.sleep(25)
        check(!settings.read().enabled && settings.read().fontSize == 31) { "Missed file update without host callback" }
        Files.writeString(file, "{")
        settings.refresh()
        check(!settings.read().enabled && settings.read().fontSize == 31) { "Partial write replaced accepted settings" }
        check(external.save()); settings.refresh()
        external.set("lyric_cover_color", true); check(external.save()); settings.refresh()
        check(settings.read().lyricCoverColor)
        check(changes > 0)
        println("PASS: real SPW helper, missed callbacks, partial writes, native preference updates, disk round-trip")
    }
}
