// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.remote.LinuxIslandProcess
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** Reproduces a blocked HOST EDT, which a plain minimized JFrame does not reproduce. */
object LinuxProcessCheck {
    @JvmStatic fun main(args: Array<String>) {
        val timeline = PlaybackTimeline().apply { playingChanged(true) }
        val failures = CopyOnWriteArrayList<Throwable>()
        val settings = object : SettingsStore {
            override fun read() = IslandSettings(leadingContent = LeadingContent.COVER)
            override fun set(key: String, value: Any) = Unit
            override fun savePosition(screen: String, x: Int, y: Int, anchor: IslandAnchor) = Unit
            override fun resetPosition() = Unit
        }
        val actions = object : PlaybackActions {
            override fun previous() = Unit
            override fun toggle() = Unit
            override fun next() = Unit
        }
        val child = LinuxIslandProcess(timeline, settings, actions, failures::add,
            System.getProperty("spwisland.test.runtime", System.getProperty("java.home")))
        val release = CountDownLatch(1)
        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
            while (child.presentedFrames < 10 && failures.isEmpty() && System.nanoTime() < deadline) Thread.sleep(100)
            check(failures.isEmpty()) { failures.joinToString() }
            check(child.presentedFrames >= 10) { "Child UI failed to start" }
            val blocked = CountDownLatch(1)
            SwingUtilities.invokeLater { blocked.countDown(); release.await(10, TimeUnit.SECONDS) }
            check(blocked.await(2, TimeUnit.SECONDS))
            val before = child.presentedFrames
            Thread.sleep(3200)
            val presented = child.presentedFrames - before
            println("Host EDT blocked for 3.2s; child presented $presented complete frames")
            check(presented >= 40) { "Child rendering still depends on host EDT: $presented" }
            check(failures.isEmpty()) { failures.joinToString() }
        } finally { release.countDown(); child.close() }
        println("Linux UI process isolation check passed")
    }
}
