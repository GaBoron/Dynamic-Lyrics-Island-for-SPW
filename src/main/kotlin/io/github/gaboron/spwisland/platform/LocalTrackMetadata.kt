// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.platform

import io.github.gaboron.spwisland.core.TrackMetadata
import org.jaudiotagger.audio.AudioFileIO
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.File
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.math.ceil
import kotlin.math.roundToLong

/** Read-only local tags. No audio decoding, writes, remote artwork, or host-private APIs. */
object LocalTrackMetadata {
    fun read(path: String): TrackMetadata {
        val file = File(path)
        if (!file.isFile) return TrackMetadata()
        val audio = runCatching { AudioFileIO.read(file) }.getOrNull()
        val duration = audio?.audioHeader?.preciseTrackLength
            ?.takeIf { it.isFinite() && it > 0 }?.let { (it * 1000).roundToLong() } ?: 0
        val embedded = runCatching { audio?.tag?.firstArtwork?.binaryData }.getOrNull()
        val cover = embedded?.takeIf { it.size <= 16 * 1024 * 1024 }?.let { bytes ->
            runCatching { ByteArrayInputStream(bytes).use { decode(it) } }.getOrNull()
        } ?: sequenceOf("cover.jpg", "cover.png", "folder.jpg", "folder.png").mapNotNull { name ->
            val image = File(file.parentFile, name)
            if (!image.isFile || image.length() > 16 * 1024 * 1024) null
            else runCatching { image.inputStream().use { decode(it) } }.getOrNull()
        }.firstOrNull()
        return TrackMetadata(duration, cover?.let(::dominantColor))
    }

    private fun decode(input: java.io.InputStream): BufferedImage? = MemoryCacheImageInputStream(input).use { stream ->
        val readers = ImageIO.getImageReaders(stream)
        if (!readers.hasNext()) return null
        val reader = readers.next()
        try {
            reader.input = stream
            val width = reader.getWidth(0); val height = reader.getHeight(0)
            if (width <= 0 || height <= 0 || width.toLong() * height > 100_000_000) return null
            val step = ceil(maxOf(width, height) / 64.0).toInt().coerceAtLeast(1)
            val params = reader.defaultReadParam.apply { setSourceSubsampling(step, step, 0, 0) }
            reader.read(0, params)
        } finally { reader.dispose() }
    }

    /** Quantized dominant hue avoids averaging complementary cover colors into gray. */
    internal fun dominantColor(image: BufferedImage): Int? {
        val counts = IntArray(4096)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val c = Color(image.getRGB(x, y), true)
            if (c.alpha < 128) continue
            val key = (c.red shr 4 shl 8) or (c.green shr 4 shl 4) or (c.blue shr 4)
            counts[key]++
        }
        val key = counts.indices.maxByOrNull { counts[it] } ?: return null
        if (counts[key] == 0) return null
        return Color((key shr 8) * 16 + 8, ((key shr 4) and 15) * 16 + 8, (key and 15) * 16 + 8).rgb
    }
}
