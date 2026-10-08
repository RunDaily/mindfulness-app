package com.life.mindfulnessapp.domain.model

/**
 * 「有意义的事」：预置或自定义。
 * [boundPackageName] 可选：绑定 App 后，在拦截页选中会离开并打开该 App。
 */
data class MeaningfulThing(
    val id: String,
    val title: String,
    val isPreset: Boolean,
    val boundPackageName: String? = null,
) {
    val hasBoundApp: Boolean get() = !boundPackageName.isNullOrBlank()
}

object MeaningfulThingsCatalog {
    val PRESETS: List<MeaningfulThing> = listOf(
        MeaningfulThing("preset_walk", "出去走走", true),
        MeaningfulThing("preset_water", "喝杯水，歇一会儿", true),
        MeaningfulThing("preset_eyes", "做个眼保健操", true),
        MeaningfulThing("preset_stretch", "站起来拉伸一下", true),
        MeaningfulThing("preset_read", "读几页纸质书", true),
        MeaningfulThing("preset_note", "写一句今日收获", true),
        MeaningfulThing("preset_tidy", "整理一下桌面", true),
        MeaningfulThing("preset_message", "给重要的人发条消息", true),
        MeaningfulThing("preset_breathe", "闭眼深呼吸三次", true),
        MeaningfulThing("preset_dish", "去做一件家务", true),
    )

    fun customId(): String = "custom_${System.currentTimeMillis()}"
}
