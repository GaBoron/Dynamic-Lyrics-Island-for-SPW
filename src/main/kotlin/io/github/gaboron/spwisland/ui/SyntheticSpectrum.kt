// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import kotlin.math.sin

/** Cheap deterministic fallback animation that requires no audio capture process. */
object SyntheticSpectrum {
    fun levels(positionMs: Long): FloatArray = FloatArray(4) { band ->
        val slow = sin(positionMs / 310.0 + band * 1.7)
        val pulse = sin(positionMs / 127.0 + band * .9)
        (.28 + slow * .13 + pulse * .07).toFloat().coerceIn(.08f, .52f)
    }
}
