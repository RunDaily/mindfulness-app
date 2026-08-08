package com.life.mindfulnessapp.ui.settings

import android.graphics.drawable.Drawable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.usecase.GetInstalledAppsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PositiveDestinationUi(
    val packageName: String,
    val appName: String,
    val alias: String?,
    val icon: Drawable?,
    val isDefault: Boolean
) {
    val displayLabel: String
        get() = alias?.takeIf { it.isNotBlank() } ?: appName
}

@HiltViewModel
class PositiveDestinationsViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
    private val getInstalledAppsUseCase: GetInstalledAppsUseCase
) : ViewModel() {

    val isDarkTheme: StateFlow<Boolean> = appPreferences.isDarkTheme
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private val _allApps = MutableStateFlow<List<AppInfo>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)
    private val _picking = MutableStateFlow(false)

    val searchQuery: StateFlow<String> = _searchQuery
    val isLoading: StateFlow<Boolean> = _isLoading
    val isPicking: StateFlow<Boolean> = _picking

    val selectedPackages: StateFlow<Set<String>> = appPreferences.positiveDestinations
        .map { list -> list.map { it.packageName }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val destinations: StateFlow<List<PositiveDestinationUi>> = combine(
        appPreferences.positiveDestinations,
        appPreferences.preferredPositiveDestination,
        _allApps
    ) { dests, preferred, apps ->
        val appMap = apps.associateBy { it.packageName }
        val preferredPkg = preferred ?: dests.firstOrNull()?.packageName
        dests.map { dest ->
            val app = appMap[dest.packageName]
            PositiveDestinationUi(
                packageName = dest.packageName,
                appName = app?.appName
                    ?: dest.packageName.substringAfterLast('.').ifBlank { dest.packageName },
                alias = dest.alias,
                icon = app?.icon,
                isDefault = dest.packageName == preferredPkg
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pickerApps: StateFlow<List<AppInfo>> = combine(
        _allApps,
        _searchQuery,
        selectedPackages
    ) { apps, query, selected ->
        val q = query.trim()
        val filtered = if (q.isEmpty()) apps
        else apps.filter {
            it.appName.contains(q, ignoreCase = true) ||
                it.packageName.contains(q, ignoreCase = true)
        }
        filtered.sortedWith(
            compareByDescending<AppInfo> { it.packageName in selected }
                .thenBy { it.appName }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _isLoading.value = true
            _allApps.value = try {
                getInstalledAppsUseCase()
            } catch (_: Exception) {
                emptyList()
            }
            _isLoading.value = false
        }
    }

    fun openPicker() {
        _searchQuery.value = ""
        _picking.value = true
    }

    fun closePicker() {
        _picking.value = false
        _searchQuery.value = ""
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggle(packageName: String) {
        appPreferences.togglePositiveDestination(packageName)
    }

    fun remove(packageName: String) {
        val list = appPreferences.getPositiveDestinations()
            .filter { it.packageName != packageName }
        appPreferences.setPositiveDestinations(list)
    }

    fun setAsDefault(packageName: String) {
        appPreferences.setPreferredPositiveDestination(packageName)
        val list = appPreferences.getPositiveDestinations().toMutableList()
        val idx = list.indexOfFirst { it.packageName == packageName }
        if (idx > 0) {
            val item = list.removeAt(idx)
            list.add(0, item)
            appPreferences.setPositiveDestinations(list)
        }
    }

    fun setAlias(packageName: String, alias: String?) {
        appPreferences.setPositiveDestinationAlias(packageName, alias)
    }
}
