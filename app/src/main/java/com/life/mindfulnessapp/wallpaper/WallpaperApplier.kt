package com.life.mindfulnessapp.wallpaper

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 将静图设为桌面 / 锁屏（可分设）。
 */
object WallpaperApplier {

    enum class Target {
        Home,
        Lock
    }

    sealed class Result {
        data class Success(val applied: Set<Target>) : Result()
        data class Partial(
            val applied: Set<Target>,
            val failed: Set<Target>,
            val message: String
        ) : Result()
        data class Failure(val message: String) : Result()
    }

    fun supportsLockSeparately(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.N

    suspend fun apply(
        context: Context,
        bitmap: Bitmap,
        targets: Set<Target>
    ): Result = withContext(Dispatchers.IO) {
        if (targets.isEmpty()) {
            return@withContext Result.Failure("请选择桌面或锁屏")
        }
        val wm = WallpaperManager.getInstance(context.applicationContext)
        val effective = if (!supportsLockSeparately()) {
            setOf(Target.Home)
        } else {
            targets
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return@withContext try {
                @Suppress("DEPRECATION")
                wm.setBitmap(bitmap)
                Result.Success(setOf(Target.Home))
            } catch (e: Exception) {
                Result.Failure(e.message?.takeIf { it.isNotBlank() } ?: "无法设置壁纸")
            }
        }

        val flags = flagsFor(effective)
        try {
            wm.setBitmap(bitmap, null, true, flags)
            Result.Success(effective)
        } catch (_: Exception) {
            // 组合失败时按目标逐个重试（部分机型不支持锁屏）
            val applied = linkedSetOf<Target>()
            val failed = linkedSetOf<Target>()
            for (target in effective) {
                try {
                    wm.setBitmap(bitmap, null, true, flagsFor(setOf(target)))
                    applied += target
                } catch (_: Exception) {
                    failed += target
                }
            }
            when {
                applied.isEmpty() -> Result.Failure(
                    when {
                        Target.Lock in failed && Target.Home !in effective -> "此机型不支持单独锁屏壁纸"
                        else -> "无法设置壁纸"
                    }
                )
                failed.isEmpty() -> Result.Success(applied)
                else -> Result.Partial(
                    applied = applied,
                    failed = failed,
                    message = failureLabel(failed)
                )
            }
        }
    }

    fun successToast(applied: Set<Target>): String = when {
        applied == setOf(Target.Home) -> "已设为桌面"
        applied == setOf(Target.Lock) -> "已设为锁屏"
        Target.Home in applied && Target.Lock in applied -> "已设为桌面与锁屏"
        else -> "已设为壁纸"
    }

    fun actionLabel(home: Boolean, lock: Boolean, lockSupported: Boolean): String = when {
        home && lock && lockSupported -> "设为桌面与锁屏"
        home && (!lock || !lockSupported) -> "设为桌面"
        !home && lock && lockSupported -> "设为锁屏"
        else -> "设为壁纸"
    }

    private fun flagsFor(targets: Set<Target>): Int {
        var flags = 0
        if (Target.Home in targets) flags = flags or WallpaperManager.FLAG_SYSTEM
        if (Target.Lock in targets) flags = flags or WallpaperManager.FLAG_LOCK
        return flags
    }

    private fun failureLabel(failed: Set<Target>): String = when {
        failed == setOf(Target.Lock) -> "桌面已设 · 锁屏未支持"
        failed == setOf(Target.Home) -> "锁屏已设 · 桌面未成功"
        else -> "部分未成功"
    }
}
