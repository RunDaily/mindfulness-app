package com.life.mindfulnessapp.ui.invite

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.repository.InviteRepository
import com.life.mindfulnessapp.data.repository.InviteResult
import com.life.mindfulnessapp.data.repository.VipRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ════════════════════════════════════════════
//  UI State
// ════════════════════════════════════════════

data class InviteUiState(
    /** 当前用户自己的邀请码 */
    val myCode: String = "",
    /** 已成功邀请人数 */
    val invitedCount: Int = 0,
    /** 奖励摘要文本 */
    val rewardSummary: String = "邀请好友，各获 7 天 VIP 奖励",
    /** 是否已使用过别人的邀请码 */
    val hasRedeemed: Boolean = false,
    /** 是否正在加载 */
    val isLoading: Boolean = false,
    /** 是否正在兑换邀请码 */
    val isRedeeming: Boolean = false,
    /** 是否已登录 */
    val isLoggedIn: Boolean = false,
    /** Toast 消息 */
    val toastMessage: String? = null
)

// ════════════════════════════════════════════
//  InviteViewModel
// ════════════════════════════════════════════

@HiltViewModel
class InviteViewModel @Inject constructor(
    private val inviteRepository: InviteRepository,
    private val vipRepository: VipRepository,
    private val appPreferences: AppPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        InviteUiState(
            isLoggedIn = appPreferences.isLoggedIn
        ).let { base ->
            val local = inviteRepository.getLocalInviteInfo()
            base.copy(
                myCode        = local.myCode,
                invitedCount  = local.invitedCount,
                rewardSummary = local.rewardSummary,
                hasRedeemed   = local.hasRedeemed
            )
        }
    )
    val uiState: StateFlow<InviteUiState> = _uiState.asStateFlow()

    // ── 进入页面时刷新 ────────────────────────────────────────────────────

    /** 从服务端拉取最新邀请信息（进入页面时调用） */
    fun refresh() {
        if (!appPreferences.isLoggedIn) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = inviteRepository.fetchInviteInfo()) {
                is InviteResult.Success -> {
                    val info = inviteRepository.getLocalInviteInfo()
                    _uiState.update {
                        it.copy(
                            isLoading     = false,
                            myCode        = info.myCode,
                            invitedCount  = info.invitedCount,
                            rewardSummary = info.rewardSummary,
                            hasRedeemed   = info.hasRedeemed
                        )
                    }
                }
                is InviteResult.Error -> {
                    _uiState.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    // ── 兑换邀请码 ────────────────────────────────────────────────────────

    /**
     * 使用别人的邀请码（已登录的新用户才能使用）。
     */
    fun redeemCode(code: String) {
        if (!appPreferences.isLoggedIn) {
            _uiState.update { it.copy(toastMessage = "请先登录后再使用邀请码") }
            return
        }
        if (appPreferences.hasRedeemedInvite) {
            _uiState.update { it.copy(toastMessage = "每个账号只能使用一次邀请码") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isRedeeming = true) }
            when (val result = inviteRepository.redeemInviteCode(code, vipRepository)) {
                is InviteResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isRedeeming = false,
                            hasRedeemed = true,
                            toastMessage = result.message
                        )
                    }
                }
                is InviteResult.Error -> {
                    _uiState.update {
                        it.copy(isRedeeming = false, toastMessage = result.message)
                    }
                }
            }
        }
    }

    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
