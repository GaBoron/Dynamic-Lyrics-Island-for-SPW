// SPDX-License-Identifier: GPL-3.0-only
@file:OptIn(com.xuncorp.spw.workshop.api.UnstableSpwWorkshopApi::class)
package io.github.gaboron.spwisland.host

import com.xuncorp.spw.workshop.api.WorkshopApi
import io.github.gaboron.spwisland.core.LyricLine
import io.github.gaboron.spwisland.core.Word
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.util.Collections
import java.util.IdentityHashMap

/** Copies SPW's private lyrics document without retaining or modifying host-owned objects. */
internal class HostLyricsDocumentProbe {
    @Volatile private var service: Any? = null

    fun read(): List<LyricLine>? {
        return runCatching {
            val playbackService = service ?: findService()?.also { service = it } ?: return null
            val monitor = findObject(listOf(playbackService), 2,
                { it.javaClass.name == PLAYBACK_MONITOR }, ::mayTraverse) ?: return null
            val rawDocument = monitor.call("getLyricsDocument") ?: return null
            val document = rawDocument.call("getValue") ?: rawDocument
            val rawLines = document.call("getLyricsLines") ?: document.field("lyricsLines") ?: return null
            (rawLines as? Iterable<*>)?.mapNotNull(::mapLine)?.sortedBy { it.startMs }
        }.getOrNull()
    }

    private fun mapLine(value: Any?): LyricLine? {
        value ?: return null
        val start = value.number("getStartTime") ?: return null
        val end = value.number("getEndTime") ?: return null
        val text = value.call("getPureMainText") as? String ?: return null
        val translation = value.call("getPureSubText") as? String
        val words = (value.call("getLyricsCells") as? Iterable<*>)?.mapNotNull { cell ->
            cell ?: return@mapNotNull null
            val cellStart = cell.number("getStartTime") ?: return@mapNotNull null
            val cellEnd = cell.number("getEndTime") ?: return@mapNotNull null
            val cellText = cell.call("getText") as? String ?: return@mapNotNull null
            Word(cellStart, cellEnd, cellText)
        }.orEmpty()
        return LyricLine(start, end, text, translation, words)
    }

    private fun findService(): Any? {
        val roots = runCatching { listOf(WorkshopApi.instance, WorkshopApi.playback) }.getOrDefault(emptyList())
        for (loader in roots.map { it.javaClass.classLoader }.distinct()) {
            val found = runCatching {
                val controller = Class.forName(PLAYBACK_CONTROLLER, false, loader)
                controller.declaredFields.firstOrNull {
                    Modifier.isStatic(it.modifiers) && it.type.name == PLAYBACK_SERVICE
                }?.read(null)
            }.getOrNull()
            if (found != null) return found
        }
        return findObject(roots, 5, { it.javaClass.name == PLAYBACK_SERVICE }, ::mayTraverse)
    }

    private fun findObject(roots: List<Any>, maxDepth: Int, matches: (Any) -> Boolean,
                           traverse: (Class<*>) -> Boolean): Any? {
        val seen = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
        var level = roots
        repeat(maxDepth + 1) { depth ->
            val next = mutableListOf<Any>()
            for (value in level) {
                if (!seen.add(value)) continue
                if (matches(value)) return value
                if (depth == maxDepth || !traverse(value.javaClass)) continue
                allFields(value.javaClass).filterNot { Modifier.isStatic(it.modifiers) }.forEach { field ->
                    field.read(value)?.takeIf { traverse(it.javaClass) }?.let(next::add)
                }
            }
            level = next
        }
        return null
    }

    private fun Any.call(name: String): Any? = allMethods(javaClass)
        .firstOrNull { it.name == name && it.parameterCount == 0 }
        ?.let { method -> runCatching { method.trySetAccessible(); method.invoke(this) }.getOrNull() }

    private fun Any.number(name: String): Long? = (call(name) as? Number)?.toLong()
    private fun Any.field(name: String): Any? = allFields(javaClass).firstOrNull { it.name == name }?.read(this)
    private fun Field.read(owner: Any?): Any? = runCatching { trySetAccessible(); get(owner) }.getOrNull()
    private fun allMethods(type: Class<*>): Sequence<Method> = sequence {
        yieldAll(type.methods.asSequence())
        yieldAll(generateSequence(type) { it.superclass }.flatMap { it.declaredMethods.asSequence() })
    }.distinctBy { method -> method.name to method.parameterTypes.toList() }
    private fun allFields(type: Class<*>): Sequence<Field> = generateSequence(type) { it.superclass }
        .flatMap { it.declaredFields.asSequence() }
    private fun mayTraverse(type: Class<*>): Boolean = type.name == PLAYBACK_SERVICE ||
        type.name.startsWith("com.xuncorp.voxzen") || type.name.startsWith("com.xuncorp.spw")

    companion object {
        private const val PLAYBACK_SERVICE = "com.xuncorp.voxzen.service.PlaybackService"
        private const val PLAYBACK_CONTROLLER = "com.xuncorp.voxzen.service.PlaybackController"
        private const val PLAYBACK_MONITOR = "com.xuncorp.voxzen.service.PlaybackMonitor"
    }
}
