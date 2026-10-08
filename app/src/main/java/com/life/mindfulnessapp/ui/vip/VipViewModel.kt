package com.life.mindfulnessapp.ui.vip

import android.app.Activity
import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.billing.BillingManager
import com.life.mindfulnessapp.billing.BillingResult2
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.PendingHaOrder
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.network.HaEarlyBirdMeta
import com.life.mindfulnessapp.data.network.HaPayPlanDto
import com.life.mindfulnessapp.data.network.VipPlan
import com.life.mindfulnessapp.data.network.WxAppPayParams
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.HaPayCreateResult
import com.life.mindfulnessapp.data.repository.HaPayNotifyResult
import com.life.mindfulnessapp.data.repository.HaPayRepository
import com.life.mindfulnessapp.data.repository.HaPayStatusResult
import com.life.mindfulnessapp.data.repository.HaRestoreResult
import com.life.mindfulnessapp.data.repository.HaPlansSnapshot
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.data.repository.VipResult
import com.life.mindfulnessapp.pay.WeChatPayHelper
import com.life.mindfulnessapp.util.PhoneNumberHintHelper
import dagger.hilt.android.lifecycle.HiltViewModel
import com.tencent.mm.opensdk.modelbase.BaseResp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ManualCheckoutUi(
    val visible: Boolean = false,
    val creating: Boolean = false,
    val planTitle: String = "",
    val amountYuan: String = "",
    val listAmountYuan: String = "",
    val earlyBird: Boolean = false,
    val tierLabel: String = "",
    val planId: String = "",
    val orderNo: String = "",
    val contactWechat: String = AppPreferences.CONTACT_WECHAT,
    val remarkText: String = "",
    val benefitSummary: String = "",
    val hint: String = "",
    val notified: Boolean = false,
    val polling: Boolean = false,
    val error: String? = null,
    val collectingPhone: Boolean = false,
    val phoneInput: String = "",
    val boundPhone: String = "",
    val submitting: Boolean = false,
    /** 已申报后仍要回看转账信息 */
    val showPayDetails: Boolean = false
)

data class RestorePhoneUi(
    val visible: Boolean = false,
    val phoneInput: String = "",
    val busy: Boolean = false,
    val error: String? = null
)

data class WxPayUi(
    val visible: Boolean = false,
    val creating: Boolean = false,
    val orderNo: String = "",
    val amountYuan: String = "",
    val planTitle: String = "",
    val payMode: String = "",
    /** 待系统 Intent 打开的支付链接（H5 / weixin://） */
    val payUrl: String = "",
    val appPay: WxAppPayParams? = null,
    val launchToken: Int = 0,
    val showQr: Boolean = false,
    val qrBitmap: Bitmap? = null,
    val contactWechat: String = "",
    val hint: String = "",
    val polling: Boolean = false,
    val error: String? = null
)

data class VipUiState(
    val vipLevel: Int = 0,
    val vipExpireTime: Long = 0L,
    val isVip: Boolean = false,
    val isPremium: Boolean = false,
    val isLifetimeVip: Boolean = false,
    /** 已兑换会员码（才可展示早鸟价） */
    val betaUnlocked: Boolean = false,
    /** 本机已领取/兑换过的会员码（未开通时用于页内入口提示） */
    val claimedInviteCode: String = "",
    val statusText: String = "免费版",
    val trialAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val toastMessage: String? = null,
    val purchasingPlan: VipPlan? = null,
    val productPrices: Map<VipPlan, String> = emptyMap(),
    /** 官网渠道展示用价目（未兑码时已剥离早鸟） */
    val serverPlans: Map<VipPlan, HaPayPlanDto> = emptyMap(),
    val earlyBird: HaEarlyBirdMeta = HaEarlyBirdMeta(),
    /** 仅会员码会员、且仍在赠送期内：可按早鸟价升年卡/永久 */
    val showEarlyBirdUpgrade: Boolean = false,
    val isWebsiteChannel: Boolean = BuildConfig.DISTRIBUTION_CHANNEL != "play",
    val manualCheckout: ManualCheckoutUi = ManualCheckoutUi(),
    val pendingOrder: ManualCheckoutUi? = null,
    val restoring: Boolean = false,
    val restorePhone: RestorePhoneUi = RestorePhoneUi(),
    val wxPay: WxPayUi = WxPayUi()
)

