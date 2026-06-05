package com.cloudmelody.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.ColorUtils
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a dominant color from an album cover for dynamic theming.
 * Uses simple frequency-sampling approach to stay lightweight.
 */
object ColorExtractor {

    suspend fun getDominantColor(context: Context, imageUrl: String?): Int {
        if (imageUrl.isNullOrBlank()) return Color.parseColor("#1a1a2e")
        return withContext(Dispatchers.IO) {
            runCatching {
                val loader = ImageLoader(context)
                val req = ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build()
                val result = (loader.execute(req) as? SuccessResult)?.drawable
                val bitmap = (result as? BitmapDrawable)?.bitmap
                    ?: return@withContext Color.parseColor("#1a1a2e")
                dominant(bitmap)
            }.getOrDefault(Color.parseColor("#1a1a2e"))
        }
    }

    private fun dominant(bmp: Bitmap): Int {
        val scaled = Bitmap.createScaledBitmap(bmp, 24, 24, true)
        val colorCount = mutableMapOf<Int, Int>()
        for (x in 0 until scaled.width) {
            for (y in 0 until scaled.height) {
                val pixel = scaled.getPixel(x, y)
                // Quantize to reduce noise
                val r = (Color.red(pixel) / 32) * 32
                val g = (Color.green(pixel) / 32) * 32
                val b = (Color.blue(pixel) / 32) * 32
                val q = Color.rgb(r, g, b)
                colorCount[q] = (colorCount[q] ?: 0) + 1
            }
        }
        val dominant = colorCount.maxByOrNull { it.value }?.key
            ?: Color.parseColor("#1a1a2e")
        // Darken slightly for backgrounds
        return ColorUtils.blendARGB(dominant, Color.BLACK, 0.4f)
    }
}
