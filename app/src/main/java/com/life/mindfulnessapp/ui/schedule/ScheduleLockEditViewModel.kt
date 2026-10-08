package com.life.mindfulnessapp.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.data.repository.PlanBlockRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.PeriodDays
import com.life.mindfulnessapp.domain.model.PeriodWindowConflict
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.domain.model.PlanLockScope
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.util.AppNameSearch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class ScheduleLockEditUiState(
    val ready: Boolean = false,
    val pickingApps: Boolean = false,
    val editor: ScheduleLockEditorState = ScheduleLockEditorState(),
    /** 加载时的原计划；新建为 null。用于判定生效中削弱是否需呼吸。 */
    val original: PlanBlock? = null,
    val monitoredApps: List<AppInfo> = emptyList(),
    /** 全量可安装列表（含未监控），供「另选 App」勾选 */
    val allApps: List<AppInfo> = emptyList(),
    val appLabels: Map<String, String> = emptyMap(),
    val toast: String? = null,
    val finished: Boolean = false,
    /** 非 null 时 UI 弹出呼吸门槛；确认后执行对应动作 */
    val pendingBreath: ScheduleLockBreathAction? = null,
    /** 关态删除：无呼吸门槛时走轻确认 */
    val pendingSoftDelete: Boolean = false
)

enum class ScheduleLockBreathAction {
    Save,
    Delete
}

