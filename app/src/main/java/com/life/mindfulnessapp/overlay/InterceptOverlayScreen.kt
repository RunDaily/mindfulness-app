package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Tune
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import com.life.mindfulnessapp.domain.usecase.GetAppHistoryUsageUseCase
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.statusBars

/** 胶囊目标位置（屏幕坐标系，用于退场动画定位） */
data class CapsuleTargetPosition(
    val x: Float,
    val y: Float
)

/** 按钮冷静期倒计时秒数（未填目的时的默认冷静期） */
private const val COOLDOWN_SECONDS = 3

/** 已填写目的后的缩短冷静期秒数（鼓励有意识使用） */
private const val COOLDOWN_WITH_PURPOSE = 1

// ── 深色基础背景（深蓝灰系，无暗绿压抑感）────────────────────────────────────
private val DarkBg = Color(0xFF111318)
private val DarkSurface = Color(0xFF1E2130)
private val DarkSurfaceVariant = Color(0xFF252840)
private val TextPrimary = Color(0xFFF0F4F8)
private val TextSecondary = Color(0xFF8E99B0)
private val TextMuted = Color(0xFF4A5468)

// ── 时长锁分区配色（橙红·警示） ───────────────────────────────────────────────
private val LockSectionBg = Color(0xFF1C1820)
private val LockSectionBorder = Color(0xFF3D2818)
private val LockLabelBg = Color(0xFF251C1A)
private val LockLabelBorder = Color(0xFF5C3018)
private val LockLabelText = Color(0xFFFF9A78)
private val LockAccent = Color(0xFFFF6B4A)
private val LockTextSecondary = Color(0xFFB87A60)
private val LockTextMuted = Color(0xFF6B4030)
private val LockDismissBtn = Color(0xFFFF6B4A)

// ── 绿色主色调 ─────────────────────────────────────────────────────────────
private val MindfulAccent = Color(0xFF3DDC84)
private val MindfulAccentDim = Color(0xFF2ECC84)
private val MindfulSectionBg = Color(0xFF181E2E)
private val MindfulSectionBorder = Color(0xFF2A3550)
private val MindfulLabelBg = Color(0xFF1A2040)
private val MindfulLabelBorder = Color(0xFF3A5080)
private val MindfulLabelText = Color(0xFF4AE890)
private val MindfulTextSecondary = Color(0xFF8E99B0)
private val MindfulTextMuted = Color(0xFF4A5468)

// ── 拦截主题配置 ──────────────────────────────────────────────────────────────

data class InterceptThemeConfig(
    val bgColor: Color,
    val surfaceColor: Color,
    val accentColor: Color,
    val accentDimColor: Color,
    val sectionBgColor: Color,
    val sectionBorderColor: Color,
    val labelBgColor: Color,
    val labelBorderColor: Color,
    val labelTextColor: Color,
    val titleText: String,        // 非超限时的标题文字
    val limitTitleText: String,   // 超限时的标题文字
    val continueButtonText: String,
    val dismissButtonText: String,

    // ── 悬浮胶囊专属配色 ──────────────────────────────────────────────────
    /** 胶囊背景色（比全屏背景略亮，但保持主题基调） */
    val capsuleBgColor: Color,
    /** 胶囊主强调色：光盘轨道、停止按钮、时间数字高亮 */
    val capsuleAccentColor: Color,
    /** 胶囊停止按钮背景色（通常与 capsuleAccentColor 相近，但可单独区分） */
    val capsuleStopButtonColor: Color,
    /** 是否在胶囊中使用等宽字体（cyberpunk 终端风格需要） */
    val capsuleUseMonoFont: Boolean = false,

    // ── 仪式感浮窗专属配色 ────────────────────────────────────────────────
    /** 仪式感胶囊背景色 */
    val ceremonyBgColor: Color,
    /** 仪式感主文字色 */
    val ceremonyTextColor: Color,
    /** 仪式感次要标签色（"此刻的意图"小标签） */
    val ceremonySubLabelColor: Color
)

fun getInterceptThemeConfig(themeId: String): InterceptThemeConfig = when (themeId) {

    "deep_sea" -> InterceptThemeConfig(
        bgColor = Color(0xFF050F1E),
        surfaceColor = Color(0xFF0A1E36),
        accentColor = Color(0xFF00B4D8),
        accentDimColor = Color(0xFF0096C7),
        sectionBgColor = Color(0xFF071428),
        sectionBorderColor = Color(0xFF0A3050),
        labelBgColor = Color(0xFF081832),
        labelBorderColor = Color(0xFF1A4068),
        labelTextColor = Color(0xFF48CAE4),
        titleText = "🌊 暂停一下",
        limitTitleText = "🌊 停一停",
        continueButtonText = "潜入深海，带着目的",
        dismissButtonText = "浮出水面，离开这里 🌊",
        capsuleBgColor = Color(0xF0071428),
        capsuleAccentColor = Color(0xFF00B4D8),
        capsuleStopButtonColor = Color(0xFF0077A8),
        ceremonyBgColor = Color(0xFF050F1E),
        ceremonyTextColor = Color(0xFF48CAE4),
        ceremonySubLabelColor = Color(0xFF00B4D8).copy(alpha = 0.55f)
    )

    "cyberpunk" -> InterceptThemeConfig(
        bgColor = Color(0xFF001100),
        surfaceColor = Color(0xFF002200),
        accentColor = Color(0xFF00FF41),
        accentDimColor = Color(0xFF00CC33),
        sectionBgColor = Color(0xFF001A00),
        sectionBorderColor = Color(0xFF004400),
        labelBgColor = Color(0xFF001500),
        labelBorderColor = Color(0xFF006600),
        labelTextColor = Color(0xFF39FF14),
        titleText = "> INTERRUPT_",
        limitTitleText = "> ACCESS_DENIED_",
        continueButtonText = "> PROCEED WITH PURPOSE",
        dismissButtonText = "EXIT_PROCESS() ⌨",
        capsuleBgColor = Color(0xF0001500),
        capsuleAccentColor = Color(0xFF00FF41),
        capsuleStopButtonColor = Color(0xFF008820),
        capsuleUseMonoFont = true,
        ceremonyBgColor = Color(0xFF001100),
        ceremonyTextColor = Color(0xFF39FF14),
        ceremonySubLabelColor = Color(0xFF00FF41).copy(alpha = 0.50f)
    )

    "lava" -> InterceptThemeConfig(
        bgColor = Color(0xFF1A0000),
        surfaceColor = Color(0xFF2D0000),
        accentColor = Color(0xFFFF4500),
        accentDimColor = Color(0xFFCC3700),
        sectionBgColor = Color(0xFF200000),
        sectionBorderColor = Color(0xFF4A0A00),
        labelBgColor = Color(0xFF1A0000),
        labelBorderColor = Color(0xFF6B1500),
        labelTextColor = Color(0xFFFF6B35),
        titleText = "🔥 冷却一下",
        limitTitleText = "🔥 能量耗尽",
        continueButtonText = "穿越熔岩，目标明确",
        dismissButtonText = "撤退降温，远离诱惑 🔥",
        capsuleBgColor = Color(0xF0200000),
        capsuleAccentColor = Color(0xFFFF4500),
        capsuleStopButtonColor = Color(0xFFAA2200),
        ceremonyBgColor = Color(0xFF1A0000),
        ceremonyTextColor = Color(0xFFFF6B35),
        ceremonySubLabelColor = Color(0xFFFF4500).copy(alpha = 0.55f)
    )

    "sakura" -> InterceptThemeConfig(
        bgColor = Color(0xFF1E0011),
        surfaceColor = Color(0xFF2D001A),
        accentColor = Color(0xFFFF85A1),
        accentDimColor = Color(0xFFCC6680),
        sectionBgColor = Color(0xFF1A000E),
        sectionBorderColor = Color(0xFF4A0022),
        labelBgColor = Color(0xFF150008),
        labelBorderColor = Color(0xFF6B0033),
        labelTextColor = Color(0xFFFFB3C6),
        titleText = "🌸 片刻宁静",
        limitTitleText = "🌸 花期已尽",
        continueButtonText = "带着心意，继续前行",
        dismissButtonText = "随花瓣飘落，离开吧 🌸",
        capsuleBgColor = Color(0xF01A000E),
        capsuleAccentColor = Color(0xFFFF85A1),
        capsuleStopButtonColor = Color(0xFFAA3355),
        ceremonyBgColor = Color(0xFF1E0011),
        ceremonyTextColor = Color(0xFFFFB3C6),
        ceremonySubLabelColor = Color(0xFFFF85A1).copy(alpha = 0.55f)
    )

    "moon" -> InterceptThemeConfig(
        bgColor = Color(0xFF080316),
        surfaceColor = Color(0xFF110528),
        accentColor = Color(0xFFB39DDB),
        accentDimColor = Color(0xFF9575CD),
        sectionBgColor = Color(0xFF0D041E),
        sectionBorderColor = Color(0xFF2A1050),
        labelBgColor = Color(0xFF080212),
        labelBorderColor = Color(0xFF40186A),
        labelTextColor = Color(0xFFCE93D8),
        titleText = "🌙 星空问你",
        limitTitleText = "🌙 月相已满",
        continueButtonText = "带着星光，明确前行",
        dismissButtonText = "回归寂静，放下手机 🌙",
        capsuleBgColor = Color(0xF00D041E),
        capsuleAccentColor = Color(0xFFB39DDB),
        capsuleStopButtonColor = Color(0xFF7B55A8),
        ceremonyBgColor = Color(0xFF080316),
        ceremonyTextColor = Color(0xFFCE93D8),
        ceremonySubLabelColor = Color(0xFFB39DDB).copy(alpha = 0.55f)
    )

    "glitch" -> InterceptThemeConfig(
        bgColor = Color(0xFF0A0011),
        surfaceColor = Color(0xFF130022),
        accentColor = Color(0xFFFF0080),
        accentDimColor = Color(0xFFCC0066),
        sectionBgColor = Color(0xFF0D0018),
        sectionBorderColor = Color(0xFF3D0044),
        labelBgColor = Color(0xFF0A0010),
        labelBorderColor = Color(0xFF5A0066),
        labelTextColor = Color(0xFF00FFFF),
        titleText = "💥 ERR_0x404",
        limitTitleText = "💥 FATAL_ERROR",
        continueButtonText = "忽略错误，强行闯入",
        dismissButtonText = "CTRL+Z 撤销打开 😤",
        capsuleBgColor = Color(0xF00D0018),
        capsuleAccentColor = Color(0xFF00FFFF),
        capsuleStopButtonColor = Color(0xFFCC0066),
        capsuleUseMonoFont = true,
        ceremonyBgColor = Color(0xFF0A0011),
        ceremonyTextColor = Color(0xFF00FFFF),
        ceremonySubLabelColor = Color(0xFFFF0080).copy(alpha = 0.60f)
    )

    "rpg" -> InterceptThemeConfig(
        bgColor = Color(0xFF05060A),
        surfaceColor = Color(0xFF0D1018),
        accentColor = Color(0xFFFFD700),
        accentDimColor = Color(0xFFCC9900),
        sectionBgColor = Color(0xFF080A12),
        sectionBorderColor = Color(0xFF2A2208),
        labelBgColor = Color(0xFF060810),
        labelBorderColor = Color(0xFF4A3A00),
        labelTextColor = Color(0xFFFF8C00),
        titleText = "⚔️ 精力不足",
        limitTitleText = "💀 勇者已倒下",
        continueButtonText = "消耗精力，继续冒险",
        dismissButtonText = "回村补给，养精蓄锐 ⚔️",
        capsuleBgColor = Color(0xF0080A12),
        capsuleAccentColor = Color(0xFFFFD700),
        capsuleStopButtonColor = Color(0xFF996600),
        ceremonyBgColor = Color(0xFF05060A),
        ceremonyTextColor = Color(0xFFFFD700),
        ceremonySubLabelColor = Color(0xFFFF8C00).copy(alpha = 0.60f)
    )

    // "default" 以及任何未知 themeId
    else -> InterceptThemeConfig(
        bgColor = DarkBg,
        surfaceColor = DarkSurface,
        accentColor = MindfulAccent,
        accentDimColor = MindfulAccentDim,
        sectionBgColor = MindfulSectionBg,
        sectionBorderColor = MindfulSectionBorder,
        labelBgColor = MindfulLabelBg,
        labelBorderColor = MindfulLabelBorder,
        labelTextColor = MindfulLabelText,
        titleText = "🌿 停一下",
        limitTitleText = "🌿 时间到了",
        continueButtonText = "写下目的，有意识地进入",
        dismissButtonText = "好的，我去做别的事 🌿",
        capsuleBgColor = Color(0xF01A1E2A),
        capsuleAccentColor = MindfulAccent,
        capsuleStopButtonColor = Color(0xFF27AE60),
        ceremonyBgColor = DarkBg,
        ceremonyTextColor = MindfulLabelText,
        ceremonySubLabelColor = MindfulAccent.copy(alpha = 0.55f)
    )
}