@HiltViewModel
class VipViewModel @Inject constructor(
    private val vipRepository: VipRepository,
    private val billingManager: BillingManager,
    private val appPreferences: AppPreferences,
    private val haPayRepository: HaPayRepository,
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VipUiState(
            vipLevel = appPreferences.getVipLevel(),
            vipExpireTime = appPreferences.vipExpireTime.value,
            isVip = appPreferences.isVipActive(),
            isPremium = appPreferences.isPremium(),
            isLifetimeVip = appPreferences.isLifetimeVip(),
            betaUnlocked = appPreferences.isBetaUnlocked(),
            claimedInviteCode = appPreferences.getClaimedInviteCode(),
            statusText = vipRepository.getLocalStatusText(),
            trialAvailable = vipRepository.isTrialAvailable(),
            isWebsiteChannel = BuildConfig.DISTRIBUTION_CHANNEL != "play",
            pendingOrder = haPayRepository.pendingOrder()?.toCheckoutUi()
        )
    )
    val uiState: StateFlow<VipUiState> = _uiState.asStateFlow()

    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            combine(
                vipRepository.vipLevel,
                vipRepository.vipExpireTime,
                appPreferences.claimedInviteCode,
                appPreferences.betaUnlocked
            ) { level, expire, claimed, unlocked ->
                _uiState.update {
                    val next = it.copy(
                        vipLevel = level,
                        vipExpireTime = expire,
                        isVip = appPreferences.isVipActive(),
                        isPremium = appPreferences.isPremium(),
                        isLifetimeVip = appPreferences.isLifetimeVip(),
                        betaUnlocked = unlocked,
                        claimedInviteCode = claimed,
                        statusText = vipRepository.getLocalStatusText(),
                        trialAvailable = vipRepository.isTrialAvailable()
                    )
                    next.copy(showEarlyBirdUpgrade = computeShowEarlyBirdUpgrade(next))
                }
            }.collect { }
        }

        viewModelScope.launch {
            billingManager.productDetailsMap.collect { detailsMap ->
                val prices = detailsMap.mapValues { (_, v) -> v.formattedPrice }
                    .entries
                    .mapNotNull { (productId, price) ->
                        VipPlan.entries.firstOrNull { it.productId == productId }?.let { plan ->
                            plan to price
                        }
                    }.toMap()
                _uiState.update { it.copy(productPrices = prices) }
            }
        }

        viewModelScope.launch {
            billingManager.purchaseResultFlow.collect { result ->
                handleBillingResult(result)
            }
        }

        viewModelScope.launch {
            WeChatPayHelper.payResults.collect { errCode ->
                when (errCode) {
                    BaseResp.ErrCode.ERR_OK -> {
                        // 以服务端回调/轮询为准；此处仅提示并加速查询
                        val orderNo = _uiState.value.wxPay.orderNo
                        if (orderNo.isNotBlank()) {
                            _uiState.update {
                                it.copy(
                                    wxPay = it.wxPay.copy(
                                        hint = "支付结果确认中…",
                                        error = null
                                    )
                                )
                            }
                            refreshWxPayStatus()
                        }
                    }
                    BaseResp.ErrCode.ERR_USER_CANCEL -> {
                        _uiState.update {
                            it.copy(
                                toastMessage = "已取消支付",
                                wxPay = it.wxPay.copy(hint = "可重新打开微信支付")
                            )
                        }
                    }
                    else -> {
                        _uiState.update {
                            it.copy(
                                toastMessage = "微信支付未完成，请重试",
                                wxPay = it.wxPay.copy(error = "微信支付未完成，请重试")
                            )
                        }
                    }
                }
            }
        }
    }

    fun refreshVipStatus() {
        when (val result = vipRepository.refreshLocalStatus()) {
            is VipResult.Success -> {
                _uiState.update {
                    val next = it.copy(
                        vipLevel = result.vipLevel,
                        vipExpireTime = result.expireTime,
                        isVip = appPreferences.isVipActive(),
                        isPremium = appPreferences.isPremium(),
                        isLifetimeVip = appPreferences.isLifetimeVip(),
                        betaUnlocked = appPreferences.isBetaUnlocked(),
                        statusText = vipRepository.getLocalStatusText(),
                        trialAvailable = vipRepository.isTrialAvailable()
                    )
                    next.copy(showEarlyBirdUpgrade = computeShowEarlyBirdUpgrade(next))
                }
            }
            is VipResult.Error -> Unit
        }
        if (!_uiState.value.isWebsiteChannel) {
            viewModelScope.launch { billingManager.queryAllProductDetails() }
        }
    }

    fun refreshPricing() {
        if (!_uiState.value.isWebsiteChannel) return
        viewModelScope.launch {
            applyPlansSnapshot(haPayRepository.fetchPlans())
        }
    }

    private fun applyPlansSnapshot(snap: HaPlansSnapshot) {
        val allowEarlyBird = appPreferences.isBetaUnlocked() &&
            !appPreferences.isLifetimeVip() &&
            snap.earlyBird.eligible
        val plans = if (allowEarlyBird) {
            snap.plans
        } else {
            // 未兑码 / 不在早鸟期：一律展示正式标价，避免接口误带早鸟字段
            snap.plans.mapValues { (_, dto) -> toListPricePlan(dto) }
        }
        val earlyBird = if (allowEarlyBird) snap.earlyBird else HaEarlyBirdMeta()
        _uiState.update {
            val next = it.copy(
                serverPlans = plans,
                earlyBird = earlyBird,
                betaUnlocked = appPreferences.isBetaUnlocked()
            )
            next.copy(showEarlyBirdUpgrade = computeShowEarlyBirdUpgrade(next))
        }
    }

    private fun toListPricePlan(dto: HaPayPlanDto): HaPayPlanDto {
        val listFen = dto.list_fen.takeIf { it > 0 } ?: dto.amount_fen
        val listYuan = dto.list_yuan.ifBlank { dto.amount_yuan }
        return dto.copy(
            amount_fen = listFen,
            amount_yuan = listYuan,
            early_bird = false,
            save_fen = 0,
            tier = null,
            tier_label = null
        )
    }

    private fun computeShowEarlyBirdUpgrade(state: VipUiState): Boolean {
        if (!state.isWebsiteChannel) return false
        if (state.isLifetimeVip) return false
        if (!appPreferences.isBetaUnlocked()) return false
        return state.earlyBird.eligible
    }

    fun launchPurchase(activity: Activity, plan: VipPlan) {
        if (_uiState.value.isWebsiteChannel) {
            startManualCheckout(plan)
            return
        }
        _uiState.update { it.copy(purchasingPlan = plan) }
        val launched = billingManager.launchBillingFlow(activity, plan)
        if (!launched) {
            _uiState.update { it.copy(purchasingPlan = null) }
        }
    }

    /** 官网渠道：创建人工收款订单（早鸟价仅兑码用户由服务端锁定）。 */
    fun startManualCheckout(plan: VipPlan) {
        pollJob?.cancel()
        analyticsRepository.trackVipPayClick(plan.productId)
        val phone = appPreferences.getBoundPhone()
        if (phone.isBlank()) {
            _uiState.update {
                it.copy(
                    purchasingPlan = plan,
                    wxPay = WxPayUi(),
                    manualCheckout = ManualCheckoutUi(
                        visible = true,
                        creating = false,
                        collectingPhone = true,
                        planTitle = plan.titleZh,
                        planId = plan.productId,
                        phoneInput = it.manualCheckout.phoneInput,
                        hint = "填写手机号后生成订单，换机可用此号恢复。"
                    )
                )
            }
            return
        }
        createManualOrder(plan, phone)
    }

    fun onCheckoutPhoneChange(value: String) {
        _uiState.update {
            it.copy(manualCheckout = it.manualCheckout.copy(phoneInput = value.filter { ch ->
                ch.isDigit() || ch == '+'
            }.take(13), error = null))
        }
    }

    fun submitCheckoutPhone() {
        val plan = _uiState.value.purchasingPlan
            ?: VipPlan.entries.firstOrNull { it.productId == _uiState.value.manualCheckout.planId }
            ?: return
        val phone = PhoneNumberHintHelper.normalizeCnMobile(_uiState.value.manualCheckout.phoneInput)
        if (phone.isNullOrBlank()) {
            _uiState.update {
                it.copy(manualCheckout = it.manualCheckout.copy(error = "请输入有效的手机号"))
            }
            return
        }
        createManualOrder(plan, phone)
    }

    private fun createManualOrder(plan: VipPlan, phone: String) {
        pollJob?.cancel()
        _uiState.update {
            it.copy(
                purchasingPlan = plan,
                manualCheckout = it.manualCheckout.copy(
                    visible = true,
                    creating = true,
                    collectingPhone = false,
                    planTitle = plan.titleZh,
                    planId = plan.productId,
                    boundPhone = phone,
                    phoneInput = phone,
                    error = null,
                    hint = "正在生成订单…"
                ),
                wxPay = WxPayUi()
            )
        }
        viewModelScope.launch {
            when (val result = haPayRepository.createOrder(plan, payPrefer = "manual", phone = phone)) {
                is HaPayCreateResult.Success -> {
                    analyticsRepository.trackVipIntentShow(plan.productId, result.orderNo)
                    val notified = result.orderStatus == "notified" ||
                        result.orderStatus == "paid" ||
                        haPayRepository.pendingOrder()?.takeIf { it.orderNo == result.orderNo }?.notified == true
                    _uiState.update {
                        it.copy(
                            manualCheckout = ManualCheckoutUi(
                                visible = true,
                                creating = false,
                                planTitle = plan.titleZh,
                                amountYuan = result.amountYuan,
                                listAmountYuan = result.listAmountYuan,
                                earlyBird = result.earlyBird,
                                tierLabel = result.tierLabel,
                                planId = plan.productId,
                                orderNo = result.orderNo,
                                contactWechat = result.contactWechat.ifBlank {
                                    AppPreferences.CONTACT_WECHAT
                                },
                                remarkText = "${result.orderNo} ${result.remarkHint}".trim(),
                                benefitSummary = result.benefitSummary,
                                boundPhone = phone,
                                phoneInput = phone,
                                notified = notified,
                                showPayDetails = !notified,
                                hint = if (notified)
                                    "已收到申报。确认到账后这台手机会自动开通。"
                                else
                                    "转 ¥${result.amountYuan}，备注粘贴下方内容。",
                                polling = true
                            )
                        )
                    }
                    persistCheckout(_uiState.value.manualCheckout)
                    startPolling(result.orderNo, isManual = true)
                }
                is HaPayCreateResult.Error -> {
                    _uiState.update {
                        it.copy(
                            purchasingPlan = null,
                            toastMessage = result.message,
                            manualCheckout = ManualCheckoutUi(
                                visible = true,
                                creating = false,
                                planTitle = plan.titleZh,
                                planId = plan.productId,
                                error = result.message,
                                hint = "订单创建失败，请检查网络后重试"
                            )
                        )
                    }
                }
            }
        }
    }

    /** @deprecated 兼容旧调用，转到人工收款 */
    fun startCheckoutIntent(plan: VipPlan) = startManualCheckout(plan)

    fun onPlanSelected(plan: VipPlan) {
        analyticsRepository.trackVipPlanSelect(plan.productId)
    }

    fun onVipPageOpened() {
        analyticsRepository.trackVipPageView(_uiState.value.isVip)
        refreshVipStatus()
        if (_uiState.value.isWebsiteChannel) {
            applyPlansSnapshot(haPayRepository.localPlansSnapshot())
            refreshPricing()
            syncWebsiteVip(showToastOnActivate = true)
        }
    }

    fun markManualPaid() {
        val checkout = _uiState.value.manualCheckout
        val orderNo = checkout.orderNo
        if (orderNo.isBlank() || checkout.submitting) return
        analyticsRepository.trackVipIntentSubmit(
            checkout.planId,
            HaEvents.VipChannel.WECHAT_CONTACT,
            orderNo
        )
        _uiState.update {
            it.copy(manualCheckout = it.manualCheckout.copy(submitting = true, error = null))
        }
        viewModelScope.launch {
            when (val result = haPayRepository.notifyPaid(orderNo)) {
                is HaPayNotifyResult.Ok -> {
                    if (result.paid) {
                        if (result.vipLevel > 0) {
                            haPayRepository.applyPaidFromNotify(
                                orderNo, result.vipLevel, result.expireTime
                            )
                        }
                        when (val status = haPayRepository.pollOrder(orderNo)) {
                            is HaPayStatusResult.Paid -> applyPaidSuccess(
                                status, orderNo, HaEvents.VipChannel.WECHAT_CONTACT
                            )
                            else -> {
                                refreshVipStatus()
                                val activated = appPreferences.isVipActive()
                                val waiting = _uiState.value.manualCheckout.copy(
                                    submitting = false,
                                    notified = true,
                                    showPayDetails = false,
                                    hint = "已收到申报。确认到账后这台手机会自动开通。"
                                )
                                if (!activated) persistCheckout(waiting)
                                _uiState.update {
                                    it.copy(
                                        purchasingPlan = if (activated) null else it.purchasingPlan,
                                        toastMessage = if (activated)
                                            "支付成功，会员已开通"
                                        else
                                            "已确认付款，正在同步开通状态",
                                        manualCheckout = if (activated) ManualCheckoutUi() else waiting,
                                        pendingOrder = if (activated) null else waiting.copy(visible = false)
                                    )
                                }
                                if (!activated) syncWebsiteVip(showToastOnActivate = true)
                            }
                        }
                    } else {
                        val waiting = _uiState.value.manualCheckout.copy(
                            submitting = false,
                            notified = true,
                            showPayDetails = false,
                            hint = "已收到申报。确认到账后这台手机会自动开通。"
                        )
                        persistCheckout(waiting)
                        _uiState.update {
                            it.copy(
                                toastMessage = result.message.ifBlank { "已收到申报，等待确认到账" },
                                pendingOrder = waiting.copy(visible = false),
                                manualCheckout = waiting
                            )
                        }
                    }
                }
                is HaPayNotifyResult.Error -> {
                    _uiState.update {
                        it.copy(
                            toastMessage = result.message,
                            manualCheckout = it.manualCheckout.copy(
                                submitting = false,
                                error = result.message
                            )
                        )
                    }
                }
            }
        }
    }

    fun revealCheckoutPayDetails() {
        _uiState.update {
            it.copy(
                manualCheckout = it.manualCheckout.copy(
                    showPayDetails = true,
                    hint = "转 ¥${it.manualCheckout.amountYuan}，备注粘贴下方内容。"
                )
            )
        }
    }

    fun dismissManualCheckout(abandoned: Boolean = true) {
        val checkout = _uiState.value.manualCheckout
        if (abandoned && checkout.visible && checkout.orderNo.isNotBlank() && !checkout.notified) {
            analyticsRepository.trackVipPayAbandon(checkout.planId, checkout.orderNo)
        }
        if (checkout.orderNo.isNotBlank()) {
            persistCheckout(checkout)
        }
        pollJob?.cancel()
        _uiState.update {
            it.copy(
                purchasingPlan = if (checkout.orderNo.isBlank()) null else it.purchasingPlan,
                manualCheckout = ManualCheckoutUi(),
                pendingOrder = if (checkout.orderNo.isNotBlank()) checkout.copy(visible = false) else it.pendingOrder
            )
        }
    }

    fun reopenPendingCheckout() {
        val pending = haPayRepository.pendingOrder()?.toCheckoutUi()
            ?: _uiState.value.pendingOrder
            ?: return
        if (pending.orderNo.isBlank()) return
        _uiState.update {
            it.copy(
                purchasingPlan = VipPlan.entries.firstOrNull { plan ->
                    plan.productId == pending.planId
                },
                manualCheckout = pending.copy(
                    visible = true,
                    showPayDetails = !pending.notified,
                    hint = if (pending.notified)
                        "已收到申报。确认到账后这台手机会自动开通。"
                    else
                        "转 ¥${pending.amountYuan}，备注粘贴下方内容。"
                )
            )
        }
        startPolling(pending.orderNo, isManual = true)
    }

    fun restorePurchases() {
        if (!_uiState.value.isWebsiteChannel) return
        _uiState.update {
            it.copy(
                restorePhone = RestorePhoneUi(
                    visible = true,
                    phoneInput = appPreferences.getBoundPhone(),
                    busy = false,
                    error = null
                )
            )
        }
    }

    fun onRestorePhoneChange(value: String) {
        _uiState.update {
            it.copy(
                restorePhone = it.restorePhone.copy(
                    phoneInput = value.filter { ch -> ch.isDigit() || ch == '+' }.take(13),
                    error = null
                )
            )
        }
    }

    fun dismissRestorePhone() {
        _uiState.update { it.copy(restorePhone = RestorePhoneUi(), restoring = false) }
    }

    fun confirmRestorePhone() {
        val phone = PhoneNumberHintHelper.normalizeCnMobile(_uiState.value.restorePhone.phoneInput)
        if (phone.isNullOrBlank()) {
            _uiState.update {
                it.copy(restorePhone = it.restorePhone.copy(error = "请输入有效的手机号"))
            }
            return
        }
        _uiState.update {
            it.copy(
                restoring = true,
                restorePhone = it.restorePhone.copy(busy = true, error = null)
            )
        }
        viewModelScope.launch {
            when (val result = haPayRepository.restoreByPhone(phone)) {
                is HaRestoreResult.Restored -> {
                    refreshVipStatus()
                    _uiState.update {
                        it.copy(
                            restoring = false,
                            restorePhone = RestorePhoneUi(),
                            pendingOrder = null,
                            manualCheckout = ManualCheckoutUi(),
                            toastMessage = result.message
                        )
                    }
                }
                is HaRestoreResult.PendingOrder -> {
                    refreshVipStatus()
                    val checkout = result.pending.toCheckoutUi().copy(
                        visible = true,
                        showPayDetails = !result.pending.notified
                    )
                    _uiState.update {
                        it.copy(
                            restoring = false,
                            restorePhone = RestorePhoneUi(),
                            pendingOrder = checkout.copy(visible = false),
                            manualCheckout = checkout,
                            toastMessage = result.message
                        )
                    }
                    if (checkout.orderNo.isNotBlank()) {
                        startPolling(checkout.orderNo, isManual = true)
                    }
                }
                is HaRestoreResult.Error -> {
                    _uiState.update {
                        it.copy(
                            restoring = false,
                            restorePhone = it.restorePhone.copy(busy = false, error = result.message)
                        )
                    }
                }
            }
        }
    }

    fun refreshPendingFromPage() {
        val orderNo = _uiState.value.pendingOrder?.orderNo?.takeIf { it.isNotBlank() }
            ?: _uiState.value.manualCheckout.orderNo.takeIf { it.isNotBlank() }
        if (orderNo.isNullOrBlank()) {
            syncWebsiteVip(showToastOnActivate = true, userRequested = true)
            return
        }
        viewModelScope.launch {
            when (val status = haPayRepository.pollOrder(orderNo)) {
                is HaPayStatusResult.Paid -> applyPaidSuccess(
                    status, orderNo, HaEvents.VipChannel.WECHAT_CONTACT
                )
                is HaPayStatusResult.Pending -> {
                    syncWebsiteVip(showToastOnActivate = true)
                    _uiState.update {
                        it.copy(
                            toastMessage = if (it.pendingOrder?.notified == true ||
                                it.manualCheckout.notified
                            ) {
                                "尚未确认到账，打开本页也会自动开通"
                            } else {
                                "尚未检测到付款。转账后请点「我已转账」"
                            }
                        )
                    }
                }
                is HaPayStatusResult.Error -> {
                    _uiState.update { it.copy(toastMessage = status.message) }
                }
            }
        }
    }

    fun refreshManualCheckoutStatus() {
        val orderNo = _uiState.value.manualCheckout.orderNo
        if (orderNo.isBlank()) return
        viewModelScope.launch {
            when (val status = haPayRepository.pollOrder(orderNo)) {
                is HaPayStatusResult.Paid -> {
                    applyPaidSuccess(status, orderNo, HaEvents.VipChannel.WECHAT_CONTACT)
                }
                is HaPayStatusResult.Pending -> {
                    _uiState.update {
                        it.copy(
                            toastMessage = if (it.manualCheckout.notified)
                                "尚未确认到账，请稍后再刷新"
                            else
                                "尚未检测到付款，转账后请点「我已转账」"
                        )
                    }
                }
                is HaPayStatusResult.Error -> {
                    _uiState.update { it.copy(toastMessage = status.message) }
                }
            }
        }
    }

    private fun startPolling(orderNo: String, isManual: Boolean = false) {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            // 最长约 10 分钟：2s × 300
            repeat(300) {
                if (!isActive) return@launch
                delay(2000)
                when (val status = haPayRepository.pollOrder(orderNo)) {
                    is HaPayStatusResult.Paid -> {
                        applyPaidSuccess(
                            status,
                            orderNo,
                            if (isManual) HaEvents.VipChannel.WECHAT_CONTACT
                            else HaEvents.VipChannel.WECHAT_PAY
                        )
                        return@launch
                    }
                    is HaPayStatusResult.Error -> {
                        // 轮询中偶发网络错误不打断
                    }
                    is HaPayStatusResult.Pending -> {
                        if (isManual) {
                            _uiState.update {
                                it.copy(manualCheckout = it.manualCheckout.copy(polling = true))
                            }
                        }
                    }
                }
            }
            if (isManual) {
                _uiState.update {
                    val notified = it.manualCheckout.notified
                    it.copy(
                        manualCheckout = it.manualCheckout.copy(
                            polling = false,
                            hint = if (notified)
                                "仍在等待确认。关掉后打开本页也会自动开通。"
                            else
                                "若已转账，请点「我已转账」。"
                        )
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        wxPay = it.wxPay.copy(
                            polling = false,
                            error = "支付超时，若已付款请稍后重启 App 或联系客服"
                        )
                    )
                }
            }
        }
    }

    fun onPayLaunchFailed() {
        _uiState.update {
            val hasQr = it.wxPay.qrBitmap != null
            it.copy(
                wxPay = it.wxPay.copy(
                    showQr = hasQr,
                    error = when {
                        hasQr -> "无法自动打开微信，请扫码支付"
                        !WeChatPayHelper.isConfigured() ->
                            "未配置微信 AppID，请联系开发者"
                        else -> "无法打开微信支付，请确认已安装微信后重试"
                    },
                    hint = if (hasQr) "请使用微信扫一扫完成支付" else it.wxPay.hint
                )
            )
        }
    }

    fun relaunchWeChatPay() {
        val wx = _uiState.value.wxPay
        val canApp = wx.payMode == "wxpay_app" && wx.appPay != null
        if (!canApp && wx.payUrl.isBlank()) {
            if (wx.qrBitmap != null) {
                _uiState.update { it.copy(wxPay = it.wxPay.copy(showQr = true)) }
            }
            return
        }
        _uiState.update {
            it.copy(
                wxPay = it.wxPay.copy(
                    showQr = false,
                    launchToken = it.wxPay.launchToken + 1,
                    error = null,
                    hint = "正在重新打开微信支付…"
                )
            )
        }
    }

    fun showPayQr() {
        _uiState.update {
            it.copy(
                wxPay = it.wxPay.copy(
                    showQr = true,
                    hint = "请使用微信扫一扫完成支付"
                )
            )
        }
    }

    fun onVipScreenResumed() {
        if (_uiState.value.isWebsiteChannel) {
            syncWebsiteVip(showToastOnActivate = true)
            return
        }
        val wxOrder = _uiState.value.wxPay.orderNo.takeIf {
            _uiState.value.wxPay.polling && it.isNotBlank()
        } ?: return
        viewModelScope.launch {
            when (val status = haPayRepository.pollOrder(wxOrder)) {
                is HaPayStatusResult.Paid -> applyPaidSuccess(
                    status, wxOrder, HaEvents.VipChannel.WECHAT_PAY
                )
                else -> Unit
            }
        }
    }

    private fun persistCheckout(checkout: ManualCheckoutUi) {
        if (checkout.orderNo.isBlank()) return
        haPayRepository.savePendingOrder(checkout.toPendingOrder())
        _uiState.update { it.copy(pendingOrder = checkout.copy(visible = false)) }
    }

    private fun applyPaidSuccess(
        status: HaPayStatusResult.Paid,
        orderNo: String,
        channel: String
    ) {
        pollJob?.cancel()
        analyticsRepository.trackVipPaidSuccess(
            planId = _uiState.value.manualCheckout.planId
                .ifBlank { _uiState.value.pendingOrder?.planId.orEmpty() }
                .ifBlank { _uiState.value.purchasingPlan?.productId.orEmpty() },
            channel = channel,
            ref = orderNo
        )
        _uiState.update {
            it.copy(
                purchasingPlan = null,
                vipLevel = status.vipLevel,
                vipExpireTime = status.expireTime,
                isVip = appPreferences.isVipActive(),
                isPremium = appPreferences.isPremium(),
                isLifetimeVip = appPreferences.isLifetimeVip(),
                statusText = vipRepository.getLocalStatusText(),
                trialAvailable = false,
                restoring = false,
                toastMessage = paidSuccessMessage(status.expireTime),
                wxPay = WxPayUi(),
                manualCheckout = ManualCheckoutUi(),
                pendingOrder = null
            )
        }
    }

    private fun syncWebsiteVip(
        showToastOnActivate: Boolean,
        userRequested: Boolean = false
    ) {
        viewModelScope.launch {
            val result = haPayRepository.syncDeviceVip()
            refreshVipStatus()
            val pending = result.pending?.toCheckoutUi()
            _uiState.update {
                it.copy(
                    restoring = false,
                    pendingOrder = if (result.vipApplied || appPreferences.isVipActive()) null else pending,
                    toastMessage = when {
                        result.vipApplied && showToastOnActivate ->
                            restoreSuccessMessage(result.expireTime)
                        userRequested && appPreferences.isVipActive() && !result.vipApplied ->
                            "当前已是会员"
                        userRequested && pending != null ->
                            "订单待确认，可打开订单查看"
                        userRequested ->
                            "未找到可恢复的开通记录"
                        else -> it.toastMessage
                    }
                )
            }
        }
    }

    private fun restoreSuccessMessage(expireTime: Long): String {
        if (expireTime <= 0L) return "已恢复会员（永久）"
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = expireTime }
        val text = String.format(
            "%d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        return "已恢复会员（至 $text）"
    }

    fun dismissWxPay() {
        pollJob?.cancel()
        _uiState.update {
            it.copy(purchasingPlan = null, wxPay = WxPayUi())
        }
    }

    fun refreshWxPayStatus() {
        val orderNo = _uiState.value.wxPay.orderNo
        if (orderNo.isBlank()) return
        viewModelScope.launch {
            when (val status = haPayRepository.pollOrder(orderNo)) {
                is HaPayStatusResult.Paid -> {
                    _uiState.update {
                        it.copy(
                            purchasingPlan = null,
                            vipLevel = status.vipLevel,
                            vipExpireTime = status.expireTime,
                            isVip = appPreferences.isVipActive(),
                            isPremium = appPreferences.isPremium(),
                            statusText = vipRepository.getLocalStatusText(),
                            toastMessage = paidSuccessMessage(status.expireTime),
                            wxPay = WxPayUi()
                        )
                    }
                }
                is HaPayStatusResult.Pending -> {
                    _uiState.update { it.copy(toastMessage = "尚未检测到付款，请完成支付后重试") }
                }
                is HaPayStatusResult.Error -> {
                    _uiState.update { it.copy(toastMessage = status.message) }
                }
            }
        }
    }

    private fun paidSuccessMessage(expireTime: Long): String {
        if (expireTime <= 0L) return "支付成功，会员已开通（永久）"
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = expireTime }
        val text = String.format(
            "%d-%02d-%02d",
            cal.get(java.util.Calendar.YEAR),
            cal.get(java.util.Calendar.MONTH) + 1,
            cal.get(java.util.Calendar.DAY_OF_MONTH)
        )
        return "支付成功，会员已开通（至 $text）"
    }

    private fun handleBillingResult(result: BillingResult2) {
        when (result) {
            is BillingResult2.Success -> {
                _uiState.update { it.copy(isLoading = true) }
                viewModelScope.launch {
                    when (val vipResult = vipRepository.activateFromPurchase(
                        purchaseToken = result.purchaseToken,
                        productId = result.productId,
                        productType = result.productType
                    )) {
                        is VipResult.Success -> {
                            analyticsRepository.trackVipPaidSuccess(
                                planId = result.productId,
                                channel = HaEvents.VipChannel.PLAY
                            )
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    purchasingPlan = null,
                                    vipLevel = vipResult.vipLevel,
                                    vipExpireTime = vipResult.expireTime,
                                    isVip = appPreferences.isVipActive(),
                                    isPremium = appPreferences.isPremium(),
                                    statusText = vipRepository.getLocalStatusText(),
                                    trialAvailable = false,
                                    toastMessage = vipResult.message
                                )
                            }
                        }
                        is VipResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading = false,
                                    purchasingPlan = null,
                                    toastMessage = vipResult.message
                                )
                            }
                        }
                    }
                }
            }
            is BillingResult2.Cancelled -> {
                _uiState.update { it.copy(purchasingPlan = null) }
            }
            is BillingResult2.Error -> {
                _uiState.update {
                    it.copy(
                        purchasingPlan = null,
                        toastMessage = result.message
                    )
                }
            }
        }
    }

    fun activateTrial() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = vipRepository.activateTrial()) {
                is VipResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            trialAvailable = false,
                            vipLevel = result.vipLevel,
                            vipExpireTime = result.expireTime,
                            isVip = appPreferences.isVipActive(),
                            isPremium = appPreferences.isPremium(),
                            statusText = vipRepository.getLocalStatusText(),
                            toastMessage = result.message
                        )
                    }
                }
                is VipResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, toastMessage = result.message)
                    }
                }
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    override fun onCleared() {
        pollJob?.cancel()
        super.onCleared()
    }

    val freeMonitorLimit: Int get() = AppPreferences.FREE_MONITOR_LIMIT
}

