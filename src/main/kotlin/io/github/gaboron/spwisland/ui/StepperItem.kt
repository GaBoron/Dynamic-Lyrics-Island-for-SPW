// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.platform.KeyboardCapture
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Dimension
import java.awt.FontMetrics
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.KeyEventDispatcher
import java.awt.KeyboardFocusManager
import java.awt.RenderingHints
import java.awt.event.HierarchyEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JMenu
import javax.swing.JPopupMenu
import javax.swing.MenuElement
import javax.swing.MenuSelectionManager
import javax.swing.SwingUtilities
import javax.swing.Timer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A single-row inline stepper: `设置名称  范围   − □ +`.
 *
 * The box shows the current value and turns into an inline editor when clicked: the
 * whole number appears selected, ready to be overtyped. The − / + slots step once per
 * click and keep stepping while held down, accelerating on wide ranges.
 *
 * Keys are captured with a desktop-wide low-level hook ([KeyboardCapture]) rather than
 * component focus. The menu popup is a separate window the host never activates, and the
 * island itself cannot take the focus, so this process often never receives the
 * keystrokes at all — a child `JTextField` (or a Swing key listener) would silently drop
 * every character. A registered [KeyEventDispatcher] covers the case where the keys do
 * reach this process but the native hook could not be armed. Keys the editor uses are
 * swallowed so they never reach the window behind the menu.
 *
 * It is a plain component (not a JMenuItem), so clicking it never dismisses the menu.
 */
