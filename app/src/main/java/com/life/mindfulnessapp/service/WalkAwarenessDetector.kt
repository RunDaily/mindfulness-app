package com.life.mindfulnessapp.service

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 步行检测：优先 [Sensor.TYPE_STEP_DETECTOR]，回退 [Sensor.TYPE_STEP_COUNTER]。
 * 不依赖 GMS，适合国内机型。
 *
 * 判定：近 [WINDOW_MS] 内步数 ≥ [MIN_STEPS_IN_WINDOW]，
 * 且距上次步进未超过 [IDLE_MS] 仍视为在走。
 */
@Singleton
class WalkAwarenessDetector @Inject constructor(
    @ApplicationContext private val context: Context
) : SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val _isWalking = MutableStateFlow(false)
    val isWalking: StateFlow<Boolean> = _isWalking.asStateFlow()

    private val _available = MutableStateFlow(false)
    val available: StateFlow<Boolean> = _available.asStateFlow()

    private val stepTimesMs = ArrayDeque<Long>()
    private var lastStepElapsedMs = 0L
    private var lastCounterValue = -1f
    private var listening = false

    @Synchronized
    fun start() {
        if (listening) return
        if (!hasActivityPermission()) {
            Log.w(TAG, "无 ACTIVITY_RECOGNITION，跳过步行传感器")
            _available.value = false
            _isWalking.value = false
            return
        }
        val detector = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
        val counter = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        val sensor = detector ?: counter
        if (sensor == null) {
            Log.w(TAG, "设备无计步传感器")
            _available.value = false
            _isWalking.value = false
            return
        }
        val ok = sensorManager.registerListener(
            this,
            sensor,
            SensorManager.SENSOR_DELAY_NORMAL
        )
        listening = ok
        _available.value = ok
        if (!ok) {
            Log.w(TAG, "注册计步传感器失败 type=${sensor.type}")
        } else {
            Log.d(TAG, "步行检测已启动 type=${sensor.type}")
        }
    }

    @Synchronized
    fun stop() {
        if (!listening) return
        try {
            sensorManager.unregisterListener(this)
        } catch (_: Exception) {
        }
        listening = false
        stepTimesMs.clear()
        lastCounterValue = -1f
        lastStepElapsedMs = 0L
        _isWalking.value = false
        Log.d(TAG, "步行检测已停止")
    }

    /** 由协调器定期调用，刷新「是否仍在走」的超时态。 */
    fun refreshIdle() {
        if (!listening) return
        val now = SystemClock.elapsedRealtime()
        prune(now)
        val active = stepTimesMs.size >= MIN_STEPS_IN_WINDOW &&
            lastStepElapsedMs > 0L &&
            now - lastStepElapsedMs <= IDLE_MS
        if (_isWalking.value != active) {
            _isWalking.value = active
        }
    }

    fun hasActivityPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACTIVITY_RECOGNITION
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun hasStepSensor(): Boolean {
        return sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null ||
            sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = SystemClock.elapsedRealtime()
        when (event.sensor.type) {
            Sensor.TYPE_STEP_DETECTOR -> {
                recordStep(now)
            }
            Sensor.TYPE_STEP_COUNTER -> {
                val value = event.values.firstOrNull() ?: return
                if (lastCounterValue < 0f) {
                    lastCounterValue = value
                    return
                }
                val delta = (value - lastCounterValue).toInt()
                lastCounterValue = value
                if (delta <= 0) return
                repeat(delta.coerceAtMost(8)) { recordStep(now) }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun recordStep(now: Long) {
        lastStepElapsedMs = now
        stepTimesMs.addLast(now)
        prune(now)
        if (stepTimesMs.size >= MIN_STEPS_IN_WINDOW) {
            _isWalking.value = true
        }
    }

    private fun prune(now: Long) {
        while (stepTimesMs.isNotEmpty() && now - stepTimesMs.first() > WINDOW_MS) {
            stepTimesMs.removeFirst()
        }
    }

    companion object {
        private const val TAG = "WalkAwarenessDetector"
        /** 滑动窗口：近 4 秒内的步 */
        private const val WINDOW_MS = 4_000L
        /** 窗口内至少几步才算在走 */
        private const val MIN_STEPS_IN_WINDOW = 3
        /** 末步后多久仍视为在走（过街停顿等） */
        private const val IDLE_MS = 10_000L
    }
}
