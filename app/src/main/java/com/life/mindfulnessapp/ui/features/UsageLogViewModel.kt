package com.life.mindfulnessapp.ui.features

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.PlanBlockRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.data.repository.UsageRecordRepository
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindowsCodec
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.domain.model.UsageLogDisplayNames
import com.life.mindfulnessapp.domain.model.UsageLogListItem
import com.life.mindfulnessapp.domain.model.UsageTransitionBuilder
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import java.util.Calendar
import javax.inject.Inject

data class UsageLogUiState(
    val hasUsageStats: Boolean = true,
    val items: List<UsageLogListItem> = emptyList(),
    val loading: Boolean = true,
    val isToday: Boolean = true
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class UsageLogViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val systemUsageRepository: SystemUsageRepository,
    private val usageRecordRepository: UsageRecordRepository,
    private val appLimitRepository: AppLimitRepository,
    private val planBlockRepository: PlanBlockRepository,
    private val checkPermissionsUseCase: CheckPermissionsUseCase
) : ViewModel() {

    val dayCount: Int = DAY_COUNT

    private val _selectedPage = MutableStateFlow(DAY_COUNT - 1)
    val selectedPage: StateFlow<Int> = _selectedPage

    private val launcherPackages: Set<String> by lazy {
        val homeIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        context.packageManager
            .queryIntentActivities(homeIntent, PackageManager.MATCH_DEFAULT_ONLY)
            .map { it.activityInfo.packageName }
            .toSet()
    }

    private val appNameCache = mutableMapOf<String, String>()

    val uiState: StateFlow<UsageLogUiState> = _selectedPage
        .flatMapLatest { page ->
            val dayStart = dayStartMs(page)
            val dayEnd = dayStart + DAY_MS
            val isToday = page == dayCount - 1
            flow {
                emit(UsageLogUiState(loading = true, isToday = isToday))
                emitAll(
                    usageRecordRepository.getDayRecords(dayStart, dayEnd)
                        .mapLatest { records ->
                            buildDayState(records, dayStart, dayEnd, isToday)
                        }
                        .flowOn(Dispatchers.IO)
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UsageLogUiState(loading = true)
        )

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

    private suspend fun buildDayState(
        records: List<com.life.mindfulnessapp.data.db.entity.UsageRecordEntity>,
        dayStart: Long,
        dayEnd: Long,
        isToday: Boolean
    ): UsageLogUiState {
        val hasStats = checkPermissionsUseCase.hasUsageStatsPermission()
        if (!hasStats) {
            return UsageLogUiState(
                hasUsageStats = false,
                items = emptyList(),
                loading = false,
                isToday = isToday
            )
        }
        val monitored = appLimitRepository.getEnabledPackageNames().toSet()
        val limitsByPkg = appLimitRepository.getAllLimitsOnce()
            .associateBy { it.packageName }
        val plans = planBlockRepository.getAllOnce()
        val segments = systemUsageRepository
            .getDayForegroundTimeline(dayStart, dayEnd)
            .map {
                UsageTransitionBuilder.SystemSegment(
                    packageName = it.packageName,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    ongoing = it.ongoing
                )
            }
        val transitions = UsageTransitionBuilder.build(
            segments = segments,
            records = records,
            monitoredPackages = monitored,
            launcherPackages = launcherPackages,
            ownPackageName = context.packageName,
            resolveAppName = ::resolveAppName,
            dayEndMs = if (isToday) System.currentTimeMillis() else dayEnd,
            intentGatePackages = limitsByPkg.values
                .filter { it.requireIntentOnOpen }
                .map { it.packageName }
                .toSet(),
            resolvePeriodLabel = { pkg, atMs ->
                resolvePeriodWindowLabel(
                    packageName = pkg,
                    atMs = atMs,
                    limitsByPkg = limitsByPkg,
                    plans = plans
                )
            }
        )
        val endHourExclusive = if (isToday) {
            Calendar.getInstance().get(Calendar.HOUR_OF_DAY) + 1
        } else {
            24
        }
        return UsageLogUiState(
            hasUsageStats = true,
            items = UsageTransitionBuilder.toListItems(
                transitions = transitions,
                endHourExclusive = endHourExclusive
            ),
            loading = false,
            isToday = isToday
        )
    }

    private fun resolveAppName(packageName: String): String {
        appNameCache[packageName]?.let { return it }
        val raw = try {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
        } catch (_: PackageManager.NameNotFoundException) {
            packageName.substringAfterLast('.')
        }
        val label = UsageLogDisplayNames.displayName(packageName, raw)
        appNameCache[packageName] = label
        return label
    }

    private fun resolvePeriodWindowLabel(
        packageName: String,
        atMs: Long,
        limitsByPkg: Map<String, AppLimitEntity>,
        plans: List<PlanBlock>
    ): String? {
        val plan = PlanBlockPolicy.activeForPackage(plans, packageName, atMs)
        if (plan != null) return plan.label()
        val limit = limitsByPkg[packageName] ?: return null
        if (!limit.periodLockEnabled) return null
        val windows = PeriodWindowsCodec.decode(limit.periodWindowsJson)
        return PeriodLockPolicy.activeWindow(windows, atMs)?.label()
    }

    companion object {
        const val DAY_COUNT = 90
        const val DAY_MS = 24L * 60L * 60L * 1000L

        fun formatClock(timeMs: Long): String {
            val cal = Calendar.getInstance().apply { timeInMillis = timeMs }
            return String.format(
                "%02d:%02d",
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE)
            )
        }
    }
}
