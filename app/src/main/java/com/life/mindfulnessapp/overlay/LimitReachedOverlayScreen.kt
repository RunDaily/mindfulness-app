package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.usecase.GetAppHistoryUsageUseCase
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars

// ── 通用深色基底（兜底色，不依赖主题时使用） ──────────────────────────────────
private val LimitDarkBg = Color(0xFF111318)
private val LimitDarkSurface = Color(0xFF1E2130)
private val LimitDarkSurfaceVariant = Color(0xFF252840)
private val LimitTextPrimary = Color(0xFFF0F4F8)
private val LimitTextSecondary = Color(0xFF8E99B0)
private val LimitTextMuted = Color(0xFF4A5468)

/**
 * 时间用完后的全屏浮窗（主题色联动版）。
 *
 * 若今日还有剩余修改机会（[remainingModifyCount] > 0），则展示"重新设定目标"按钮，
 * 点击后弹出 [ResetLimitDialog] 让用户参考历史用量调整限制。
 *
 * @param todayUsedSeconds     今日已使用秒数
 * @param remainingModifyCount 今日剩余可修改次数（0 = 不再显示修改按钮）
 * @param currentDailyLimitMinutes 当前每日限制（分钟），用于校验新值
 * @param historyUsage         系统历史均值数据，为 null 时不显示参考信息
 * @param themeId              当前拦截主题 ID，用于主题化全页配色
 * @param onReset              用户确认新时限后的回调，参数为 (新每日分钟, 新每周分钟)
 * @param onDismiss            用户选择离开时的回调
 */
