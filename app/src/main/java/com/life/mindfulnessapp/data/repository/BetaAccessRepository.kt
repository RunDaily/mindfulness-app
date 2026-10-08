package com.life.mindfulnessapp.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.BetaClaimRequest
import com.life.mindfulnessapp.data.network.BetaRedeemRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

sealed class BetaRedeemResult {
    data class Success(
        val message: String,
        val alreadyUnlocked: Boolean,
        val vipLevel: Int,
        val expireTime: Long
    ) : BetaRedeemResult()

    data class Error(val message: String) : BetaRedeemResult()
}

sealed class BetaClaimResult {
    data class Success(
        val code: String,
        val alreadyIssued: Boolean,
        val message: String
    ) : BetaClaimResult()

    data class Error(val message: String) : BetaClaimResult()
}

/**
 * 会员码：设备级兑换会员，不创建账号。
 * 出现时机：免费版超过 [AppPreferences.FREE_MONITOR_LIMIT] 个 App（点第 4 坑）时的体系底栏 / 会员页。
 * 默认开通时长由服务端配置（当前为 3 个月）。
 */
@Singleton
class BetaAccessRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val appPreferences: AppPreferences,
    private val analyticsRepository: AnalyticsRepository
) {

    fun isUnlocked(): Boolean = appPreferences.isBetaUnlocked()

    fun deviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return androidId?.takeIf { it.isNotBlank() } ?: "unknown-${Build.MODEL}"
    }

    /**
     * 自助留资领取会员码（手机 / 邮箱，非真实验证）。
     * 成功后写入本机「已领码」，供「我」Tab 展示与复制。
     */
    suspend fun claim(channel: String, contact: String): BetaClaimResult {
        val ch = channel.trim().lowercase()
        val value = contact.trim()
        if (ch !in setOf("phone", "email")) {
            return BetaClaimResult.Error("请选择手机号或邮箱")
        }
        if (value.isBlank()) {
            return BetaClaimResult.Error(if (ch == "phone") "请输入手机号" else "请输入邮箱")
        }

        if (BuildConfig.DEBUG && value.equals("debug", ignoreCase = true)) {
            val code = "DEBUG"
            appPreferences.setClaimedInviteCode(code)
            return BetaClaimResult.Success(
                code = code,
                alreadyIssued = false,
                message = "调试模式已生成会员码"
            )
        }

        return try {
            val resp = apiService.claimBetaCode(
                BetaClaimRequest(
                    channel = ch,
                    contact = value,
                    device_id = deviceId(),
                    device_model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    app_version = BuildConfig.VERSION_NAME
                )
            )
            if (resp.success) {
                val code = (resp.code ?: "").trim().uppercase()
                if (code.isBlank()) {
                    BetaClaimResult.Error("服务未返回会员码")
                } else {
                    appPreferences.setClaimedInviteCode(code)
                    analyticsRepository.trackVipCodeClaim(ch, resp.already_issued)
                    if (ch == "phone") {
                        appPreferences.setBoundPhone(value)
                    } else if (!resp.contact_value.isNullOrBlank() &&
                        resp.contact_type == "phone"
                    ) {
                        appPreferences.setBoundPhone(resp.contact_value)
                    }
                    BetaClaimResult.Success(
                        code = code,
                        alreadyIssued = resp.already_issued,
                        message = resp.message ?: "会员码已生成"
                    )
                }
            } else {
                BetaClaimResult.Error(resp.error ?: "领取失败")
            }
        } catch (e: Exception) {
            BetaClaimResult.Error(
                e.message?.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试"
            )
        }
    }

    /**
     * 兑换会员码并开通 VIP。
     * Debug 包可用 `DEBUG` / `XINMAO` 离线开通（后端未部署时便于开发）。
     */
    suspend fun redeem(rawCode: String): BetaRedeemResult {
        val code = rawCode.trim().uppercase().replace("\\s+".toRegex(), "")
        if (code.isBlank()) return BetaRedeemResult.Error("请输入会员码")

        if (BuildConfig.DEBUG && code in DEBUG_BYPASS_CODES) {
            applyVipGrant(level = 1, durationDays = 90, expireTime = 0L, code = code)
            return BetaRedeemResult.Success(
                message = "调试模式已开通 3 个月会员",
                alreadyUnlocked = false,
                vipLevel = 1,
                expireTime = System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000
            )
        }

        return try {
            val resp = apiService.redeemBetaCode(
                BetaRedeemRequest(
                    code = code,
                    device_id = deviceId(),
                    device_model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    app_version = BuildConfig.VERSION_NAME
                )
            )
            if (resp.success) {
                val level = resp.vip_level.coerceAtLeast(1)
                val expire = resolveExpireTime(resp.expire_time, resp.duration_days)
                applyVipGrant(
                    level = level,
                    durationDays = resp.duration_days,
                    expireTime = expire,
                    code = resp.code ?: code
                )
                BetaRedeemResult.Success(
                    message = resp.message ?: "会员码已兑换，权益已开通",
                    alreadyUnlocked = resp.already_unlocked,
                    vipLevel = level,
                    expireTime = expire
                )
            } else {
                BetaRedeemResult.Error(resp.error ?: "验证失败")
            }
        } catch (e: Exception) {
            BetaRedeemResult.Error(
                e.message?.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试"
            )
        }
    }

    /** 仅 Debug：跳过校验并开通 VIP */
    fun unlockForDebug() {
        if (BuildConfig.DEBUG) {
            val expire = System.currentTimeMillis() + 90L * 24 * 60 * 60 * 1000
            applyVipGrant(level = 1, durationDays = 90, expireTime = expire, code = "DEBUG")
        }
    }

    private fun applyVipGrant(level: Int, durationDays: Int, expireTime: Long, code: String) {
        val expire = if (expireTime > 0L) expireTime else resolveExpireTime(0L, durationDays)
        val now = System.currentTimeMillis()
        appPreferences.setBetaUnlocked(true, code)
        // 新兑换写入兑换时刻；已兑过则保留首次时间
        if (appPreferences.getBetaRedeemedAt() <= 0L) {
            appPreferences.setBetaRedeemedAt(now)
        }
        appPreferences.saveVipStatus(level = level.coerceAtLeast(1), expireTime = expire)
    }

    private fun resolveExpireTime(serverExpire: Long, durationDays: Int): Long {
        if (serverExpire > 0L) return serverExpire
        if (durationDays <= 0) return 0L
        return System.currentTimeMillis() + durationDays * 24L * 60 * 60 * 1000
    }

    companion object {
        private val DEBUG_BYPASS_CODES = setOf("DEBUG", "XINMAO")
    }
}
