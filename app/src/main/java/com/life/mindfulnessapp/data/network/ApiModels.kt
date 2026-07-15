package com.life.mindfulnessapp.data.network

import com.google.gson.annotations.SerializedName

// ════════════════════════════════════════════
//  通用响应包装
// ════════════════════════════════════════════

data class ApiResponse<T>(
    val success: Boolean,
    val message: String? = null,
    val data: T? = null,
    val error: String? = null
)

// ════════════════════════════════════════════
//  账号相关
// ════════════════════════════════════════════

data class AuthRequest(
    val phone: String,
    val password: String,
    /** 注册时可选携带邀请码，登录时传 null 即可 */
    @SerializedName("invite_code") val inviteCode: String? = null
)

data class AuthResponse(
    val success: Boolean,
    val token: String? = null,
    val user: RemoteUser? = null,
    val error: String? = null
)

data class RemoteUser(
    val id: Int,
    val username: String,
    val nickname: String,
    @SerializedName("avatar_emoji") val avatarEmoji: String
)

data class UpdateProfileRequest(
    val nickname: String? = null,
    @SerializedName("avatar_emoji") val avatarEmoji: String? = null
)

// ════════════════════════════════════════════
//  使用会话同步
// ════════════════════════════════════════════

data class RemoteSession(
    @SerializedName("session_id")    val sessionId: String,
    @SerializedName("bundle_id")     val bundleId: String,
    @SerializedName("app_name")      val appName: String,
    val intent: String = "",
    @SerializedName("start_time")    val startTime: Long,
    @SerializedName("end_time")      val endTime: Long? = null,
    @SerializedName("duration_ms")   val durationMs: Long = 0,
    @SerializedName("was_intercepted") val wasIntercepted: Int = 1,
    val date: String
)

data class SyncSessionsRequest(
    val sessions: List<RemoteSession>
)

data class SyncResponse(
    val success: Boolean,
    val synced: Int = 0,
    val error: String? = null
)

// ════════════════════════════════════════════
//  被监控 App 同步
// ════════════════════════════════════════════

data class RemoteMonitoredApp(
    @SerializedName("bundle_id")  val bundleId: String,
    @SerializedName("app_name")   val appName: String,
    @SerializedName("app_icon")   val appIcon: String = "",
    @SerializedName("is_enabled") val isEnabled: Int = 1
)

data class SyncMonitoredAppsRequest(
    val apps: List<RemoteMonitoredApp>
)

// ════════════════════════════════════════════
//  VIP 相关
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
    MONTHLY_STANDARD("standard_monthly",  "subs"),
    YEARLY_STANDARD( "standard_yearly",   "subs"),
    YEARLY_PREMIUM(  "premium_yearly",    "subs"),
    LIFETIME(        "lifetime_premium",  "inapp")
}

/**
 * Google Play 购买验证请求。
 * 客户端拿到 Google Play 的购买凭证后发给自己的服务端做二次校验。
 */
data class VipPurchaseRequest(
    /** Google Play 返回的 purchaseToken */
    @SerializedName("purchase_token") val purchaseToken: String,
    /** Play Console 中配置的产品 ID */
    @SerializedName("product_id")     val productId: String,
    /** 产品类型："subs" 或 "inapp" */
    @SerializedName("product_type")   val productType: String
)

/** VIP 状态响应 */
data class VipStatusResponse(
    val success: Boolean,
    @SerializedName("vip_level")       val vipLevel: Int = 0,
    @SerializedName("expire_time")     val expireTime: Long = 0L,  // 0 = 永久
    @SerializedName("product_id")      val productId: String = "",
    @SerializedName("trial_available") val trialAvailable: Boolean = false,
    val error: String? = null
)

/** 激活试用请求（保留，供服务端记录） */
data class ActivateTrialRequest(
    @SerializedName("user_id") val userId: Int
)

// ════════════════════════════════════════════
//  邀请码相关
// ════════════════════════════════════════════

/** 使用（兑换）邀请码请求 */
data class RedeemInviteRequest(
    @SerializedName("invite_code") val inviteCode: String
)

/** 邀请码信息响应 */
data class InviteInfoResponse(
    val success: Boolean,
    /** 当前用户自己的邀请码 */
    @SerializedName("invite_code")     val inviteCode: String = "",
    /** 已成功邀请的人数 */
    @SerializedName("invited_count")   val invitedCount: Int = 0,
    /** 邀请奖励描述（服务端生成，如"已获得 14 天 VIP 延期"） */
    @SerializedName("reward_summary")  val rewardSummary: String = "",
    val error: String? = null
)

/** 兑换邀请码响应 */
data class RedeemInviteResponse(
    val success: Boolean,
    /** 兑换成功后的奖励描述，如"试用期延长至 14 天" */
    @SerializedName("reward_message")  val rewardMessage: String = "",
    /** 兑换后更新的 VIP 级别（服务端顺带返回最新状态） */
    @SerializedName("vip_level")       val vipLevel: Int = 0,
    @SerializedName("expire_time")     val expireTime: Long = 0L,
    val error: String? = null
)

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

// ════════════════════════════════════════════
//  激活码（一次性兑换码开通会员）
// ════════════════════════════════════════════

/** 激活码兑换请求 */
data class ActivationCodeRequest(
    @SerializedName("activation_code") val activationCode: String
)

/** 激活码兑换响应 */
data class ActivationCodeResponse(
    val success: Boolean,
    /** 兑换成功后的奖励描述，如"已开通 1 个月标准版会员" */
    @SerializedName("reward_message")  val rewardMessage: String = "",
    /** 激活后的 VIP 级别 */
    @SerializedName("vip_level")       val vipLevel: Int = 0,
    /** 激活后的过期时间戳（毫秒），0 = 永久 */
    @SerializedName("expire_time")     val expireTime: Long = 0L,
    val error: String? = null
)
