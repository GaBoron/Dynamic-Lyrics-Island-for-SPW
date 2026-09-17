// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import java.awt.*
import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.swing.*

/** Opt-in desktop integration check. Run with an otherwise idle desktop, never headlessly. */
object LinuxDesktopCheck {
    @JvmStatic fun main(args: Array<String>) {
        val output = File("build/desktop-check").apply { mkdirs() }
        val errors = mutableListOf<Throwable>()
        val frames = AtomicInteger()
        val cleanup = mutableListOf<() -> Unit>()
        val timeline = PlaybackTimeline()
        timeline.playingChanged(true)
        val settings = object : SettingsStore {
            override fun read() = IslandSettings(legacyCenterX = 400, legacyTop = 120)
            override fun set(key: String, value: Any) = Unit
            override fun savePosition(screen: String, x: Int, y: Int, anchor: IslandAnchor) = Unit
            override fun resetPosition() = Unit
        }
        lateinit var island: IslandWindow
        lateinit var host: JFrame
        lateinit var owner: JFrame
        val robot = Robot().apply { autoDelay = 40 }
        fun edt(block: () -> Unit) = SwingUtilities.invokeAndWait(block)
        fun capture(name: String, component: Component): BufferedImage {
            lateinit var image: BufferedImage
            edt {
                image = BufferedImage(component.width, component.height, BufferedImage.TYPE_INT_ARGB)
                val g = image.createGraphics()
                try { component.paint(g) } finally { g.dispose() }
            }
            ImageIO.write(image, "png", File(output, "$name.png"))
            return image
        }
        try {
            edt {
                host = JFrame("Island verification backdrop").apply {
                    contentPane.background = Color(90, 130, 170)
                    setBounds(80, 80, 760, 650)
                    isVisible = true
                }
                cleanup += { host.dispose() }
                island = IslandWindow(timeline, settings, object : PlaybackActions {
                    override fun previous() = Unit
                    override fun toggle() = Unit
                    override fun next() = Unit
                }, { errors += it }, { frames.incrementAndGet(); floatArrayOf(.2f, .4f, .6f, .8f) })
                cleanup += { island.close() }
                owner = Window.getWindows().filterIsInstance<JFrame>()
                    .single { it.name == ApplicationIdentity.NAME }
            }
            Thread.sleep(700)
            val islandImage = capture("island", owner)
            check(islandImage.getRGB(owner.width / 2, owner.height - 5) ushr 24 == 0) { "Opaque pixels outside island" }
            edt { check(owner.shape == null) { "Visible shape must remain fixed during animation" } }
            for (state in listOf(JFrame.NORMAL, JFrame.ICONIFIED)) {
                edt { host.extendedState = state }
                Thread.sleep(400)
                val before = frames.get()
                Thread.sleep(1000)
                val count = frames.get() - before
                println("Host state $state: $count frames/s")
                check(count >= 20) { "Background cadence dropped: $count" }
            }
            edt { host.extendedState = JFrame.NORMAL }
            check(errors.isEmpty()) { errors.joinToString() }
            println("Desktop checks passed; screenshots: ${output.absolutePath}")
        } finally {
            edt {
                cleanup.asReversed().forEach { it() }
            }
        }
    }
}
