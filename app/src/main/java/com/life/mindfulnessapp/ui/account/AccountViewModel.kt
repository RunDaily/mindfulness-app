package com.life.mindfulnessapp.ui.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.AccountRepository
import com.life.mindfulnessapp.data.repository.AuthResult
import com.life.mindfulnessapp.data.repository.SyncResult
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

data class AccountUiState(
    val isLoggedIn: Boolean = false,
    val username: String = "",
    val nickname: String = "",
    val avatarEmoji: String = "⚓",
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val syncStatus: String = "",
    val toastMessage: String? = null
)

// ════════════════════════════════════════════
//  AccountViewModel
// ════════════════════════════════════════════

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AccountUiState(
            isLoggedIn    = accountRepository.isLoggedIn,
            username      = accountRepository.savedUsername ?: "",
            nickname      = accountRepository.savedNickname ?: "",
            avatarEmoji   = accountRepository.savedAvatarEmoji ?: "⚓"
        )
    )
    val uiState: StateFlow<AccountUiState> = _uiState.asStateFlow()

    // ── 登录 / 注册 ──────────────────────────────────────────────────────────

    /**
     * 一键登录注册：
     * - 手机号已存在且密码正确 → 登录成功
     * - 手机号不存在 → 自动注册后登录
     * - 密码错误 → 返回错误提示
     */
    fun loginOrRegister(
        phone: String,
        password: String,
        onSuccess: (isNewUser: Boolean) -> Unit = {}
    ) {
        if (phone.isBlank() || password.isBlank()) {
            _uiState.update { it.copy(toastMessage = "手机号和密码不能为空") }
            return
        }
        if (!phone.matches(Regex("^1[3-9]\\d{9}$"))) {
            _uiState.update { it.copy(toastMessage = "请输入正确的手机号码") }
            return
        }
        if (password.length < 6) {
            _uiState.update { it.copy(toastMessage = "密码至少 6 位") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            when (val result = accountRepository.loginOrRegister(phone, password)) {
                is AuthResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isLoading   = false,
                            isLoggedIn  = true,
                            username    = result.username,
                            nickname    = result.nickname,
                            avatarEmoji = result.avatarEmoji,
                            toastMessage = if (result.isNewUser) "注册成功，欢迎使用心锚 ⚓" else "登录成功，欢迎回来 👋"
                        )
                    }
                    onSuccess(result.isNewUser)
                }
                is AuthResult.Error -> {
                    _uiState.update {
                        it.copy(isLoading = false, toastMessage = result.message)
                    }
                }
            }
        }
    }

    /** 退出登录 */
    fun logout() {
        accountRepository.logout()
        _uiState.update {
            AccountUiState(isLoggedIn = false, toastMessage = "已退出登录")
        }
    }

    // ── 数据同步 ──────────────────────────────────────────────────────────────

    /** 完整同步：上传使用记录 + 被监控 App 列表 */
    fun syncToCloud(onDone: (String) -> Unit = {}) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, syncStatus = "同步中...") }
            when (val result = accountRepository.fullSync()) {
                is SyncResult.Success -> {
                    _uiState.update {
                        it.copy(
                            isSyncing    = false,
                            syncStatus   = result.message,
                            toastMessage = result.message
                        )
                    }
                    onDone(result.message)
                }
                is SyncResult.Error -> {
                    _uiState.update {
                        it.copy(
                            isSyncing    = false,
                            syncStatus   = "同步失败",
                            toastMessage = result.message
                        )
                    }
                    onDone(result.message)
                }
            }
        }
    }

    /** 后台静默同步（单次使用会话结束后调用，不更新 UI loading 状态）*/
    fun silentSyncSessions() {
        if (!accountRepository.isLoggedIn) return
        viewModelScope.launch {
            accountRepository.syncSessions()
        }
    }

    /** 清除 Toast 消息 */
    fun clearToast() {
        _uiState.update { it.copy(toastMessage = null) }
    }
}