@HiltViewModel
class ScheduleLockEditViewModel @Inject constructor(
    private val planBlockRepository: PlanBlockRepository,
    private val appLimitRepository: AppLimitRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val _state = MutableStateFlow(ScheduleLockEditUiState())
    val state: StateFlow<ScheduleLockEditUiState> = _state.asStateFlow()

    fun load(planId: String?) {
        viewModelScope.launch {
            val catalog = loadCatalog()
            val existing = planId
                ?.takeIf { it.isNotBlank() && it != EXTRA_NEW }
                ?.let { planBlockRepository.getById(it) }
            val monitoredPkgs = catalog.monitored.map { it.packageName }.toSet()
            val editor = if (existing != null) {
                val extras = sanitizeExtras(
                    packages = existing.packageNames,
                    includeMonitored = existing.lockScope.includesMonitored,
                    monitoredPackages = monitoredPkgs
                )
                ScheduleLockEditorState(
                    itemId = existing.id,
                    title = existing.title,
                    startMinute = existing.startMinute,
                    endMinute = existing.endMinute,
                    daysMask = existing.daysMask,
                    includeMonitored = existing.lockScope.includesMonitored,
                    packageNames = extras,
                    enabled = existing.enabled,
                    isNew = false
                )
            } else {
                ScheduleLockEditorState.forNew()
            }
            _state.value = ScheduleLockEditUiState(
                ready = true,
                editor = editor,
                original = existing,
                monitoredApps = catalog.monitored,
                allApps = catalog.all,
                appLabels = catalog.labels
            )
        }
    }

    fun updateTitle(title: String) {
        _state.update {
            it.copy(editor = it.editor.copy(title = title.take(PlanBlock.TITLE_MAX_CHARS)))
        }
    }

    fun updateRange(start: Int, end: Int) {
        _state.update { it.copy(editor = it.editor.copy(startMinute = start, endMinute = end)) }
    }

    fun updateDaysMask(mask: Int) {
        _state.update {
            it.copy(editor = it.editor.copy(daysMask = mask and PeriodDays.EVERY_DAY))
        }
    }

    fun setIncludeMonitored(on: Boolean) {
        _state.update { st ->
            val monitoredPkgs = st.monitoredApps.map { it.packageName }.toSet()
            val extras = sanitizeExtras(
                packages = st.editor.packageNames,
                includeMonitored = on,
                monitoredPackages = monitoredPkgs
            )
            st.copy(editor = st.editor.copy(includeMonitored = on, packageNames = extras))
        }
        // 关掉跟随后若还没有另选，直接去选，避免空范围
        if (!on && _state.value.editor.packageNames.isEmpty()) {
            openAppPicker()
        }
    }

    fun openAppPicker() {
        viewModelScope.launch {
            val catalog = loadCatalog()
            val monitoredPkgs = catalog.monitored.map { it.packageName }.toSet()
            _state.update { st ->
                val extras = sanitizeExtras(
                    packages = st.editor.packageNames,
                    includeMonitored = st.editor.includeMonitored,
                    monitoredPackages = monitoredPkgs
                )
                st.copy(
                    pickingApps = true,
                    monitoredApps = catalog.monitored,
                    allApps = catalog.all,
                    appLabels = catalog.labels,
                    editor = st.editor.copy(packageNames = extras)
                )
            }
        }
    }

    fun closeAppPicker() {
        _state.update { it.copy(pickingApps = false) }
    }

    fun togglePackage(packageName: String) {
        _state.update { st ->
            val monitoredPkgs = st.monitoredApps.map { it.packageName }.toSet()
            // 跟随开时，监控中的已由主开关覆盖，不能当另选项勾选
            if (st.editor.includeMonitored && packageName in monitoredPkgs) return@update st
            val set = st.editor.packageNames.toMutableSet()
            if (!set.add(packageName)) set.remove(packageName)
            st.copy(editor = st.editor.copy(packageNames = set.toList()))
        }
    }

    fun requestSave() {
        val st = _state.value
        val draft = buildDraft(st.editor) ?: return
        val original = st.original
        if (original != null && PlanBlockPolicy.requiresBreathToCommit(original, draft)) {
            _state.update { it.copy(pendingBreath = ScheduleLockBreathAction.Save) }
        } else {
            commitSave(draft)
        }
    }

    fun requestDelete() {
        val st = _state.value
        if (st.editor.itemId == null) return
        val original = st.original
        if (original != null && PlanBlockPolicy.requiresBreathToCommit(original, updated = null)) {
            _state.update { it.copy(pendingBreath = ScheduleLockBreathAction.Delete) }
        } else {
            _state.update { it.copy(pendingSoftDelete = true) }
        }
    }

    fun confirmSoftDelete() {
        val id = _state.value.editor.itemId ?: return
        _state.update { it.copy(pendingSoftDelete = false) }
        commitDelete(id)
    }

    fun dismissSoftDelete() {
        _state.update { it.copy(pendingSoftDelete = false) }
    }

    fun confirmBreath() {
        val st = _state.value
        val action = st.pendingBreath ?: return
        _state.update { it.copy(pendingBreath = null) }
        when (action) {
            ScheduleLockBreathAction.Save -> {
                val draft = buildDraft(st.editor) ?: return
                commitSave(draft)
            }
            ScheduleLockBreathAction.Delete -> {
                val id = st.editor.itemId ?: return
                commitDelete(id)
            }
        }
    }

    fun dismissBreath() {
        _state.update { it.copy(pendingBreath = null) }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    private fun buildDraft(ed: ScheduleLockEditorState): PlanBlock? {
        val monitoredPkgs = _state.value.monitoredApps.map { it.packageName }.toSet()
        val extras = sanitizeExtras(
            packages = ed.packageNames,
            includeMonitored = ed.includeMonitored,
            monitoredPackages = monitoredPkgs
        )
        if (!ed.includeMonitored && extras.isEmpty()) {
            _state.update { it.copy(toast = "选要锁的 App") }
            return null
        }
        val scope = PlanLockScope.fromFlags(ed.includeMonitored, extras) ?: run {
            _state.update { it.copy(toast = "选锁定范围") }
            return null
        }
        return PlanBlock(
            id = ed.itemId ?: UUID.randomUUID().toString(),
            title = ed.title,
            startMinute = ed.startMinute,
            endMinute = ed.endMinute,
            daysMask = ed.daysMask,
            enabled = ed.enabled,
            packageNames = extras,
            lockScope = scope
        )
    }

    /** 跟随开时去掉与监控名单重复的另选，避免双重计入。 */
    private fun sanitizeExtras(
        packages: List<String>,
        includeMonitored: Boolean,
        monitoredPackages: Set<String>
    ): List<String> {
        if (!includeMonitored || monitoredPackages.isEmpty()) {
            return packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        }
        return packages
            .map { it.trim() }
            .filter { it.isNotEmpty() && it !in monitoredPackages }
            .distinct()
    }

    private fun commitSave(block: PlanBlock) {
        viewModelScope.launch {
            when (val result = planBlockRepository.save(block)) {
                PlanBlockRepository.SaveResult.Ok ->
                    _state.update { it.copy(finished = true) }
                PlanBlockRepository.SaveResult.EmptyTitle ->
                    _state.update { it.copy(toast = "写一下名称") }
                PlanBlockRepository.SaveResult.EmptyPackages ->
                    _state.update { it.copy(toast = "选要锁的 App") }
                is PlanBlockRepository.SaveResult.Conflict ->
                    _state.update {
                        it.copy(
                            toast = when (result.type) {
                                PeriodWindowConflict.Overlap -> "与其他时段重叠"
                                PeriodWindowConflict.Duplicate -> "与其他时段重复"
                            }
                        )
                    }
            }
        }
    }

    private fun commitDelete(id: String) {
        viewModelScope.launch {
            planBlockRepository.delete(id)
            _state.update { it.copy(finished = true) }
        }
    }

    private data class Catalog(
        val monitored: List<AppInfo>,
        val all: List<AppInfo>,
        val labels: Map<String, String>
    )

    private suspend fun loadCatalog(): Catalog {
        val enabled = appLimitRepository.getEnabledPackageNames().toSet()
        val installed = getInstalledAppsUseCase()
            .filter { !it.isUninstalled }
            .sortedBy { AppNameSearch.sortKey(it.appName) }
        val monitored = installed.filter { it.packageName in enabled }
        return Catalog(
            monitored = monitored,
            all = installed,
            labels = installed.associate { it.packageName to it.appName }
        )
    }

    companion object {
        const val EXTRA_NEW = "new"
    }
}
