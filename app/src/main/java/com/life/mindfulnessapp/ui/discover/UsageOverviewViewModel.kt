package com.life.mindfulnessapp.ui.discover

import android.content.Context
import android.content.pm.PackageManager
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.UsageOverviewCut
import com.life.mindfulnessapp.domain.model.UsageOverviewSnapshot
import com.life.mindfulnessapp.domain.model.computeUsageOverview
import com.life.mindfulnessapp.domain.model.formatOverviewMonthDay
import com.life.mindfulnessapp.domain.model.formatOverviewRangeLabel
import com.life.mindfulnessapp.domain.model.formatOverviewWeekdayShort
import com.life.mindfulnessapp.domain.model.rollingSevenDayRange
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class UsageOverviewUiState(
    val snapshot: UsageOverviewSnapshot? = null,
    val cut: UsageOverviewCut = UsageOverviewCut.TOTAL,
    val selectedDayStartMs: Long = 0L,
    val rangeLabel: String = ""
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UsageOverviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    usageRecordRepository: UsageRecordRepository
) : ViewModel() {

    private val initialCut = parseCut(savedStateHandle.get<String>(ARG_CUT))
    private val initialDateMs =
        savedStateHandle.get<Long>(ARG_DATE_MS)
            ?: savedStateHandle.get<String>(ARG_DATE_MS)?.toLongOrNull()
            ?: 0L

    private val cut = MutableStateFlow(initialCut)
    private val selectedDay = MutableStateFlow(dayStartOf(initialDateMs))

    private val rangeFlow = flow { emit(rollingSevenDayRange()) }

    private val snapshotFlow = rangeFlow.flatMapLatest { (start, end) ->
        usageRecordRepository.getWeekRecords(start, end).map { records ->
            val names = resolveAppNames(records.map { it.packageName }.distinct())
            computeUsageOverview(
                records = records,
                rangeStartMs = start,
                rangeEndMs = end,
                appNames = names
            )
        }.flowOn(Dispatchers.IO)
    }

    val state: StateFlow<UsageOverviewUiState> = combine(
        snapshotFlow,
        cut,
        selectedDay
    ) { snap, c, day ->
        val selected = when {
            day > 0L -> day
            snap.byDay.isNotEmpty() -> snap.byDay.last().dayStartMs
            else -> 0L
        }
        UsageOverviewUiState(
            snapshot = snap,
            cut = c,
            selectedDayStartMs = selected,
            rangeLabel = formatOverviewRangeLabel(snap.rangeStartMs, snap.rangeEndMs)
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UsageOverviewUiState(cut = initialCut)
    )

    fun selectCut(value: UsageOverviewCut) {
        cut.value = value
    }

    fun selectDay(dayStartMs: Long) {
        selectedDay.value = dayStartMs
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
        const val ARG_CUT = "cut"
        const val ARG_DATE_MS = "dateMs"

        fun formatMonthDay(timeMs: Long): String = formatOverviewMonthDay(timeMs)
        fun formatWeekdayShort(timeMs: Long): String = formatOverviewWeekdayShort(timeMs)

        fun parseCut(raw: String?): UsageOverviewCut = when (raw?.trim()?.lowercase()) {
            "app" -> UsageOverviewCut.APP
            "day" -> UsageOverviewCut.DAY
            else -> UsageOverviewCut.TOTAL
        }

        private fun dayStartOf(timeMs: Long): Long {
            if (timeMs <= 0L) return 0L
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = timeMs
                set(java.util.Calendar.HOUR_OF_DAY, 0)
                set(java.util.Calendar.MINUTE, 0)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            return cal.timeInMillis
        }
    }
}