@Composable
fun InterceptOverlayScreen(
    appName: String,
    packageName: String = "",
    dailyLimitMinutes: Int,
    weeklyLimitMinutes: Int,
    todayUsedSeconds: Long,
    weekUsedSeconds: Long,
    todayRecords: List<UsageRecordEntity> = emptyList(),
    capsuleTargetPosition: CapsuleTargetPosition? = null,
    remainingModifyCount: Int = 0,
    currentWeeklyLimitMinutes: Int = 0,
    historyUsage: GetAppHistoryUsageUseCase.HistoryUsageResult? = null,
    themeId: String = "default",
    onReset: ((newDailyMinutes: Int, newWeeklyMinutes: Int) -> Unit)? = null,
    onContinue: (purpose: String?) -> Unit,
    onDismiss: () -> Unit
) {
    val themeConfig = remember(themeId) { getInterceptThemeConfig(themeId) }
    val todayUsedMinutes = todayUsedSeconds / 60
    val weekUsedMinutes = weekUsedSeconds / 60
    val dailyProgress = if (dailyLimitMinutes > 0)
        (todayUsedMinutes.toFloat() / dailyLimitMinutes).coerceAtMost(1f) else 0f

    // 超限判断用「秒」直接与「限制秒数」比较，避免整数除法 /60 截断导致
    // 例如已用 3599 秒（59分59秒）、限制 60 分钟时：3599/60=59 < 60 误判为未超限，
    // 而秒级比较 3599 < 3600 同样未超，但 3600 >= 3600 才准确触发。
    // 更关键的场景：已用 3660 秒（61分）、限制 60 分钟，秒比较才能正确超限。
    val dailyLimitSeconds = dailyLimitMinutes * 60L
    val weeklyLimitSeconds = weeklyLimitMinutes * 60L
    val isOverDailyLimit = dailyLimitSeconds > 0 && todayUsedSeconds >= dailyLimitSeconds
    val isOverWeeklyLimit = weeklyLimitSeconds > 0 && weekUsedSeconds >= weeklyLimitSeconds
    val isOverLimit = isOverDailyLimit || isOverWeeklyLimit

    val animDailyProgress by animateFloatAsState(
        targetValue = dailyProgress,
        animationSpec = tween(1000),
        label = "dailyProgress"
    )

    var showResetDialog by remember { mutableStateOf(false) }

    // 目的输入（始终显示，超限时不显示）
    var intentText by remember { mutableStateOf("") }
    val confirmedPurpose: String? = if (intentText.isNotBlank()) intentText.trim() else null

    // 超限时不要求目的；非超限时必须输入目的
    val canContinue = isOverLimit || confirmedPurpose != null

    // ── 退场动画 ─────────────────────────────────────────────────────────────
    var isExiting by remember { mutableStateOf(false) }
    val exitProgress = remember { Animatable(0f) }
    val coroutineScope = rememberCoroutineScope()

    // dismiss 专属淡出：整页 alpha 1 → 0，300ms
    // 与 onDismiss（pressHomeButton）同时触发，确保 App 退完之前拦截页仍可见
    val dismissAlpha = remember { Animatable(1f) }
    var isDismissing by remember { mutableStateOf(false) }

    suspend fun playExitAnimation() {
        isExiting = true
        exitProgress.animateTo(1f, animationSpec = tween(520, easing = FastOutSlowInEasing))
    }

    /** dismiss 路径：整页淡出，300ms */
    suspend fun playDismissFadeOut() {
        isDismissing = true
        dismissAlpha.animateTo(0f, animationSpec = tween(300, easing = FastOutSlowInEasing))
    }

    // 使用 WindowManager 显示区域来获取屏幕中心（避免 BoxWithConstraints 在 Overlay 中崩溃）
    val context = LocalContext.current
    val screenCenterOffset = remember {
        try {
            val wm = context.getSystemService(android.content.Context.WINDOW_SERVICE)
                    as android.view.WindowManager
            val bounds = wm.currentWindowMetrics.bounds
            androidx.compose.ui.geometry.Offset(bounds.width() / 2f, bounds.height() / 2f)
        } catch (e: Exception) {
            androidx.compose.ui.geometry.Offset(540f, 1200f)
        }
    }
    val targetPos = capsuleTargetPosition ?: CapsuleTargetPosition(8f, 160f)

    val translateX = remember(screenCenterOffset, targetPos) {
        derivedStateOf {
            if (!isExiting) 0f else (targetPos.x - screenCenterOffset.x) * exitProgress.value
        }
    }
    val translateY = remember(screenCenterOffset, targetPos) {
        derivedStateOf {
            if (!isExiting) 0f else (targetPos.y - screenCenterOffset.y) * exitProgress.value
        }
    }
    val scaleValue = remember(isExiting) {
        derivedStateOf { if (!isExiting) 1f else 1f - exitProgress.value * 0.78f }
    }
    val cornerRadius = remember(isExiting) {
        derivedStateOf { if (!isExiting) 0f else exitProgress.value * 26f }
    }
    val bgAlpha = remember(isExiting) {
        derivedStateOf { if (!isExiting) 1f else 1f - exitProgress.value * 0.7f }
    }
    val contentAlpha = remember(isExiting) {
        derivedStateOf { if (!isExiting) 1f else 1f - exitProgress.value }
    }
    val buttonAlpha = remember(isExiting) {
        derivedStateOf { if (!isExiting) 1f else (1f - exitProgress.value * 2f).coerceAtLeast(0f) }
    }

    // ── 入场动画 ─────────────────────────────────────────────────────────────
    var showTop by remember { mutableStateOf(false) }
    var showMiddle by remember { mutableStateOf(false) }
    var showBottom by remember { mutableStateOf(false) }

    val iconScale = remember { Animatable(0f) }
    var cooldownRemaining by remember { mutableIntStateOf(COOLDOWN_SECONDS) }
    val buttonEnabled = canContinue && !isExiting && !isDismissing && cooldownRemaining == 0
    val bgEnterAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // 背景淡入 + 入场动画（并行启动倒计时，不阻塞动画）
        bgEnterAlpha.animateTo(1f, animationSpec = tween(350))
        delay(80)
        showTop = true
        iconScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium))
        delay(160)
        showMiddle = true
        delay(200)
        showBottom = true
    }

    // 冷静期倒计时：若用户填写了目的，则只需等待 COOLDOWN_WITH_PURPOSE 秒
    LaunchedEffect(Unit) {
        repeat(COOLDOWN_SECONDS) {
            delay(1000)
            cooldownRemaining--
        }
    }

    // 当用户填写目的时，若冷静期剩余 > COOLDOWN_WITH_PURPOSE，立即缩短
    LaunchedEffect(confirmedPurpose) {
        if (confirmedPurpose != null && cooldownRemaining > COOLDOWN_WITH_PURPOSE) {
            // 给 1s 的最小冷静期，让用户反应
            delay(300)
            cooldownRemaining = cooldownRemaining.coerceAtMost(COOLDOWN_WITH_PURPOSE)
        }
    }

    MindfulnessAppTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                // dismissAlpha：dismiss 时整页淡出（300ms），与 onDismiss 同步触发
                // bgAlpha：continue 退场动画中的背景透明度
                .alpha(bgAlpha.value * bgEnterAlpha.value * dismissAlpha.value)
                .background(themeConfig.bgColor)
        ) {
            // ── 主题背景动效层（在内容之下） ──────────────────────────────────
            ThemeBackground(themeId = themeId, modifier = Modifier.fillMaxSize())

            // ── 状态栏遮罩：与主题背景色完全一致，让状态栏无缝融入 ──────────────
            val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(statusBarHeight)
                    .background(themeConfig.bgColor)
                    .align(Alignment.TopStart)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .graphicsLayer {
                        scaleX = scaleValue.value
                        scaleY = scaleValue.value
                        translationX = translateX.value
                        translationY = translateY.value
                        shape = RoundedCornerShape(cornerRadius.value.dp)
                        clip = true
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                contentPadding = PaddingValues(
                    horizontal = 24.dp,
                    vertical = 0.dp
                )
            ) {
                // 顶部间距
                item { Spacer(Modifier.height(24.dp)) }

                // ── 第一层：App 图标 + 标题 ──────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = showTop,
                        enter = fadeIn(tween(300)) + slideInVertically(tween(300)) { it / 3 }
                    ) {
                        AppHeader(
                            appName = appName,
                            packageName = packageName,
                            iconScale = iconScale.value,
                            isOverLimit = isOverLimit,
                            contentAlpha = contentAlpha.value,
                            themeConfig = themeConfig
                        )
                    }
                    if (!showTop) Spacer(Modifier.height(180.dp))
                }

                item { Spacer(Modifier.height(32.dp)) }

                // ── 第二层：信息卡片区 ────────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = showMiddle && !isExiting,
                        enter = fadeIn(tween(320)) + slideInVertically(tween(320)) { it / 2 },
                        exit = fadeOut(tween(150))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(contentAlpha.value),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // 📊 今日使用时长（圆弧进度环 + 大字展示）
                            if (isOverLimit) {
                                LockSection(
                                    todayUsedMinutes = todayUsedMinutes,
                                    dailyLimitMinutes = dailyLimitMinutes,
                                    dailyProgress = animDailyProgress,
                                    isOverDailyLimit = isOverDailyLimit
                                )
                            } else {
                                UsageSection(
                                    todayUsedMinutes = todayUsedMinutes,
                                    dailyLimitMinutes = dailyLimitMinutes,
                                    dailyProgress = animDailyProgress,
                                    themeConfig = themeConfig
                                )
                            }

                            // 📝 目的输入卡片（超限时不显示）
                            if (!isOverLimit) {
                                PurposeInputSection(
                                    intentText = intentText,
                                    onIntentChange = { intentText = it },
                                    buttonEnabled = buttonEnabled,
                                    cooldownRemaining = cooldownRemaining,
                                    confirmedPurpose = confirmedPurpose,
                                    isExiting = isExiting,
                                    themeConfig = themeConfig,
                                    onEnter = {
                                        coroutineScope.launch {
                                            playExitAnimation()
                                            onContinue(confirmedPurpose)
                                        }
                                    }
                                )
                            }

                            // 📋 今日使用记录（有记录时显示）
                            if (todayRecords.isNotEmpty()) {
                                TodayRecordsSection(records = todayRecords)
                            }
                        }
                    }
                    if (!showMiddle) Spacer(Modifier.height(80.dp))
                }

                item { Spacer(Modifier.height(32.dp)) }

                // ── 第三层：按钮区 ───────────────────────────────────────────
                item {
                    AnimatedVisibility(
                        visible = showBottom && !isExiting,
                        enter = fadeIn(tween(350)) + slideInVertically(tween(350)) { it / 2 },
                        exit = fadeOut(tween(150))
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .alpha(buttonAlpha.value)
                        ) {
                            if (isOverLimit) {
                                // 超限：只有离开按钮 + 可选的重设目标
                                Button(
                                    onClick = {
                                        if (!isExiting && !isDismissing) {
                                            // 1. 立刻 pressHomeButton，App 开始退场
                                            // 2. 同时播放整页淡出（300ms）
                                            // 视觉效果：拦截页柔和消失，用户绝不会看到 App 界面
                                            onDismiss()
                                            coroutineScope.launch { playDismissFadeOut() }
                                        }
                                    },
                                    enabled = !isExiting && !isDismissing,
                                    modifier = Modifier.fillMaxWidth().height(54.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = themeConfig.accentColor),
                                    shape = RoundedCornerShape(27.dp)
                                ) {
                                    Text(
                                        themeConfig.dismissButtonText,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.Black.copy(alpha = 0.85f)
                                    )
                                }

                                AnimatedVisibility(
                                    visible = remainingModifyCount > 0 && onReset != null,
                                    enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 2 },
                                    exit = fadeOut()
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.padding(top = 14.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = { showResetDialog = true },
                                            modifier = Modifier.fillMaxWidth().height(48.dp),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = LockTextSecondary
                                            ),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp, LockSectionBorder
                                            ),
                                            shape = RoundedCornerShape(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.Tune,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = LockTextSecondary
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                "重新设定今日目标",
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = LockTextSecondary
                                            )
                                        }
                                        Text(
                                            text = "今日还有 $remainingModifyCount 次调整机会",
                                            fontSize = 12.sp,
                                            color = LockTextMuted
                                        )
                                    }
                                }
                            } else {
                                // 非超限：主按钮已内嵌在 PurposeInputSection 里，这里只显示"还是算了"
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isExiting && !isDismissing) {
                                            // 1. 立刻 pressHomeButton，App 开始退场
                                            // 2. 同时播放整页淡出（300ms）
                                            onDismiss()
                                            coroutineScope.launch { playDismissFadeOut() }
                                        }
                                        .padding(vertical = 10.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "还是算了",
                                        fontSize = 15.sp,
                                        color = TextMuted
                                    )
                                }
                            }
                        }
                    }
                    if (!showBottom) Spacer(Modifier.height(88.dp))
                }

                // 底部间距
                item { Spacer(Modifier.height(40.dp)) }
            }
        }

        if (showResetDialog && onReset != null) {
            ResetLimitDialog(
                todayUsedMinutes = todayUsedMinutes.toInt(),
                currentDailyLimitMinutes = dailyLimitMinutes,
                currentWeeklyLimitMinutes = currentWeeklyLimitMinutes,
                historyUsage = historyUsage,
                onConfirm = { newDaily, newWeekly ->
                    showResetDialog = false
                    onReset(newDaily, newWeekly)
                },
                onDismiss = { showResetDialog = false }
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  顶部：App 图标 + 标题（全新设计：大图标 + 光晕 + 更具气场的排版）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun AppHeader(
    appName: String,
    packageName: String,
    iconScale: Float,
    isOverLimit: Boolean,
    contentAlpha: Float,
    themeConfig: InterceptThemeConfig = getInterceptThemeConfig("default")
) {
    val context = LocalContext.current
    val appIcon = remember(packageName) {
        if (packageName.isNotEmpty()) {
            try { context.packageManager.getApplicationIcon(packageName) }
            catch (e: Exception) { null }
        } else null
    }

    // 呼吸动画：尺寸 140→185dp + alpha 0.12→0.35，周期 2.8s
    val infiniteTransition = rememberInfiniteTransition(label = "glowBreath")
    val breathSize by infiniteTransition.animateFloat(
        initialValue = 140f,
        targetValue = 185f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathSize"
    )
    val breathAlpha by infiniteTransition.animateFloat(
        initialValue = 0.12f,
        targetValue = 0.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathAlpha"
    )

    val glowBaseColor = if (isOverLimit) LockAccent else themeConfig.accentColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.alpha(contentAlpha)
    ) {
        // 图标 + 光晕层（光晕独立在外层，不受 iconScale 裁剪）
        Box(
            modifier = Modifier.size(185.dp),
            contentAlignment = Alignment.Center
        ) {
            // 外层呼吸光晕（尺寸 + alpha 双重脉动，视觉明显）
            Box(
                modifier = Modifier
                    .size(breathSize.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                glowBaseColor.copy(alpha = breathAlpha),
                                glowBaseColor.copy(alpha = breathAlpha * 0.4f),
                                Color.Transparent
                            ),
                            radius = breathSize / 2f * 3.5f
                        ),
                        shape = CircleShape
                    )
            )
            // 图标内容区（弹簧入场缩放）
            Box(
                modifier = Modifier
                    .scale(iconScale)
                    .size(140.dp),
                contentAlignment = Alignment.Center
            ) {
            // 中层背景圆
            Box(
                modifier = Modifier
                    .size(108.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                DarkSurface,
                                DarkBg
                            )
                        )
                    )
                    .border(
                        width = 1.dp,
                        brush = Brush.linearGradient(
                            colors = if (isOverLimit)
                                listOf(LockAccent.copy(alpha = 0.4f), LockSectionBorder)
                            else
                                listOf(MindfulAccent.copy(alpha = 0.35f), DarkSurfaceVariant)
                        ),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (appIcon != null) {
                    val bitmap = remember(appIcon) { appIcon.toBitmap(216, 216).asImageBitmap() }
                    androidx.compose.foundation.Image(
                        bitmap = bitmap,
                        contentDescription = appName,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(RoundedCornerShape(18.dp))
                    )
                } else {
                    Text(
                        text = appName.take(1),
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isOverLimit) LockAccent else themeConfig.accentColor
                    )
                }
            }
            // 超限时右下角锁标记
            if (isOverLimit) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .align(Alignment.BottomEnd)
                        .offset(x = (-4).dp, y = (-4).dp)
                        .clip(CircleShape)
                        .background(LockAccent),
                    contentAlignment = Alignment.Center
                ) {
                    Text("🔒", fontSize = 13.sp)
                }
            }
            } // end Box(iconScale)
        } // end Box(185.dp)

        Spacer(Modifier.height(24.dp))

        // App 名称（细小置顶，低调呈现）
        Text(
            text = appName,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = if (isOverLimit) LockTextSecondary else TextMuted,
            letterSpacing = 2.sp,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(10.dp))

        // 大标题
        Text(
            text = if (isOverLimit) themeConfig.limitTitleText else themeConfig.titleText,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            color = TextPrimary,
            textAlign = TextAlign.Center,
            letterSpacing = 1.sp
        )

        Spacer(Modifier.height(8.dp))

        // 副标题
        Text(
            text = if (isOverLimit)
                "今天的配额已用完，好好休息吧"
            else
                "打开之前，先想想你真正想做什么",
            fontSize = 14.sp,
            color = if (isOverLimit) LockTextSecondary else TextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 21.sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  分区小标签
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(emoji: String, label: String, bgColor: Color, borderColor: Color, textColor: Color) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(emoji, fontSize = 11.sp)
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            letterSpacing = 0.5.sp
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  🔒 时长锁 分区卡片（超限场景，去掉本周，突出今日超限）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun LockSection(
    todayUsedMinutes: Long,
    dailyLimitMinutes: Int,
    dailyProgress: Float,
    isOverDailyLimit: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(LockSectionBg)
            .border(1.dp, LockSectionBorder, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionLabel(
            emoji = "🔒",
            label = "时长锁",
            bgColor = LockLabelBg,
            borderColor = LockLabelBorder,
            textColor = LockLabelText
        )

        // 圆弧进度环 + 中心大字时长
        UsageArcDisplay(
            usedMinutes = todayUsedMinutes,
            limitMinutes = dailyLimitMinutes,
            progress = dailyProgress,
            isOver = isOverDailyLimit,
            arcColor = LockAccent,
            arcTrackColor = LockSectionBorder,
            centerLabelColor = LockAccent
        )

        HorizontalDivider(color = LockSectionBorder, thickness = 1.dp)

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("🌿", fontSize = 14.sp)
            Text(
                text = "今天已经很不错了，让眼睛和大脑休息一下",
                fontSize = 13.sp,
                color = LockTextSecondary,
                lineHeight = 19.sp
            )
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  📊 使用进度 分区卡片（普通拦截场景，仅展示今日）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun UsageSection(
    todayUsedMinutes: Long,
    dailyLimitMinutes: Int,
    dailyProgress: Float,
    themeConfig: InterceptThemeConfig = getInterceptThemeConfig("default")
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(themeConfig.sectionBgColor)
            .border(1.dp, themeConfig.sectionBorderColor, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SectionLabel(
            emoji = "⏱",
            label = "今日使用",
            bgColor = themeConfig.labelBgColor,
            borderColor = themeConfig.labelBorderColor,
            textColor = themeConfig.labelTextColor
        )

        // 圆弧进度环 + 中心大字时长
        val isNearLimit = dailyLimitMinutes > 0 && dailyProgress > 0.8f
        val arcColor = when {
            isNearLimit -> Color(0xFFFFB74D)
            else -> themeConfig.accentColor
        }
        UsageArcDisplay(
            usedMinutes = todayUsedMinutes,
            limitMinutes = dailyLimitMinutes,
            progress = dailyProgress,
            isOver = false,
            arcColor = arcColor,
            arcTrackColor = themeConfig.surfaceColor,
            centerLabelColor = arcColor
        )
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  圆弧进度环 + 中心大字时长（可复用）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun UsageArcDisplay(
    usedMinutes: Long,
    limitMinutes: Int,
    progress: Float,
    isOver: Boolean,
    arcColor: Color,
    arcTrackColor: Color,
    centerLabelColor: Color
) {
    Box(
        modifier = Modifier
            .size(160.dp),
        contentAlignment = Alignment.Center
    ) {
        // Canvas 绘制圆弧
        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 10.dp.toPx()
            val inset = strokeWidth / 2f
            val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
            val topLeft = Offset(inset, inset)
            val startAngle = 135f
            val sweepTotal = 270f

            // 轨道（底层）
            drawArc(
                color = arcTrackColor,
                startAngle = startAngle,
                sweepAngle = sweepTotal,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // 进度（前层）
            if (progress > 0f) {
                drawArc(
                    color = arcColor,
                    startAngle = startAngle,
                    sweepAngle = sweepTotal * progress.coerceAtMost(1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }
        }

        // 中心文字内容
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // 主时长大字
            val hours = usedMinutes / 60
            val minutes = usedMinutes % 60
            if (hours > 0) {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = "$hours",
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold,
                        color = centerLabelColor,
                        lineHeight = 40.sp
                    )
                    Text(
                        text = "小时",
                        fontSize = 13.sp,
                        color = centerLabelColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    if (minutes > 0) {
                        Text(
                            text = "$minutes",
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = centerLabelColor,
                            lineHeight = 28.sp
                        )
                        Text(
                            text = "分",
                            fontSize = 13.sp,
                            color = centerLabelColor.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            } else {
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (usedMinutes > 0) "$minutes" else "0",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                        color = centerLabelColor,
                        lineHeight = 44.sp
                    )
                    Text(
                        text = "分钟",
                        fontSize = 13.sp,
                        color = centerLabelColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(bottom = 7.dp)
                    )
                }
            }

            Spacer(Modifier.height(4.dp))

            // 副标签：已使用 / 限制
            if (limitMinutes > 0) {
                Text(
                    text = "已用 / 限 ${formatMinutes(limitMinutes)}",
                    fontSize = 11.sp,
                    color = centerLabelColor.copy(alpha = 0.5f),
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "今日已使用",
                    fontSize = 11.sp,
                    color = centerLabelColor.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  📝 目的输入 分区卡片（始终显示，作为进入的必要条件）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun PurposeInputSection(
    intentText: String,
    onIntentChange: (String) -> Unit,
    buttonEnabled: Boolean,
    cooldownRemaining: Int,
    confirmedPurpose: String?,
    isExiting: Boolean,
    themeConfig: InterceptThemeConfig = getInterceptThemeConfig("default"),
    onEnter: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val buttonCoroutineScope = rememberCoroutineScope()

    // ── 呼吸光晕动画（联动按钮状态） ────────────────────────────────────────
    val infiniteTransition = rememberInfiniteTransition(label = "btn_breath")
    val breathProgress by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (buttonEnabled) 1400 else 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_prog"
    )

    // ── 按钮可用时的光晕扩散半径 & 透明度（随呼吸脉动） ──────────────────────
    val glowAlpha = if (buttonEnabled) 0.12f + breathProgress * 0.22f else 0f
    val glowScale = if (buttonEnabled) 1f + breathProgress * 0.06f else 1f

    // ── 按下缩放动画 ─────────────────────────────────────────────────────────
    val buttonPressScale = remember { Animatable(1f) }

    // ── 涌现动画：从禁用 → 可用时的状态切换 ──────────────────────────────────
    val buttonAppearScale by animateFloatAsState(
        targetValue = if (buttonEnabled) 1f else 0.96f,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
        label = "btn_appear"
    )
    val buttonAppearAlpha by animateFloatAsState(
        targetValue = if (buttonEnabled) 1f else 0.55f,
        animationSpec = tween(300),
        label = "btn_alpha"
    )

    // ── 冷却期圆弧进度（0→1 倒计时弧度） ─────────────────────────────────────
    val cooldownArcProgress by animateFloatAsState(
        targetValue = if (cooldownRemaining > 0) cooldownRemaining.toFloat() / COOLDOWN_SECONDS else 0f,
        animationSpec = tween(900),
        label = "cooldown_arc"
    )

    // ── 卡片边框呼吸色（buttonEnabled 时边框随主题色脉冲） ────────────────────
    val cardBorderAlpha by animateFloatAsState(
        targetValue = if (buttonEnabled) 0.5f + breathProgress * 0.5f else 0.15f,
        animationSpec = tween(600),
        label = "card_border"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(themeConfig.sectionBgColor)
            .border(
                width = if (buttonEnabled) 1.5.dp else 1.dp,
                color = if (buttonEnabled)
                    themeConfig.accentColor.copy(alpha = cardBorderAlpha)
                else
                    themeConfig.sectionBorderColor,
                shape = RoundedCornerShape(20.dp)
            )
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        SectionLabel(
            emoji = "✏️",
            label = "使用目的",
            bgColor = themeConfig.labelBgColor,
            borderColor = themeConfig.labelBorderColor,
            textColor = themeConfig.labelTextColor
        )

        // 意图输入框
        OutlinedTextField(
            value = intentText,
            onValueChange = { newText ->
                if (newText.length != intentText.length) {
                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                onIntentChange(newText)
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("我想要…", fontSize = 13.sp, color = themeConfig.accentColor.copy(alpha = 0.45f)) },
            placeholder = { Text("写下此刻打开它的原因", fontSize = 14.sp, color = TextMuted) },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = themeConfig.accentColor,
                unfocusedBorderColor = themeConfig.sectionBorderColor,
                focusedLabelColor = themeConfig.accentColor,
                unfocusedLabelColor = MindfulTextMuted,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = themeConfig.accentColor,
                focusedContainerColor = themeConfig.surfaceColor,
                unfocusedContainerColor = themeConfig.surfaceColor
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, color = TextPrimary)
        )

        // ── 进入按钮（强化确认感版）──────────────────────────────────────────
        val enterLabel = when {
            cooldownRemaining > 0 && confirmedPurpose == null ->
                "先写下目的，${cooldownRemaining}s 后可继续"
            cooldownRemaining > 0 -> "冷静 ${cooldownRemaining}s …"
            confirmedPurpose == null -> "写下目的后继续"
            else -> themeConfig.continueButtonText
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = buttonAppearScale * buttonPressScale.value * glowScale
                    scaleY = buttonAppearScale * buttonPressScale.value * glowScale
                    alpha = buttonAppearAlpha
                },
            contentAlignment = Alignment.Center
        ) {
            // ── 外层扩散光晕（多层，呼吸感强烈） ────────────────────────────────
            if (buttonEnabled) {
                // 最外层大光晕
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(70.dp)
                        .clip(RoundedCornerShape(35.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    themeConfig.accentColor.copy(alpha = glowAlpha * 0.7f),
                                    themeConfig.accentColor.copy(alpha = glowAlpha * 0.15f),
                                    Color.Transparent
                                )
                            )
                        )
                )
                // 中层光晕（略小）
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.88f)
                        .height(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(themeConfig.accentColor.copy(alpha = glowAlpha * 0.25f))
                )
            }

            // ── 按钮主体 ──────────────────────────────────────────────────────
            Button(
                onClick = {
                    if (buttonEnabled) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        buttonCoroutineScope.launch {
                            // 按下：快速压缩
                            buttonPressScale.animateTo(0.93f, tween(70))
                            // 回弹：弹簧感
                            buttonPressScale.animateTo(1f, spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ))
                        }
                        onEnter()
                    }
                },
                enabled = buttonEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeConfig.accentColor,
                    disabledContainerColor = themeConfig.surfaceColor.copy(alpha = 0.5f)
                ),
                shape = RoundedCornerShape(26.dp),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (buttonEnabled) 6.dp else 0.dp,
                    pressedElevation = 2.dp
                )
            ) {
                // ── 冷却期：带圆弧倒计时的状态 ────────────────────────────────
                if (cooldownRemaining > 0) {
                    Box(
                        modifier = Modifier.size(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // 圆弧倒计时进度
                        androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeW = 2.5.dp.toPx()
                            val inset = strokeW / 2f
                            // 轨道
                            drawArc(
                                color = TextMuted.copy(alpha = 0.3f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - strokeW, size.height - strokeW),
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                            // 剩余时间弧
                            drawArc(
                                color = TextMuted.copy(alpha = 0.8f),
                                startAngle = -90f,
                                sweepAngle = 360f * cooldownArcProgress,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - strokeW, size.height - strokeW),
                                style = Stroke(width = strokeW, cap = StrokeCap.Round)
                            )
                        }
                        Text(
                            text = "$cooldownRemaining",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextMuted
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                } else if (buttonEnabled) {
                    // 可用时：显示向右箭头图标（主题色）
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowForward,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.Black.copy(alpha = 0.7f)
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    text = enterLabel,
                    fontSize = if (buttonEnabled && cooldownRemaining == 0) 15.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (buttonEnabled) Color.Black.copy(alpha = 0.85f) else TextMuted.copy(alpha = 0.7f),
                    letterSpacing = if (buttonEnabled && cooldownRemaining == 0) 0.3.sp else 0.sp
                )
            }
        }

        // ── 按钮可用时显示「确认提示」小字 ──────────────────────────────────
        AnimatedVisibility(
            visible = buttonEnabled && cooldownRemaining == 0,
            enter = fadeIn(tween(400)) + slideInVertically(tween(300)) { it / 2 },
            exit = fadeOut(tween(200))
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "✓ 有意识地进入",
                    fontSize = 11.sp,
                    color = themeConfig.accentColor.copy(alpha = 0.5f + breathProgress * 0.3f),
                    letterSpacing = 0.8.sp
                )
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  📋 今日使用记录 分区卡片（时间轴风格）
// ════════════════════════════════════════════════════════════════════════════

@Composable
private fun TodayRecordsSection(records: List<UsageRecordEntity>) {
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(DarkSurface)
            .border(1.dp, DarkSurfaceVariant, RoundedCornerShape(20.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        SectionLabel(
            emoji = "📋",
            label = "今日记录",
            bgColor = DarkSurfaceVariant,
            borderColor = Color(0xFF2A3555),
            textColor = TextSecondary
        )

        Spacer(Modifier.height(16.dp))

        records.forEachIndexed { index, record ->
            TimelineRecordRow(
                record = record,
                timeFormat = timeFormat,
                isLast = index == records.lastIndex
            )
        }
    }
}

@Composable
private fun TimelineRecordRow(
    record: UsageRecordEntity,
    timeFormat: SimpleDateFormat,
    isLast: Boolean
) {
    val durationText = formatRecordDuration(record.durationSeconds)
    val timeText = remember(record.startTime) {
        timeFormat.format(Date(record.startTime))
    }
    val hasPurpose = record.purpose?.isNotBlank() == true

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        // 左侧：时间轴线
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(44.dp)
        ) {
            // 时间点圆点
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(
                        if (hasPurpose) MindfulAccent.copy(alpha = 0.7f)
                        else TextMuted.copy(alpha = 0.5f)
                    )
                    .offset(y = 5.dp)
            )
            // 连接线（非最后一项）
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(36.dp)
                        .background(DarkSurfaceVariant)
                        .offset(y = 5.dp)
                )
            }
        }

        // 右侧：内容
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 20.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = timeText,
                    fontSize = 12.sp,
                    color = TextMuted,
                    fontWeight = FontWeight.Medium
                )
                // 时长 pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceVariant)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = durationText,
                        fontSize = 11.sp,
                        color = TextMuted,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            // 目的文字
            Text(
                text = record.purpose?.takeIf { it.isNotBlank() } ?: "无备注",
                fontSize = 14.sp,
                color = if (hasPurpose) TextSecondary else TextMuted.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/** 将秒数格式化为简洁时长字符串，用于记录行 */
private fun formatRecordDuration(seconds: Long): String {
    if (seconds <= 0) return "< 1分"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return when {
        h > 0 && m > 0 -> "${h}h${m}m"
        h > 0 -> "${h}h"
        m > 0 -> "${m}分"
        else -> "${s}秒"
    }
}

// ════════════════════════════════════════════════════════════════════════════
//  主题背景动效层（根据 themeId 渲染不同的全屏粒子/动效）
// ════════════════════════════════════════════════════════════════════════════

/**
 * 永远单调递增的帧时间（毫秒），基于 withFrameMillis。
 * 不存在任何「归零」时刻，所有粒子类动效用它驱动即可彻底无缝。
 */
@Composable
private fun rememberFrameTimeMs(): State<Long> {
    val ms = remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { frameMs ->
                ms.longValue = frameMs - start
            }
        }
    }
    return ms
}

/**
 * 根据当前主题渲染对应的全屏背景动效。
 * 叠加在纯色背景之上、内容之下。
 */
@Composable
fun ThemeBackground(themeId: String, modifier: Modifier = Modifier) {
    when (themeId) {
        "deep_sea"  -> DeepSeaBubbles(modifier)
        "cyberpunk" -> CyberpunkCodeRain(modifier)
        "lava"      -> LavaWaves(modifier)
        "sakura"    -> SakuraPetals(modifier)
        "moon"      -> MoonStarfield(modifier)
        "glitch"    -> GlitchScanlines(modifier)
        "rpg"       -> RpgPixelParticles(modifier)
        // default / 正念：呼吸光晕
        else        -> MindfulBreath(modifier)
    }
}

// ── 深海：气泡漂浮 ────────────────────────────────────────────────────────────

private data class Bubble(
    val x: Float,          // 0..1 相对宽度
    val radius: Float,     // dp
    val periodMs: Float,   // 完成一次完整上升周期所需毫秒数
    val alpha: Float,
    val phaseMs: Float,    // 时间相位偏移（毫秒），各气泡独立
    val driftAmp: Float    // 横向漂移幅度
)

@Composable
private fun DeepSeaBubbles(modifier: Modifier) {
    val bubbles = remember {
        val rng = java.util.Random(42L)
        List(28) {
            Bubble(
                x        = rng.nextFloat(),
                radius   = 6f + rng.nextFloat() * 18f,
                periodMs = 5000f + rng.nextFloat() * 7000f,  // 5~12 秒一次完整上升
                alpha    = 0.35f + rng.nextFloat() * 0.40f,
                phaseMs  = rng.nextFloat() * 12000f,         // 各自独立相位（毫秒偏移）
                driftAmp = 0.015f + rng.nextFloat() * 0.025f
            )
        }
    }
    // 单调递增时间戳，永不归零，从根本上消除 tween restart 跳变
    val frameTimeMs by rememberFrameTimeMs()
    val accent = Color(0xFF00B4D8)

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        bubbles.forEach { b ->
            // t: 0..1，基于真实单调时间戳，每个气泡独立周期，永不同步归零
            val t = ((frameTimeMs + b.phaseMs) % b.periodMs) / b.periodMs
            // y: t=0 在底部屏幕外，t=1 刚好飘出顶部
            val y = size.height * (1.08f - t * 1.18f)
            // 横向正弦漂移
            val driftX = kotlin.math.sin(t * 2 * Math.PI * 1.5).toFloat() * size.width * b.driftAmp
            val x = size.width * b.x + driftX
            val r = b.radius.dp.toPx()

            // 淡入（t < 0.08）→ 稳定 → 淡出（t > 0.85）
            val fadeAlpha = b.alpha * when {
                t < 0.08f -> t / 0.08f
                t > 0.85f -> (1f - t) / 0.15f
                else      -> 1f - (t - 0.08f) * 0.25f
            }.coerceIn(0f, 1f)

            if (fadeAlpha < 0.01f) return@forEach

            drawCircle(
                color = accent.copy(alpha = fadeAlpha),
                radius = r,
                center = Offset(x, y),
                style = Stroke(width = 2.dp.toPx())
            )
            drawCircle(
                color = accent.copy(alpha = fadeAlpha * 0.12f),
                radius = r,
                center = Offset(x, y)
            )
            drawCircle(
                color = Color.White.copy(alpha = fadeAlpha * 0.55f),
                radius = r * 0.3f,
                center = Offset(x - r * 0.28f, y - r * 0.28f)
            )
        }
    }
}

// ── 赛博：代码雨 ──────────────────────────────────────────────────────────────

private val CODE_CHARS = "01アイウエオカキクケコサシスセソタチツテト"

@Composable
private fun CyberpunkCodeRain(modifier: Modifier) {
    // 每列的当前"头部"位置（0..1 相对高度）
    val colCount = 18
    val columns = remember {
        val rng = java.util.Random(7L)
        List(colCount) { rng.nextFloat() }  // 初始偏移
    }
    val speeds = remember {
        val rng = java.util.Random(13L)
        List(colCount) { 0.18f + rng.nextFloat() * 0.25f }
    }
    val charLists = remember {
        val rng = java.util.Random(99L)
        List(colCount) {
            List(16) { CODE_CHARS[rng.nextInt(CODE_CHARS.length)].toString() }
        }
    }
    val transition = rememberInfiniteTransition(label = "coderain")
    val time by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing)),
        label = "rain_time"
    )

    val paint = remember {
        android.graphics.Paint().apply {
            color = android.graphics.Color.parseColor("#00FF41")
            textSize = 42f   // 稍大，更清晰
            typeface = android.graphics.Typeface.MONOSPACE
            isAntiAlias = true
        }
    }

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val colWidth = size.width / colCount
        val charHeightPx = 40f

        val nativeCanvas: android.graphics.Canvas = drawContext.canvas.nativeCanvas
        // 每列用独立 paint 拷贝，避免 alpha 在列间串扰
        val colPaint = android.graphics.Paint(paint)
        columns.forEachIndexed { i, offset ->
            // 把 time*speed+offset 映射到 0..1，再乘以总高度（含尾部缓冲）
            val progress = (time * speeds[i] + offset) % 1f
            val headY = progress * (size.height + charHeightPx * charLists[i].size)
            val chars = charLists[i]
            chars.forEachIndexed { j, ch ->
                val y = headY - j * charHeightPx
                if (y < -charHeightPx || y > size.height + charHeightPx) return@forEachIndexed
                val brightness = (1f - j.toFloat() / chars.size)
                // 头部字符纯白最亮，后续绿色渐暗，最低亮度提高
                colPaint.alpha = if (j == 0) 255 else (brightness * 220).toInt().coerceIn(60, 220)
                nativeCanvas.drawText(ch, i * colWidth + colWidth * 0.1f, y, colPaint)
            }
        }
    }
}

