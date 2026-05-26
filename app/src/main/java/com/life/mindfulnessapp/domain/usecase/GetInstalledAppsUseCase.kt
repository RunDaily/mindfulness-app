package com.life.mindfulnessapp.domain.usecase

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLimitRepository: AppLimitRepository
) {
    /**
     * 获取所有用户可见的已安装 App 列表，排除系统关键组件
     * 并标记哪些 App 已经在监控列表中
     * 对于仍在监控列表但已被卸载的 App，也会包含在结果中并标记 isUninstalled = true
     */
    suspend operator fun invoke(): List<AppInfo> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }

        val launcherApps = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        val monitoredLimits = appLimitRepository.getAllAppLimits().first()
        val monitoredMap = monitoredLimits.associateBy { it.packageName }

        // 排除自己
        val ownPackage = context.packageName

        // 已安装 App 的包名集合
        val installedPackages = launcherApps
            .map { it.activityInfo.packageName }
            .toSet()

        // 将已安装的 App 映射为 AppInfo
        val installedAppInfos = launcherApps
            .filter { it.activityInfo.packageName != ownPackage }
            .map { resolveInfo ->
                val pkg = resolveInfo.activityInfo.packageName
                val monitoredEntry = monitoredMap[pkg]
                AppInfo(
                    packageName = pkg,
                    appName = resolveInfo.loadLabel(pm).toString(),
                    icon = resolveInfo.loadIcon(pm),
                    isMonitored = monitoredEntry != null && monitoredEntry.isEnabled,
                    dailyLimitMinutes = monitoredEntry?.dailyLimitMinutes ?: 60,
                    weeklyLimitMinutes = monitoredEntry?.weeklyLimitMinutes ?: 0,
                    timeLimitEnabled = monitoredEntry?.timeLimitEnabled ?: true,
                    overTimeMessage = monitoredEntry?.overTimeMessage ?: "",
                    isUninstalled = false
                )
            }

        // 在监控列表中但已被卸载的 App（不在已安装集合中）
        val uninstalledMonitoredAppInfos = monitoredLimits
            .filter { it.isEnabled && it.packageName !in installedPackages && it.packageName != ownPackage }
            .map { limit ->
                AppInfo(
                    packageName = limit.packageName,
                    appName = limit.appName,
                    icon = null,
                    isMonitored = true,
                    dailyLimitMinutes = limit.dailyLimitMinutes,
                    weeklyLimitMinutes = limit.weeklyLimitMinutes,
                    timeLimitEnabled = limit.timeLimitEnabled,
                    overTimeMessage = limit.overTimeMessage,
                    isUninstalled = true
                )
            }

        // 合并：已卸载的监控 App 优先排在前面，其次是其他监控 App，最后是未监控 App
        (uninstalledMonitoredAppInfos + installedAppInfos)
            .sortedWith(
                compareByDescending<AppInfo> { it.isUninstalled && it.isMonitored }
                    .thenByDescending { it.isMonitored }
                    .thenBy { it.appName }
            )
    }
}
