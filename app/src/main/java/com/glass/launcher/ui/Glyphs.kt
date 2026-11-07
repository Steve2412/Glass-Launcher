package com.glass.launcher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Trazo de un glifo en una cuadrícula de 24×24 (mismos paths que el diseño). */
class GPath(val d: String, val fill: Boolean, val stroke: Float = 2f)

private fun f(d: String) = GPath(d, true)
private fun s(d: String, w: Float = 2f) = GPath(d, false, w)
private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

private fun build(name: String, vararg paths: GPath): ImageVector {
    val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
    for (p in paths) {
        if (p.fill) {
            b.addPath(pathData = addPathNodes(p.d), fill = SolidColor(Color.White))
        } else {
            b.addPath(
                pathData = addPathNodes(p.d),
                stroke = SolidColor(Color.White),
                strokeLineWidth = p.stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }
    return b.build()
}

object Glyphs {
    val phone = build("phone", f("M6.6 3.5h2.6a1 1 0 0 1 .95.68l1.1 3.3a1 1 0 0 1-.3 1.07L9.4 9.9a12 12 0 0 0 4.7 4.7l1.35-1.55a1 1 0 0 1 1.07-.3l3.3 1.1a1 1 0 0 1 .68.95v2.6a2 2 0 0 1-2.2 2A16.5 16.5 0 0 1 4.6 5.7a2 2 0 0 1 2-2.2z"))
    val nav = build("nav", f("M3.5 10.6L20.2 3.4a.3.3 0 0 1 .4.4L13.4 20.5a.3.3 0 0 1-.56-.03l-2.1-6.8-6.8-2.1a.3.3 0 0 1-.03-.56z"))
    val music = build("music", f("M9 17.5V6.2a1 1 0 0 1 .8-.98l9.5-2a1 1 0 0 1 1.2.98V15.5h-2V6.4L11 7.9v9.6z"), f(circle(6.5f, 17.5f, 3f)), f(circle(18f, 15.5f, 3f)))
    val link = build("link", s("M10 13.5a4.5 4.5 0 0 0 6.8.5l2.8-2.8a4.5 4.5 0 0 0-6.4-6.4l-1.5 1.5", 2.2f), s("M14 10.5a4.5 4.5 0 0 0-6.8-.5l-2.8 2.8a4.5 4.5 0 0 0 6.4 6.4l1.5-1.5", 2.2f))
    val gear = build("gear", s(circle(12f, 12f, 3.2f)), s("M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3M5.3 5.3l2.1 2.1M16.6 16.6l2.1 2.1M5.3 18.7l2.1-2.1M16.6 7.4l2.1-2.1"))
    val grid = build(
        "grid",
        f(circle(5f, 5f, 2.2f)), f(circle(12f, 5f, 2.2f)), f(circle(19f, 5f, 2.2f)),
        f(circle(5f, 12f, 2.2f)), f(circle(12f, 12f, 2.2f)), f(circle(19f, 12f, 2.2f)),
        f(circle(5f, 19f, 2.2f)), f(circle(12f, 19f, 2.2f)), f(circle(19f, 19f, 2.2f)),
    )
    val bluetooth = build("bt", s("M7 7l10 10-5 5V2l5 5L7 17", 2.2f))
    val wifi = build("wifi", s("M2 8.8a15 15 0 0 1 20 0M5 12.6a10 10 0 0 1 14 0M8.5 16.2a5 5 0 0 1 7 0", 2.2f), f(circle(12f, 19.6f, 1.2f)))
    val wifiOff = build("wifiOff", s("M8.5 16.2a5 5 0 0 1 7 0M3 3l18 18", 2.2f), f(circle(12f, 19.6f, 1.2f)))
    val sun = build("sun", f(circle(12f, 12f, 4.5f)), s("M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"))
    val moon = build("moon", f("M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z"))
    val cloud = build("cloud", s("M7 17h10a4 4 0 0 0 .4-7.98A6 6 0 0 0 6.2 8.3 4.4 4.4 0 0 0 7 17z"))
    val rain = build("rain", s("M7 14h10a4 4 0 0 0 .4-7.98A6 6 0 0 0 6.2 5.3 4.4 4.4 0 0 0 7 14z"), s("M8 17.5l-1 2.5M12 17.5l-1 2.5M16 17.5l-1 2.5"))
    val storm = build("storm", s("M7 14h10a4 4 0 0 0 .4-7.98A6 6 0 0 0 6.2 5.3 4.4 4.4 0 0 0 7 14z"), s("M12.5 15l-2 3.5h3l-2 3.5"))
    val fog = build("fog", s("M4 9h16M6 13h12M4 17h16"))
    val snow = build("snow", s("M12 3v18M4.2 7.5l15.6 9M4.2 16.5l15.6-9"))
    val play = build("play", f("M8 5.5v13a1 1 0 0 0 1.5.86l10.4-6.5a1 1 0 0 0 0-1.72L9.5 4.64A1 1 0 0 0 8 5.5z"))
    val pause = build("pause", f("M7.2 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2H7.2A1.2 1.2 0 0 1 6 17.8V6.2A1.2 1.2 0 0 1 7.2 5zM15.2 5h1.6a1.2 1.2 0 0 1 1.2 1.2v11.6a1.2 1.2 0 0 1-1.2 1.2h-1.6a1.2 1.2 0 0 1-1.2-1.2V6.2A1.2 1.2 0 0 1 15.2 5z"))
    val prev = build("prev", f("M6 5h2v14H6zM20 5v14L9.5 12z"))
    val next = build("next", f("M16 5h2v14h-2zM4 5v14l10.5-7z"))
    val minus = build("minus", s("M6 12h12", 2.4f))
    val plus = build("plus", s("M6 12h12M12 6v12", 2.4f))
    val volume = build("volume", f("M4 9.5h3.5L12 5.5v13l-4.5-4H4z"), s("M15.5 9a4 4 0 0 1 0 6M18 6.5a7.5 7.5 0 0 1 0 11"))
    val back = build("back", s("M15 5l-7 7 7 7", 2.6f))
    val left = build("left", s("M15 5l-7 7 7 7", 2.4f))
    val right = build("right", s("M9 5l7 7-7 7", 2.4f))
    val search = build("search", s(circle(11f, 11f, 6.5f), 2.4f), s("M20 20l-4-4", 2.4f))
    val check = build("check", s("M5 12.5l4.5 4.5L19 7.5", 3.4f))
    val photo = build("photo", s("M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z"), s(circle(9f, 10f, 2f)), s("M21 16l-5-5-8 9"))
    val iphone = build("iphone", s("M9 2.5h6a2.5 2.5 0 0 1 2.5 2.5v14a2.5 2.5 0 0 1-2.5 2.5H9A2.5 2.5 0 0 1 6.5 19V5A2.5 2.5 0 0 1 9 2.5z"), s("M10.5 18.5h3"))
    val customize = build(
        "customize",
        s("M5.5 3.5h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2zM15.5 3.5h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2zM5.5 13.5h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z"),
        s("M17 13.5v7M13.5 17h7"),
    )
    val widget = build("widget", s("M5.5 3.5h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2zM15.5 3.5h3a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-3a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2zM5.5 13.5h13a2 2 0 0 1 2 2v3a2 2 0 0 1-2 2h-13a2 2 0 0 1-2-2v-3a2 2 0 0 1 2-2z"))
    val trash = build("trash", s("M4 7h16M10 11v6M14 11v6M6 7l1 12a2 2 0 0 0 2 2h6a2 2 0 0 0 2-2l1-12M9 7V4h6v3"))
    val info = build("info", s(circle(12f, 12f, 9f)), s("M12 11v6M12 7.5v.5", 2.4f))
    val resize = build("resize", s("M14 4h6v6M10 20H4v-6M20 4l-7 7M4 20l7-7"))
    val swap = build("swap", s("M4 8h14l-3-3M20 16H6l3 3"))
    val image = build("image", s("M6 4h12a3 3 0 0 1 3 3v10a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3V7a3 3 0 0 1 3-3z"), s("M3 16l5-5 4 4 3-3 6 6"))
    val home = build("home", s("M4 11l8-7 8 7v8a1 1 0 0 1-1 1h-4v-6H9v6H5a1 1 0 0 1-1-1z"))
    val dock = build("dock", s("M6 15h12a3 3 0 0 1 3 3v0a3 3 0 0 1-3 3H6a3 3 0 0 1-3-3v0a3 3 0 0 1 3-3zM8 4h8v7H8z"))
    val car = build("car", s("M5 15.5l1.6-5a2 2 0 0 1 1.9-1.4h7a2 2 0 0 1 1.9 1.4l1.6 5"), s("M4.5 15.5h15a1 1 0 0 1 1 1v2.5h-17v-2.5a1 1 0 0 1 1-1z"), f(circle(7.5f, 19.5f, 1.6f)), f(circle(16.5f, 19.5f, 1.6f)))
    val user = build("user", s(circle(12f, 8f, 4f)), s("M4.5 20.5a7.5 7.5 0 0 1 15 0"))
    val close = build("close", s("M6 6l12 12M18 6L6 18", 2.4f))
}

@Composable
fun Glyph(icon: ImageVector, size: Dp, tint: Color = Color.White, modifier: Modifier = Modifier) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(tint),
        modifier = modifier.size(size),
    )
}
