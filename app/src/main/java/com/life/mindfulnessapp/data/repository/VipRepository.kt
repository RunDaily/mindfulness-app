package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.billing.BillingManager
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.network.ActivationCodeRequest
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.VipPlan
import com.life.mindfulnessapp.data.network.VipPurchaseRequest
import com.life.mindfulnessapp.data.network.VipStatusResponse
import kotlinx.coroutines.flow.StateFlow
import retrofit2.HttpException
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
//  VipRepository
// ════════════════════════════════════════════

/**
 * 统一管理 VIP 状态的 Repository。
 *
 * 功能：
 *  1. 向服务端查询 VIP 状态并本地缓存
 *  2. 发起购买请求（提交支付凭证，后端校验后激活）
 *  3. 激活 7 天免费试用
 *  4. 向外暴露实时 VIP 状态 Flow，供 ViewModel 订阅
 *
 * 功能门禁说明（免费版限制）：
 *  - 监控 App 数量：最多 3 个
 *  - 拦截主题：三种风格均可用（default / zen / gauge）
 *  - 每周限额设置：VIP（标准版及以上）专属
 *  - 数据历史查看：免费版仅最近 7 天，VIP 30天，高级版永久
 */
@Singleton
class VipRepository @Inject constructor(
    private val appPreferences: AppPreferences,
    private val api: ApiService,
    private val billingManager: BillingManager
) {

    // ── 本地 VIP 状态 Flow（直接来自 AppPreferences）────────────────────────

    /** VIP 等级（0=免费，1=标准版，2=高级版），实时响应 */
    val vipLevel: StateFlow<Int> = appPreferences.vipLevel

    /** VIP 过期时间戳（0 = 永久） */
    val vipExpireTime: StateFlow<Long> = appPreferences.vipExpireTime

    // ── 便捷同步判断 ────────────────────────────────────────────────────────

    /** 当前是否为 VIP（任意等级，未过期） */
    fun isVip(): Boolean = appPreferences.isVipActive()

    /** 当前是否为高级版（Premium，等级 2） */
    fun isPremium(): Boolean = appPreferences.isPremium()

    /** 是否可以添加更多监控 App（免费公测期不限制数量） */
    fun canAddMoreApps(currentCount: Int): Boolean {
        return AppPreferences.FREE_PERIOD_ENABLED || isVip() || currentCount < AppPreferences.FREE_MONITOR_LIMIT
    }

    /** 是否可以使用所有拦截主题 */
    fun canUseAllThemes(): Boolean = isVip()

    /** 是否可以设置每周上限 */
    fun canSetWeeklyLimit(): Boolean = isVip()

    /** 是否可以查看 30 天以上历史数据 */
    fun canViewExtendedHistory(): Boolean = isPremium()

    /** 获取可查看数据的天数上限（免费=7，标准=30，高级=永久/Int.MAX_VALUE） */
    fun getDataRetentionDays(): Int = when (appPreferences.getVipLevel()) {
        0    -> 7
        1    -> 30
        else -> Int.MAX_VALUE
    }

    // ── 远程操作 ────────────────────────────────────────────────────────────

    /**
     * 从服务端查询最新 VIP 状态，并更新本地缓存。
     * 应在：登录成功后、进入 VIP 页面时、App 启动时调用。
     */
    suspend fun fetchVipStatus(): VipResult {
        val token = appPreferences.savedToken ?: return VipResult.Error("请先登录")
        return try {
            val resp = api.getVipStatus("Bearer $token")
            if (resp.success) {
                appPreferences.saveVipStatus(resp.vipLevel, resp.expireTime)
                VipResult.Success(
                    vipLevel = resp.vipLevel,
                    expireTime = resp.expireTime,
                    message = buildStatusMessage(resp)
                )
            } else {
                VipResult.Error(resp.error ?: "查询 VIP 状态失败")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> VipResult.Error("登录已过期，请重新登录")
                else -> VipResult.Error("查询失败（${e.code()}）")
            }
        } catch (e: Exception) {
            // 网络失败时返回本地缓存状态，不报错
            VipResult.Success(
                vipLevel = appPreferences.getVipLevel(),
                expireTime = appPreferences.vipExpireTime.value,
                message = "使用本地缓存状态"
            )
        }
    }

    /**
     * 向自己的服务端提交 Google Play 购买凭证进行二次验证，验证通过后激活 VIP。
     *
     * 调用时机：BillingManager 回调 [BillingResult2.Success] 后立即调用。
     *
     * 服务端应：
     *  1. 调用 Google Play Developer API 验证 purchaseToken 的真实性
     *  2. 验证通过后激活账号 VIP 状态
     *  3. 调用 acknowledgePurchase（也可在客户端由 BillingManager 完成）
     *
     * @param purchaseToken  Google Play 返回的 purchaseToken
     * @param productId      Play Console 中配置的产品 ID
     * @param productType    "subs" 或 "inapp"
     */
    suspend fun verifyAndActivateVip(
        purchaseToken: String,
        productId: String,
        productType: String
    ): VipResult {
        val token = appPreferences.savedToken ?: return VipResult.Error("请先登录")
        return try {
            val resp = api.purchaseVip(
                "Bearer $token",
                VipPurchaseRequest(
                    purchaseToken = purchaseToken,
                    productId     = productId,
                    productType   = productType
                )
            )
            if (resp.success) {
                appPreferences.saveVipStatus(resp.vipLevel, resp.expireTime)
                // 服务端验证通过后，客户端 acknowledge 购买（告知 Google Play 已处理）
                billingManager.acknowledgePurchase(purchaseToken)
                VipResult.Success(
                    vipLevel   = resp.vipLevel,
                    expireTime = resp.expireTime,
                    message    = "购买成功！${buildStatusMessage(resp)}"
                )
            } else {
                VipResult.Error(resp.error ?: "购买验证失败，请联系客服")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                400 -> VipResult.Error("购买凭证无效，请联系客服")
                401 -> VipResult.Error("登录已过期，请重新登录")
                409 -> VipResult.Error("该方案已激活，无需重复购买")
                else -> VipResult.Error("购买验证失败（${e.code()}），请联系客服")
            }
        } catch (e: Exception) {
            VipResult.Error("网络连接失败，请检查网络后重试")
        }
    }

    /**
     * 激活 7 天免费试用（每个账号仅限一次）。
     */
    suspend fun activateTrial(): VipResult {
        val token = appPreferences.savedToken ?: return VipResult.Error("请先登录")
        return try {
            val resp = api.activateTrial("Bearer $token")
            if (resp.success) {
                appPreferences.saveVipStatus(resp.vipLevel, resp.expireTime)
                VipResult.Success(
                    vipLevel = resp.vipLevel,
                    expireTime = resp.expireTime,
                    message = "已激活 7 天高级版免费试用 🎉"
                )
            } else {
                VipResult.Error(resp.error ?: "激活失败")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                409 -> VipResult.Error("每个账号仅可使用一次免费试用")
                401 -> VipResult.Error("登录已过期，请重新登录")
                else -> VipResult.Error("激活失败（${e.code()}）")
            }
        } catch (e: Exception) {
            VipResult.Error("网络连接失败，请检查网络后重试")
        }
    }

    // ── 工具方法 ────────────────────────────────────────────────────────────

    /** 构建 VIP 状态描述文本 */
    private fun buildStatusMessage(resp: VipStatusResponse): String {
        return when (resp.vipLevel) {
            0 -> "当前为免费版"
            1 -> {
                if (resp.expireTime == 0L) "标准版 · 永久有效"
                else {
                    val days = ((resp.expireTime - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                    "标准版 · 剩余 $days 天"
                }
            }
            2 -> {
                if (resp.expireTime == 0L) "高级版 · 永久有效"
                else {
                    val days = ((resp.expireTime - System.currentTimeMillis()) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                    "高级版 · 剩余 $days 天"
                }
            }
            else -> "未知状态"
        }
    }

    /**
     * 使用 Google Play 促销码（Promo Code）开通会员。
     *
     * ⚠️  Google Play 合规说明：
     *  - 此功能仅应接受通过 Google Play Console 生成的官方 Promo Code，
     *    不得接受绕过 Play Billing 进行付费的自定义激活码。
     *  - 服务端在收到此请求后，应先通过 Google Play Developer API 验证
     *    该 Promo Code 的有效性（Promo Code 本质上对应一个免费 SKU）。
     *  - 若通过线下渠道销售激活码（等价于付费），则违反 Google Play 政策。
     *
     * @param activationCode 用户从 Google Play 获取的促销码
     */
    suspend fun redeemActivationCode(activationCode: String): VipResult {
        val code = activationCode.trim().uppercase()
        if (code.isBlank()) return VipResult.Error("请输入激活码")
        if (code.length < 4) return VipResult.Error("激活码格式不正确")
        val token = appPreferences.savedToken ?: return VipResult.Error("请先登录后再使用激活码")
        return try {
            val resp = api.redeemActivationCode("Bearer $token", ActivationCodeRequest(code))
            if (resp.success) {
                // 本地更新 VIP 状态
                appPreferences.saveVipStatus(resp.vipLevel, resp.expireTime)
                VipResult.Success(
                    vipLevel = resp.vipLevel,
                    expireTime = resp.expireTime,
                    message = resp.rewardMessage.ifBlank { "激活成功！会员已开通 🎉" }
                )
            } else {
                VipResult.Error(resp.error ?: "激活失败，请检查激活码是否正确")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                400 -> VipResult.Error("激活码不存在或格式错误")
                401 -> VipResult.Error("登录已过期，请重新登录")
                409 -> VipResult.Error("该激活码已被使用")
                else -> VipResult.Error("激活失败（${e.code()}），请稍后重试")
            }
        } catch (e: Exception) {
            VipResult.Error("网络连接失败，请检查网络后重试")
        }
    }

    /** 获取当前 VIP 状态描述文本（不请求网络，仅读本地缓存） */
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
}