// ── 熔岩：波浪扰动 ───────────────────────────────────────────────────────────

@Composable
private fun LavaWaves(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "lava")
    val phase1 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "lava_p1"
    )
    val phase2 by transition.animateFloat(
        initialValue = (Math.PI).toFloat(), targetValue = (3 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(4100, easing = LinearEasing)),
        label = "lava_p2"
    )
    val rise by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(2800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "lava_rise"
    )

    val lavaOrange = Color(0xFFFF4500)
    val lavaRed    = Color(0xFFFF1A00)
    val lavaDark   = Color(0xFF8B1A00)

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        // 呼吸感：波浪基准线上下浮动更大幅度
        val baseY = h * (0.65f - rise * 0.10f)   // 从65%上升到55%
        val ampA = h * 0.07f   // 增大波幅
        val ampB = h * 0.04f

        // 第一层波浪（深红填充到底部）
        val path1 = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h)
            lineTo(0f, baseY)
            val steps = 80
            for (s in 0..steps) {
                val x = w * s / steps
                val y = baseY +
                        ampA * kotlin.math.sin(phase1 + x / w * 4 * Math.PI).toFloat() +
                        ampB * kotlin.math.sin(phase2 + x / w * 7 * Math.PI).toFloat()
                lineTo(x, y)
            }
            lineTo(w, h)
            close()
        }
        drawPath(path1, Brush.verticalGradient(
            listOf(lavaOrange.copy(alpha = 0.55f), lavaDark.copy(alpha = 0.85f)),  // 大幅提高
            startY = baseY - ampA * 2, endY = h
        ))

        // 第二层波浪（更亮橙色，稍高）
        val baseY2 = baseY - h * 0.04f
        val path2 = androidx.compose.ui.graphics.Path().apply {
            moveTo(0f, h)
            lineTo(0f, baseY2)
            val steps = 80
            for (s in 0..steps) {
                val x = w * s / steps
                val y = baseY2 +
                        ampB * kotlin.math.sin(phase2 + x / w * 5 * Math.PI).toFloat() +
                        ampA * 0.5f * kotlin.math.sin(phase1 * 1.3f + x / w * 9 * Math.PI).toFloat()
                lineTo(x, y)
            }
            lineTo(w, h)
            close()
        }
        drawPath(path2, Brush.verticalGradient(
            listOf(lavaRed.copy(alpha = 0.50f), lavaOrange.copy(alpha = 0.35f)),
            startY = baseY2, endY = h
        ))

        // 底部强辉光
        drawRect(
            Brush.verticalGradient(
                listOf(Color.Transparent, lavaOrange.copy(alpha = 0.30f), lavaRed.copy(alpha = 0.50f)),
                startY = h * 0.75f, endY = h
            )
        )
    }
}

