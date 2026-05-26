package com.life.mindfulnessapp.ui.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.domain.usecase.CheckPermissionsUseCase
import com.life.mindfulnessapp.domain.usecase.PermissionStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val checkPermissionsUseCase: CheckPermissionsUseCase
) : ViewModel() {

    private val _permissionStatus = MutableStateFlow(PermissionStatus(false, false, false))
    val permissionStatus: StateFlow<PermissionStatus> = _permissionStatus

    fun refreshPermissions() {
        _permissionStatus.value = checkPermissionsUseCase()
    }
}
