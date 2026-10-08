package com.life.mindfulnessapp.util

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.life.mindfulnessapp.domain.usecase.PermissionStatus

/**
 * 厂商保活开关清单：每项 = 用户要开的一个开关。
 * 主指南 [contentForDevice] 各厂商统一精简结构；
 * OriginOS 完整六步见 [vivoOriginOsContent]。
 */
object OemKeepAliveGuide {

    enum class Family {
        Xiaomi, Huawei, Honor, OppoFamily, VivoFamily, Samsung, Other
    }

    /**
     * 系统可自动识别的权限类型（无需用户手动勾选）。
     */
    enum class DetectKind {
        Overlay,
        UsageStats,
        Notification,
        BatteryIgnore,
        Accessibility
    }

    /**
     * @param id 本地勾选键（仅 [detectKind] 为空时使用）
     * @param title 开关名（短）
     * @param target 目标状态
     * @param how 路径（一行内说清）
     * @param intents 跳转意图；[detectKind] 非空时也可用于「去设置」
     * @param detectKind 非空则根据系统 API 自动识别开关状态
     * @param required 是否计入「必备」进度
     */
    data class SwitchItem(
        val id: String,
        val title: String,
        val target: String,
        val how: String,
        val intents: List<Intent>,
        val detectKind: DetectKind? = null,
        val required: Boolean = true
    )

    fun isDetectOn(kind: DetectKind, status: PermissionStatus): Boolean =
        when (kind) {
            DetectKind.Overlay -> status.hasOverlay
            DetectKind.UsageStats -> status.hasUsageStats
            DetectKind.Notification -> status.hasNotification
            DetectKind.BatteryIgnore -> status.hasBatteryOptimizationIgnored
            DetectKind.Accessibility -> status.hasAccessibilityKeepAlive
        }

    data class Content(
        val family: Family,
        val displayName: String,
        /** 机型一句提示，可空 */
        val tip: String?,
        val switches: List<SwitchItem>
    )

    private const val PREFS = "keep_alive_checklist"

    fun resolveFamily(
        manufacturer: String = Build.MANUFACTURER.orEmpty(),
        brand: String = Build.BRAND.orEmpty()
    ): Family {
        val key = "${manufacturer.lowercase()} ${brand.lowercase()}"
        return when {
            key.contains("xiaomi") || key.contains("redmi") ||
                key.contains("poco") || key.contains("blackshark") -> Family.Xiaomi
            key.contains("huawei") -> Family.Huawei
            key.contains("honor") -> Family.Honor
            key.contains("oppo") || key.contains("realme") ||
                key.contains("oneplus") || key.contains("oplus") -> Family.OppoFamily
            key.contains("vivo") || key.contains("iqoo") -> Family.VivoFamily
            key.contains("samsung") -> Family.Samsung
            else -> Family.Other
        }
    }

    fun contentForDevice(context: Context): Content {
        val packageUri = Uri.parse("package:${context.packageName}")
        val base = when (resolveFamily()) {
            Family.Xiaomi -> xiaomi(packageUri)
            Family.Huawei -> huawei(packageUri, honor = false)
            Family.Honor -> huawei(packageUri, honor = true)
            Family.OppoFamily -> oppoFamily(packageUri)
            // 主指南走与其他机型一致的精简清单；OriginOS 六步见 [vivoOriginOs]
            Family.VivoFamily -> vivoUnified(packageUri)
            Family.Samsung -> samsung(packageUri)
            Family.Other -> other(packageUri)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            base.copy(switches = listOf(restrictedSettingsItem(packageUri)) + base.switches)
        } else {
            base
        }
    }

    /** OriginOS 完整六步（独立入口页面使用） */
    fun vivoOriginOsContent(context: Context): Content =
        vivoOriginOs(Uri.parse("package:${context.packageName}"))

    /** 仅 vivo / iQOO 本机展示 OriginOS 六步入口（非本机不出现） */
    fun shouldShowVivoOriginOsEntry(): Boolean =
        resolveFamily() == Family.VivoFamily

    /**
     * 一键清理更狠的机型：防清理段置顶。
     * 三星等相对温和：能工作段置顶。
     */
    fun prioritizeAntiClean(family: Family = resolveFamily()): Boolean =
        when (family) {
            Family.Xiaomi, Family.Huawei, Family.Honor,
            Family.OppoFamily, Family.VivoFamily -> true
            Family.Samsung, Family.Other -> false
        }

