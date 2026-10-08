package com.life.mindfulnessapp.data.analytics

/**
 * 意见反馈分类：使用问题 vs 产品想法。
 * [apiValue] 与后台 `ha_feedback.category` 对齐。
 */
enum class FeedbackCategory(
    val apiValue: String,
    val label: String,
    val hint: String,
    val successTitle: String,
    val successMessage: String,
) {
    PROBLEM(
        apiValue = "problem",
        label = "遇到问题",
        hint = "拦截异常、权限卡住、文案对不上…",
        successTitle = "收到了",
        successMessage = "我们会尽快看。若你留了联系方式，必要时会再找你确认细节。",
    ),
    IDEA(
        apiValue = "idea",
        label = "想法建议",
        hint = "想怎么更好用、少打扰、更贴心…",
        successTitle = "谢谢你的想法",
        successMessage = "每一条都会认真看。不一定逐条回复，但会记进产品方向。",
    );

    companion object {
        fun fromApi(value: String?): FeedbackCategory? =
            entries.find { it.apiValue == value }
    }
}
