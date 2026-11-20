package com.bitdroid.tube.ui

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

typealias Radius = (Double) -> Double

/** A shape traced around the center: radius(angle) as a fraction of half the box. Optional slow spin. */
class PolarShape(private val steps: Int = 180, private val rotation: Double = 0.0, private val radius: Radius) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val cx = size.width / 2f
        val cy = size.height / 2f
        val s = min(size.width, size.height) / 2f
        val path = Path()
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val r = (radius(t - rotation) * s).toFloat()
            val x = cx + r * cos(t).toFloat()
            val y = cy + r * sin(t).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** A polygon with softly rounded corners. */
class RoundedPolygonShape(private val sides: Int, private val round: Float = 0.3f, private val rotation: Double = -PI / 2) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val s = min(size.width, size.height) / 2f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val r = s * 0.98f
        val v = List(sides) { i ->
            val a = rotation + 2 * PI * i / sides
            Offset(cx + r * cos(a).toFloat(), cy + r * sin(a).toFloat())
        }
        val path = Path()
        for (i in 0 until sides) {
            val prev = v[(i - 1 + sides) % sides]
            val cur = v[i]
            val next = v[(i + 1) % sides]
            val p1 = cur + (prev - cur) * round
            val p2 = cur + (next - cur) * round
            if (i == 0) path.moveTo(p1.x, p1.y) else path.lineTo(p1.x, p1.y)
            path.quadraticTo(cur.x, cur.y, p2.x, p2.y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** A soft rounded pill whose edge ripples like a cookie when [wave] > 0. */
class WavyPill(private val wave: Float, private val phase: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val steps = 160
        for (i in 0..steps) {
            val t = 2 * PI * i / steps
            val c = cos(t)
            val s = sin(t)
            val r = (abs(c).pow(4.0) + abs(s).pow(4.0)).pow(-0.25)
            val w = 1.0 + wave * cos(9 * t + phase)
            val x = cx + (size.width / 2f) * (c * r * w).toFloat()
            val y = cy + (size.height / 2f) * (s * r * w).toFloat()
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

private fun superellipse(t: Double, p: Double) = (abs(cos(t)).pow(p) + abs(sin(t)).pow(p)).pow(-1.0 / p)

/** The Bloom shape set: scalloped cookies, clovers, bursts and friends. */
object Shapes {
    val ripple: Radius = { t -> 0.9 + 0.08 * cos(9 * t) }
    val sunny: Radius = { t -> 0.93 + 0.06 * cos(12 * t) }
    val clover: Radius = { t -> 0.74 + 0.25 * abs(cos(2 * t)).pow(0.6) }
    val flower: Radius = { t -> 0.82 + 0.16 * abs(cos(2.5 * t)).pow(0.8) }
    val squircle: Radius = { t -> superellipse(t, 4.0) }
    val gem: Radius = { t -> 0.98 * superellipse(t, 1.35) }
    val blob: Radius = { t -> 0.9 + 0.06 * cos(3 * t) + 0.03 * sin(5 * t) }

    val Circle: Shape = CircleShape
    val Ripple: Shape = PolarShape(radius = ripple)
    val Sunny: Shape = PolarShape(radius = sunny)
    val Clover: Shape = PolarShape(radius = clover)
    val Flower: Shape = PolarShape(radius = flower)
    val Squircle: Shape = PolarShape(radius = squircle)
    val Gem: Shape = PolarShape(radius = gem)
    val Blob: Shape = PolarShape(radius = blob)
    val Triangle: Shape = RoundedPolygonShape(3, 0.32f, 0.0)
    val Pentagon: Shape = RoundedPolygonShape(5, 0.28f)

    /** Halfway shapes for morphing: [t]=0 is [a], 1 is [b]. */
    fun morph(a: Radius, b: Radius, t: Float, rotation: Double = 0.0): Shape =
        PolarShape(rotation = rotation) { x -> a(x) * (1 - t) + b(x) * t }

    /** A stable cookie shape per channel so the same creator always gets the same one. */
    private val avatarSet = listOf(Ripple, Sunny, Clover, Flower, Gem, Squircle, Blob)
    fun forKey(key: String): Shape = avatarSet[Math.floorMod(key.hashCode(), avatarSet.size)]
}
