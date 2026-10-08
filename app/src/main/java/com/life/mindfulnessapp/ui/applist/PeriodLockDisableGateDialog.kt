package com.life.mindfulnessapp.ui.applist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.life.mindfulnessapp.data.analytics.HaEvents
import com.life.mindfulnessapp.data.repository.AnalyticsRepository
import com.life.mindfulnessapp.ui.common.BreathCostGateDialog
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * 时段锁 / 日程锁已武装时，关闭 / 删除 / 改动的代价：
 * 全屏按住配合呼吸（见 [BreathCostGateDialog] / [com.life.mindfulnessapp.ui.common.BreathCostHoldScreen]）。
 * 不要求此刻落在窗内——窗外拆锁也是旁路。拦截页本身不提供破界入口。
 */
@Composable
fun PeriodLockDisableGateDialog(
    commitment: String,
    windowLabel: String?,
    title: String = "关闭时段锁？",
    confirmLabel: String = "确认关闭",
    breathReason: String = HaEvents.BreathReason.PERIOD_DISABLE,
    appName: String = "",
    packageName: String = "",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val analytics = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AnalyticsEntryPoint::class.java
        ).analyticsRepository()
    }
    val subtitle = buildString {
        if (!windowLabel.isNullOrBlank()) {
            append("这将削弱「$windowLabel」的锁定。")
        } else {
            append("这将削弱已设定的锁定。")
        }
        if (commitment.isNotBlank()) {
            append("\n你曾写下：「$commitment」")
        }
        append("\n把手指按在圆里，配合呼吸，再决定。")
    }
    BreathCostGateDialog(
        title = title,
        subtitle = subtitle.trim(),
        confirmHint = confirmLabel,
        onCompleted = {
            analytics.trackBreathGatePass(
                reason = breathReason,
                app = appName,
                pkg = packageName
            )
            onConfirm()
        },
        onDismiss = onDismiss
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AnalyticsEntryPoint {
    fun analyticsRepository(): AnalyticsRepository
}
