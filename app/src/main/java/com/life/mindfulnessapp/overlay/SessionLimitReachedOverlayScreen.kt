package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.CommonIntentsCodec
import com.life.mindfulnessapp.domain.model.CompanionPath
import com.life.mindfulnessapp.domain.model.IntentKind
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 单次时长到点。
 * - 意图 / 搜索：离开 · 续一点时间（滑尺）
 * - 随意浏览：离开（主）· 再需要一点时间（次）→ 命名半门 + 滑尺 → 转意图续段
 */
@Composable
fun SessionLimitReachedOverlayScreen(
    appName: String,
    packageName: String = "",
    purpose: String?,
    committedMinutes: Int,
    @Suppress("UNUSED_PARAMETER") durationSeconds: Long = committedMinutes * 60L,
    intentKind: IntentKind? = null,
    canExtend: Boolean = false,
    maxExtendMinutes: Int = 0,
    @Suppress("UNUSED_PARAMETER") compareEnabled: Boolean = true,
    @Suppress("UNUSED_PARAMETER") compareMinMinutes: Int = 10,
    themePack: ThemePack = ThemePack.Night,
    @Suppress("UNUSED_PARAMETER") isDarkTheme: Boolean = themePack.isDark,
    @Suppress("UNUSED_PARAMETER") onConfirm: (mindfulnessLevel: Int?, note: String?, driftSeconds: Long?) -> Unit,
    /** namedPurpose 非空 = 随意浏览命名升级后续时 */
    onExtend: (extraMinutes: Int, namedPurpose: String?) -> Unit = { _, _ -> },
    onReviewLater: () -> Unit = {}
) {
    val isBrowse = CompanionPath.resolve(
        intentKind = intentKind,
        purpose = purpose,
        hasSessionLimit = true
    ) == CompanionPath.BROWSE || BrowseCasualIntent.isBrowseLike(purpose.orEmpty())

    var showContent by remember { mutableStateOf(false) }
    var isActing by remember { mutableStateOf(false) }
    var phase by remember {
        mutableStateOf(SessionLimitPhase.Door)
    }
    val namingShown = when {
        isBrowse -> BrowseCasualIntent.displayLabel(purpose)
        else -> purpose?.trim().orEmpty()
    }
    val extendCeiling = maxExtendMinutes.coerceAtLeast(0)
    val defaultPick = SessionLimitPolicy.MIN_SESSION_MINUTES.coerceAtMost(
        extendCeiling.coerceAtLeast(SessionLimitPolicy.MIN_SESSION_MINUTES)
    )
    var pickMinutes by remember(extendCeiling) { mutableIntStateOf(defaultPick) }
    var draftPurpose by remember { mutableStateOf("") }
    var quickTags by remember { mutableStateOf<List<String>>(emptyList()) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        delay(80)
        showContent = true
    }

    LaunchedEffect(packageName, phase) {
        if (phase != SessionLimitPhase.BrowseName || packageName.isBlank()) return@LaunchedEffect
        val labels = withContext(Dispatchers.IO) {
            val entry = EntryPointAccessors.fromApplication(
                context.applicationContext,
                InterceptOverlayEntryPoint::class.java
            )
            entry.appLimitRepository().getCommonIntents(packageName)
                .asSequence()
                .filter { it.showOnGate }
                .map { it.label.trim() }
                .filter { it.isNotEmpty() && !BrowseCasualIntent.isBrowseLike(it) }
                .distinct()
                .take(CommonIntentsCodec.MAX_GATE_VISIBLE)
                .toList()
        }
        quickTags = labels
    }

    fun leave() {
        if (isActing) return
        isActing = true
        onReviewLater()
    }

    fun openDirectExtendPicker() {
        if (isActing || !canExtend || extendCeiling <= 0 || isBrowse) return
        phase = SessionLimitPhase.DirectExtend
        pickMinutes = defaultPick.coerceIn(1, extendCeiling)
    }

    fun openBrowseNameGate() {
        if (isActing || !canExtend || extendCeiling <= 0 || !isBrowse) return
        phase = SessionLimitPhase.BrowseName
        pickMinutes = defaultPick.coerceIn(1, extendCeiling)
        draftPurpose = ""
    }

    fun confirmDirectExtend() {
        if (isActing) return
        if (pickMinutes !in SessionLimitPolicy.MIN_SESSION_MINUTES..extendCeiling) return
        isActing = true
        onExtend(pickMinutes, null)
    }

    fun confirmBrowseNamedExtend() {
        if (isActing) return
        val naming = draftPurpose.trim()
        if (naming.isEmpty() || BrowseCasualIntent.isBrowseLike(naming)) return
        if (pickMinutes !in SessionLimitPolicy.MIN_SESSION_MINUTES..extendCeiling) return
        isActing = true
        onExtend(pickMinutes, naming)
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
            when (phase) {
                SessionLimitPhase.Door -> SessionLimitDoorPhase(
                    showContent = showContent,
                    appName = appName,
                    committedMinutes = committedMinutes,
                    namingShown = namingShown,
                    isBrowse = isBrowse,
                    canExtend = canExtend && extendCeiling > 0,
                    isActing = isActing,
                    onLeave = { leave() },
                    onRequestMoreTime = {
                        if (isBrowse) openBrowseNameGate() else openDirectExtendPicker()
                    }
                )
                SessionLimitPhase.DirectExtend -> SessionLimitDirectExtendPhase(
                    showContent = showContent,
                    appName = appName,
                    committedMinutes = committedMinutes,
                    namingShown = namingShown,
                    pickMinutes = pickMinutes,
                    extendCeiling = extendCeiling,
                    isActing = isActing,
                    onPick = {
                        pickMinutes = it.coerceIn(
                            SessionLimitPolicy.MIN_SESSION_MINUTES,
                            extendCeiling
                        )
                    },
                    onBack = { if (!isActing) phase = SessionLimitPhase.Door },
                    onConfirm = { confirmDirectExtend() }
                )
                SessionLimitPhase.BrowseName -> SessionLimitBrowseNamePhase(
                    showContent = showContent,
                    appName = appName,
                    committedMinutes = committedMinutes,
                    draftPurpose = draftPurpose,
                    onDraftChange = { draftPurpose = it.take(CommonIntentsCodec.MAX_LABEL) },
                    quickTags = quickTags,
                    onPickTag = { draftPurpose = it },
                    pickMinutes = pickMinutes,
                    extendCeiling = extendCeiling,
                    isActing = isActing,
                    onPickMinutes = {
                        pickMinutes = it.coerceIn(
                            SessionLimitPolicy.MIN_SESSION_MINUTES,
                            extendCeiling
                        )
                    },
                    onBack = { if (!isActing) phase = SessionLimitPhase.Door },
                    onEnter = { confirmBrowseNamedExtend() }
                )
            }
        }
      }
    }
}

