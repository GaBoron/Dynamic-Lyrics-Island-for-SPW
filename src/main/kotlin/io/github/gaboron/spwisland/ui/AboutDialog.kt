// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import io.github.gaboron.spwisland.platform.SystemTheme
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.RoundRectangle2D
import javax.swing.*
import javax.swing.border.EmptyBorder

private fun uiFont(style: Int, size: Float): Font =
    if (com.sun.jna.Platform.isWindows()) Font("Segoe UI", style, size.toInt()).deriveFont(size)
    else SystemUiFont.derive(style, size)

/** Owns the modern, self-drawn project and licensing window. */
internal class AboutDialog(private val owner: Window?, private val report: (Throwable) -> Unit) : AutoCloseable {
    private data class Palette(
        val background: Color, val card: Color, val text: Color, val muted: Color,
        val accent: Color, val hover: Color, val border: Color
    )

    private var window: JDialog? = null
    private var dark: Boolean? = null

    fun show() {
        check(SwingUtilities.isEventDispatchThread())
        val useDark = !SystemTheme.isLight()
        if (window == null || dark != useDark) {
            window?.dispose()
            dark = useDark
            window = createWindow(palette(useDark))
        }
        window?.let { dialog ->
            fitToWorkArea(dialog)
            dialog.isVisible = true
            dialog.toFront()
            dialog.requestFocus()
        }
    }

    private fun createWindow(colors: Palette): JDialog {
        val dialog = JDialog(owner, "关于与许可", Dialog.ModalityType.MODELESS).apply {
            setIconImage(ApplicationIdentity.icon)
            isUndecorated = true
            isAlwaysOnTop = true
            background = Color(0, 0, 0, 0)
            defaultCloseOperation = WindowConstants.HIDE_ON_CLOSE
            contentPane = surface(colors, this)
            rootPane.registerKeyboardAction({ isVisible = false }, KeyStroke.getKeyStroke("ESCAPE"),
                JComponent.WHEN_IN_FOCUSED_WINDOW)
        }
        dialog.pack()
        return dialog
    }

