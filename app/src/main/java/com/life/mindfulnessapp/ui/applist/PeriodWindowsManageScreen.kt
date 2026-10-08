package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen

/**
 * 时段管理子页：多段、子开关、编辑/删除。
 * 寄语在各时段编辑中填写；已生效时关闭/删除会走解锁门槛。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodWindowsManageScreen(
    windows: List<PeriodWindow>,
    onWindowsChange: (List<PeriodWindow>) -> Unit,
    onBack: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val policy = PeriodLockPolicy
    var pendingDisable by remember { mutableStateOf<PeriodManagePending?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "管理时段",
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            fontSize = 17.sp
                        )
                        Text(
                            "可设多段，互不重叠；每段独立开关与寄语",
                            fontSize = 12.sp,
                            color = cs.onSurface.copy(alpha = 0.42f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = cs.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = ConfigPagePadding)
                .padding(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(ConfigGroupGap)
        ) {
            SettingsSection(
                header = "锁定时段",
                headerIcon = {
                    CapabilityMark(
                        kind = CapabilityKind.PeriodLock,
                        form = CapabilityForm.Standard,
                        size = 15.dp
                    )
                },
                footer = when {
                    windows.isEmpty() -> "添加至少一段后，时段锁才会生效。"
                    windows.none { it.enabled } -> "所有时段都已关闭，当前不会硬挡。"
                    else -> "各时段互不重叠；列表中标「生效中」的段此刻正在锁定。"
                }
            ) {
                PeriodWindowSettings(
                    windows = windows,
                    onWindowsChange = onWindowsChange,
                    lockIsLive = true,
                    onRequestDisableWindow = { id ->
                        pendingDisable = PeriodManagePending.WindowOff(id)
                    },
                    onRequestDeleteWindow = { id ->
                        val w = windows.find { it.id == id } ?: return@PeriodWindowSettings
                        if (w.enabled) {
                            pendingDisable = PeriodManagePending.DeleteWindow(id)
                        } else {
                            onWindowsChange(windows.filter { it.id != id })
                        }
                    }
                )
            }
        }
    }

    pendingDisable?.let { pending ->
        val target = when (pending) {
            is PeriodManagePending.WindowOff ->
                windows.find { it.id == pending.id }
            is PeriodManagePending.DeleteWindow ->
                windows.find { it.id == pending.id }
        }
        PeriodLockDisableGateDialog(
            commitment = target?.message.orEmpty(),
            windowLabel = target?.label(),
            title = when (pending) {
                is PeriodManagePending.WindowOff -> "关闭此时段？"
                is PeriodManagePending.DeleteWindow -> "删除此时段？"
            },
            confirmLabel = when (pending) {
                is PeriodManagePending.DeleteWindow -> "确认删除"
                else -> "确认关闭"
            },
            breathReason = com.life.mindfulnessapp.data.analytics.HaEvents.BreathReason.PERIOD_WINDOW,
            onConfirm = {
                when (pending) {
                    is PeriodManagePending.WindowOff -> {
                        onWindowsChange(
                            windows.map {
                                if (it.id == pending.id) it.copy(enabled = false) else it
                            }
                        )
                    }
                    is PeriodManagePending.DeleteWindow -> {
                        onWindowsChange(windows.filter { it.id != pending.id })
                    }
                }
                pendingDisable = null
            },
            onDismiss = { pendingDisable = null }
        )
    }
}

private sealed class PeriodManagePending {
    data class WindowOff(val id: String) : PeriodManagePending()
    data class DeleteWindow(val id: String) : PeriodManagePending()
}
