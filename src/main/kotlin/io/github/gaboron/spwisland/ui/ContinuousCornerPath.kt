// SPDX-License-Identifier: GPL-3.0-only
package io.github.gaboron.spwisland.ui

import java.awt.Shape
import java.awt.geom.Path2D
import java.awt.geom.Rectangle2D
import java.awt.geom.RoundRectangle2D
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/** Builds rounded-superellipse corners while preserving a circular stadium at full roundness. */
internal object ContinuousCornerPath {
    private const val CORNER_SEGMENTS = 24
    private const val CONTINUOUS_EXPONENT = 4.0
    private const val CIRCULAR_TRANSITION_START = .6
    private const val CIRCLE_BEZIER = .5522847498307936

    fun roundedRectangle(width: Double, height: Double, roundness: Int): Shape {
        if (width <= 0.0 || height <= 0.0) return Path2D.Double()
        val radius = radius(width, height, roundness)
        if (radius <= 0.0) return Rectangle2D.Double(0.0, 0.0, width, height)
        if (roundness >= 100) {
            return RoundRectangle2D.Double(0.0, 0.0, width, height, radius * 2.0, radius * 2.0)
        }
        val exponent = exponent(roundness)
        return Path2D.Double().apply {
            moveTo(radius, 0.0)
            lineTo(width - radius, 0.0)
            appendTopRight(this, width, 0.0, radius, exponent)
            lineTo(width, height - radius)
            appendBottomRight(this, width, height, radius, exponent)
            lineTo(radius, height)
            appendBottomLeft(this, 0.0, height, radius, exponent)
            lineTo(0.0, radius)
            appendTopLeft(this, 0.0, 0.0, radius, exponent)
            closePath()
        }
    }

    fun radius(width: Double, height: Double, roundness: Int): Double =
        minOf(width, height) / 2.0 * roundness.coerceIn(0, 100) / 100.0

    fun exponent(roundness: Int): Double {
        val amount = roundness.coerceIn(0, 100) / 100.0
        if (amount <= CIRCULAR_TRANSITION_START) return CONTINUOUS_EXPONENT
        val circularBlend = (amount - CIRCULAR_TRANSITION_START) / (1.0 - CIRCULAR_TRANSITION_START)
        return CONTINUOUS_EXPONENT + (2.0 - CONTINUOUS_EXPONENT) * circularBlend
    }

    fun appendBottomRight(path: Path2D.Double, right: Double, bottom: Double,
                          radius: Double, exponent: Double) {
        if (exponent <= 2.0) {
            path.curveTo(right, bottom - radius + radius * CIRCLE_BEZIER,
                right - radius + radius * CIRCLE_BEZIER, bottom, right - radius, bottom)
            return
        }
        append(path, exponent) { angle, power ->
            right - radius + radius * cos(angle).pow(power) to
                bottom - radius + radius * sin(angle).pow(power)
        }
    }

    fun appendBottomLeft(path: Path2D.Double, left: Double, bottom: Double,
                         radius: Double, exponent: Double) {
        if (exponent <= 2.0) {
            path.curveTo(left + radius - radius * CIRCLE_BEZIER, bottom,
                left, bottom - radius + radius * CIRCLE_BEZIER, left, bottom - radius)
            return
        }
        append(path, exponent) { angle, power ->
            left + radius - radius * sin(angle).pow(power) to
                bottom - radius + radius * cos(angle).pow(power)
        }
    }

    private fun appendTopRight(path: Path2D.Double, right: Double, top: Double,
                               radius: Double, exponent: Double) =
        append(path, exponent) { angle, power ->
            right - radius + radius * sin(angle).pow(power) to
                top + radius - radius * cos(angle).pow(power)
        }

    private fun appendTopLeft(path: Path2D.Double, left: Double, top: Double,
                              radius: Double, exponent: Double) =
        append(path, exponent) { angle, power ->
            left + radius - radius * cos(angle).pow(power) to
                top + radius - radius * sin(angle).pow(power)
        }

    private inline fun append(path: Path2D.Double, exponent: Double,
                              point: (Double, Double) -> Pair<Double, Double>) {
        val power = 2.0 / exponent
        for (index in 1..CORNER_SEGMENTS) {
            val angle = PI / 2.0 * index / CORNER_SEGMENTS
            val (x, y) = point(angle, power)
            path.lineTo(x, y)
        }
    }
}