    private fun surface(colors: Palette, dialog: JDialog): JPanel {
        val body = ScrollableBody().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(0, 28, 14, 28)
            add(card(colors, "项目", "独立的 SPW 歌词显示插件，重新实现渲染、交互与播放时序；\n非官方 Lyricify 产品。"))
            add(Box.createVerticalStrut(10))
            add(card(colors, "原创与视觉改编", "灵动词岛原创：Lyricify / WXRIW（XY Wang）\n" +
                "视觉创意采用 CC BY-SA 4.0\n" +
                "github.com/WXRIW/Lyricify-App\n" +
                "creativecommons.org/licenses/by-sa/4.0"))
            add(Box.createVerticalStrut(10))
            add(card(colors, "歌词动画", "AMLL contributors / Steve-xmh\n" +
                "动画移植模块采用 AGPL-3.0-only\n" +
                "github.com/amll-dev/applemusic-like-lyrics"))
            add(Box.createVerticalStrut(10))
            add(card(colors, "Windows 歌词字体", "MiSans 可变字体由小米提供，已用于 Windows 原生词岛。\n" +
                "字体授权和版权声明随插件提供。"))
            add(Box.createVerticalStrut(10))
            add(card(colors, "许可说明", "其他程序：GPL-3.0-only · SPW API：Apache-2.0\n" +
                "按两者第 13 条组合分发；完整许可、第三方声明及对应源码随插件 ZIP 提供。"))
        }
        return AboutSurface(colors).apply {
            layout = BorderLayout()
            border = EmptyBorder(1, 1, 1, 1)
            val header = header(colors, dialog)
            add(header, BorderLayout.NORTH)
            add(JScrollPane(body, ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER).apply {
                isOpaque = false
                viewport.isOpaque = false
                border = null
                viewportBorder = null
                verticalScrollBar.unitIncrement = 18
            }, BorderLayout.CENTER)
            add(footer(colors, dialog), BorderLayout.SOUTH)
            installDrag(header, dialog)
        }
    }

    private fun header(colors: Palette, dialog: JDialog) = JPanel(BorderLayout()).apply {
        isOpaque = false
        border = EmptyBorder(23, 28, 19, 22)
        add(IslandMark(colors), BorderLayout.WEST)
        add(JPanel().apply {
            isOpaque = false
            layout = BoxLayout(this, BoxLayout.Y_AXIS)
            border = EmptyBorder(1, 15, 0, 0)
            add(JLabel(ApplicationIdentity.NAME).apply {
                foreground = colors.text
                font = uiFont(Font.BOLD, 20f)
            })
            add(Box.createVerticalStrut(3))
            add(JLabel("关于与许可 · for SPW").apply {
                foreground = colors.muted
                font = uiFont(Font.PLAIN, 12f)
            })
        }, BorderLayout.CENTER)
        add(CloseButton(colors).apply { addActionListener { dialog.isVisible = false } }, BorderLayout.EAST)
    }

    private fun card(colors: Palette, title: String, content: String): AboutCard {
        val text = JTextArea(content).apply {
            isOpaque = false
            isEditable = false
            isFocusable = false
            rows = content.lineSequence().count()
            columns = 42
            lineWrap = false
            foreground = colors.muted
            font = uiFont(Font.PLAIN, 12f)
            border = null
        }
        return AboutCard(colors).apply {
            layout = BorderLayout(0, 7)
            border = EmptyBorder(13, 17, 13, 17)
            alignmentX = Component.LEFT_ALIGNMENT
            add(JLabel(title).apply {
                foreground = colors.text
                font = uiFont(Font.BOLD, 13f)
            }, BorderLayout.NORTH)
            add(text, BorderLayout.CENTER)
            val naturalHeight = preferredSize.height
            minimumSize = Dimension(0, naturalHeight)
            maximumSize = Dimension(Int.MAX_VALUE, naturalHeight)
        }
    }

    private fun fitToWorkArea(dialog: JDialog) {
        dialog.pack()
        val configuration = owner?.graphicsConfiguration
            ?: GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val bounds = configuration.bounds
        val insets = Toolkit.getDefaultToolkit().getScreenInsets(configuration)
        val work = Rectangle(bounds.x + insets.left, bounds.y + insets.top,
            bounds.width - insets.left - insets.right, bounds.height - insets.top - insets.bottom)
        val width = dialog.width.coerceAtMost((work.width - 48).coerceAtLeast(360))
        val height = dialog.height.coerceAtMost((work.height - 48).coerceAtLeast(320))
        dialog.setSize(width, height)
        dialog.shape = RoundRectangle2D.Double(0.0, 0.0, width.toDouble(), height.toDouble(), 30.0, 30.0)
        val center = Point(work.x + work.width / 2, work.y + work.height / 2)
        dialog.setLocation(
            (center.x - width / 2).coerceIn(work.x, (work.x + work.width - width).coerceAtLeast(work.x)),
            (center.y - height / 2).coerceIn(work.y, (work.y + work.height - height).coerceAtLeast(work.y)))
    }

    private fun footer(colors: Palette, dialog: JDialog) = JPanel().apply {
        isOpaque = false
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        border = EmptyBorder(0, 28, 22, 28)
        add(JLabel("在适用法律允许范围内不提供担保，可按对应许可证再分发。").apply {
            foreground = colors.muted
            font = uiFont(Font.PLAIN, 11f)
            alignmentX = Component.LEFT_ALIGNMENT
        })
        add(Box.createVerticalStrut(12))
        add(JPanel(FlowLayout(FlowLayout.RIGHT, 8, 0)).apply {
            isOpaque = false
            alignmentX = Component.LEFT_ALIGNMENT
            maximumSize = Dimension(Int.MAX_VALUE, 36)
            add(ActionButton("项目源代码", colors, true).apply {
                addActionListener { runCatching(ProjectLinks::openSource).onFailure(report) }
            })
            add(ActionButton("关闭", colors, false).apply {
                addActionListener { dialog.isVisible = false }
            })
        })
    }

    private fun installDrag(component: Container, dialog: JDialog) {
        val drag = object : MouseAdapter() {
            private var pointer: Point? = null
            private var origin: Point? = null
            override fun mousePressed(event: MouseEvent) {
                pointer = event.locationOnScreen
                origin = dialog.location
            }
            override fun mouseDragged(event: MouseEvent) {
                val start = pointer ?: return
                val location = origin ?: return
                dialog.setLocation(location.x + event.xOnScreen - start.x, location.y + event.yOnScreen - start.y)
            }
        }
        fun attach(target: Component) {
            if (target is AbstractButton) return
            target.addMouseListener(drag)
            target.addMouseMotionListener(drag)
            if (target is Container) target.components.forEach(::attach)
        }
        attach(component)
    }

    private fun palette(useDark: Boolean) = if (useDark) Palette(
        Color(0x20, 0x20, 0x20), Color(0x2B, 0x2B, 0x2B), Color(0xF5, 0xF5, 0xF5),
        Color(0xAE, 0xAE, 0xAE), Color(0x60, 0xCD, 0xFF), Color(0x3B, 0x3B, 0x3B),
        Color(0xFF, 0xFF, 0xFF, 38)
    ) else Palette(
        Color(0xF3, 0xF3, 0xF3), Color(0xFA, 0xFA, 0xFA), Color(0x1B, 0x1B, 0x1B),
        Color(0x60, 0x60, 0x60), Color(0x00, 0x67, 0xC0), Color(0xE7, 0xE7, 0xE7),
        Color(0x45, 0x45, 0x45, 30)
    )

    override fun close() {
        window?.dispose()
        window = null
    }

    private class AboutSurface(private val colors: Palette) : JPanel() {
        init { isOpaque = false }
        override fun paintComponent(graphics: Graphics) {
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            val outline = RoundRectangle2D.Double(.5, .5, width - 1.0, height - 1.0, 30.0, 30.0)
            g.color = colors.background
            g.fill(outline)
            g.color = colors.border
            g.draw(outline)
            g.dispose()
            super.paintComponent(graphics)
        }
    }

    private class AboutCard(private val colors: Palette) : JPanel() {
        init { isOpaque = false }
        override fun paintComponent(graphics: Graphics) {
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = colors.card
            g.fillRoundRect(0, 0, width, height, 18, 18)
            g.color = colors.border
            g.drawRoundRect(0, 0, width - 1, height - 1, 18, 18)
            g.dispose()
            super.paintComponent(graphics)
        }
    }

    private class ScrollableBody : JPanel(), Scrollable {
        override fun getPreferredScrollableViewportSize(): Dimension = preferredSize
        override fun getScrollableUnitIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) = 18
        override fun getScrollableBlockIncrement(visibleRect: Rectangle, orientation: Int, direction: Int) =
            (visibleRect.height - 36).coerceAtLeast(18)
        override fun getScrollableTracksViewportWidth() = true
        override fun getScrollableTracksViewportHeight() = false
    }

    private class IslandMark(private val colors: Palette) : JComponent() {
        init {
            preferredSize = Dimension(44, 44)
            minimumSize = preferredSize
        }
        override fun paintComponent(graphics: Graphics) {
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = colors.text
            g.fillRoundRect(0, 5, 44, 34, 28, 28)
            g.color = colors.background
            intArrayOf(11, 17, 23, 29).forEachIndexed { index, x ->
                val bar = if (index % 2 == 0) 14 else 9
                g.fillRoundRect(x, 22 - bar / 2, 4, bar, 4, 4)
            }
            g.dispose()
        }
    }

    private class CloseButton(private val colors: Palette) : JButton() {
        init {
            preferredSize = Dimension(34, 34)
            isFocusable = false
            isContentAreaFilled = false
            isBorderPainted = false
            toolTipText = "关闭"
            accessibleContext?.accessibleName = "关闭"
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        override fun paintComponent(graphics: Graphics) {
            val g = graphics.create() as Graphics2D
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
                if (model.isRollover || model.isPressed) {
                g.color = colors.hover
                g.fillOval(2, 2, width - 4, height - 4)
                }
                g.color = colors.text
                g.stroke = BasicStroke(1.6f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.drawLine(12, 12, 22, 22)
                g.drawLine(22, 12, 12, 22)
            } finally {
                g.dispose()
            }
        }
    }

    private class ActionButton(text: String, private val colors: Palette, private val primary: Boolean) : JButton(text) {
        init {
            preferredSize = Dimension(if (primary) 104 else 72, 34)
            isFocusPainted = false
            isContentAreaFilled = false
            isBorderPainted = false
            foreground = if (primary) colors.background else colors.text
            font = uiFont(Font.PLAIN, 12f)
            cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        }
        override fun paintComponent(graphics: Graphics) {
            val g = graphics.create() as Graphics2D
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = when {
                model.isPressed -> if (primary) colors.accent.darker() else colors.hover.darker()
                model.isRollover -> if (primary) colors.accent.brighter() else colors.card
                primary -> colors.accent
                else -> colors.hover
            }
            g.fillRoundRect(0, 0, width, height, 12, 12)
            if (!primary) {
                g.color = colors.border
                g.drawRoundRect(0, 0, width - 1, height - 1, 12, 12)
            }
            g.dispose()
            super.paintComponent(graphics)
        }
    }
}
