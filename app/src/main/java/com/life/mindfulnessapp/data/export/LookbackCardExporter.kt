package com.life.mindfulnessapp.data.export

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import com.life.mindfulnessapp.domain.model.formatWeekRangeLabel
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * 周回望卡片：把已消化的觉察存成图片，而不是会话流水。
 */
object LookbackCardExporter {

    const val MIME_TYPE = "image/png"

    fun suggestedFileName(weekStartMs: Long, weekEndMs: Long): String {
        val range = formatWeekRangeLabel(weekStartMs, weekEndMs).replace(" ", "")
        return "心锚_周回望_$range.png"
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

    fun writeToUri(context: Context, uri: Uri, png: ByteArray) {
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(png)
            out.flush()
        } ?: error("无法写入所选位置")
    }

    fun sharePng(context: Context, file: File, subject: String) {
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
            Intent.createChooser(share, "留下这一周").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}
