// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.Desktop
import java.net.URI
import java.util.Properties

object ProjectLinks {
    val source: URI by lazy {
        val properties = Properties()
        checkNotNull(javaClass.getResourceAsStream("/project.properties")).use { properties.load(it) }
        URI(properties.getProperty("source.url"))
    }
    fun openSource() {
        check(Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
            "无法打开浏览器，请访问：$source"
        }
        Desktop.getDesktop().browse(source)
    }
}
