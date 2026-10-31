package com.glass.launcher.icons

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sign
import kotlin.math.sin

/** Forma "squircle" (superelipse) como los iconos de iOS. */
object Squircle {
    private const val N = 5.0
    private const val STEPS = 96

    fun points(w: Float, h: Float): List<Pair<Float, Float>> {
        val a = w / 2f
        val b = h / 2f
        return (0 until STEPS).map { i ->
            val t = 2.0 * Math.PI * i / STEPS
            val c = cos(t)
            val s = sin(t)
            val x = a * sign(c) * abs(c).pow(2.0 / N)
            val y = b * sign(s) * abs(s).pow(2.0 / N)
            (a + x.toFloat()) to (b + y.toFloat())
        }
    }

    fun androidPath(w: Float, h: Float): android.graphics.Path {
        val p = android.graphics.Path()
        points(w, h).forEachIndexed { i, (x, y) -> if (i == 0) p.moveTo(x, y) else p.lineTo(x, y) }
        p.close()
        return p
    }
}

object SquircleShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val p = Path()
        Squircle.points(size.width, size.height).forEachIndexed { i, (x, y) ->
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}
