package com.life.mindfulnessapp.domain.usecase

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.life.mindfulnessapp.service.KeepAliveAccessibilityService
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class PermissionStatus(
    val hasOverlay: Boolean,
    val hasUsageStats: Boolean,
    val hasBatteryOptimizationIgnored: Boolean,
    val hasNotification: Boolean = true,
    /** 可选系统级保活：无障碍绑定（不计入 core / recommended 硬门槛） */
    val hasAccessibilityKeepAlive: Boolean = false
) {
    /** 监控生效所需的核心权限（悬浮窗 + 使用情况访问） */
    val coreGranted: Boolean get() = hasOverlay && hasUsageStats

    /**
     * 产品效果推荐完整度：核心 + 忽略电池优化 + 通知。
     * 电池与通知直接影响后台保活与前台服务稳定性，按「建议必备」对待。
     * 无障碍保活为更强一档，单独展示，不阻塞「推荐完整」以免强推敏感权限。
     */
    val recommendedGranted: Boolean
        get() = coreGranted && hasBatteryOptimizationIgnored && hasNotification

    /** 首页完整性展示用，等同 [recommendedGranted] */
    val allGranted: Boolean get() = recommendedGranted

    val missingRecommendedLabels: List<String>
        get() = buildList {
            if (!hasOverlay) add("悬浮窗")
            if (!hasUsageStats) add("使用情况")
            if (!hasBatteryOptimizationIgnored) add("电池优化")
            if (!hasNotification) add("通知")
        }

    /** Soft 门额外展示的加强项（无障碍） */
    val missingKeepAliveBoostLabels: List<String>
        get() = buildList {
            if (!hasAccessibilityKeepAlive) add("无障碍保活")
        }
}

class CheckPermissionsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): PermissionStatus {
        return PermissionStatus(
            hasOverlay = hasOverlayPermission(),
            hasUsageStats = hasUsageStatsPermission(),
            hasBatteryOptimizationIgnored = isBatteryOptimizationIgnored(),
            hasNotification = hasNotificationPermission(),
            hasAccessibilityKeepAlive = KeepAliveAccessibilityService.isEnabled(context)
        )
    }

    fun hasOverlayPermission(): Boolean = Settings.canDrawOverlays(context)

    fun hasUsageStatsPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                context.packageName
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isBatteryOptimizationIgnored(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    /** Android 13+ 运行时授权；更低版本看通知总开关是否被用户关掉 */
    fun hasNotificationPermission(): Boolean {
        val managerOn = androidx.core.app.NotificationManagerCompat
            .from(context)
            .areNotificationsEnabled()
        if (!managerOn) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    companion object {
        fun getOverlaySettingsUri(packageName: String): Uri {
            return Uri.parse("package:$packageName")
        }
    }
}
