package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.GatePathTeach
import com.life.mindfulnessapp.domain.model.IntentGateAction
import com.life.mindfulnessapp.domain.model.LeaveRitual
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy

/** 刷逛门：搜 / 写意图；有可靠搜索深链时才出第三路「刷」。只有刷才定时长。 */
internal enum class BreathGateFace {
    Fork,
    Search,
    Intent,
    Browse,
    TagsManage,
}

/** 三路明暗：搜最亮 → 写意图次之 → 随意浏览最淡。 */
internal enum class ForkPathEmphasis {
    Primary,
    Secondary,
    Tertiary,
}

internal val BrowseDoorMinutes = listOf(3, 5, 10)

/**
 * 随意浏览可选时长。
 * 常规档 3 / 5 / 10；若日剩余压在最短档以下但仍 ≥ 1 分，给出剩余分钟作为临时档，
 * 好让最后一点额度还能用掉。
 */
internal fun browseDoorMarks(maxSessionMinutes: Int): List<Int> {
    val ceiling = maxSessionMinutes.coerceAtLeast(0)
    val marks = BrowseDoorMinutes.filter { it <= ceiling }
    if (marks.isNotEmpty()) return marks
    return if (ceiling >= SessionLimitPolicy.MIN_SESSION_MINUTES) listOf(ceiling) else emptyList()
}

@Composable
internal fun BreathGateForkFace(
    factsLabel: String,
    canSearch: Boolean,
    enabled: Boolean,
    /** 目录有可靠搜索深链才出「随意浏览」；普通 App 只留写下意图 */
    offerBrowse: Boolean = false,
    canBrowse: Boolean = true,
    browseCooldownMinutes: Int = 0,
    /** 有名意图 / 搜索未闭环时的弱续文案；空则不出 */
    resumePurposeLabel: String = "",
    /** 正在门内呼气收束：离开字略亮 */
    leaveHolding: Boolean = false,
    /** 是否挂教学副句（新 App 按天递减）；状态句不受此限 */
    showTeachSubtitles: Boolean = true,
    onSearch: () -> Unit,
    onIntent: () -> Unit,
    onBrowse: () -> Unit,
    onResume: (() -> Unit)? = null,
    onCancel: () -> Unit,
) {
    val resumeClick = onResume
    val showResume = resumePurposeLabel.isNotBlank() && resumeClick != null
    val browseStatus = when {
        !canBrowse -> "今日额度已用完"
        browseCooldownMinutes > 0 -> "冷却中 · 约 ${browseCooldownMinutes} 分钟"
        else -> null
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "你这次要做什么？",
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = LockInk.paper,
                lineHeight = 34.sp
            )
            if (factsLabel.isNotBlank()) {
                Text(
                    text = factsLabel,
                    modifier = Modifier.padding(top = 10.dp),
                    fontSize = 13.sp,
                    color = LockInk.fog
                )
            }
            Spacer(Modifier.height(28.dp))
            if (canSearch) {
                ForkPathRow(
                    glyph = "⌕",
                    title = "搜索直达",
                    subtitle = if (showTeachSubtitles) GatePathTeach.SEARCH_SUBTITLE else "",
                    emphasis = ForkPathEmphasis.Primary,
                    enabled = enabled,
                    onClick = onSearch
                )
                Spacer(Modifier.height(18.dp))
            }
            ForkPathRow(
                glyph = "✎",
                title = "写下意图",
                subtitle = if (showTeachSubtitles) GatePathTeach.INTENT_SUBTITLE else "",
                emphasis = if (canSearch) {
                    ForkPathEmphasis.Secondary
                } else {
                    ForkPathEmphasis.Primary
                },
                enabled = enabled,
                onClick = onIntent
            )
            if (offerBrowse) {
                Spacer(Modifier.height(18.dp))
                ForkPathRow(
                    glyph = "≡",
                    title = BrowseCasualIntent.DISPLAY_LABEL,
                    subtitle = browseStatus
                        ?: if (showTeachSubtitles) GatePathTeach.BROWSE_SUBTITLE else "",
                    emphasis = ForkPathEmphasis.Tertiary,
                    enabled = enabled && canBrowse && browseCooldownMinutes <= 0,
                    onClick = onBrowse
                )
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (showResume && resumeClick != null) {
                SoftResumeAffirm(
                    purposeLabel = resumePurposeLabel,
                    enabled = enabled,
                    onClick = resumeClick
                )
                Spacer(Modifier.height(10.dp))
            }
            SoftLeaveAffirm(
                enabled = enabled,
                holding = leaveHolding,
                onClick = onCancel
            )
        }
    }
}

