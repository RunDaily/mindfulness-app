package com.life.mindfulnessapp.ui.applist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindow

/**
 * 时段锁：时段列表即主内容；寄语在各时段里填写。
 * 添加流主开关已开，默认带一段睡眠时段。
 *
 * @param lockIsLive 时段锁已对当前 App 真正跑起来（已保存且开启）。
 * 初次设定为 false：不标「生效中」，改动也不过呼吸门槛。
 */
@Composable
fun PeriodLockConfigContent(
    appName: String,
    periodLockOn: Boolean,
    onPeriodLockChange: (Boolean) -> Unit,
    periodWindows: List<PeriodWindow>,
    onPeriodWindowsChange: (List<PeriodWindow>) -> Unit,
    allowMasterToggle: Boolean = true,
    lockIsLive: Boolean = false,
    modifier: Modifier = Modifier
) {
    val policy = PeriodLockPolicy

    var pendingMasterOff by remember { mutableStateOf(false) }
    var pendingWindow by remember { mutableStateOf<PeriodInlinePending?>(null) }

    fun requestMaster(on: Boolean) {
        when {
            // 已保存并开启的时段锁：关掉总开关即削弱，窗外也过门槛
            !on && lockIsLive -> pendingMasterOff = true
            else -> {
                onPeriodLockChange(on)
                if (on && periodWindows.isEmpty()) {
                    onPeriodWindowsChange(listOf(PeriodWindow.defaultSleep()))
                }
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 48.dp)
    ) {
        CapabilityHeroWithSwitch(
            title = "时段锁",
            description = "在关键时间段限制「$appName」的使用，也可设为全天",
            checked = periodLockOn,
            onCheckedChange = { requestMaster(it) },
            allowToggle = allowMasterToggle
        )

        AnimatedVisibility(
            visible = !periodLockOn && allowMasterToggle,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CapabilityRuleCaption(
                text = "关闭后，关键时段将不再硬挡进入。",
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        AnimatedVisibility(
            visible = periodLockOn,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(28.dp))

                CapabilityNestedCard(emphasized = true) {
                    PeriodWindowSettings(
                        windows = periodWindows,
                        onWindowsChange = onPeriodWindowsChange,
                        lockIsLive = lockIsLive,
                        onRequestDisableWindow = { id ->
                            pendingWindow = PeriodInlinePending.WindowOff(id)
                        },
                        onRequestDeleteWindow = { id ->
                            val w = periodWindows.find { it.id == id }
                            // 已武装：删掉开启中的时段即削弱（不要求此刻落在窗内）
                            if (lockIsLive && w != null && w.enabled) {
                                pendingWindow = PeriodInlinePending.DeleteWindow(id)
                            } else {
                                onPeriodWindowsChange(periodWindows.filter { it.id != id })
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                CapabilityRuleCaption(
                    text = periodLockCaption(
                        empty = periodWindows.isEmpty(),
                        lockIsLive = lockIsLive
                    )
                )
            }
        }
    }

    if (pendingMasterOff) {
        val active = policy.activeWindow(periodWindows)
        PeriodLockDisableGateDialog(
            commitment = active?.message.orEmpty(),
            windowLabel = active?.label(),
            title = "关闭时段锁？",
            confirmLabel = "确认关闭",
            onConfirm = {
                onPeriodLockChange(false)
                pendingMasterOff = false
            },
            onDismiss = { pendingMasterOff = false }
        )
    }

    pendingWindow?.let { pending ->
        val target = when (pending) {
            is PeriodInlinePending.WindowOff ->
                periodWindows.find { it.id == pending.id }
            is PeriodInlinePending.DeleteWindow ->
                periodWindows.find { it.id == pending.id }
        }
        PeriodLockDisableGateDialog(
            commitment = target?.message.orEmpty(),
            windowLabel = target?.label(),
            title = when (pending) {
                is PeriodInlinePending.WindowOff -> "关闭此时段？"
                is PeriodInlinePending.DeleteWindow -> "删除此时段？"
            },
            confirmLabel = when (pending) {
                is PeriodInlinePending.DeleteWindow -> "确认删除"
                else -> "确认关闭"
            },
            breathReason = com.life.mindfulnessapp.data.analytics.HaEvents.BreathReason.PERIOD_WINDOW,
            onConfirm = {
                when (pending) {
                    is PeriodInlinePending.WindowOff -> {
                        onPeriodWindowsChange(
                            periodWindows.map {
                                if (it.id == pending.id) it.copy(enabled = false) else it
                            }
                        )
                    }
                    is PeriodInlinePending.DeleteWindow -> {
                        onPeriodWindowsChange(periodWindows.filter { it.id != pending.id })
                    }
                }
                pendingWindow = null
            },
            onDismiss = { pendingWindow = null }
        )
    }
}

private fun periodLockCaption(empty: Boolean, lockIsLive: Boolean): String = when {
    empty -> "请至少添加一个时段。锁定时段内不可进入；各时段不可重复或重叠。"
    lockIsLive ->
        "锁定时段内不可进入。各时段不可重复或重叠。关闭、删除或改动已开启的时段，需按住配合呼吸。可在各时段写下寄语，拦截页会显示。"
    else ->
        "锁定时段内不可进入。各时段不可重复或重叠。可在各时段写下寄语，拦截页会显示。"
}

private sealed class PeriodInlinePending {
    data class WindowOff(val id: String) : PeriodInlinePending()
    data class DeleteWindow(val id: String) : PeriodInlinePending()
}
