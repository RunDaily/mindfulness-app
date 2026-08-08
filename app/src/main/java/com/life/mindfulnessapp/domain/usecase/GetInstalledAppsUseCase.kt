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
        // 同一包名可能有多个 launcher Activity（如美团扫卡等），需按包名去重，
        // 否则 LazyColumn 以 packageName 为 key 时会抛 IllegalArgumentException
        val installedAppInfos = launcherApps
            .filter { it.activityInfo.packageName != ownPackage }
            .distinctBy { it.activityInfo.packageName }
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
                    usageCovenant = monitoredEntry?.usageCovenant ?: "",
                    remindCovenantOnOpen = monitoredEntry?.remindCovenantOnOpen ?: true,
                    requireIntentOnOpen = monitoredEntry?.requireIntentOnOpen ?: true,
                    sessionLimitEnabled = monitoredEntry?.sessionLimitEnabled ?: true,
                    intentQualityCheckEnabled = monitoredEntry?.intentQualityCheckEnabled ?: false,
                    intentBlockKeywordsJson = monitoredEntry?.intentBlockKeywordsJson ?: "",
                    defaultSessionLimitMinutes = monitoredEntry?.defaultSessionLimitMinutes ?: 15,
                    intentReviewEnabled = monitoredEntry?.intentReviewEnabled ?: false,
                    dailyOpenLimitEnabled = monitoredEntry?.dailyOpenLimitEnabled ?: false,
                    dailyOpenLimit = monitoredEntry?.dailyOpenLimit ?: 5,
                    periodLockEnabled = monitoredEntry?.periodLockEnabled ?: false,
                    periodWindowsJson = monitoredEntry?.periodWindowsJson ?: "",
                    periodLockCommitment = monitoredEntry?.periodLockCommitment ?: "",
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
                    usageCovenant = limit.usageCovenant,
                    remindCovenantOnOpen = limit.remindCovenantOnOpen,
                    requireIntentOnOpen = limit.requireIntentOnOpen,
                    sessionLimitEnabled = limit.sessionLimitEnabled,
                    intentQualityCheckEnabled = limit.intentQualityCheckEnabled,
                    intentBlockKeywordsJson = limit.intentBlockKeywordsJson,
                    defaultSessionLimitMinutes = limit.defaultSessionLimitMinutes,
                    intentReviewEnabled = limit.intentReviewEnabled,
                    dailyOpenLimitEnabled = limit.dailyOpenLimitEnabled,
                    dailyOpenLimit = limit.dailyOpenLimit,
                    periodLockEnabled = limit.periodLockEnabled,
                    periodWindowsJson = limit.periodWindowsJson,
                    periodLockCommitment = limit.periodLockCommitment,
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

    /** 只解析单个包名，供监控配置页使用，避免整机扫一遍。 */
    suspend fun getApp(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        if (packageName == context.packageName) return@withContext null
        val pm = context.packageManager
        val limit = appLimitRepository.getAppLimit(packageName)
        try {
            val app = pm.getApplicationInfo(packageName, 0)
            AppInfo(
                packageName = packageName,
                appName = pm.getApplicationLabel(app).toString(),
                icon = pm.getApplicationIcon(app),
                isMonitored = limit != null && limit.isEnabled,
                dailyLimitMinutes = limit?.dailyLimitMinutes ?: 60,
                weeklyLimitMinutes = limit?.weeklyLimitMinutes ?: 0,
                timeLimitEnabled = limit?.timeLimitEnabled ?: true,
                overTimeMessage = limit?.overTimeMessage ?: "",
                usageCovenant = limit?.usageCovenant ?: "",
                remindCovenantOnOpen = limit?.remindCovenantOnOpen ?: true,
                requireIntentOnOpen = limit?.requireIntentOnOpen ?: true,
                sessionLimitEnabled = limit?.sessionLimitEnabled ?: true,
                intentQualityCheckEnabled = limit?.intentQualityCheckEnabled ?: false,
                intentBlockKeywordsJson = limit?.intentBlockKeywordsJson ?: "",
                defaultSessionLimitMinutes = limit?.defaultSessionLimitMinutes ?: 15,
                intentReviewEnabled = limit?.intentReviewEnabled ?: false,
                dailyOpenLimitEnabled = limit?.dailyOpenLimitEnabled ?: false,
                dailyOpenLimit = limit?.dailyOpenLimit ?: 5,
                periodLockEnabled = limit?.periodLockEnabled ?: false,
                periodWindowsJson = limit?.periodWindowsJson ?: "",
                periodLockCommitment = limit?.periodLockCommitment ?: "",
                isUninstalled = false
            )
        } catch (_: PackageManager.NameNotFoundException) {
            limit?.takeIf { it.isEnabled }?.let { entity ->
                AppInfo(
                    packageName = entity.packageName,
                    appName = entity.appName,
                    icon = null,
                    isMonitored = true,
                    dailyLimitMinutes = entity.dailyLimitMinutes,
                    weeklyLimitMinutes = entity.weeklyLimitMinutes,
                    timeLimitEnabled = entity.timeLimitEnabled,
                    overTimeMessage = entity.overTimeMessage,
                    usageCovenant = entity.usageCovenant,
                    remindCovenantOnOpen = entity.remindCovenantOnOpen,
                    requireIntentOnOpen = entity.requireIntentOnOpen,
                    sessionLimitEnabled = entity.sessionLimitEnabled,
                    intentQualityCheckEnabled = entity.intentQualityCheckEnabled,
                    intentBlockKeywordsJson = entity.intentBlockKeywordsJson,
                    defaultSessionLimitMinutes = entity.defaultSessionLimitMinutes,
                    intentReviewEnabled = entity.intentReviewEnabled,
                    dailyOpenLimitEnabled = entity.dailyOpenLimitEnabled,
                    dailyOpenLimit = entity.dailyOpenLimit,
                    periodLockEnabled = entity.periodLockEnabled,
                    periodWindowsJson = entity.periodWindowsJson,
                    periodLockCommitment = entity.periodLockCommitment,
                    isUninstalled = true
                )
            }
        }
    }
}
