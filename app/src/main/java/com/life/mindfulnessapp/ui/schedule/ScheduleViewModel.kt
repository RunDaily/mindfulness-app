package com.life.mindfulnessapp.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.PlanBlockRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.PeriodDays
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.domain.model.PlanLockScope
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ScheduleLockUiState(
    val plans: List<PlanBlock> = emptyList(),
    val activeId: String? = null,
    val monitoredApps: List<AppInfo> = emptyList(),
    /** 装机目录包名 → 显示名；列表摘要不绑监控名单 */
    val appLabels: Map<String, String> = emptyMap()
)

/** 编辑态：由 [ScheduleLockEditActivity] 使用。 */
data class ScheduleLockEditorState(
    val itemId: String? = null,
    val title: String = "",
    val startMinute: Int = 9 * 60,
    val endMinute: Int = 12 * 60,
    val daysMask: Int = PeriodDays.WEEKDAYS,
    /** 主：是否跟随当前监控名单 */
    val includeMonitored: Boolean = true,
    /** 从：额外指定的包名（可空；空则只跟监控） */
    val packageNames: List<String> = emptyList(),
    val enabled: Boolean = true,
    val isNew: Boolean = true
) {
    val lockScope: PlanLockScope?
        get() = PlanLockScope.fromFlags(includeMonitored, packageNames)

    companion object {
        private const val MINUTES_PER_DAY = 1440
        private const val DEFAULT_DURATION_MIN = 60
        private const val SNAP_MINUTES = 5

        /** 新建：开始贴近此刻（5 分钟取整），结束 = 开始 + 1 小时（可跨午夜）。 */
        fun forNew(nowMillis: Long = System.currentTimeMillis()): ScheduleLockEditorState {
            val (start, end) = defaultRangeNearNow(nowMillis)
            return ScheduleLockEditorState(
                startMinute = start,
                endMinute = end,
                isNew = true
            )
        }

        fun defaultRangeNearNow(nowMillis: Long = System.currentTimeMillis()): Pair<Int, Int> {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
            val raw = cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 +
                cal.get(java.util.Calendar.MINUTE)
            val rounded = ((raw + SNAP_MINUTES / 2) / SNAP_MINUTES) * SNAP_MINUTES
            val start = when {
                rounded >= MINUTES_PER_DAY -> 0
                else -> rounded
            }
            val end = (start + DEFAULT_DURATION_MIN) % MINUTES_PER_DAY
            return start to end
        }
    }
}

@HiltViewModel
class ScheduleViewModel @Inject constructor(
    private val planBlockRepository: PlanBlockRepository,
    private val appLimitRepository: AppLimitRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val monitoredApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val appLabels = MutableStateFlow<Map<String, String>>(emptyMap())
    private val plansFlow = planBlockRepository.observeAll()

    val state: StateFlow<ScheduleLockUiState> = combine(
        plansFlow,
        monitoredApps,
        appLabels
    ) { plans, monitored, labels ->
        ScheduleLockUiState(
            plans = plans,
            activeId = PlanBlockPolicy.activeNow(plans)?.id,
            monitoredApps = monitored,
            appLabels = labels
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ScheduleLockUiState()
    )

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val enabled = appLimitRepository.getEnabledPackageNames().toSet()
            val installed = getInstalledAppsUseCase().filter { !it.isUninstalled }
            monitoredApps.value = installed
                .filter { it.packageName in enabled }
                .sortedBy { it.appName }
            appLabels.value = installed.associate { it.packageName to it.appName }
        }
    }
}
