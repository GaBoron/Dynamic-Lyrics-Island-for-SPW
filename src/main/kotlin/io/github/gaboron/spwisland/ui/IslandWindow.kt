// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.core.*
import io.github.gaboron.spwisland.platform.WindowsOverlay
import java.awt.*
import java.awt.event.*
import javax.swing.*
import kotlin.math.abs
import kotlin.math.roundToInt

/** Owns only window lifecycle, placement and presentation animation. Must live on the EDT. */
class IslandWindow(private val timeline: PlaybackTimeline, private val store: SettingsStore,
                   actions: PlaybackActions, private val report: (Throwable) -> Unit,
                   private val spectrum: () -> FloatArray = { FloatArray(4) }) : AutoCloseable {
    private val window = JWindow().apply {
        name = "Dynamic Lyrics Island for SPW"
        type = Window.Type.UTILITY; isAlwaysOnTop = true
        focusableWindowState = false; isAutoRequestFocus = false
        background = Color(0, 0, 0, 0)
    }
    private val panel = IslandPanel(actions)
    private val surface = IslandSurface(panel)
    private val native = WindowsOverlay()
    private val menu = IslandMenu(store, report)
    private var settings = store.read()
    private var nativeAvailable = true
    private var clickThroughApplied: Boolean? = null
    private var closed = false
    private var fullscreen = false
    private var nextScreenCheck = 0L
    private var nextTopmostCheck = 0L
    private var topmostAvailable = true
    private var lastFrame = System.nanoTime()
    private var lastLine: LyricLine? = null
    private var previousSnapshot: PlaybackSnapshot? = null
    private var width = 280.0
    private var height = 58.0
    private var expansion = 0.0
    private var hoverWidth = 0

    private var canvasWidth = 0
    private var canvasHeight = 0
    private var anchor: Point? = null
    private var press: Point? = null
    private var dragOrigin: Point? = null
    private var dragging = false
    private val timer = Timer(16) { tick() }

    init {
        check(SwingUtilities.isEventDispatchThread())
        window.contentPane = surface
        window.setSize(width.toInt(), height.toInt())
        panel.addMouseListener(object : MouseAdapter() {
            override fun mousePressed(e: MouseEvent) {
                if (e.isPopupTrigger) menu.popup(panel, e.x, e.y)
                if (SwingUtilities.isLeftMouseButton(e) && !settings.clickThrough) {
                    press = e.locationOnScreen; dragOrigin = Point(window.x + panel.x + panel.width / 2, window.y + panel.y)
                }
            }
            override fun mouseReleased(e: MouseEvent) {
                if (e.isPopupTrigger) menu.popup(panel, e.x, e.y)
                if (dragging) {
                    val screen = window.graphicsConfiguration.device.iDstring
                    try { store.savePosition(screen, window.x + panel.x + panel.width / 2, window.y + panel.y) } catch (error: Exception) { report(error) }
                }
                dragging = false; press = null; dragOrigin = null; anchor = null
            }
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2 && SwingUtilities.isLeftMouseButton(e)) actions.toggle()
            }
        })
        panel.addMouseMotionListener(object : MouseMotionAdapter() {
            override fun mouseDragged(e: MouseEvent) {
                val start = press ?: return
                val origin = dragOrigin ?: return
                val current = e.locationOnScreen
                if (start.distance(current) < 4 && !dragging) return
                dragging = true
                anchor = Point(origin.x + current.x - start.x, origin.y + current.y - start.y)
            }
        })
        // Allocate a native peer while hidden, so full-screen checks also work before first show.
        window.addNotify()
        try { menu.installTray() } catch (error: Exception) { report(error) }
        tick(); timer.start()
    }
    fun reload() {
        if (closed) return
        settings = store.read(); anchor = null; nextScreenCheck = 0
        tick()
    }
    fun about() = menu.about()

    private fun tick() {
        if (closed) return
        val now = System.nanoTime()
        val dt = ((now - lastFrame) / 1_000_000_000.0).coerceIn(0.0, .1)
        lastFrame = now
        val snap = timeline.snapshot()
        panel.settings = settings; panel.snapshot = snap
        panel.updateSpectrum(if (snap.playing) spectrum() else FloatArray(4), dt)

        val devices = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices
        val draggedScreen = anchor?.let { a -> devices.find { it.defaultConfiguration.bounds.contains(a) } }
        val device = draggedScreen ?: devices.find { it.iDstring == settings.screen } ?:
            GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice
        val screen = device.defaultConfiguration.bounds
        val center = anchor?.x ?: settings.centerX?.takeIf { settings.screen == device.iDstring } ?: (screen.x + screen.width / 2)
        val top = IslandGeometry.top(screen, settings.notch, anchor?.y,
            settings.top?.takeIf { settings.screen == device.iDstring })
        val mouse = MouseInfo.getPointerInfo()?.location
        val overIsland = mouse != null && IslandGeometry.silhouette(panel.width, panel.height, settings.notch,
            settings.cornerRadius)
            .contains((mouse.x - window.x - panel.x).toDouble(), (mouse.y - window.y - panel.y).toDouble())
        panel.expanded = !settings.clickThrough && (dragging || panel.progress.dragging || (window.isVisible && overIsland))
        if (snap.line != lastLine || snap.track != previousSnapshot?.track) {
            panel.outgoing = previousSnapshot?.takeIf { it.track == snap.track }
            panel.transition = 0.0; lastLine = snap.line
        }
        previousSnapshot = snap
        panel.transition = (panel.transition + dt / .65).coerceAtMost(1.0)
        val desired = panel.desiredSize(screen.width)
        if (panel.expanded) {
            hoverWidth = maxOf(hoverWidth, width.roundToInt(), desired.width)
            desired.width = hoverWidth.coerceAtMost(minOf(settings.maxWidth, screen.width))
        } else hoverWidth = 0
        val factor = if (settings.reducedMotion) 1.0 else 1 - kotlin.math.exp(-dt * 15)
        expansion += ((if (panel.expanded) 1.0 else 0.0) - expansion) * factor
        panel.expansion = expansion
        width += (desired.width - width) * factor
        height += (desired.height - height) * factor
        if (abs(width - desired.width) < .5) width = desired.width.toDouble()
        if (abs(height - desired.height) < .5) height = desired.height.toDouble()
        val islandBounds = IslandGeometry.clamp(screen, center, top, width.roundToInt(), height.roundToInt())
        canvasWidth = maxOf(canvasWidth, settings.maxWidth, islandBounds.width)
        canvasHeight = maxOf(canvasHeight, islandBounds.height, desired.height,
            settings.fontSize * 4 + IslandTextBlock.EXPANDED_HEIGHT + 60)
        val bounds = IslandGeometry.clamp(screen, center, top, canvasWidth, canvasHeight)
        val resized = window.width != bounds.width || window.height != bounds.height
        if (window.bounds != bounds) window.bounds = bounds
        if (resized) window.validate()
        surface.setSize(bounds.width, bounds.height)
        panel.setBounds(islandBounds.x - bounds.x, islandBounds.y - bounds.y, islandBounds.width, islandBounds.height)
        panel.doLayout()
        if (nativeAvailable && now >= nextScreenCheck) {
            try { fullscreen = settings.hideFullscreen && native.foregroundIsFullscreen(window) }
            catch (error: Exception) { nativeAvailable = false; fullscreen = false; report(error) }
            nextScreenCheck = now + 400_000_000
        }
        val visible = settings.enabled && (!settings.hidePaused || snap.playing) && (!settings.hideFullscreen || !fullscreen)
        if (window.isVisible != visible) {
            window.isVisible = visible
            nextTopmostCheck = 0
        }
        if (visible && topmostAvailable && now >= nextTopmostCheck) {
            try { native.reinforceTopmost(window) }
            catch (error: Exception) { topmostAvailable = false; report(error) }
            nextTopmostCheck = now + 100_000_000
        }
        if (nativeAvailable && clickThroughApplied != settings.clickThrough) {
            try { native.clickThrough(window, settings.clickThrough); clickThroughApplied = settings.clickThrough }
            catch (error: Exception) { nativeAvailable = false; report(error) }
        }
        if (visible) {
            surface.repaint()
        }
        timer.delay = if (!visible) 200 else if (settings.reducedMotion || !snap.playing && panel.transition >= 1 && width == desired.width.toDouble() && height == desired.height.toDouble()) 50 else 16
    }
    override fun close() {
        if (closed) return
        closed = true; timer.stop(); menu.close(); window.dispose()
    }
}
