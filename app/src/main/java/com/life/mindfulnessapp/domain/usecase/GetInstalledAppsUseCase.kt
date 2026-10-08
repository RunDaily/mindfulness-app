package com.life.mindfulnessapp.domain.usecase

import android.content.Context
import android.content.pm.PackageManager
import com.life.mindfulnessapp.data.InstalledAppsCatalog
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.domain.model.DualAppDetector
import com.life.mindfulnessapp.util.AppNameSearch
import com.life.mindfulnessapp.util.OemDualSpace
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import javax.inject.Inject

class GetInstalledAppsUseCase @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appLimitRepository: AppLimitRepository,
    private val installedAppsCatalog: InstalledAppsCatalog
) {
    /**
     * 获取所有用户可见的已安装 App 列表，排除系统关键组件
     * 并标记哪些 App 已经在监控列表中
     * 对于仍在监控列表但已被卸载的 App，也会包含在结果中并标记 isUninstalled = true
     *
     * 包名/图标等来自 [InstalledAppsCatalog] 进程缓存；监控配置每次从 DB 合并。
     */
    suspend operator fun invoke(): List<AppInfo> = withContext(Dispatchers.IO) {
        val entries = installedAppsCatalog.snapshot()
        val monitoredLimits = appLimitRepository.getAllAppLimits().first()
        val monitoredMap = monitoredLimits.associateBy { it.packageName }
        val ownPackage = context.packageName
        val installedPackages = entries.map { it.packageName }.toSet()

        val installedAppInfos = entries.map { entry ->
            val monitoredEntry = monitoredMap[entry.packageName]
            AppInfo(
                packageName = entry.packageName,
                appName = entry.appName,
                icon = entry.icon,
                isMonitored = monitoredEntry != null && monitoredEntry.isEnabled,
                dailyLimitMinutes = monitoredEntry?.dailyLimitMinutes ?: 60,
                weeklyLimitMinutes = monitoredEntry?.weeklyLimitMinutes ?: 0,
                timeLimitEnabled = monitoredEntry?.timeLimitEnabled ?: true,
                timeAwarenessEnabled = false,
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
                compareEnabled = monitoredEntry?.compareEnabled ?: ComparePolicy.DEFAULT_ENABLED,
                compareMinMinutes = ComparePolicy.sanitizeMinMinutes(
                    monitoredEntry?.compareMinMinutes ?: ComparePolicy.DEFAULT_MIN_MINUTES
                ),
                isUninstalled = false,
                lockClonesEnabled = monitoredEntry?.lockClonesEnabled ?: true,
                blockDiscoverFeedEnabled = monitoredEntry?.blockDiscoverFeedEnabled ?: false,
                suspectedCloneOfPackage = entry.suspectedCloneOfPackage,
                suspectedClonePackages = entry.suspectedClonePackages,
                hasSystemDualInstance = entry.hasSystemDualInstance
            )
        }

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
                    timeAwarenessEnabled = false,
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
                    compareEnabled = limit.compareEnabled,
                    compareMinMinutes = ComparePolicy.sanitizeMinMinutes(limit.compareMinMinutes),
                    isUninstalled = true,
                    lockClonesEnabled = limit.lockClonesEnabled,
                    blockDiscoverFeedEnabled = limit.blockDiscoverFeedEnabled,
                    hasSystemDualInstance = false
                )
            }

        (uninstalledMonitoredAppInfos + installedAppInfos)
            .let { expandSystemDualRows(it) }
            .sortedWith(
                compareByDescending<AppInfo> { it.isUninstalled && it.isMonitored }
                    .thenByDescending { it.isMonitored }
                    .thenBy { AppNameSearch.sortKey(it.appName) }
                    .thenBy { it.appName }
            )
    }

    /**
     * 系统分身与主应用同包名，桌面只有一项；额外插入「微信 · 分身」展示行，
     * 便于搜索与理解。点击仍配置主包 [AppInfo.packageName]。
     */
    private fun expandSystemDualRows(apps: List<AppInfo>): List<AppInfo> {
        if (apps.none { it.hasSystemDualInstance && !it.isSystemDualRow }) return apps
        val out = ArrayList<AppInfo>(apps.size + 4)
        for (app in apps) {
            out += app
            if (!app.hasSystemDualInstance || app.isSystemDualRow || app.isUninstalled) continue
            // 主应用名已带「分身」时不再重复插行
            if (app.appName.contains("分身")) continue
            out += app.copy(
                appName = "${app.appName} · 分身",
                listKey = "${app.packageName}#system_dual",
                isSystemDualRow = true,
                suspectedCloneOfPackage = app.packageName,
                suspectedClonePackages = emptyList()
            )
        }
        return out
    }

    /** 只解析单个包名，供监控配置页使用；优先走目录缓存，避免整机扫一遍。 */
    suspend fun getApp(packageName: String): AppInfo? = withContext(Dispatchers.IO) {
        if (packageName == context.packageName) return@withContext null
        val pm = context.packageManager
        val limit = appLimitRepository.getAppLimit(packageName)
        val cached = installedAppsCatalog.find(packageName)
        if (cached != null) {
            return@withContext AppInfo(
                packageName = cached.packageName,
                appName = cached.appName,
                icon = cached.icon,
                isMonitored = limit != null && limit.isEnabled,
                dailyLimitMinutes = limit?.dailyLimitMinutes ?: 60,
                weeklyLimitMinutes = limit?.weeklyLimitMinutes ?: 0,
                timeLimitEnabled = limit?.timeLimitEnabled ?: true,
                timeAwarenessEnabled = false,
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
                compareEnabled = limit?.compareEnabled ?: ComparePolicy.DEFAULT_ENABLED,
                compareMinMinutes = ComparePolicy.sanitizeMinMinutes(
                    limit?.compareMinMinutes ?: ComparePolicy.DEFAULT_MIN_MINUTES
                ),
                isUninstalled = false,
                lockClonesEnabled = limit?.lockClonesEnabled ?: true,
                blockDiscoverFeedEnabled = limit?.blockDiscoverFeedEnabled ?: false,
                suspectedCloneOfPackage = cached.suspectedCloneOfPackage,
                suspectedClonePackages = cached.suspectedClonePackages,
                hasSystemDualInstance = cached.hasSystemDualInstance
            )
        }
        val launcher = installedAppsCatalog.launcherForDetect()
        try {
            val app = pm.getApplicationInfo(packageName, 0)
            val appName = pm.getApplicationLabel(app).toString()
            AppInfo(
                packageName = packageName,
                appName = appName,
                icon = pm.getApplicationIcon(app),
                isMonitored = limit != null && limit.isEnabled,
                dailyLimitMinutes = limit?.dailyLimitMinutes ?: 60,
                weeklyLimitMinutes = limit?.weeklyLimitMinutes ?: 0,
                timeLimitEnabled = limit?.timeLimitEnabled ?: true,
                timeAwarenessEnabled = false,
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
                compareEnabled = limit?.compareEnabled ?: ComparePolicy.DEFAULT_ENABLED,
                compareMinMinutes = ComparePolicy.sanitizeMinMinutes(
                    limit?.compareMinMinutes ?: ComparePolicy.DEFAULT_MIN_MINUTES
                ),
                isUninstalled = false,
                lockClonesEnabled = limit?.lockClonesEnabled ?: true,
                blockDiscoverFeedEnabled = limit?.blockDiscoverFeedEnabled ?: false,
                suspectedCloneOfPackage = DualAppDetector.findPrimaryPackage(
                    launcher, packageName, appName
                ),
                suspectedClonePackages = DualAppDetector.findClonePackages(
                    installed = launcher,
                    primaryPackage = packageName,
                    primaryLabel = appName
                ),
                hasSystemDualInstance = OemDualSpace.hasSystemDualInstance(context, packageName)
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
                    timeAwarenessEnabled = false,
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
                    compareEnabled = entity.compareEnabled,
                    compareMinMinutes = ComparePolicy.sanitizeMinMinutes(entity.compareMinMinutes),
                    isUninstalled = true,
                    lockClonesEnabled = entity.lockClonesEnabled,
                    blockDiscoverFeedEnabled = entity.blockDiscoverFeedEnabled,
                    hasSystemDualInstance = false
                )
            }
        }
    }
}
