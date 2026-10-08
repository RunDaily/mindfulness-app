package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import kotlinx.coroutines.delay
import java.util.Calendar

/**
 * 时段锁 / 日限 / 次数共用的硬挡骨架。
 * 顶上拦谁（图标 + 名）与当前时分，中间一句原因、一个事实、何时再开，底下「离开」；
 * 时段锁可另给 [exemptionLabel] 作紧急进入入口（次要、不抢离开）。
 * [showStopQuote]：单次到点 / 日限 / 时段锁为 true；次数硬挡不加。
 */
@Composable
fun HardBlockOverlayScreen(
    appName: String,
    title: String,
    hero: String,
    heroIsFraction: Boolean,
    unit: String = "",
    whenLine: String,
    extraLine: String = "",
    showStopQuote: Boolean = false,
    exemptionLabel: String? = null,
    onExempt: (() -> Unit)? = null,
    packageName: String = "",
    themePack: ThemePack = ThemePack.Night,
    onLeave: () -> Unit
) {
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
                    appName = appName,
                    trailing = { HardBlockNowClock() }
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Column {
                        Text(
                            text = title,
                            fontFamily = FontFamily.Serif,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            color = LockInk.paper,
                            lineHeight = 34.sp
                        )
                        Text(
                            text = hero,
                            modifier = Modifier.padding(top = 28.dp),
                            fontSize = if (heroIsFraction) 48.sp else 28.sp,
                            fontWeight = FontWeight.Light,
                            color = LockInk.paper,
                            letterSpacing = if (heroIsFraction) (-1.2).sp else (-0.4).sp
                        )
                        if (unit.isNotEmpty()) {
                            Text(
                                text = unit,
                                modifier = Modifier.padding(top = 8.dp),
                                fontSize = 13.sp,
                                color = LockInk.fog
                            )
                        }
                        if (extraLine.isNotEmpty()) {
                            Text(
                                text = extraLine,
                                modifier = Modifier.padding(top = 16.dp),
                                fontSize = 13.sp,
                                color = LockInk.mist
                            )
                        }
                        Text(
                            text = whenLine,
                            modifier = Modifier.padding(top = if (extraLine.isNotEmpty()) 4.dp else 16.dp),
                            fontSize = 13.sp,
                            color = LockInk.fog
                        )
                    }
                }
                if (showStopQuote) {
                    StopDoorQuoteFootnote(modifier = Modifier.padding(bottom = 22.dp))
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
                if (!exemptionLabel.isNullOrBlank() && onExempt != null) {
                    Text(
                        text = exemptionLabel,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = LockInk.mist,
                        modifier = Modifier
                            .align(Alignment.End)
                            .padding(bottom = 4.dp)
                            .clickable(role = Role.Button, onClick = onExempt)
                    )
                }
            }
        }
    }
}

/** 顶栏拦谁：小图标 + 应用名，不抢中间时段事实。 */
@Composable
internal fun HardBlockAppChrome(
    packageName: String,
    appName: String,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (packageName.isNotBlank()) {
            HardBlockAppIcon(
                packageName = packageName,
                appName = appName,
                size = 18.dp
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(
            text = appName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = LockInk.fog,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        trailing?.invoke()
    }
}

@Composable
private fun HardBlockAppIcon(
    packageName: String,
    appName: String,
    size: Dp
) {
    val context = LocalContext.current
    val iconBitmap = remember(packageName) {
        if (packageName.isEmpty()) return@remember null
        try {
            context.packageManager.getApplicationIcon(packageName)
                .toBitmap(72, 72)
                .asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    val corner = RoundedCornerShape(4.dp)
    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = appName,
            modifier = Modifier
                .size(size)
                .clip(corner)
        )
    } else {
        Box(
            modifier = Modifier
                .size(size)
                .clip(corner)
                .background(LockInk.mist.copy(alpha = 0.18f))
        )
    }
}

/** 右上墙钟：雾色 HH:MM，按分钟跳，不抢中间事实。 */
@Composable
private fun HardBlockNowClock() {
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = System.currentTimeMillis()
            val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
            val untilNextMinute =
                (60 - cal.get(Calendar.SECOND)) * 1000L - cal.get(Calendar.MILLISECOND)
            delay(untilNextMinute.coerceAtLeast(1_000L))
        }
    }
    val hm = remember(nowMs) {
        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        "%02d:%02d".format(cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE))
    }
    Text(
        text = hm,
        fontSize = 11.sp,
        color = LockInk.mist,
        letterSpacing = 0.4.sp
    )
}
