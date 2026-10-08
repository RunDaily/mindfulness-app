package com.life.mindfulnessapp.ui.features

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppWeeklySystemUsage
import com.life.mindfulnessapp.domain.model.BatchPickSortMode
import com.life.mindfulnessapp.domain.model.ParetoRankRow
import com.life.mindfulnessapp.domain.model.ParetoUsageSnapshot
import com.life.mindfulnessapp.domain.model.computeParetoUsage
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.util.AppNameSearch
import com.life.mindfulnessapp.util.BatchPickSorting
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ExploreViewModel @Inject constructor(
    private val systemUsageRepository: SystemUsageRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase,
    private val checkPermissionsUseCase: CheckPermissionsUseCase
) : ViewModel() {

    private val _permissionStatus = MutableStateFlow(checkPermissionsUseCase())
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus.asStateFlow()

    private val _usageLoading = MutableStateFlow(false)
    val usageLoading: StateFlow<Boolean> = _usageLoading.asStateFlow()

    private val _hasLoadedOnce = MutableStateFlow(false)
    val hasLoadedOnce: StateFlow<Boolean> = _hasLoadedOnce.asStateFlow()

    /** 与多选网格页相同：全量 Launcher App */
    private val _allApps = MutableStateFlow(emptyList<AppInfo>())

    /** 探索排行：近 7 完整自然日，会话口径（与详情页一致） */
    private val _usageByPackage =
        MutableStateFlow<Map<String, AppWeeklySystemUsage>>(emptyMap())

    private val _sortMode = MutableStateFlow(BatchPickSortMode.Duration)
    val sortMode: StateFlow<BatchPickSortMode> = _sortMode.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val rankedPairs: StateFlow<List<Pair<AppInfo, AppWeeklySystemUsage>>> = combine(
        _allApps,
        _usageByPackage,
        _sortMode
    ) { apps, usage, sort ->
        BatchPickSorting.sort(
            BatchPickSorting.toRows(apps, usage),
            sort
        ).mapNotNull { row ->
            val u = row.usage ?: return@mapNotNull null
            if (u.totalSeconds <= 0L && u.totalLaunches <= 0) return@mapNotNull null
            row.app to u
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** 无搜索时的二八快照 */
    val pareto: StateFlow<ParetoUsageSnapshot> = combine(
        rankedPairs,
        _sortMode
    ) { ranked, sort ->
        computeParetoUsage(ranked, sort)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ParetoUsageSnapshot.Empty)

    /**
     * 搜索命中时扁平列表（不切二八）；无搜索时为 null。
     */
    val searchRows: StateFlow<List<ParetoRankRow>?> = combine(
        rankedPairs,
        _sortMode,
        _searchQuery
    ) { ranked, sort, query ->
        if (query.isBlank()) return@combine null
        val filtered = ranked.filter { (app, _) ->
            AppNameSearch.matches(app.appName, app.packageName, query)
        }
        if (filtered.isEmpty()) return@combine emptyList()
        // 搜索结果仍带占比，相对「全量」总量，便于对照
        val snapshot = computeParetoUsage(ranked, sort)
        val byPkg = (snapshot.core + snapshot.tail).associateBy { it.app.packageName }
        filtered.mapNotNull { (app, _) -> byPkg[app.packageName] }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val maxDurationSeconds: StateFlow<Long> = rankedPairs
        .combine(_sortMode) { list, _ -> list.maxOfOrNull { it.second.totalSeconds } ?: 1L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 1L)

    fun setSortMode(mode: BatchPickSortMode) {
        _sortMode.value = mode
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun refresh() {
        viewModelScope.launch {
            val status = checkPermissionsUseCase()
            _permissionStatus.value = status
            if (_allApps.value.isEmpty()) {
                _allApps.value = getInstalledAppsUseCase()
            }
            if (!status.hasUsageStats) {
                _usageByPackage.value = emptyMap()
                _hasLoadedOnce.value = true
                return@launch
            }
            _usageLoading.value = true
            try {
                _usageByPackage.value = systemUsageRepository.getRecentDaysUsageByPackage(
                    includeToday = false
                )
            } finally {
                _usageLoading.value = false
                _hasLoadedOnce.value = true
            }
        }
    }
}
