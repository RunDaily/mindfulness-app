package com.life.mindfulnessapp.domain.model

/**
 * 门上正向出口：离开 distraction，去做另一件事。
 * 写入 usage_records 时 [endReason] = GATE_POSITIVE_EXIT，
 * [intentKind] 存 [storageValue]，[purpose] 为标题。
 */
enum class PositiveExitKind {
    THING,
    PLAN,
    PLACE;

    val storageValue: String
        get() = when (this) {
            THING -> "POSITIVE_THING"
            PLAN -> "POSITIVE_PLAN"
            PLACE -> "POSITIVE_PLACE"
        }

    val kindLabel: String
        get() = when (this) {
            THING -> "此刻可做"
            PLAN -> "计划"
            PLACE -> "地方"
        }

    companion object {
        fun fromStorage(value: String?): PositiveExitKind? = when (value) {
            "POSITIVE_THING" -> THING
            "POSITIVE_PLAN" -> PLAN
            "POSITIVE_PLACE" -> PLACE
            else -> null
        }
    }
}

data class PositiveExitChoice(
    val kind: PositiveExitKind,
    val title: String,
    val why: String? = null,
    val launchPackageName: String? = null
)
