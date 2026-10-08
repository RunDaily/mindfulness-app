package com.life.mindfulnessapp.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.domain.model.DualAppDetector
import com.life.mindfulnessapp.util.OemDualSpace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 运行时分身支持：
 * 1. 异包名分身：别名表 clonePkg → primary
 * 2. 同包名系统分身（华为 128 / 小米 999 / 三星 Dual Messenger 95）：记录需兜底探测前台的包名集合
 */
@Singleton
class DualAppRegistry @Inject constructor(
    @ApplicationContext private val context: Context
) {
    @Volatile
    private var aliasToPrimary: Map<String, String> = emptyMap()

    @Volatile
    private var primaryToClones: Map<String, Set<String>> = emptyMap()

    /** 开启「分身一并锁定」且存在系统分身空间安装的主包 */
    @Volatile
    private var systemDualPrimaries: Set<String> = emptySet()

    /** 分身包映射到主包；非分身原样返回 */
    fun canonicalize(packageName: String): String =
        aliasToPrimary[packageName] ?: packageName

    fun isAliasedClone(packageName: String): Boolean =
        aliasToPrimary.containsKey(packageName)

    fun clonesOf(primaryPackage: String): Set<String> =
        primaryToClones[primaryPackage].orEmpty()

    fun hasSystemDual(primaryPackage: String): Boolean =
        primaryPackage in systemDualPrimaries

    fun systemDualPackages(): Set<String> = systemDualPrimaries

    /**
     * 根据当前启用监控列表重建别名与系统分身集合。
     * 已单独加入监控的包名不会被当成别人的分身别名。
     */
    fun refresh(enabledLimits: List<AppLimitEntity>) {
        OemDualSpace.invalidateCache()
        val launcher = scanLauncherApps()
        val enabledPkgs = enabledLimits.asSequence()
            .filter { it.isEnabled }
            .map { it.packageName }
            .toSet()
        val alias = LinkedHashMap<String, String>()
        val byPrimary = LinkedHashMap<String, MutableSet<String>>()
        val systemDual = linkedSetOf<String>()

        for (limit in enabledLimits) {
            if (!limit.isEnabled || !limit.lockClonesEnabled) continue
            val label = launcher.firstOrNull { it.packageName == limit.packageName }?.appName
                ?: limit.appName
            val clones = DualAppDetector.findClonePackages(
                installed = launcher,
                primaryPackage = limit.packageName,
                primaryLabel = label
            ).filter { it !in enabledPkgs }
            if (clones.isNotEmpty()) {
                val bucket = byPrimary.getOrPut(limit.packageName) { linkedSetOf() }
                for (clone in clones) {
                    if (alias.putIfAbsent(clone, limit.packageName) == null) {
                        bucket += clone
                    }
                }
            }
            if (OemDualSpace.hasSystemDualInstance(context, limit.packageName)) {
                systemDual += limit.packageName
            }
        }

        aliasToPrimary = alias
        primaryToClones = byPrimary.mapValues { it.value.toSet() }
        systemDualPrimaries = systemDual
    }

    /** 桌面可见 App，供 UI 标注「疑似分身」 */
    fun scanLauncherApps(): List<DualAppDetector.LauncherApp> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val own = context.packageName
        return pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
            .asSequence()
            .map { it.activityInfo.packageName to it.loadLabel(pm).toString() }
            .filter { it.first != own }
            .distinctBy { it.first }
            .map { DualAppDetector.LauncherApp(it.first, it.second) }
            .toList()
    }

    fun findSuspectedClones(primaryPackage: String, primaryLabel: String): List<String> =
        DualAppDetector.findClonePackages(scanLauncherApps(), primaryPackage, primaryLabel)

    fun findSuspectedPrimary(packageName: String, appName: String): String? =
        DualAppDetector.findPrimaryPackage(scanLauncherApps(), packageName, appName)

    /**
     * UsageStats 未报出目标时，探测系统分身空间前台。
     * 仅对 [systemDualPrimaries] 生效。
     */
    fun probeSystemDualForeground(): String? {
        val monitored = systemDualPrimaries
        if (monitored.isEmpty()) return null
        OemDualSpace.foregroundMonitoredInDualSpace(context, monitored)?.let { return it }
        return OemDualSpace.foregroundFromDualUsageStats(context, monitored)
    }

    /** 当前拦截是否来自系统分身前台（确认进入后应启动分身而非露出机主侧本体） */
    @Volatile
    var lastInterceptWasSystemDual: Boolean = false
}