/**
 * 未闭环有名意图的弱续：只报「刚刚 · 意图」，不报中断原因；不抢三路。
 */
@Composable
private fun SoftResumeAffirm(
    purposeLabel: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "刚刚 · $purposeLabel",
            fontSize = 13.sp,
            color = if (enabled) {
                LockInk.fog.copy(alpha = 0.9f)
            } else {
                LockInk.mist.copy(alpha = 0.4f)
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = "  继续",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) {
                LockInk.leafText.copy(alpha = 0.88f)
            } else {
                LockInk.mist.copy(alpha = 0.4f)
            }
        )
    }
}

/** 离开是肯定选择：叶色字链，无框无底，不抢三路。副句固定，不进轮换池。 */
@Composable
private fun SoftLeaveAffirm(
    enabled: Boolean,
    holding: Boolean = false,
    onClick: () -> Unit,
) {
    val mainAlpha = when {
        !enabled -> 0.5f
        holding -> 1f
        else -> 1f
    }
    val subAlpha = when {
        !enabled -> 0.35f
        holding -> 0.92f
        else -> 0.55f
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = enabled && !holding, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = "先不进去了",
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled || holding) {
                LockInk.leafText.copy(alpha = mainAlpha)
            } else {
                LockInk.mist.copy(alpha = 0.5f)
            },
            letterSpacing = 0.3.sp
        )
        Text(
            text = LeaveRitual.GATE_SUBTITLE,
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            color = if (enabled || holding) {
                LockInk.leafText.copy(alpha = subAlpha)
            } else {
                LockInk.mist.copy(alpha = 0.35f)
            }
        )
    }
}

@Composable
private fun ForkPathRow(
    glyph: String,
    title: String,
    subtitle: String,
    emphasis: ForkPathEmphasis,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val (titleColor, subColor, glyphColor, titleSize, titleWeight) = forkPathColors(emphasis, enabled)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = glyph,
            fontSize = 15.sp,
            color = glyphColor,
            modifier = Modifier.padding(top = 1.dp)
        )
        Column {
            Text(
                text = title,
                fontSize = titleSize,
                fontWeight = titleWeight,
                color = titleColor
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    modifier = Modifier.padding(top = 3.dp),
                    fontSize = 12.sp,
                    color = subColor
                )
            }
        }
    }
}

private data class ForkPathColors(
    val title: Color,
    val subtitle: Color,
    val glyph: Color,
    val titleSize: TextUnit,
    val titleWeight: FontWeight,
)

