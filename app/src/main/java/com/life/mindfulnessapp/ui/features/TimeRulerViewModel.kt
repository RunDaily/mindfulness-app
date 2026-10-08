package com.life.mindfulnessapp.ui.features

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.TimeRulerBlock
import com.life.mindfulnessapp.domain.model.TimeRulerDaySummary
import com.life.mindfulnessapp.domain.model.TimeRulerMode
import com.life.mindfulnessapp.domain.model.TimeRulerWeekDay
import com.life.mindfulnessapp.domain.model.buildTimeRulerBlocks
import com.life.mindfulnessapp.domain.model.buildTimeRulerWeekDays
import com.life.mindfulnessapp.domain.model.resolveTimeRulerRange
import com.life.mindfulnessapp.domain.model.shiftTimeRulerAnchor
import com.life.mindfulnessapp.domain.model.summarizeTimeRulerBlocks
import com.life.mindfulnessapp.domain.model.timeRulerDayLabel
import com.life.mindfulnessapp.domain.model.timeRulerWeekLabel
import com.life.mindfulnessapp.util.AppIconColorExtractor
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class TimeRulerPitApp(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val color: Color
)

/** 日视图右侧：心锚意图流水（与系统刻度对照） */
data class TimeRulerIntentRow(
    val recordId: Long,
    val packageName: String,
    val appName: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long,
    val purpose: String?,
    val isGateQuit: Boolean,
    val isGateToOwn: Boolean,
    val mindfulnessLevel: Int?
)

data class TimeRulerUiState(
    val pitApps: List<TimeRulerPitApp> = emptyList(),
    val selectedPackages: Set<String> = emptySet(),
    val mode: TimeRulerMode = TimeRulerMode.Week,
    val anchorMs: Long = System.currentTimeMillis(),
    val rangeLabel: String = "",
    val blocks: List<TimeRulerBlock> = emptyList(),
    /** 当前范围（日或周）合计 */
    val rangeSummary: TimeRulerDaySummary = TimeRulerDaySummary.Zero,
    val weekDays: List<TimeRulerWeekDay> = emptyList(),
    val intentRows: List<TimeRulerIntentRow> = emptyList(),
    val selectedBlockId: Long? = null,
    val rangeStartMs: Long = 0L,
    val rangeEndMs: Long = 0L,
    val canGoNext: Boolean = false,
    val loadingPitApps: Boolean = true,
    val loadingSessions: Boolean = false
)

