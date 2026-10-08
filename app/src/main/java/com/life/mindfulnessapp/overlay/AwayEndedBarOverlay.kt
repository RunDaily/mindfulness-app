package com.life.mindfulnessapp.overlay

import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.MindfulnessLevel
import com.life.mindfulnessapp.domain.model.CompanionPath
import com.life.mindfulnessapp.domain.model.SessionAwarenessCopy
import com.life.mindfulnessapp.domain.model.SessionAwarenessMode
import com.life.mindfulnessapp.ui.common.CompareSectionColors
import com.life.mindfulnessapp.ui.common.CompareTierStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 横条可见时长；写备注时暂停。对齐侧点一下即收，不再催进度。 */
private const val AWAY_ENDED_HOLD_MS = 10_000L

private val PillShape = RoundedCornerShape(26.dp)
private val ActionShape = RoundedCornerShape(14.dp)

private enum class AwayEndPhase { Binary, Tier }

/**
 * 被动收束回顾：贴顶药丸，先二选一，偏航侧再可选细档。
 *
 * - 对齐侧（完成了 / 找到了 / 够了）→ 立刻存 ALIGNED 并收起
 * - 偏航侧 → 展开三档；选档后点完成，或超时已选档则自动保存
 * - 超时未答 → 不存档收起（可走提醒）
 */
