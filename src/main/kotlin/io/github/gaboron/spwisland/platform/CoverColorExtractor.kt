// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import java.awt.Color
import java.awt.image.BufferedImage

/** Selects the largest chromatic color family, avoiding black and white unless no alternative exists. */
object CoverColorExtractor {
    private const val HUE_FAMILIES = 12

    fun dominant(image: BufferedImage): Int? {
        val families = Array(HUE_FAMILIES) { Accumulator() }
        val neutral = Accumulator()
        val all = Accumulator()
        val hsb = FloatArray(3)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val color = Color(image.getRGB(x, y), true)
            if (color.alpha < 128) continue
            all.add(color)
            Color.RGBtoHSB(color.red, color.green, color.blue, hsb)
            val saturation = hsb[1]
            val brightness = hsb[2]
            val nearBlack = brightness < .08f
            val nearWhite = brightness > .92f && saturation < .12f
            if (!nearBlack && !nearWhite) neutral.add(color)
            if (saturation >= .12f && !nearBlack && !nearWhite) {
                // Offset the buckets so red wraps into one family instead of splitting at hue 0/1.
                val family = ((hsb[0] * HUE_FAMILIES + .5f).toInt() % HUE_FAMILIES)
                families[family].add(color)
            }
        }
        val dominant = families.maxByOrNull { it.count }?.takeIf { it.count > 0 }
        return (dominant ?: neutral.takeIf { it.count > 0 } ?: all.takeIf { it.count > 0 })?.rgb()
    }

    private class Accumulator {
        var count = 0
        private var red = 0L
        private var green = 0L
        private var blue = 0L
        fun add(color: Color) {
            count++
            red += color.red
            green += color.green
            blue += color.blue
        }
        fun rgb() = Color((red / count).toInt(), (green / count).toInt(), (blue / count).toInt()).rgb
    }
}
