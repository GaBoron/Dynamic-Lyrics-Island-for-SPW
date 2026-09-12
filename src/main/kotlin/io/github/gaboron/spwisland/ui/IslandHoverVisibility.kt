// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.Point
import java.awt.Shape

/** Keeps the entry silhouette stable while hidden, independently of animated rendering and lyrics. */
class IslandHoverVisibility {
    private var entryRegion: Shape? = null
    private var progress = 1.0
    var animating = false
        private set

    fun update(enabled: Boolean, mouse: Point?, region: Shape, dt: Double, instant: Boolean): Double {
        if (!enabled) entryRegion = null
        else if (mouse != null) {
            if (entryRegion?.contains(mouse) == false) entryRegion = null
            if (entryRegion == null && region.contains(mouse)) entryRegion = region
        }
        val target = if (enabled && entryRegion != null) 0.0 else 1.0
        progress = if (instant) target else if (target < progress) {
            (progress - dt / .24).coerceAtLeast(target)
        } else {
            (progress + dt / .24).coerceAtMost(target)
        }
        animating = progress != target
        return progress * progress * (3 - 2 * progress)
    }
}