private fun forkPathColors(
    emphasis: ForkPathEmphasis,
    enabled: Boolean,
): ForkPathColors {
    if (!enabled) {
        return ForkPathColors(
            title = LockInk.mist.copy(alpha = 0.45f),
            // 副文常是原因（冷却 / 额度），需可读；标题保持淡表示不可点
            subtitle = LockInk.mist.copy(alpha = 0.72f),
            glyph = LockInk.mist.copy(alpha = 0.35f),
            titleSize = 16.sp,
            titleWeight = FontWeight.Normal
        )
    }
    return when (emphasis) {
        ForkPathEmphasis.Primary -> ForkPathColors(
            title = LockInk.paper,
            subtitle = LockInk.fog,
            glyph = LockInk.paper.copy(alpha = 0.88f),
            titleSize = 17.sp,
            titleWeight = FontWeight.Medium
        )
        ForkPathEmphasis.Secondary -> ForkPathColors(
            title = LockInk.paper.copy(alpha = 0.78f),
            subtitle = LockInk.mist.copy(alpha = 0.92f),
            glyph = LockInk.fog.copy(alpha = 0.85f),
            titleSize = 16.sp,
            titleWeight = FontWeight.Normal
        )
        // 仍轻于主路，但可读、可点——不是 disabled
        ForkPathEmphasis.Tertiary -> ForkPathColors(
            title = LockInk.fog.copy(alpha = 0.9f),
            subtitle = LockInk.mist.copy(alpha = 0.88f),
            glyph = LockInk.fog.copy(alpha = 0.78f),
            titleSize = 16.sp,
            titleWeight = FontWeight.Normal
        )
    }
}

@Composable
internal fun BreathGateSearchFace(
    query: String,
    onQueryChange: (String) -> Unit,
    recentItems: List<GateRecentItem> = emptyList(),
    enabled: Boolean,
    onSearch: () -> Unit,
    onBack: () -> Unit,
    onDeleteRecent: ((String) -> Unit)? = null,
) {
    val trimmed = query.trim()
    val ready = enabled && trimmed.replace(" ", "").length >= 2
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "你要搜什么？",
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = LockInk.paper,
                lineHeight = 34.sp
            )
            SoftDoorUnderlineField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = "输入搜索的内容",
                enabled = enabled,
                focusRequester = focus,
                imeAction = ImeAction.Search,
                onIme = { if (ready) onSearch() },
                modifier = Modifier.padding(top = 18.dp)
            )
            GateRecentBlock(
                items = recentItems,
                currentText = trimmed,
                enabled = enabled,
                collapsedCount = GateSearchRecentCollapsed,
                onPick = onQueryChange,
                onDelete = onDeleteRecent
            )
        }
        SoftDoorPairActions(
            primary = "搜索",
            primaryReady = ready,
            onPrimary = {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                onSearch()
            },
            secondary = "返回",
            secondaryEnabled = enabled,
            onSecondary = {
                focusManager.clearFocus(force = true)
                keyboard?.hide()
                onBack()
            }
        )
    }
}

/** 门口「最近」一行 */
internal data class GateRecentItem(
    val purpose: String,
    val whenLabel: String,
)

