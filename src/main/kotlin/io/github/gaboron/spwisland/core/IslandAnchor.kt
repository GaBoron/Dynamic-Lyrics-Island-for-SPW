// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.core

enum class HorizontalAnchor { LEFT, CENTER, RIGHT }
enum class VerticalAnchor { TOP, CENTER, BOTTOM }

/** One of the nine resize pivots selected from the island's screen region. */
data class IslandAnchor(
    val horizontal: HorizontalAnchor,
    val vertical: VerticalAnchor
) {
    val storageName: String = "${vertical.name.lowercase()}_${horizontal.name.lowercase()}"

    companion object {
        val TOP_CENTER = IslandAnchor(HorizontalAnchor.CENTER, VerticalAnchor.TOP)

        fun fromStorage(value: String): IslandAnchor? {
            val parts = value.split('_', limit = 2)
            if (parts.size != 2) return null
            val vertical = VerticalAnchor.entries.firstOrNull { it.name.equals(parts[0], true) } ?: return null
            val horizontal = HorizontalAnchor.entries.firstOrNull { it.name.equals(parts[1], true) } ?: return null
            return IslandAnchor(horizontal, vertical)
        }
    }
}
