package com.life.mindfulnessapp.ui.applist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.CommonIntentItem
import com.life.mindfulnessapp.domain.model.CommonIntentsCodec
import com.life.mindfulnessapp.domain.model.IntentGateProfiles
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class QuickIntentTagsUiState(
    val packageName: String = "",
    val appName: String = "",
    val items: List<CommonIntentItem> = emptyList(),
    val presetPool: List<String> = emptyList(),
    val loading: Boolean = true,
    val showingAdd: Boolean = false,
    val toast: String? = null,
) {
    val onGateCount: Int get() = CommonIntentsCodec.countOnGate(items)
    val canPinMore: Boolean get() = CommonIntentsCodec.canPinMore(items)
    val canAddMore: Boolean get() = items.size < CommonIntentsCodec.MAX_ITEMS
}

@HiltViewModel
class QuickIntentTagsViewModel @Inject constructor(
    private val appLimitRepository: AppLimitRepository
) : ViewModel() {

    private val _state = MutableStateFlow(QuickIntentTagsUiState())
    val state: StateFlow<QuickIntentTagsUiState> = _state.asStateFlow()

    fun load(packageName: String, appName: String) {
        viewModelScope.launch {
            val entity = appLimitRepository.getAppLimit(packageName)
            val resolvedName = appName.ifBlank { entity?.appName.orEmpty() }
            _state.update {
                it.copy(
                    packageName = packageName,
                    appName = resolvedName,
                    loading = true,
                    toast = null
                )
            }
            refresh(packageName)
        }
    }

    private suspend fun refresh(packageName: String) {
        val items = appLimitRepository.getCommonIntents(packageName)
        val owned = items.map { it.label.trim().lowercase() }.toSet()
        val pool = IntentGateProfiles.quickIntentTags(packageName)
            .map { it.label.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in owned }
        _state.update {
            it.copy(
                items = items,
                presetPool = pool,
                loading = false
            )
        }
    }

    fun openAdd() {
        _state.update { it.copy(showingAdd = true, toast = null) }
    }

    fun closeAdd() {
        _state.update { it.copy(showingAdd = false, toast = null) }
    }

    fun consumeToast() {
        _state.update { it.copy(toast = null) }
    }

    fun reorder(from: Int, to: Int) {
        val cur = _state.value.items.toMutableList()
        if (from !in cur.indices || to !in cur.indices || from == to) return
        val item = cur.removeAt(from)
        cur.add(to, item)
        persist(cur)
    }

    /** 拖完后一次性写入当前顺序。 */
    fun commitOrder(ordered: List<CommonIntentItem>) {
        if (ordered.isEmpty() && _state.value.items.isEmpty()) return
        persist(ordered)
    }

    fun setOnGate(index: Int, on: Boolean) {
        val cur = _state.value.items.toMutableList()
        if (index !in cur.indices) return
        if (on && !CommonIntentsCodec.canPinMore(cur) && !cur[index].showOnGate) {
            _state.update {
                it.copy(toast = "门上最多 ${CommonIntentsCodec.MAX_GATE_VISIBLE} 个")
            }
            return
        }
        cur[index] = cur[index].copy(showOnGate = on)
        persist(cur)
    }

    fun removeAt(index: Int) {
        val cur = _state.value.items.toMutableList()
        if (index !in cur.indices) return
        cur.removeAt(index)
        persist(cur)
    }

    fun addCustom(raw: String) {
        val label = CommonIntentsCodec.normalizeLabel(raw)
        if (label.isEmpty()) {
            _state.update { it.copy(toast = "写一下标签") }
            return
        }
        if (BrowseCasualIntent.isBrowseLike(label)) {
            _state.update { it.copy(toast = "刷类意图请走「${BrowseCasualIntent.DISPLAY_LABEL}」") }
            return
        }
        val cur = _state.value.items
        if (cur.any { it.label.equals(label, ignoreCase = true) }) {
            _state.update { it.copy(toast = "已有这个标签") }
            return
        }
        if (cur.size >= CommonIntentsCodec.MAX_ITEMS) {
            _state.update {
                it.copy(toast = "最多 ${CommonIntentsCodec.MAX_ITEMS} 个")
            }
            return
        }
        val pin = CommonIntentsCodec.canPinMore(cur)
        val pkg = _state.value.packageName
        persist(
            cur + AppDeepLinkCatalog.bindDeepLinkOnCreate(pkg, label, pin),
            closeAdd = true,
            toastIfNeeded = if (!pin) "已加入，门上已满可稍后打开" else null
        )
    }

    fun addPreset(label: String) {
        addCustom(label)
    }

    fun restorePresets() {
        viewModelScope.launch {
            val pkg = _state.value.packageName
            if (pkg.isBlank()) return@launch
            appLimitRepository.resetCommonIntentsToPresets(pkg)
            refresh(pkg)
            _state.update {
                it.copy(showingAdd = false, toast = "已恢复预设")
            }
        }
    }

    private fun persist(
        next: List<CommonIntentItem>,
        closeAdd: Boolean = false,
        toastIfNeeded: String? = null
    ) {
        viewModelScope.launch {
            val pkg = _state.value.packageName
            if (pkg.isBlank()) return@launch
            appLimitRepository.setCommonIntents(pkg, next)
            refresh(pkg)
            _state.update {
                it.copy(
                    showingAdd = if (closeAdd) false else it.showingAdd,
                    toast = toastIfNeeded
                )
            }
        }
    }
}
