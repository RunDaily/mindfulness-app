package com.life.mindfulnessapp.wallpaper

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * 壁纸样式跨进程持久化（主进程工作室 ↔ `:guardian` Live Wallpaper）。
 * 用文件而非 SharedPreferences，避免多进程缓存不一致。
 */
object WallpaperStyleStore {

    private const val FILE_NAME = "wallpaper_style.json"

    data class Style(
        val template: WallpaperTemplate = WallpaperTemplate.default,
        val line: String = WallpaperLines.default,
        val author: String = ""
    )

    fun load(context: Context): Style {
        return try {
            val file = file(context)
            if (!file.exists()) return Style()
            val json = JSONObject(file.readText())
            val template = WallpaperTemplate.entries.find {
                it.name == json.optString("template")
            } ?: WallpaperTemplate.default
            Style(
                template = template,
                line = json.optString("line").ifBlank { WallpaperLines.default },
                author = json.optString("author")
            )
        } catch (_: Exception) {
            Style()
        }
    }

    fun save(context: Context, style: Style) {
        try {
            val json = JSONObject()
                .put("template", style.template.name)
                .put("line", style.line)
                .put("author", style.author)
            file(context).writeText(json.toString())
        } catch (_: Exception) {
            // ignore
        }
    }

    fun save(
        context: Context,
        template: WallpaperTemplate,
        line: String,
        author: String
    ) {
        save(context, Style(template = template, line = line, author = author))
    }

    private fun file(context: Context): File =
        File(context.applicationContext.filesDir, FILE_NAME)
}