// ── 樱花：花瓣飘落 ───────────────────────────────────────────────────────────

private data class Petal(
    val startX: Float,    // 0..1
    val size: Float,      // dp
    val periodMs: Float,  // 完成一次完整飘落所需毫秒数
    val swayAmp: Float,   // 横向摆动幅度（0..1）
    val swayFreq: Float,
    val rotSpeed: Float,
    val phaseMs: Float    // 毫秒相位偏移，各花瓣独立
)

@Composable
private fun SakuraPetals(modifier: Modifier) {
    val petals = remember {
        val rng = java.util.Random(55L)
        List(22) {
            Petal(
                startX   = rng.nextFloat(),
                size     = 6f + rng.nextFloat() * 10f,
                periodMs = 7000f + rng.nextFloat() * 9000f, // 7~16 秒一次完整飘落
                swayAmp  = 0.03f + rng.nextFloat() * 0.05f,
                swayFreq = 0.8f + rng.nextFloat() * 1.5f,
                rotSpeed = 0.5f + rng.nextFloat() * 1.5f,
                phaseMs  = rng.nextFloat() * 16000f         // 各自独立毫秒相位
            )
        }
    }
    // 单调递增时间戳，永不归零
    val frameTimeMs by rememberFrameTimeMs()

    val pink = Color(0xFFFF85A1)
    val pink2 = Color(0xFFFFB3C6)

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        petals.forEach { p ->
            // t: 0..1，基于真实单调时间，每朵花瓣独立周期，永不同步归零
            val t = ((frameTimeMs + p.phaseMs) % p.periodMs) / p.periodMs
            // y: t=0 在顶部屏幕外(-8%)，t=1 在底部屏幕外(108%)
            val y = size.height * (-0.08f + t * 1.16f)
            val sway = kotlin.math.sin((t * p.swayFreq * 2 * Math.PI).toDouble()).toFloat()
            val x = size.width * (p.startX + sway * p.swayAmp)
            val rot = (t * p.rotSpeed * 360f) % 360f

            val alpha = when {
                t < 0.07f -> t / 0.07f * 0.90f
                t > 0.88f -> (1f - t) / 0.12f * 0.90f
                else      -> 0.75f + sway * 0.12f
            }.coerceIn(0f, 0.92f)

            if (alpha < 0.01f) return@forEach

            val r = p.size.dp.toPx()
            withTransform({
                rotate(rot, pivot = Offset(x, y))
            }) {
                drawOval(
                    color = pink.copy(alpha = alpha),
                    topLeft = Offset(x - r, y - r * 0.5f),
                    size = Size(r * 2f, r)
                )
                drawOval(
                    color = pink2.copy(alpha = alpha * 0.7f),
                    topLeft = Offset(x - r * 0.5f, y - r),
                    size = Size(r, r * 2f)
                )
            }
        }
    }
}

