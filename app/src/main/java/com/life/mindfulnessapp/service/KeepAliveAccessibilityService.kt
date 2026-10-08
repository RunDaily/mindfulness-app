package com.life.mindfulnessapp.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.view.inputmethod.InputMethodManager
import com.life.mindfulnessapp.util.KeepAliveAlarmScheduler
import com.life.mindfulnessapp.util.KeepAliveRestarter

/**
 * 可选「系统级」保活：用户开启无障碍后，系统会绑定本服务并倾向于保持进程存活。
 *
 * 同时：用 [TYPE_WINDOW_STATE_CHANGED] 即时把前台包名推给 [MonitorForegroundService]，
 * 让意图门在进入受监控 App 时立刻盖上（对齐 AppBlock：不等 UsageStats / 首次触摸）。
 *
 * 另提供 [goHome] / [openRecents]：模拟系统键。
 * [onKeyEvent]：拦截页期间吞掉返回、回桌面、最近任务，不交给系统。
 */
class KeepAliveAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "KeepAliveA11y"
        private const val RESTART_THROTTLE_MS = 30_000L
        private const val WINDOWS_CHANGED_THROTTLE_MS = 120L

        @Volatile
        private var instance: KeepAliveAccessibilityService? = null

        fun instanceOrNull(): KeepAliveAccessibilityService? = instance

        /** 系统壳等：不当作用户目标 App 的前台切换（输入法另用 IMM 动态判断） */
        private val IGNORED_PACKAGES = setOf(
            "com.android.systemui",
            "com.android.intentresolver",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.vpndialogs",
            "com.android.phone",
            "com.android.server.telecom",
            "android"
        )

        /** 音量条 / 控制中心等厂商浮层：包名不一定是 systemui */
        private fun isTransientSystemOverlayHint(pkg: String): Boolean {
            val p = pkg.lowercase()
            return p.contains("systemui") ||
                p.contains("volume") ||
                p.contains("flashlight") ||
                p.contains("controlcenter") ||
                p.contains("miui.systemui") ||
                p.endsWith(".ops.systemui")
        }

        fun isEnabled(context: Context): Boolean {
            val enabled = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            val expected = ComponentName(context, KeepAliveAccessibilityService::class.java)
            return enabled.split(':').any { raw ->
                val cn = ComponentName.unflattenFromString(raw.trim()) ?: return@any false
                cn.packageName == expected.packageName && cn.className == expected.className
            }
        }

        fun openSettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }

        /**
         * 模拟 Home 键回桌面。服务未连接时返回 false，由调用方改走 Intent。
         */
        fun goHome(): Boolean {
            val svc = instance ?: return false
            return try {
                val ok = svc.performGlobalAction(GLOBAL_ACTION_HOME)
                Log.d(TAG, "GLOBAL_ACTION_HOME => $ok")
                ok
            } catch (e: Exception) {
                Log.e(TAG, "GLOBAL_ACTION_HOME 失败", e)
                false
            }
        }

        /** 拦截页期间进程列表被手势打开时，退回拦截页。 */
        fun pressBack(): Boolean {
            val svc = instance ?: return false
            return try {
                svc.performGlobalAction(GLOBAL_ACTION_BACK)
            } catch (e: Exception) {
                Log.e(TAG, "GLOBAL_ACTION_BACK 失败", e)
                false
            }
        }
        fun openRecents(): Boolean {
            val svc = instance ?: return false
            return try {
                val ok = svc.performGlobalAction(GLOBAL_ACTION_RECENTS)
                Log.d(TAG, "GLOBAL_ACTION_RECENTS => $ok")
                ok
            } catch (e: Exception) {
                Log.e(TAG, "GLOBAL_ACTION_RECENTS 失败", e)
                false
            }
        }

        fun looksLikeRecentsClass(className: CharSequence?): Boolean {
            val name = className?.toString()?.lowercase() ?: return false
            return name.contains("recent") ||
                name.contains("taskview") ||
                name.contains("taskstack") ||
                name.contains("taskcontainer") ||
                name.contains("overview") ||
                name.contains("quickstep") ||
                name.contains("taskswitcher") ||
                name.contains("absrecents") ||
                name.contains("secondaryhomesheet") ||
                name.contains("seascape") ||
                name.contains("recentsview") ||
                name.contains("recentscontainer") ||
                name.contains("navstub") ||
                name.contains("homeswitch") ||
                name.contains("taskholder") ||
                name.contains("recentsimpl")
        }
    }

    @Volatile
    private var lastEnsureAtMs = 0L

    @Volatile
    private var lastHintPkg: String? = null

    @Volatile
    private var lastHintAtMs = 0L

    @Volatile
    private var lastWindowsChangedAtMs = 0L

    /** 缓存输入法包名，避免每次事件都扫 IMM */
    @Volatile
    private var imePackages: Set<String> = emptySet()

    @Volatile
    private var imePackagesCachedAtMs = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        serviceInfo = serviceInfo?.apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.DEFAULT or
                AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            // 尽快收到窗口切换（原 3s 会拖慢意图门）
            notificationTimeout = 0
        }
        Log.i(TAG, "无障碍保活已连接，拉起监控服务")
        refreshImePackages()
        KeepAliveRestarter.ensureRunning(this)
        lastEnsureAtMs = System.currentTimeMillis()
    }

    /**
     * 拦截页期间吞掉返回、回桌面、最近任务。
     * 三键不交给系统，只能走页上的「继续 / 离开」。全面屏手势没有这三个键，仍靠窗口事件把进程列表拉回来。
     */
    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (!MonitorForegroundService.hasActiveInterceptForRecentsHint()) {
            return false
        }
        return when (event.keyCode) {
            KeyEvent.KEYCODE_HOME,
            KeyEvent.KEYCODE_APP_SWITCH,
            KeyEvent.KEYCODE_BACK -> true
            else -> false
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        when (event?.eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                val pkg = event.packageName?.toString()?.takeIf { it.isNotBlank() }
                // 只认进程列表窗口本身。桌面/systemui 在门盖着时也会报到，
                // 若一律卸层会拆掉又挂上，表现为门不停跳动。
                if (looksLikeRecentsClass(event.className) &&
                    MonitorForegroundService.hasActiveInterceptForRecentsHint()
                ) {
                    MonitorForegroundService.notifyRecentsOpened()
                    return
                }
                if (pkg != null && shouldHintPackage(pkg)) {
                    val now = System.currentTimeMillis()
                    // 同包极短去抖，避免同屏多窗口事件刷爆；跨包立即推送
                    if (pkg != lastHintPkg || now - lastHintAtMs >= 80L) {
                        lastHintPkg = pkg
                        lastHintAtMs = now
                        MonitorForegroundService.notifyForegroundHint(pkg)
                    }
                }
            }
            AccessibilityEvent.TYPE_WINDOWS_CHANGED -> {
                val now = System.currentTimeMillis()
                if (now - lastWindowsChangedAtMs >= WINDOWS_CHANGED_THROTTLE_MS) {
                    lastWindowsChangedAtMs = now
                    MonitorForegroundService.requestImmediateForegroundCheck()
                }
            }
        }
        val now = System.currentTimeMillis()
        if (now - lastEnsureAtMs < RESTART_THROTTLE_MS) return
        lastEnsureAtMs = now
        KeepAliveRestarter.ensureRunning(this)
    }

    private fun shouldHintPackage(pkg: String): Boolean {
        if (pkg == packageName) return false
        if (pkg in IGNORED_PACKAGES) return false
        if (isTransientSystemOverlayHint(pkg)) return false
        refreshImePackagesIfStale()
        if (pkg in imePackages) return false
        return true
    }

    private fun refreshImePackagesIfStale() {
        val now = System.currentTimeMillis()
        if (now - imePackagesCachedAtMs < 60_000L && imePackages.isNotEmpty()) return
        refreshImePackages()
    }

    private fun refreshImePackages() {
        imePackages = try {
            val imm = getSystemService(INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList
                ?.mapNotNull { it.packageName }
                ?.toSet()
                .orEmpty()
        } catch (_: Exception) {
            emptySet()
        }
        imePackagesCachedAtMs = System.currentTimeMillis()
    }

    override fun onInterrupt() {
        Log.w(TAG, "无障碍保活被中断")
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        Log.w(TAG, "无障碍保活服务销毁，调度短延迟重启")
        KeepAliveAlarmScheduler.scheduleImmediateRestart(this)
        super.onDestroy()
    }
}
