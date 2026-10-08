package com.life.mindfulnessapp.overlay

import androidx.compose.runtime.Composable
import com.life.mindfulnessapp.domain.model.DailyCapFacts
import com.life.mindfulnessapp.domain.model.ThemePack

/** 硬挡写的是今日还是本周。周限只在日限未尽时单独出现。 */
enum class LimitReachedScope {
    Today,
    Week
}

/**
 * 时长触顶。事实是已用 / 限额，只有离开；脚注格言可收藏。
 * [usedSeconds] 与 [limitSeconds] 必须和拦住这次的那套账相同。
 */
@Composable
fun LimitReachedOverlayScreen(
    usedSeconds: Long,
    limitSeconds: Long,
    appName: String,
    packageName: String = "",
    scope: LimitReachedScope = LimitReachedScope.Today,
    themePack: ThemePack = ThemePack.Night,
    onDismiss: () -> Unit
) {
    val usedMinutes = DailyCapFacts.wholeMinutes(usedSeconds)
    val cap = DailyCapFacts.wholeMinutes(limitSeconds)
    val hero = if (cap > 0) "$usedMinutes / $cap" else "$usedMinutes"
    val (title, whenLine) = when (scope) {
        LimitReachedScope.Today -> "今天的时间\n用完了" to "明天再开"
        LimitReachedScope.Week -> "这周的时间\n用完了" to "下周再开"
    }
    HardBlockOverlayScreen(
        packageName = packageName,
        appName = appName,
        title = title,
        hero = hero,
        heroIsFraction = true,
        unit = "分",
        whenLine = whenLine,
        showStopQuote = true,
        themePack = themePack,
        onLeave = onDismiss
    )
}
