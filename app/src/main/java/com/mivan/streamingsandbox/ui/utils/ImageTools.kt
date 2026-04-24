package com.mivan.streamingsandbox.ui.utils

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
        val dominant = Palette.from(bitmap)
            .clearFilters()
            .generate()
            .getDominantColor(fallbackColor.toArgb())
        Color(dominant).copy(alpha = alpha)
    }.getOrElse { fallbackColor }
}