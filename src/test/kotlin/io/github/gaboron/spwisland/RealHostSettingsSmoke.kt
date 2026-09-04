// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.spwisland

import com.xuncorp.spw.workshop.api.config.*
import io.github.gaboron.spwisland.host.HostSettings
import io.github.gaboron.spwisland.ui.IslandSettingsPanel
import java.awt.Container
import java.nio.file.*
import java.util.function.Consumer
import javax.swing.*

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
        SwingUtilities.invokeAndWait {
            val panel = IslandSettingsPanel(settings)
            fun descendants(root: Container): List<java.awt.Component> = root.components.flatMap {
                listOf(it) + if (it is Container) descendants(it) else emptyList()
            }
            val controls = descendants(panel)
            panel.setSize(640, panel.preferredSize.height)
            fun layout(root: Container) { root.doLayout(); root.components.filterIsInstance<Container>().forEach(::layout) }
            layout(panel)
            val image = java.awt.image.BufferedImage(panel.width, panel.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
            image.createGraphics().let { panel.printAll(it); it.dispose() }
            Files.createDirectories(Path.of("build/preview"))
            javax.imageio.ImageIO.write(image, "png", Path.of("build/preview/settings.png").toFile())
            for (box in controls.filterIsInstance<JCheckBox>()) {
                val expected = !box.isSelected; box.doClick()
                check(helper().get(box.name, !expected) == expected) { "Switch did not persist: ${box.name}" }
            }
            for (slider in controls.filterIsInstance<JSlider>()) {
                check(slider.snapToTicks && slider.minorTickSpacing == 1)
                val target = slider.value + 1
                slider.value = target
                check(helper().get(slider.name, Int.MIN_VALUE) == target) { "Slider did not persist integer: ${slider.name}" }
                val spinner = controls.filterIsInstance<JSpinner>().single { it.name == "${slider.name}.value" }
                spinner.value = target + 1
                check(slider.value == target + 1 && helper().get(slider.name, Int.MIN_VALUE) == target + 1)
            }
            controls.filterIsInstance<JComboBox<*>>().single { it.name == "shape" }.selectedIndex = 1
            controls.filterIsInstance<JComboBox<*>>().single { it.name == "font_family" }.selectedItem = "Malgun Gothic"
            check(settings.read().notch && settings.read().fontFamily == "Malgun Gothic")
        }
        check(changes > 10)
        println("PASS: real SPW helper, missed callbacks, partial writes, 7 switches, 4 integer sliders/spinners, shape/font, disk round-trip")
    }
}