internal class StepperItem(
    private val label: String,
    private val rangeText: String,
    private val min: Int,
    private val max: Int,
    private val step: Int,
    private val getValue: () -> Int,
    private val commit: (Int) -> Unit
) : JComponent() {
    private var hovered = false

    /** Digits being edited; non-null while the inline editor is open. Read by the hook thread. */
    @Volatile private var buffer: String? = null
    private var caretIndex = 0
    private var anchorIndex = 0
    private var caretOn = true
    private val caretBlink = Timer(CARET_BLINK_MS) {
        caretOn = !caretOn
        if (buffer != null) repaint()
    }

    /** Held-down − / + stepping. */
    private var repeatDirection = 0
    private var repeatTicks = 0
    private val repeatTimer = Timer(REPEAT_INTERVAL_MS) { repeatStep() }.apply {
        isRepeats = true
        initialDelay = REPEAT_DELAY_MS
    }

    private val capture = KeyboardCapture(::onNativeKeyDown)

    /** Value-box width for the current row height; re-derived by [useRowHeight]. */
    private var valueW = 0

    companion object {
        private const val MINUS_W = 26
        private const val PLUS_W = 26
        private const val DEFAULT_VALUE_W = 54
        private const val MAX_VALUE_W = 78
        private const val GAP = 3
        private const val RANGE_GAP = Windows11PopupStyle.rangeGap
        private const val CARET_BLINK_MS = 530
        private const val MAX_INPUT_LENGTH = 8
        private const val MIN_RANGE_FONT_SIZE = 9f
        private const val REPEAT_DELAY_MS = 400
        private const val REPEAT_INTERVAL_MS = 110
        private const val ROW_W = 270

        /** Win32 `VK_OEM_MINUS`; AWT reports the same physical key as [KeyEvent.VK_MINUS]. */
        private const val WIN_VK_OEM_MINUS = 0xBD

        /** Kept in step with the menu item rows so every label starts on the same edge. */
        private val LEFT_PAD = Windows11PopupStyle.rowStart
        private val RIGHT_PAD = Windows11PopupStyle.rowEnd

        /** The item whose inline editor owns the keyboard, and the router that feeds it. */
        private var activeItem: StepperItem? = null
        private val keyRouter = KeyEventDispatcher { event -> activeItem?.onKey(event) ?: false }
    }

    init {
        isOpaque = false
        isFocusable = false
        caretBlink.isRepeats = true
        useRowHeight(Windows11PopupStyle.defaultRowHeight)
        addMouseMotionListener(object : MouseAdapter() {
            override fun mouseMoved(e: MouseEvent) {
                syncMenuSelection()
                if (!hovered) { hovered = true; repaint() }
            }
        })
        addMouseListener(object : MouseAdapter() {
            override fun mouseEntered(e: MouseEvent) = syncMenuSelection()

            override fun mouseExited(e: MouseEvent) {
                stopRepeat()
                if (hovered) { hovered = false; repaint() }
            }

            override fun mousePressed(e: MouseEvent) {
                when (e.x) {
                    in minusX()..minusX() + MINUS_W -> beginStep(-1)
                    in plusX()..plusX() + PLUS_W -> beginStep(1)
                    in valueX()..valueX() + valueW -> onValueClick(e)
                }
            }

            override fun mouseReleased(e: MouseEvent) = stopRepeat()
        })
        // The menu closing hides this component; drop the editor and the held button so
        // no global hook or key router stays behind, and keep what the user typed.
        addHierarchyListener { event ->
            if (event.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong() != 0L && !isShowing) {
                stopRepeat()
                closeEditor(commitChange = true)
            }
        }
    }

    /**
     * Adopts the menu's adaptive row height (see [Windows11PopupStyle.rowHeightFor]) so the
     * stepper shrinks together with its sibling rows instead of clipping them.
     */
    internal fun useRowHeight(height: Int) {
        font = Windows11PopupStyle.fontFor(height)
        val fm = getFontMetrics(font)
        valueW = (maxOf(fm.stringWidth("$min"), fm.stringWidth("$max")) + 16)
            .coerceIn(DEFAULT_VALUE_W, MAX_VALUE_W)
        preferredSize = Dimension(ROW_W, height)
        minimumSize = preferredSize
        maximumSize = Dimension(Int.MAX_VALUE, height)
        revalidate()
        repaint()
    }



    private fun minusX() = valueX() - GAP - MINUS_W
    private fun valueX() = plusX() - GAP - valueW
    private fun plusX() = width - RIGHT_PAD - PLUS_W

    /**
     * A stepper is not a `MenuElement`, so hovering one leaves Swing's selection untouched
     * and a submenu opened on a neighbouring row (字体 / 垂直锚点) would stay on screen.
     * Point the selection at the menu chain that owns this row, which folds any other
     * submenu away — exactly what hovering a plain menu item does.
     */
    private fun syncMenuSelection() {
        val popup = SwingUtilities.getAncestorOfClass(JPopupMenu::class.java, this) as? JPopupMenu ?: return
        val manager = MenuSelectionManager.defaultManager()
        if (manager.selectedPath.lastOrNull() === popup) return
        manager.setSelectedPath(menuPath(popup))
    }

    /** `[topPopup, …, parentMenu, popup]` — the chain that keeps this row's popup open. */
    private fun menuPath(popup: JPopupMenu): Array<MenuElement> {
        val chain = ArrayDeque<MenuElement>()
        var current: JPopupMenu? = popup
        while (current != null) {
            chain.addFirst(current)
            val menu = current.invoker as? JMenu ?: break
            chain.addFirst(menu)
            current = SwingUtilities.getAncestorOfClass(JPopupMenu::class.java, menu) as? JPopupMenu
        }
        return chain.toTypedArray()
    }

    private fun onValueClick(e: MouseEvent) {
        if (buffer == null || e.clickCount >= 2) {
            beginEdit(selectAll = true)
            return
        }
        moveCaretTo(indexAt(e.x), extendSelection = false)
    }

    private fun apply(next: Int) {
        commit(next.coerceIn(min, max))
        repaint()
    }

    // -------------------------------------------------------------- held − / +

    /** One step on press, then auto-repeat while the slot stays held. */
    private fun beginStep(direction: Int) {
        closeEditor(commitChange = true)
        apply(getValue() + direction * step)
        repeatDirection = direction
        repeatTicks = 0
        repeatTimer.restart()
    }

    private fun repeatStep() {
        val direction = repeatDirection
        if (direction == 0 || buffer != null || !isShowing) {
            stopRepeat()
            return
        }
        repeatTicks++
        val current = getValue()
        val next = (current + direction * step * repeatMultiplier(repeatTicks)).coerceIn(min, max)
        if (next == current) {
            stopRepeat()
            return
        }
        apply(next)
    }

    /** Holding accelerates so wide ranges (e.g. 歌词偏移 ±2000 ms) stay reachable. */
    private fun repeatMultiplier(ticks: Int) = when {
        ticks <= 8 -> 1
        ticks <= 20 -> 5
        else -> 25
    }

    private fun stopRepeat() {
        repeatDirection = 0
        if (repeatTimer.isRunning) repeatTimer.stop()
    }

    // ---------------------------------------------------------------- editing

    /** Opens the inline editor with the current value selected, or re-selects it. */
    private fun beginEdit(selectAll: Boolean) {
        val current = buffer
        if (current == null) {
            val initial = getValue().toString()
            buffer = initial
            caretIndex = initial.length
            anchorIndex = if (selectAll) 0 else initial.length
            attachRouter()
            capture.arm()
            caretBlink.start()
            touch()
            return
        }
        if (selectAll) {
            anchorIndex = 0
            caretIndex = current.length
            touch()
        }
    }

    /** Commits the typed value and closes the editor (also used before − / + clicks). */
    private fun closeEditor(commitChange: Boolean) {
        val current = buffer ?: return
        buffer = null
        caretBlink.stop()
        capture.close()
        detachRouter()
        if (commitChange) commitBuffer(current)
        repaint()
    }

    private fun commitBuffer(value: String) {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return
        val parsed = trimmed.toDoubleOrNull() ?: return
        if (parsed.isFinite()) apply(parsed.roundToInt())
    }

    private fun attachRouter() {
        if (activeItem === this) return
        activeItem?.closeEditor(commitChange = true)
        activeItem = this
        runCatching {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(keyRouter)
        }
    }

    private fun detachRouter() {
        if (activeItem !== this) return
        activeItem = null
        runCatching {
            KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(keyRouter)
        }
    }

    /** Keeps the caret solid for a moment after every edit, like a real text field. */
    private fun touch() {
        caretOn = true
        if (buffer != null) caretBlink.restart()
        repaint()
    }

    // ------------------------------------------------------------ key routing

    /** Hook thread: decides whether to swallow the key, then hands the work to the EDT. */
    private fun onNativeKeyDown(vkCode: Int): Boolean {
        if (buffer == null) return false
        // Win32 reports the main '-' key as VK_OEM_MINUS, AWT as VK_MINUS.
        val key = if (vkCode == WIN_VK_OEM_MINUS) KeyEvent.VK_MINUS else vkCode
        // Ctrl+A selects the whole number, like a real text field.
        if (key == KeyEvent.VK_A && capture.isControlDown()) {
            SwingUtilities.invokeLater { if (buffer != null) selectAll() }
            return true
        }
        if (isEditorKey(key)) {
            // Modifier state is read on the hook thread and captured, so the EDT sees
            // the state as of the moment the key was pressed.
            val shiftDown = capture.isShiftDown()
            SwingUtilities.invokeLater { applyEditorKey(key, shiftDown, typedDigit = true) }
            return true
        }
        // Printable characters (letters, punctuation) must not leak into whatever window
        // has the focus (the host, or another application); swallow them while editing,
        // unless a Ctrl/Alt shortcut is held.
        return isPrintingKey(key) && !capture.isControlDown() && !capture.isAltDown()
    }

    /** True for letter / punctuation virtual keys, which are not editor keys but should
     *  not reach the window behind the menu while a number is being edited. */
    private fun isPrintingKey(vkCode: Int) =
        vkCode in KeyEvent.VK_A..KeyEvent.VK_Z || vkCode in 0xBA..0xE2

    /** Swing fallback, used when the native hook could not be armed. */
    private fun onKey(event: KeyEvent): Boolean {
        if (buffer == null) return false
        return when (event.id) {
            KeyEvent.KEY_TYPED -> onKeyTyped(event)
            KeyEvent.KEY_PRESSED -> onKeyPressed(event)
            else -> false
        }
    }

    private fun onKeyTyped(event: KeyEvent): Boolean {
        if (event.isControlDown || event.isAltDown || event.isMetaDown) return false
        val typed = event.keyChar
        return when {
            typed in '0'..'9' -> { replaceSelection(typed.toString()); true }
            typed == '-' && min < 0 -> { replaceSelection("-"); true }
            // Swallow any other printable key: while the number is being edited it
            // must not reach the player window behind the menu.
            typed >= ' ' && typed != '\u007F' -> true
            else -> false
        }
    }

    private fun onKeyPressed(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.VK_A && event.isControlDown) {
            selectAll()
            return true
        }
        if (!isEditorKey(event.keyCode)) return false
        // Digits insert from their KEY_TYPED event on this path; consuming the press
        // keeps them out of the host window without inserting them twice.
        return applyEditorKey(event.keyCode, event.isShiftDown, typedDigit = false)
    }

    /** True for every key the editor acts on; those keys are swallowed. */
    private fun isEditorKey(vkCode: Int) = vkCode in KeyEvent.VK_0..KeyEvent.VK_9 ||
        vkCode in KeyEvent.VK_NUMPAD0..KeyEvent.VK_NUMPAD9 ||
        vkCode == KeyEvent.VK_MINUS ||
        vkCode == KeyEvent.VK_SUBTRACT ||
        vkCode == KeyEvent.VK_BACK_SPACE ||
        vkCode == KeyEvent.VK_DELETE ||
        vkCode == KeyEvent.VK_LEFT ||
        vkCode == KeyEvent.VK_RIGHT ||
        vkCode == KeyEvent.VK_HOME ||
        vkCode == KeyEvent.VK_END ||
        vkCode == KeyEvent.VK_UP ||
        vkCode == KeyEvent.VK_DOWN ||
        vkCode == KeyEvent.VK_ENTER ||
        vkCode == KeyEvent.VK_SPACE ||
        vkCode == KeyEvent.VK_ESCAPE

    private fun digitFor(vkCode: Int): Char? = when (vkCode) {
        in KeyEvent.VK_0..KeyEvent.VK_9 -> '0' + (vkCode - KeyEvent.VK_0)
        in KeyEvent.VK_NUMPAD0..KeyEvent.VK_NUMPAD9 -> '0' + (vkCode - KeyEvent.VK_NUMPAD0)
        else -> null
    }

    /** Performs the command for a virtual key. Runs on the EDT. */
    private fun applyEditorKey(vkCode: Int, shiftDown: Boolean, typedDigit: Boolean): Boolean {
        if (buffer == null) return false
        digitFor(vkCode)?.let { digit ->
            if (typedDigit) replaceSelection(digit.toString())
            return true
        }
        return when (vkCode) {
            KeyEvent.VK_MINUS, KeyEvent.VK_SUBTRACT -> {
                if (min < 0) replaceSelection("-")
                true
            }
            KeyEvent.VK_BACK_SPACE -> { deleteBackward(); true }
            KeyEvent.VK_DELETE -> { deleteForward(); true }
            KeyEvent.VK_LEFT -> { moveCaretBy(-1, shiftDown); true }
            KeyEvent.VK_RIGHT -> { moveCaretBy(1, shiftDown); true }
            KeyEvent.VK_HOME -> { moveCaretTo(0, shiftDown); true }
            KeyEvent.VK_END -> { moveCaretTo(buffer?.length ?: 0, shiftDown); true }
            KeyEvent.VK_UP -> { nudge(step); true }
            KeyEvent.VK_DOWN -> { nudge(-step); true }
            // Enter and Space both confirm, like a native spinner or dialog default button.
            KeyEvent.VK_ENTER, KeyEvent.VK_SPACE -> { closeEditor(commitChange = true); true }
            KeyEvent.VK_ESCAPE -> { closeEditor(commitChange = false); true }
            else -> false
        }
    }

    private fun selectAll() {
        anchorIndex = 0
        caretIndex = buffer?.length ?: 0
        touch()
    }

    private fun selectionStart() = min(anchorIndex, caretIndex)
    private fun selectionEnd() = max(anchorIndex, caretIndex)

    private fun replaceSelection(inserted: String) {
        val current = buffer ?: return
        val start = selectionStart()
        val end = selectionEnd()
        val next = current.substring(0, start) + inserted + current.substring(end)
        if (next.length > MAX_INPUT_LENGTH) return
        buffer = next
        caretIndex = start + inserted.length
        anchorIndex = caretIndex
        touch()
    }

    private fun deleteSelection(): Boolean {
        val current = buffer ?: return false
        val start = selectionStart()
        val end = selectionEnd()
        if (start == end) return false
        buffer = current.substring(0, start) + current.substring(end)
        caretIndex = start
        anchorIndex = start
        touch()
        return true
    }

    private fun deleteBackward() {
        if (deleteSelection()) return
        val current = buffer ?: return
        if (caretIndex == 0) return
        buffer = current.substring(0, caretIndex - 1) + current.substring(caretIndex)
        caretIndex--
        anchorIndex = caretIndex
        touch()
    }

    private fun deleteForward() {
        if (deleteSelection()) return
        val current = buffer ?: return
        if (caretIndex >= current.length) return
        buffer = current.substring(0, caretIndex) + current.substring(caretIndex + 1)
        anchorIndex = caretIndex
        touch()
    }

    private fun moveCaretBy(delta: Int, extendSelection: Boolean) {
        moveCaretTo(caretIndex + delta, extendSelection)
    }

    private fun moveCaretTo(index: Int, extendSelection: Boolean) {
        val length = buffer?.length ?: return
        caretIndex = index.coerceIn(0, length)
        if (!extendSelection) anchorIndex = caretIndex
        touch()
    }

    /** Adjusts the edited number by [delta] without leaving edit mode. */
    private fun nudge(delta: Int) {
        val current = buffer ?: return
        val base = current.toIntOrNull() ?: getValue()
        val next = (base + delta).coerceIn(min, max).toString()
        buffer = next
        caretIndex = next.length
        anchorIndex = 0
        touch()
    }

    /** Caret index closest to a mouse x inside the value box. */
    private fun indexAt(mouseX: Int): Int {
        val current = buffer ?: return 0
        val fm = getFontMetrics(font)
        val textX = valueX() + (valueW - fm.stringWidth(current)) / 2
        for (index in 0 until current.length) {
            val before = fm.stringWidth(current.substring(0, index))
            val glyph = fm.stringWidth(current.substring(index, index + 1))
            if (mouseX < textX + before + glyph / 2) return index
        }
        return current.length
    }

    // ---------------------------------------------------------------- painting

    override fun paintComponent(graphics: Graphics) {
        val g = graphics.create() as Graphics2D
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
        g.font = font
        val fm = g.fontMetrics
        val baseline = (height + fm.ascent - fm.descent) / 2

        if (hovered) {
            g.color = Windows11PopupStyle.hover
            g.fillRoundRect(3, 2, width - 6, height - 4, 7, 7)
        }

        // Label on the left (shared column) + range hint centered in the middle column
        // between label end and the − slot — the three-column grid for the settings menu:
        // name | range | − [box] +, each column aligned to its own edge.
        drawText(g, fm, label, Windows11PopupStyle.text, LEFT_PAD, baseline)
        val labelEnd = LEFT_PAD + fm.stringWidth(label)
        val rangeLeft = labelEnd + RANGE_GAP
        val rangeRight = minusX() - RANGE_GAP
        drawRangeHint(g, rangeLeft, rangeRight, baseline)

        // − □ + controls on the right, vertically centred for the current row height.
        val slotH = (height - 12).coerceAtLeast(10)
        val slotY = (height - slotH) / 2
        drawSlot(g, fm, minusX(), MINUS_W, "−", baseline, slotY, slotH)
        drawSlot(g, fm, plusX(), PLUS_W, "+", baseline, slotY, slotH)
        val boxH = (height - 10).coerceAtLeast(12)
        drawValueBox(g, fm, valueX(), valueW, baseline, (height - boxH) / 2, boxH)
        g.dispose()
    }

    /**
     * Range hint centred between [leftX] and [rightX]. The font shrinks if the hint would
     * not fit, and it is clipped to the right edge, so the label stays flush left, the hint
     * sits in the middle column, and nothing overlaps the − □ + controls.
     */
    private fun drawRangeHint(g: Graphics2D, leftX: Int, rightX: Int, baseline: Int) {
        val maxWidth = rightX - leftX
        if (maxWidth <= 0) return
        var hintFont = font
        var hintWidth = g.getFontMetrics(hintFont).stringWidth(rangeText)
        while (hintWidth > maxWidth && hintFont.size2D > MIN_RANGE_FONT_SIZE) {
            hintFont = hintFont.deriveFont(hintFont.size2D - 1f)
            hintWidth = g.getFontMetrics(hintFont).stringWidth(rangeText)
        }
        val hintX = leftX + (maxWidth - hintWidth) / 2
        g.font = hintFont
        g.color = Windows11PopupStyle.muted
        val clip = g.clip
        g.clipRect(leftX, 0, maxWidth, height)
        g.drawString(rangeText, hintX, baseline)
        g.clip = clip
        g.font = font
    }

    private fun drawSlot(
        g: Graphics2D,
        fm: FontMetrics,
        x: Int,
        w: Int,
        glyph: String,
        baseline: Int,
        slotY: Int,
        slotH: Int
    ) {
        g.color = Windows11PopupStyle.muted
        g.stroke = BasicStroke(1f)
        g.drawRoundRect(x + 3, slotY, w - 6, slotH, 6, 6)
        drawText(g, fm, glyph, Windows11PopupStyle.text, x, baseline, w)
    }

    private fun drawValueBox(
        g: Graphics2D,
        fm: FontMetrics,
        x: Int,
        w: Int,
        baseline: Int,
        boxY: Int,
        boxH: Int
    ) {
        val editing = buffer != null
        g.color = Windows11PopupStyle.surface
        g.fillRoundRect(x + 1, boxY, w - 2, boxH, 6, 6)
        g.color = when {
            editing || hovered -> Windows11PopupStyle.accent
            else -> Windows11PopupStyle.muted
        }
        g.stroke = BasicStroke(if (editing || hovered) 1.5f else 1f)
        g.drawRoundRect(x + 1, boxY, w - 2, boxH, 6, 6)

        val value = buffer ?: getValue().toString()
        val textX = x + (w - fm.stringWidth(value)) / 2
        if (!editing) {
            drawText(g, fm, value, Windows11PopupStyle.text, x, baseline, w)
            return
        }

        // Selected digits sit on an accent band — the "clicked it, the number is
        // loaded and selected" look of a native spinner.
        val start = selectionStart()
        val end = selectionEnd()
        if (start < end) {
            val bandX = textX + fm.stringWidth(value.substring(0, start))
            val bandW = fm.stringWidth(value.substring(start, end))
            g.color = Windows11PopupStyle.accent
            g.fillRoundRect(bandX - 3, boxY + 3, bandW + 6, boxH - 6, 4, 4)
        }
        drawSelectionAwareText(g, fm, value, textX, baseline, start, end)
        drawCaret(g, fm, value, textX, boxY, boxH, start, end)
    }

    private fun drawSelectionAwareText(
        g: Graphics2D,
        fm: FontMetrics,
        value: String,
        textX: Int,
        baseline: Int,
        start: Int,
        end: Int
    ) {
        var penX = textX
        for ((from, to) in listOf(0 to start, start to end, end to value.length)) {
            if (to <= from) continue
            val text = value.substring(from, to)
            g.color = if (from >= start && from < end) {
                Windows11PopupStyle.accentText
            } else {
                Windows11PopupStyle.text
            }
            g.drawString(text, penX, baseline)
            penX += fm.stringWidth(text)
        }
    }

    private fun drawCaret(
        g: Graphics2D,
        fm: FontMetrics,
        value: String,
        textX: Int,
        boxY: Int,
        boxH: Int,
        start: Int,
        end: Int
    ) {
        if (!caretOn) return
        val caretX = textX + fm.stringWidth(value.substring(0, caretIndex))
        g.color = if (caretIndex in start until end) {
            Windows11PopupStyle.accentText
        } else {
            Windows11PopupStyle.accent
        }
        g.fillRect(caretX, boxY + 4, 1, boxH - 8)
    }

    private fun drawText(g: Graphics2D, fm: FontMetrics, text: String, color: Color, x: Int, baseline: Int, box: Int? = null) {
        g.color = color
        val tx = box?.let { x + (it - fm.stringWidth(text)) / 2 } ?: x
        g.drawString(text, tx, baseline)
    }
}
