package com.life.mindfulnessapp

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.life.mindfulnessapp.billing.BillingManager
import dagger.hilt.android.HiltAndroidApp
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

    override fun onCreate() {
        super.onCreate()
        // 启动时连接 Google Play Billing，提前加载产品价格
        billingManager.connect()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
