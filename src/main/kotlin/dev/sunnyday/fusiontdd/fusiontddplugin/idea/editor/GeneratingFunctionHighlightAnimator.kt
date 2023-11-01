package dev.sunnyday.fusiontdd.fusiontddplugin.idea.editor

import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.Editor
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.util.animation.JBAnimator
import com.intellij.util.animation.animation
import org.jetbrains.kotlin.psi.KtNamedFunction
import java.awt.*
import javax.swing.JComponent
import kotlin.math.max

@Suppress("UnstableApiUsage")
internal class GeneratingFunctionHighlightAnimator {

    private val startColor = JBColor.YELLOW
    private val endColor = ColorUtil.withAlpha(startColor, 0.3)

    fun animate(function: KtNamedFunction, editor: Editor): Disposable {
        val highlight = Highlight(function, editor)

        val contentComponent = editor.contentComponent
        contentComponent.add(highlight)
        highlight.setBounds(0, 0, contentComponent.width, contentComponent.height)

        val animationDisposable = animateColor(highlight::setHighlightColor)

        return Disposable {
            animationDisposable.dispose()
            highlight.setHighlightColor(ColorUtil.withAlpha(startColor, .0))
            editor.contentComponent.remove(highlight)
        }
    }

    private fun animateColor(apply: (Color) -> Unit): Disposable {
        return JBAnimator().apply {
            setCyclic(true)
            animate(
                animation(startColor, endColor, apply),
                animation(endColor, startColor, apply)
                    .setDelay(500),
            )
        }
    }

    private class Highlight(
        private val function: KtNamedFunction,
        private val editor: Editor,
    ) : JComponent() {

        private var color: Color? = null
        private var fillColor: Color? = null

        private val funRect = Rectangle()
        private var funOffset: IntRange = 0..0

        fun setHighlightColor(color: Color) {
            this.color = color
            fillColor = ColorUtil.withAlpha(color, color.alpha.toDouble() / 0xff * 0.3)

            repaint()
        }

        override fun paintChildren(g: Graphics) {
            super.paintChildren(g)

            if (color == null) {
                return
            }

            (g as? Graphics2D)?.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

            if (funOffset.first != function.startOffset || funOffset.last != function.endOffset) {
                updateFunctionRect()
                funOffset = function.startOffset..function.endOffset
            }

            g.color = color
            g.drawRoundRect(funRect.x, funRect.y, funRect.width, funRect.height, HIGHLIGHT_RADIUS, HIGHLIGHT_RADIUS)

            g.color = fillColor
            g.fillRoundRect(funRect.x, funRect.y, funRect.width, funRect.height, HIGHLIGHT_RADIUS, HIGHLIGHT_RADIUS)
        }

        private fun updateFunctionRect() {
            val funStartPoint = editor.offsetToXY(function.startOffset)
            val x = funStartPoint.x
            var y = funStartPoint.y
            var width = 0
            var height = 0

            for (i in function.startOffset..function.endOffset) {
                val point = editor.offsetToXY(i)
                if (point.y != funStartPoint.y && y == funStartPoint.y) {
                    y = point.y
                }
                width = max(width, point.x - x)
                height = max(height, point.y - y)
            }

            funRect.setLocation(x, y)
            funRect.setSize(width, height)
        }

        private companion object {
            const val HIGHLIGHT_RADIUS = 10
        }
    }
}