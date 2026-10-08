package com.life.mindfulnessapp.util

import android.app.ActivityManager
import android.content.Context
import android.util.Log

/**
 * 判断第三方包进程是否已不存在。
 *
 * 不用 [ActivityManager.getRunningAppProcesses]：Android 11+ 基本只能看到本应用，
 * 会把「回桌面」误判成「进程已死」。
 *
 * 使用系统 [ActivityManager.getPackageImportance]（需已授予 Usage Access）。
 * 该方法为 SystemApi，经反射调用；失败时视为「仍存活」，避免误拆暂停胶囊。
 */
object PackageProcessLiveness {

    private const val TAG = "PackageProcessLiveness"

    @Volatile
    private var importanceMethod: java.lang.reflect.Method? = null

    @Volatile
    private var methodResolved: Boolean = false

    /** 该包是否已无任何存活进程（IMPORTANCE_GONE）。 */
    fun isProcessGone(context: Context, packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            ?: return false
        val method = resolveImportanceMethod() ?: return false
        return try {
            val importance = method.invoke(am, packageName) as Int
            importance >= ActivityManager.RunningAppProcessInfo.IMPORTANCE_GONE
        } catch (e: Exception) {
            Log.w(TAG, "getPackageImportance 失败 [$packageName]", e)
            false
        }
    }

    private fun resolveImportanceMethod(): java.lang.reflect.Method? {
        if (methodResolved) return importanceMethod
        synchronized(this) {
            if (methodResolved) return importanceMethod
            importanceMethod = try {
                ActivityManager::class.java.getMethod("getPackageImportance", String::class.java)
            } catch (e: Exception) {
                Log.w(TAG, "无法解析 getPackageImportance", e)
                null
            }
            methodResolved = true
            return importanceMethod
        }
    }
}
