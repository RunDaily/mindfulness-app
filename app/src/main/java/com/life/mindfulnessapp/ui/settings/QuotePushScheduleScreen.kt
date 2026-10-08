package com.life.mindfulnessapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.PeriodWindow
import com.life.mindfulnessapp.ui.applist.ClockTimePickerDialog
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.DayBorder
import com.life.mindfulnessapp.ui.theme.DayCardBg
import com.life.mindfulnessapp.ui.theme.DayTextPrimary
import com.life.mindfulnessapp.ui.theme.DayTextSecondary
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.NightBg
import com.life.mindfulnessapp.ui.theme.NightBorder
import com.life.mindfulnessapp.ui.theme.NightCardBg
import com.life.mindfulnessapp.ui.theme.NightTextPrimary
import com.life.mindfulnessapp.ui.theme.NightTextSecondary
import com.life.mindfulnessapp.ui.theme.themeChrome

/**
 * 格言推送：设定开始/截止时段 + 间隔。
 */
@Composable
fun QuotePushScheduleScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val chrome = themeChrome()
    val isDark = chrome.isDark
    val startMinute by viewModel.scheduledQuotePushStart.collectAsState()
    val endMinute by viewModel.scheduledQuotePushEnd.collectAsState()
    val interval by viewModel.scheduledQuotePushInterval.collectAsState()

    val bgColor = chrome.bg
    val cardColor = chrome.card
    val borderColor = chrome.border
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary

    var editingField by remember { mutableStateOf<String?>(null) }

    val previewSlots = remember(startMinute, endMinute, interval) {
        viewModel.previewQuotePushSlots()
    }
    val rangeLabel = remember(startMinute, endMinute) {
        when {
            startMinute == endMinute -> "全天"
            endMinute < startMinute ->
                "${PeriodWindow.formatHm(startMinute)} – ${PeriodWindow.formatHm(endMinute)} · 跨午夜"
            else ->
                "${PeriodWindow.formatHm(startMinute)} – ${PeriodWindow.formatHm(endMinute)}"
        }
    }
    val previewText = remember(previewSlots) {
        when {
            previewSlots.isEmpty() -> "当前设置不会产生推送"
            previewSlots.size <= 6 ->
                previewSlots.joinToString(" · ") { PeriodWindow.formatHm(it) }
            else ->
                previewSlots.take(5).joinToString(" · ") { PeriodWindow.formatHm(it) } +
                    " · …共 ${previewSlots.size} 次"
        }
    }

    if (editingField != null) {
        val isStart = editingField == "start"
        ClockTimePickerDialog(
            title = if (isStart) "开始时间" else "截止时间",
            initialMinuteOfDay = if (isStart) startMinute else endMinute,
            onDismiss = { editingField = null },
            onConfirm = { minute ->
                if (isStart) {
                    viewModel.setScheduledQuotePushWindow(minute, endMinute)
                } else {
                    viewModel.setScheduledQuotePushWindow(startMinute, minute)
                }
                editingField = null
            }
        )
    }

    Scaffold(
        containerColor = bgColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textPrimary
                    )
                }
                Text(
                    text = "推送时段",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Text(
                text = "设定一天内的开始与截止时间，再选间隔。心锚会在此时段内按时推送，并借助保活尽量准点送达。",
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.75f),
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            SettingsSectionLabel("时段", textSecondary)
            SettingsGroup(cardColor = cardColor, borderColor = borderColor) {
                TimeFieldRow(
                    title = "开始",
                    value = PeriodWindow.formatHm(startMinute),
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { editingField = "start" }
                )
                GroupDivider(borderColor)
                TimeFieldRow(
                    title = "截止",
                    value = PeriodWindow.formatHm(endMinute),
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { editingField = "end" }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = rangeLabel,
                fontSize = 13.sp,
                color = LogoGreen.copy(alpha = 0.85f),
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))
            SettingsSectionLabel("间隔", textSecondary)
            SettingsGroup(cardColor = cardColor, borderColor = borderColor) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppPreferences.QUOTE_PUSH_INTERVAL_OPTIONS.take(3).forEach { opt ->
                            IntervalChip(
                                label = AppPreferences.formatQuotePushInterval(opt),
                                selected = interval == opt,
                                accent = LogoGreen,
                                textPrimary = textPrimary,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setScheduledQuotePushInterval(opt) }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppPreferences.QUOTE_PUSH_INTERVAL_OPTIONS.drop(3).forEach { opt ->
                            IntervalChip(
                                label = AppPreferences.formatQuotePushInterval(opt),
                                selected = interval == opt,
                                accent = LogoGreen,
                                textPrimary = textPrimary,
                                modifier = Modifier.weight(1f),
                                onClick = { viewModel.setScheduledQuotePushInterval(opt) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            SettingsSectionLabel("今日预览", textSecondary)
            SettingsGroup(cardColor = cardColor, borderColor = borderColor) {
                Text(
                    text = previewText,
                    fontSize = 14.sp,
                    color = textPrimary.copy(alpha = 0.82f),
                    lineHeight = 22.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun TimeFieldRow(
    title: String,
    value: String,
    textPrimary: androidx.compose.ui.graphics.Color,
    textSecondary: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = LogoGreen
        )
        Text(
            text = "  改",
            fontSize = 13.sp,
            color = textSecondary.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun IntervalChip(
    label: String,
    selected: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    textPrimary: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (selected) accent.copy(alpha = 0.16f)
                else textPrimary.copy(alpha = 0.05f)
            )
            .border(
                1.dp,
                if (selected) accent.copy(alpha = 0.5f)
                else textPrimary.copy(alpha = 0.08f),
                RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) accent else textPrimary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
    }
}
