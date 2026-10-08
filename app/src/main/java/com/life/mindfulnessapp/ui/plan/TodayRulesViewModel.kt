package com.life.mindfulnessapp.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.RulePlanRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.TodayRuleRow
import com.life.mindfulnessapp.domain.model.UsageDigestRow
import com.life.mindfulnessapp.domain.model.UsageRecordCounts
import com.life.mindfulnessapp.domain.model.paretoCoreCountByDuration
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

data class TodayUiState(
    val rows: List<TodayRuleRow> = emptyList(),
    /** 无规则时：机上近 7 日 Top（系统口径） */
    val phoneHeat: List<UsageDigestRow> = emptyList(),
    val hasUsagePermission: Boolean = true,
    val loading: Boolean = true,
    /** 今日守住次数（peek） */
    val heldCount: Int = 0,
    /** 今日进入次数 = 搜索 + 写下 + 随意浏览（peek） */
    val enterCount: Int = 0
) {
    val heldHint: String
        get() = when {
            rows.isNotEmpty() -> "${rows.size} 个 App 在规则里"
            phoneHeat.isNotEmpty() -> {
                val core = paretoCoreCountByDuration(phoneHeat)
                if (core > 0) "近 7 日 · 八成在前 $core 个"
                else "近 7 日 · 这台手机"
            }
            !hasUsagePermission -> "需要使用情况访问"
            else -> "还没有规则"
        }
}

@HiltViewModel
class TodayRulesViewModel @Inject constructor(
    private val repository: RulePlanRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val permissions: CheckPermissionsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(TodayUiState())
    val state: StateFlow<TodayUiState> = _state.asStateFlow()

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val rows = repository.todayRows()
            val hasPerm = permissions().hasUsageStats
            val heat = if (rows.isEmpty() && hasPerm) {
                repository.usageForAdvice()
            } else {
                emptyList()
            }
            val (held, enters) = if (rows.isNotEmpty()) todayGatePeek() else 0 to 0
            _state.value = TodayUiState(
                rows = rows,
                phoneHeat = heat,
                hasUsagePermission = hasPerm,
                loading = false,
                heldCount = held,
                enterCount = enters
            )
        }
    }

    private suspend fun todayGatePeek(): Pair<Int, Int> {
        val (start, end) = UsageRecordRepository.getDayRange(System.currentTimeMillis())
        val records = usageRecordRepository.getDayRecords(start, end).first()
        return UsageRecordCounts.dismissCount(records) to UsageRecordCounts.enterCount(records)
    }
}
