package com.life.mindfulnessapp.domain.model

/**
 * 旧版外观模式（日间 / 夜间 / 跟随系统）。
 * 新逻辑见 [ThemePack]；保留枚举供迁移与上报兼容。
 */
enum class ThemeMode {
    Light,
    Dark,
    System;

    fun resolveIsDark(systemDark: Boolean): Boolean = when (this) {
        Light -> false
        Dark -> true
        System -> systemDark
    }

    val storageValue: String
        get() = when (this) {
            Light -> STORAGE_LIGHT
            Dark -> STORAGE_DARK
            System -> STORAGE_SYSTEM
        }

    companion object {
        const val STORAGE_LIGHT = "light"
        const val STORAGE_DARK = "dark"
        const val STORAGE_SYSTEM = "system"

        fun fromStorage(value: String?): ThemeMode = when (value) {
            STORAGE_LIGHT -> Light
            STORAGE_SYSTEM -> System
            else -> Dark
        }

        fun fromPack(pack: ThemePack, followSystem: Boolean): ThemeMode = when {
            followSystem && pack.allowsFollowSystem -> System
            pack == ThemePack.Day || pack == ThemePack.Mist -> Light
            else -> Dark
        }
    }
}