    fun deviceSubtitle(content: Content): String {
        val model = Build.MODEL.orEmpty().ifBlank { "本机" }
        return if (prioritizeAntiClean(content.family)) {
            "${content.displayName} · 防清理优先"
        } else {
            "${content.displayName} · $model"
        }
    }

    /** 「我」入口副文用：可自动检测的能工作项进度 */
    fun workReadyProgress(status: PermissionStatus): Pair<Int, Int> {
        val flags = listOf(
            status.hasOverlay,
            status.hasUsageStats,
            status.hasNotification,
            status.hasBatteryOptimizationIgnored,
            status.hasAccessibilityKeepAlive
        )
        return flags.count { it } to flags.size
    }

    fun isMarkedDone(context: Context, id: String): Boolean =
        prefs(context).getBoolean(id, false)

    fun setMarkedDone(context: Context, id: String, done: Boolean) {
        prefs(context).edit().putBoolean(id, done).apply()
    }

    fun launchFirstAvailable(context: Context, intents: List<Intent>): Boolean {
        for (intent in intents) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(context.packageManager) != null) {
                try {
                    context.startActivity(intent)
                    return true
                } catch (_: Exception) {
                    // try next
                }
            }
        }
        return try {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun appDetails(packageUri: Uri) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).setData(packageUri)

    private fun restrictedSettingsItem(packageUri: Uri) = SwitchItem(
        id = "restricted_settings",
        title = "允许受限制设置",
        target = "已允许",
        how = com.life.mindfulnessapp.util.RestrictedSettingsGuide.howOneLine +
            "。若弹「系统已拒绝授予」即缺此项",
        intents = listOf(appDetails(packageUri))
    )

    // ── 小米 ───────────────────────────────────────────────────────────────

    private fun xiaomi(packageUri: Uri) = Content(
        family = Family.Xiaomi,
        displayName = "小米",
        tip = "一键清理会彻底杀进程：必须自启 + 无限制 + 锁定；建议再开壁纸守护与隐藏最近任务",
        switches = listOf(
            lockTaskItem(),
            SwitchItem(
                id = "mi_autostart",
                title = "自启动",
                target = "允许",
                how = "设置 → 应用设置 → 应用管理 → 心锚 → 自启动；安全中心 → 应用管理 → 权限 → 自启动管理",
                intents = listOf(
                    component("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
                    appDetails(packageUri)
                )
            ),
            SwitchItem(
                id = "mi_battery",
                title = "省电策略（无限制）",
                target = "无限制",
                how = "应用管理 → 心锚 → 省电策略 → 无限制；勿选「省电」或「限制」",
                intents = listOf(
                    component("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"),
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(packageUri),
                    appDetails(packageUri)
                ),
                detectKind = DetectKind.BatteryIgnore
            ),
            SwitchItem(
                id = "mi_clean",
                title = "清理加速白名单",
                target = "锁定 / 不清理",
                how = "安全中心 → 清理加速 → 设置（或右上角）→ 锁屏清理 / 清理白名单 → 心锚不清理；多任务卡片务必锁定",
                intents = listOf(
                    component("com.miui.cleanmaster", "com.miui.optimizecenter.settings.SettingsActivity"),
                    component("com.miui.securitycenter", "com.miui.securityscan.MainActivity"),
                    appDetails(packageUri)
                )
            )
        )
    )

    // ── 华为 / 荣耀 ────────────────────────────────────────────────────────

    private fun huawei(packageUri: Uri, honor: Boolean) = Content(
        family = if (honor) Family.Honor else Family.Huawei,
        displayName = if (honor) "荣耀" else "华为",
        tip = "一键优化会杀后台：启动管理三项全开 + 多任务锁定；建议壁纸守护与隐藏最近任务",
        switches = listOf(
            lockTaskItem(),
            SwitchItem(
                id = "hw_startup",
                title = "启动管理（手动三项）",
                target = "自启·关联·后台全开",
                how = "手机管家 → 应用启动管理 → 心锚 → 关自动管理 → 允许自启动 / 关联启动 / 后台活动 全开",
                intents = listOf(
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    component("com.hihonor.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"),
                    appDetails(packageUri)
                )
            ),
            SwitchItem(
                id = "hw_overlay",
                title = "悬浮窗",
                target = "允许",
                how = "设置 → 应用和服务 → 权限管理 → 悬浮窗 → 心锚",
                intents = listOf(
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.addviewmonitor.AddViewMonitorActivity"),
                    Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).setData(packageUri),
                    appDetails(packageUri)
                ),
                detectKind = DetectKind.Overlay
            ),
            SwitchItem(
                id = "hw_bg_popup",
                title = "后台弹出界面",
                target = "允许",
                how = "权限管理 → 后台弹出界面 → 心锚；缺此项拦截页可能不显示",
                intents = listOf(
                    component("com.huawei.systemmanager", "com.huawei.permissionmanager.ui.MainActivity"),
                    appDetails(packageUri)
                )
            ),
            SwitchItem(
                id = "hw_battery",
                title = "耗电详情不优化",
                target = "允许后台高耗电",
                how = "设置 → 电池 → 应用耗电详情 / 更多电池设置 → 心锚 → 允许后台活动、关闭休眠清理",
                intents = listOf(
                    component("com.huawei.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"),
                    component("com.hihonor.systemmanager", "com.huawei.systemmanager.power.ui.HwPowerManagerActivity"),
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(packageUri),
                    appDetails(packageUri)
                ),
                detectKind = DetectKind.BatteryIgnore
            )
        )
    )

    // ── OPPO / realme / 一加 ───────────────────────────────────────────────

    private fun oppoFamily(packageUri: Uri): Content {
        val label = when {
            Build.BRAND.orEmpty().contains("realme", true) ||
                Build.MANUFACTURER.orEmpty().contains("realme", true) -> "realme"
            Build.BRAND.orEmpty().contains("oneplus", true) ||
                Build.MANUFACTURER.orEmpty().contains("oneplus", true) -> "一加"
            else -> "OPPO"
        }
        return Content(
            family = Family.OppoFamily,
            displayName = label,
            tip = "关键：自启动 + 允许后台 + 多任务锁定",
            switches = listOf(
                SwitchItem(
                    id = "op_autostart",
                    title = "自启动",
                    target = "允许",
                    how = "设置 → 应用 → 自启动 → 允许心锚",
                    intents = listOf(
                        component("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                        component("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
                        appDetails(packageUri)
                    )
                ),
                SwitchItem(
                    id = "op_battery",
                    title = "允许后台运行",
                    target = "开 · 不优化",
                    how = "设置 → 电池 → 应用耗电管理 → 心锚允许后台（关闭电池优化）",
                    intents = listOf(
                        component("com.oplus.battery", "com.oplus.powermanager.fuelgaue.PowerConsumptionActivity"),
                        component("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
                        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(packageUri),
                        appDetails(packageUri)
                    ),
                    detectKind = DetectKind.BatteryIgnore
                ),
                lockTaskItem()
            )
        )
    }

    /** 主指南：与其他厂商同结构的精简三项 */
    private fun vivoUnified(packageUri: Uri): Content {
        val label = vivoLabel()
        return Content(
            family = Family.VivoFamily,
            displayName = label,
            tip = "i管家一键清理会彻底杀进程：先锁定多任务，再开自启与高耗电；完整六步见下方 OriginOS 指南，并建议开壁纸守护。",
            switches = listOf(
                SwitchItem(
                    id = "vv_lock",
                    title = "锁定后台任务",
                    target = "出现锁头",
                    how = "上滑停顿 → 心锚卡片下拉至锁头（或长按选锁定）。未锁定时一键清理必杀",
                    intents = emptyList()
                ),
                SwitchItem(
                    id = "vv_autostart",
                    title = "自启动",
                    target = "允许",
                    how = "设置 → 应用与权限 → 权限管理 → 自启动",
                    intents = listOf(
                        component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                        component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                        appDetails(packageUri)
                    )
                ),
                SwitchItem(
                    id = "vv_power",
                    title = "允许后台高耗电",
                    target = "开",
                    how = "设置 → 电池 → 后台耗电管理 → 允许后台高耗电",
                    intents = listOf(
                        component("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
                        component("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"),
                        appDetails(packageUri)
                    )
                )
            )
        )
    }

    /**
     * OriginOS 5+/6 完整六步（独立页），顺序与官方保活路径一致：
     * 1 多任务锁定 → 2 自启动 → 3 后台高耗电 → 4 关闭电池优化 → 5 加速白名单 → 6 智能后台冻结例外
     */
    private fun vivoOriginOs(packageUri: Uri): Content = Content(
        family = Family.VivoFamily,
        displayName = "vivo OriginOS",
        tip = "适用于 OriginOS 5 / 6。按 1～6 做完；第 6 步后需重启生效。勿在 i管家一键清理时清掉心锚。",
        switches = listOf(
            SwitchItem(
                id = "vv_os_lock",
                title = "锁定后台任务",
                target = "出现锁头",
                how = "上滑停顿打开多任务 → 找到心锚卡片 → 下拉至出现锁头（或长按选「锁定」）",
                intents = emptyList()
            ),
            SwitchItem(
                id = "vv_os_autostart",
                title = "开启自启动权限",
                target = "开",
                how = "设置 → 应用与权限 → 权限管理 → 自启动 → 开启心锚",
                intents = listOf(
                    component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    component("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.PurviewTabActivity"),
                    appDetails(packageUri)
                )
            ),
            SwitchItem(
                id = "vv_os_power",
                title = "允许后台高耗电",
                target = "开",
                how = "设置 → 电池 → 后台耗电管理 → 心锚 → 允许后台高耗电；若为智能省电，建议改「普通/高性能」",
                intents = listOf(
                    component("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
                    component("com.iqoo.powersaving", "com.iqoo.powersaving.PowerSavingManagerActivity"),
                    appDetails(packageUri)
                )
            ),
            SwitchItem(
                id = "vv_os_battery",
                title = "关闭电池优化",
                target = "不允许 / 不限制",
                how = "长按图标 → 应用信息 → 电池 → 电池优化 →「不允许」；或 设置 → 电池 → 更多设置 → 电池优化 →「不限制」",
                intents = listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(packageUri),
                    Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
                    appDetails(packageUri)
                ),
                detectKind = DetectKind.BatteryIgnore
            ),
            SwitchItem(
                id = "vv_os_whitelist",
                title = "加入加速白名单",
                target = "已加入",
                how = "打开「i管家」→ 省电管理 → 加速白名单 → 添加心锚（防一键清理误杀）",
                intents = listOf(
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                    component("com.vivo.garbageclear", "com.vivo.garbageclear.WhiteListActivity"),
                    component("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.BgStartUpManager"),
                    appDetails(packageUri)
                )
            ),
            SwitchItem(
                id = "vv_os_freeze",
                title = "关闭智能后台冻结",
                target = "不受冻结影响",
                how = "设置 → 电池 → 更多设置 → 智能后台冻结 → 将心锚加入「不受冻结影响列表」→ 重启生效（OriginOS 5+/6）",
                intents = listOf(
                    component("com.vivo.abe", "com.vivo.applicationbehaviorengine.ui.ExcessivePowerManagerActivity"),
                    Intent(Settings.ACTION_BATTERY_SAVER_SETTINGS),
                    appDetails(packageUri)
                )
            )
        )
    )

    private fun vivoLabel(): String =
        if (
            Build.BRAND.orEmpty().contains("iqoo", true) ||
            Build.MANUFACTURER.orEmpty().contains("iqoo", true)
        ) "iQOO" else "vivo"

    // ── 三星 ───────────────────────────────────────────────────────────────

    private fun samsung(packageUri: Uri) = Content(
        family = Family.Samsung,
        displayName = "三星",
        tip = "关键：电池不受限制；侧载安装须先允许受限制设置，再开无障碍",
        switches = listOf(
            SwitchItem(
                id = "ss_unrestricted",
                title = "电池不受限制",
                target = "不受限制",
                how = "设置 → 应用程序 → 心锚 → 电池 → 不受限制（关闭电池优化）",
                intents = listOf(
                    Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).setData(packageUri),
                    appDetails(packageUri)
                ),
                detectKind = DetectKind.BatteryIgnore
            ),
            SwitchItem(
                id = "ss_sleep",
                title = "勿休眠",
                target = "不在休眠列表",
                how = "设置 → 电池 → 后台使用限制 → 勿加入休眠/深度休眠",
                intents = listOf(
                    component("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                    component("com.samsung.android.sm", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                    appDetails(packageUri)
                )
            ),
            lockTaskItem()
        )
    )

    private fun other(packageUri: Uri) = Content(
        family = Family.Other,
        displayName = Build.MANUFACTURER.orEmpty().ifBlank { "本机" },
        tip = "在设置里搜索「自启动」「后台」并允许心锚，再锁定多任务",
        switches = listOf(
            SwitchItem(
                id = "ot_autostart",
                title = "自启动 / 后台",
                target = "允许",
                how = "设置搜索「自启动」「后台运行」，允许心锚",
                intents = listOf(appDetails(packageUri))
            ),
            lockTaskItem()
        )
    )

    private fun lockTaskItem() = SwitchItem(
        id = "habit_lock",
        title = "多任务锁定",
        target = "已锁定",
        how = "最近任务里找到心锚，下拉卡片或点锁图标",
        intents = emptyList()
    )

    private fun component(pkg: String, cls: String): Intent =
        Intent().setComponent(ComponentName(pkg, cls))
}
