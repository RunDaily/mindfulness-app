package com.life.mindfulnessapp.data.network

import retrofit2.http.*

// ════════════════════════════════════════════
//  心锚 App 后端 API 接口定义
//  对应服务端路由前缀: /api/heartanchor
// ════════════════════════════════════════════

interface ApiService {

    // ── 账号（注册/登录）────────────────────────────────────────────

    @POST("api/heartanchor/register")
    suspend fun register(@Body req: AuthRequest): AuthResponse

    @POST("api/heartanchor/login")
    suspend fun login(@Body req: AuthRequest): AuthResponse

    @GET("api/heartanchor/me")
    suspend fun getMe(@Header("Authorization") token: String): ApiResponse<RemoteUser>

    @PUT("api/heartanchor/me")
    suspend fun updateProfile(
        @Header("Authorization") token: String,
        @Body req: UpdateProfileRequest
    ): ApiResponse<RemoteUser>

    @DELETE("api/heartanchor/me")
    suspend fun deleteAccount(
        @Header("Authorization") token: String
    ): ApiResponse<Unit>

    // ── 使用会话同步 ──────────────────────────────────────────────

    @POST("api/heartanchor/sessions/sync")
    suspend fun syncSessions(
        @Header("Authorization") token: String,
        @Body req: SyncSessionsRequest
    ): SyncResponse

        // ── 被监控 App 列表同步 ───────────────────────────────────────
    @POST("api/heartanchor/monitored-apps/sync")
    suspend fun syncMonitoredApps(
        @Header("Authorization") token: String,
        @Body req: SyncMonitoredAppsRequest
    ): SyncResponse

    // ── VIP 状态 ──────────────────────────────────────────────

    /** 查询当前用户的 VIP 状态 */
    @GET("api/heartanchor/vip/status")
    suspend fun getVipStatus(
        @Header("Authorization") token: String
    ): VipStatusResponse

    /** 购买 VIP（提交支付凭证，后端校验后激活） */
    @POST("api/heartanchor/vip/purchase")
    suspend fun purchaseVip(
        @Header("Authorization") token: String,
        @Body req: VipPurchaseRequest
    ): VipStatusResponse

    /** 激活 7 天免费试用（每个账号仅限一次） */
    @POST("api/heartanchor/vip/trial")
    suspend fun activateTrial(
        @Header("Authorization") token: String
    ): VipStatusResponse

    // ── 邀请码 ────────────────────────────────────────────────────────────

    /** 获取当前用户的邀请码及邀请统计 */
    @GET("api/heartanchor/invite/info")
    suspend fun getInviteInfo(
        @Header("Authorization") token: String
    ): InviteInfoResponse

    /** 兑换邀请码（注册后填写邀请人的邀请码） */
    @POST("api/heartanchor/invite/redeem")
    suspend fun redeemInviteCode(
        @Header("Authorization") token: String,
        @Body req: RedeemInviteRequest
    ): RedeemInviteResponse

    // ── 激活码（一次性兑换码开通会员）─────────────────────────────────────

    /** 使用激活码开通会员（每个激活码仅能使用一次） */
    @POST("api/heartanchor/activation/redeem")
    suspend fun redeemActivationCode(
        @Header("Authorization") token: String,
        @Body req: ActivationCodeRequest
    ): ActivationCodeResponse

    // ── 拦截名言 ──────────────────────────────────────────────────────────

    /** 随机获取 count 条拦截名言（无需登录） */
    @GET("api/heartanchor/quotes/random")
    suspend fun getRandomQuotes(
        @Query("count") count: Int = 5
    ): QuoteRandomResponse

    /** 批量拉取名言用于本地缓存（无需登录） */
    @GET("api/heartanchor/quotes")
    suspend fun getQuotes(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): QuoteListResponse
}
