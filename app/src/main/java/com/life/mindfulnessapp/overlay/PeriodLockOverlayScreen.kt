package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.PeriodLockPolicy
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.delay
import kotlin.math.min

private enum class PeriodLockFace {
    Door,
    Confirm,
    Duration
}

/**
 * 时段锁硬挡。事实是时间窗；不足一小时时「N 分后打开」随分钟变。
 * 日程锁传入 [scheduleTitle] / [scheduleWhy] 时，门面露出段名与可选「为了」。
 * [exemptionLabel] / [onBreakthrough]：紧急进入（计次、选时长，不拆锁）；无剩余时不传。
 * 初次点紧急进入会郑重确认；可勾选不再提示。
 */
@Composable
fun PeriodLockOverlayScreen(
    window: PeriodWindow,
    packageName: String = "",
    appName: String = "",
    scheduleTitle: String? = null,
    scheduleWhy: String? = null,
    exemptionLabel: String? = null,
    themePack: ThemePack = ThemePack.Night,
    onBreakthrough: ((minutes: Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var face by remember { mutableStateOf(PeriodLockFace.Door) }
    val whyLine = scheduleWhy?.trim().orEmpty()
    val canBreakthrough = !exemptionLabel.isNullOrBlank() && onBreakthrough != null
    val context = LocalContext.current
    val prefs = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            InterceptOverlayEntryPoint::class.java
        ).appPreferences()
    }

    LaunchedEffect(window) {
        while (true) {
            now = System.currentTimeMillis()
            delay(20_000)
        }
    }

    when {
        face == PeriodLockFace.Duration && canBreakthrough -> {
            PeriodLockBreakthroughFace(
                packageName = packageName,
                appName = appName,
                window = window,
                nowMillis = now,
                themePack = themePack,
                onEnter = { minutes -> onBreakthrough?.invoke(minutes) },
                onBack = { face = PeriodLockFace.Door }
            )
        }
        face == PeriodLockFace.Confirm && canBreakthrough -> {
            PeriodLockEmergencyConfirmFace(
                packageName = packageName,
                appName = appName,
                themePack = themePack,
                onConfirm = { skipTip ->
                    if (skipTip) prefs.setSkipPeriodEmergencyEnterTip(true)
                    face = PeriodLockFace.Duration
                },
                onLeave = { face = PeriodLockFace.Door }
            )
        }
        else -> {
            HardBlockOverlayScreen(
                packageName = packageName,
                appName = appName,
                title = PeriodLockPolicy.doorTitle(window, scheduleTitle),
                hero = PeriodLockPolicy.doorHero(window),
                heroIsFraction = false,
                extraLine = whyLine,
                whenLine = PeriodLockPolicy.doorWhenLine(window, now),
                showStopQuote = true,
                exemptionLabel = exemptionLabel,
                themePack = themePack,
                onExempt = if (canBreakthrough) {
                    {
                        face = if (prefs.shouldSkipPeriodEmergencyEnterTip()) {
                            PeriodLockFace.Duration
                        } else {
                            PeriodLockFace.Confirm
                        }
                    }
                } else {
                    null
                },
                onLeave = onDismiss
            )
        }
    }
}

/**
 * 紧急进入首次确认：劝住为先。
 * 「离开」更好按；「确认紧急进入」次要、郑重。
 */
