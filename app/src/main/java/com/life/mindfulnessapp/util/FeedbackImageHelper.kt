package com.life.mindfulnessapp.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 * 反馈截图：压缩、落盘、转 base64，供提交与本机预览。
 *
 * 管理台约定：
 * - 提交体 `images` 为 JPEG base64 字符串数组（无 data: 前缀）
 * - 最多 [MAX_IMAGES] 张；单边最长约 1280px
 * - 回复列表可回传 `images`（公网 URL 或仍为 base64，客户端优先展示本机缓存）
 */
object FeedbackImageHelper {
    const val MAX_IMAGES = 3
    private const val MAX_EDGE_PX = 1280
    private const val JPEG_QUALITY = 82
    private const val DIR_NAME = "feedback_images"

    data class PreparedImage(
        val localPath: String,
        /** 纯 base64 JPEG，不含 data URI 前缀 */
        val base64Jpeg: String
    )

    fun prepareForUpload(context: Context, uris: List<Uri>): List<PreparedImage> {
        if (uris.isEmpty()) return emptyList()
        val dir = imageDir(context).also { it.mkdirs() }
        return uris.take(MAX_IMAGES).mapNotNull { uri ->
            runCatching { prepareOne(context, uri, dir) }.getOrNull()
        }
    }

    fun deleteLocal(paths: List<String>) {
        paths.forEach { path ->
            runCatching { File(path).takeIf { it.exists() }?.delete() }
        }
    }

    private fun prepareOne(context: Context, uri: Uri, dir: File): PreparedImage? {
        val raw = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        if (raw.isEmpty()) return null

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val sample = sampleSize(bounds.outWidth, bounds.outHeight, MAX_EDGE_PX)
        val decoded = BitmapFactory.decodeByteArray(
            raw, 0, raw.size,
            BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: return null

        val scaled = scaleToMaxEdge(decoded, MAX_EDGE_PX)
        if (scaled !== decoded) decoded.recycle()

        val jpeg = ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            out.toByteArray()
        }
        scaled.recycle()

        val file = File(
            dir,
            "fb_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(8)}.jpg"
        )
        FileOutputStream(file).use { it.write(jpeg) }
        return PreparedImage(
            localPath = file.absolutePath,
            base64Jpeg = Base64.encodeToString(jpeg, Base64.NO_WRAP)
        )
    }

    private fun imageDir(context: Context): File =
        File(context.filesDir, DIR_NAME)

    private fun sampleSize(width: Int, height: Int, maxEdge: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= maxEdge || h / 2 >= maxEdge) {
            sample *= 2
            w /= 2
            h /= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun scaleToMaxEdge(bitmap: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = maxOf(bitmap.width, bitmap.height)
        if (longEdge <= maxEdge) return bitmap
        val scale = maxEdge.toFloat() / longEdge
        val w = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }
}
