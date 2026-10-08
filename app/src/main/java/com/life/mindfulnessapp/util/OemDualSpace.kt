package com.life.mindfulnessapp.util

import android.app.ActivityManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import java.io.File
import java.lang.reflect.InvocationTargetException

/**
 * OEM「应用分身」多为同包名 + 独立 Android User：
 * - 华为 / 荣耀：userId **128**（用户名常为「分身应用」）
 * - 小米 / Redmi：userId **999**
 * - 三星 Dual Messenger：userId **95**（用户名常为 `DUAL_APP`）
 *
 * 桌面 [PackageManager.queryIntentActivities] 只看当前 user，扫不到这类分身。
 * 前台探测受系统隔离限制，拦截依赖无障碍窗口事件 + 抢回机主焦点。
 */
object OemDualSpace {

    private const val TAG = "OemDualSpace"

    /** 常见分身空间 userId（不含 Secure Folder 等隐私空间） */
    private val KNOWN_DUAL_USER_IDS = intArrayOf(128, 999, 95)

    private val DUAL_USER_NAME_HINTS = listOf(
        "分身", "dual", "twin", "clone", "平行", "双开", "cloneapp",
        "dual_app", "dualapp", "dual messenger", "双开应用"
    )

    /** 明确不是「应用分身」的 profile（勿当成双开去拦） */
    private val EXCLUDED_USER_NAME_HINTS = listOf(
        "secure folder", "安全文件夹", "私人空间", "privatespace", "work profile"
    )

    @Volatile
    private var cachedDualUserIds: List<Int>? = null

    @Volatile
    private var cacheAtElapsedMs: Long = 0L

    /** packageName → 是否在分身空间安装（短缓存） */
    private val installedAsDualCache = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    private const val CACHE_TTL_MS = 60_000L

    fun invalidateCache() {
        cachedDualUserIds = null
        cacheAtElapsedMs = 0L
        installedAsDualCache.clear()
    }

    fun dualUserIds(context: Context): List<Int> {
        val now = android.os.SystemClock.elapsedRealtime()
        val cached = cachedDualUserIds
        if (cached != null && now - cacheAtElapsedMs < CACHE_TTL_MS) return cached
        val discovered = discoverDualUserIds(context)
        cachedDualUserIds = discovered
        cacheAtElapsedMs = now
        return discovered
    }

    /** 该包是否在任一系统分身空间中已安装（同包名双开） */
    fun hasSystemDualInstance(context: Context, packageName: String): Boolean {
        installedAsDualCache[packageName]?.let { return it }
        val myUser = Process.myUid() / 100_000
        var found = false
        val dualIds = dualUserIds(context)
        for (userId in dualIds) {
            if (userId == myUser) continue
            if (isPackageInstalledAsUser(context, packageName, userId)) {
                found = true
                break
            }
        }
        // 反射查包失败时：进程列表里是否已有该包的分身 user 进程
        if (!found) {
            found = hasRunningProcessInDualSpace(context, packageName, dualIds)
        }
        installedAsDualCache[packageName] = found
        if (found) {
            Log.i(TAG, "系统分身已安装: $packageName dualUsers=$dualIds")
        }
        return found
    }

    /**
     * 若分身空间里有受监控包处于前台，返回其包名。
     * UsageStats 只覆盖机主 user 时的兜底；组合 RunningTasks / ATM / 进程 /proc。
     */
    @Suppress("DEPRECATION")
    fun foregroundMonitoredInDualSpace(
        context: Context,
        monitoredPackages: Set<String>
    ): String? {
        if (monitoredPackages.isEmpty()) return null
        val dualIds = dualUserIds(context).ifEmpty {
            defaultProbeUserIds(context)
        }
        if (dualIds.isEmpty()) return null

        // 1) RunningTasks：部分华为 ROM 能看到分身栈顶 Activity
        topMonitoredFromRunningTasks(context, monitoredPackages, dualIds)?.let { return it }

        // 2) ActivityTaskManager 焦点栈（反射）
        topMonitoredFromFocusedStack(monitoredPackages, dualIds)?.let { return it }

        // 3) /proc：机主侧 runningAppProcesses 常扫不到 u128；oom_score_adj≤100 近似前台
        foregroundFromProc(dualIds, monitoredPackages)?.let { return it }

        // 4) runningAppProcesses：放宽到 VISIBLE（部分 ROM 对跨 user 只给到 100）
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val procs = try {
            am.runningAppProcesses
        } catch (_: SecurityException) {
            null
        } ?: return null

        var bestPkg: String? = null
        var bestImportance = Int.MAX_VALUE
        val visibleCap = ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE
        for (proc in procs) {
            val userId = proc.uid / 100_000
            if (userId !in dualIds) continue
            if (proc.importance > visibleCap) continue
            val procName = proc.processName ?: continue
            if (':' in procName) continue // 跳过 :push 等子进程
            val pkg = proc.pkgList?.firstOrNull()
                ?: procName.substringBefore(':')
            if (pkg !in monitoredPackages) continue
            if (proc.importance <= bestImportance) {
                bestImportance = proc.importance
                bestPkg = pkg
            }
        }
        return bestPkg
    }

