package com.life.mindfulnessapp.domain.usecase

import android.app.AppOpsManager
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

data class PermissionStatus(
    val hasOverlay: Boolean,
    val hasUsageStats: Boolean,
    val hasBatteryOptimizationIgnored: Boolean,
    val hasNotification: Boolean = true
) {
    val allGranted: Boolean get() = hasOverlay && hasUsageStats
}

class CheckPermissionsUseCase @Inject constructor(
    @ApplicationContext private val context: Context
) {
    operator fun invoke(): PermissionStatus {
        return PermissionStatus(
            hasOverlay = hasOverlayPermission(),
            hasUsageStats = hasUsageStatsPermission(),
            hasBatteryOptimizationIgnored = isBatteryOptimizationIgnored(),
            hasNotification = hasNotificationPermission()
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

    /** Android 13+ 需要运行时授权；13 以下系统直接返回 true */
    fun hasNotificationPermission(): Boolean {
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
