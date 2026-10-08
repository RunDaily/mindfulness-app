package com.life.mindfulnessapp.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 格言卡片分享：PNG 经 FileProvider 发出。
 */
object QuoteCardExporter {

    const val MIME_TYPE = "image/png"

    fun suggestedFileName(author: String): String {
        val tag = author.trim().removePrefix("—").trim()
            .ifBlank { "格言" }
            .take(12)
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return "心锚_格言_$tag.png"
    }

    fun bitmapToPng(bitmap: Bitmap): ByteArray {
        val software = if (bitmap.config == Bitmap.Config.HARDWARE) {
            bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: bitmap
        } else {
            bitmap
        }
        val out = ByteArrayOutputStream()
        software.compress(Bitmap.CompressFormat.PNG, 100, out)
        return out.toByteArray()
    }

    fun writeToCache(context: Context, fileName: String, png: ByteArray): File {
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        dir.listFiles()?.forEach { old ->
            if (old.isFile && old.name.endsWith(".png") &&
                System.currentTimeMillis() - old.lastModified() > 24L * 60 * 60 * 1000
            ) {
                old.delete()
            }
        }
        val file = File(dir, fileName)
        file.writeBytes(png)
        return file
    }

    fun sharePng(context: Context, file: File, subject: String = "心锚 · 格言") {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val share = Intent(Intent.ACTION_SEND).apply {
            type = MIME_TYPE
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(
            Intent.createChooser(share, "分享格言").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
