package com.life.mindfulnessapp.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.Typeface
import android.util.DisplayMetrics
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 静壁纸绘制：竖向柔渐变 + 中央主文（可换行）+ 可选作者 + 底部极淡「心锚」。
 */
object WallpaperRenderer {

    private const val MaxWidthPx = 1440
    private const val MaxHeightPx = 3200
    private const val MaxLines = 4

    fun targetSize(metrics: DisplayMetrics): Pair<Int, Int> {
        val rawW = metrics.widthPixels.coerceAtLeast(720)
        val rawH = metrics.heightPixels.coerceAtLeast(1280)
        val scale = min(1f, min(MaxWidthPx / rawW.toFloat(), MaxHeightPx / rawH.toFloat()))
        return (rawW * scale).roundToInt() to (rawH * scale).roundToInt()
    }

    fun render(
        width: Int,
        height: Int,
        template: WallpaperTemplate,
        line: String,
        author: String = ""
    ): Bitmap {
        val w = width.coerceAtLeast(1)
        val h = height.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                template.topArgb,
                template.bottomArgb,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), bgPaint)

        val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = template.textArgb
            alpha = 8
            strokeWidth = (w * 0.0012f).coerceAtLeast(1f)
        }
        val step = h / 28f
        var y = step
        while (y < h) {
            canvas.drawLine(0f, y, w.toFloat(), y, grainPaint)
            y += step
        }

        val main = line.trim().ifBlank { WallpaperLines.default }
        val authorClean = author.trim().removePrefix("—").trim()
        val maxTextWidth = w * 0.78f

        val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = template.textArgb
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            isFakeBoldText = main.length <= 4 && !main.contains('\n')
        }

        var textSize = when {
            main.length <= 6 -> h * 0.048f
            main.length <= 16 -> h * 0.040f
            main.length <= 32 -> h * 0.034f
            else -> h * 0.030f
        }
        var lines: List<String>
        do {
            mainPaint.textSize = textSize
            lines = wrapText(main, mainPaint, maxTextWidth, MaxLines)
            val widest = lines.maxOfOrNull { mainPaint.measureText(it) } ?: 0f
            if (widest <= maxTextWidth || textSize <= h * 0.022f) break
            textSize *= 0.92f
        } while (true)

        val lineHeight = mainPaint.fontSpacing
        val blockHeight = lineHeight * lines.size
        val blockTop = h * 0.42f - blockHeight / 2f
        lines.forEachIndexed { index, row ->
            val baseline = blockTop + lineHeight * (index + 1) - mainPaint.descent()
            canvas.drawText(row, w / 2f, baseline, mainPaint)
        }

        if (authorClean.isNotEmpty()) {
            val authorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = template.textArgb
                alpha = 140
                textAlign = Paint.Align.CENTER
                textSize = h * 0.018f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            }
            val authorY = blockTop + blockHeight + h * 0.035f
            canvas.drawText("— $authorClean", w / 2f, authorY, authorPaint)
        }

        val brandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = template.brandArgb
            textAlign = Paint.Align.CENTER
            textSize = h * 0.018f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            letterSpacing = 0.12f
        }
        canvas.drawText("心锚", w / 2f, h * 0.88f, brandPaint)

        return bitmap
    }

    private fun wrapText(
        text: String,
        paint: Paint,
        maxWidth: Float,
        maxLines: Int
    ): List<String> {
        if (text.isEmpty()) return listOf(WallpaperLines.default)
        val words = text.toList()
        val result = mutableListOf<String>()
        var current = StringBuilder()
        for (ch in words) {
            val candidate = current.toString() + ch
            if (paint.measureText(candidate) <= maxWidth || current.isEmpty()) {
                current.append(ch)
            } else {
                result += current.toString()
                if (result.size >= maxLines) {
                    return finalizeOverflow(result, maxLines, paint, maxWidth)
                }
                current = StringBuilder().append(ch)
            }
        }
        if (current.isNotEmpty()) {
            result += current.toString()
        }
        return if (result.size > maxLines) {
            finalizeOverflow(result, maxLines, paint, maxWidth)
        } else {
            result.ifEmpty { listOf(text) }
        }
    }

    private fun finalizeOverflow(
        lines: List<String>,
        maxLines: Int,
        paint: Paint,
        maxWidth: Float
    ): List<String> {
        val kept = lines.take(maxLines).toMutableList()
        var last = kept.last()
        val ellipsis = "…"
        while (last.length > 1 && paint.measureText(last + ellipsis) > maxWidth) {
            last = last.dropLast(1)
        }
        kept[kept.lastIndex] = last + ellipsis
        return kept
    }
}
