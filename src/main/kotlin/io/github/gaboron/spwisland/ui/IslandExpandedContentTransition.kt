// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.AlphaComposite
import java.awt.Graphics
import java.awt.Graphics2D

/** Applies one reversible transition to every control revealed by island expansion. */
internal object IslandExpandedContentTransition {
    fun visible(expansion: Double): Boolean = opacity(expansion) > .001f

    fun layer(graphics: Graphics, expansion: Double): Graphics2D? {
        val opacity = opacity(expansion)
        if (opacity <= 0f) return null
        return (graphics.create() as Graphics2D).apply {
            composite = AlphaComposite.SrcOver.derive(opacity)
            translate(0.0, 7.0 * (1.0 - opacity))
        }
    }

    private fun opacity(expansion: Double): Float {
        val progress = ((expansion.coerceIn(0.0, 1.0) - .08) / .92).coerceIn(0.0, 1.0)
        return (progress * progress * (3.0 - 2.0 * progress)).toFloat()
    }
}
