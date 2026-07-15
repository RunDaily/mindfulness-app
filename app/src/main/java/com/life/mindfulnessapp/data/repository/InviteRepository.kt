package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.RedeemInviteRequest
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

// ════════════════════════════════════════════
//  邀请码操作结果
// ════════════════════════════════════════════

sealed class InviteResult {
    data class Success(val message: String) : InviteResult()
    data class Error(val message: String) : InviteResult()
}

// ════════════════════════════════════════════
//  InviteInfo — 本地展示用
// ════════════════════════════════════════════

data class InviteInfo(
    /** 当前用户自己的邀请码 */
    val myCode: String,
    /** 已成功邀请人数 */
    val invitedCount: Int,
    /** 奖励说明文本 */
    val rewardSummary: String,
    /** 是否已使用过别人的邀请码 */
    val hasRedeemed: Boolean
)

// ════════════════════════════════════════════
//  InviteRepository
// ════════════════════════════════════════════

/**
 * 邀请码核心逻辑：
 *
 * 规则：
 *  - 被邀请的新用户注册后填写邀请码 → 试用从 7 天延长到 14 天
 *  - 邀请人每成功邀请 1 人 → 获得 7 天标准版延期，上限 3 人（共 21 天）
 *  - 每个账号仅能使用一次别人的邀请码
 */
@Singleton
class InviteRepository @Inject constructor(
    private val appPreferences: AppPreferences,
    private val api: ApiService
) {

    // ── 读取本地缓存 ──────────────────────────────────────────────────────

    /** 读取本地缓存的邀请信息（无需网络）*/
    fun getLocalInviteInfo(): InviteInfo = InviteInfo(
        myCode       = appPreferences.myInviteCode,
        invitedCount = appPreferences.invitedCount,
        rewardSummary = buildLocalRewardSummary(appPreferences.invitedCount),
        hasRedeemed  = appPreferences.hasRedeemedInvite
    )

    /** 当前用户自己的邀请码（空字符串表示尚未从服务端获取） */
    fun getMyCode(): String = appPreferences.myInviteCode

    /** 是否已使用过他人邀请码 */
    fun hasRedeemed(): Boolean = appPreferences.hasRedeemedInvite

    // ── 服务端操作 ────────────────────────────────────────────────────────

    /**
     * 从服务端拉取最新邀请信息（包括自己的邀请码和邀请统计），并更新本地缓存。
     * 应在：打开邀请页面时、登录后调用。
     */
    suspend fun fetchInviteInfo(): InviteResult {
        val token = appPreferences.savedToken ?: return InviteResult.Error("请先登录")
        return try {
            val resp = api.getInviteInfo("Bearer $token")
            if (resp.success) {
                // 更新本地缓存
                if (resp.inviteCode.isNotBlank()) {
                    appPreferences.myInviteCode = resp.inviteCode
                }
                appPreferences.invitedCount = resp.invitedCount
                InviteResult.Success(resp.rewardSummary.ifBlank {
                    buildLocalRewardSummary(resp.invitedCount)
                })
            } else {
                InviteResult.Error(resp.error ?: "获取邀请信息失败")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                401 -> InviteResult.Error("登录已过期，请重新登录")
                else -> InviteResult.Error("获取失败（${e.code()}）")
            }
        } catch (e: Exception) {
            // 网络失败时使用本地缓存，不报错
            InviteResult.Success(buildLocalRewardSummary(appPreferences.invitedCount))
        }
    }

    /**
     * 兑换邀请码（新用户注册后填写邀请人的邀请码）。
     * 成功后本地标记 hasRedeemedInvite = true，并更新 VIP 状态。
     */
    suspend fun redeemInviteCode(
        inviteCode: String,
        vipRepository: VipRepository
    ): InviteResult {
        val code = inviteCode.trim().uppercase()
        if (code.isBlank()) return InviteResult.Error("请输入邀请码")
        if (code.length < 4) return InviteResult.Error("邀请码格式不正确")
        val token = appPreferences.savedToken ?: return InviteResult.Error("请先登录")
        if (appPreferences.hasRedeemedInvite) {
            return InviteResult.Error("每个账号只能使用一次邀请码")
        }
        return try {
            val resp = api.redeemInviteCode("Bearer $token", RedeemInviteRequest(code))
            if (resp.success) {
                appPreferences.hasRedeemedInvite = true
                // 顺带更新 VIP 状态（服务端兑换时会延长试用期）
                if (resp.vipLevel > 0 || resp.expireTime > 0L) {
                    appPreferences.saveVipStatus(resp.vipLevel, resp.expireTime)
                }
                // 静默刷新一次服务端 VIP 状态确保最新
                vipRepository.fetchVipStatus()
                InviteResult.Success(
                    resp.rewardMessage.ifBlank { "兑换成功！试用期已延长至 14 天 🎉" }
                )
            } else {
                InviteResult.Error(resp.error ?: "兑换失败，请检查邀请码是否正确")
            }
        } catch (e: HttpException) {
            when (e.code()) {
                400 -> InviteResult.Error("邀请码不存在或已失效")
                409 -> InviteResult.Error("该邀请码已被使用，或不能使用自己的邀请码")
                401 -> InviteResult.Error("登录已过期，请重新登录")
                else -> InviteResult.Error("兑换失败（${e.code()}），请稍后重试")
            }
        } catch (e: Exception) {
            InviteResult.Error("网络连接失败，请检查网络后重试")
        }
    }

    // ── 工具方法 ──────────────────────────────────────────────────────────

    /** 根据已邀请人数生成本地奖励摘要文本 */
    private fun buildLocalRewardSummary(count: Int): String = when {
        count <= 0 -> "邀请好友，各获 7 天 VIP 奖励"
        count >= 3 -> "已邀请 $count 人 · 已获最大奖励（21 天 VIP）🎉"
        else -> "已成功邀请 $count 人 · 获得 ${count * 7} 天 VIP 延期"
    }
}
