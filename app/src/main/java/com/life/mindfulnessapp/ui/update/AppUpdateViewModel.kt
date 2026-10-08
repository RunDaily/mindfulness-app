package com.life.mindfulnessapp.ui.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.network.AppReleaseResponse
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.AppUpdateRepository
import com.life.mindfulnessapp.data.repository.WhatsNewContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class UpdatePhase {
    Idle,
    Checking,
    Available,
    Downloading,
    ReadyToInstall,
    Installing,
    Error
}

data class PendingUpdateInfo(
    val release: AppReleaseResponse,
    val forceUpdate: Boolean
)

data class AppUpdateUiState(
    val phase: UpdatePhase = UpdatePhase.Idle,
    val visible: Boolean = false,
    val forceUpdate: Boolean = false,
    val release: AppReleaseResponse? = null,
    /** 0f..1f；未知长度时为 -1f */
    val downloadProgress: Float = 0f,
    val localApk: File? = null,
    val errorMessage: String? = null,
    /** 设置页「检查更新」进行中，用于行内 loading */
    val manualChecking: Boolean = false,
    /** 更新页静默检查进行中 */
    val pageChecking: Boolean = false
)

data class WhatsNewUiState(
    val visible: Boolean = false,
    val versionCode: Int = 0,
    val versionName: String = "",
    val changelog: String = ""
)

sealed class AppUpdateToast {
    data object AlreadyLatest : AppUpdateToast()
    data class Message(val text: String) : AppUpdateToast()
    data class OpenUrl(val url: String) : AppUpdateToast()
}

