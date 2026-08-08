package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.billing.BillingManager
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.network.VipPlan
import javax.inject.Inject
import javax.inject.Singleton

// ════════════════════════════════════════════
//  VIP 操作结果密封类
// ════════════════════════════════════════════

sealed class VipResult {
    data class Success(
        val vipLevel: Int,
        val expireTime: Long,   // 0 = 永久
        val message: String
    ) : VipResult()
    data class Error(val message: String) : VipResult()
}

// ════════════════════════════════════════════
//  VipRepository（纯本地 + Google Play Billing）
// ════════════════════════════════════════════

/**
 * VIP 状态完全保存在本机，购买通过 Google Play 完成，无需账号登录。
 *
 * 功能门禁说明（免费版限制）：
 *  - 监控 App 数量：最多 3 个
 *  - 每周限额设置：VIP（标准版及以上）专属
 *  - 数据历史查看：免费版仅最近 7 天，VIP 30 天，高级版永久
 */
@Singleton
class VipRepository @Inject constructor(
    private val appPreferences: AppPreferences,
    private val billingManager: BillingManager
) {

    val vipLevel = appPreferences.vipLevel
    val vipExpireTime = appPreferences.vipExpireTime

    fun isVip(): Boolean = appPreferences.isVipActive()

    fun isPremium(): Boolean = appPreferences.isPremium()

    fun canAddMoreApps(currentCount: Int): Boolean {
        return AppPreferences.FREE_PERIOD_ENABLED || isVip() || currentCount < AppPreferences.FREE_MONITOR_LIMIT
    }

    fun canUseAllThemes(): Boolean = isVip()

    fun canSetWeeklyLimit(): Boolean = isVip()

    fun canViewExtendedHistory(): Boolean = isPremium()

    fun getDataRetentionDays(): Int = when (appPreferences.getVipLevel()) {
        0 -> 7
        1 -> 30
        else -> Int.MAX_VALUE
    }

    /** 刷新本地 VIP 展示状态（不再请求服务端） */
    fun refreshLocalStatus(): VipResult {
        return VipResult.Success(
            vipLevel = appPreferences.getVipLevel(),
            expireTime = appPreferences.vipExpireTime.value,
            message = getLocalStatusText()
        )
    }

    /**
     * Google Play 购买成功后，在本机激活对应 VIP 权益并 acknowledge 订单。
     */
    suspend fun activateFromPurchase(
        purchaseToken: String,
        productId: String,
        productType: String
    ): VipResult {
        val plan = VipPlan.entries.firstOrNull { it.productId == productId }
            ?: return VipResult.Error("未知的商品，请联系客服")

        val (level, expireTime) = resolveVipGrant(plan)
        appPreferences.saveVipStatus(level, expireTime)
        billingManager.acknowledgePurchase(purchaseToken)

        return VipResult.Success(
            vipLevel = level,
            expireTime = expireTime,
            message = "购买成功！${getLocalStatusText()}"
        )
    }

    /**
     * 激活本机 7 天高级版试用（每台设备一次，无需账号）。
     */
    fun activateTrial(): VipResult {
        if (appPreferences.hasUsedTrial) {
            return VipResult.Error("每台设备仅可使用一次免费试用")
        }
        if (isVip()) {
            return VipResult.Error("当前已是 VIP，无需试用")
        }
        val expire = System.currentTimeMillis() + 7L * 24 * 60 * 60 * 1000
        appPreferences.hasUsedTrial = true
        appPreferences.saveVipStatus(level = 2, expireTime = expire)
        return VipResult.Success(
            vipLevel = 2,
            expireTime = expire,
            message = "已激活 7 天高级版免费试用 🎉"
        )
    }

    fun isTrialAvailable(): Boolean = !appPreferences.hasUsedTrial && !isVip()

    fun getLocalStatusText(): String {
        val level = appPreferences.getVipLevel()
        val expire = appPreferences.vipExpireTime.value
        return when {
            level <= 0 -> "免费版"
            expire == 0L -> if (level >= 2) "高级版 · 永久有效" else "标准版 · 永久有效"
            expire > System.currentTimeMillis() -> {
                val days = ((expire - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                val prefix = if (level >= 2) "高级版" else "标准版"
                "$prefix · 剩余 $days 天"
            }
            else -> "VIP 已过期"
        }
    }

    private fun resolveVipGrant(plan: VipPlan): Pair<Int, Long> {
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        return when (plan) {
            VipPlan.MONTHLY_STANDARD -> 1 to (now + 30 * dayMs)
            VipPlan.YEARLY_STANDARD -> 1 to (now + 365 * dayMs)
            VipPlan.YEARLY_PREMIUM -> 2 to (now + 365 * dayMs)
            VipPlan.LIFETIME -> 2 to 0L
        }
    }
}
