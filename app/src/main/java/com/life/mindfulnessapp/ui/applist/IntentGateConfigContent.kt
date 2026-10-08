package com.life.mindfulnessapp.ui.applist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.ComparePolicy
import com.life.mindfulnessapp.ui.theme.LogoGreen

/**
 * 意图门配置：门前写下意图，再定时长。
 * 添加流主开关已开；编辑流可关。
 */
@Composable
fun IntentGateConfigContent(
    appName: String,
    requireIntent: Boolean,
    onRequireIntentChange: (Boolean) -> Unit,
    sessionLimitOn: Boolean,
    onSessionLimitChange: (Boolean) -> Unit,
    onManageIntentPool: (() -> Unit)? = null,
    onManageQuickTags: (() -> Unit)? = null,
    onOpenDeepLinkGlance: (() -> Unit)? = null,
    compareEnabled: Boolean = ComparePolicy.DEFAULT_ENABLED,
    onCompareEnabledChange: (Boolean) -> Unit = {},
    compareMinMinutes: Int = ComparePolicy.DEFAULT_MIN_MINUTES,
    onCompareMinMinutesChange: (Int) -> Unit = {},
    allowMasterToggle: Boolean = true,
    modifier: Modifier = Modifier
) {
    val minMinutes = ComparePolicy.sanitizeMinMinutes(compareMinMinutes)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 12.dp, bottom = 48.dp)
    ) {
        CapabilityHeroWithSwitch(
            title = "意图门",
            description = "打开「$appName」前写下意图，再选多久",
            checked = requireIntent,
            onCheckedChange = { on ->
                onRequireIntentChange(on)
                if (on) onSessionLimitChange(true)
                else onSessionLimitChange(false)
            },
            allowToggle = allowMasterToggle
        )

        AnimatedVisibility(
            visible = !requireIntent && allowMasterToggle,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            CapabilityRuleCaption(
                text = "关闭后，打开时不再经过这扇门。",
                modifier = Modifier.padding(top = 16.dp)
            )
        }

        AnimatedVisibility(
            visible = requireIntent,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column {
                Spacer(modifier = Modifier.height(28.dp))

                CapabilityNestedCard(emphasized = sessionLimitOn) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "进门先定时长",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                            modifier = Modifier.weight(1f)
                        )
                        CapabilityGateSwitch(
                            checked = sessionLimitOn,
                            onCheckedChange = onSessionLimitChange
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "写下意图后选择本次多久，到点结束本次使用。",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                        lineHeight = 20.sp
                    )
                    if (sessionLimitOn) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "结束前可续时一次，最长为原时长的三分之一。",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                            lineHeight = 20.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                CapabilityNestedCard(emphasized = compareEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "结束后对照",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.92f),
                            modifier = Modifier.weight(1f)
                        )
                        CapabilityGateSwitch(
                            checked = compareEnabled,
                            onCheckedChange = onCompareEnabledChange
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = if (compareEnabled) {
                            "满所选时长后主动弹出对照；首页符合条件的条目标【照】。"
                        } else {
                            "关闭后不主动对照，首页也不标【照】。"
                        },
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f),
                        lineHeight = 20.sp
                    )
                    AnimatedVisibility(
                        visible = compareEnabled,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Column {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "最低时长",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ComparePolicy.MIN_MINUTES_PRESETS.forEach { minutes ->
                                    FilterChip(
                                        selected = minMinutes == minutes,
                                        onClick = { onCompareMinMinutesChange(minutes) },
                                        label = {
                                            Text("${minutes}分", fontSize = 13.sp)
                                        },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = LogoGreen.copy(alpha = 0.16f),
                                            selectedLabelColor = LogoGreen
                                        )
                                    )
                                }
                            }
                        }
                    }
                }

                if (onManageIntentPool != null) {
                    Spacer(modifier = Modifier.height(20.dp))
                    TextButton(
                        onClick = onManageIntentPool,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "管理意图池 · 分类与合并 ›",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }

                if (onManageQuickTags != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(
                        onClick = onManageQuickTags,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "快捷标签 · 写意图时用 ›",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }

                if (onOpenDeepLinkGlance != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onOpenDeepLinkGlance,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "直达一览 · 能落到哪 ›",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        )
                    }
                }
            }
        }
    }
}
