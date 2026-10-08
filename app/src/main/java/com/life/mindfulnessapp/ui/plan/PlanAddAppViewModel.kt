package com.life.mindfulnessapp.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.RulePlanRepository
import com.life.mindfulnessapp.data.repository.SystemUsageRepository
import com.life.mindfulnessapp.domain.model.MonitorSuitability
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.util.AppNameSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanAddAppRow(
    val packageName: String,
    val listKey: String,
    val appName: String,
    val usageLabel: String,
    val alreadyInPlan: Boolean,
    val selected: Boolean,
    val unsuitable: Boolean,
    val rankScore: Long
)

data class PlanAddAppUiState(
    val query: String = "",
    val rows: List<PlanAddAppRow> = emptyList(),
    val selectedCount: Int = 0,
    val loading: Boolean = true,
    val saving: Boolean = false,
    val configuring: Boolean = false,
    val queue: List<InstrumentDraft> = emptyList(),
    val configIndex: Int = 0,
    val unsuitableMessage: String? = null
)

@HiltViewModel
class PlanAddAppViewModel @Inject constructor(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val rulePlan: RulePlanRepository,
    private val systemUsage: SystemUsageRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlanAddAppUiState())
    val state: StateFlow<PlanAddAppUiState> = _state.asStateFlow()

    private var all = emptyList<PlanAddAppRow>()
    private val selected = linkedSetOf<String>()

    fun load(preselectPackage: String? = null) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            val apps = getInstalledApps()
            val week = runCatching { systemUsage.getLast7CompleteDaysUsageByPackage() }
                .getOrDefault(emptyMap())
            val inPlan = rulePlan.activePacks().map { it.packageName }.toSet()
            all = apps
                .filter { !it.isUninstalled }
                .map { app ->
                    val usage = week[app.packageName]
                    val minutes = ((usage?.avgDailySeconds ?: 0L) / 60L).toInt()
                    val opens = usage?.avgDailyLaunches ?: 0
                    val label = when {
                        minutes > 0 -> "日均 ${minutes} 分 · ${opens} 次"
                        opens > 0 -> "日均 ${opens} 次"
                        else -> "近 7 日很少"
                    }
                    PlanAddAppRow(
                        packageName = app.packageName,
                        listKey = app.listKey,
                        appName = app.appName,
                        usageLabel = label,
                        alreadyInPlan = app.packageName in inPlan || app.isMonitored,
                        selected = false,
                        unsuitable = MonitorSuitability.isUnsuitable(app.packageName),
                        rankScore = (usage?.avgDailySeconds ?: 0L) +
                            (usage?.avgDailyLaunches ?: 0) * 45L
                    )
                }
                .sortedWith(
                    compareByDescending<PlanAddAppRow> { it.rankScore }
                        .thenBy { it.appName }
                )
            val hint = preselectPackage?.trim().orEmpty()
            if (hint.isNotEmpty()) {
                val row = all.find { it.packageName == hint }
                if (row != null &&
                    !row.alreadyInPlan &&
                    !row.unsuitable &&
                    hint !in selected
                ) {
                    selected.add(hint)
                }
            }
            publish()
        }
    }

    fun setQuery(query: String) {
        _state.value = _state.value.copy(query = query)
        publish()
    }

    fun toggle(packageName: String) {
        MonitorSuitability.unsuitableReminder(packageName)?.let { msg ->
            selected.remove(packageName)
            _state.value = _state.value.copy(unsuitableMessage = msg)
            publish()
            return
        }
        if (packageName in selected) selected.remove(packageName) else selected.add(packageName)
        publish()
    }

    fun dismissUnsuitable() {
        _state.value = _state.value.copy(unsuitableMessage = null)
    }

    fun beginConfig() {
        selected.removeAll { MonitorSuitability.isUnsuitable(it) }
        if (selected.isEmpty()) {
            publish()
            return
        }
        val names = all.associate { it.packageName to it.appName }
        val queue = selected.map { pkg -> InstrumentDraft.fresh(pkg, names[pkg] ?: pkg) }
        _state.value = _state.value.copy(configuring = true, queue = queue, configIndex = 0)
    }

    fun updateDraft(draft: InstrumentDraft) {
        val index = _state.value.configIndex
        val queue = _state.value.queue.toMutableList()
        if (index !in queue.indices) return
        queue[index] = draft
        _state.value = _state.value.copy(queue = queue)
    }

    fun saveCurrent(onDone: () -> Unit) {
        val snapshot = _state.value
        val draft = snapshot.queue.getOrNull(snapshot.configIndex) ?: return
        if (!draft.anyOn || snapshot.saving) return
        viewModelScope.launch {
            _state.value = _state.value.copy(saving = true)
            rulePlan.updatePack(draft.toPack())
            if (snapshot.configIndex >= snapshot.queue.lastIndex) {
                _state.value = _state.value.copy(saving = false)
                onDone()
            } else {
                _state.value = _state.value.copy(
                    saving = false,
                    configIndex = snapshot.configIndex + 1
                )
            }
        }
    }

    fun backConfig() {
        val index = _state.value.configIndex
        if (index <= 0) {
            _state.value = _state.value.copy(configuring = false)
            return
        }
        _state.value = _state.value.copy(configIndex = index - 1)
    }

    private fun publish() {
        val q = _state.value.query
        val rows = all
            .filter { AppNameSearch.matches(it.appName, it.packageName, q) }
            .map { it.copy(selected = it.packageName in selected) }
        _state.value = _state.value.copy(
            rows = rows,
            selectedCount = selected.size,
            loading = false
        )
    }
}