private fun ManualCheckoutUi.toPendingOrder(): PendingHaOrder = PendingHaOrder(
    orderNo = orderNo,
    planId = planId,
    planTitle = planTitle,
    amountYuan = amountYuan,
    listAmountYuan = listAmountYuan,
    earlyBird = earlyBird,
    tierLabel = tierLabel,
    contactWechat = contactWechat.ifBlank { AppPreferences.CONTACT_WECHAT },
    remarkText = remarkText,
    benefitSummary = benefitSummary,
    notified = notified,
    createdAt = System.currentTimeMillis(),
    phone = boundPhone.ifBlank { phoneInput }
)

private fun PendingHaOrder.toCheckoutUi(): ManualCheckoutUi = ManualCheckoutUi(
    visible = false,
    planTitle = planTitle,
    amountYuan = amountYuan,
    listAmountYuan = listAmountYuan,
    earlyBird = earlyBird,
    tierLabel = tierLabel,
    planId = planId,
    orderNo = orderNo,
    contactWechat = contactWechat.ifBlank { AppPreferences.CONTACT_WECHAT },
    remarkText = remarkText,
    benefitSummary = benefitSummary,
    notified = notified,
    boundPhone = phone,
    phoneInput = phone,
    hint = if (notified) "已收到申报。确认到账后这台手机会自动开通。"
    else "转账并备注下方内容，然后点「我已转账」。"
)
