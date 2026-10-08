package com.life.mindfulnessapp

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.life.mindfulnessapp.billing.BillingManager
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.InstalledAppsCatalog
import com.life.mindfulnessapp.data.repository.QuoteRepository
import com.life.mindfulnessapp.display.DisplayGrayscaleController
import com.life.mindfulnessapp.overlay.OverlayManager
import com.life.mindfulnessapp.pay.WeChatPayHelper
import com.life.mindfulnessapp.util.OemDualSpace
import com.life.mindfulnessapp.util.QuotePushScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Application 类。
 *
 * 实现 Configuration.Provider 以手动初始化 WorkManager，
 * 使 WorkManager 能够感知 Hilt 注入（配合 AndroidManifest 中禁用了 WorkManager 的自动 ContentProvider 初始化）。
 * 这是 ServiceWatchdogWorker 使用 @HiltWorker 的必要前提。
 */
@HiltAndroidApp
class MindfulnessApplication : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    /** Google Play Billing 客户端，在 App 启动时建立连接以尽早加载产品信息 */
    @Inject
    lateinit var billingManager: BillingManager

    @Inject
    lateinit var quoteRepository: QuoteRepository

    @Inject
    lateinit var appPreferences: AppPreferences

    @Inject
    lateinit var searchDeepLinkRepository: com.life.mindfulnessapp.data.repository.SearchDeepLinkRepository

    @Inject
    lateinit var displayGrayscaleController: DisplayGrayscaleController

    @Inject
    lateinit var overlayManager: OverlayManager

    @Inject
    lateinit var installedAppsCatalog: InstalledAppsCatalog

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var startedActivities = 0

    override fun onCreate() {
        super.onCreate()
        billingManager.connect()
        // 后台预扫已安装 App，后续选 App / 探索页命中进程缓存
        installedAppsCatalog.start()
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) {
                startedActivities++
                if (startedActivities == 1) overlayManager.setHostAppInForeground(true)
            }
            override fun onActivityStopped(activity: Activity) {
                startedActivities = (startedActivities - 1).coerceAtLeast(0)
                if (startedActivities == 0) overlayManager.setHostAppInForeground(false)
            }
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
            override fun onActivityResumed(activity: Activity) = Unit
            override fun onActivityPaused(activity: Activity) = Unit
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
            override fun onActivityDestroyed(activity: Activity) = Unit
        })
        // 异常退出可能把系统灰度留在整机：启动时立刻恢复
        displayGrayscaleController.recoverIfOrphaned()
        if (WeChatPayHelper.isConfigured()) {
            WeChatPayHelper.createApi(this)
        }
        // 定时格言推送已下线：关开关并清闹钟
        appPreferences.retireScheduledQuotePush()
        QuotePushScheduler.cancel(this)
        appScope.launch {
            quoteRepository.syncAll()
        }
        appScope.launch {
            searchDeepLinkRepository.sync()
        }
        if (BuildConfig.DEBUG) {
            appScope.launch {
                OemDualSpace.invalidateCache()
                val ids = OemDualSpace.dualUserIds(this@MindfulnessApplication)
                val mm = OemDualSpace.hasSystemDualInstance(
                    this@MindfulnessApplication, "com.tencent.mm"
                )
                val qq = OemDualSpace.hasSystemDualInstance(
                    this@MindfulnessApplication, "com.tencent.mobileqq"
                )
                Log.i("OemDualSpace", "boot probe dualUsers=$ids wechatTwin=$mm qqTwin=$qq")
                val monitored = setOf("com.tencent.mm", "com.tencent.mobileqq")
                val usFg = OemDualSpace.foregroundFromDualUsageStats(
                    this@MindfulnessApplication,
                    monitored
                )
                val procFg = OemDualSpace.foregroundFromProc(ids, monitored)
                val combo = OemDualSpace.foregroundMonitoredInDualSpace(
                    this@MindfulnessApplication,
                    monitored
                )
                Log.i(
                    "OemDualSpace",
                    "boot probe dualUsageFg=$usFg procFg=$procFg comboFg=$combo"
                )
            }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