@Composable
private fun PeriodLockEmergencyConfirmFace(
    packageName: String,
    appName: String,
    themePack: ThemePack,
    onConfirm: (skipTip: Boolean) -> Unit,
    onLeave: () -> Unit
) {
    var skipTip by remember { mutableStateOf(false) }

    MindfulnessAppTheme(themePack = themePack) {
        ProvideGateInk(themePack) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LockInk.bg)
                    .windowInsetsPadding(
                        WindowInsets.statusBars
                            .union(WindowInsets.displayCutout)
                            .union(WindowInsets.navigationBars)
                    )
                    .padding(horizontal = 26.dp)
                    .padding(top = 18.dp, bottom = 16.dp)
            ) {
                HardBlockAppChrome(
                    packageName = packageName,
                    appName = appName
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "紧急进入？",
                            fontFamily = FontFamily.Serif,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            color = LockInk.paper,
                            lineHeight = 34.sp
                        )
                        Text(
                            text = "这是今日仅有的一次破例，到点仍会锁上。\n非必要，请离开。",
                            modifier = Modifier.padding(top = 16.dp),
                            fontSize = 14.sp,
                            color = LockInk.fog,
                            lineHeight = 22.sp
                        )
                        Row(
                            modifier = Modifier
                                .padding(top = 28.dp)
                                .clickable(role = Role.Checkbox) { skipTip = !skipTip },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (skipTip) "✓" else "○",
                                fontSize = 14.sp,
                                color = if (skipTip) LockInk.leafText else LockInk.mist,
                                modifier = Modifier.width(22.dp)
                            )
                            Text(
                                text = "不再提示",
                                fontSize = 13.sp,
                                color = LockInk.mist
                            )
                        }
                    }
                }
                Text(
                    text = "离开",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = LockInk.leafText,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 8.dp)
                        .clickable(role = Role.Button, onClick = onLeave)
                )
                Text(
                    text = "确认紧急进入",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = LockInk.mist,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 4.dp)
                        .clickable(role = Role.Button) { onConfirm(skipTip) }
                )
            }
        }
    }
}

/** 紧急进入：滑动尺选时长后进入；用完次数或离开时段锁仍在。 */
@Composable
private fun PeriodLockBreakthroughFace(
    packageName: String,
    appName: String,
    window: PeriodWindow,
    nowMillis: Long,
    themePack: ThemePack,
    onEnter: (minutes: Int) -> Unit,
    onBack: () -> Unit
) {
    val remainMin = ((PeriodLockPolicy.remainingMillis(window, nowMillis) + 59_999L) / 60_000L)
        .toInt()
        .coerceAtLeast(SessionLimitPolicy.MIN_SESSION_MINUTES)
    val maxMinutes = min(SessionLimitPolicy.MAX_SESSION_MINUTES, remainMin)
        .coerceAtLeast(SessionLimitPolicy.MIN_SESSION_MINUTES)
    var minutes by remember(maxMinutes) {
        mutableIntStateOf(
            SessionLimitPolicy.DEFAULT_SESSION_MINUTES.coerceIn(
                SessionLimitPolicy.MIN_SESSION_MINUTES,
                maxMinutes
            )
        )
    }
    val canEnter = minutes >= SessionLimitPolicy.MIN_SESSION_MINUTES

    MindfulnessAppTheme(themePack = themePack) {
        ProvideGateInk(themePack) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(LockInk.bg)
                    .windowInsetsPadding(
                        WindowInsets.statusBars
                            .union(WindowInsets.displayCutout)
                            .union(WindowInsets.navigationBars)
                    )
                    .padding(horizontal = 26.dp)
                    .padding(top = 18.dp, bottom = 16.dp)
            ) {
                HardBlockAppChrome(
                    packageName = packageName,
                    appName = appName
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "紧急进入",
                            fontFamily = FontFamily.Serif,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            color = LockInk.paper,
                            lineHeight = 34.sp
                        )
                        Text(
                            text = "选多久，到点再锁上",
                            modifier = Modifier.padding(top = 10.dp, bottom = 28.dp),
                            fontSize = 13.sp,
                            color = LockInk.fog
                        )
                        DoorDurationRuler(
                            selectedMinutes = minutes,
                            onSelect = {
                                minutes = it.coerceIn(
                                    SessionLimitPolicy.MIN_SESSION_MINUTES,
                                    maxMinutes
                                )
                            },
                            minMinutes = SessionLimitPolicy.MIN_SESSION_MINUTES,
                            maxMinutes = maxMinutes
                        )
                    }
                }
                Text(
                    text = if (canEnter) "进入" else "选择时长",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (canEnter) LockInk.leafText else LockInk.mist.copy(alpha = 0.45f),
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 8.dp)
                        .then(
                            if (canEnter) {
                                Modifier.clickable(role = Role.Button) { onEnter(minutes) }
                            } else {
                                Modifier
                            }
                        )
                )
                Text(
                    text = "返回",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Normal,
                    color = LockInk.mist,
                    modifier = Modifier
                        .align(Alignment.End)
                        .padding(bottom = 4.dp)
                        .clickable(role = Role.Button, onClick = onBack)
                )
            }
        }
    }
}