    /**
     * 读 /proc/<pid>/status + oom_score_adj，找分身 user 上主进程的前台候选。
     * Android 高版本可能 hidepid 读不到，失败则静默返回 null。
     */
    fun foregroundFromProc(
        dualIds: List<Int>,
        monitoredPackages: Set<String>
    ): String? {
        if (dualIds.isEmpty() || monitoredPackages.isEmpty()) return null
        val procRoot = File("/proc")
        val dirs = try {
            procRoot.listFiles() ?: return null
        } catch (_: Exception) {
            return null
        }
        var bestPkg: String? = null
        var bestOom = Int.MAX_VALUE
        for (dir in dirs) {
            val pid = dir.name.toIntOrNull() ?: continue
            if (pid <= 1) continue
            val status = try {
                File(dir, "status").takeIf { it.canRead() }?.readText()
            } catch (_: Exception) {
                null
            } ?: continue
            val uid = status.lineSequence()
                .firstOrNull { it.startsWith("Uid:") }
                ?.split(Regex("\\s+"))
                ?.getOrNull(1)
                ?.toIntOrNull()
                ?: continue
            val userId = uid / 100_000
            if (userId !in dualIds) continue
            val cmdline = try {
                File(dir, "cmdline").takeIf { it.canRead() }?.readBytes()
                    ?.toString(Charsets.UTF_8)
                    ?.replace('\u0000', ' ')
                    ?.trim()
            } catch (_: Exception) {
                null
            } ?: continue
            val procName = cmdline.substringBefore(' ')
            if (procName.isBlank() || ':' in procName) continue
            val pkg = procName.substringBefore(':')
            if (pkg !in monitoredPackages) continue
            val oom = try {
                File(dir, "oom_score_adj").takeIf { it.canRead() }?.readText()?.trim()?.toIntOrNull()
            } catch (_: Exception) {
                null
            } ?: continue
            // 0=前台，约 100=可见；缓存通常 800+
            if (oom > 100) continue
            if (oom < bestOom) {
                bestOom = oom
                bestPkg = pkg
            }
        }
        if (bestPkg != null) {
            Log.d(TAG, "/proc 分身前台: $bestPkg oom=$bestOom dualUsers=$dualIds")
        }
        return bestPkg
    }

    /**
     * 打开系统分身空间中的主 Activity（确认进入后把用户带回分身，而非机主侧本体）。
     */
    fun launchSystemDualInstance(context: Context, packageName: String): Boolean {
        val dualIds = dualUserIds(context).ifEmpty { defaultProbeUserIds(context) }
        if (dualIds.isEmpty()) return false
        val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
        for (userId in dualIds) {
            val handle = userHandleOf(userId) ?: continue
            val activities = try {
                launcherApps.getActivityList(packageName, handle)
            } catch (e: Exception) {
                Log.d(TAG, "launchSystemDual getActivityList user=$userId: ${e.javaClass.simpleName}")
                null
            }
            val info = activities?.firstOrNull() ?: continue
            val component = ComponentName(info.componentName.packageName, info.componentName.className)
            return try {
                launcherApps.startMainActivity(component, handle, null, null)
                Log.i(TAG, "已启动系统分身: $packageName user=$userId")
                true
            } catch (e: Exception) {
                Log.w(TAG, "startMainActivity 分身失败 user=$userId: ${e.message}")
                false
            }
        }
        return false
    }