// ── 月球：星空闪烁 ───────────────────────────────────────────────────────────

private data class Star(
    val x: Float, val y: Float,
    val radius: Float,
    val phase: Float,   // 闪烁相位偏移
    val speed: Float
)

@Composable
private fun MoonStarfield(modifier: Modifier) {
    val stars = remember {
        val rng = java.util.Random(77L)
        List(80) {
            Star(
                x      = rng.nextFloat(),
                y      = rng.nextFloat(),
                radius = 0.8f + rng.nextFloat() * 2.2f,
                phase  = rng.nextFloat(),
                speed  = 0.3f + rng.nextFloat() * 0.8f
            )
        }
    }
    val transition = rememberInfiniteTransition(label = "stars")
    val time by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = LinearEasing)),
        label = "star_time"
    )

    val starColor  = Color(0xFFE8DAFF)
    val purpleGlow = Color(0xFFB39DDB)

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        // 大星云辉光（大幅增强）
        drawCircle(
            brush = Brush.radialGradient(
                listOf(purpleGlow.copy(alpha = 0.22f), purpleGlow.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width * 0.3f, size.height * 0.2f),
                radius = size.width * 0.60f
            ),
            radius = size.width * 0.60f,
            center = Offset(size.width * 0.3f, size.height * 0.2f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(purpleGlow.copy(alpha = 0.16f), purpleGlow.copy(alpha = 0.04f), Color.Transparent),
                center = Offset(size.width * 0.75f, size.height * 0.55f),
                radius = size.width * 0.45f
            ),
            radius = size.width * 0.45f,
            center = Offset(size.width * 0.75f, size.height * 0.55f)
        )

        stars.forEach { s ->
            val twinkle = (kotlin.math.sin(
                (time * s.speed + s.phase) * 2 * Math.PI
            ).toFloat() + 1f) / 2f   // 0..1
            // 闪烁范围更大，最亮更亮
            val alpha = 0.35f + twinkle * 0.65f
            val r = s.radius.dp.toPx() * (0.8f + twinkle * 0.6f)

            // 十字星光
            val cx = s.x * size.width
            val cy = s.y * size.height
            drawCircle(starColor.copy(alpha = alpha), radius = r, center = Offset(cx, cy))
            if (r > 1.5.dp.toPx()) {   // 阈值降低，更多星有十字
                val armLen = r * 3.0f
                drawLine(
                    starColor.copy(alpha = alpha * 0.65f),
                    start = Offset(cx - armLen, cy),
                    end = Offset(cx + armLen, cy),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    starColor.copy(alpha = alpha * 0.65f),
                    start = Offset(cx, cy - armLen),
                    end = Offset(cx, cy + armLen),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }
    }
}