@Composable
fun LimitReachedOverlayScreen(
    todayUsedSeconds: Long,
    remainingModifyCount: Int = 0,
    currentDailyLimitMinutes: Int = 0,
    currentWeeklyLimitMinutes: Int = 0,
    historyUsage: GetAppHistoryUsageUseCase.HistoryUsageResult? = null,
    themeId: String = "default",
    onReset: ((newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    // ── 读取主题配置 ──────────────────────────────────────────────────────
    val themeConfig = remember(themeId) { getInterceptThemeConfig(themeId) }

    val todayUsedMinutes = todayUsedSeconds / 60
    var showResetDialog by remember { mutableStateOf(false) }

    // ── 分层入场动画状态 ────────────────────────────────────────────────────
    val bgEnterAlpha = remember { Animatable(0f) }
    val iconScale = remember { Animatable(0f) }
    var showTitle by remember { mutableStateOf(false) }
    var showCards by remember { mutableStateOf(false) }
    var showButtons by remember { mutableStateOf(false) }

    // ── dismiss 淡出动画（与 onDismiss 同步触发，整页 300ms 淡出）──────────────
    val dismissAlpha = remember { Animatable(1f) }
    var isDismissing by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        bgEnterAlpha.animateTo(1f, animationSpec = tween(350))
        delay(100)
        iconScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
        delay(120)
        showTitle = true
        delay(200)
        showCards = true
        delay(200)
        showButtons = true
    }

    // ── 主题化本地派生色 ──────────────────────────────────────────────────
    val bgColor        = themeConfig.bgColor
    val surfaceColor   = themeConfig.surfaceColor
    val accentColor    = themeConfig.accentColor
    val borderColor    = themeConfig.sectionBorderColor

    // 主题图标：兼顾超限场景下每个主题的语义
    val limitIcon = when (themeId) {
        "cyberpunk" -> "⛔"
        "lava"      -> "🌋"
        "sakura"    -> "🍂"
        "moon"      -> "🌑"
        "glitch"    -> "💀"
        "rpg"       -> "💀"
        "deep_sea"  -> "🚫"
        else        -> "⏰"
    }

    // 主标题文字（使用 themeConfig 的 limitTitleText）
    val titleText = themeConfig.limitTitleText

    // 副文案：主题化
    val subtitleText = when (themeId) {
        "cyberpunk" -> "ACCESS_DENIED. 今日配额耗尽 ⌨"
        "lava"      -> "火焰熄灭，今日能量归零 🔥"
        "sakura"    -> "花期已尽，今日时光用完 🌸"
        "moon"      -> "月圆则满，今日时间已到 🌙"
        "glitch"    -> "FATAL: 今日配额 overflow 🔻"
        "rpg"       -> "勇者的体力已耗尽，需要休整"
        "deep_sea"  -> "已浮出水面，今日探索结束 🌊"
        else        -> "今日的时间配额用完了 🌱"
    }

    // 鼓励文案
    val praiseText = when (themeId) {
        "cyberpunk" -> "> COMMIT: 你守住了今日配额 ✓"
        "glitch"    -> "// SUCCESS: 限制生效，任务完成"
        "rpg"       -> "⚔️ 英勇战斗！今日任务完成"
        "lava"      -> "🔥 烈焰淬炼，意志坚守！"
        "sakura"    -> "🌸 惜花如惜时，你做到了"
        "moon"      -> "🌙 星光见证，你信守承诺"
        "deep_sea"  -> "🌊 深潜归来，意志如磐石"
        else        -> "很棒！你守住了自己的承诺 ✨"
    }

    // 离开按钮文案（直接复用 themeConfig）
    val dismissButtonText = themeConfig.dismissButtonText

    MindfulnessAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // dismissAlpha：dismiss 时整页淡出（300ms），与 onDismiss 同步触发
                .alpha(bgEnterAlpha.value * dismissAlpha.value)
                .background(bgColor),
            contentAlignment = Alignment.Center
        ) {
            // 主题背景动效层（与拦截页一致）
            ThemeBackground(themeId = themeId, modifier = Modifier.fillMaxSize())

            // 状态栏遮罩：与主题背景色完全一致，让状态栏无缝融入
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(bgColor)
                    .align(Alignment.TopStart)
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── 1. 图标：spring 弹跳入场 ─────────────────────────────────
                Box(
                    modifier = Modifier
                        .scale(iconScale.value)
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(surfaceColor)
                        .then(
                            // 主题边框光晕
                            Modifier.then(Modifier)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(limitIcon, fontSize = 42.sp)
                }

                Spacer(modifier = Modifier.height(28.dp))

                // ── 2. 大标题：淡入上滑 ───────────────────────────────────────
                AnimatedVisibility(
                    visible = showTitle,
                    enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 2 }
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = titleText,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = LimitTextPrimary,
                            textAlign = TextAlign.Center,
                            letterSpacing = 1.sp
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = subtitleText,
                            fontSize = 15.sp,
                            color = LimitTextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                if (!showTitle) Spacer(modifier = Modifier.height(62.dp))

                Spacer(modifier = Modifier.height(24.dp))

                // ── 3. 信息卡片：从下滑入 ───────────────────────────────────
                AnimatedVisibility(
                    visible = showCards,
                    enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        // 使用时长信息卡片
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(themeConfig.sectionBgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                .padding(18.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Text(
                                text = "你今天已使用 ${formatMinutes(todayUsedMinutes.toInt())}",
                                fontSize = 15.sp,
                                color = LimitTextSecondary,
                                textAlign = TextAlign.Center
                            )
                            HorizontalDivider(
                                color = borderColor,
                                thickness = 1.dp
                            )
                            Text(
                                text = praiseText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = accentColor,
                                textAlign = TextAlign.Center
                            )
                        }

                        // 离开手机建议卡片
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(16.dp))
                                .background(themeConfig.sectionBgColor)
                                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = when (themeId) {
                                    "cyberpunk", "glitch" -> "// 离线后可执行的操作"
                                    "rpg"                 -> "勇者休整期间可以..."
                                    else                  -> "离开手机，你可以..."
                                },
                                fontSize = 13.sp,
                                color = LimitTextMuted,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = when (themeId) {
                                    "cyberpunk" -> "充电 · 整理代码 · 现实社交 · 运动"
                                    "glitch"    -> "重启大脑 · 运动 · 阅读 · 休眠充能"
                                    "rpg"       -> "补给 · 修炼 · 阅读秘籍 · 与盟友交流"
                                    "lava"      -> "冷静 · 户外散步 · 冥想 · 深呼吸"
                                    "sakura"    -> "赏花 · 写字 · 与人面对面 · 静心"
                                    "moon"      -> "夜观星空 · 阅读 · 冥想 · 与人倾谈"
                                    "deep_sea"  -> "浮出水面 · 深呼吸 · 散步 · 与人面谈"
                                    else        -> "散步 · 阅读 · 冥想 · 与人面对面交流"
                                },
                                fontSize = 15.sp,
                                color = LimitTextSecondary
                            )
                        }
                    }
                }
                if (!showCards) Spacer(modifier = Modifier.height(148.dp))

                Spacer(modifier = Modifier.height(32.dp))

                // ── 4. 按钮区：淡入上滑 ─────────────────────────────────────
                AnimatedVisibility(
                    visible = showButtons,
                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 }
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // 主按钮（主题强调色）
                        Button(
                            onClick = {
                                if (!isDismissing) {
                                    // 1. 立刻 pressHomeButton，App 开始退场
                                    // 2. 同时播放整页淡出（300ms）
                                    onDismiss()
                                    coroutineScope.launch {
                                        isDismissing = true
                                        dismissAlpha.animateTo(
                                            0f,
                                            animationSpec = tween(300, easing = FastOutSlowInEasing)
                                        )
                                    }
                                }
                            },
                            enabled = !isDismissing,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = accentColor
                            ),
                            shape = RoundedCornerShape(28.dp)
                        ) {
                            Text(
                                dismissButtonText,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.Black.copy(alpha = 0.85f)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // 次要按钮：重新设定目标（弱化样式）
                        AnimatedVisibility(
                            visible = remainingModifyCount > 0 && onReset != null,
                            enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
                            exit = fadeOut()
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showResetDialog = true },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = LimitTextSecondary
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        1.dp,
                                        borderColor
                                    ),
                                    shape = RoundedCornerShape(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Tune,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = LimitTextSecondary
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        "重新设定今日目标",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = LimitTextSecondary
                                    )
                                }
                                Text(
                                    text = "今日还有 $remainingModifyCount 次调整机会",
                                    fontSize = 12.sp,
                                    color = LimitTextMuted
                                )
                            }
                        }
                    }
                }
            }
        }

        // 重新设定弹窗
        if (showResetDialog && onReset != null) {
            ResetLimitDialog(
                todayUsedMinutes = todayUsedMinutes.toInt(),
                currentDailyLimitMinutes = currentDailyLimitMinutes,
                currentWeeklyLimitMinutes = currentWeeklyLimitMinutes,
                historyUsage = historyUsage,
                accentColor = accentColor,
                surfaceColor = surfaceColor,
                borderColor = borderColor,
                onConfirm = { newDaily, newWeekly ->
                    showResetDialog = false
                    onReset(newDaily, newWeekly)
                },
                onDismiss = { showResetDialog = false }
            )
        }
    }
}

