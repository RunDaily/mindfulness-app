package com.life.mindfulnessapp.ui.applist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 时长锁：每日上限始终作为主内容；添加流主开关已开。
 */
@Composable
fun TimeLockConfigContent(
    appName: String,
    timeLimitOn: Boolean,
    onTimeLimitChange: (Boolean) -> Unit,
    dailyLimit: Int,
    onDailyLimitChange: (Int) -> Unit,
    dailyHint: String? = null,
    allowMasterToggle: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 48.dp)
    ) {
        CapabilityHeroWithSwitch(
            title = "时长锁",
            description = "限制「$appName」每日的使用时长",
            checked = timeLimitOn,
            onCheckedChange = onTimeLimitChange,
            allowToggle = allowMasterToggle
        )

        AnimatedVisibility(
            visible = !timeLimitOn && allowMasterToggle,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CapabilityRuleCaption(
                text = "关闭后不再限制每日使用时长。",
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        AnimatedVisibility(
            visible = timeLimitOn,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(28.dp))

                CapabilityNestedCard(emphasized = true) {
                    DurationLimitSettings(
                        dailyMinutes = dailyLimit,
                        onDailyMinutesChange = onDailyLimitChange,
                        dailyHint = dailyHint
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                CapabilityRuleCaption(
                    text = "用着时顶部会浮着胶囊。额度用尽后会拦住进入；确有必须完成的事，可写意图再进一次（5 / 10 / 15 分钟），每个 App 当日仅一次。"
                )
            }
        }
    }
}