// ── 故障：扫描线 + 色差偏移 ────────────────────────────────────────────────────

@Composable
private fun GlitchScanlines(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "glitch")
    // 扫描线位置——连续线性，本身就无缝
    val scanPos by transition.animateFloat(
        initialValue = -0.1f, targetValue = 1.1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = LinearEasing)),
        label = "scan_pos"
    )
    // 快速抖动时钟（用于生成随机种子）
    val glitchTime by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(200, easing = LinearEasing)),
        label = "glitch_jitter"
    )
    // 两个不同素数周期的振荡：2300ms 和 3700ms
    // 它们的最小公倍数 = 8510 秒，远超人类感知范围，节奏看起来随机
    val osc1 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(2300, easing = LinearEasing)),
        label = "glitch_osc1"
    )
    val osc2 by transition.animateFloat(
        initialValue = 0f, targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(3700, easing = LinearEasing)),
        label = "glitch_osc2"
    )

    val pink = Color(0xFFFF0080)
    val cyan = Color(0xFF00FFFF)

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        // 全屏扫描线（CRT 效果）
        val lineSpacing = 4.dp.toPx()
        var lineY = 0f
        while (lineY < size.height) {
            drawLine(
                color = Color.White.copy(alpha = 0.025f),
                start = Offset(0f, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = 1f
            )
            lineY += lineSpacing
        }

        // 移动扫描光带
        val sy = scanPos * size.height
        drawRect(
            brush = Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    pink.copy(alpha = 0.06f),
                    cyan.copy(alpha = 0.08f),
                    Color.Transparent
                ),
                startY = sy - 30f, endY = sy + 30f
            )
        )

        // 故障触发：两个振荡的乘积在峰值时触发
        // sin 峰值处（> 0.7）才触发，两个频率不同 → 看起来随机无规律
        val glitchIntensity = (kotlin.math.sin(osc1.toDouble()).toFloat() *
                               kotlin.math.sin(osc2.toDouble()).toFloat())
        val glitchActive = glitchIntensity > 0.55f  // 阈值越高，触发越稀少

        if (glitchActive) {
            val strength = ((glitchIntensity - 0.55f) / 0.45f).coerceIn(0f, 1f)
            val rng = java.util.Random((glitchTime * 1000).toLong())
            val barCount = 2 + (strength * 3).toInt()   // 强度越高条数越多
            repeat(barCount) {
                val gy = rng.nextFloat() * size.height
                val gh = (4.dp.toPx() + rng.nextFloat() * 20.dp.toPx()) * strength
                val offsetX = (rng.nextFloat() - 0.5f) * 32.dp.toPx() * strength
                drawRect(
                    color = pink.copy(alpha = 0.65f * strength),
                    topLeft = Offset(offsetX, gy),
                    size = Size(size.width, gh)
                )
                drawRect(
                    color = cyan.copy(alpha = 0.50f * strength),
                    topLeft = Offset(-offsetX * 0.7f, gy + gh * 0.5f),
                    size = Size(size.width, gh * 0.65f)
                )
            }
        }

        // 角落辉光
        drawCircle(
            brush = Brush.radialGradient(
                listOf(pink.copy(alpha = 0.08f), Color.Transparent),
                center = Offset(0f, 0f),
                radius = size.width * 0.5f
            ),
            radius = size.width * 0.5f,
            center = Offset(0f, 0f)
        )
        drawCircle(
            brush = Brush.radialGradient(
                listOf(cyan.copy(alpha = 0.06f), Color.Transparent),
                center = Offset(size.width, size.height),
                radius = size.width * 0.5f
            ),
            radius = size.width * 0.5f,
            center = Offset(size.width, size.height)
        )
    }
}

