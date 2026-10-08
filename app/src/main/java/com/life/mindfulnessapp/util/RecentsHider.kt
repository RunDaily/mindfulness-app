package com.life.mindfulnessapp.util

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.util.Log

/**
 * 在后台切换界面中隐藏心锚任务卡片（对齐禅定「在后台切换界面中隐藏」）。
 * 一键清理常只清可见卡片；隐藏后更不易被点名杀掉。
 */
object RecentsHider {

    private const val TAG = "RecentsHider"

    fun apply(context: Context, hide: Boolean) {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.appTasks.forEach { task ->
                try {
                    task.setExcludeFromRecents(hide)
                } catch (e: Exception) {
                    Log.w(TAG, "setExcludeFromRecents 失败", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "apply 失败", e)
        }
    }

    fun applyFromActivity(activity: Activity, hide: Boolean) {
        apply(activity, hide)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                activity.setTaskDescription(
                    ActivityManager.TaskDescription(activity.title?.toString() ?: "心锚")
                )
            } catch (_: Exception) {
            }
        }
    }

    /** 小米 / vivo / 华为 / 荣耀 / OPPO 系：一键清理更狠，默认倾向隐藏 */
    fun defaultHideOnThisDevice(): Boolean {
        val key = listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.DISPLAY
        ).joinToString(" ").lowercase()
        return key.contains("xiaomi") ||
            key.contains("redmi") ||
            key.contains("miui") ||
            key.contains("hyperos") ||
            key.contains("vivo") ||
            key.contains("iqoo") ||
            key.contains("huawei") ||
            key.contains("honor") ||
            key.contains("oppo") ||
            key.contains("realme") ||
            key.contains("oneplus") ||
            key.contains("oplus")
    }
}
