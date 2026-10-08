package com.life.mindfulnessapp.data.network

// ════════════════════════════════════════════
//  VIP 产品（Google Play Billing，本地激活）
// ════════════════════════════════════════════

/**
 * 会员方案（官网渠道展示标价；Play 渠道以 Console 配置为准）。
 *
 * 当前产品只区分免费 / 会员一层：会员 = 无限 App 管理坑位 + 持续更新的更多能力。
 */
enum class VipPlan(
    val productId: String,
    /** SUBS = 订阅产品，INAPP = 一次性购买 */
    val productType: String,
    val titleZh: String,
    /** 官网标价文案（人民币） */
    val listPriceCny: String,
    /** 开通天数；0 = 永久 */
    val durationDays: Int
) {
    MONTHLY("vip_monthly", "subs", "月卡", "¥12", 30),
    QUARTERLY("vip_quarterly", "subs", "季卡", "¥28", 90),
    YEARLY("vip_yearly", "subs", "年卡", "¥48", 365),
    LIFETIME("vip_lifetime", "inapp", "永久", "¥128", 0)
}

// ════════════════════════════════════════════
//  拦截名言
// ════════════════════════════════════════════

/** 单条名言 */
data class RemoteQuote(
    val id: Int,
    val content: String,
    val author: String = "",
    val category: String = "",
    val author_id: Int = 0
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

/** 名人目录项（不含名言正文） */
data class RemoteAuthor(
    val id: Int,
    val name: String,
    val bio: String = "",
    val category: String = "",
    val quote_count: Int = 0,
    val subscribed: Int = 0,
    val content_updated_at: String? = null,
    val subscribed_at: String? = null
)

data class AuthorListResponse(
    val success: Boolean,
    val data: List<RemoteAuthor>? = null,
    val error: String? = null
)

data class AuthorCategoryCount(
    val category: String,
    val count: Int = 0
)

data class AuthorCategoriesResponse(
    val success: Boolean,
    val data: List<AuthorCategoryCount>? = null,
    val error: String? = null
)

data class AuthorDeviceRequest(
    val device_id: String,
    val app_version: String = "",
    val device_model: String = ""
)

data class AuthorSubscribeResponse(
    val success: Boolean,
    val subscribed: Boolean = false,
    val error: String? = null
)

data class AuthorRequestBody(
    val device_id: String,
    val name: String,
    val note: String = "",
    val app_version: String = "",
    val device_model: String = ""
)

data class AuthorRequestResult(
    val success: Boolean,
    val id: Int? = null,
    val already_exists: Boolean = false,
    val already_requested: Boolean = false,
    val author_id: Int? = null,
    val message: String? = null,
    val error: String? = null
)

data class RemoteAuthorRequest(
    val id: Int,
    val name: String,
    val note: String = "",
    val status: String = "pending",
    val fulfilled_author_id: Int = 0,
    val created_at: String? = null,
    val resolved_at: String? = null
)

data class AuthorRequestsResponse(
    val success: Boolean,
    val data: List<RemoteAuthorRequest>? = null,
    val error: String? = null
)

data class RemoteAuthorNotice(
    val id: Int,
    val author_id: Int,
    val author_name: String,
    val message: String = "",
    val created_at: String? = null,
    val read_at: String? = null
)

data class AuthorNoticesResponse(
    val success: Boolean,
    val data: List<RemoteAuthorNotice>? = null,
    val error: String? = null
)

data class AuthorNoticesReadBody(
    val device_id: String,
    val ids: List<Int>? = null
)

data class SimpleOkResponse(
    val success: Boolean,
    val error: String? = null
)

// ════════════════════════════════════════════
//  会员支付（设备级 · 优先微信 APP 支付 SDK）
// ════════════════════════════════════════════

data class HaCreateOrderRequest(
    val device_id: String,
    val plan_id: String,
    val device_model: String = "",
    val app_version: String = "",
    /** app | h5 | native | auto */
    val pay_prefer: String = "app",
    val phone: String = ""
)

/** 微信 APP 支付调起参数（服务端 RSA 签名） */
data class WxAppPayParams(
    val appId: String = "",
    val partnerId: String = "",
    val prepayId: String = "",
    val packageValue: String = "Sign=WXPay",
    val nonceStr: String = "",
    val timeStamp: String = "",
    val sign: String = ""
)

data class HaCreateOrderResponse(
    val success: Boolean,
    val order_no: String? = null,
    val status: String? = null,
    val amount_yuan: String? = null,
    val amount_fen: Int = 0,
    val plan_id: String? = null,
    val pay_mode: String? = null,
    /** 统一可打开的支付地址（H5 或 weixin:// / code_url） */
    val pay_url: String? = null,
    val mweb_url: String? = null,
    val qr_code_url: String? = null,
    val wx_pay: WxAppPayParams? = null,
    val contact_wechat: String? = null,
    val expire_minutes: Int = 10,
    val message: String? = null,
    val error: String? = null,
    /** 是否早鸟价成交 */
    val early_bird: Boolean = false,
    val list_fen: Int = 0,
    val list_yuan: String? = null,
    val tier_label: String? = null,
    val remark_hint: String? = null,
    val benefit_summary: String? = null,
    val instruction: String? = null,
    val phone: String? = null,
    val phone_masked: String? = null
)

// ════════════════════════════════════════════
//  会员方案价目（含会员码早鸟）
// ════════════════════════════════════════════

data class HaEarlyBirdMeta(
    val eligible: Boolean = false,
    val tier: String? = null,
    val tier_label: String? = null,
    val day_index: Int = 0,
    val days_in_period: Int = 0,
    val days_left_in_period: Int = 0,
    val next_tier_in_days: Int? = null,
    val next_tier_label: String? = null,
    val redeemed_at: String? = null,
    val period_ends_at: Long = 0L
)

data class HaPayPlanDto(
    val plan_id: String = "",
    val product_id: String = "",
    val title: String = "",
    val days: Int = 0,
    val amount_fen: Int = 0,
    val list_fen: Int = 0,
    val amount_yuan: String = "",
    val list_yuan: String = "",
    val early_bird: Boolean = false,
    val save_fen: Int = 0,
    val tier: String? = null,
    val tier_label: String? = null
)

data class HaPayPlansResponse(
    val success: Boolean,
    val early_bird: HaEarlyBirdMeta? = null,
    val plans: List<HaPayPlanDto> = emptyList(),
    val error: String? = null
)

data class HaOrderStatusResponse(
    val success: Boolean,
    val order_no: String? = null,
    val status: String? = null,
    val paid: Boolean = false,
    val plan_id: String? = null,
    val amount_fen: Int = 0,
    val vip_level: Int = 0,
    val expire_time: Long = 0L,
    val duration_days: Int = 0,
    val error: String? = null
)

data class HaNotifyPaidRequest(
    val order_no: String,
    val device_id: String,
    val phone: String = ""
)

data class HaNotifyPaidResponse(
    val success: Boolean,
    val paid: Boolean = false,
    val message: String? = null,
    val error: String? = null,
    val vip_level: Int = 0,
    val expire_time: Long = 0L,
    val plan_id: String? = null
)

data class HaDeviceVipDto(
    val vip_level: Int = 0,
    val expire_time: Long = 0L,
    val plan_id: String? = null,
    val source: String? = null,
    val code: String? = null,
    val active: Boolean = false
)

data class HaPendingOrderDto(
    val order_no: String = "",
    val plan_id: String = "",
    val amount_fen: Int = 0,
    val amount_yuan: String = "",
    val status: String = "",
    val pay_method: String = "",
    val created_at: String? = null,
    val contact_wechat: String = "",
    val remark_hint: String = "",
    val plan_title: String = ""
)

data class HaDeviceStatusResponse(
    val success: Boolean,
    val device_id: String? = null,
    val phone_masked: String? = null,
    val vip: HaDeviceVipDto? = null,
    val pending_order: HaPendingOrderDto? = null,
    val error: String? = null
)

data class HaRestoreRequest(
    val device_id: String,
    val phone: String
)

data class HaRestoreResponse(
    val success: Boolean,
    val rebound: Boolean = false,
    val paid: Boolean = false,
    val vip_level: Int = 0,
    val expire_time: Long = 0L,
    val plan_id: String? = null,
    val phone_masked: String? = null,
    val pending_order: HaPendingOrderDto? = null,
    val message: String? = null,
    val error: String? = null
)

// ════════════════════════════════════════════
//  邀请码（设备级 VIP 兑换）
// ════════════════════════════════════════════

data class BetaRedeemRequest(
    val code: String,
    val device_id: String,
    val device_model: String = "",
    val app_version: String = ""
)

data class BetaRedeemResponse(
    val success: Boolean,
    val message: String? = null,
    val code: String? = null,
    val already_unlocked: Boolean = false,
    /** VIP 等级：当前统一为 1=会员 */
    val vip_level: Int = 1,
    /** 时长（天）；0=永久；缺省由服务端按 90 天处理 */
    val duration_days: Int = 90,
    /** 服务端计算的过期时间戳（毫秒）；0=永久；缺省时由客户端按 duration_days 推算 */
    val expire_time: Long = 0L,
    val error: String? = null
)

/** 自助留资领取邀请码（手机 / 邮箱，非真实验证） */
data class BetaClaimRequest(
    /** phone | email */
    val channel: String,
    val contact: String,
    val device_id: String,
    val device_model: String = "",
    val app_version: String = ""
)

data class BetaClaimResponse(
    val success: Boolean,
    val code: String? = null,
    val already_issued: Boolean = false,
    val contact_type: String? = null,
    val contact_value: String? = null,
    val message: String? = null,
    val error: String? = null
)

// ════════════════════════════════════════════
//  版本检查
// ════════════════════════════════════════════

data class AppReleaseResponse(
    val success: Boolean,
    val version_code: Int = 1,
    val version_name: String = "1.0.0",
    val apk_url: String = "",
    val changelog: String = "",
    val force_update: Boolean = false,
    val min_version_code: Int = 1,
    val download_page_url: String = "",
    val updated_at: String? = null,
    val error: String? = null
)

// ════════════════════════════════════════════
//  搜索直达深链目录
// ════════════════════════════════════════════

data class SearchDeepLinkDto(
    val id: String = "",
    val display_name: String = "",
    val package_name: String = "",
    val category: String = "内容",
    val scheme_template: String = "",
    val uri_kind: String = "q",
    val supports_keyword: Boolean = true,
    val prefer_search_landing: Boolean = true,
    val is_primary: Boolean = false,
    val is_enabled: Boolean = true,
    val note: String = "",
    val sort_order: Int = 0,
    val updated_at: String? = null
)

data class SearchDeeplinkListResponse(
    val success: Boolean,
    val updated_at: String? = null,
    val data: List<SearchDeepLinkDto> = emptyList(),
    val error: String? = null
)

// ════════════════════════════════════════════
//  埋点 & 意见反馈
// ════════════════════════════════════════════

data class AnalyticsEventDto(
    val name: String,
    val props: Map<String, String> = emptyMap()
)

data class AnalyticsEventsRequest(
    val device_id: String,
    val app_version: String = "",
    val os_version: String = "",
    val device_model: String = "",
    val channel: String = "website",
    val events: List<AnalyticsEventDto>
)

data class AnalyticsEventsResponse(
    val success: Boolean,
    val accepted: Int = 0,
    val error: String? = null
)

/**
 * 被监控 App 图标（按包名全局去重）。
 * [icon_base64]：PNG 或 JPEG，无 data URI 前缀；建议边长 48px。
 */
data class AnalyticsAppIconDto(
    val pkg: String,
    val app: String = "",
    val icon_base64: String,
    val hash: String = ""
)

data class AnalyticsAppIconsRequest(
    val device_id: String,
    val icons: List<AnalyticsAppIconDto>
)

data class AnalyticsAppIconsResponse(
    val success: Boolean,
    val accepted: Int = 0,
    val error: String? = null
)

/**
 * 设备监控配置快照（当前态真相源）。
 * 服务端按 [device_id] upsert；不上报寄语/关键词原文/意图全文。
 */
data class AnalyticsConfigSnapshotRequest(
    val device_id: String,
    val app_version: String = "",
    val os_version: String = "",
    val device_model: String = "",
    val channel: String = "website",
    val captured_at: Long = 0L,
    /** cold_start / bind / edit / unbind / reorder / monitor_toggle / home_limit / sync */
    val reason: String = "sync",
    val monitor_service_on: Boolean = false,
    val vip_active: Boolean = false,
    val monitored_count: Int = 0,
    val permission: AnalyticsPermissionSnapshotDto = AnalyticsPermissionSnapshotDto(),
    val prefs: AnalyticsPrefsSnapshotDto = AnalyticsPrefsSnapshotDto(),
    val apps: List<AnalyticsMonitoredAppDto> = emptyList()
)

data class AnalyticsPermissionSnapshotDto(
    val overlay: Boolean = false,
    val usage: Boolean = false,
    val battery: Boolean = false,
    val notification: Boolean = false,
    val accessibility: Boolean = false
)

data class AnalyticsPrefsSnapshotDto(
    val theme_mode: String = "system",
    val enhanced_keep_alive: Boolean = false,
    val capsule_mini_size: String = "standard"
)

data class AnalyticsMonitoredAppDto(
    val pkg: String,
    val app: String = "",
    val enabled: Boolean = true,
    val sort_order: Int = 0,
    val cap_intent: Boolean = false,
    val cap_time: Boolean = false,
    val cap_period: Boolean = false,
    val cap_session: Boolean = false,
    val cap_keywords: Boolean = false,
    val daily_limit_min: Int = 0,
    val default_session_min: Int = 0,
    val keyword_count: Int = 0,
    val period_window_count: Int = 0,
    /** 周内日均锁定小时（一位小数，字符串避免 Gson 精度噪音） */
    val period_lock_hours: String = "0"
)

data class AnalyticsConfigSnapshotResponse(
    val success: Boolean,
    val error: String? = null
)

/**
 * 提交意见反馈。
 *
 * 管理台约定（图片）：
 * - [images]：JPEG base64 数组，无 `data:image/jpeg;base64,` 前缀；可为空
 * - 建议服务端解码落盘后存 URL，并在回复列表的 [FeedbackReplyItemDto.images] 回传
 */
data class FeedbackRequest(
    val device_id: String,
    val contact: String = "",
    val content: String,
    /** problem = 使用问题；idea = 想法建议；general = 旧版/未分类 */
    val category: String = "general",
    val app_version: String = "",
    val device_model: String = "",
    val os_version: String = "",
    val diagnostic: String = "",
    val images: List<String> = emptyList()
)

data class FeedbackResponse(
    val success: Boolean,
    val id: Long? = null,
    val error: String? = null
)

/**
 * 拉取本机已回复的反馈（轻量收件箱）。
 * 后端约定：仅返回 [reply] 非空的条目；[replied_at] 为 ISO8601 或 epoch 毫秒字符串均可。
 */
data class FeedbackRepliesResponse(
    val success: Boolean = false,
    val items: List<FeedbackReplyItemDto> = emptyList(),
    val error: String? = null
)

data class FeedbackReplyItemDto(
    val id: Long = 0L,
    val category: String = "general",
    val content: String = "",
    val reply: String = "",
    /** ISO8601 或 epoch millis 字符串 */
    val created_at: String? = null,
    val replied_at: String? = null,
    /** 公网图片 URL；管理台展示用，客户端有本机缓存时优先本地 */
    val images: List<String> = emptyList()
)
