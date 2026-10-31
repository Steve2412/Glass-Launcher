package com.glass.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.Shader
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

data class Radial(val cx: Float, val cy: Float, val rx: Float, val ry: Float, val color: Int, val stop: Float)

data class WallpaperSpec(
    val id: String,
    val name: String,
    val radials: List<Radial>,
    val stops: List<Pair<Float, Int>>,
)

class WallpaperImages(val full: ImageBitmap, val blurred: ImageBitmap)

/** Los fondos del launcher (mismos degradados que el diseño) y la foto propia. */
object Wallpapers {
    private fun c(hex: Long) = hex.toInt()

    val all = listOf(
        WallpaperSpec(
            "noche", "Noche",
            listOf(Radial(.80f, .10f, .70f, .60f, c(0xFF3A6AD8), .60f), Radial(.10f, .20f, .60f, .60f, c(0xFF6B3FC0), .65f)),
            listOf(0f to c(0xFF24306E), .45f to c(0xFF1A1D48), 1f to c(0xFF0D0E22)),
        ),
        WallpaperSpec(
            "medianoche", "Medianoche",
            listOf(Radial(.70f, 0f, .60f, .50f, c(0xFF2A3F8F), .65f), Radial(0f, 1f, .50f, .50f, c(0xFF1D2A66), .70f)),
            listOf(0f to c(0xFF0F1430), 1f to c(0xFF070914)),
        ),
        WallpaperSpec(
            "aurora", "Aurora",
            listOf(Radial(.25f, .10f, .60f, .55f, c(0xFF2FD5A0), .60f), Radial(.85f, .25f, .60f, .60f, c(0xFF3D6FE0), .62f)),
            listOf(0f to c(0xFF164C63), .5f to c(0xFF12284A), 1f to c(0xFF0A1022)),
        ),
        WallpaperSpec(
            "oceano", "Océano",
            listOf(Radial(.80f, .08f, .70f, .60f, c(0xFF39C3C9), .60f), Radial(.10f, .20f, .60f, .60f, c(0xFF2B7BD1), .65f)),
            listOf(0f to c(0xFF1F7F9E), .45f to c(0xFF1C4F86), 1f to c(0xFF0F1C3A)),
        ),
        WallpaperSpec(
            "atardecer", "Atardecer",
            listOf(Radial(.78f, .08f, .70f, .60f, c(0xFFF08AA8), .60f), Radial(.10f, .15f, .60f, .55f, c(0xFFA35BB8), .65f)),
            listOf(0f to c(0xFFC0628F), .38f to c(0xFF8A4FA6), .68f to c(0xFF3B2A6B), 1f to c(0xFF17132E)),
        ),
        WallpaperSpec(
            "coral", "Coral",
            listOf(Radial(.80f, .10f, .65f, .55f, c(0xFFFF9A6B), .60f), Radial(.10f, .25f, .60f, .60f, c(0xFFE0466E), .62f)),
            listOf(0f to c(0xFFB8456A), .45f to c(0xFF6E2A5C), 1f to c(0xFF1E1028)),
        ),
        WallpaperSpec(
            "lavanda", "Lavanda",
            listOf(Radial(.75f, .08f, .65f, .55f, c(0xFFC7A8FF), .60f), Radial(.10f, .25f, .60f, .60f, c(0xFF7A7CF0), .62f)),
            listOf(0f to c(0xFF7A62C8), .45f to c(0xFF463C94), 1f to c(0xFF15132E)),
        ),
        WallpaperSpec(
            "grafito", "Grafito",
            listOf(Radial(.75f, .05f, .60f, .50f, c(0xFF5B6273), .65f), Radial(.10f, .30f, .50f, .50f, c(0xFF3A3F4C), .70f)),
            listOf(0f to c(0xFF2A2D35), .55f to c(0xFF16181D), 1f to c(0xFF0B0C0F)),
        ),
    )

    const val PHOTO = "photo"

    fun spec(id: String) = all.firstOrNull { it.id == id } ?: all.first()

    fun photoFile(context: Context) = File(context.filesDir, "wallpaper.jpg")

    fun renderSpec(spec: WallpaperSpec, w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.DITHER_FLAG)
        paint.shader = LinearGradient(
            0f, 0f, 0f, h.toFloat(),
            spec.stops.map { it.second }.toIntArray(),
            spec.stops.map { it.first }.toFloatArray(),
            Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        // En CSS la primera capa va encima: se dibujan en orden inverso
        for (r in spec.radials.reversed()) {
            val cx = r.cx * w
            val cy = r.cy * h
            val rx = max(1f, r.rx * w)
            val ry = max(1f, r.ry * h)
            val transparent = r.color and 0x00FFFFFF
            val g = RadialGradient(cx, cy, rx, intArrayOf(r.color, transparent), floatArrayOf(0f, r.stop), Shader.TileMode.CLAMP)
            g.setLocalMatrix(Matrix().apply { setScale(1f, ry / rx, cx, cy) })
            paint.shader = g
            canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
        }
        return bmp
    }

    private fun loadPhoto(context: Context, w: Int, h: Int): Bitmap? {
        val f = photoFile(context)
        if (!f.exists()) return null
        return try {
            val src = BitmapFactory.decodeFile(f.absolutePath) ?: return null
            centerCrop(src, w, h)
        } catch (e: Exception) {
            null
        }
    }

    private fun centerCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val scale = max(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (w / scale).roundToInt()
        val sh = (h / scale).roundToInt()
        val sx = (src.width - sw) / 2
        val sy = (src.height - sh) / 2
        Canvas(out).drawBitmap(src, Rect(sx, sy, sx + sw, sy + sh), Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    fun render(context: Context, id: String, w: Int, h: Int): WallpaperImages {
        val full = (if (id == PHOTO) loadPhoto(context, w, h) else null) ?: renderSpec(spec(id), w, h)
        return WallpaperImages(full.asImageBitmap(), blur(full).asImageBitmap())
    }

    /** Copia de baja resolución, desenfocada y con más saturación (efecto cristal). */
    fun blur(src: Bitmap): Bitmap {
        val bw = max(8, src.width / 10)
        val bh = max(8, src.height / 10)
        val small = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val p = Paint(Paint.FILTER_BITMAP_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.6f) })
        }
        Canvas(small).drawBitmap(src, Rect(0, 0, src.width, src.height), Rect(0, 0, bw, bh), p)
        val px = IntArray(bw * bh)
        small.getPixels(px, 0, bw, 0, 0, bw, bh)
        repeat(3) {
            boxBlur(px, bw, bh, 3, horizontal = true)
            boxBlur(px, bw, bh, 3, horizontal = false)
        }
        small.setPixels(px, 0, bw, 0, 0, bw, bh)
        return small
    }

    private fun boxBlur(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val out = IntArray(px.size)
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (line in 0 until lines) {
            for (i in 0 until len) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val idx = if (horizontal) line * w + j else j * w + line
                    val c = px[idx]
                    a += (c ushr 24) and 0xFF
                    rr += (c shr 16) and 0xFF
                    g += (c shr 8) and 0xFF
                    b += c and 0xFF
                    n++
                }
                val idx = if (horizontal) line * w + i else i * w + line
                out[idx] = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
            }
        }
        System.arraycopy(out, 0, px, 0, px.size)
    }

    /** Guarda una foto elegida por el usuario como fondo. */
    fun savePhoto(context: Context, uri: Uri): Boolean = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 2560 || opts.outHeight / sample > 2560) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp == null) {
            false
        } else {
            photoFile(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 92, it) }
            true
        }
    } catch (e: Exception) {
        false
    }
}