// ── RPG：像素粒子飘散 ─────────────────────────────────────────────────────────

private data class PixelParticle(
    val startX: Float,
    val vx: Float,        // 横向漂移系数
    val vy: Float,        // 飞行高度（占屏幕比例）
    val size: Float,      // 像素大小 dp
    val color: Int,       // 0=gold 1=orange 2=white
    val periodMs: Float,  // 完成一次抛物线所需毫秒数
    val phaseMs: Float    // 毫秒相位偏移，各粒子独立
)

@Composable
private fun RpgPixelParticles(modifier: Modifier) {
    val particles = remember {
        val rng = java.util.Random(33L)
        List(35) {
            PixelParticle(
                startX   = rng.nextFloat(),
                vx       = (rng.nextFloat() - 0.5f) * 0.15f,
                vy       = 0.35f + rng.nextFloat() * 0.55f,
                size     = 2f + (rng.nextInt(4) * 2f),
                color    = rng.nextInt(3),
                periodMs = 4000f + rng.nextFloat() * 6000f, // 4~10 秒一次抛物线
                phaseMs  = rng.nextFloat() * 10000f         // 各自独立毫秒相位
            )
        }
    }
    // 单调递增时间戳，永不归零
    val frameTimeMs by rememberFrameTimeMs()

    val gold   = Color(0xFFFFD700)
    val orange = Color(0xFFFF8C00)
    val white  = Color(0xFFFFF8E1)

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        particles.forEach { p ->
            // t: 0..1，基于真实单调时间，每个粒子独立周期，永不同步归零
            val t = ((frameTimeMs + p.phaseMs) % p.periodMs) / p.periodMs
            val x = size.width * (p.startX + p.vx * t)
            // 抛物线：t=0/1 在底部屏外，t=0.6 在最高处
            val rise = if (t < 0.6f) t / 0.6f else 1f - (t - 0.6f) / 0.4f
            val y = size.height * (1.05f - rise * p.vy)

            val alpha = when {
                t < 0.12f -> t / 0.12f * 0.95f
                t > 0.82f -> (1f - t) / 0.18f * 0.95f
                else      -> 0.90f
            }.coerceIn(0f, 0.95f)

            if (alpha < 0.01f) return@forEach

            val c = when (p.color) {
                0 -> gold.copy(alpha = alpha)
                1 -> orange.copy(alpha = alpha)
                else -> white.copy(alpha = alpha * 0.6f)
            }
            val sz = p.size.dp.toPx()
            val px = (x / sz).toInt() * sz
            val py = (y / sz).toInt() * sz
            drawRect(c, topLeft = Offset(px, py), size = Size(sz, sz))
        }

        // 底部能量积聚辉光
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, gold.copy(alpha = 0.20f), gold.copy(alpha = 0.38f)),
                startY = size.height * 0.65f, endY = size.height
            )
        )
    }
}