/**
 * 重新设定时间限制的弹窗（主题色联动版）。
 * 展示系统历史均值参考 + 推荐区间 + 滑块，让用户做出有依据的决策。
 */
@Composable
internal fun ResetLimitDialog(
    todayUsedMinutes: Int,
    currentDailyLimitMinutes: Int,
    currentWeeklyLimitMinutes: Int,
    historyUsage: GetAppHistoryUsageUseCase.HistoryUsageResult?,
    accentColor: Color = Color(0xFF3DDC84),
    surfaceColor: Color = Color(0xFF1E2130),
    borderColor: Color = Color(0xFF252840),
    onConfirm: (newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit,
    onDismiss: () -> Unit
) {
    val minNewLimit = (todayUsedMinutes + 5).coerceAtLeast(10)
    val maxNewLimit = if (historyUsage != null && historyUsage.avgDailyMinutes > 0) {
        (historyUsage.avgDailyMinutes * 1.2).toInt().coerceAtLeast(minNewLimit + 30)
    } else 120

    val initialValue = if (historyUsage != null && historyUsage.recommendedMinHigh > 0) {
        historyUsage.recommendedMinHigh.coerceAtLeast(minNewLimit).coerceAtMost(maxNewLimit)
    } else {
        (currentDailyLimitMinutes * 2).coerceAtLeast(minNewLimit).coerceAtMost(maxNewLimit)
    }

    var newDailyLimit by remember { mutableIntStateOf(initialValue) }

    // 每周上限状态（仅当当前有每周上限时展示）
    val weeklyInitial = if (currentWeeklyLimitMinutes > 0) {
        currentWeeklyLimitMinutes.coerceAtLeast(initialValue)
    } else {
        initialValue * 7
    }
    var newWeeklyLimit by remember { mutableIntStateOf(weeklyInitial) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "重新设定今日目标 🎯",
                fontWeight = FontWeight.Bold,
                color = LimitTextPrimary,
                fontSize = 18.sp
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

                // ── 历史参考卡片 ──────────────────────────────────────
                if (historyUsage != null && historyUsage.validDays > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(borderColor.copy(alpha = 0.4f))
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📊 你的历史使用习惯（过去7天）",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = accentColor
                        )
                        Text(
                            text = "日均使用：${formatMinutes(historyUsage.avgDailyMinutes)}（${historyUsage.validDays} 天有记录）",
                            fontSize = 13.sp,
                            color = LimitTextSecondary
                        )
                        Text(
                            text = "推荐目标区间：${formatMinutes(historyUsage.recommendedMinLow)} ~ ${formatMinutes(historyUsage.recommendedMinHigh)}",
                            fontSize = 13.sp,
                            color = accentColor,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "设定在推荐区间内，既有挑战感又容易坚持 ✨",
                            fontSize = 11.sp,
                            color = LimitTextMuted,
                            lineHeight = 16.sp
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(borderColor.copy(alpha = 0.3f))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = "📊 暂无足够的历史数据",
                            fontSize = 13.sp,
                            color = LimitTextSecondary
                        )
                        Text(
                            text = "根据感觉设定一个合理目标，坚持几天后会有更精准的参考",
                            fontSize = 12.sp,
                            color = LimitTextMuted,
                            lineHeight = 18.sp
                        )
                    }
                }

                // ── 当前已用时长提示 ──────────────────────────────────
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF252018))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("⚠️ ", fontSize = 14.sp)
                    Text(
                        text = "今日已使用 ${formatMinutes(todayUsedMinutes)}，新目标至少需比这多 5 分钟",
                        fontSize = 12.sp,
                        color = Color(0xFFD4A843),
                        lineHeight = 18.sp
                    )
                }

                // ── 每日时长滑块 ──────────────────────────────────────────
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "新的今日限制",
                            fontSize = 14.sp,
                            color = LimitTextSecondary
                        )
                        Text(
                            text = formatMinutes(newDailyLimit),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = accentColor
                        )
                    }

                    Slider(
                        value = newDailyLimit.toFloat(),
                        onValueChange = {
                            newDailyLimit = it.toInt()
                            // 每日调高时同步保持每周 >= 每日
                            if (newWeeklyLimit < newDailyLimit) newWeeklyLimit = newDailyLimit * 7
                        },
                        valueRange = minNewLimit.toFloat()..maxNewLimit.toFloat(),
                        colors = SliderDefaults.colors(
                            thumbColor = accentColor,
                            activeTrackColor = accentColor,
                            inactiveTrackColor = borderColor
                        )
                    )

                    if (historyUsage != null && historyUsage.validDays > 0) {
                        val isInRange = newDailyLimit in historyUsage.recommendedMinLow..historyUsage.recommendedMinHigh
                        Text(
                            text = if (isInRange) "✅ 目标在推荐区间内，很棒！"
                            else if (newDailyLimit < historyUsage.recommendedMinLow) "💪 目标偏严格，挑战一下！"
                            else "😌 目标偏宽松，试试往下调一点",
                            fontSize = 12.sp,
                            color = if (isInRange) accentColor else LimitTextMuted
                        )
                    }
                }

                // ── 每周时长滑块（仅当原来设置了每周限制时展示）─────────────
                if (currentWeeklyLimitMinutes > 0) {
                    HorizontalDivider(color = borderColor, thickness = 1.dp)
                    val weeklyMin = newDailyLimit
                    val weeklyMax = (newDailyLimit * 7).coerceAtLeast(weeklyMin + 1)
                    val clampedWeekly = newWeeklyLimit.coerceIn(weeklyMin, weeklyMax)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "本周总限制",
                                fontSize = 14.sp,
                                color = LimitTextSecondary
                            )
                            Text(
                                text = formatMinutes(clampedWeekly),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = accentColor.copy(alpha = 0.8f)
                            )
                        }
                        Slider(
                            value = clampedWeekly.toFloat(),
                            onValueChange = { newWeeklyLimit = it.toInt() },
                            valueRange = weeklyMin.toFloat()..weeklyMax.toFloat(),
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor.copy(alpha = 0.8f),
                                activeTrackColor = accentColor.copy(alpha = 0.6f),
                                inactiveTrackColor = borderColor
                            )
                        )
                        Text(
                            text = "约等于每日上限 × ${ "%.1f".format(clampedWeekly.toFloat() / newDailyLimit) } 天",
                            fontSize = 11.sp,
                            color = LimitTextMuted
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        newDailyLimit,
                        if (currentWeeklyLimitMinutes > 0) {
                            val weeklyMax = (newDailyLimit * 7).coerceAtLeast(newDailyLimit + 1)
                            newWeeklyLimit.coerceIn(newDailyLimit, weeklyMax)
                        } else {
                            currentWeeklyLimitMinutes
                        }
                    )
                }
            ) {
                Text(
                    "确定，用新目标继续",
                    color = accentColor,
                    fontWeight = FontWeight.SemiBold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("算了，不改了", color = LimitTextMuted)
            }
        },
        containerColor = surfaceColor,
        titleContentColor = LimitTextPrimary,
        textContentColor = LimitTextSecondary,
        shape = RoundedCornerShape(20.dp)
    )
}

/** 将分钟数格式化为“X小时Y分”或“Y分钟”的可读字符串 */
internal fun formatMinutes(minutes: Int): String {
    return if (minutes >= 60) {
        val h = minutes / 60
        val m = minutes % 60
        if (m > 0) "${h}小时${m}分" else "${h}小时"
    } else {
        "${minutes}分钟"
    }
}
