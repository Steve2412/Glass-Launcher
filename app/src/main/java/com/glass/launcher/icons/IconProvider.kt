package com.glass.launcher.icons

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import com.glass.launcher.data.AppEntry
import com.glass.launcher.data.AppRepository

/**
 * Genera los iconos: estilo Glass (squircle + brillo), original, o de un icon pack.
 */
class IconProvider(
    private val apps: AppRepository,
    private val packs: IconPackManager,
) {
    private val cache = LruCache<String, ImageBitmap>(400)

    /** Cambia cada vez que hay que redibujar todos los iconos. */
    var version by mutableIntStateOf(0)
        private set

    @Volatile var style: String = "glass"
    @Volatile var packFrame: Boolean = true
    @Volatile var overrides: Map<String, String> = emptyMap()

    fun configure(style: String, packFrame: Boolean, overrides: Map<String, String>) {
        this.style = style
        this.packFrame = packFrame
        this.overrides = overrides
        cache.evictAll()
        version++
    }

    fun invalidate() {
        cache.evictAll()
        version++
    }

    /** Debe llamarse fuera del hilo principal. */
    fun icon(entry: AppEntry): ImageBitmap {
        val key = "${entry.key}#$style#$packFrame#${overrides[entry.key]}"
        cache.get(key)?.let { return it }
        val bmp = render(entry).asImageBitmap()
        cache.put(key, bmp)
        return bmp
    }

    /** Si el estilo actual dibuja su propia forma squircle (para la sombra). */
    fun usesSquircle(): Boolean = style == "glass" || packFrame

    private fun render(entry: AppEntry): Bitmap {
        val override = overrides[entry.key]
        var packDrawable: Drawable? = null
        if (override != null) {
            val pkg = override.substringBefore('|')
            val name = override.substringAfter('|')
            packDrawable = packs.load(pkg)?.load(name)
        }
        if (packDrawable == null && style != "glass" && style != "original") {
            val pack = packs.load(style)
            packDrawable = pack?.drawableNameFor(entry.key)?.let { pack.load(it) }
        }
        val base = packDrawable ?: apps.loadIcon(entry)
        return when {
            base == null -> glass(null)
            packDrawable != null && !packFrame -> plain(base)
            packDrawable == null && style == "original" -> plain(base)
            else -> glass(base)
        }
    }

    private fun plain(d: Drawable): Bitmap {
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        d.setBounds(0, 0, SIZE, SIZE)
        d.draw(c)
        return bmp
    }

    private fun glass(d: Drawable?): Bitmap {
        val s = SIZE.toFloat()
        val bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val path = Squircle.androidPath(s, s)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        c.save()
        c.clipPath(path)
        when {
            d == null -> {
                paint.shader = LinearGradient(0f, 0f, 0f, s, 0xFF8A93A6.toInt(), 0xFF4A5164.toInt(), Shader.TileMode.CLAMP)
                c.drawRect(0f, 0f, s, s, paint)
            }
            d is AdaptiveIconDrawable -> {
                val inset = SIZE / 4
                d.background?.let {
                    it.setBounds(-inset, -inset, SIZE + inset, SIZE + inset)
                    it.draw(c)
                }
                d.foreground?.let {
                    it.setBounds(-inset, -inset, SIZE + inset, SIZE + inset)
                    it.draw(c)
                }
            }
            else -> {
                val src = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
                d.setBounds(0, 0, SIZE, SIZE)
                d.draw(Canvas(src))
                if (isFullBleed(src)) {
                    // Icono cuadrado: se amplía un poco para cortar sus esquinas
                    val g = (SIZE * 0.04f).toInt()
                    c.drawBitmap(src, null, Rect(-g, -g, SIZE + g, SIZE + g), paint)
                } else {
                    val color = dominantColor(src)
                    val top = ColorUtils.blendARGB(color, Color.WHITE, 0.28f)
                    val bottom = ColorUtils.blendARGB(color, Color.BLACK, 0.22f)
                    paint.shader = LinearGradient(0f, 0f, 0f, s, top, bottom, Shader.TileMode.CLAMP)
                    c.drawRect(0f, 0f, s, s, paint)
                    paint.shader = null
                    val m = (SIZE * 0.17f).toInt()
                    c.drawBitmap(src, null, Rect(m, m, SIZE - m, SIZE - m), paint)
                }
                src.recycle()
            }
        }
        // Brillo superior tipo cristal
        paint.shader = LinearGradient(0f, 0f, 0f, s * 0.52f, Color.argb(92, 255, 255, 255), Color.argb(0, 255, 255, 255), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, s, s * 0.52f, paint)
        // Sombra interior inferior
        paint.shader = LinearGradient(0f, s * 0.72f, 0f, s, Color.argb(0, 0, 0, 0), Color.argb(46, 0, 0, 0), Shader.TileMode.CLAMP)
        c.drawRect(0f, s * 0.72f, s, s, paint)
        c.restore()

        // Borde de luz
        val rim = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            setStyle(Paint.Style.STROKE)
            strokeWidth = s * 0.014f
            shader = LinearGradient(0f, 0f, 0f, s, Color.argb(170, 255, 255, 255), Color.argb(30, 255, 255, 255), Shader.TileMode.CLAMP)
        }
        c.drawPath(path, rim)
        return bmp
    }

    private fun isFullBleed(b: Bitmap): Boolean {
        fun opaque(fx: Float, fy: Float): Boolean {
            val x = (fx * (b.width - 1)).toInt()
            val y = (fy * (b.height - 1)).toInt()
            return Color.alpha(b.getPixel(x, y)) > 200
        }
        val edges = opaque(0.06f, 0.5f) && opaque(0.94f, 0.5f) && opaque(0.5f, 0.06f) && opaque(0.5f, 0.94f)
        val corners = opaque(0.15f, 0.15f) && opaque(0.85f, 0.15f) && opaque(0.15f, 0.85f) && opaque(0.85f, 0.85f)
        return edges && corners
    }

    private fun dominantColor(b: Bitmap): Int = try {
        val p = Palette.from(b).clearFilters().generate()
        p.getVibrantColor(p.getDominantColor(0xFF5B6273.toInt()))
    } catch (e: Exception) {
        0xFF5B6273.toInt()
    }

    companion object {
        const val SIZE = 192

        /** Para el selector de iconos: dibuja un drawable suelto. */
        fun drawableToImage(d: Drawable): ImageBitmap {
            val bmp = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888)
            d.setBounds(0, 0, 128, 128)
            d.draw(Canvas(bmp))
            return bmp.asImageBitmap()
        }
    }
}
