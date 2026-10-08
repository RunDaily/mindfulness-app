package com.life.mindfulnessapp.domain.model

/**
 * 觉察练习：发现里认领的可选能力。
 * 总闸同时管中途轻问与离开对照；节奏只管中途。
 */
enum class AwarenessPracticeRhythm {
    /** 每 10 分钟 */
    Sparse,
    /** 每 5 分钟（接近旧默认） */
    Normal;

    val gapSec: Long
        get() = when (this) {
            Sparse -> 10 * 60L
            Normal -> 5 * 60L
        }

    val label: String
        get() = when (this) {
            Sparse -> "疏"
            Normal -> "常"
        }

    val detail: String
        get() = when (this) {
            Sparse -> "每 10 分钟"
            Normal -> "每 5 分钟"
        }

    val storageKey: String
        get() = when (this) {
            Sparse -> STORAGE_SPARSE
            Normal -> STORAGE_NORMAL
        }

    companion object {
        const val STORAGE_SPARSE = "sparse"
        const val STORAGE_NORMAL = "normal"

        val DEFAULT: AwarenessPracticeRhythm = Normal

        fun fromStorage(raw: String?): AwarenessPracticeRhythm = when (raw) {
            STORAGE_SPARSE -> Sparse
            else -> Normal
        }
    }
}

object AwarenessPracticeCopy {
    /** 发现目录右侧状态 */
    fun glanceMeta(enabled: Boolean, rhythm: AwarenessPracticeRhythm): String =
        if (!enabled) "关" else "${rhythm.label} · ${rhythm.detail}"
}
