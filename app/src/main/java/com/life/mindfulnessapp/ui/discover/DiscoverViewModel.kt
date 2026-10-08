package com.life.mindfulnessapp.ui.discover

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.repository.PlanBlockRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.domain.model.computeUsageOverview
import com.life.mindfulnessapp.domain.model.computeWeekOverview
import com.life.mindfulnessapp.domain.model.formatOverviewDuration
import com.life.mindfulnessapp.domain.model.rollingSevenDayRange
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class DiscoverUiState(
    val overviewGlance: String = "还没有",
    val scheduleGlance: String = "还没有",
    val awarenessGlance: String = "关",
    val quoteGlance: String = "未开推送",
    val wallpaperGlance: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class DiscoverViewModel @Inject constructor(
    usageRecordRepository: UsageRecordRepository,
    planBlockRepository: PlanBlockRepository,
    appPreferences: AppPreferences
) : ViewModel() {

    private val overviewGlanceFlow = flow {
        emit(rollingSevenDayRange())
    }.flatMapLatest { (start, end) ->
        usageRecordRepository.getWeekRecords(start, end).map { records ->
            val gate = computeWeekOverview(records, start, end)
            val usage = computeUsageOverview(records, start, end)
            overviewGlanceLine(gate.heldCount, gate.browseCount, gate.enterCount, usage.totalDurationSeconds)
        }
    }

    private val awarenessGlanceFlow = combine(
        appPreferences.awarenessPracticeEnabled,
        appPreferences.awarenessPracticeRhythm
    ) { enabled, rhythm ->
        com.life.mindfulnessapp.domain.model.AwarenessPracticeCopy.glanceMeta(enabled, rhythm)
    }

    val state: StateFlow<DiscoverUiState> = combine(
        overviewGlanceFlow,
        planBlockRepository.observeAll(),
        awarenessGlanceFlow,
        appPreferences.scheduledQuotePushEnabled
    ) { overviewGlance, plans, awarenessGlance, quoteOn ->
        DiscoverUiState(
            overviewGlance = overviewGlance,
            scheduleGlance = scheduleGlance(plans),
            awarenessGlance = awarenessGlance,
            quoteGlance = if (quoteOn) "推送已开" else "未开推送",
            wallpaperGlance = ""
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DiscoverUiState()
    )

    private fun scheduleGlance(plans: List<PlanBlock>): String {
        if (plans.isEmpty()) return "还没有"
        val active = PlanBlockPolicy.activeNow(plans)
        if (active != null) {
            return "生效中 · ${active.title} ${active.label()}"
        }
        val enabled = plans.count { it.enabled }
        return if (enabled > 0) "$enabled 个时段" else "已关闭"
    }

    companion object {
        fun overviewGlanceLine(
            heldCount: Int,
            browseCount: Int,
            enterCount: Int,
            totalDurationSeconds: Long
        ): String {
            val hasGate = heldCount + enterCount > 0
            val hasUsage = totalDurationSeconds > 0L
            return when {
                !hasGate && !hasUsage -> "还没有"
                hasGate && hasUsage -> buildString {
                    append("守住 $heldCount 次")
                    append(" · ")
                    append(formatOverviewDuration(totalDurationSeconds))
                }
                hasGate -> buildString {
                    append("守住 $heldCount 次")
                    if (browseCount > 0) append(" · 刷 $browseCount 次")
                    else if (enterCount > 0) append(" · 进入 $enterCount 次")
                }
                else -> formatOverviewDuration(totalDurationSeconds)
            }
        }
    }
}