@HiltViewModel
class AppUpdateViewModel @Inject constructor(
    private val appUpdateRepository: AppUpdateRepository,
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(AppUpdateUiState())
    val uiState: StateFlow<AppUpdateUiState> = _uiState.asStateFlow()

    private val _whatsNew = MutableStateFlow(WhatsNewUiState())
    val whatsNew: StateFlow<WhatsNewUiState> = _whatsNew.asStateFlow()

    private val _toasts = MutableSharedFlow<AppUpdateToast>(extraBufferCapacity = 1)
    val toasts: SharedFlow<AppUpdateToast> = _toasts.asSharedFlow()

    private val _pendingUpdate = MutableStateFlow<PendingUpdateInfo?>(null)
    val pendingUpdate: StateFlow<PendingUpdateInfo?> = _pendingUpdate.asStateFlow()

    private var downloadJob: Job? = null
    private var launchChecked = false
    private var lastSilentCheckMs = 0L
    private var lastHomeResumeCheckMs = 0L
    /** 本会话内用户点了「稍后」的 versionCode，避免反复弹 */
    private var dismissedThisSessionVersion: Int = 0

    /** 启动后静默检查：强更仍弹窗，可选更新仅记红点 */
    fun checkOnLaunch() {
        if (launchChecked) return
        launchChecked = true
        viewModelScope.launch {
            performCheck(
                showPageChecking = false,
                offerOptionalDialogOnce = false,
                tryWhatsNew = true
            )
        }
    }

    /** 进入「我」Tab 时后台刷新（带节流，避免频繁请求） */
    fun checkOnProfileVisible() {
        val now = System.currentTimeMillis()
        if (now - lastSilentCheckMs < SILENT_CHECK_INTERVAL_MS) return
        lastSilentCheckMs = now
        viewModelScope.launch {
            performCheck(
                showPageChecking = false,
                offerOptionalDialogOnce = false,
                tryWhatsNew = false
            )
        }
    }

    /**
     * 暖启动回到首页：检测更新；可选更新每个 versionCode 仅弹窗一次，之后仅在「我」Tab 红点体现。
     */
    fun checkOnHomeResume() {
        val now = System.currentTimeMillis()
        if (now - lastHomeResumeCheckMs < HOME_RESUME_CHECK_INTERVAL_MS) return
        lastHomeResumeCheckMs = now
        viewModelScope.launch {
            performCheck(
                showPageChecking = false,
                offerOptionalDialogOnce = true,
                tryWhatsNew = false
            )
        }
    }

    /** 更新详情页：进入时拉一次最新结果 */
    fun checkOnUpdatePageVisible() {
        viewModelScope.launch {
            performCheck(
                showPageChecking = true,
                offerOptionalDialogOnce = false,
                tryWhatsNew = false
            )
        }
    }

    /** 设置页手动检查（保留 Toast 反馈） */
    fun checkManual() {
        if (_uiState.value.manualChecking || _uiState.value.phase == UpdatePhase.Checking) return
        if (_uiState.value.phase == UpdatePhase.Downloading) {
            _uiState.update { it.copy(visible = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    manualChecking = true,
                    phase = UpdatePhase.Checking,
                    errorMessage = null
                )
            }
            val result = appUpdateRepository.check()
            analyticsRepository.trackUpdateCheck(result.hasUpdate)
            _uiState.update { it.copy(manualChecking = false) }

            when {
                result.error != null && result.release == null -> {
                    _uiState.update { it.copy(phase = UpdatePhase.Idle) }
                    _toasts.emit(AppUpdateToast.Message(result.error))
                }
                !result.hasUpdate || result.release == null -> {
                    clearPendingUpdate()
                    _uiState.update { it.copy(phase = UpdatePhase.Idle, visible = false) }
                    _toasts.emit(AppUpdateToast.AlreadyLatest)
                }
                else -> {
                    rememberPendingUpdate(result.release, result.forceUpdate)
                    showAvailable(result.release, result.forceUpdate)
                }
            }
        }
    }

    private suspend fun performCheck(
        showPageChecking: Boolean,
        offerOptionalDialogOnce: Boolean,
        tryWhatsNew: Boolean
    ) {
        if (_uiState.value.pageChecking || _uiState.value.manualChecking) return
        if (showPageChecking) {
            _uiState.update { it.copy(pageChecking = true, errorMessage = null) }
        }
        var offeredUpdate = false
        if (appUpdateRepository.isWebsiteChannel()) {
            val result = appUpdateRepository.check()
            analyticsRepository.trackUpdateCheck(result.hasUpdate)
            val release = result.release
            if (result.hasUpdate && release != null &&
                appUpdateRepository.shouldOfferAfterSkip(release, result.forceUpdate)
            ) {
                rememberPendingUpdate(release, result.forceUpdate)
                val shouldPopup = result.forceUpdate ||
                    (offerOptionalDialogOnce &&
                        appUpdateRepository.shouldPromptOptionalUpdate(release))
                if (shouldPopup) {
                    showAvailable(release, result.forceUpdate)
                    if (!result.forceUpdate) {
                        appUpdateRepository.setLastPromptedUpdateVersionCode(release.version_code)
                    }
                    offeredUpdate = true
                }
            } else {
                clearPendingUpdate()
            }
            if (!offeredUpdate && tryWhatsNew) {
                tryShowWhatsNew(result.release)
            }
        } else if (tryWhatsNew) {
            tryShowWhatsNew(remote = null)
        }
        if (showPageChecking) {
            _uiState.update { it.copy(pageChecking = false) }
        }
    }

    private fun rememberPendingUpdate(release: AppReleaseResponse, forceUpdate: Boolean) {
        _pendingUpdate.value = PendingUpdateInfo(release = release, forceUpdate = forceUpdate)
    }

    private fun clearPendingUpdate() {
        _pendingUpdate.value = null
    }

    private suspend fun tryShowWhatsNew(remote: AppReleaseResponse?) {
        if (_uiState.value.visible || _whatsNew.value.visible) return
        var content = appUpdateRepository.resolveWhatsNew(remote)
        if (content == null && remote == null) {
            val result = runCatching { appUpdateRepository.check() }.getOrNull()
            if (_uiState.value.visible) return
            content = appUpdateRepository.resolveWhatsNew(result?.release)
        }
        content?.let { presentWhatsNew(it) }
    }

    private fun presentWhatsNew(content: WhatsNewContent) {
        _whatsNew.value = WhatsNewUiState(
            visible = true,
            versionCode = content.versionCode,
            versionName = content.versionName,
            changelog = content.changelog
        )
    }

    fun dismissWhatsNew() {
        val code = _whatsNew.value.versionCode
        if (code > 0) appUpdateRepository.markWhatsNewSeen(code)
        _whatsNew.value = WhatsNewUiState()
    }

    private fun showAvailable(release: AppReleaseResponse, forceUpdate: Boolean) {
        appUpdateRepository.cacheWhatsNewFromRelease(release)
        val existing = _uiState.value
        // 已在下载/待安装同一版本时，只确保弹窗可见
        if (existing.release?.version_code == release.version_code &&
            existing.phase in listOf(UpdatePhase.Downloading, UpdatePhase.ReadyToInstall, UpdatePhase.Installing)
        ) {
            _uiState.update { it.copy(visible = true, forceUpdate = forceUpdate || it.forceUpdate) }
            return
        }
        _uiState.value = AppUpdateUiState(
            phase = UpdatePhase.Available,
            visible = true,
            forceUpdate = forceUpdate,
            release = release,
            downloadProgress = 0f,
            localApk = null,
            errorMessage = null,
            manualChecking = false
        )
    }

    fun dismissOptional() {
        val state = _uiState.value
        if (state.forceUpdate) return
        if (state.phase == UpdatePhase.Downloading) {
            downloadJob?.cancel()
            downloadJob = null
        }
        state.release?.version_code?.let {
            dismissedThisSessionVersion = it
            appUpdateRepository.setLastPromptedUpdateVersionCode(it)
        }
        _uiState.value = AppUpdateUiState()
    }

    fun skipThisVersion() {
        val state = _uiState.value
        if (state.forceUpdate) return
        val code = state.release?.version_code ?: _pendingUpdate.value?.release?.version_code ?: return
        if (state.phase == UpdatePhase.Downloading) {
            downloadJob?.cancel()
            downloadJob = null
        }
        appUpdateRepository.skipVersion(code)
        appUpdateRepository.setLastPromptedUpdateVersionCode(code)
        dismissedThisSessionVersion = code
        clearPendingUpdate()
        _uiState.value = AppUpdateUiState()
    }

    fun startDownload() {
        startDownload(presentAsDialog = true)
    }

    private fun startDownload(presentAsDialog: Boolean) {
        val release = _uiState.value.release ?: return
        if (!appUpdateRepository.isWebsiteChannel()) {
            // 非官网渠道：外链
            openExternal()
            return
        }
        if (release.apk_url.isBlank()) {
            openExternal()
            return
        }
        if (downloadJob?.isActive == true) return

        downloadJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    phase = UpdatePhase.Downloading,
                    visible = presentAsDialog,
                    downloadProgress = 0f,
                    errorMessage = null,
                    localApk = null
                )
            }
            val result = appUpdateRepository.downloadApk(release) { progress ->
                _uiState.update { it.copy(downloadProgress = progress) }
            }
            result.fold(
                onSuccess = { file ->
                    _uiState.update {
                        it.copy(
                            phase = UpdatePhase.ReadyToInstall,
                            downloadProgress = 1f,
                            localApk = file,
                            errorMessage = null
                        )
                    }
                    // 下载完成自动尝试安装
                    installDownloaded()
                },
                onFailure = { e ->
                    _uiState.update {
                        it.copy(
                            phase = UpdatePhase.Error,
                            errorMessage = e.message?.takeIf { m -> m.isNotBlank() } ?: "下载失败"
                        )
                    }
                }
            )
        }
    }

    fun installDownloaded() {
        val file = _uiState.value.localApk ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(phase = UpdatePhase.Installing, errorMessage = null) }
            val result = appUpdateRepository.installApk(file)
            result.fold(
                onSuccess = {
                    // 安装器拉起后保持 Ready，便于用户取消安装后重试
                    _uiState.update { it.copy(phase = UpdatePhase.ReadyToInstall) }
                },
                onFailure = { e ->
                    if (e is SecurityException &&
                        e.message == AppUpdateRepository.NEED_INSTALL_PERMISSION
                    ) {
                        _uiState.update {
                            it.copy(
                                phase = UpdatePhase.ReadyToInstall,
                                errorMessage = AppUpdateRepository.NEED_INSTALL_PERMISSION
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                phase = UpdatePhase.Error,
                                errorMessage = e.message?.takeIf { m -> m.isNotBlank() }
                                    ?: "无法打开安装程序"
                            )
                        }
                    }
                }
            )
        }
    }

    fun clearInstallPermissionError() {
        _uiState.update {
            if (it.errorMessage == AppUpdateRepository.NEED_INSTALL_PERMISSION) {
                it.copy(errorMessage = null)
            } else it
        }
    }

    fun openExternal() {
        val release = _uiState.value.release ?: return
        val url = appUpdateRepository.openUrlPreferApk(release)
        viewModelScope.launch {
            _toasts.emit(AppUpdateToast.OpenUrl(url))
        }
    }

    fun retry() {
        val state = _uiState.value
        val presentAsDialog = state.visible
        when {
            state.localApk != null && state.localApk.exists() -> installDownloaded()
            state.release != null -> startDownload(presentAsDialog)
            else -> checkManual()
        }
    }

    fun installPermissionSettingsIntent() =
        appUpdateRepository.installPermissionSettingsIntent()

    fun needsInstallPermission(): Boolean =
        !appUpdateRepository.canInstallPackages()

    /** 更新详情页：用已缓存的待更新信息，或当前弹窗态 */
    fun prepareUpdatePage() {
        val pending = _pendingUpdate.value ?: return
        val existing = _uiState.value
        if (existing.release?.version_code == pending.release.version_code &&
            existing.phase != UpdatePhase.Idle
        ) {
            return
        }
        _uiState.value = AppUpdateUiState(
            phase = UpdatePhase.Available,
            visible = false,
            forceUpdate = pending.forceUpdate,
            release = pending.release
        )
    }

    fun startDownloadFromPage() {
        val pending = _pendingUpdate.value
        if (_uiState.value.release == null && pending != null) {
            _uiState.value = AppUpdateUiState(
                phase = UpdatePhase.Available,
                visible = false,
                forceUpdate = pending.forceUpdate,
                release = pending.release
            )
        }
        startDownload(presentAsDialog = false)
    }

    companion object {
        private const val SILENT_CHECK_INTERVAL_MS = 5 * 60 * 1000L
        private const val HOME_RESUME_CHECK_INTERVAL_MS = 30_000L
    }
}
