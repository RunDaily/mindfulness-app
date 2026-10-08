package com.life.mindfulnessapp.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.life.mindfulnessapp.domain.model.DualAppDetector
import com.life.mindfulnessapp.util.OemDualSpace
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 已安装 launcher App 的进程级目录：缓存包名 / 名称 / 图标 / 分身探测结果。
 *
 * 监控配置仍由 [GetInstalledAppsUseCase] 每次从 DB 合并，避免改限额时整机重扫。
 * 装卸/更新包名时通过广播失效并后台再暖。
 */
@Singleton
class InstalledAppsCatalog @Inject constructor(
    @ApplicationContext private val context: Context
) {
    data class Entry(
        val packageName: String,
        val appName: String,
        val icon: Drawable?,
        val suspectedCloneOfPackage: String?,
        val suspectedClonePackages: List<String>,
        val hasSystemDualInstance: Boolean
    )

    private val mutex = Mutex()
    private val warmScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var version: Int = 0

    @Volatile
    private var cachedVersion: Int = -1

    @Volatile
    private var cache: List<Entry>? = null

    @Volatile
    private var listening: Boolean = false

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            val action = intent.action ?: return
            when (action) {
                Intent.ACTION_PACKAGE_ADDED,
                Intent.ACTION_PACKAGE_REMOVED,
                Intent.ACTION_PACKAGE_CHANGED,
                Intent.ACTION_PACKAGE_REPLACED -> {
                    val pkg = intent.data?.schemeSpecificPart
                    Log.d(TAG, "invalidate after $action pkg=$pkg")
                    invalidate(rewarm = true)
                }
            }
        }
    }

    /** Application.onCreate：注册装卸监听并后台预扫。 */
    fun start() {
        startListening()
        warm()
    }

    fun warm() {
        warmScope.launch {
            runCatching { snapshot() }
                .onFailure { Log.w(TAG, "warm failed", it) }
        }
    }

    fun invalidate(rewarm: Boolean = false) {
        version++
        OemDualSpace.invalidateCache()
        if (rewarm) warm()
    }

    /**
     * 返回当前有效缓存；未命中则扫机并写入。
     * 并发调用会串行化，避免重复全量扫。
     */
    suspend fun snapshot(): List<Entry> = mutex.withLock {
        val v = version
        cache?.takeIf { cachedVersion == v }?.let { return it }
        // 持锁扫一遍，并发调用排队复用同一次结果，避免重复全量 queryIntentActivities
        val loaded = scanLocked()
        if (version == v) {
            cache = loaded
            cachedVersion = v
        }
        loaded
    }

    suspend fun find(packageName: String): Entry? =
        snapshot().firstOrNull { it.packageName == packageName }

    /** DualApp 探测用的轻量列表（与上次扫描一致）。 */
    suspend fun launcherForDetect(): List<DualAppDetector.LauncherApp> =
        snapshot().map {
            DualAppDetector.LauncherApp(packageName = it.packageName, appName = it.appName)
        }

    private fun startListening() {
        if (listening) return
        listening = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.RECEIVER_NOT_EXPORTED
        } else {
            0
        }
        ContextCompat.registerReceiver(context, packageReceiver, filter, flags)
    }

    private fun scanLocked(): List<Entry> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val ownPackage = context.packageName
        val launcherApps = pm.queryIntentActivities(intent, PackageManager.GET_META_DATA)
        val launcherForDetect = launcherApps
            .asSequence()
            .filter { it.activityInfo.packageName != ownPackage }
            .distinctBy { it.activityInfo.packageName }
            .map {
                DualAppDetector.LauncherApp(
                    packageName = it.activityInfo.packageName,
                    appName = it.loadLabel(pm).toString()
                )
            }
            .toList()

        return launcherForDetect.map { launcher ->
            val pkg = launcher.packageName
            Entry(
                packageName = pkg,
                appName = launcher.appName,
                icon = try {
                    pm.getApplicationIcon(pkg)
                } catch (_: PackageManager.NameNotFoundException) {
                    null
                },
                suspectedCloneOfPackage = DualAppDetector.findPrimaryPackage(
                    installed = launcherForDetect,
                    pkg = pkg,
                    label = launcher.appName
                ),
                suspectedClonePackages = DualAppDetector.findClonePackages(
                    installed = launcherForDetect,
                    primaryPackage = pkg,
                    primaryLabel = launcher.appName
                ),
                hasSystemDualInstance = OemDualSpace.hasSystemDualInstance(context, pkg)
            )
        }
    }

    companion object {
        private const val TAG = "InstalledAppsCatalog"
    }
}
