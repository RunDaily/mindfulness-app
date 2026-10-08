package com.life.mindfulnessapp.service

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import com.life.mindfulnessapp.util.KeepAliveAlarmScheduler
import com.life.mindfulnessapp.util.KeepAliveRestarter
import com.life.mindfulnessapp.wallpaper.WallpaperRenderer
import com.life.mindfulnessapp.wallpaper.WallpaperStyleStore

/**
 * 心锚桌面壁纸（Live Wallpaper）：视觉与发现「壁纸」工作室同源，
 * 独立进程 `:guardian` 周期拉起 Monitor，兼作保活守护。
 */
class KeepAliveWallpaperService : WallpaperService() {

    companion object {
        private const val TAG = "KeepAliveWallpaper"
        private const val PING_INTERVAL_MS = 45_000L
        private const val STYLE_RELOAD_MS = 15_000L

        fun component(context: Context): ComponentName =
            ComponentName(context, KeepAliveWallpaperService::class.java)

        fun isActive(context: Context): Boolean {
            return try {
                val info = WallpaperManager.getInstance(context).wallpaperInfo ?: return false
                info.component == component(context)
            } catch (_: Exception) {
                false
            }
        }

        /** 打开系统「设为动态壁纸」确认页（需用户点一次） */
        fun openSetter(context: Context): Boolean {
            val app = context.applicationContext
            val direct = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                putExtra(
                    WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                    component(app)
                )
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            return try {
                app.startActivity(direct)
                true
            } catch (_: Exception) {
                try {
                    app.startActivity(
                        Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    )
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "无法打开壁纸设置", e)
                    false
                }
            }
        }
    }

    override fun onCreateEngine(): Engine = GuardianEngine()

    private inner class GuardianEngine : Engine() {

        private val handler = Handler(Looper.getMainLooper())
        private var cachedBitmap: Bitmap? = null
        private var cachedKey: String? = null
        private var surfaceW = 0
        private var surfaceH = 0

        private val pingRunnable = object : Runnable {
            override fun run() {
                try {
                    KeepAliveRestarter.ensureRunning(applicationContext)
                    KeepAliveAlarmScheduler.schedulePeriodic(applicationContext)
                } catch (e: Exception) {
                    Log.w(TAG, "壁纸守护拉起失败", e)
                }
                handler.postDelayed(this, PING_INTERVAL_MS)
            }
        }

        private val styleReloadRunnable = object : Runnable {
            override fun run() {
                if (isVisible || isPreview) drawFrame(forceReload = true)
                handler.postDelayed(this, STYLE_RELOAD_MS)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder?) {
            super.onCreate(surfaceHolder)
            setTouchEventsEnabled(false)
            Log.i(TAG, "心锚壁纸引擎创建")
            KeepAliveRestarter.ensureRunning(applicationContext)
            handler.post(pingRunnable)
            handler.postDelayed(styleReloadRunnable, STYLE_RELOAD_MS)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                drawFrame(forceReload = true)
                KeepAliveRestarter.ensureRunning(applicationContext)
            }
        }

        override fun onSurfaceChanged(
            holder: SurfaceHolder?,
            format: Int,
            width: Int,
            height: Int
        ) {
            super.onSurfaceChanged(holder, format, width, height)
            surfaceW = width
            surfaceH = height
            drawFrame(forceReload = true)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder?) {
            handler.removeCallbacks(pingRunnable)
            handler.removeCallbacks(styleReloadRunnable)
            KeepAliveAlarmScheduler.scheduleImmediateRestart(applicationContext)
            recycleCache()
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            handler.removeCallbacks(pingRunnable)
            handler.removeCallbacks(styleReloadRunnable)
            KeepAliveAlarmScheduler.scheduleImmediateRestart(applicationContext)
            KeepAliveAlarmScheduler.schedulePeriodic(applicationContext)
            recycleCache()
            super.onDestroy()
        }

        private fun recycleCache() {
            cachedBitmap?.recycle()
            cachedBitmap = null
            cachedKey = null
        }

        private fun drawFrame(forceReload: Boolean = false) {
            val holder = surfaceHolder ?: return
            val w = surfaceW.takeIf { it > 0 } ?: holder.surfaceFrame.width()
            val h = surfaceH.takeIf { it > 0 } ?: holder.surfaceFrame.height()
            if (w <= 0 || h <= 0) return

            var canvas: Canvas? = null
            try {
                val style = WallpaperStyleStore.load(applicationContext)
                val key = "${style.template.name}|${style.line}|${style.author}|$w|$h"
                if (forceReload || key != cachedKey || cachedBitmap?.isRecycled != false) {
                    recycleCache()
                    cachedBitmap = WallpaperRenderer.render(
                        width = w,
                        height = h,
                        template = style.template,
                        line = style.line,
                        author = style.author
                    )
                    cachedKey = key
                }
                val bmp = cachedBitmap ?: return
                canvas = holder.lockCanvas() ?: return
                canvas.drawBitmap(bmp, null, Rect(0, 0, w, h), null)
            } catch (e: Exception) {
                Log.w(TAG, "绘制壁纸失败", e)
            } finally {
                if (canvas != null) {
                    try {
                        holder.unlockCanvasAndPost(canvas)
                    } catch (_: Exception) {
                    }
                }
            }
        }
    }
}
