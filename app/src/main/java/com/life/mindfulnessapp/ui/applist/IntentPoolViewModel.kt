package com.life.mindfulnessapp.ui.applist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.data.repository.IntentPoolRepository
import com.life.mindfulnessapp.domain.model.IntentCategory
import com.life.mindfulnessapp.domain.model.IntentPoolItemDetail
import com.life.mindfulnessapp.domain.model.IntentPoolSnapshot
import com.life.mindfulnessapp.domain.model.IntentPoolSort
import com.life.mindfulnessapp.domain.model.IntentPoolTimeScope
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class IntentPoolViewModel @Inject constructor(
    private val intentPoolRepository: IntentPoolRepository,
    private val analyticsRepository: AnalyticsRepository,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    private val _packageName = MutableStateFlow<String?>(null)
    private val _snapshot = MutableStateFlow<IntentPoolSnapshot?>(null)
    val snapshot: StateFlow<IntentPoolSnapshot?> = _snapshot.asStateFlow()

    private val _detail = MutableStateFlow<IntentPoolItemDetail?>(null)
    val detail: StateFlow<IntentPoolItemDetail?> = _detail.asStateFlow()

    private val _appName = MutableStateFlow("")
    val appName: StateFlow<String> = _appName.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _timeScope = MutableStateFlow(IntentPoolTimeScope.All)
    val timeScope: StateFlow<IntentPoolTimeScope> = _timeScope.asStateFlow()

    private val _sort = MutableStateFlow(IntentPoolSort.Duration)
    val sort: StateFlow<IntentPoolSort> = _sort.asStateFlow()

    private val _categoryFilterId = MutableStateFlow<String?>(null)
    val categoryFilterId: StateFlow<String?> = _categoryFilterId.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun load(packageName: String, trackView: Boolean = true) {
        val isNewPackage = _packageName.value != packageName
        _packageName.value = packageName
        viewModelScope.launch {
            if (isNewPackage) {
                _appName.value = getInstalledAppsUseCase.getApp(packageName)?.appName.orEmpty()
            }
            refresh()
            if (trackView && isNewPackage) {
                analyticsRepository.track(
                    HaEvents.INTENT_POOL_TAB_VIEW,
                    mapOf(HaEvents.Prop.PKG to packageName)
                )
            }
        }
    }

    fun refresh() {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isLoading.value = _snapshot.value == null
            _snapshot.value = intentPoolRepository.loadSnapshot(
                packageName = pkg,
                timeScope = _timeScope.value,
                sort = _sort.value,
                categoryFilterId = _categoryFilterId.value
            )
            _isLoading.value = false
        }
    }

    fun loadDetail(entryId: String) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            _isLoading.value = _detail.value == null
            _detail.value = intentPoolRepository.loadItemDetail(pkg, entryId)
            _isLoading.value = false
            analyticsRepository.track(
                HaEvents.INTENT_ENTRY_DETAIL_VIEW,
                mapOf(
                    HaEvents.Prop.PKG to pkg,
                    HaEvents.Prop.ENTRY to entryId
                )
            )
        }
    }

    fun setTimeScope(scope: IntentPoolTimeScope) {
        if (_timeScope.value == scope) return
        _timeScope.value = scope
        refresh()
    }

    fun setSort(sort: IntentPoolSort) {
        if (_sort.value == sort) return
        _sort.value = sort
        refresh()
    }

    fun setCategoryFilter(categoryId: String?) {
        if (_categoryFilterId.value == categoryId) return
        _categoryFilterId.value = categoryId
        refresh()
    }

    fun assignCategory(entryId: String, categoryId: String?) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            intentPoolRepository.assignCategory(pkg, entryId, categoryId)
            refreshDetail(entryId)
            refresh()
            analyticsRepository.track(
                HaEvents.INTENT_CATEGORY_ASSIGN,
                mapOf(HaEvents.Prop.PKG to pkg)
            )
        }
    }

    fun hideEntry(entryId: String) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            intentPoolRepository.hideEntry(pkg, entryId)
            refresh()
            _actionMessage.value = "已隐藏该意图"
            analyticsRepository.track(HaEvents.INTENT_HIDE, mapOf(HaEvents.Prop.PKG to pkg))
        }
    }

    fun unhideEntry(entryId: String) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            intentPoolRepository.unhideEntry(pkg, entryId)
            refresh()
            _actionMessage.value = "已恢复显示"
            analyticsRepository.track(HaEvents.INTENT_UNHIDE, mapOf(HaEvents.Prop.PKG to pkg))
        }
    }

    fun mergeEntries(sourceEntryId: String, targetEntryId: String, newDisplayName: String?) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            val ok = intentPoolRepository.mergeEntries(
                packageName = pkg,
                sourceEntryId = sourceEntryId,
                targetEntryId = targetEntryId,
                newDisplayName = newDisplayName
            )
            if (ok) {
                refresh()
                _actionMessage.value = "已合并意图"
                analyticsRepository.track(HaEvents.INTENT_MERGE, mapOf(HaEvents.Prop.PKG to pkg))
            }
        }
    }

    fun createCategory(name: String, emoji: String? = null) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            intentPoolRepository.createCategory(pkg, name, emoji)
            refresh()
            analyticsRepository.track(HaEvents.INTENT_CATEGORY_CREATE, mapOf(HaEvents.Prop.PKG to pkg))
        }
    }

    fun deleteCategory(categoryId: String) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            intentPoolRepository.deleteCategory(pkg, categoryId)
            if (_categoryFilterId.value == categoryId) {
                _categoryFilterId.value = null
            }
            refresh()
            analyticsRepository.track(HaEvents.INTENT_CATEGORY_DELETE, mapOf(HaEvents.Prop.PKG to pkg))
        }
    }

    fun applyCategoryTemplate() {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            intentPoolRepository.applyCategoryTemplate(pkg)
            refresh()
        }
    }

    fun renameEntry(entryId: String, displayName: String) {
        val pkg = _packageName.value ?: return
        viewModelScope.launch {
            intentPoolRepository.renameEntry(pkg, entryId, displayName)
            refreshDetail(entryId)
            refresh()
        }
    }

    fun consumeActionMessage() {
        _actionMessage.value = null
    }

    fun clearDetail() {
        _detail.value = null
    }

    private suspend fun refreshDetail(entryId: String) {
        val pkg = _packageName.value ?: return
        _detail.value = intentPoolRepository.loadItemDetail(pkg, entryId)
    }
}
