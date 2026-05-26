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
    val password: String
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
