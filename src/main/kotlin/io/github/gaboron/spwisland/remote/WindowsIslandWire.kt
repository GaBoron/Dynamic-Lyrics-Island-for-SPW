// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.remote

import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import io.github.gaboron.spwisland.core.IslandSettings
import io.github.gaboron.spwisland.core.LyricLine
import io.github.gaboron.spwisland.core.PlaybackSnapshot
import io.github.gaboron.spwisland.core.Track
import io.github.gaboron.spwisland.core.TrackMetadata
import kotlin.math.abs

/** Version 1 of the private, newline-delimited state/control protocol. */
internal class WindowsIslandWire {
    private val gson = GsonBuilder().serializeNulls().create()
    private var initialized = false
    private var track: Track? = null
    private var line: LyricLine? = null
    private var lyrics: List<LyricLine> = emptyList()
    private var metadata: TrackMetadata = TrackMetadata()
    private var settings: IslandSettings? = null
    private var playing = false
    private var status = ""
    private var rate = 1.0
    private var sentAt = 0L
    private var sentPosition = 0L

    fun state(snapshot: PlaybackSnapshot, current: IslandSettings, now: Long = System.nanoTime()): String? {
        val trackChanged = !initialized || track != snapshot.track
        val lineChanged = !initialized || line != snapshot.line
        val lyricsChanged = !initialized || lyrics != snapshot.lyrics
        val metadataChanged = !initialized || metadata != snapshot.metadata
        val settingsChanged = !initialized || settings != current
        val playbackChanged = !initialized || playing != snapshot.playing ||
            status != snapshot.status.name || rate != snapshot.playbackRate
        val elapsedMs = (now - sentAt) / 1_000_000.0
        val expectedPosition = sentPosition + if (playing) (elapsedMs * rate).toLong() else 0
        val clockChanged = abs(snapshot.positionMs - expectedPosition) > 500 ||
            (snapshot.playing && elapsedMs >= 1000)
        if (!(trackChanged || lineChanged || lyricsChanged || metadataChanged ||
                    settingsChanged || playbackChanged || clockChanged)) return null

        val message = linkedMapOf<String, Any?>(
            "type" to "state",
            "positionMs" to snapshot.positionMs,
            "playing" to snapshot.playing,
            "status" to snapshot.status.name,
            "playbackRate" to snapshot.playbackRate
        )
        if (trackChanged) message["track"] = snapshot.track
        if (lineChanged) message["line"] = snapshot.line
        if (lyricsChanged) message["lyrics"] = snapshot.lyrics
        // Artwork belongs to the later visual channel; do not JSON-encode its entire pixel array.
        if (metadataChanged) message["metadata"] = mapOf(
            "durationMs" to snapshot.metadata.durationMs,
            "coverRgb" to snapshot.metadata.coverRgb
        )
        if (settingsChanged) message["settings"] = current
        track = snapshot.track
        line = snapshot.line
        lyrics = snapshot.lyrics
        metadata = snapshot.metadata
        settings = current
        playing = snapshot.playing
        status = snapshot.status.name
        rate = snapshot.playbackRate
        sentAt = now
        sentPosition = snapshot.positionMs
        initialized = true
        return gson.toJson(message)
    }

    fun command(line: String): IslandCommand? = runCatching {
        val message = JsonParser.parseString(line).asJsonObject
        if (message.get("type")?.asString != "command") return null
        val action = message.get("action")?.asString ?: return null
        when (action) {
            "previous", "toggle", "next" -> IslandCommand(action)
            "seek" -> IslandCommand(action, listOf(message.get("positionMs").asLong.coerceAtLeast(0).toString()))
            else -> null
        }
    }.getOrNull()

    fun ready(line: String): Boolean = runCatching {
        val message = JsonParser.parseString(line).asJsonObject
        message.get("type")?.asString == "ready" && message.get("protocol")?.asInt == 1
    }.getOrDefault(false)
}
