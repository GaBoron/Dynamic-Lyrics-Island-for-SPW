// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.remote

import io.github.gaboron.spwisland.core.CoverArtwork
import java.util.Base64

/** Sends one small, center-cropped cover frame when artwork changes. */
internal object NativeCoverFrame {
    private const val SIZE = 64

    fun encode(artwork: CoverArtwork?): String? {
        if (artwork == null || artwork.width <= 0 || artwork.height <= 0 ||
            artwork.argb.size.toLong() < artwork.width.toLong() * artwork.height) return null
        val pixels = ByteArray(SIZE * SIZE * 4)
        val sourceSize = minOf(artwork.width, artwork.height)
        val left = (artwork.width - sourceSize) / 2
        val top = (artwork.height - sourceSize) / 2
        for (y in 0 until SIZE) for (x in 0 until SIZE) {
            val sourceX = left + ((x.toLong() * sourceSize + sourceSize / 2) / SIZE).toInt()
            val sourceY = top + ((y.toLong() * sourceSize + sourceSize / 2) / SIZE).toInt()
            val argb = artwork.argb[sourceY * artwork.width + sourceX]
            val alpha = argb ushr 24
            val offset = (y * SIZE + x) * 4
            pixels[offset] = ((argb and 0xff) * alpha / 255).toByte()
            pixels[offset + 1] = (((argb ushr 8) and 0xff) * alpha / 255).toByte()
            pixels[offset + 2] = (((argb ushr 16) and 0xff) * alpha / 255).toByte()
            pixels[offset + 3] = alpha.toByte()
        }
        return Base64.getEncoder().encodeToString(pixels)
    }
}
