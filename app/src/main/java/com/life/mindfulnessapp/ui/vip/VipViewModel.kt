package com.life.mindfulnessapp.ui.vip

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.billing.BillingManager
import com.life.mindfulnessapp.billing.BillingResult2
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.network.VipPlan
import com.life.mindfulnessapp.data.repository.VipRepository
import com.life.mindfulnessapp.data.repository.VipResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VipUiState(
    val vipLevel: Int = 0,
    val vipExpireTime: Long = 0L,
    val isVip: Boolean = false,
    val isPremium: Boolean = false,
    val statusText: String = "免费版",
    val trialAvailable: Boolean = false,
    val isLoading: Boolean = false,
    val toastMessage: String? = null,
    val purchasingPlan: VipPlan? = null,
    val productPrices: Map<VipPlan, String> = emptyMap()
)

@HiltViewModel
class VipViewModel @Inject constructor(
    private val vipRepository: VipRepository,
    private val billingManager: BillingManager,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        VipUiState(
            vipLevel = appPreferences.getVipLevel(),
            vipExpireTime = appPreferences.vipExpireTime.value,
            isVip = appPreferences.isVipActive(),
            isPremium = appPreferences.isPremium(),
            statusText = vipRepository.getLocalStatusText(),
            trialAvailable = vipRepository.isTrialAvailable()
        )
    )
    val uiState: StateFlow<VipUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(
                vipRepository.vipLevel,
                vipRepository.vipExpireTime
            ) { level, expire -> level to expire }
                .collect { (level, expire) ->
                    _uiState.update {
                        it.copy(
                            vipLevel = level,
                            vipExpireTime = expire,
                            isVip = appPreferences.isVipActive(),
                            isPremium = appPreferences.isPremium(),
                            statusText = vipRepository.getLocalStatusText(),
                            trialAvailable = vipRepository.isTrialAvailable()
                        )
                    }
                }
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
    }

    fun refreshVipStatus() {
        when (val result = vipRepository.refreshLocalStatus()) {
            is VipResult.Success -> {
                _uiState.update {
                    it.copy(
                        vipLevel = result.vipLevel,
                        vipExpireTime = result.expireTime,
                        isVip = appPreferences.isVipActive(),
                        isPremium = appPreferences.isPremium(),
                        statusText = vipRepository.getLocalStatusText(),
                        trialAvailable = vipRepository.isTrialAvailable()
                    )
                }
            }
            is VipResult.Error -> Unit
        }
        viewModelScope.launch {
            billingManager.queryAllProductDetails()
        }
    }

    fun launchPurchase(activity: Activity, plan: VipPlan) {
        _uiState.update { it.copy(purchasingPlan = plan) }
        val launched = billingManager.launchBillingFlow(activity, plan)
        if (!launched) {
            _uiState.update { it.copy(purchasingPlan = null) }
        }
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

    val vipLevelFlow: StateFlow<Int> = vipRepository.vipLevel

    fun isVip(): Boolean = vipRepository.isVip()

    fun canAddMoreApps(currentCount: Int): Boolean = vipRepository.canAddMoreApps(currentCount)

    fun canUseAllThemes(): Boolean = vipRepository.canUseAllThemes()

    fun canSetWeeklyLimit(): Boolean = vipRepository.canSetWeeklyLimit()

    val freeMonitorLimit: Int get() = AppPreferences.FREE_MONITOR_LIMIT
}
