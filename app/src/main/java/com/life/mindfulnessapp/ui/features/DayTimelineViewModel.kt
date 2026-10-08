package com.life.mindfulnessapp.ui.features

import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.util.Calendar
import javax.inject.Inject

/** 总览时间线上的一次会话（进入 / 时长 / 意图 / 离开） */
data class DayAxisSession(
    val recordId: Long,
    val startTime: Long,
    val endTime: Long,
    val appName: String,
    val durationSeconds: Long,
    val purpose: String?,
    /** 离开侧短文案，如「离开」「守住」「主动结束」 */
    val leaveLabel: String,
    val isGateQuit: Boolean
)

@HiltViewModel
class DayTimelineViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val usageRecordRepository: UsageRecordRepository,
    private val analyticsRepository: AnalyticsRepository
) : ViewModel() {

    /** page 0 = 今天往前 [DAY_COUNT-1] 天；末页 = 今天 */
    val dayCount: Int = DAY_COUNT

    private val _selectedPage = MutableStateFlow(DAY_COUNT - 1)
    val selectedPage: StateFlow<Int> = _selectedPage

    private var viewTracked = false

    fun trackViewIfNeeded() {
        if (viewTracked) return
        viewTracked = true
        analyticsRepository.trackDayTimelineView()
    }

    fun selectPage(page: Int) {
        _selectedPage.value = page.coerceIn(0, dayCount - 1)
    }

    fun dayStartMs(page: Int): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, page - (dayCount - 1))
        }
        return cal.timeInMillis
    }

    fun sessionsForPage(page: Int): Flow<List<DayAxisSession>> {
        val dayStart = dayStartMs(page)
        val dayEnd = dayStart + DAY_MS
        return usageRecordRepository.getDayRecords(dayStart, dayEnd)
            .map { records ->
                records
                    .sortedBy { it.startTime }
                    .map { record -> toSession(record) }
            }
            .flowOn(Dispatchers.IO)
    }

    private fun toSession(record: UsageRecordEntity): DayAxisSession {
        val gateQuit = record.isGateQuit
        val leaveLabel = when {
            gateQuit && record.endReason == UsageRecordEntity.EndReason.GATE_PASSIVE -> "被动离开"
            gateQuit -> "守住"
            record.endReason == UsageRecordEntity.EndReason.MANUAL -> "离开"
            else -> UsageRecordEntity.EndReason.displayKindLabel(record.endReason)
                ?: UsageRecordEntity.EndReason.softEndReasonLabel(record.endReason)
                ?: "离开"
        }
        return DayAxisSession(
            recordId = record.id,
            startTime = record.startTime,
            endTime = record.endTime,
            appName = resolveAppName(record.packageName),
            durationSeconds = record.durationSeconds.coerceAtLeast(0L),
            purpose = record.purpose?.trim()?.takeIf { it.isNotEmpty() },
            leaveLabel = leaveLabel,
            isGateQuit = gateQuit
        )
    }

    private fun resolveAppName(packageName: String): String {
        val pm = context.packageManager
        return try {
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        }
    }

    companion object {
        const val DAY_COUNT = 90
        const val DAY_MS = 24L * 60L * 60L * 1000L

        fun formatDuration(seconds: Long): String {
            if (seconds <= 0L) return "0分"
            val totalMin = seconds / 60L
            return when {
                totalMin < 60L -> "${totalMin}分"
                else -> {
                    val h = totalMin / 60L
                    val m = totalMin % 60L
                    if (m == 0L) "${h}小时" else "${h}小时${m}分"
                }
            }
        }

        fun formatClock(timeMs: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
            return "%02d:%02d".format(
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE)
            )
        }
    }
}
