package com.life.mindfulnessapp.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.PlanBlockRepository
import com.life.mindfulnessapp.domain.model.PeriodWindowConflict
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.domain.model.PlanBlockPolicy
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import com.life.mindfulnessapp.domain.model.AppInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlanBlockListViewModel @Inject constructor(
    private val planBlockRepository: PlanBlockRepository
) : ViewModel() {

    val plans: StateFlow<List<PlanBlock>> = planBlockRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeNow: PlanBlock?
        get() = PlanBlockPolicy.activeNow(plans.value)

    fun setEnabled(id: String, enabled: Boolean) {
        viewModelScope.launch { planBlockRepository.setEnabled(id, enabled) }
    }

    fun delete(id: String) {
        viewModelScope.launch { planBlockRepository.delete(id) }
    }
}

@HiltViewModel
class PlanBlockEditViewModel @Inject constructor(
    private val planBlockRepository: PlanBlockRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val _draft = MutableStateFlow<PlanBlock?>(null)
    val draft: StateFlow<PlanBlock?> = _draft.asStateFlow()

    private val _installedApps = MutableStateFlow<List<AppInfo>>(emptyList())
    val installedApps: StateFlow<List<AppInfo>> = _installedApps.asStateFlow()

    private val _saveError = MutableStateFlow<String?>(null)
    val saveError: StateFlow<String?> = _saveError.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    fun load(planId: String?) {
        viewModelScope.launch {
            _loading.value = true
            _installedApps.value = getInstalledAppsUseCase()
                .filter { !it.isUninstalled }
                .sortedBy { it.appName }
            val existing = planId?.takeIf { it.isNotBlank() && it != "new" }
                ?.let { planBlockRepository.getById(it) }
            _draft.value = existing ?: PlanBlock(
                title = "",
                startMinute = 9 * 60,
                endMinute = 12 * 60,
                lockScope = com.life.mindfulnessapp.domain.model.PlanLockScope.SPECIFIC
            )
            _loading.value = false
        }
    }

    fun applyTemplate(template: PlanBlock) {
        val current = _draft.value ?: return
        _draft.value = current.copy(
            title = template.title,
            startMinute = template.startMinute,
            endMinute = template.endMinute,
            daysMask = template.daysMask,
            scene = template.scene,
            why = template.why
        )
        _saveError.value = null
    }

    fun updateTitle(title: String) {
        val current = _draft.value ?: return
        _draft.value = current.copy(title = title.take(PlanBlock.TITLE_MAX_CHARS))
        _saveError.value = null
    }

    fun updateScene(scene: com.life.mindfulnessapp.domain.model.PlanScene) {
        val current = _draft.value ?: return
        _draft.value = current.copy(scene = scene)
        _saveError.value = null
    }

    fun updateWhy(why: String) {
        val current = _draft.value ?: return
        _draft.value = current.copy(why = why.take(PlanBlock.WHY_MAX_CHARS))
        _saveError.value = null
    }

    fun updateRange(startMinute: Int, endMinute: Int) {
        val current = _draft.value ?: return
        _draft.value = current.copy(startMinute = startMinute, endMinute = endMinute)
        _saveError.value = null
    }

    fun updateDaysMask(mask: Int) {
        val current = _draft.value ?: return
        _draft.value = current.copy(daysMask = mask)
        _saveError.value = null
    }

    fun togglePackage(packageName: String) {
        val current = _draft.value ?: return
        val next = current.packageNames.toMutableList()
        if (packageName in next) next.remove(packageName) else next.add(packageName)
        _draft.value = current.copy(
            packageNames = next,
            lockScope = com.life.mindfulnessapp.domain.model.PlanLockScope.SPECIFIC
        )
        _saveError.value = null
    }

    fun setEnabled(enabled: Boolean) {
        val current = _draft.value ?: return
        _draft.value = current.copy(enabled = enabled)
    }

    fun clearSaveError() {
        _saveError.value = null
    }

    fun save() {
        viewModelScope.launch {
            val block = _draft.value ?: return@launch
            when (val result = planBlockRepository.save(block)) {
                PlanBlockRepository.SaveResult.Ok -> _saved.value = true
                PlanBlockRepository.SaveResult.EmptyTitle ->
                    _saveError.value = "请填写计划名称"
                PlanBlockRepository.SaveResult.EmptyPackages ->
                    _saveError.value = "请至少选择一个要限制的 App"
                is PlanBlockRepository.SaveResult.Conflict ->
                    _saveError.value = when (result.type) {
                        PeriodWindowConflict.Duplicate -> "与已有计划时段完全相同"
                        PeriodWindowConflict.Overlap -> "与已有计划时段重叠，请调整时间"
                    }
            }
        }
    }

    fun deleteAndFinish(onDone: () -> Unit) {
        viewModelScope.launch {
            val id = _draft.value?.id ?: return@launch
            planBlockRepository.delete(id)
            onDone()
        }
    }
}
