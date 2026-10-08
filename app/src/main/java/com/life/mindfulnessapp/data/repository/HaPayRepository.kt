package com.life.mindfulnessapp.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.provider.Settings
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.PendingHaOrder
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.EarlyBirdPricing
import com.life.mindfulnessapp.data.network.HaCreateOrderRequest
import com.life.mindfulnessapp.data.network.HaEarlyBirdMeta
import com.life.mindfulnessapp.data.network.HaNotifyPaidRequest
import com.life.mindfulnessapp.data.network.HaPayPlanDto
import com.life.mindfulnessapp.data.network.HaPendingOrderDto
import com.life.mindfulnessapp.data.network.HaRestoreRequest
import com.life.mindfulnessapp.data.network.VipPlan
import com.life.mindfulnessapp.data.network.WxAppPayParams
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

data class HaPlansSnapshot(
    val earlyBird: HaEarlyBirdMeta = HaEarlyBirdMeta(),
    val plans: Map<VipPlan, HaPayPlanDto> = emptyMap()
)

sealed class HaPayCreateResult {
    data class Success(
        val orderNo: String,
        val amountYuan: String,
        val listAmountYuan: String,
        val planId: String,
        val payMode: String,
        /** 可直接 Intent 打开：H5 链接或 weixin:// Native 码 */
        val payUrl: String,
        val qrCodeUrl: String,
        val appPay: WxAppPayParams?,
        val contactWechat: String,
        val message: String,
        val qrBitmap: Bitmap?,
        val earlyBird: Boolean = false,
        val tierLabel: String = "",
        val remarkHint: String = "",
        val benefitSummary: String = "",
        val orderStatus: String = "pending"
    ) : HaPayCreateResult()

    data class Error(val message: String) : HaPayCreateResult()
}

sealed class HaPayNotifyResult {
    data class Ok(
        val paid: Boolean,
        val message: String,
        val vipLevel: Int = 0,
        val expireTime: Long = 0L
    ) : HaPayNotifyResult()
    data class Error(val message: String) : HaPayNotifyResult()
}

data class HaDeviceSyncResult(
    val vipApplied: Boolean,
    val vipLevel: Int,
    val expireTime: Long,
    val pending: PendingHaOrder?,
    val error: String? = null,
    val revoked: Boolean = false
)

sealed class HaRestoreResult {
    data class Restored(
        val vipLevel: Int,
        val expireTime: Long,
        val rebound: Boolean,
        val message: String
    ) : HaRestoreResult()
    data class PendingOrder(val pending: PendingHaOrder, val message: String) : HaRestoreResult()
    data class Error(val message: String) : HaRestoreResult()
}

sealed class HaPayStatusResult {
    data class Pending(val status: String) : HaPayStatusResult()
    data class Paid(val vipLevel: Int, val expireTime: Long, val planId: String) : HaPayStatusResult()
    data class Error(val message: String) : HaPayStatusResult()
}

/**
 * 官网渠道会员支付：优先微信 APP 支付（OpenSDK），失败由服务端回落 H5 / Native。
 */
