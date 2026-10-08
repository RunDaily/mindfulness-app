package com.life.mindfulnessapp.domain.model

/**
 * 外观气质套：夜锚 / 日间 / 雾青。
 *
 * [followSystem] 只在日间 ↔ 夜锚之间切换；选雾青为刻意锁定。
 * 每套绑定色板与胶囊壳透明度。
 */
enum class ThemePack(
    val storageKey: String,
    val title: String,
    val subtitle: String,
    val shellOpacity: Float,
) {
    Night("night", "夜锚", "沉浸", 0.82f),
    Day("day", "日间", "清晰", 0.88f),
    Mist("mist", "雾青", "轻存在", 0.70f);

    val isDark: Boolean get() = this == Night

    /** 雾青为刻意选择，不参与跟随系统。 */
    val allowsFollowSystem: Boolean get() = this != Mist

    companion object {
        fun fromStorage(raw: String?): ThemePack = when (raw) {
            Day.storageKey, ThemeMode.STORAGE_LIGHT -> Day
            Mist.storageKey -> Mist
            else -> Night
        }

        /**
         * @param preferred 用户选中的气质（跟随开启时仅日/夜会被系统覆盖）
         * @param followSystem 是否跟随系统明暗
         * @param systemDark 系统是否深色
         */
        fun resolve(
            preferred: ThemePack,
            followSystem: Boolean,
            systemDark: Boolean,
        ): ThemePack {
            if (preferred == Mist || !followSystem) return preferred
            return if (systemDark) Night else Day
        }
    }
}
