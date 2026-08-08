package com.life.mindfulnessapp.data.network

// ════════════════════════════════════════════
//  VIP 产品（Google Play Billing，本地激活）
// ════════════════════════════════════════════

/**
 * Google Play 产品 ID 枚举。
 *
 * 命名规范与 Play Console 保持一致：
 *  - 订阅（Subscription）产品：productId 对应 Play Console 中配置的订阅 ID
 *  - 一次性购买（In-app）产品：productId 对应 Play Console 中配置的商品 ID
 *
 * 注意：所有价格由 Play Console 配置，代码中不硬编码任何金额。
 */
enum class VipPlan(
    val productId: String,
    /** SUBS = 订阅产品，INAPP = 一次性购买 */
    val productType: String
) {
    MONTHLY_STANDARD("standard_monthly", "subs"),
    YEARLY_STANDARD("standard_yearly", "subs"),
    YEARLY_PREMIUM("premium_yearly", "subs"),
    LIFETIME("lifetime_premium", "inapp")
}

// ════════════════════════════════════════════
//  拦截名言
// ════════════════════════════════════════════

/** 单条名言 */
data class RemoteQuote(
    val id: Int,
    val content: String,
    val author: String = "",
    val category: String = ""
)

/** 随机名言响应 */
data class QuoteRandomResponse(
    val success: Boolean,
    val data: List<RemoteQuote>? = null,
    val error: String? = null
)

/** 批量名言响应 */
data class QuoteListResponse(
    val success: Boolean,
    val total: Int = 0,
    val data: List<RemoteQuote>? = null,
    val error: String? = null
)
