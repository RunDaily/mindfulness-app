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
}
