package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme

/** 事务门尽量短的时长；点芯片即进入。 */
private val TaskDoorMinutes = listOf(1, 2, 3, 5)

private enum class TaskDoorBeat { Ask, Duration }

/**
 * 事务软门：无呼吸。
 * 第一拍主旋律是不必进（离开优先）；第二拍最短时长，点一下就进。
 */
@Composable
fun TaskGateOverlayScreen(
    appName: String,
    factsLabel: String,
    themePack: ThemePack = ThemePack.Night,
    onAdmit: (SoftDoorChoice, (Boolean) -> Unit) -> Unit,
    onLeave: () -> Unit
) {
    var beat by remember { mutableStateOf(TaskDoorBeat.Ask) }
    var busy by remember { mutableStateOf(false) }

    fun enter(minutes: Int) {
        if (busy || minutes <= 0) return
        busy = true
        onAdmit(SoftDoorChoice(search = false, text = "", minutes = minutes)) { ok ->
            if (!ok) busy = false
        }
    }

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
                Text(appName, fontSize = 11.sp, color = LockInk.mist)
                AnimatedContent(
                    targetState = beat,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    },
                    label = "task_door_beat"
                ) { face ->
                    when (face) {
                        TaskDoorBeat.Ask -> TaskAskBeat(
                            factsLabel = factsLabel,
                            enabled = !busy,
                            onLeave = onLeave,
                            onNeedEnter = { beat = TaskDoorBeat.Duration }
                        )
                        TaskDoorBeat.Duration -> TaskDurationBeat(
                            enabled = !busy,
                            onPick = { enter(it) },
                            onBack = { beat = TaskDoorBeat.Ask },
                            onLeave = onLeave
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskAskBeat(
    factsLabel: String,
    enabled: Boolean,
    onLeave: () -> Unit,
    onNeedEnter: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(
                    text = "这一次\n先不必进",
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = LockInk.paper,
                    lineHeight = 34.sp
                )
                if (factsLabel.isNotBlank()) {
                    Text(
                        text = factsLabel,
                        modifier = Modifier.padding(top = 16.dp),
                        fontSize = 13.sp,
                        color = LockInk.fog
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "有事，进去",
                fontSize = 15.sp,
                color = LockInk.mist,
                modifier = Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onNeedEnter
                )
            )
            Text(
                text = "离开",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = LockInk.leafText,
                modifier = Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onLeave
                )
            )
        }
    }
}

@Composable
private fun TaskDurationBeat(
    enabled: Boolean,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
    onLeave: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.CenterStart
        ) {
            Column {
                Text(
                    text = "多久",
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = LockInk.paper
                )
                Text(
                    text = "点一下就进",
                    modifier = Modifier.padding(top = 10.dp),
                    fontSize = 13.sp,
                    color = LockInk.fog
                )
                Row(
                    modifier = Modifier.padding(top = 28.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    TaskDoorMinutes.forEach { mark ->
                        Text(
                            text = "$mark",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Light,
                            color = LockInk.leafText,
                            modifier = Modifier.clickable(
                                enabled = enabled,
                                role = Role.Button
                            ) { onPick(mark) }
                        )
                    }
                    Text(
                        text = "分",
                        fontSize = 12.sp,
                        color = LockInk.mist,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "返回",
                fontSize = 15.sp,
                color = LockInk.mist,
                modifier = Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onBack
                )
            )
            Text(
                text = "离开",
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                color = LockInk.leafText,
                modifier = Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onLeave
                )
            )
        }
    }
}
