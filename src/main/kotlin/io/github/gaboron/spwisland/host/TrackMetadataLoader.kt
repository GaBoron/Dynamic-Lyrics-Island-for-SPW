// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.host

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.platform.LocalTrackMetadata
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Future

/** One bounded worker, latest request wins; generation checks reject late results after A/B/A switches. */
class TrackMetadataLoader(private val timeline: PlaybackTimeline,
                          private val read: (String) -> TrackMetadata = LocalTrackMetadata::read) : AutoCloseable {
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue(1),
        { task -> Thread(task, "SPW Island local metadata").apply { isDaemon = true } },
        ThreadPoolExecutor.DiscardOldestPolicy())
    private var pending: Future<*>? = null
    private var token: Long? = null
    private var closed = false
    @Synchronized fun load(track: Track) {
        if (closed) return
        val generation = timeline.trackChanged(track)
        if (token == generation) return
        token = generation
        pending?.cancel(true)
        pending = executor.submit {
            val value = runCatching { read(track.path) }.getOrDefault(TrackMetadata())
            synchronized(this) { if (!closed) timeline.metadataLoaded(generation, value) }
        }
    }
    @Synchronized override fun close() {
        closed = true; pending?.cancel(true); executor.shutdownNow()
    }
}
