package com.life.mindfulnessapp.ui.discover

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.UsageOverviewCut
import com.life.mindfulnessapp.domain.model.UsageOverviewSnapshot
import com.life.mindfulnessapp.domain.model.WeekOverviewCut
import com.life.mindfulnessapp.domain.model.WeekOverviewSnapshot
import com.life.mindfulnessapp.domain.model.computeUsageOverview
import com.life.mindfulnessapp.domain.model.computeWeekOverview
import com.life.mindfulnessapp.domain.model.formatOverviewRangeLabel
import com.life.mindfulnessapp.domain.model.rollingSevenDayRange
import com.life.mindfulnessapp.ui.navigation.OverviewNavBridge
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar
import javax.inject.Inject

enum class OverviewLens {
    GATE,
    USAGE
}

data class OverviewUiState(
    val lens: OverviewLens = OverviewLens.GATE,
    val gateCut: WeekOverviewCut = WeekOverviewCut.EVENT,
    val usageCut: UsageOverviewCut = UsageOverviewCut.TOTAL,
    val selectedDayStartMs: Long = 0L,
    val rangeLabel: String = "",
    val gate: WeekOverviewSnapshot? = null,
    val usage: UsageOverviewSnapshot? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OverviewViewModel @Inject constructor(
    private val savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    usageRecordRepository: UsageRecordRepository
) : ViewModel() {

    private val initialLens = parseLens(savedStateHandle.get<String>(ARG_LENS))
    private val initialCutRaw = savedStateHandle.get<String>(ARG_CUT).orEmpty()
    private val initialDateMs =
        savedStateHandle.get<Long>(ARG_DATE_MS)
            ?: savedStateHandle.get<String>(ARG_DATE_MS)?.toLongOrNull()
            ?: 0L

    private val lens = MutableStateFlow(initialLens)
    private val gateCut = MutableStateFlow(
        if (initialLens == OverviewLens.GATE) parseGateCut(initialCutRaw) else WeekOverviewCut.EVENT
    )
    private val usageCut = MutableStateFlow(
        if (initialLens == OverviewLens.USAGE) parseUsageCut(initialCutRaw) else UsageOverviewCut.TOTAL
    )
    private val selectedDay = MutableStateFlow(dayStartOf(initialDateMs))

    init {
        // 收据「总览 ›」pop 回来时：更新选中日/切片，不重建本页
        viewModelScope.launch {
            savedStateHandle
                .getStateFlow<Long?>(OverviewNavBridge.DATE_MS, null)
                .filterNotNull()
                .collect { dateMs ->
                    applyBridgeReveal(
                        dateMs = dateMs,
                        lensRaw = savedStateHandle.get<String>(OverviewNavBridge.LENS),
                        cutRaw = savedStateHandle.get<String>(OverviewNavBridge.CUT)
                    )
                    savedStateHandle[OverviewNavBridge.DATE_MS] = null
                    savedStateHandle[OverviewNavBridge.LENS] = null
                    savedStateHandle[OverviewNavBridge.CUT] = null
                }
        }
    }

    private val rangeFlow = flow { emit(rollingSevenDayRange()) }

    private val snapshotsFlow = rangeFlow.flatMapLatest { (start, end) ->
        usageRecordRepository.getWeekRecords(start, end).map { records ->
            val names = resolveAppNames(records.map { it.packageName }.distinct())
            val gate = computeWeekOverview(records, start, end, names)
            val usage = computeUsageOverview(records, start, end, names)
            Triple(gate, usage, formatOverviewRangeLabel(start, end))
        }.flowOn(Dispatchers.IO)
    }

    val state: StateFlow<OverviewUiState> = combine(
        snapshotsFlow,
        lens,
        gateCut,
        usageCut,
        selectedDay
    ) { snaps, lensVal, gCut, uCut, day ->
        val (gate, usage, rangeLabel) = snaps
        val selected = when {
            day > 0L -> day
            gate.byDay.isNotEmpty() -> gate.byDay.last().dayStartMs
            else -> 0L
        }
        OverviewUiState(
            lens = lensVal,
            gateCut = gCut,
            usageCut = uCut,
            selectedDayStartMs = selected,
            rangeLabel = rangeLabel,
            gate = gate,
            usage = usage
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OverviewUiState(lens = initialLens)
    )

    fun selectLens(value: OverviewLens) {
        if (lens.value == value) return
        val from = lens.value
        lens.value = value
        // App / 按日跨镜头保持；事件↔合计无对齐，落到对面汇总默认。选中日始终保留。
        when (value) {
            OverviewLens.GATE -> {
                gateCut.value = when {
                    from == OverviewLens.USAGE && usageCut.value == UsageOverviewCut.APP ->
                        WeekOverviewCut.APP
                    from == OverviewLens.USAGE && usageCut.value == UsageOverviewCut.DAY ->
                        WeekOverviewCut.DAY
                    else -> WeekOverviewCut.EVENT
                }
            }
            OverviewLens.USAGE -> {
                usageCut.value = when {
                    from == OverviewLens.GATE && gateCut.value == WeekOverviewCut.APP ->
                        UsageOverviewCut.APP
                    from == OverviewLens.GATE && gateCut.value == WeekOverviewCut.DAY ->
                        UsageOverviewCut.DAY
                    else -> UsageOverviewCut.TOTAL
                }
            }
        }
    }

    fun selectGateCut(value: WeekOverviewCut) {
        gateCut.value = value
    }

    fun selectUsageCut(value: UsageOverviewCut) {
        usageCut.value = value
    }

    fun selectDay(dayStartMs: Long) {
        selectedDay.value = dayStartMs
    }

    /** 从收据露出时套用桥接意图；不走 selectLens，避免打乱已保持的切片习惯以外的状态。 */
    private fun applyBridgeReveal(dateMs: Long, lensRaw: String?, cutRaw: String?) {
        val targetLens = parseLens(lensRaw)
        lens.value = targetLens
        when (targetLens) {
            OverviewLens.GATE -> gateCut.value = parseGateCut(cutRaw?.ifBlank { "day" } ?: "day")
            OverviewLens.USAGE -> usageCut.value = parseUsageCut(cutRaw?.ifBlank { "day" } ?: "day")
        }
        if (dateMs > 0L) {
            selectedDay.value = dayStartOf(dateMs)
        }
    }

    private fun resolveAppNames(packages: List<String>): Map<String, String> {
        val pm = context.packageManager
        return packages.associateWith { pkg ->
            try {
                val ai = pm.getApplicationInfo(pkg, 0)
                pm.getApplicationLabel(ai).toString()
            } catch (_: PackageManager.NameNotFoundException) {
                pkg.substringAfterLast('.')
            }
        }
    }

    companion object {
        const val ARG_LENS = "lens"
        const val ARG_CUT = "cut"
        const val ARG_DATE_MS = "dateMs"

        fun parseLens(raw: String?): OverviewLens = when (raw?.trim()?.lowercase()) {
            "usage", "用量", "time", "duration" -> OverviewLens.USAGE
            else -> OverviewLens.GATE
        }

        fun parseGateCut(raw: String?): WeekOverviewCut = when (raw?.trim()?.lowercase()) {
            "app" -> WeekOverviewCut.APP
            "day" -> WeekOverviewCut.DAY
            else -> WeekOverviewCut.EVENT
        }

        fun parseUsageCut(raw: String?): UsageOverviewCut = when (raw?.trim()?.lowercase()) {
            "app" -> UsageOverviewCut.APP
            "day" -> UsageOverviewCut.DAY
            else -> UsageOverviewCut.TOTAL
        }

        private fun dayStartOf(timeMs: Long): Long {
            if (timeMs <= 0L) return 0L
            val cal = Calendar.getInstance().apply {
                timeInMillis = timeMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }
    }
}
