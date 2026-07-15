package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.AuthRequest
import com.life.mindfulnessapp.data.network.RemoteMonitoredApp
import com.life.mindfulnessapp.data.network.RemoteSession
import com.life.mindfulnessapp.data.network.SyncMonitoredAppsRequest
import com.life.mindfulnessapp.data.network.SyncSessionsRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

// ════════════════════════════════════════════
//  操作结果密封类
// ════════════════════════════════════════════

sealed class AuthResult {
    data class Success(
        val token: String,
        val username: String,
        val nickname: String,
        val avatarEmoji: String,
        val isNewUser: Boolean = false
    ) : AuthResult()
    data class Error(val message: String) : AuthResult()
}

sealed class SyncResult {
    data class Success(val message: String) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

// ════════════════════════════════════════════
//  AccountRepository
// ════════════════════════════════════════════

@Singleton
class AccountRepository @Inject constructor(
    private val appPreferences: AppPreferences,
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val api: ApiService,
    private val vipRepository: VipRepository
) {

    /** 用于在登录/注册完成后在后台静默同步 VIP 状态 */
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    val isLoggedIn: Boolean get() = appPreferences.isLoggedIn
    val savedToken: String? get() = appPreferences.savedToken
    val savedNickname: String? get() = appPreferences.savedNickname
    val savedUsername: String? get() = appPreferences.savedUsername
    val savedAvatarEmoji: String? get() = appPreferences.savedAvatarEmoji

    private fun bearerToken(token: String) = "Bearer $token"

    // ── 登录 / 注册 ────────────────────────────────────────────────────────────

    /**
     * 一键登录注册：先尝试登录，若 404（未注册）则自动注册。
     * @param inviteCode 注册时可携带的邀请码（选填），登录场景传 null 即可
     */
    suspend fun loginOrRegister(
        phone: String,
        password: String,
        inviteCode: String? = null
    ): AuthResult {
        return try {
            val resp = api.login(AuthRequest(phone, password))
            val token = resp.token
            val user = resp.user
            if (resp.success && token != null && user != null) {
                appPreferences.saveAccount(token, user.username, user.nickname, user.avatarEmoji)
                // 登录成功后，静默在后台同步 VIP 状态
                syncScope.launch { vipRepository.fetchVipStatus() }
                AuthResult.Success(
                    token = token,
                    username = user.username,
                    nickname = user.nickname,
                    avatarEmoji = user.avatarEmoji,
                    isNewUser = false
                )
            } else {
                AuthResult.Error(resp.error ?: "登录失败，请稍后重试")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                404 -> autoRegister(phone, password, inviteCode)
                401 -> AuthResult.Error("密码错误，请重新输入")
                403 -> AuthResult.Error("账号已被禁用，请联系我们")
                else -> AuthResult.Error("登录失败（${e.code()}），请稍后重试")
            }
        } catch (e: Exception) {
            AuthResult.Error("网络连接失败，请检查网络")
        }
    }

    private suspend fun autoRegister(
        phone: String,
        password: String,
        inviteCode: String? = null
    ): AuthResult {
        return try {
            // 注册时携带邀请码（为空时传 null，服务端忽略）
            val req = AuthRequest(
                phone      = phone,
                password   = password,
                inviteCode = inviteCode?.uppercase()?.takeIf { it.isNotBlank() }
            )
            val resp = api.register(req)
            val token = resp.token
            val user = resp.user
            if (resp.success && token != null && user != null) {
                appPreferences.saveAccount(token, user.username, user.nickname, user.avatarEmoji)
                // 若携带了邀请码，标记本地已使用
                if (!inviteCode.isNullOrBlank()) {
                    appPreferences.hasRedeemedInvite = true
                }
                // 注册成功后，静默在后台同步 VIP 状态
                syncScope.launch { vipRepository.fetchVipStatus() }
                AuthResult.Success(
                    token = token,
                    username = user.username,
                    nickname = user.nickname,
                    avatarEmoji = user.avatarEmoji,
                    isNewUser = true
                )
            } else {
                AuthResult.Error(resp.error ?: "注册失败，请稍后重试")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                409 -> AuthResult.Error("该手机号已注册，请直接登录")
                400 -> AuthResult.Error("请输入正确的手机号码")
                else -> AuthResult.Error("注册失败（${e.code()}）")
            }
        } catch (e: Exception) {
            AuthResult.Error("网络连接失败，请检查网络")
        }
    }

    /** 退出登录 */
    fun logout() {
        appPreferences.clearAccount()
    }

    /** 注销账号（向服务端发起删除请求，成功后清除本地登录态）*/
    suspend fun deleteAccount(): AuthResult {
        val token = savedToken ?: return AuthResult.Error("未登录")
        return try {
            val resp = api.deleteAccount(bearerToken(token))
            if (resp.success) {
                appPreferences.clearAccount()
                AuthResult.Success(
                    token = "", username = "", nickname = "", avatarEmoji = "", isNewUser = false
                )
            } else {
                AuthResult.Error(resp.error ?: "注销失败，请稍后重试")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> {
                    // Token 失效，视为已注销，清理本地数据
                    appPreferences.clearAccount()
                    AuthResult.Success(token = "", username = "", nickname = "", avatarEmoji = "", isNewUser = false)
                }
                else -> AuthResult.Error("注销失败（${e.code()}），请稍后重试")
            }
        } catch (e: Exception) {
            AuthResult.Error("网络连接失败，请检查网络")
        }
    }

    // ── 数据同步 ────────────────────────────────────────────────────────────────

    /**
     * 将本地使用会话记录推送到服务端。
     * 只上传已完成（endTime > 0）且最近 30 天内的记录。
     */
    suspend fun syncSessions(): SyncResult {
        val token = savedToken ?: return SyncResult.Error("未登录")
        return try {
            // 取最近 30 天的记录范围
            val nowMs = System.currentTimeMillis()
            val thirtyDaysAgoMs = nowMs - 30L * 24 * 60 * 60 * 1000
            val records = usageRecordRepository.getAllCompletedRecordsSince(thirtyDaysAgoMs)

            if (records.isEmpty()) return SyncResult.Success("暂无数据需要同步")

            val remoteSessions = records.map { it.toRemoteSession() }
            val resp = api.syncSessions(
                bearerToken(token),
                SyncSessionsRequest(remoteSessions)
            )
            if (resp.success) {
                SyncResult.Success("已同步 ${resp.synced} 条使用记录")
            } else {
                SyncResult.Error(resp.error ?: "同步失败")
            }
        } catch (e: Exception) {
            SyncResult.Error("网络错误：${e.message ?: "请检查网络"}")
        }
    }

    /**
     * 将本地被监控 App 列表推送到服务端。
     */
    suspend fun syncMonitoredApps(): SyncResult {
        val token = savedToken ?: return SyncResult.Error("未登录")
        return try {
            val appLimits = appLimitRepository.getAllLimitsOnce()
            if (appLimits.isEmpty()) return SyncResult.Success("暂无被监控 App 数据")

            val remoteApps = appLimits.map {
                RemoteMonitoredApp(
                    bundleId = it.packageName,
                    appName = it.appName,
                    isEnabled = if (it.isEnabled) 1 else 0
                )
            }
            val resp = api.syncMonitoredApps(
                bearerToken(token),
                SyncMonitoredAppsRequest(remoteApps)
            )
            if (resp.success) {
                SyncResult.Success("已同步 ${resp.synced} 个被监控 App")
            } else {
                SyncResult.Error(resp.error ?: "同步失败")
            }
        } catch (e: Exception) {
            SyncResult.Error("网络错误：${e.message ?: "请检查网络"}")
        }
    }

    /**
     * 完整同步：上传使用记录 + 被监控 App 列表。
     */
    suspend fun fullSync(): SyncResult {
        val sessionsResult = syncSessions()
        if (sessionsResult is SyncResult.Error) return sessionsResult
        val appsResult = syncMonitoredApps()
        return if (appsResult is SyncResult.Success) {
            SyncResult.Success("数据同步完成 ✓")
        } else appsResult
    }

    // ── 工具函数 ────────────────────────────────────────────────────────────────

    private fun UsageRecordEntity.toRemoteSession(): RemoteSession {
        val sessionId = "ha_${packageName}_${startTime}"
        val dateStr = dateFormat.format(Date(startTime))
        return RemoteSession(
            sessionId = sessionId,
            bundleId = packageName,
            appName = packageName,   // App 名后续可通过 AppInfo 补全
            intent = purpose ?: "",
            startTime = startTime,
            endTime = if (endTime > 0) endTime else null,
            durationMs = durationSeconds * 1000L,
            wasIntercepted = if (purpose != null) 1 else 0,
            date = dateStr
        )
    }
}
