package com.life.mindfulnessapp.ui.vip

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.billing.BillingManager
import com.life.mindfulnessapp.billing.BillingResult2
import com.life.mindfulnessapp.billing.PlayProductDetails
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.network.VipPlan
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.data.repository.VipResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ════════════════════════════════════════════
//  UI State
// ════════════════════════════════════════════

data class VipUiState(
    /** VIP 等级：0=免费，1=标准版，2=高级版 */
    val vipLevel: Int = 0,
    /** VIP 过期时间戳（毫秒），0=永久 */
    val vipExpireTime: Long = 0L,
    /** 是否为激活状态的 VIP */
    val isVip: Boolean = false,
    /** 是否为高级版 */
    val isPremium: Boolean = false,
    /** VIP 状态文本（如"标准版 · 剩余28天"） */
    val statusText: String = "免费版",
    /** 是否有试用资格 */
    val trialAvailable: Boolean = false,
    /** 正在加载（购买/查询） */
    val isLoading: Boolean = false,
    /** 操作消息（Toast 提示） */
    val toastMessage: String? = null,
    /** 正在处理购买的方案（用于 UI 显示 loading 状态） */
    val purchasingPlan: VipPlan? = null,
    /**
     * 从 Google Play 加载的产品详情（含本地化价格）。
     * Key = VipPlan，Value = 格式化价格字符串（如 "$4.99/month"）。
     * 未加载完成时为空 Map，UI 可展示占位符。
     */
    val productPrices: Map<VipPlan, String> = emptyMap()
)

// ════════════════════════════════════════════
//  VipViewModel
// ════════════════════════════════════════════

@HiltViewModel
class VipViewModel @Inject constructor(
    private val vipRepository: VipRepository,
    private val billingManager: BillingManager,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VipUiState(
            vipLevel      = appPreferences.getVipLevel(),
            vipExpireTime = appPreferences.vipExpireTime.value,
            isVip         = appPreferences.isVipActive(),
            isPremium     = appPreferences.isPremium(),
            statusText    = vipRepository.getLocalStatusText()
        )
    )
    val uiState: StateFlow<VipUiState> = _uiState.asStateFlow()

    /** 是否已登录（决定是否显示登录提示）*/
    val isLoggedIn: Boolean get() = appPreferences.isLoggedIn

    init {
        // 监听 VIP 等级变化，实时更新 UI 状态
        viewModelScope.launch {
            combine(
                vipRepository.vipLevel,
                vipRepository.vipExpireTime
            ) { level, expire -> level to expire }
            .collect { (level, expire) ->
                _uiState.update {
                    it.copy(
                        vipLevel      = level,
                        vipExpireTime = expire,
                        isVip         = appPreferences.isVipActive(),
                        isPremium     = appPreferences.isPremium(),
                        statusText    = vipRepository.getLocalStatusText()
                    )
                }
            }
        }

        // 监听 Google Play 产品详情（本地化价格），加载完成后刷新 UI
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

        // 监听 BillingManager 的购买结果
        viewModelScope.launch {
            billingManager.purchaseResultFlow.collect { result ->
                handleBillingResult(result)
            }
        }
    }

    // ── 查询 VIP 状态 ────────────────────────────────────────────────────────

    /** 从服务端刷新 VIP 状态（进入 VIP 页面时自动调用） */
    fun refreshVipStatus() {
        if (!isLoggedIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = vipRepository.fetchVipStatus()) {
                is VipResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading     = false,
                            vipLevel      = result.vipLevel,
                            vipExpireTime = result.expireTime,
                            isVip         = appPreferences.isVipActive(),
                            isPremium     = appPreferences.isPremium(),
                            statusText    = vipRepository.getLocalStatusText()
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

        // 同时刷新 Google Play 产品详情（确保价格最新）
        viewModelScope.launch {
            billingManager.queryAllProductDetails()
        }
    }

    // ── 发起 Google Play 购买流程 ────────────────────────────────────────────

    /**
     * 通过 BillingManager 调起 Google Play 购买底栏。
     *
     * 购买结果通过 [billingManager.purchaseResultFlow] 异步回调，
     * 由 [handleBillingResult] 统一处理。
     *
     * @param activity 当前前台 Activity（Google Play Billing 要求）
     * @param plan     要购买的方案
     */
    fun launchPurchase(activity: Activity, plan: VipPlan) {
        if (!isLoggedIn) {
            _uiState.update { it.copy(toastMessage = "请先登录后再购买") }
            return
        }
        _uiState.update { it.copy(purchasingPlan = plan) }
        val launched = billingManager.launchBillingFlow(activity, plan)
        if (!launched) {
            _uiState.update { it.copy(purchasingPlan = null) }
        }
    }

    /** 处理 BillingManager 回调的购买结果 */
    private fun handleBillingResult(result: BillingResult2) {
        when (result) {
            is BillingResult2.Success -> {
                // Google Play 购买成功 → 提交服务端验证
                _uiState.update { it.copy(isLoading = true) }
                viewModelScope.launch {
                    when (val vipResult = vipRepository.verifyAndActivateVip(
                        purchaseToken = result.purchaseToken,
                        productId     = result.productId,
                        productType   = result.productType
                    )) {
                        is VipResult.Success -> {
                            _uiState.update {
                                it.copy(
                                    isLoading      = false,
                                    purchasingPlan = null,
                                    vipLevel       = vipResult.vipLevel,
                                    vipExpireTime  = vipResult.expireTime,
                                    isVip          = appPreferences.isVipActive(),
                                    isPremium      = appPreferences.isPremium(),
                                    statusText     = vipRepository.getLocalStatusText(),
                                    toastMessage   = vipResult.message
                                )
                            }
                        }
                        is VipResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    isLoading      = false,
                                    purchasingPlan = null,
                                    toastMessage   = vipResult.message
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
                        toastMessage   = result.message
                    )
                }
            }
        }
    }

    // ── 免费试用 ────────────────────────────────────────────────────────────

    /** 激活 7 天免费试用 */
    fun activateTrial() {
        if (!isLoggedIn) {
            _uiState.update { it.copy(toastMessage = "请先登录后再激活试用") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = vipRepository.activateTrial()) {
                is VipResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading      = false,
                            trialAvailable = false,
                            vipLevel       = result.vipLevel,
                            vipExpireTime  = result.expireTime,
                            isVip          = appPreferences.isVipActive(),
                            isPremium      = appPreferences.isPremium(),
                            statusText     = vipRepository.getLocalStatusText(),
                            toastMessage   = result.message
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

    /** 清除 Toast 消息 */
    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }

    // ── 本地 VIP 状态（供其他 ViewModel 通过 StateFlow 订阅）────────────────

    /** 实时 VIP 等级 Flow（整个 App 共用，注入后直接监听） */
    val vipLevelFlow: StateFlow<Int> = vipRepository.vipLevel

    /** 快捷方法：是否为 VIP */
    fun isVip(): Boolean = vipRepository.isVip()

    /** 快捷方法：是否可以添加更多监控 App */
    fun canAddMoreApps(currentCount: Int): Boolean = vipRepository.canAddMoreApps(currentCount)

    /** 快捷方法：是否可以使用所有拦截主题 */
    fun canUseAllThemes(): Boolean = vipRepository.canUseAllThemes()

    /** 快捷方法：是否可以设置每周上限 */
    fun canSetWeeklyLimit(): Boolean = vipRepository.canSetWeeklyLimit()

    /** 免费版监控数量上限 */
    val freeMonitorLimit: Int get() = AppPreferences.FREE_MONITOR_LIMIT
}
