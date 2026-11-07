package com.glass.launcher.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.glass.launcher.R
import java.io.File
import java.util.Calendar

/** Foto del vehículo (la del usuario o el Aveo 2007 por defecto) y el saludo según la hora. */
object Vehicle {

    fun photoFile(context: Context) = File(context.filesDir, "vehicle.png")

    /** Carga la foto del usuario si existe; si no, la imagen por defecto. */
    fun load(context: Context): Bitmap? = try {
        val f = photoFile(context)
        (if (f.exists()) BitmapFactory.decodeFile(f.absolutePath) else null)
            ?: BitmapFactory.decodeResource(context.resources, R.drawable.vehicle_default)
    } catch (e: Exception) {
        null
    }

    /** Guarda la imagen elegida como PNG (conserva la transparencia). */
    fun savePhoto(context: Context, uri: Uri): Boolean = try {
        val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        var sample = 1
        while (opts.outWidth / sample > 1600 || opts.outHeight / sample > 1600) sample *= 2
        val bmp = context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
        if (bmp == null) {
            false
        } else {
            photoFile(context).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            true
        }
    } catch (e: Exception) {
        false
    }

    /** Buenos días 5:00–11:59 · Buenas tardes 12:00–18:59 · Buenas noches 19:00–4:59 */
    fun greeting(cal: Calendar = Calendar.getInstance()): String = when (cal.get(Calendar.HOUR_OF_DAY)) {
        in 5..11 -> "Buenos días"
        in 12..18 -> "Buenas tardes"
        else -> "Buenas noches"
    }
}
