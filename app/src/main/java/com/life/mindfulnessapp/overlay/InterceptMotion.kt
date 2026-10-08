package com.life.mindfulnessapp.overlay

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

/**
 * 拦截页轻量动效：淡入 + 位移，避免 expandVertically 每帧触发布局重算。
 */
internal object InterceptMotion {
    private val easing = FastOutSlowInEasing

    fun revealEnter(duration: Int = 260): EnterTransition =
        fadeIn(tween(duration, easing = easing)) +
            slideInVertically(tween(duration, easing = easing)) { fullHeight -> fullHeight / 6 }

    fun revealExit(duration: Int = 120): ExitTransition =
        fadeOut(tween(duration, easing = easing)) +
            slideOutVertically(tween(duration, easing = easing)) { fullHeight -> fullHeight / 6 }

    fun contextEnter(duration: Int = 200): EnterTransition =
        fadeIn(tween(duration, easing = easing)) +
            slideInVertically(tween(duration, easing = easing)) { fullHeight -> fullHeight / 8 }

    fun contextExit(duration: Int = 100): ExitTransition =
        fadeOut(tween(duration, easing = easing)) +
            slideOutVertically(tween(duration, easing = easing)) { fullHeight -> fullHeight / 8 }
}
