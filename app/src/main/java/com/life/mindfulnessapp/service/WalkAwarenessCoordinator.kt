package com.life.mindfulnessapp.service

import android.os.SystemClock
import android.util.Log
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.WalkAwarenessCopy
import com.life.mindfulnessapp.domain.model.WalkAwarenessLevel
import com.life.mindfulnessapp.overlay.OverlayManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 步行觉察编排：检测 × 亮屏 × 前台 App → 路况锚点 L0/L1/L2。
 *
 * 常驻策略：走路看手机期间持续在场；用形态阶梯表达强度，而非反复弹窗。
 */
@Singleton
class WalkAwarenessCoordinator @Inject constructor(
    private val detector: WalkAwarenessDetector,
    private val overlayManager: OverlayManager,
    private val appPreferences: AppPreferences
) {
    private var job: Job? = null
    private var tickJob: Job? = null

    @Volatile private var screenOn: Boolean = true
    @Volatile private var foregroundPackage: String? = null
    @Volatile private var ownPackage: String = ""

    /** 本段「边走边看」起始 elapsedRealtime；0 = 未激活 */
    private var phoneWalkSinceElapsed: Long = 0L
    private var copySeed: Int = 0
    private var lastL2PulseElapsed: Long = 0L
    private var l2HoldUntilElapsed: Long = 0L
    private var sessionHidden: Boolean = false

    fun start(scope: CoroutineScope, ownPackageName: String) {
        ownPackage = ownPackageName
        stopInternal(keepDetector = false)
        job = scope.launch {
            appPreferences.walkAwarenessEnabled.collectLatest { enabled ->
                if (!enabled) {
                    detector.stop()
                    resetAndDismiss()
                    return@collectLatest
                }
                detector.start()
                // 开启后持续融合 walking 态
                detector.isWalking.collectLatest { walking ->
                    if (!appPreferences.isWalkAwarenessEnabled()) return@collectLatest
                    evaluate(walking)
                }
            }
        }
        tickJob = scope.launch {
            while (isActive) {
                delay(TICK_MS)
                if (!appPreferences.isWalkAwarenessEnabled()) continue
                if (!detector.available.value && detector.hasActivityPermission()) {
                    detector.start()
                }
                detector.refreshIdle()
                evaluate(detector.isWalking.value)
            }
        }
        Log.d(TAG, "步行觉察协调器已启动")
    }

    fun stop() {
        stopInternal(keepDetector = false)
    }

    private fun stopInternal(keepDetector: Boolean) {
        tickJob?.cancel()
        tickJob = null
        job?.cancel()
        job = null
        if (!keepDetector) detector.stop()
        resetAndDismiss()
    }

    fun onScreenOn() {
        screenOn = true
    }

    fun onScreenOff() {
        screenOn = false
        resetAndDismiss()
    }

    fun onForegroundPackage(packageName: String?) {
        foregroundPackage = packageName
        if (packageName == ownPackage) {
            // 回到心锚：收起，避免叠在自家 UI 上
            if (overlayManager.isWalkAwareShowing()) {
                overlayManager.dismissWalkAware()
            }
        }
    }

    /** 供意图门等读取：当前是否判定在走（与锚点是否展示无关） */
    fun isWalkingNow(): Boolean = detector.isWalking.value

    /** 用户点「本次隐藏」 */
    fun hideForThisWalk() {
        sessionHidden = true
        overlayManager.dismissWalkAware()
    }

    private fun evaluate(walking: Boolean) {
        val eligible = appPreferences.isWalkAwarenessEnabled() &&
            walking &&
            screenOn &&
            !sessionHidden &&
            !shouldSuppress() &&
            foregroundPackage != null &&
            foregroundPackage != ownPackage

        if (!eligible) {
            if (!walking) {
                // 停走：清本段，允许下次再走时重新出现
                phoneWalkSinceElapsed = 0L
                l2HoldUntilElapsed = 0L
                sessionHidden = false
            }
            if (overlayManager.isWalkAwareShowing()) {
                overlayManager.dismissWalkAware()
            }
            return
        }

        val now = SystemClock.elapsedRealtime()
        if (phoneWalkSinceElapsed == 0L) {
            phoneWalkSinceElapsed = now
            copySeed = (now % 997).toInt()
            lastL2PulseElapsed = 0L
            l2HoldUntilElapsed = 0L
        }

        val elapsed = now - phoneWalkSinceElapsed
        val level = resolveLevel(now, elapsed)
        val label = WalkAwarenessCopy.labelFor(level, copySeed + (elapsed / 45_000L).toInt())
        overlayManager.showOrUpdateWalkAware(
            level = level,
            label = label,
            onHideThisWalk = { hideForThisWalk() }
        )
    }

    private fun resolveLevel(now: Long, elapsedInPhoneWalk: Long): WalkAwarenessLevel {
        // L2 短暂保持窗
        if (l2HoldUntilElapsed > now) return WalkAwarenessLevel.L2

        when {
            elapsedInPhoneWalk < L1_AFTER_MS -> return WalkAwarenessLevel.L0
            elapsedInPhoneWalk < L2_AFTER_MS -> return WalkAwarenessLevel.L1
            else -> {
                // 首次进 L2，或周期性醒神脉冲
                val dueFirst = lastL2PulseElapsed == 0L
                val duePulse = lastL2PulseElapsed > 0L &&
                    now - lastL2PulseElapsed >= L2_PULSE_INTERVAL_MS
                if (dueFirst || duePulse) {
                    lastL2PulseElapsed = now
                    l2HoldUntilElapsed = now + L2_HOLD_MS
                    copySeed++
                    return WalkAwarenessLevel.L2
                }
                return WalkAwarenessLevel.L1
            }
        }
    }

    private fun shouldSuppress(): Boolean {
        return overlayManager.isInterceptVisible.get() ||
            overlayManager.isAwayEndedBarShowing() ||
            overlayManager.isWalkAwareSuppressedByFullscreen()
    }

    private fun resetAndDismiss() {
        phoneWalkSinceElapsed = 0L
        l2HoldUntilElapsed = 0L
        lastL2PulseElapsed = 0L
        sessionHidden = false
        overlayManager.dismissWalkAware()
    }

    companion object {
        private const val TAG = "WalkAwarenessCoord"
        private const val TICK_MS = 800L
        /** 边走边看满此时长 → L1 轻语 */
        private const val L1_AFTER_MS = 30_000L
        /** 再持续 → 首次 L2 */
        private const val L2_AFTER_MS = 90_000L
        /** L2 展示时长 */
        private const val L2_HOLD_MS = 2_400L
        /** 之后每隔多久再醒神一次 */
        private const val L2_PULSE_INTERVAL_MS = 120_000L
    }
}
