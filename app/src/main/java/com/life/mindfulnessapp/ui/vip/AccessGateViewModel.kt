package com.life.mindfulnessapp.ui.vip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.BetaAccessRepository
import com.life.mindfulnessapp.data.repository.BetaClaimResult
import com.life.mindfulnessapp.data.repository.BetaRedeemResult
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * 会员体系底栏状态：首页 / 管理页 / 绑定流 / 会员页共用。
 */
@HiltViewModel
class AccessGateViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val betaAccessRepository: BetaAccessRepository,
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _accessGate = MutableStateFlow(AccessGateUiState())
    val accessGate: StateFlow<AccessGateUiState> = _accessGate.asStateFlow()

    fun dismiss() {
        val toast = _accessGate.value.unlockToast
        _accessGate.value = AccessGateUiState(unlockToast = toast)
    }

    fun consumeUnlockToast() {
        _accessGate.update { it.copy(unlockToast = null) }
    }

    fun request(preferRedeemIfClaimed: Boolean = true) {
        val claimed = appPreferences.getClaimedInviteCode()
        val unlocked = appPreferences.isBetaUnlocked()
        val startRedeem = preferRedeemIfClaimed && claimed.isNotBlank() && !unlocked
        _accessGate.value = AccessGateUiState(
            visible = true,
            step = if (startRedeem) AccessGateStep.Redeem else AccessGateStep.Gate,
            codeInput = if (!unlocked) claimed else "",
            issuedCode = if (!unlocked) claimed else ""
        )
    }

    fun onCodeChange(value: String) {
        _accessGate.update {
            it.copy(
                codeInput = value.uppercase().filter { ch -> ch.isLetterOrDigit() }.take(16),
                error = null
            )
        }
    }

    fun openRedeemStep() {
        val claimed = appPreferences.getClaimedInviteCode()
        _accessGate.update {
            it.copy(
                step = AccessGateStep.Redeem,
                codeInput = it.codeInput.ifBlank { claimed },
                issuedCode = it.issuedCode.ifBlank { claimed },
                error = null,
                infoMessage = null,
                busy = false
            )
        }
    }

    fun redeem() {
        val code = _accessGate.value.codeInput
        if (code.isBlank()) {
            _accessGate.update { it.copy(error = "请输入会员码") }
            return
        }
        viewModelScope.launch { redeemInternal(code) }
    }

    fun openClaimStep() {
        _accessGate.update {
            it.copy(
                step = AccessGateStep.Claim,
                error = null,
                infoMessage = null,
                busy = false
            )
        }
    }

    fun onClaimChannelChange(channel: String) {
        _accessGate.update {
            it.copy(
                claimChannel = channel,
                claimContact = "",
                error = null
            )
        }
    }

    fun onClaimContactChange(value: String) {
        _accessGate.update { it.copy(claimContact = value, error = null) }
    }

    fun submitClaim() {
        val state = _accessGate.value
        viewModelScope.launch {
            _accessGate.update { it.copy(busy = true, error = null) }
            when (val result = betaAccessRepository.claim(state.claimChannel, state.claimContact)) {
                is BetaClaimResult.Success -> {
                    _accessGate.update {
                        it.copy(
                            issuedCode = result.code,
                            codeInput = result.code,
                            infoMessage = result.message
                        )
                    }
                    // 领码成功后自动兑换；失败则进入 Issued 兜底
                    redeemInternal(result.code, fromClaim = true)
                }
                is BetaClaimResult.Error -> {
                    _accessGate.update { it.copy(busy = false, error = result.message) }
                }
            }
        }
    }

    fun redeemIssued() {
        val code = _accessGate.value.issuedCode.ifBlank { _accessGate.value.codeInput }
        if (code.isBlank()) {
            _accessGate.update { it.copy(error = "会员码为空") }
            return
        }
        viewModelScope.launch { redeemInternal(code) }
    }

    fun backToGate() {
        _accessGate.update {
            it.copy(
                step = AccessGateStep.Gate,
                busy = false,
                error = null,
                infoMessage = null
            )
        }
    }

    private suspend fun redeemInternal(code: String, fromClaim: Boolean = false) {
        _accessGate.update { it.copy(busy = true, error = null) }
        when (val result = betaAccessRepository.redeem(code)) {
            is BetaRedeemResult.Success -> {
                analyticsRepository.trackVipCodeRedeem(result.alreadyUnlocked)
                if (!result.alreadyUnlocked) {
                    analyticsRepository.trackBetaUnlock()
                }
                val toast = result.message.ifBlank { "会员已开通" }
                _accessGate.value = AccessGateUiState(unlockToast = toast)
            }
            is BetaRedeemResult.Error -> {
                if (fromClaim) {
                    _accessGate.update {
                        it.copy(
                            busy = false,
                            step = AccessGateStep.Issued,
                            issuedCode = code,
                            codeInput = code,
                            error = result.message,
                            infoMessage = "会员码已生成，但自动开通未完成，请手动兑换。"
                        )
                    }
                } else {
                    _accessGate.update { it.copy(busy = false, error = result.message) }
                }
            }
        }
    }
}
