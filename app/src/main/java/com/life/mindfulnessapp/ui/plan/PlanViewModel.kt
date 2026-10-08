package com.life.mindfulnessapp.ui.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.repository.RulePlanRepository
import com.life.mindfulnessapp.domain.model.RulePack
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PlanUiState(
    val packs: List<RulePack> = emptyList(),
    val usage: List<com.life.mindfulnessapp.domain.model.UsageDigestRow> = emptyList(),
    val loading: Boolean = false,
    val message: String? = null
)

@HiltViewModel
class PlanViewModel @Inject constructor(
    private val repository: RulePlanRepository
) : ViewModel() {

    private val _state = MutableStateFlow(PlanUiState())
    val state: StateFlow<PlanUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true)
            _state.value = _state.value.copy(
                packs = repository.activePacks(),
                usage = repository.usageForAdvice(),
                loading = false
            )
        }
    }

    fun consumeMessage() {
        _state.value = _state.value.copy(message = null)
    }
}
