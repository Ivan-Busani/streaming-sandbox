package com.mivan.streamingsandbox.ui.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun getImageBgColorFromUrl(
    context: Context,
    imageUrl: String,
    fallbackColor: Color = Color(0xFF1E1E1E),
    alpha: Float = 0.25f
): Color = withContext(Dispatchers.IO) {
    runCatching {
        val loader = ImageLoader(context)
        val request = ImageRequest.Builder(context)
            .data(imageUrl)
            .allowHardware(false) // Needed to read bitmap
            .build()
        val result = loader.execute(request) as? SuccessResult
        val bitmap = (result?.drawable as? BitmapDrawable)?.bitmap
            ?: return@runCatching fallbackColor
        // Prefer visible pixels and ignore transparent background in PNG logos.
        val stats = analyzeVisiblePixels(bitmap)
        val dominant = stats.dominantColor ?: Palette.from(bitmap)
            .clearFilters()
            .generate()
            .getDominantColor(fallbackColor.toArgb())

        val luminance = stats.meanLuminance ?: ColorUtils.calculateLuminance(dominant)
        val contrasted = when {
            luminance < 0.30 -> Color.White
            luminance > 0.72 -> Color.Black
            else -> Color(dominant)
        }
        val effectiveAlpha = if (contrasted == Color.White || contrasted == Color.Black) {
            alpha.coerceAtLeast(0.78f)
        } else {
            alpha
        }
        contrasted.copy(alpha = effectiveAlpha)
    }.getOrElse { fallbackColor }
}

private data class VisiblePixelStats(
    val dominantColor: Int?,
    val meanLuminance: Double?
)

private fun analyzeVisiblePixels(bitmap: Bitmap): VisiblePixelStats {
    val targetW = if (bitmap.width > 96) 96 else bitmap.width.coerceAtLeast(1)
    val targetH = if (bitmap.height > 96) 96 else bitmap.height.coerceAtLeast(1)
    val scaled = Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    val bins = HashMap<Int, Float>()
    val hsv = FloatArray(3)
    var luminanceAcc = 0.0
    var weightAcc = 0.0

    for (y in 0 until scaled.height) {
        for (x in 0 until scaled.width) {
            val pixel = scaled.getPixel(x, y)
            val alpha = AndroidColor.alpha(pixel)
            if (alpha < 40) continue

            val red = AndroidColor.red(pixel)
            val green = AndroidColor.green(pixel)
            val blue = AndroidColor.blue(pixel)
            AndroidColor.RGBToHSV(red, green, blue, hsv)

            val value = hsv[2]
            if (value < 0.04f) continue

            val rq = red / 24
            val gq = green / 24
            val bq = blue / 24
            val key = (rq shl 16) or (gq shl 8) or bq
            val weight = alpha / 255f
            bins[key] = (bins[key] ?: 0f) + weight

            val luminance = ColorUtils.calculateLuminance(pixel)
            luminanceAcc += luminance * weight
            weightAcc += weight
        }
    }

    val winner = bins.maxByOrNull { it.value }?.key
    val dominant = winner?.let {
        val red = ((it shr 16) and 0xFF) * 24 + 12
        val green = ((it shr 8) and 0xFF) * 24 + 12
        val blue = (it and 0xFF) * 24 + 12
        AndroidColor.rgb(
            red.coerceIn(0, 255),
            green.coerceIn(0, 255),
            blue.coerceIn(0, 255)
        )
    }
    val meanLuminance = if (weightAcc > 0.0) luminanceAcc / weightAcc else null
    return VisiblePixelStats(dominantColor = dominant, meanLuminance = meanLuminance)
}