@Singleton
class HaPayRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val appPreferences: AppPreferences
) {

    fun deviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return androidId?.takeIf { it.isNotBlank() } ?: "unknown-${Build.MODEL}"
    }

    /** 拉取价目（含早鸟）；失败时用本地规则兜底 */
    suspend fun fetchPlans(): HaPlansSnapshot {
        return try {
            val resp = apiService.getVipPlans(deviceId())
            if (!resp.success) return localPlansFallback()
            val map = resp.plans.mapNotNull { dto ->
                val plan = VipPlan.entries.firstOrNull {
                    it.productId == dto.product_id || it.name.equals(dto.plan_id, ignoreCase = true)
                } ?: return@mapNotNull null
                plan to dto
            }.toMap()
            val meta = resp.early_bird ?: HaEarlyBirdMeta()
            if (meta.eligible && !meta.redeemed_at.isNullOrBlank()) {
                // 有服务端兑换时间则写回本地（供离线兜底）
                parseServerTimeMs(meta.redeemed_at)?.let { appPreferences.setBetaRedeemedAt(it) }
            }
            HaPlansSnapshot(earlyBird = meta, plans = map)
        } catch (_: Exception) {
            localPlansFallback()
        }
    }

    /** 同步本地早鸟估算（进页瞬间展示，再被 [fetchPlans] 覆盖） */
    fun localPlansSnapshot(): HaPlansSnapshot = localPlansFallback()

    private fun localPlansFallback(): HaPlansSnapshot {
        val meta = computeLocalEarlyBirdMeta()
        val map = VipPlan.entries.associateWith { plan ->
            val listFen = planListFen(plan)
            val fen = localEarlyBirdFen(plan, meta, listFen)
            HaPayPlanDto(
                plan_id = plan.name.lowercase(),
                product_id = plan.productId,
                title = plan.titleZh,
                days = plan.durationDays,
                amount_fen = fen,
                list_fen = listFen,
                amount_yuan = formatYuan(fen),
                list_yuan = formatYuan(listFen),
                early_bird = fen < listFen,
                save_fen = (listFen - fen).coerceAtLeast(0),
                tier = meta.tier,
                tier_label = meta.tier_label
            )
        }
        return HaPlansSnapshot(earlyBird = meta, plans = map)
    }

    private fun computeLocalEarlyBirdMeta(): HaEarlyBirdMeta {
        if (!appPreferences.isBetaUnlocked()) return HaEarlyBirdMeta()
        if (appPreferences.isLifetimeVip()) return HaEarlyBirdMeta()
        val expire = appPreferences.vipExpireTime.value
        val now = System.currentTimeMillis()
        val dayMs = 24L * 60 * 60 * 1000
        var redeemedAt = appPreferences.getBetaRedeemedAt()
        // 旧用户无兑换时间：用「到期前 90 天」反推
        if (redeemedAt <= 0L && expire > now) {
            redeemedAt = expire - 90L * dayMs
            if (redeemedAt > 0L) appPreferences.setBetaRedeemedAt(redeemedAt)
        }
        if (redeemedAt <= 0L) return HaEarlyBirdMeta()
        val dayIndex = ((now - redeemedAt) / dayMs).toInt().coerceAtLeast(0)
        val periodEndsAt = if (expire > 0L) expire else redeemedAt + 90L * dayMs
        val daysInPeriod = (((periodEndsAt - redeemedAt) / dayMs).toInt()).coerceAtLeast(1)
        if (now >= periodEndsAt || dayIndex >= daysInPeriod) {
            return HaEarlyBirdMeta(
                eligible = false,
                day_index = dayIndex,
                days_in_period = daysInPeriod,
                days_left_in_period = 0,
                period_ends_at = periodEndsAt
            )
        }
        val (tier, label, tierEnd) = when {
            dayIndex < 14 -> Triple("super", "超早鸟", 14)
            dayIndex < 45 -> Triple("early", "早鸟", 45)
            dayIndex < 90 && dayIndex < daysInPeriod -> Triple("late", "临近结束", minOf(90, daysInPeriod))
            else -> return HaEarlyBirdMeta(
                eligible = false,
                day_index = dayIndex,
                days_in_period = daysInPeriod,
                days_left_in_period = ((periodEndsAt - now + dayMs - 1) / dayMs).toInt().coerceAtLeast(0),
                period_ends_at = periodEndsAt
            )
        }
        val daysLeft = ((periodEndsAt - now + dayMs - 1) / dayMs).toInt().coerceAtLeast(0)
        val nextIn = (tierEnd - dayIndex).coerceAtLeast(0)
        val nextLabel = when (tier) {
            "super" -> "早鸟"
            "early" -> "临近结束"
            else -> null
        }
        return HaEarlyBirdMeta(
            eligible = true,
            tier = tier,
            tier_label = label,
            day_index = dayIndex,
            days_in_period = daysInPeriod,
            days_left_in_period = daysLeft,
            next_tier_in_days = if (nextLabel != null && nextIn > 0) nextIn else null,
            next_tier_label = nextLabel,
            period_ends_at = periodEndsAt
        )
    }

    private fun localEarlyBirdFen(plan: VipPlan, meta: HaEarlyBirdMeta, listFen: Int): Int {
        if (!meta.eligible) return listFen
        return EarlyBirdPricing.fenFor(plan, meta.tier).coerceAtMost(listFen)
    }

    private fun planListFen(plan: VipPlan): Int = EarlyBirdPricing.listFen(plan)

    private fun defaultBenefitSummary(plan: VipPlan): String {
        val upgradingGift =
            appPreferences.isBetaUnlocked() &&
                appPreferences.isVipActive() &&
                !appPreferences.isLifetimeVip()
        return when (plan) {
            VipPlan.LIFETIME -> if (upgradingGift) {
                "永久会员自确认到账起生效；坑位与能力不变，会员码剩余天数不另叠加"
            } else {
                "永久无限 App 管理坑位 + 持续更新能力（一次买断）"
            }
            VipPlan.YEARLY -> if (upgradingGift) {
                "年卡自确认到账起计 365 天；坑位与能力不变，会员码剩余天数不另叠加"
            } else {
                "年卡无限 App 管理坑位 + 持续更新能力（365 天）"
            }
            VipPlan.QUARTERLY -> "季卡无限 App 管理坑位 + 持续更新能力（90 天）"
            VipPlan.MONTHLY -> "月卡无限 App 管理坑位 + 持续更新能力（30 天）"
        }
    }

    private fun formatYuan(fen: Int): String = String.format("%.2f", fen / 100.0)

    private fun parseServerTimeMs(value: String): Long? {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return null
        trimmed.toLongOrNull()?.let { return it }
        return try {
            val normalized = if (trimmed.contains('T')) trimmed else trimmed.replace(' ', 'T')
            java.time.LocalDateTime.parse(normalized)
                .atZone(java.time.ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        } catch (_: Exception) {
            null
        }
    }

    suspend fun createOrder(
        plan: VipPlan,
        payPrefer: String = "manual",
        phone: String = ""
    ): HaPayCreateResult {
        val bound = phone.ifBlank { appPreferences.getBoundPhone() }
        if (bound.isBlank()) {
            return HaPayCreateResult.Error("请填写手机号，便于换机后恢复开通")
        }
        appPreferences.setBoundPhone(bound)
        return try {
            val resp = apiService.createVipOrder(
                HaCreateOrderRequest(
                    device_id = deviceId(),
                    plan_id = plan.productId,
                    device_model = "${Build.MANUFACTURER} ${Build.MODEL}".trim(),
                    app_version = BuildConfig.VERSION_NAME,
                    pay_prefer = payPrefer,
                    phone = bound
                )
            )
            if (!resp.success || resp.order_no.isNullOrBlank()) {
                return HaPayCreateResult.Error(resp.error ?: resp.message ?: "下单失败")
            }
            val payMode = resp.pay_mode ?: "manual"
            val appPay = resp.wx_pay?.takeIf {
                it.appId.isNotBlank() && it.prepayId.isNotBlank() && it.sign.isNotBlank()
            }
            val payUrl = listOf(resp.pay_url, resp.mweb_url, resp.qr_code_url)
                .firstOrNull { !it.isNullOrBlank() }
                .orEmpty()
            val qrUrl = when (payMode) {
                "wxpay_h5", "wxpay_app" -> ""
                else -> resp.qr_code_url.orEmpty().ifBlank { payUrl }
            }
            val amountYuan = prettyYuan(resp.amount_yuan, resp.amount_fen, plan)
            val listYuan = when {
                resp.list_yuan != null -> prettyYuan(resp.list_yuan, resp.list_fen, plan)
                resp.list_fen > 0 -> formatYuan(resp.list_fen).trimEnd('0').trimEnd('.')
                else -> ""
            }
            HaPayCreateResult.Success(
                orderNo = resp.order_no,
                amountYuan = amountYuan,
                listAmountYuan = listYuan,
                planId = resp.plan_id ?: plan.name.lowercase(),
                payMode = payMode,
                payUrl = payUrl,
                qrCodeUrl = qrUrl,
                appPay = appPay,
                contactWechat = resp.contact_wechat?.takeIf { it.isNotBlank() }
                    ?: AppPreferences.CONTACT_WECHAT,
                message = resp.message.orEmpty().ifBlank { resp.instruction.orEmpty() },
                qrBitmap = if (qrUrl.isNotBlank()) encodeQrBitmap(qrUrl) else null,
                earlyBird = resp.early_bird,
                tierLabel = resp.tier_label.orEmpty(),
                remarkHint = resp.remark_hint.orEmpty().ifBlank {
                    "心锚 ${plan.name.lowercase()} ¥$amountYuan"
                },
                benefitSummary = resp.benefit_summary.orEmpty().ifBlank {
                    defaultBenefitSummary(plan)
                },
                orderStatus = resp.status.orEmpty().ifBlank { "pending" }
            )
        } catch (e: Exception) {
            HaPayCreateResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试")
        }
    }

    suspend fun notifyPaid(orderNo: String): HaPayNotifyResult {
        return try {
            val resp = apiService.notifyVipPaid(
                HaNotifyPaidRequest(
                    order_no = orderNo,
                    device_id = deviceId(),
                    phone = appPreferences.getBoundPhone()
                )
            )
            if (!resp.success) {
                return HaPayNotifyResult.Error(resp.error ?: resp.message ?: "申报失败")
            }
            HaPayNotifyResult.Ok(
                paid = resp.paid,
                message = resp.message ?: if (resp.paid) "已开通" else "已收到申报，请等待确认",
                vipLevel = resp.vip_level,
                expireTime = resp.expire_time
            )
        } catch (e: Exception) {
            HaPayNotifyResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "网络异常")
        }
    }

    private fun prettyYuan(raw: String?, fen: Int, plan: VipPlan): String {
        val fromRaw = raw?.trimStart('¥')?.trim().orEmpty()
        if (fromRaw.isNotBlank()) {
            val n = fromRaw.toDoubleOrNull()
            return if (n != null && n == n.toLong().toDouble()) n.toLong().toString() else fromRaw
        }
        if (fen > 0) {
            val n = fen / 100.0
            return if (n == n.toLong().toDouble()) n.toLong().toString() else String.format("%.2f", n)
        }
        return plan.listPriceCny.removePrefix("¥")
    }

    suspend fun pollOrder(orderNo: String): HaPayStatusResult {
        return try {
            val resp = apiService.getVipOrderStatus(orderNo, deviceId())
            if (!resp.success) {
                return HaPayStatusResult.Error(resp.error ?: "查询失败")
            }
            if (resp.paid) {
                applyPaidGrant(orderNo, resp.vip_level, resp.expire_time)
                HaPayStatusResult.Paid(
                    vipLevel = resp.vip_level.coerceAtLeast(1),
                    expireTime = resp.expire_time,
                    planId = resp.plan_id.orEmpty()
                )
            } else {
                HaPayStatusResult.Pending(resp.status ?: "pending")
            }
        } catch (e: Exception) {
            HaPayStatusResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "网络异常")
        }
    }

    fun isWebsiteChannel(): Boolean = BuildConfig.DISTRIBUTION_CHANNEL != "play"

    fun savePendingOrder(order: PendingHaOrder) {
        if (order.orderNo.isBlank()) return
        appPreferences.savePendingHaOrder(order)
    }

    fun markPendingNotified() {
        appPreferences.markPendingHaOrderNotified()
    }

    fun pendingOrder(): PendingHaOrder? = appPreferences.getPendingHaOrder()

    fun applyPaidFromNotify(orderNo: String, vipLevel: Int, expireTime: Long) {
        if (vipLevel <= 0) return
        applyPaidGrant(orderNo, vipLevel, expireTime)
    }

    /**
     * 官网渠道：按设备拉取已开通 VIP 与未完成订单。
     * Play 渠道不请求。本地已是永久会员时不会被较短权益覆盖。
     */
    suspend fun syncDeviceVip(): HaDeviceSyncResult {
        val localLevel = appPreferences.getVipLevel()
        val localExpire = appPreferences.vipExpireTime.value
        val localPending = appPreferences.getPendingHaOrder()
        if (!isWebsiteChannel()) {
            return HaDeviceSyncResult(
                vipApplied = false,
                vipLevel = localLevel,
                expireTime = localExpire,
                pending = localPending
            )
        }
        return try {
            val resp = apiService.getVipDeviceStatus(deviceId())
            if (!resp.success) {
                return fallbackPollPending(
                    localLevel, localExpire, localPending,
                    resp.error ?: "查询失败"
                )
            }
            val serverVip = resp.vip
            var applied = false
            var level = localLevel
            var expire = localExpire
            if (serverVip != null && shouldApplyServerVip(
                    serverVip.vip_level, serverVip.expire_time
                )
            ) {
                val code = serverVip.code?.takeIf { it.isNotBlank() }
                    ?: if (serverVip.source == "invite") appPreferences.getBetaCode()
                    else "WX:SYNC"
                appPreferences.setBetaUnlocked(true, code)
                appPreferences.saveVipStatus(
                    serverVip.vip_level.coerceAtLeast(1),
                    serverVip.expire_time
                )
                applied = true
                level = serverVip.vip_level.coerceAtLeast(1)
                expire = serverVip.expire_time
            } else if (serverVip == null || !serverVip.active) {
                if (localLevel > 0) {
                    appPreferences.clearVipStatus()
                    level = 0
                    expire = 0L
                }
            }
            val pending = if (applied || appPreferences.isVipActive()) {
                appPreferences.clearPendingHaOrder()
                null
            } else {
                mergePendingFromServer(resp.pending_order, localPending)
            }
            if (pending == null && localPending != null && !applied) {
                when (val polled = pollOrder(localPending.orderNo)) {
                    is HaPayStatusResult.Paid -> {
                        return HaDeviceSyncResult(
                            vipApplied = true,
                            vipLevel = polled.vipLevel,
                            expireTime = polled.expireTime,
                            pending = null
                        )
                    }
                    else -> Unit
                }
            }
            HaDeviceSyncResult(
                vipApplied = applied,
                vipLevel = level,
                expireTime = expire,
                pending = pending
            )
        } catch (e: Exception) {
            fallbackPollPending(
                localLevel, localExpire, localPending,
                e.message?.takeIf { it.isNotBlank() } ?: "网络异常"
            )
        }
    }

    private suspend fun fallbackPollPending(
        localLevel: Int,
        localExpire: Long,
        localPending: PendingHaOrder?,
        error: String
    ): HaDeviceSyncResult {
        val orderNo = localPending?.orderNo
        if (orderNo.isNullOrBlank()) {
            return HaDeviceSyncResult(
                vipApplied = false,
                vipLevel = localLevel,
                expireTime = localExpire,
                pending = localPending,
                error = error
            )
        }
        return when (val polled = pollOrder(orderNo)) {
            is HaPayStatusResult.Paid -> HaDeviceSyncResult(
                vipApplied = true,
                vipLevel = polled.vipLevel,
                expireTime = polled.expireTime,
                pending = null
            )
            else -> HaDeviceSyncResult(
                vipApplied = false,
                vipLevel = localLevel,
                expireTime = localExpire,
                pending = localPending,
                error = error
            )
        }
    }

    private fun mergePendingFromServer(
        dto: HaPendingOrderDto?,
        local: PendingHaOrder?
    ): PendingHaOrder? {
        if (dto == null || dto.order_no.isBlank()) return local
        val merged = PendingHaOrder(
            orderNo = dto.order_no,
            planId = dto.plan_id.ifBlank { local?.planId.orEmpty() },
            planTitle = dto.plan_title.ifBlank { local?.planTitle.orEmpty() },
            amountYuan = dto.amount_yuan.ifBlank { local?.amountYuan.orEmpty() },
            listAmountYuan = local?.listAmountYuan.orEmpty(),
            earlyBird = local?.earlyBird == true,
            tierLabel = local?.tierLabel.orEmpty(),
            contactWechat = dto.contact_wechat.ifBlank {
                local?.contactWechat?.ifBlank { AppPreferences.CONTACT_WECHAT }
                    ?: AppPreferences.CONTACT_WECHAT
            },
            remarkText = local?.remarkText?.ifBlank { null }
                ?: "${dto.order_no} ${dto.remark_hint}".trim(),
            benefitSummary = local?.benefitSummary.orEmpty(),
            notified = dto.status == "notified" || local?.notified == true,
            createdAt = local?.createdAt ?: System.currentTimeMillis(),
            phone = local?.phone?.ifBlank { null } ?: appPreferences.getBoundPhone()
        )
        appPreferences.savePendingHaOrder(merged)
        return merged
    }

    private fun shouldApplyServerVip(serverLevel: Int, serverExpire: Long): Boolean {
        if (serverLevel <= 0) return false
        val now = System.currentTimeMillis()
        val serverActive = serverExpire == 0L || serverExpire > now
        if (!serverActive) return false
        val localLevel = appPreferences.getVipLevel()
        val localExpire = appPreferences.vipExpireTime.value
        val localActive = localLevel > 0 && (localExpire == 0L || localExpire > now)
        if (!localActive) return true
        if (localExpire == 0L) return false
        if (serverExpire == 0L) return true
        return serverExpire > localExpire
    }

    private fun applyPaidGrant(orderNo: String, level: Int, expire: Long) {
        appPreferences.setBetaUnlocked(true, "WX:${orderNo.takeLast(8)}")
        appPreferences.saveVipStatus(level.coerceAtLeast(1), expire)
        appPreferences.clearPendingHaOrder()
    }

    suspend fun restoreByPhone(rawPhone: String): HaRestoreResult {
        val phone = rawPhone.filter { it.isDigit() }.let { digits ->
            when {
                digits.length == 11 && digits.startsWith("1") -> digits
                digits.length == 13 && digits.startsWith("86") -> digits.substring(2)
                else -> digits.takeLast(11).takeIf { it.length == 11 && it.startsWith("1") } ?: ""
            }
        }
        if (phone.isBlank()) return HaRestoreResult.Error("请输入有效的手机号")
        return try {
            val resp = apiService.restoreVipByPhone(
                HaRestoreRequest(device_id = deviceId(), phone = phone)
            )
            if (!resp.success) {
                return HaRestoreResult.Error(resp.error ?: resp.message ?: "恢复失败")
            }
            appPreferences.setBoundPhone(phone)
            if (resp.paid && resp.vip_level > 0) {
                applyPaidGrant("RESTORE", resp.vip_level, resp.expire_time)
                return HaRestoreResult.Restored(
                    vipLevel = resp.vip_level,
                    expireTime = resp.expire_time,
                    rebound = resp.rebound,
                    message = resp.message ?: if (resp.rebound) "已转到这台手机" else "已恢复开通"
                )
            }
            val pending = mergePendingFromServer(resp.pending_order, appPreferences.getPendingHaOrder())
            if (pending != null) {
                HaRestoreResult.PendingOrder(
                    pending = pending,
                    message = resp.message ?: "找到待确认订单"
                )
            } else {
                HaRestoreResult.Error(resp.message ?: "未找到该手机号的开通记录")
            }
        } catch (e: Exception) {
            HaRestoreResult.Error(e.message?.takeIf { it.isNotBlank() } ?: "网络异常")
        }
    }

    private fun encodeQrBitmap(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.CHARACTER_SET to "UTF-8",
                EncodeHintType.MARGIN to 1
            )
            val matrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            for (x in 0 until size) {
                for (y in 0 until size) {
                    bmp.setPixel(x, y, if (matrix[x, y]) 0xFF111111.toInt() else 0xFFFFFFFF.toInt())
                }
            }
            bmp
        } catch (_: Exception) {
            null
        }
    }
}
