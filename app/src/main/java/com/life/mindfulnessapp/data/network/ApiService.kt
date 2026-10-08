package com.life.mindfulnessapp.data.network

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * 心锚后端 API。
 * 公开接口：拦截名言、邀请码（VIP）、版本检查。使用数据全部留在本机。
 */
interface ApiService {

    /** 随机获取 count 条拦截名言 */
    @GET("api/heartanchor/quotes/random")
    suspend fun getRandomQuotes(
        @Query("count") count: Int = 5,
        @Query("device_id") deviceId: String? = null,
        @Query("source") source: String? = null
    ): QuoteRandomResponse

    /** 批量拉取名言用于本地缓存（按来源/名人过滤） */
    @GET("api/heartanchor/quotes")
    suspend fun getQuotes(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0,
        @Query("device_id") deviceId: String? = null,
        @Query("source") source: String? = null,
        @Query("author_id") authorId: Int? = null
    ): QuoteListResponse

    /** 名人目录（不含名言正文） */
    @GET("api/heartanchor/authors")
    suspend fun getAuthors(
        @Query("device_id") deviceId: String? = null,
        @Query("category") category: String? = null
    ): AuthorListResponse

    @GET("api/heartanchor/authors/categories")
    suspend fun getAuthorCategories(): AuthorCategoriesResponse

    @GET("api/heartanchor/authors/subscriptions")
    suspend fun getAuthorSubscriptions(
        @Query("device_id") deviceId: String
    ): AuthorListResponse

    @POST("api/heartanchor/authors/{id}/subscribe")
    suspend fun subscribeAuthor(
        @Path("id") authorId: Int,
        @Body body: AuthorDeviceRequest
    ): AuthorSubscribeResponse

    @DELETE("api/heartanchor/authors/{id}/subscribe")
    suspend fun unsubscribeAuthor(
        @Path("id") authorId: Int,
        @Query("device_id") deviceId: String
    ): AuthorSubscribeResponse

    @POST("api/heartanchor/authors/requests")
    suspend fun requestAuthor(
        @Body body: AuthorRequestBody
    ): AuthorRequestResult

    @GET("api/heartanchor/authors/requests")
    suspend fun getMyAuthorRequests(
        @Query("device_id") deviceId: String
    ): AuthorRequestsResponse

    @GET("api/heartanchor/authors/notices")
    suspend fun getAuthorNotices(
        @Query("device_id") deviceId: String,
        @Query("unread") unread: Int = 1
    ): AuthorNoticesResponse

    @POST("api/heartanchor/authors/notices/read")
    suspend fun markAuthorNoticesRead(
        @Body body: AuthorNoticesReadBody
    ): SimpleOkResponse

    /** 设备级邀请码兑换 → 开通 VIP（无需登录） */
    @POST("api/heartanchor/beta/redeem")
    suspend fun redeemBetaCode(
        @Body body: BetaRedeemRequest
    ): BetaRedeemResponse

    /** 自助留资领取邀请码（手机/邮箱，一号一码） */
    @POST("api/heartanchor/beta/claim")
    suspend fun claimBetaCode(
        @Body body: BetaClaimRequest
    ): BetaClaimResponse

    /** 查询设备可见会员价目（含会员码早鸟） */
    @GET("api/heartanchor/pay/plans")
    suspend fun getVipPlans(
        @Query("device_id") deviceId: String
    ): HaPayPlansResponse

    /** 创建会员支付订单（微信 Native / 人工降级） */
    @POST("api/heartanchor/pay/create-order")
    suspend fun createVipOrder(
        @Body body: HaCreateOrderRequest
    ): HaCreateOrderResponse

    /** 轮询会员订单支付状态 */
    @GET("api/heartanchor/pay/order-status/{orderNo}")
    suspend fun getVipOrderStatus(
        @Path("orderNo") orderNo: String,
        @Query("device_id") deviceId: String
    ): HaOrderStatusResponse

    /** 按设备恢复会员与未完成订单（无账号） */
    @GET("api/heartanchor/pay/device-status")
    suspend fun getVipDeviceStatus(
        @Query("device_id") deviceId: String
    ): HaDeviceStatusResponse

    /** 用手机号恢复 / 改挂到当前设备 */
    @POST("api/heartanchor/pay/restore")
    suspend fun restoreVipByPhone(
        @Body body: HaRestoreRequest
    ): HaRestoreResponse

    /** 人工收款：用户申报已转账 */
    @POST("api/heartanchor/pay/notify-paid")
    suspend fun notifyVipPaid(
        @Body body: HaNotifyPaidRequest
    ): HaNotifyPaidResponse

    /** 官网 APK 最新版本信息 */
    @GET("api/heartanchor/release/latest")
    suspend fun getLatestRelease(): AppReleaseResponse

    /** 搜索直达深链目录（启用条目；失败时客户端用本地缓存 / builtin） */
    @GET("api/heartanchor/search-deeplinks")
    suspend fun getSearchDeeplinks(): SearchDeeplinkListResponse

    /** 批量上报匿名埋点 */
    @POST("api/heartanchor/analytics/events")
    suspend fun postAnalyticsEvents(
        @Body body: AnalyticsEventsRequest
    ): AnalyticsEventsResponse

    /**
     * 上报被监控 App 图标（按包名去重）。
     * 管理台用图标+名称展示「进了哪个 App」。
     */
    @POST("api/heartanchor/analytics/app-icons")
    suspend fun postAppIcons(
        @Body body: AnalyticsAppIconsRequest
    ): AnalyticsAppIconsResponse

    /**
     * 上报监控配置快照（按 device_id upsert 最新一份）。
     * 管理台用户画像「绑了哪些 App / 各开哪些能力」以此为准；事件流仅作变更故事。
     */
    @POST("api/heartanchor/analytics/config-snapshot")
    suspend fun postConfigSnapshot(
        @Body body: AnalyticsConfigSnapshotRequest
    ): AnalyticsConfigSnapshotResponse

    /**
     * 提交意见反馈。
     * Body 可含 images（JPEG base64 数组）；管理台需解码展示。
     */
    @POST("api/heartanchor/feedback")
    suspend fun submitFeedback(
        @Body body: FeedbackRequest
    ): FeedbackResponse

    /**
     * 拉取该设备已获开发者回复的反馈。
     * 仅返回有 reply 的条目；客户端合并本地已读状态。
     * items[].images 为截图 URL（可选），供记录页回看。
     */
    @GET("api/heartanchor/feedback/replies")
    suspend fun getFeedbackReplies(
        @Query("device_id") deviceId: String
    ): FeedbackRepliesResponse
}
