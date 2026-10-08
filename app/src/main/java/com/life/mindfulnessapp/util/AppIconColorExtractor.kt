package com.life.mindfulnessapp.util

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.ColorUtils
import kotlin.math.max

/**
 * 从 App 图标取主色：缩放采样后按饱和度加权，得到适合色块填充的色调。
 */
object AppIconColorExtractor {

    private val fallbackPalette = listOf(
        Color(0xFF26BB68),
        Color(0xFF5B8FAD),
        Color(0xFFCE8555),
        Color(0xFF7B6BB0),
        Color(0xFFD4A017),
        Color(0xFF4A9B8C)
    )

    fun extract(drawable: Drawable?, seed: Int = 0): Color {
        if (drawable == null) return fallbackPalette[seed.floorMod(fallbackPalette.size)]
        val bitmap = drawableToBitmap(drawable, size = 48) ?: return fallbackPalette[seed.floorMod(fallbackPalette.size)]
        return try {
            sampleDominant(bitmap) ?: fallbackPalette[seed.floorMod(fallbackPalette.size)]
        } finally {
            if (bitmap !== (drawable as? BitmapDrawable)?.bitmap) {
                bitmap.recycle()
            }
        }
    }

    private fun drawableToBitmap(drawable: Drawable, size: Int): Bitmap? {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return Bitmap.createScaledBitmap(drawable.bitmap, size, size, true)
        }
        return try {
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bmp
        } catch (_: Exception) {
            null
        }
    }

    private fun sampleDominant(bitmap: Bitmap): Color? {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null
        val step = max(1, minOf(w, h) / 12)
        var bestScore = 0f
        var bestH = 0f
        var bestS = 0f
        var bestL = 0.45f
        val hsl = FloatArray(3)
        var y = step / 2
        while (y < h) {
            var x = step / 2
            while (x < w) {
                val px = bitmap.getPixel(x, y)
                val a = (px ushr 24) and 0xFF
                if (a < 160) {
                    x += step
                    continue
                }
                ColorUtils.colorToHSL(px, hsl)
                val s = hsl[1]
                val l = hsl[2]
                // 跳过近灰、过亮、过暗
                if (s < 0.12f || l < 0.12f || l > 0.88f) {
                    x += step
                    continue
                }
                val score = s * (1f - kotlin.math.abs(l - 0.45f))
                if (score > bestScore) {
                    bestScore = score
                    bestH = hsl[0]
                    bestS = s.coerceIn(0.35f, 0.78f)
                    bestL = l.coerceIn(0.38f, 0.58f)
                }
                x += step
            }
            y += step
        }
        if (bestScore <= 0f) return null
        val out = FloatArray(3)
        out[0] = bestH
        out[1] = bestS
        out[2] = bestL
        return Color(ColorUtils.HSLToColor(out))
    }

    private fun Int.floorMod(m: Int): Int {
        val r = this % m
        return if (r < 0) r + m else r
    }
}
