package com.life.mindfulnessapp.display

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.content.ContextCompat
import com.life.mindfulnessapp.data.AppPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 系统级真·灰度（无彩色）。
 *
 * 半透明灰幕只能「罩一层灰」，底下组件仍是彩色；要「完全没有彩色」必须走
 * Android 无障碍色盲模拟里的 Monochromacy（与系统「色彩校正 / 灰度」同源）：
 *
 * - [Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER_ENABLED]
 * - [Settings.Secure.ACCESSIBILITY_DISPLAY_DALTONIZER] = 0（单色）
 *
 * 需要 [Manifest.permission.WRITE_SECURE_SETTINGS]（用户无法在系统设置里点授，
 * Debug 可用 `adb shell pm grant <pkg> android.permission.WRITE_SECURE_SETTINGS`）。
 *
 * 本类在激活时记下先前状态，停用时原样恢复，避免把用户手机留在灰度里。
 */
@Singleton
class DisplayGrayscaleController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val appPreferences: AppPreferences
) {
    private val cr get() = context.contentResolver

    @Volatile
    private var heldByUs: Boolean = false

    private var previousEnabled: Int = 0
    private var previousMode: Int = DALTONIZER_DISABLED

    fun canWriteSecureSettings(): Boolean =
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_SECURE_SETTINGS
        ) == PackageManager.PERMISSION_GRANTED

    /** 当前是否由心锚持有系统灰度 */
    fun isHeldByUs(): Boolean = heldByUs || appPreferences.isDisplayGrayscaleHeldByUs()

    /**
     * 打开 / 关闭系统灰度。
     * @return true = 已按请求落到期望状态（或本就无需改）；false = 无权限或写入失败
     */
    @Synchronized
    fun setGrayscaleActive(active: Boolean): Boolean {
        if (active) return enableGrayscale()
        return disableGrayscale()
    }

    /**
     * 进程启动时：若上次异常退出时仍持有灰度，强制恢复，避免整机留在黑白。
     */
    @Synchronized
    fun recoverIfOrphaned() {
        if (!appPreferences.isDisplayGrayscaleHeldByUs()) return
        Log.w(TAG, "恢复孤儿系统灰度（上次未正常释放）")
        heldByUs = true
        previousEnabled = appPreferences.getDisplayGrayscalePreviousEnabled()
        previousMode = appPreferences.getDisplayGrayscalePreviousMode()
        disableGrayscale()
    }

    private fun enableGrayscale(): Boolean {
        if (heldByUs) return true
        if (!canWriteSecureSettings()) {
            Log.w(TAG, "无 WRITE_SECURE_SETTINGS，无法开启系统灰度")
            return false
        }
        return try {
            previousEnabled = Settings.Secure.getInt(
                cr,
                SECURE_DALTONIZER_ENABLED,
                0
            )
            previousMode = Settings.Secure.getInt(
                cr,
                SECURE_DALTONIZER_MODE,
                DALTONIZER_DISABLED
            )
            Settings.Secure.putInt(
                cr,
                SECURE_DALTONIZER_ENABLED,
                1
            )
            Settings.Secure.putInt(
                cr,
                SECURE_DALTONIZER_MODE,
                DALTONIZER_MONOCHROMACY
            )
            heldByUs = true
            appPreferences.markDisplayGrayscaleHeld(
                held = true,
                previousEnabled = previousEnabled,
                previousMode = previousMode
            )
            Log.i(TAG, "系统灰度已开启（prevEnabled=$previousEnabled prevMode=$previousMode）")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "开启系统灰度被拒", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "开启系统灰度失败", e)
            false
        }
    }

    private fun disableGrayscale(): Boolean {
        if (!heldByUs && !appPreferences.isDisplayGrayscaleHeldByUs()) return true
        if (!canWriteSecureSettings()) {
            Log.w(TAG, "无 WRITE_SECURE_SETTINGS，无法关闭系统灰度")
            // 仍清本地持有标记，避免永远卡死；用户可在系统设置里关色彩校正
            clearHeldLocal()
            return false
        }
        return try {
            val restoreEnabled = if (appPreferences.isDisplayGrayscaleHeldByUs()) {
                appPreferences.getDisplayGrayscalePreviousEnabled()
            } else {
                previousEnabled
            }
            val restoreMode = if (appPreferences.isDisplayGrayscaleHeldByUs()) {
                appPreferences.getDisplayGrayscalePreviousMode()
            } else {
                previousMode
            }
            Settings.Secure.putInt(
                cr,
                SECURE_DALTONIZER_ENABLED,
                restoreEnabled
            )
            if (restoreEnabled == 1 && restoreMode != DALTONIZER_DISABLED) {
                Settings.Secure.putInt(
                    cr,
                    SECURE_DALTONIZER_MODE,
                    restoreMode
                )
            } else {
                // 关闭时一并写回模式，避免部分 ROM 只关 enabled 仍残留
                Settings.Secure.putInt(
                    cr,
                    SECURE_DALTONIZER_MODE,
                    if (restoreMode != DALTONIZER_DISABLED) restoreMode else DALTONIZER_DISABLED
                )
            }
            clearHeldLocal()
            Log.i(TAG, "系统灰度已释放（restoreEnabled=$restoreEnabled restoreMode=$restoreMode）")
            true
        } catch (e: SecurityException) {
            Log.e(TAG, "关闭系统灰度被拒", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "关闭系统灰度失败", e)
            false
        }
    }

    private fun clearHeldLocal() {
        heldByUs = false
        previousEnabled = 0
        previousMode = DALTONIZER_DISABLED
        appPreferences.markDisplayGrayscaleHeld(held = false)
    }

    companion object {
        private const val TAG = "DisplayGrayscale"

        /** 隐藏 API：系统色彩校正开关（公开 SDK 无常量） */
        private const val SECURE_DALTONIZER_ENABLED =
            "accessibility_display_daltonizer_enabled"

        /** 隐藏 API：色盲模拟模式（0 = monochromacy） */
        private const val SECURE_DALTONIZER_MODE = "accessibility_display_daltonizer"

        /** AOSP：模拟全色盲 = 真灰度 */
        const val DALTONIZER_MONOCHROMACY = 0

        /** 常见「未设置」哨兵 */
        const val DALTONIZER_DISABLED = -1
    }
}
