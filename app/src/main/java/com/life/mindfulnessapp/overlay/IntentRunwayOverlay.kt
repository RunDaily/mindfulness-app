package com.life.mindfulnessapp.overlay

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 全宽半透明意图跑道：先淡入铺开，再慢速跑马灯，最后淡出。
 * 不做胶囊/细轨形态。
 */
@Composable
fun IntentRunwayOverlay(
    intentText: String,
    isDarkTheme: Boolean,
    accent: Color,
    onFinished: () -> Unit,
    timeFont: FontFamily = FontFamily.Default,
    awarenessMode: com.life.mindfulnessapp.domain.model.SessionAwarenessMode =
        com.life.mindfulnessapp.domain.model.SessionAwarenessMode.TASK
) {
    val line = remember(intentText, awarenessMode) {
        val t = intentText.trim().ifBlank { "这一次" }
        val prefix = com.life.mindfulnessapp.domain.model.SessionAwarenessCopy.runwayPrefix(awarenessMode)
        "$prefix · $t"
    }
    // 慢速：字数越多越久，整体明显慢于广告条
    val scrollMs = remember(line) {
        (5200L + (line.length - 8).coerceAtLeast(0) * 140L).coerceIn(5200L, 9000L)
    }

    val runwayAlpha = remember { Animatable(0f) }
    val textAlpha = remember { Animatable(0f) }
    val textOffsetPx = remember { Animatable(0f) }

    val band = if (isDarkTheme) {
        Color(0xCC121214)
    } else {
        Color(0xCCF4F4F6)
    }
    val ink = if (isDarkTheme) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
    ) {
        val trackW = maxWidth
        val startX = with(density) { trackW.toPx() + 24.dp.toPx() }
        val endX = with(density) { -(trackW.toPx() * 0.55f) }

        LaunchedEffect(line, trackW) {
            runwayAlpha.snapTo(0f)
            textAlpha.snapTo(0f)
            textOffsetPx.snapTo(startX)

            // 1) 铺满宽度的半透明带
            runwayAlpha.animateTo(1f, tween(320, easing = FastOutSlowInEasing))
            delay(180L)

            // 2) 慢速跑马灯
            launch { textAlpha.animateTo(1f, tween(200, easing = FastOutSlowInEasing)) }
            textOffsetPx.animateTo(
                endX,
                tween(durationMillis = scrollMs.toInt(), easing = LinearEasing)
            )
            textAlpha.animateTo(0f, tween(220, easing = FastOutSlowInEasing))
            delay(80L)

            // 3) 收带
            runwayAlpha.animateTo(0f, tween(280, easing = FastOutSlowInEasing))
            onFinished()
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .graphicsLayer { alpha = runwayAlpha.value }
                .background(band),
            contentAlignment = Alignment.CenterStart
        ) {
            // 左侧色点：表示「跑道已就绪」
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxWidth(0.01f)
                    .height(44.dp)
                    .background(accent.copy(alpha = 0.55f))
            )
            Text(
                text = line,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = textOffsetPx.value
                        alpha = textAlpha.value
                    },
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = ink,
                fontFamily = timeFont,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Visible
            )
        }
    }
}