// ── 正念（default）：呼吸光晕 ──────────────────────────────────────────────────

@Composable
private fun MindfulBreath(modifier: Modifier) {
    val transition = rememberInfiniteTransition(label = "mindful_breath")
    val breath by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(3200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breath_val"
    )
    val green = Color(0xFF3DDC84)
    val greenDim = Color(0xFF2ECC71)

    androidx.compose.foundation.Canvas(modifier = modifier.fillMaxSize()) {
        val center = Offset(size.width * 0.5f, size.height * 0.38f)
        val r = size.width * (0.30f + breath * 0.14f)

        // 外层大光晕（呼吸感）
        drawCircle(
            brush = Brush.radialGradient(
                listOf(
                    green.copy(alpha = 0.18f + breath * 0.12f),
                    greenDim.copy(alpha = 0.06f + breath * 0.04f),
                    Color.Transparent
                ),
                center = center,
                radius = r * 1.8f
            ),
            radius = r * 1.8f,
            center = center
        )
        // 中层光圈
        drawCircle(
            brush = Brush.radialGradient(
                listOf(green.copy(alpha = 0.28f + breath * 0.18f), Color.Transparent),
                center = center, radius = r * 0.95f
            ),
            radius = r * 0.95f,
            center = center
        )
        // 内层实体光圈（轮廓线）
        drawCircle(
            color = green.copy(alpha = 0.50f + breath * 0.30f),
            radius = r * 0.7f,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )
        // 最内层核心亮点
        drawCircle(
            color = green.copy(alpha = 0.15f + breath * 0.20f),
            radius = r * 0.35f,
            center = center
        )
    }
}
