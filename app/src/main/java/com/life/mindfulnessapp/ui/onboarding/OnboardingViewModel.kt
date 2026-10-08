package com.life.mindfulnessapp.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.domain.model.AppCapabilityFit
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppWeeklySystemUsage
import com.life.mindfulnessapp.domain.model.BatchPickAppRow
import com.life.mindfulnessapp.domain.model.BatchPickSortMode
import com.life.mindfulnessapp.domain.model.CapabilityFitAssessor
import com.life.mindfulnessapp.domain.model.UsageInsights
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.util.BatchPickSorting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class FinishPermissionKind {
    Overlay,
    Battery,
    Accessibility
}

enum class OnboardingPhase {
    Welcome,
    UsageAccess,
    Mirror,
    Guide,
    FinishPermissions
}

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val checkPermissionsUseCase: CheckPermissionsUseCase,
    private val analyticsRepository: AnalyticsRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val systemUsageRepository: SystemUsageRepository
) : ViewModel() {

    private val _permissionStatus = MutableStateFlow(checkPermissionsUseCase())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _phase = MutableStateFlow(OnboardingPhase.Welcome)
    val phase: StateFlow<OnboardingPhase> = _phase.asStateFlow()

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _usageByPackage = MutableStateFlow<Map<String, AppWeeklySystemUsage>>(emptyMap())
    val usageLoading = MutableStateFlow(false)

    private val _sortMode = MutableStateFlow(BatchPickSortMode.Duration)
    val sortMode: StateFlow<BatchPickSortMode> = _sortMode.asStateFlow()

    private val _insights = MutableStateFlow<UsageInsights?>(null)
    val insights: StateFlow<UsageInsights?> = _insights.asStateFlow()

    private val _recommendations = MutableStateFlow<List<AppCapabilityFit>>(emptyList())
    val recommendations: StateFlow<List<AppCapabilityFit>> = _recommendations.asStateFlow()

    val mirrorRows: StateFlow<List<BatchPickAppRow>> = combine(
        _allApps,
        _usageByPackage,
        _sortMode
    ) { apps, usage, mode ->
        BatchPickSorting.sort(BatchPickSorting.toRows(apps, usage), mode)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lastTrackedStep: String? = null
    private var usageLoaded = false

    init {
        trackStep(HaEvents.OnboardingStep.WELCOME)
    }

    fun refreshPermissions() {
        val next = checkPermissionsUseCase()
        val prev = _permissionStatus.value
        if (!prev.hasOverlay && next.hasOverlay) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.OVERLAY,
                HaEvents.Source.ONBOARDING
            )
        }
        if (!prev.hasUsageStats && next.hasUsageStats) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.USAGE,
                HaEvents.Source.ONBOARDING
            )
        }
        if (!prev.hasBatteryOptimizationIgnored && next.hasBatteryOptimizationIgnored) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.BATTERY,
                HaEvents.Source.ONBOARDING
            )
        }
        if (!prev.hasNotification && next.hasNotification) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.NOTIFICATION,
                HaEvents.Source.ONBOARDING
            )
        }
        if (!prev.hasAccessibilityKeepAlive && next.hasAccessibilityKeepAlive) {
            analyticsRepository.trackPermissionGrant(
                HaEvents.Permission.ACCESSIBILITY,
                HaEvents.Source.ONBOARDING
            )
        }
        _permissionStatus.value = next
        if (!prev.hasUsageStats && next.hasUsageStats) {
            loadUsageStats()
        }
    }

    fun trackStep(step: String) {
        if (lastTrackedStep == step) return
        lastTrackedStep = step
        analyticsRepository.trackOnboardingStep(step)
    }

    fun setSortMode(mode: BatchPickSortMode) {
        _sortMode.value = mode
    }

    fun goToPhase(phase: OnboardingPhase) {
        _phase.value = phase
        trackStepForPhase(phase)
    }

    fun advanceFromWelcome() {
        goToPhase(OnboardingPhase.UsageAccess)
    }

    fun advanceFromUsageAccess(skippedUsage: Boolean) {
        if (skippedUsage || !_permissionStatus.value.hasUsageStats) {
            viewModelScope.launch {
                ensureAppsLoaded()
                goToPhase(OnboardingPhase.Guide)
            }
        } else {
            viewModelScope.launch {
                ensureAppsLoaded()
                if (!usageLoaded) loadUsageStatsInternal()
                goToPhase(OnboardingPhase.Mirror)
            }
        }
    }

    fun advanceFromMirror() {
        goToPhase(OnboardingPhase.Guide)
    }

    fun advanceToFinishPermissions() {
        refreshPermissions()
        if (!needsFinishPermissionsPage()) {
            return
        }
        goToPhase(OnboardingPhase.FinishPermissions)
    }

    /** 引导结束或首绑后：无缺失项则直接完成 */
    fun finishOnboardingIfReady(onComplete: () -> Unit): Boolean {
        refreshPermissions()
        if (needsFinishPermissionsPage()) {
            goToPhase(OnboardingPhase.FinishPermissions)
            return false
        }
        completeOnboarding(onComplete)
        return true
    }

    fun needsFinishPermissionsPage(): Boolean {
        val status = _permissionStatus.value
        return missingOptionalPermissions(status).isNotEmpty() ||
            !status.hasOverlay
    }

    /** 收尾页只补：悬浮窗（若缺）+ 可选保活项；使用情况不在此重复索要 */
    fun missingOptionalPermissions(status: PermissionStatus = _permissionStatus.value): List<FinishPermissionKind> =
        buildList {
            if (!status.hasOverlay) add(FinishPermissionKind.Overlay)
            if (!status.hasBatteryOptimizationIgnored) add(FinishPermissionKind.Battery)
            if (!status.hasAccessibilityKeepAlive) add(FinishPermissionKind.Accessibility)
        }

    fun hasCoreBindPermissions(): Boolean {
        val status = _permissionStatus.value
        return status.hasOverlay && status.hasUsageStats
    }

    fun onUsageAccessOpened() {
        viewModelScope.launch { ensureAppsLoaded() }
    }

    fun loadUsageStats() {
        if (!_permissionStatus.value.hasUsageStats) return
        viewModelScope.launch { loadUsageStatsInternal() }
    }

    private suspend fun loadUsageStatsInternal() {
        usageLoading.value = true
        try {
            ensureAppsLoaded()
            val usage = systemUsageRepository.getLast7CompleteDaysUsageByPackage()
            _usageByPackage.value = usage
            val apps = _allApps.value
            _insights.value = CapabilityFitAssessor.buildInsights(apps, usage)
            _recommendations.value = CapabilityFitAssessor.recommend(apps, usage)
            usageLoaded = true
        } finally {
            usageLoading.value = false
        }
    }

    fun completeOnboarding(onDone: () -> Unit) {
        val status = checkPermissionsUseCase()
        if (!status.hasOverlay || !status.hasUsageStats) {
            analyticsRepository.trackPermissionSkip(HaEvents.Source.ONBOARDING)
        }
        analyticsRepository.trackOnboardingComplete(
            overlay = status.hasOverlay,
            usage = status.hasUsageStats,
            battery = status.hasBatteryOptimizationIgnored,
            notification = status.hasNotification
        )
        onDone()
    }

    private suspend fun ensureAppsLoaded() {
        if (_allApps.value.isNotEmpty()) return
        _allApps.value = getInstalledAppsUseCase()
    }

    fun ensureDataForGuide() {
        viewModelScope.launch {
            ensureAppsLoaded()
            if (_permissionStatus.value.hasUsageStats && !usageLoaded) {
                loadUsageStats()
            }
        }
    }

    private fun trackStepForPhase(phase: OnboardingPhase) {
        val step = when (phase) {
            OnboardingPhase.Welcome -> HaEvents.OnboardingStep.WELCOME
            OnboardingPhase.UsageAccess -> HaEvents.OnboardingStep.USAGE_ACCESS
            OnboardingPhase.Mirror -> HaEvents.OnboardingStep.MIRROR
            OnboardingPhase.Guide -> HaEvents.OnboardingStep.GUIDE
            OnboardingPhase.FinishPermissions -> HaEvents.OnboardingStep.FINISH_PERMISSIONS
        }
        trackStep(step)
    }
}