    @Suppress("DEPRECATION")
    private fun topMonitoredFromRunningTasks(
        context: Context,
        monitoredPackages: Set<String>,
        dualIds: List<Int>
    ): String? {
        val am = context.getSystemService(ActivityManager::class.java) ?: return null
        val tasks = try {
            am.getRunningTasks(8)
        } catch (_: Exception) {
            null
        } ?: return null
        for (task in tasks) {
            val userId = readTaskUserId(task) ?: continue
            if (userId !in dualIds) continue
            val top = task.topActivity ?: task.baseActivity ?: continue
            val pkg = top.packageName
            if (pkg in monitoredPackages) {
                Log.d(TAG, "RunningTasks 分身前台: $pkg user=$userId")
                return pkg
            }
        }
        return null
    }

    private fun readTaskUserId(task: ActivityManager.RunningTaskInfo): Int? {
        // API 29+ 有 public userId；旧 ROM 用反射
        try {
            val field = task.javaClass.getField("userId")
            return field.getInt(task)
        } catch (_: Exception) {
        }
        try {
            val m = task.javaClass.methods.firstOrNull {
                it.name == "getUserId" && it.parameterCount == 0
            }
            return m?.invoke(task) as? Int
        } catch (_: Exception) {
        }
        return null
    }

    /**
     * 反射 ActivityTaskManager / IActivityManager 焦点栈，
     * 读取跨 user 焦点 Activity（华为分身前台兜底）。
     */
    @Volatile
    private var atmReflectFailedLogged = false

    private fun topMonitoredFromFocusedStack(
        monitoredPackages: Set<String>,
        dualIds: List<Int>
    ): String? {
        val service = resolveActivityService() ?: return null
        // Prefer getFocusedStackInfo()
        val focused = tryInvokeNoArg(service, "getFocusedStackInfo")
        parseStackInfoForMonitored(focused, monitoredPackages, dualIds)?.let { return it }

        // Fallback: getAllStackInfos()
        val all = tryInvokeNoArg(service, "getAllStackInfos") as? List<*>
        all?.forEach { stack ->
            parseStackInfoForMonitored(stack, monitoredPackages, dualIds)?.let { return it }
        }
        return null
    }

    private fun resolveActivityService(): Any? {
        // 1) ActivityTaskManager.getService()
        try {
            val atmClass = Class.forName("android.app.ActivityTaskManager")
            val getService = atmClass.methods.firstOrNull {
                it.name == "getService" && it.parameterCount == 0
            }
            if (getService != null) {
                val svc = getService.invoke(null)
                if (svc != null) return svc
            }
        } catch (_: Exception) {
        }
        // 2) ServiceManager → activity_task / activity
        return try {
            val sm = Class.forName("android.os.ServiceManager")
            val getService = sm.getMethod("getService", String::class.java)
            for (name in listOf("activity_task", "activity")) {
                val binder = getService.invoke(null, name) ?: continue
                val stubNames = listOf(
                    "android.app.IActivityTaskManager\$Stub",
                    "android.app.IActivityManager\$Stub"
                )
                for (stubName in stubNames) {
                    try {
                        val stub = Class.forName(stubName)
                        val asInterface = stub.getMethod(
                            "asInterface",
                            Class.forName("android.os.IBinder")
                        )
                        val svc = asInterface.invoke(null, binder)
                        if (svc != null) return svc
                    } catch (_: Exception) {
                    }
                }
            }
            if (!atmReflectFailedLogged) {
                atmReflectFailedLogged = true
                Log.d(TAG, "Activity 服务反射不可用")
            }
            null
        } catch (e: Exception) {
            if (!atmReflectFailedLogged) {
                atmReflectFailedLogged = true
                Log.d(TAG, "Activity 服务反射失败: ${e.javaClass.simpleName}")
            }
            null
        }
    }

