// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

internal enum class PopupMenuKind { TITLE, NOTE, SEPARATOR, TOGGLE, ACTION }

internal data class PopupMenuEntry(
    val kind: PopupMenuKind,
    val id: Int = 0,
    val selected: Boolean = false,
    val label: String = ""
)
