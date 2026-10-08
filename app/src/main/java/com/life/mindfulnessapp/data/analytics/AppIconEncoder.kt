package com.life.mindfulnessapp.data.analytics

import android.content.Context
import android.graphics.Bitmap
import android.util.Base64
import androidx.core.graphics.drawable.toBitmap
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

/**
 * 把本机应用图标压成小图，供管理台展示。
 * 不上报到每条埋点（太大）；走独立图标接口，按包名去重。
 */
object AppIconEncoder {
    private const val SIZE_PX = 48
    private const val MAX_BYTES = 48_000

    data class Encoded(
        val base64: String,
        val hash: String,
        val appName: String
    )

    fun encode(context: Context, packageName: String): Encoded? {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return null
        return runCatching {
            val pm = context.packageManager
            val info = pm.getApplicationInfo(pkg, 0)
            val appName = pm.getApplicationLabel(info).toString().trim()
            val drawable = pm.getApplicationIcon(info)
            val bitmap = drawable.toBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
            try {
                val png = compress(bitmap, Bitmap.CompressFormat.PNG, 100)
                    ?: return@runCatching null
                val bytes = if (png.size <= MAX_BYTES) {
                    png
                } else {
                    compress(bitmap, Bitmap.CompressFormat.JPEG, 82)
                        ?.takeIf { it.isNotEmpty() && it.size <= MAX_BYTES }
                        ?: return@runCatching null
                }
                Encoded(
                    base64 = Base64.encodeToString(bytes, Base64.NO_WRAP),
                    hash = sha256(bytes),
                    appName = appName.take(40)
                )
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }.getOrNull()
    }

    private fun compress(bitmap: Bitmap, format: Bitmap.CompressFormat, quality: Int): ByteArray? {
        val out = ByteArrayOutputStream()
        if (!bitmap.compress(format, quality, out)) return null
        val bytes = out.toByteArray()
        return bytes.takeIf { it.isNotEmpty() }
    }

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }
}
