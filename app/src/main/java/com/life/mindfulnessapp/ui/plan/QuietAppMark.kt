package com.life.mindfulnessapp.ui.plan

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

private val QuietMarkFilter = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.238f, 0.801f, 0.081f, 0f, 12f,
            0.238f, 0.801f, 0.081f, 0f, 12f,
            0.238f, 0.801f, 0.081f, 0f, 12f,
            0f, 0f, 0f, 0.74f, 0f
        )
    )
)

private val QuietMarkBitmaps = object {
    private val cache = LruCache<String, ImageBitmap>(64)
    fun get(packageName: String, context: Context): ImageBitmap? {
        cache.get(packageName)?.let { return it }
        val image = runCatching {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(64, 64)
                .asImageBitmap()
        }.getOrNull() ?: return null
        cache.put(packageName, image)
        return image
    }
}

/** 名册用的小灰标：去饱和、略提亮、半透明。拦截门仍用彩色原标。 */
@Composable
fun QuietAppMark(packageName: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val bitmap = remember(packageName) { QuietMarkBitmaps.get(packageName, context) }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            colorFilter = QuietMarkFilter,
            modifier = modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
        )
    } else {
        Box(
            modifier
                .size(22.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )
    }
}
