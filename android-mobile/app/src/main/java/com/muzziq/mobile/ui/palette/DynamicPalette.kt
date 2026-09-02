package com.muzziq.mobile.ui.palette

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil.ImageLoader
import coil.request.ImageRequest
import com.muzziq.mobile.ui.theme.MuzziQColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Hero header dynamique par pochette (§56.1) : dégradé extrait de l'image
 * dominante, calculé une fois par pochette. Pas de cache disque dédié en V1
 * (Coil met déjà l'image bitmap en cache mémoire/disque) — limite acceptable,
 * le recalcul Palette reste rapide (downsample interne à la lib).
 */
@Composable
fun rememberDominantColor(artworkUrl: String?): Color {
    var color by remember(artworkUrl) { mutableStateOf(MuzziQColors.Surface) }
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(artworkUrl) {
        if (artworkUrl.isNullOrBlank()) { color = MuzziQColors.Surface; return@LaunchedEffect }
        val extracted = withContext(Dispatchers.IO) {
            runCatching {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .size(128, 128)
                    .build()
                val result = loader.execute(request).drawable
                val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap ?: return@runCatching null
                extractDominant(bitmap)
            }.getOrNull()
        }
        color = extracted ?: MuzziQColors.Surface
    }
    return color
}

private fun extractDominant(bitmap: Bitmap): Color {
    val palette = Palette.from(bitmap).generate()
    val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
    return swatch?.let { Color(it.rgb) } ?: MuzziQColors.Surface
}
