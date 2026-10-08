package com.life.mindfulnessapp.wallpaper

/**
 * 桌面壁纸氛围模板（发现工作室 ↔ Live Wallpaper 共用）。
 */
enum class WallpaperTemplate(
    val label: String,
    val topArgb: Int,
    val bottomArgb: Int,
    val textArgb: Int,
    val brandArgb: Int
) {
    MistDay(
        label = "雾昼",
        topArgb = 0xFFF4F6F9.toInt(),
        bottomArgb = 0xFFE2E8F0.toInt(),
        textArgb = 0xFF191D2B.toInt(),
        brandArgb = 0x66191D2B
    ),
    NightShore(
        label = "夜岸",
        topArgb = 0xFF0F1117.toInt(),
        bottomArgb = 0xFF171B25.toInt(),
        textArgb = 0xFFEDF0F7.toInt(),
        brandArgb = 0x66EDF0F7
    ),
    LeafGreen(
        label = "叶绿",
        topArgb = 0xFFEAF6EF.toInt(),
        bottomArgb = 0xFFC8E6D4.toInt(),
        textArgb = 0xFF196B40.toInt(),
        brandArgb = 0x66196B40
    );

    companion object {
        val default: WallpaperTemplate = MistDay
    }
}

object WallpaperLines {
    val all: List<String> = listOf(
        "心锚",
        "少一点就好",
        "先停一下",
        "今天够了"
    )

    val default: String = all.first()
}