    private fun tryInvokeNoArg(target: Any, methodName: String): Any? {
        return try {
            val m = target.javaClass.methods.firstOrNull {
                it.name == methodName && it.parameterCount == 0
            } ?: return null
            m.invoke(target)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * UsageStatsManager.queryEventsForUser（隐藏 API）：
     * 直接查分身 user 的前台事件。
     */
    fun foregroundFromDualUsageStats(
        context: Context,
        monitoredPackages: Set<String>
    ): String? {
        if (monitoredPackages.isEmpty()) return null
        val dualIds = dualUserIds(context).ifEmpty { defaultProbeUserIds(context) }
        if (dualIds.isEmpty()) return null
        val usm = context.getSystemService(android.app.usage.UsageStatsManager::class.java)
            ?: return null
        val now = System.currentTimeMillis()
        val begin = now - 5 * 60_000L
        val queryForUser = try {
            android.app.usage.UsageStatsManager::class.java.getMethod(
                "queryEventsForUser",
                Long::class.javaPrimitiveType,
                Long::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
        } catch (_: Exception) {
            null
        } ?: return null

        for (userId in dualIds) {
            val events = try {
                queryForUser.invoke(usm, begin, now, userId) as? android.app.usage.UsageEvents
            } catch (e: Exception) {
                val cause = (e as? InvocationTargetException)?.cause ?: e
                Log.d(
                    TAG,
                    "queryEventsForUser($userId) 失败: ${cause.javaClass.simpleName}: ${cause.message}"
                )
                null
            } ?: continue
            val event = android.app.usage.UsageEvents.Event()
            val state = mutableMapOf<String, Pair<Boolean, Long>>()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName ?: continue
                when (event.eventType) {
                    android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND,
                    android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED -> {
                        val prev = state[pkg]
                        if (prev == null || event.timeStamp > prev.second) {
                            state[pkg] = true to event.timeStamp
                        }
                    }
                    android.app.usage.UsageEvents.Event.MOVE_TO_BACKGROUND,
                    android.app.usage.UsageEvents.Event.ACTIVITY_PAUSED,
                    android.app.usage.UsageEvents.Event.ACTIVITY_STOPPED -> {
                        val prev = state[pkg]
                        if (prev == null || event.timeStamp > prev.second) {
                            state[pkg] = false to event.timeStamp
                        }
                    }
                }
            }
            val fg = state.entries
                .filter { it.value.first && it.key in monitoredPackages }
                .maxByOrNull { it.value.second }
                ?.key
            if (fg != null) {
                Log.d(TAG, "UsageStats 分身前台: $fg user=$userId")
                return fg
            }
        }
        return null
    }

    private fun parseStackInfoForMonitored(
        stackInfo: Any?,
        monitoredPackages: Set<String>,
        dualIds: List<Int>
    ): String? {
        if (stackInfo == null) return null
        val userId = try {
            val f = stackInfo.javaClass.getField("userId")
            f.getInt(stackInfo)
        } catch (_: Exception) {
            try {
                stackInfo.javaClass.methods.firstOrNull {
                    it.name == "getUserId" && it.parameterCount == 0
                }?.invoke(stackInfo) as? Int
            } catch (_: Exception) {
                null
            }
        } ?: return null
        if (userId !in dualIds) return null

        val top = try {
            stackInfo.javaClass.getField("topActivity").get(stackInfo)
        } catch (_: Exception) {
            null
        } ?: return null
        val pkg = try {
            top.javaClass.getMethod("getPackageName").invoke(top) as? String
        } catch (_: Exception) {
            null
        } ?: return null
        if (pkg in monitoredPackages) {
            Log.d(TAG, "FocusedStack 分身前台: $pkg user=$userId")
            return pkg
        }
        return null
    }

    private fun defaultProbeUserIds(context: Context): List<Int> {
        val myUser = Process.myUid() / 100_000
        val brand = Build.MANUFACTURER.lowercase()
        val ids = linkedSetOf<Int>()
        when {
            brand.contains("huawei") || brand.contains("honor") -> ids += 128
            brand.contains("xiaomi") || brand.contains("redmi") || brand.contains("poco") -> ids += 999
            brand.contains("samsung") -> ids += 95
            else -> {
                ids += 128
                ids += 999
                ids += 95
            }
        }
        return ids.filter { it != myUser }
    }

    private fun discoverDualUserIds(context: Context): List<Int> {
        val myUser = Process.myUid() / 100_000
        val found = linkedSetOf<Int>()

        // 1) 公开 API：当前用户关联的 profiles（三星 Dual Messenger 常为 95）
        try {
            val um = context.getSystemService(UserManager::class.java)
            um?.userProfiles?.forEach { handle ->
                val id = userIdFromHandle(handle) ?: return@forEach
                if (id == myUser || id < 0) return@forEach
                // Dual Messenger≈95；Secure Folder 多为 150+，勿纳入
                if (id in KNOWN_DUAL_USER_IDS || id in 90..99) {
                    found += id
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "getUserProfiles 失败: ${e.javaClass.simpleName}")
        }

        // 2) 隐藏 getUsers（需权限，多数机型会失败）
        try {
            val um = context.getSystemService(UserManager::class.java)
            val method = UserManager::class.java.methods.firstOrNull {
                it.name == "getUsers" && it.parameterCount == 0
            } ?: UserManager::class.java.methods.firstOrNull {
                it.name == "getUsers" && it.parameterCount == 1
            }
            if (um != null && method != null) {
                val raw = if (method.parameterCount == 0) {
                    method.invoke(um)
                } else {
                    method.invoke(um, false)
                }
                val list = raw as? List<*>
                list?.forEach { userInfo ->
                    if (userInfo == null) return@forEach
                    val id = readUserId(userInfo) ?: return@forEach
                    if (id == myUser || id < 0) return@forEach
                    val name = readUserName(userInfo).orEmpty()
                    if (isExcludedUserName(name)) return@forEach
                    val looksDual = DUAL_USER_NAME_HINTS.any { name.contains(it, ignoreCase = true) } ||
                        id in KNOWN_DUAL_USER_IDS
                    if (looksDual) found += id
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "getUsers 不可用: ${e.javaClass.simpleName}: ${e.message}")
        }

        // 3) 品牌默认 id + 已知 id：LauncherApps / 进程可达则纳入
        for (userId in defaultProbeUserIds(context) + KNOWN_DUAL_USER_IDS.toList()) {
            if (userId == myUser || userId in found) continue
            if (probeUserReachable(context, userId) || hasAnyProcessForUser(context, userId)) {
                found += userId
            }
        }

        if (found.isNotEmpty()) {
            Log.i(TAG, "系统分身空间 userId=$found manufacturer=${Build.MANUFACTURER}")
        } else {
            Log.w(TAG, "未发现系统分身空间（manufacturer=${Build.MANUFACTURER}）")
        }
        return found.toList()
    }

    private fun isExcludedUserName(name: String): Boolean =
        EXCLUDED_USER_NAME_HINTS.any { name.contains(it, ignoreCase = true) }

    private fun userIdFromHandle(handle: UserHandle): Int? {
        return try {
            val m = UserHandle::class.java.methods.firstOrNull {
                it.name == "getIdentifier" && it.parameterCount == 0
            }
            m?.invoke(handle) as? Int
        } catch (_: Exception) {
            null
        }
    }

    private fun probeUserReachable(context: Context, userId: Int): Boolean {
        val probes = listOf(
            "android",
            "com.android.settings",
            "com.huawei.systemmanager",
            "com.miui.securitycenter",
            "com.samsung.android.lool",
            "com.samsung.android.app.telephonyui"
        )
        if (probes.any { isPackageInstalledAsUser(context, it, userId) }) return true
        // 列出该 user 桌面项：华为分身空间常对三方可读
        return try {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            val handle = userHandleOf(userId) ?: return false
            val activities = launcherApps.getActivityList(null, handle)
            !activities.isNullOrEmpty()
        } catch (e: Exception) {
            Log.d(TAG, "probeUserReachable LauncherApps user=$userId: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun hasAnyProcessForUser(context: Context, userId: Int): Boolean {
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val procs = try {
            am.runningAppProcesses
        } catch (_: Exception) {
            null
        } ?: return false
        return procs.any { it.uid / 100_000 == userId }
    }

    private fun hasRunningProcessInDualSpace(
        context: Context,
        packageName: String,
        dualIds: List<Int>
    ): Boolean {
        val ids = dualIds.ifEmpty { defaultProbeUserIds(context) }
        if (ids.isEmpty()) return false
        val am = context.getSystemService(ActivityManager::class.java) ?: return false
        val procs = try {
            am.runningAppProcesses
        } catch (_: Exception) {
            null
        } ?: return false
        return procs.any { proc ->
            val userId = proc.uid / 100_000
            if (userId !in ids) return@any false
            val pkg = proc.pkgList?.firstOrNull()
                ?: proc.processName.substringBefore(':')
            pkg == packageName
        }
    }

    fun isPackageInstalledAsUser(
        context: Context,
        packageName: String,
        userId: Int
    ): Boolean {
        val pm = context.packageManager
        try {
            val method = PackageManager::class.java.getMethod(
                "getApplicationInfoAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(pm, packageName, 0, userId)
            return true
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            if (cause is SecurityException) {
                Log.d(TAG, "getApplicationInfoAsUser 无权限 user=$userId pkg=$packageName")
            }
        } catch (_: NoSuchMethodException) {
        } catch (e: Exception) {
            Log.d(TAG, "getApplicationInfoAsUser 失败: ${e.javaClass.simpleName}")
        }

        try {
            val method = PackageManager::class.java.getMethod(
                "getPackageUidAsUser",
                String::class.java,
                Int::class.javaPrimitiveType
            )
            val uid = method.invoke(pm, packageName, userId) as Int
            return uid >= 0
        } catch (e: InvocationTargetException) {
            val cause = e.cause
            if (cause is SecurityException) {
                Log.d(TAG, "getPackageUidAsUser 无权限 user=$userId pkg=$packageName")
            }
        } catch (_: Exception) {
        }

        try {
            val method = PackageManager::class.java.getMethod(
                "getPackageInfoAsUser",
                String::class.java,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            method.invoke(pm, packageName, 0, userId)
            return true
        } catch (_: Exception) {
        }

        // LauncherApps：部分 ROM 允许查询分身空间桌面项（无需 MANAGE_USERS）
        if (isPackageVisibleViaLauncherApps(context, packageName, userId)) {
            return true
        }

        return false
    }

    private fun isPackageVisibleViaLauncherApps(
        context: Context,
        packageName: String,
        userId: Int
    ): Boolean {
        return try {
            val launcherApps = context.getSystemService(LauncherApps::class.java) ?: return false
            val handle = userHandleOf(userId) ?: return false
            val activities = launcherApps.getActivityList(packageName, handle)
            activities != null && activities.isNotEmpty()
        } catch (e: Exception) {
            Log.d(TAG, "LauncherApps.getActivityList 失败 user=$userId: ${e.javaClass.simpleName}")
            false
        }
    }

    private fun userHandleOf(userId: Int): UserHandle? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                UserHandle.getUserHandleForUid(userId * 100_000)
            } else {
                val of = UserHandle::class.java.getMethod("of", Int::class.javaPrimitiveType)
                of.invoke(null, userId) as UserHandle
            }
        } catch (_: Exception) {
            try {
                val ctor = UserHandle::class.java.getConstructor(Int::class.javaPrimitiveType)
                ctor.newInstance(userId)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun readUserId(userInfo: Any): Int? {
        return try {
            val m = userInfo.javaClass.methods.firstOrNull {
                it.name == "getUserHandle" && it.parameterCount == 0
            }
            if (m != null) {
                val handle = m.invoke(userInfo)
                val idMethod = handle?.javaClass?.methods?.firstOrNull {
                    it.name == "getIdentifier" && it.parameterCount == 0
                }
                (idMethod?.invoke(handle) as? Int)
            } else {
                val field = userInfo.javaClass.getField("id")
                field.getInt(userInfo)
            }
        } catch (_: Exception) {
            try {
                userInfo.javaClass.getField("id").getInt(userInfo)
            } catch (_: Exception) {
                null
            }
        }
    }

    private fun readUserName(userInfo: Any): String? {
        return try {
            val m = userInfo.javaClass.methods.firstOrNull {
                it.name == "getName" && it.parameterCount == 0
            }
            (m?.invoke(userInfo) as? String)
                ?: userInfo.javaClass.getField("name").get(userInfo) as? String
        } catch (_: Exception) {
            null
        }
    }
}
