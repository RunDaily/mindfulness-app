package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.ExploreOtherIntentQuota
import com.life.mindfulnessapp.domain.model.IntentGateAction
import com.life.mindfulnessapp.domain.model.IntentGateProfile
import com.life.mindfulnessapp.domain.model.IntentGateProfiles
import com.life.mindfulnessapp.domain.model.RecentPurposeStat

/**
 * 探索门中下区：中区搜主通道 + 其它进法折叠；下区离开与替代。
 * 搜索历史仅走搜索框旁入口；上区语境由头区承担。
 */
@Composable
internal fun ExploreTripleZoneGate(
    themeConfig: InterceptThemeConfig,
    gateProfile: IntentGateProfile,
    enabled: Boolean,
    otherIntentUsed: Int,
    otherIntentLimit: Int = ExploreOtherIntentQuota.DEFAULT_DAILY_LIMIT,
    selectedActionId: String?,
    freeWriteOpen: Boolean,
    freeWriteText: String,
    leaveAffirming: Boolean,
    leaveAffirmLabel: String,
    dismissHintCount: Int,
    recentPurposes: List<RecentPurposeStat>,
    showLeaveZone: Boolean,
    searchSlot: @Composable () -> Unit,
    onRecentPurposeSelect: (String) -> Unit,
    onOtherActionClick: (IntentGateAction) -> Unit,
    onFreeWriteOpen: () -> Unit,
    onFreeWriteChange: (String) -> Unit,
    onBrowseClick: () -> Unit,
    onLeave: () -> Unit,
    onBreath: () -> Unit,
    onMoreMeaningful: () -> Unit,
) {
    val exhausted = ExploreOtherIntentQuota.isExhausted(otherIntentUsed, otherIntentLimit)
    val otherEnabled = enabled && !exhausted
    val otherRemaining = ExploreOtherIntentQuota.remaining(otherIntentUsed, otherIntentLimit)
    val chips = gateProfile.secondaryActions
        .filterNot { IntentGateProfiles.isBrowseLikeLabel(it.label) || it.id == "browse" }
        .take(IntentGateProfiles.MAX_EXPLORE_OTHER_OPS)
    val presetLabels = remember(chips) { chips.map { it.label.trim() }.toSet() }

    val otherActive = freeWriteOpen ||
        (selectedActionId != null && selectedActionId != "browse") ||
        selectedActionId == "browse"
    var otherExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(otherActive) {
        if (otherActive) otherExpanded = true
    }

    // 其它进法里的「意图历史」：手写过的意图；与预设 chip、随便看看去重
    val intentHistoryChips = remember(recentPurposes, presetLabels) {
        recentPurposes
            .map { it.purpose.trim() }
            .filter { it.isNotEmpty() }
            .filterNot { IntentGateProfiles.isBrowseLikeLabel(it) }
            .filterNot { it in presetLabels }
            .distinct()
            .take(4)
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        // ── 中区：主操作 ──────────────────────────────────────
        Text(
            text = "这一次",
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeConfig.textTertiary.copy(alpha = 0.7f),
            letterSpacing = 0.6.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        searchSlot()
        Text(
            text = "有目的进入 · 尽量避开推荐流 · 搜索不占次数",
            fontSize = 11.sp,
            color = themeConfig.textTertiary.copy(alpha = 0.72f),
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(18.dp))
        HorizontalDivider(
            thickness = 1.dp,
            color = themeConfig.dividerColor.copy(alpha = 0.35f)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled) { otherExpanded = !otherExpanded }
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (exhausted) {
                    "其它进法 · 今日次数已用完（$otherIntentUsed/$otherIntentLimit）"
                } else {
                    "其它进法 · 今日还剩 $otherRemaining 次"
                },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = if (exhausted) {
                    themeConfig.limitAccentColor.copy(alpha = 0.85f)
                } else {
                    themeConfig.textSecondary.copy(alpha = 0.88f)
                },
                textDecoration = TextDecoration.Underline
            )
            Text(
                text = if (otherExpanded) "收起" else "展开",
                fontSize = 11.sp,
                color = themeConfig.textTertiary.copy(alpha = 0.7f)
            )
        }

        AnimatedVisibility(
            visible = otherExpanded,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 10.dp)) {
                // 1) 写下意图（在历史 chip 之上）
                if (freeWriteOpen) {
                    ExploreFreeWriteField(
                        themeConfig = themeConfig,
                        text = freeWriteText,
                        enabled = otherEnabled,
                        onChange = onFreeWriteChange
                    )
                } else {
                    Text(
                        text = "写下意图…",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (otherEnabled) {
                            themeConfig.textSecondary.copy(alpha = 0.88f)
                        } else {
                            themeConfig.textTertiary.copy(alpha = 0.4f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = 1.dp,
                                color = themeConfig.dividerColor.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = otherEnabled, onClick = onFreeWriteOpen)
                            .padding(horizontal = 12.dp, vertical = 11.dp)
                    )
                }

                // 2) 意图历史 chips
                if (intentHistoryChips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "最近意图",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = themeConfig.textTertiary.copy(alpha = 0.78f),
                        letterSpacing = 0.4.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        intentHistoryChips.forEach { purpose ->
                            ExploreOtherIntentChip(
                                label = purpose,
                                selected = freeWriteOpen &&
                                    freeWriteText.trim() == purpose &&
                                    selectedActionId == null,
                                enabled = otherEnabled,
                                themeConfig = themeConfig,
                                onClick = { onRecentPurposeSelect(purpose) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                // 3) 预设其它进法
                if (chips.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        chips.chunked(2).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                row.forEach { action ->
                                    ExploreOtherIntentChip(
                                        label = action.label,
                                        selected = selectedActionId == action.id,
                                        enabled = otherEnabled,
                                        themeConfig = themeConfig,
                                        onClick = { onOtherActionClick(action) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (row.size == 1) {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "随便看看",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Light,
                    color = if (otherEnabled) {
                        themeConfig.textTertiary.copy(alpha = 0.72f)
                    } else {
                        themeConfig.textTertiary.copy(alpha = 0.35f)
                    },
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable(enabled = otherEnabled, onClick = onBrowseClick)
                        .padding(vertical = 4.dp)
                )
            }
        }

        // ── 下区：离开与替代 ──────────────────────────────────
        if (showLeaveZone) {
            Spacer(modifier = Modifier.height(22.dp))
            HorizontalDivider(
                thickness = 1.dp,
                color = themeConfig.dividerColor.copy(alpha = 0.35f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "不进也可以",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textTertiary.copy(alpha = 0.72f),
                letterSpacing = 0.6.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onLeave,
                enabled = enabled || leaveAffirming,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = themeConfig.accentColor,
                    disabledContainerColor = themeConfig.accentColor,
                    contentColor = themeConfig.accentForeground,
                    disabledContentColor = themeConfig.accentForeground
                ),
                shape = RoundedCornerShape(14.dp),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
            ) {
                Text(
                    text = if (leaveAffirming) leaveAffirmLabel else "先离开",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "呼吸一分钟",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        themeConfig.textSecondary.copy(alpha = 0.9f)
                    } else {
                        themeConfig.textTertiary.copy(alpha = 0.4f)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            BorderStroke(1.dp, themeConfig.dividerColor.copy(alpha = 0.4f)),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = enabled, onClick = onBreath)
                        .padding(horizontal = 8.dp, vertical = 11.dp)
                )
                Text(
                    text = "有意义的事",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) {
                        themeConfig.textSecondary.copy(alpha = 0.9f)
                    } else {
                        themeConfig.textTertiary.copy(alpha = 0.4f)
                    },
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .border(
                            BorderStroke(1.dp, themeConfig.dividerColor.copy(alpha = 0.4f)),
                            RoundedCornerShape(10.dp)
                        )
                        .clickable(enabled = enabled, onClick = onMoreMeaningful)
                        .padding(horizontal = 8.dp, vertical = 11.dp)
                )
            }
            if (dismissHintCount > 0 && !leaveAffirming) {
                Text(
                    text = "今日已守住 $dismissHintCount 次",
                    fontSize = 11.sp,
                    color = themeConfig.textTertiary.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun ExploreOtherIntentChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    themeConfig: InterceptThemeConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val border = when {
        selected -> themeConfig.accentColor.copy(alpha = 0.55f)
        else -> themeConfig.dividerColor.copy(alpha = 0.42f)
    }
    Text(
        text = label,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = when {
            !enabled -> themeConfig.textTertiary.copy(alpha = 0.35f)
            selected -> themeConfig.accentColor
            else -> themeConfig.textSecondary.copy(alpha = 0.9f)
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) themeConfig.accentColor.copy(alpha = 0.08f)
                else themeConfig.surfaceColor.copy(alpha = 0.55f)
            )
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 9.dp)
    )
}

@Composable
private fun ExploreFreeWriteField(
    themeConfig: InterceptThemeConfig,
    text: String,
    enabled: Boolean,
    onChange: (String) -> Unit
) {
    androidx.compose.material3.OutlinedTextField(
        value = text,
        onValueChange = { if (it.length <= 40) onChange(it) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        placeholder = {
            Text(
                "写下意图…",
                fontSize = 13.sp,
                color = themeConfig.textTertiary.copy(alpha = 0.8f)
            )
        },
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 13.sp,
            color = themeConfig.textPrimary
        ),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = themeConfig.accentColor.copy(alpha = 0.5f),
            unfocusedBorderColor = themeConfig.dividerColor.copy(alpha = 0.4f),
            cursorColor = themeConfig.accentColor,
            focusedContainerColor = themeConfig.surfaceColor.copy(alpha = 0.95f),
            unfocusedContainerColor = themeConfig.surfaceColor.copy(alpha = 0.78f)
        )
    )
}