private const val GateRecentCollapsed = 1
private const val GateSearchRecentCollapsed = 3
private const val GateRecentExpandedMax = 5

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GateRecentBlock(
    items: List<GateRecentItem>,
    currentText: String,
    enabled: Boolean,
    collapsedCount: Int,
    onPick: (String) -> Unit,
    onDelete: ((String) -> Unit)?,
) {
    val recents = remember(items) {
        items
            .map { it.copy(purpose = it.purpose.trim()) }
            .filter { it.purpose.isNotEmpty() }
            .distinctBy { it.purpose }
            .take(GateRecentExpandedMax)
    }
    var recentExpanded by remember { mutableStateOf(false) }
    if (recents.isEmpty()) return
    val visibleRecents = if (recentExpanded) recents else recents.take(collapsedCount)
    val canExpandRecent = recents.size > collapsedCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 22.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "最近",
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = LockInk.mist
        )
        if (canExpandRecent) {
            Text(
                text = if (recentExpanded) "收起" else "展开",
                fontSize = 12.sp,
                color = LockInk.mist,
                modifier = Modifier.clickable(
                    enabled = enabled,
                    role = Role.Button
                ) { recentExpanded = !recentExpanded }
            )
        }
    }
    visibleRecents.forEach { item ->
        val on = currentText == item.purpose
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .combinedClickable(
                    enabled = enabled,
                    onClick = { onPick(item.purpose) },
                    onLongClick = { onDelete?.invoke(item.purpose) }
                ),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = item.purpose,
                fontSize = 14.sp,
                color = when {
                    !enabled -> LockInk.mist.copy(alpha = 0.4f)
                    on -> LockInk.leafText
                    else -> LockInk.fog
                },
                modifier = Modifier.weight(1f)
            )
            Text(
                text = item.whenLabel,
                fontSize = 11.sp,
                color = LockInk.mist
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BreathGateIntentFace(
    text: String,
    onTextChange: (String) -> Unit,
    tags: List<IntentGateAction>,
    recentItems: List<GateRecentItem>,
    enabled: Boolean,
    onEnter: () -> Unit,
    onBack: () -> Unit,
    onDeleteRecent: ((String) -> Unit)? = null,
    onAdjustTags: (() -> Unit)? = null,
    onPickTag: ((IntentGateAction) -> Unit)? = null,
) {
    val trimmed = text.trim()
    val ready = enabled && trimmed.isNotEmpty()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    // 进场不挂可聚焦字段 → 光标用假闪烁表示，键盘不弹；点框才真正编辑
    var editing by remember { mutableStateOf(false) }
    val anyDeepLink = tags.any { it.canDeepLink }

    LaunchedEffect(editing) {
        if (editing) {
            focus.requestFocus()
            keyboard?.show()
        }
    }

    fun endEditing() {
        editing = false
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { endEditing() }
                ),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "你要干什么？",
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = LockInk.paper,
                lineHeight = 34.sp
            )
            if (editing) {
                SoftDoorUnderlineField(
                    value = text,
                    onValueChange = onTextChange,
                    placeholder = "这一次…",
                    enabled = enabled,
                    focusRequester = focus,
                    imeAction = ImeAction.Done,
                    onIme = {
                        if (ready) onEnter() else endEditing()
                    },
                    modifier = Modifier.padding(top = 18.dp),
                    requestFocusOnMount = false
                )
            } else {
                SoftDoorIdleField(
                    value = text,
                    placeholder = "这一次…",
                    enabled = enabled,
                    onActivate = { if (enabled) editing = true },
                    modifier = Modifier.padding(top = 18.dp)
                )
            }

            if (tags.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "快捷标签",
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp,
                        color = LockInk.mist
                    )
                    if (onAdjustTags != null) {
                        Text(
                            text = "调整",
                            fontSize = 12.sp,
                            color = LockInk.mist,
                            modifier = Modifier.clickable(
                                enabled = enabled,
                                role = Role.Button,
                                onClick = onAdjustTags
                            )
                        )
                    }
                }
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    tags.forEach { tag ->
                        val label = tag.label.trim()
                        val on = trimmed == label
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable(
                                enabled = enabled,
                                role = Role.Button
                            ) {
                                if (onPickTag != null) onPickTag(tag)
                                else onTextChange(if (on) "" else tag.label)
                                endEditing()
                            }
                        ) {
                            if (tag.canDeepLink) {
                                Box(
                                    modifier = Modifier
                                        .padding(end = 6.dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(
                                            when {
                                                !enabled -> LockInk.mist.copy(alpha = 0.35f)
                                                on -> LockInk.leafText
                                                else -> LockInk.leafText.copy(alpha = 0.85f)
                                            }
                                        )
                                )
                            }
                            Text(
                                text = tag.label,
                                fontSize = 13.sp,
                                color = when {
                                    !enabled -> LockInk.mist.copy(alpha = 0.4f)
                                    on -> LockInk.leafText
                                    else -> LockInk.fog
                                }
                            )
                        }
                    }
                }
                if (anyDeepLink) {
                    Text(
                        text = "带叶点的，进入后会尽量直达对应页",
                        modifier = Modifier.padding(top = 10.dp),
                        fontSize = 11.sp,
                        color = LockInk.mist,
                        lineHeight = 16.sp
                    )
                }
            } else if (onAdjustTags != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 22.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "快捷标签",
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp,
                        color = LockInk.mist
                    )
                    Text(
                        text = "去添加",
                        fontSize = 12.sp,
                        color = LockInk.leafText,
                        modifier = Modifier.clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onAdjustTags
                        )
                    )
                }
                Text(
                    text = "还没有标签",
                    modifier = Modifier.padding(top = 12.dp),
                    fontSize = 13.sp,
                    color = LockInk.fog
                )
            }

            // 最近是辅助复用，跟在标签后面；默认 1 条，可展开
            GateRecentBlock(
                items = recentItems,
                currentText = trimmed,
                enabled = enabled,
                collapsedCount = GateRecentCollapsed,
                onPick = {
                    onTextChange(it)
                    endEditing()
                },
                onDelete = onDeleteRecent
            )
        }

        SoftDoorPairActions(
            primary = "进入",
            primaryReady = ready,
            onPrimary = {
                endEditing()
                onEnter()
            },
            secondary = "返回",
            secondaryEnabled = enabled,
            onSecondary = {
                endEditing()
                onBack()
            }
        )
    }
}