private enum class SessionLimitPhase {
    Door,
    DirectExtend,
    BrowseName,
}

@Composable
private fun ColumnScope.SessionLimitDoorPhase(
    showContent: Boolean,
    appName: String,
    committedMinutes: Int,
    namingShown: String,
    isBrowse: Boolean,
    canExtend: Boolean,
    isActing: Boolean,
    onLeave: () -> Unit,
    onRequestMoreTime: () -> Unit,
) {
    AnimatedVisibility(visible = showContent, enter = fadeIn(tween(280))) {
        Text(
            text = appName.ifBlank { "这个 App" },
            fontSize = 11.sp,
            color = LockInk.mist
        )
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        if (showContent) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "这次到了",
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = LockInk.paper
                )
                Text(
                    text = "$committedMinutes",
                    modifier = Modifier.padding(top = 28.dp),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = LockInk.paper,
                    letterSpacing = (-1.2).sp
                )
                Text(
                    text = "分",
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 13.sp,
                    color = LockInk.fog
                )
                if (namingShown.isNotEmpty()) {
                    Text(
                        text = namingShown,
                        modifier = Modifier.padding(top = 16.dp),
                        fontSize = 13.sp,
                        color = if (isBrowse) LockInk.fog.copy(alpha = 0.85f) else LockInk.fog,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
    AnimatedVisibility(visible = showContent, enter = fadeIn(tween(280))) {
        Column(modifier = Modifier.fillMaxWidth()) {
            StopDoorQuoteFootnote(modifier = Modifier.padding(bottom = 18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "离开",
                    fontSize = 16.sp,
                    color = if (!isActing) LockInk.leafText else LockInk.mist,
                    modifier = Modifier.clickable(
                        enabled = !isActing,
                        role = Role.Button,
                        onClick = onLeave
                    )
                )
                if (canExtend) {
                    Text(
                        text = if (isBrowse) "再需要一点时间" else "续一点时间",
                        fontSize = 16.sp,
                        color = if (!isActing) LockInk.fog else LockInk.mist,
                        modifier = Modifier.clickable(
                            enabled = !isActing,
                            role = Role.Button,
                            onClick = onRequestMoreTime
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.SessionLimitDirectExtendPhase(
    showContent: Boolean,
    appName: String,
    committedMinutes: Int,
    namingShown: String,
    pickMinutes: Int,
    extendCeiling: Int,
    isActing: Boolean,
    onPick: (Int) -> Unit,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val canConfirm = pickMinutes in SessionLimitPolicy.MIN_SESSION_MINUTES..extendCeiling
    AnimatedVisibility(visible = showContent, enter = fadeIn(tween(280))) {
        Text(
            text = appName.ifBlank { "这个 App" },
            fontSize = 11.sp,
            color = LockInk.mist
        )
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.CenterStart
    ) {
        if (showContent) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "这次到了",
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = LockInk.paper
                )
                Text(
                    text = "$committedMinutes",
                    modifier = Modifier.padding(top = 28.dp),
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Light,
                    color = LockInk.paper,
                    letterSpacing = (-1.2).sp
                )
                Text(
                    text = "分",
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 13.sp,
                    color = LockInk.fog
                )
                if (namingShown.isNotEmpty()) {
                    Text(
                        text = namingShown,
                        modifier = Modifier.padding(top = 16.dp),
                        fontSize = 13.sp,
                        color = LockInk.fog,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "再留多久",
                    modifier = Modifier.padding(top = 36.dp, bottom = 16.dp),
                    fontSize = 13.sp,
                    color = LockInk.fog
                )
                DoorDurationRuler(
                    selectedMinutes = pickMinutes.coerceIn(1, extendCeiling),
                    onSelect = onPick,
                    minMinutes = SessionLimitPolicy.MIN_SESSION_MINUTES,
                    maxMinutes = extendCeiling
                )
            }
        }
    }
    AnimatedVisibility(visible = showContent, enter = fadeIn(tween(280))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "返回",
                fontSize = 16.sp,
                color = if (!isActing) LockInk.mist else LockInk.mist.copy(alpha = 0.5f),
                modifier = Modifier.clickable(
                    enabled = !isActing,
                    role = Role.Button,
                    onClick = onBack
                )
            )
            Text(
                text = "续上",
                fontSize = 16.sp,
                color = when {
                    isActing -> LockInk.mist
                    canConfirm -> LockInk.paper
                    else -> LockInk.mist
                },
                modifier = Modifier.clickable(
                    enabled = !isActing && canConfirm,
                    role = Role.Button,
                    onClick = onConfirm
                )
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.SessionLimitBrowseNamePhase(
    showContent: Boolean,
    appName: String,
    committedMinutes: Int,
    draftPurpose: String,
    onDraftChange: (String) -> Unit,
    quickTags: List<String>,
    onPickTag: (String) -> Unit,
    pickMinutes: Int,
    extendCeiling: Int,
    isActing: Boolean,
    onPickMinutes: (Int) -> Unit,
    onBack: () -> Unit,
    onEnter: () -> Unit,
) {
    val namingOk = draftPurpose.trim().isNotEmpty() &&
        !BrowseCasualIntent.isBrowseLike(draftPurpose)
    val canEnter = namingOk &&
        pickMinutes in SessionLimitPolicy.MIN_SESSION_MINUTES..extendCeiling

    AnimatedVisibility(visible = showContent, enter = fadeIn(tween(280))) {
        Text(
            text = appName.ifBlank { "这个 App" },
            fontSize = 11.sp,
            color = LockInk.mist
        )
    }
    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxWidth(),
        contentAlignment = Alignment.TopStart
    ) {
        if (showContent) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "再需要一点时间",
                    fontFamily = FontFamily.Serif,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    color = LockInk.paper
                )
                Text(
                    text = "刚才 · ${BrowseCasualIntent.DISPLAY_LABEL} · ${committedMinutes} 分",
                    modifier = Modifier.padding(top = 8.dp),
                    fontSize = 13.sp,
                    color = LockInk.fog
                )
                BrowseExtendPurposeField(
                    value = draftPurpose,
                    onValueChange = onDraftChange,
                    modifier = Modifier.padding(top = 22.dp)
                )
                if (quickTags.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        quickTags.forEach { label ->
                            val on = draftPurpose.trim() == label
                            Text(
                                text = label,
                                fontSize = 12.sp,
                                color = if (on) LockInk.leafText else LockInk.fog,
                                modifier = Modifier
                                    .border(
                                        width = 1.dp,
                                        color = if (on) {
                                            LockInk.leafText.copy(alpha = 0.45f)
                                        } else {
                                            LockInk.line
                                        },
                                        shape = RoundedCornerShape(999.dp)
                                    )
                                    .background(
                                        if (on) LockInk.leafSoft else LockInk.bg,
                                        RoundedCornerShape(999.dp)
                                    )
                                    .clickable(
                                        enabled = !isActing,
                                        role = Role.Button,
                                        onClick = { onPickTag(label) }
                                    )
                                    .padding(horizontal = 11.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
                Text(
                    text = "再留多久",
                    modifier = Modifier.padding(top = 28.dp, bottom = 14.dp),
                    fontSize = 13.sp,
                    color = LockInk.fog
                )
                DoorDurationRuler(
                    selectedMinutes = pickMinutes.coerceIn(1, extendCeiling),
                    onSelect = onPickMinutes,
                    minMinutes = SessionLimitPolicy.MIN_SESSION_MINUTES,
                    maxMinutes = extendCeiling
                )
            }
        }
    }
    AnimatedVisibility(visible = showContent, enter = fadeIn(tween(280))) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "返回",
                fontSize = 16.sp,
                color = if (!isActing) LockInk.mist else LockInk.mist.copy(alpha = 0.5f),
                modifier = Modifier.clickable(
                    enabled = !isActing,
                    role = Role.Button,
                    onClick = onBack
                )
            )
            Text(
                text = "进入",
                fontSize = 16.sp,
                color = when {
                    isActing -> LockInk.mist
                    canEnter -> LockInk.paper
                    else -> LockInk.mist
                },
                modifier = Modifier.clickable(
                    enabled = !isActing && canEnter,
                    role = Role.Button,
                    onClick = onEnter
                )
            )
        }
    }
}

@Composable
private fun BrowseExtendPurposeField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val underline = Modifier.drawBehind {
        val y = size.height - 1.dp.toPx()
        drawLine(
            color = LockInk.line,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
    }
    Box(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .then(underline)
                .padding(bottom = 8.dp),
            textStyle = TextStyle(color = LockInk.paper, fontSize = 15.sp),
            cursorBrush = SolidColor(LockInk.paper),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {}),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text("这一次要做什么", color = LockInk.mist, fontSize = 15.sp)
                    }
                    inner()
                }
            }
        )
    }
}