private const val MaxSelectedApps = 5

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TimeRulerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLimitRepository: AppLimitRepository,
    private val systemUsageRepository: SystemUsageRepository,
    private val usageRecordRepository: UsageRecordRepository
) : ViewModel() {

    private val _mode = MutableStateFlow(TimeRulerMode.Week)
    private val _anchorMs = MutableStateFlow(System.currentTimeMillis())
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _selectedBlockId = MutableStateFlow<Long?>(null)
    private val _pitApps = MutableStateFlow<List<TimeRulerPitApp>>(emptyList())
    private val _loadingPitApps = MutableStateFlow(true)

    val uiState: StateFlow<TimeRulerUiState> = combine(
        combine(_pitApps, _selectedPackages, _loadingPitApps) { a, b, c -> Triple(a, b, c) },
        combine(_mode, _anchorMs, _selectedBlockId) { a, b, c -> Triple(a, b, c) }
    ) { pitSel, modeAnchor ->
        val (pits, selected, loading) = pitSel
        val (mode, anchor, blockId) = modeAnchor
        QueryBundle(pits, selected, loading, mode, anchor, blockId)
    }.flatMapLatest { bundle ->
        val (rangeStart, rangeEnd) = resolveTimeRulerRange(bundle.anchor, bundle.mode)
        val (todayStart, _) = getDayRange(System.currentTimeMillis())
        val canGoNext = rangeStart < todayStart
        val label = when (bundle.mode) {
            TimeRulerMode.Day -> timeRulerDayLabel(rangeStart, todayStart)
            TimeRulerMode.Week -> timeRulerWeekLabel(rangeStart, rangeEnd)
        }
        val names = bundle.pits.associate { it.packageName to it.appName }

        if (bundle.selected.isEmpty()) {
            flowOf(
                TimeRulerUiState(
                    pitApps = bundle.pits,
                    selectedPackages = bundle.selected,
                    mode = bundle.mode,
                    anchorMs = bundle.anchor,
                    rangeLabel = label,
                    selectedBlockId = bundle.blockId,
                    rangeStartMs = rangeStart,
                    rangeEndMs = rangeEnd,
                    canGoNext = canGoNext,
                    loadingPitApps = bundle.loading
                )
            )
        } else {
            flow {
                emit(
                    TimeRulerUiState(
                        pitApps = bundle.pits,
                        selectedPackages = bundle.selected,
                        mode = bundle.mode,
                        anchorMs = bundle.anchor,
                        rangeLabel = label,
                        selectedBlockId = bundle.blockId,
                        rangeStartMs = rangeStart,
                        rangeEndMs = rangeEnd,
                        canGoNext = canGoNext,
                        loadingPitApps = bundle.loading,
                        loadingSessions = true
                    )
                )
                val sessions = systemUsageRepository.getForegroundSessionsForPackages(
                    packageNames = bundle.selected,
                    rangeStartMs = rangeStart,
                    rangeEndMs = rangeEnd
                )
                val blocks = buildTimeRulerBlocks(
                    sessionsByPackage = sessions,
                    selectedPackages = bundle.selected,
                    appNames = names,
                    rangeStartMs = rangeStart,
                    mode = bundle.mode
                )
                val intentRows = if (bundle.mode == TimeRulerMode.Day) {
                    loadIntentRows(
                        dayStart = rangeStart,
                        dayEnd = rangeEnd,
                        selected = bundle.selected,
                        names = names
                    )
                } else {
                    emptyList()
                }
                val rangeSummary = if (bundle.mode == TimeRulerMode.Day) {
                    TimeRulerDaySummary(
                        totalSeconds = intentRows.sumOf { it.durationSeconds.coerceAtLeast(0L) },
                        openCount = intentRows.size
                    )
                } else {
                    summarizeTimeRulerBlocks(blocks)
                }
                val weekDays = if (bundle.mode == TimeRulerMode.Week) {
                    buildTimeRulerWeekDays(rangeStart, todayStart, blocks)
                } else {
                    emptyList()
                }
                emit(
                    TimeRulerUiState(
                        pitApps = bundle.pits,
                        selectedPackages = bundle.selected,
                        mode = bundle.mode,
                        anchorMs = bundle.anchor,
                        rangeLabel = label,
                        blocks = blocks,
                        rangeSummary = rangeSummary,
                        weekDays = weekDays,
                        intentRows = intentRows,
                        selectedBlockId = bundle.blockId,
                        rangeStartMs = rangeStart,
                        rangeEndMs = rangeEnd,
                        canGoNext = canGoNext,
                        loadingPitApps = bundle.loading,
                        loadingSessions = false
                    )
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeRulerUiState())

    init {
        viewModelScope.launch { loadPitApps() }
    }

    private suspend fun loadIntentRows(
        dayStart: Long,
        dayEnd: Long,
        selected: Set<String>,
        names: Map<String, String>
    ): List<TimeRulerIntentRow> = withContext(Dispatchers.IO) {
        // 日视图只关心「真正进入」的意图会话；守住离开不进列表、也不占刻度
        val records = usageRecordRepository.getDayRecords(dayStart, dayEnd).first()
            .filter { it.packageName in selected && !it.isGateQuit && !it.isSeed }
            .sortedBy { it.startTime }
        records.map { r ->
            TimeRulerIntentRow(
                recordId = r.id,
                packageName = r.packageName,
                appName = names[r.packageName] ?: r.packageName.substringAfterLast('.'),
                startTime = r.startTime,
                endTime = r.endTime,
                durationSeconds = r.durationSeconds,
                purpose = r.purpose?.trim()?.takeIf { it.isNotEmpty() }
                    ?.takeUnless { IntentKind.fromStorage(r.intentKind) == IntentKind.PURPOSELESS },
                isGateQuit = false,
                isGateToOwn = false,
                mindfulnessLevel = r.mindfulnessLevel
                    ?.takeIf { UsageRecordEntity.MindfulnessLevel.isValid(it) }
            )
        }
    }

    private suspend fun loadPitApps() {
        _loadingPitApps.value = true
        val limits = appLimitRepository.getEnabledAppLimits().first()
        val apps = withContext(Dispatchers.IO) {
            val pm = context.packageManager
            limits.mapIndexed { index, limit ->
                val icon = try {
                    pm.getApplicationIcon(limit.packageName)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                }
                val name = try {
                    pm.getApplicationLabel(pm.getApplicationInfo(limit.packageName, 0)).toString()
                } catch (_: PackageManager.NameNotFoundException) {
                    limit.appName
                }
                TimeRulerPitApp(
                    packageName = limit.packageName,
                    appName = name,
                    icon = icon,
                    color = AppIconColorExtractor.extract(icon, seed = index)
                )
            }
        }
        _pitApps.value = apps
        if (_selectedPackages.value.isEmpty() && apps.isNotEmpty()) {
            _selectedPackages.value = setOf(apps.first().packageName)
        } else {
            _selectedPackages.update { cur ->
                cur.filter { pkg -> apps.any { it.packageName == pkg } }.toSet()
            }
        }
        _loadingPitApps.value = false
    }

    fun toggleApp(packageName: String) {
        _selectedPackages.update { cur ->
            when {
                packageName in cur -> {
                    if (cur.size <= 1) cur else cur - packageName
                }
                cur.size >= MaxSelectedApps -> cur
                else -> cur + packageName
            }
        }
        _selectedBlockId.value = null
    }

    fun setMode(mode: TimeRulerMode) {
        if (_mode.value == mode) return
        _mode.value = mode
        _selectedBlockId.value = null
    }

    fun toggleMode() {
        setMode(
            if (_mode.value == TimeRulerMode.Week) TimeRulerMode.Day else TimeRulerMode.Week
        )
    }

    fun shiftAnchor(delta: Int) {
        val mode = _mode.value
        val next = shiftTimeRulerAnchor(_anchorMs.value, mode, delta)
        val (todayStart, _) = getDayRange(System.currentTimeMillis())
        val (nextStart, _) = resolveTimeRulerRange(next, mode)
        if (delta > 0 && nextStart > todayStart) return
        _anchorMs.value = next
        _selectedBlockId.value = null
    }

    /** 周视图点某天 → 进入该日日视图 */
    fun openDay(dayStartMs: Long) {
        val (todayStart, _) = getDayRange(System.currentTimeMillis())
        if (dayStartMs > todayStart) return
        _anchorMs.value = dayStartMs
        _mode.value = TimeRulerMode.Day
        _selectedBlockId.value = null
    }

    fun selectBlock(blockId: Long?) {
        _selectedBlockId.value = blockId
    }

    fun colorOf(packageName: String): Color =
        _pitApps.value.firstOrNull { it.packageName == packageName }?.color
            ?: Color(0xFF26BB68)
}

private data class QueryBundle(
    val pits: List<TimeRulerPitApp>,
    val selected: Set<String>,
    val loading: Boolean,
    val mode: TimeRulerMode,
    val anchor: Long,
    val blockId: Long?
)