@Composable
internal fun BreathGateBrowseFace(
    selectedMinutes: Int,
    onSelectMinutes: (Int) -> Unit,
    /** 今日已刷整分；与 [limitMinutes] 组成 x/y */
    usedMinutes: Int = 0,
    /** 随意浏览日限；null / ≤0 = 不限，不写 x/y */
    limitMinutes: Int? = null,
    maxSessionMinutes: Int,
    cooldownMinutes: Int = 0,
    enabled: Boolean,
    onEnter: () -> Unit,
    onBack: () -> Unit,
) {
    val marks = remember(maxSessionMinutes) { browseDoorMarks(maxSessionMinutes) }
    val cooling = cooldownMinutes > 0
    val ready = enabled && !cooling && selectedMinutes > 0 && marks.contains(selectedMinutes)
    val budgetLimit = limitMinutes?.takeIf { it > 0 }
    val budgetLine = budgetLimit?.let { limit ->
        "今日 ${usedMinutes.coerceAtLeast(0)}/$limit"
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (cooling) "先歇一会儿" else "这次多久？",
                fontFamily = FontFamily.Serif,
                fontSize = 26.sp,
                fontWeight = FontWeight.Medium,
                color = LockInk.paper,
                lineHeight = 34.sp
            )
            if (cooling) {
                Text(
                    text = "冷却中 · 约 ${cooldownMinutes} 分钟",
                    modifier = Modifier.padding(top = 22.dp),
                    fontSize = 14.sp,
                    color = LockInk.mist
                )
            } else if (marks.isEmpty()) {
                Text(
                    text = "今日额度已用完",
                    modifier = Modifier.padding(top = 22.dp),
                    fontSize = 14.sp,
                    color = LockInk.mist
                )
            } else {
                Row(
                    modifier = Modifier.padding(top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(22.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    marks.forEach { mark ->
                        val on = selectedMinutes == mark
                        Text(
                            text = "$mark",
                            fontSize = if (on) 24.sp else 22.sp,
                            fontWeight = FontWeight.Light,
                            color = when {
                                !enabled -> LockInk.mist.copy(alpha = 0.4f)
                                on -> LockInk.leafText
                                else -> LockInk.mist
                            },
                            modifier = Modifier.clickable(
                                enabled = enabled,
                                role = Role.Button
                            ) { onSelectMinutes(mark) }
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
            if (budgetLine != null && !cooling) {
                Text(
                    text = budgetLine,
                    modifier = Modifier.padding(top = 16.dp),
                    fontSize = 12.sp,
                    color = LockInk.fog
                )
            }
        }
        SoftDoorPairActions(
            primary = "进入",
            primaryReady = ready,
            onPrimary = onEnter,
            secondary = "返回",
            secondaryEnabled = enabled,
            onSecondary = onBack
        )
    }
}

@Composable
private fun SoftDoorPairActions(
    primary: String,
    primaryReady: Boolean,
    onPrimary: () -> Unit,
    secondary: String,
    secondaryEnabled: Boolean,
    onSecondary: () -> Unit,
) {
    // 左次右主：返回 / 进入·搜索，贴右手拇指
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = secondary,
            fontSize = 15.sp,
            color = LockInk.mist,
            modifier = Modifier.clickable(
                enabled = secondaryEnabled,
                role = Role.Button,
                onClick = onSecondary
            )
        )
        Text(
            text = primary,
            fontSize = 16.sp,
            fontWeight = if (primaryReady) FontWeight.Medium else FontWeight.Normal,
            color = if (primaryReady) LockInk.leafText else LockInk.mist,
            modifier = Modifier.clickable(
                enabled = primaryReady,
                role = Role.Button,
                onClick = onPrimary
            )
        )
    }
}

@Composable
private fun SoftDoorIdleField(
    value: String,
    placeholder: String,
    enabled: Boolean,
    onActivate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val blink = rememberInfiniteTransition(label = "idle_caret")
    val caretAlpha by blink.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 530),
            repeatMode = RepeatMode.Reverse
        ),
        label = "caret_alpha"
    )
    val underline = Modifier.drawBehind {
        val y = size.height - 1.dp.toPx()
        drawLine(
            color = LockInk.line,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.dp.toPx()
        )
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(underline)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onActivate
            )
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = LockInk.mist, fontSize = 16.sp)
            Box(
                modifier = Modifier
                    .padding(start = 1.dp)
                    .width(1.5.dp)
                    .height(16.dp)
                    .alpha(if (enabled) caretAlpha else 0.35f)
                    .background(LockInk.paper)
            )
        } else {
            Text(value, color = LockInk.paper, fontSize = 16.sp)
            Box(
                modifier = Modifier
                    .padding(start = 1.dp)
                    .width(1.5.dp)
                    .height(16.dp)
                    .alpha(if (enabled) caretAlpha else 0.35f)
                    .background(LockInk.paper)
            )
        }
    }
}

