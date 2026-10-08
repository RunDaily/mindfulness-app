package com.life.mindfulnessapp

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import com.life.mindfulnessapp.service.MonitorForegroundService
import java.lang.ref.WeakReference

/**
 * 拦截页焦点托管：把被拦 App 推离前台，避免门上犹豫计入系统 UsageStats。
 *
 * 兼用于华为等「系统分身」（独立 User）：机主侧悬浮窗盖不住分身界面时，
 * 先把焦点抢回 user 0 再叠意图门；确认进入后由服务启动分身主界面。
 */
class DualSpaceGateActivity : Activity() {

    companion object {
        private const val EXTRA_PACKAGE = "package"

        @Volatile
        private var instanceRef: WeakReference<DualSpaceGateActivity>? = null

        fun launch(context: Context, packageName: String) {
            if (instanceRef?.get() != null) return
            val intent = Intent(context, DualSpaceGateActivity::class.java).apply {
                putExtra(EXTRA_PACKAGE, packageName)
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_ACTIVITY_NO_ANIMATION or
                        Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or
                        Intent.FLAG_ACTIVITY_CLEAR_TOP
                )
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
            }
        }

        fun finishIfShowing() {
            instanceRef?.get()?.let { act ->
                try {
                    act.finish()
                } catch (_: Exception) {
                }
            }
        }

        fun isShowing(): Boolean = instanceRef?.get() != null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        instanceRef = WeakReference(this)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK
        setContentView(
            View(this).apply {
                setBackgroundColor(Color.BLACK)
                isClickable = true
                isFocusable = true
            }
        )
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        // 交给拦截层处理离开；不直接露出分身
        moveTaskToBack(true)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_APP_SWITCH) {
            MonitorForegroundService.notifyRecentsOpened()
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        super.onDestroy()
    }
}