@Composable
fun AwayEndedBarOverlay(
    appName: String,
    packageName: String,
    timeAgoLabel: String,
    durationSeconds: Long,
    purpose: String? = null,
    intentKind: com.life.mindfulnessapp.domain.model.IntentKind? = null,
    isDarkTheme: Boolean = true,
    showFastCompareHint: Boolean = false,
    onCompareSaved: (level: Int, note: String?, driftSeconds: Long?) -> Unit,
    onFinished: () -> Unit,
    onInputFocusChanged: (Boolean) -> Unit = {}
) {
    val companionPath = remember(intentKind, purpose) {
        CompanionPath.resolve(intentKind, purpose, hasSessionLimit = false)
    }
    val awarenessMode = remember(intentKind, purpose) {
        SessionAwarenessMode.from(intentKind, purpose)
    }
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(-16f) }
    val scale = remember { Animatable(0.96f) }
    val barProgress = remember { Animatable(1f) }
    var remainingSec by remember { mutableIntStateOf((AWAY_ENDED_HOLD_MS / 1000L).toInt()) }
    var finished by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(AwayEndPhase.Binary) }
    var selectedLevel by remember { mutableStateOf<Int?>(null) }
    var noteText by remember { mutableStateOf("") }
    var driftSeconds by remember { mutableStateOf<Long?>(null) }
    var noteInputActive by remember { mutableStateOf(false) }

    fun finishOnce(saved: Triple<Int, String?, Long?>?) {
        if (finished) return
        finished = true
        onInputFocusChanged(false)
        if (saved != null) {
            onCompareSaved(saved.first, saved.second, saved.third)
        } else {
            onFinished()
        }
    }

    suspend fun dismissAnimated(saved: Triple<Int, String?, Long?>?) {
        if (dismissing || finished) return
        dismissing = true
        coroutineScope {
            launch {
                scale.animateTo(0.94f, tween(300, easing = FastOutSlowInEasing))
            }
            launch {
                offsetY.animateTo(-22f, tween(320, easing = FastOutSlowInEasing))
            }
            launch {
                alpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            }
        }
        finishOnce(saved)
    }

    val shell = if (isDarkTheme) Color(0xF0181B20) else Color(0xF5FAFAFC)
    val border = if (isDarkTheme) Color.White.copy(alpha = 0.09f) else Color.Black.copy(alpha = 0.06f)
    val muted = if (isDarkTheme) Color(0xFF8B919A) else Color(0xFF6B6B6B)
    val ink = if (isDarkTheme) Color(0xFFECEEF2) else Color(0xFF1C1C1E)
    val purposeInk = if (isDarkTheme) Color(0xFFD4DAE3) else Color(0xFF2C2C2E)
    val progressTrack = if (isDarkTheme) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f)
    val progressActive = if (isDarkTheme) Color(0xFF6FAE90) else Color(0xFF3D8F6E)
    val primaryBtn = if (isDarkTheme) Color(0xFF3D8F6E) else Color(0xFF2F6B52)
    val accent = if (isDarkTheme) Color(0xFF7EB8A2) else Color(0xFF3D8F6E)
    val compareColors = remember(isDarkTheme) { CompareSectionColors.forAwayBar(isDarkTheme) }

    val displayName = appName.trim().ifBlank { "这个 App" }
    val timeLabel = timeAgoLabel.trim().ifBlank { "刚刚" }
    val intentLine = purpose?.trim()?.takeIf { it.isNotEmpty() }
    val durationText = formatAwayBarDuration(durationSeconds)
    val metaLine = buildString {
        if (intentLine != null) append(intentLine)
        if (durationText.isNotEmpty()) {
            if (isNotEmpty()) append(" · ")
            append(durationText)
        }
    }.ifBlank { null }
    val canComplete = MindfulnessLevel.isValid(selectedLevel)
    val dismissHint = when {
        phase == AwayEndPhase.Binary ->
            if (noteInputActive) "写备注中" else "${remainingSec.coerceAtLeast(0)}秒后轻收"
        noteInputActive -> "写备注中，已暂停倒计时"
        canComplete -> "可补备注；时间到将保存档位"
        else -> "${remainingSec.coerceAtLeast(0)}秒后自动收起"
    }

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        launch {
            alpha.animateTo(1f, tween(280, easing = FastOutSlowInEasing))
        }
        launch {
            scale.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
        }
        launch {
            offsetY.animateTo(0f, tween(340, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(noteInputActive) {
        onInputFocusChanged(noteInputActive)
    }

    LaunchedEffect(finished, noteInputActive, phase) {
        if (finished) return@LaunchedEffect
        var anchorProgress = barProgress.value
        var anchorMs = SystemClock.elapsedRealtime()
        while (!finished && barProgress.value > 0f) {
            if (noteInputActive) {
                anchorProgress = barProgress.value
                anchorMs = SystemClock.elapsedRealtime()
                delay(50L)
                continue
            }
            val elapsed = SystemClock.elapsedRealtime() - anchorMs
            val progress = (anchorProgress - elapsed.toFloat() / AWAY_ENDED_HOLD_MS)
                .coerceIn(0f, 1f)
            barProgress.snapTo(progress)
            remainingSec = ((progress * AWAY_ENDED_HOLD_MS + 999f) / 1000f).toInt()
            if (progress <= 0f) break
            delay(16L)
        }
        if (!finished) {
            val level = selectedLevel?.takeIf { MindfulnessLevel.isValid(it) }
            dismissAnimated(
                saved = level?.let {
                    Triple(it, noteText.trim().ifEmpty { null }, driftSeconds)
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .graphicsLayer {
                    this.alpha = alpha.value
                    translationY = offsetY.value.dp.toPx()
                    scaleX = scale.value
                    scaleY = scale.value
                }
                .fillMaxWidth()
                .clip(PillShape)
                .background(shell)
                .border(0.5.dp, border, PillShape)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 13.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = muted,
                        maxLines = 1
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Text(
                        text = displayName,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 6.dp)
                    )
                    AwayBarAppIcon(
                        packageName = packageName,
                        appName = displayName,
                        size = 18.dp
                    )
                }

                when (phase) {
                    AwayEndPhase.Binary -> {
                        EndCompareBinaryAsk(
                            path = companionPath,
                            metaLine = metaLine,
                            accent = primaryBtn,
                            ink = purposeInk,
                            muted = muted,
                            enabled = !dismissing,
                            isDarkTheme = isDarkTheme,
                            onAligned = {
                                scope.launch {
                                    dismissAnimated(
                                        saved = Triple(MindfulnessLevel.ALIGNED, null, null)
                                    )
                                }
                            },
                            onDrift = {
                                scope.launch {
                                    barProgress.snapTo(1f)
                                    remainingSec = (AWAY_ENDED_HOLD_MS / 1000L).toInt()
                                    phase = AwayEndPhase.Tier
                                }
                            }
                        )
                        if (showFastCompareHint) {
                            Text(
                                text = SessionAwarenessCopy.endCompareDoneChip(companionPath),
                                fontSize = 10.sp,
                                color = muted.copy(alpha = 0.72f),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                    AwayEndPhase.Tier -> {
                        if (metaLine != null) {
                            Text(
                                text = metaLine,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = purposeInk,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                        EndCompareTierPanel(
                            visible = true,
                            selectedLevel = selectedLevel,
                            onLevelSelected = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                selectedLevel = it
                            },
                            noteText = noteText,
                            onNoteChange = { noteText = it },
                            colors = compareColors,
                            awarenessMode = awarenessMode,
                            durationSeconds = durationSeconds,
                            driftSeconds = driftSeconds,
                            onDriftSecondsChange = { driftSeconds = it },
                            style = CompareTierStyle.CompactDivider,
                            enabled = !dismissing,
                            onNoteFocusChanged = { noteInputActive = it },
                            hint = "可选 · 点一档后完成"
                        )
                        AnimatedVisibility(
                            visible = canComplete,
                            enter = fadeIn(tween(220)) + expandVertically(tween(240)),
                            exit = fadeOut(tween(180)) + shrinkVertically(tween(200))
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(ActionShape)
                                    .background(primaryBtn)
                                    .clickable(enabled = !dismissing) {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        val level = selectedLevel ?: return@clickable
                                        scope.launch {
                                            dismissAnimated(
                                                saved = Triple(
                                                    level,
                                                    noteText.trim().ifEmpty { null },
                                                    driftSeconds
                                                )
                                            )
                                        }
                                    }
                                    .padding(vertical = 11.dp)
                            ) {
                                Text(
                                    text = "完成",
                                    fontSize = 14.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }

                Text(
                    text = dismissHint,
                    fontSize = 10.sp,
                    color = muted.copy(alpha = 0.72f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(progressTrack)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barProgress.value)
                        .fillMaxHeight()
                        .background(progressActive.copy(alpha = if (phase == AwayEndPhase.Binary) 0.55f else 0.9f))
                )
            }
        }
    }
}

@Composable
private fun AwayBarAppIcon(
    packageName: String,
    appName: String,
    size: androidx.compose.ui.unit.Dp
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        if (packageName.isNotEmpty()) {
            try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (_: Exception) {
                null
            }
        } else {
            null
        }
    }
    val iconBitmap = remember(appIcon) {
        appIcon?.toBitmap(64, 64)?.asImageBitmap()
    }
    val corner = RoundedCornerShape(5.dp)

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
                .background(Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = appName.take(1),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White.copy(alpha = 0.8f)
            )
        }
    }
}

private fun formatAwayBarDuration(seconds: Long): String {
    if (seconds <= 0L) return "0分钟"
    val totalMin = seconds / 60L
    return when {
        totalMin < 60L -> "${totalMin.coerceAtLeast(1L)}分钟"
        else -> {
            val h = totalMin / 60L
            val m = totalMin % 60L
            if (m == 0L) "${h}小时" else "${h}小时${m}分"
        }
    }
}