@Composable
private fun SoftDoorUnderlineField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    focusRequester: FocusRequester,
    imeAction: ImeAction,
    onIme: () -> Unit,
    modifier: Modifier = Modifier,
    requestFocusOnMount: Boolean = true,
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
    if (requestFocusOnMount) {
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
        }
    }
    Box(modifier = modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(underline)
                .padding(bottom = 8.dp),
            textStyle = TextStyle(color = LockInk.paper, fontSize = 16.sp),
            cursorBrush = SolidColor(LockInk.paper),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = imeAction),
            keyboardActions = KeyboardActions(
                onSearch = { onIme() },
                onDone = { onIme() }
            ),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(placeholder, color = LockInk.mist, fontSize = 16.sp)
                    }
                    inner()
                }
            }
        )
    }
}

internal fun formatGateRecentWhen(lastUsedAt: Long, nowMs: Long = System.currentTimeMillis()): String {
    if (lastUsedAt <= 0L) return ""
    val dayMs = 24L * 60 * 60 * 1000
    val cal = java.util.Calendar.getInstance().apply {
        timeInMillis = nowMs
        set(java.util.Calendar.HOUR_OF_DAY, 0)
        set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0)
        set(java.util.Calendar.MILLISECOND, 0)
    }
    val todayStart = cal.timeInMillis
    val yesterdayStart = todayStart - dayMs
    val clock = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        .format(java.util.Date(lastUsedAt))
    return when {
        lastUsedAt >= todayStart -> "今天 $clock"
        lastUsedAt >= yesterdayStart -> "昨天 $clock"
        else -> java.text.SimpleDateFormat("M月d日", java.util.Locale.CHINA)
            .format(java.util.Date(lastUsedAt))
    }
}
