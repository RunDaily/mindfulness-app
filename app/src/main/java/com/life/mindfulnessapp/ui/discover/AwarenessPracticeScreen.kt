package com.life.mindfulnessapp.ui.discover

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.AwarenessPracticeRhythm
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.CompanionPath
import com.life.mindfulnessapp.domain.model.SessionAwarenessCopy
import com.life.mindfulnessapp.domain.model.SessionAwarenessMode
import com.life.mindfulnessapp.ui.settings.SettingsSwitchRow
import com.life.mindfulnessapp.ui.theme.LogoGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AwarenessPracticeUiState(
    val enabled: Boolean = false,
    val rhythm: AwarenessPracticeRhythm = AwarenessPracticeRhythm.DEFAULT,
)

@HiltViewModel
class AwarenessPracticeViewModel @Inject constructor(
    private val appPreferences: AppPreferences,
) : ViewModel() {

    val state: StateFlow<AwarenessPracticeUiState> = combine(
        appPreferences.awarenessPracticeEnabled,
        appPreferences.awarenessPracticeRhythm
    ) { enabled, rhythm ->
        AwarenessPracticeUiState(enabled = enabled, rhythm = rhythm)
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AwarenessPracticeUiState(
            enabled = appPreferences.isAwarenessPracticeEnabled(),
            rhythm = appPreferences.getAwarenessPracticeRhythm()
        )
    )

    fun setEnabled(enabled: Boolean) {
        appPreferences.setAwarenessPracticeEnabled(enabled)
    }

    fun setRhythm(rhythm: AwarenessPracticeRhythm) {
        appPreferences.setAwarenessPracticeRhythm(rhythm)
    }
}

private enum class PracticePreviewAct {
    Quiet,
    Ask,
    Compare,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AwarenessPracticeScreen(
    onNavigateBack: () -> Unit,
    viewModel: AwarenessPracticeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.luminance() < 0.3f
    var previewAct by remember { mutableStateOf(PracticePreviewAct.Ask) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("觉察练习", fontWeight = FontWeight.SemiBold, fontSize = 17.sp)
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background,
                    titleContentColor = cs.onBackground,
                    navigationIconContentColor = cs.onBackground
                )
            )
        },
        containerColor = cs.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "使用中问一句还在不在；离开时对照这一次。不拦截、不扣分。",
                fontSize = 14.sp,
                color = cs.onSurface.copy(alpha = 0.62f),
                lineHeight = 21.sp
            )

            // 三幕预览：不设「如何运作」分区标题，tab 自己说话
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surface)
            ) {
                PracticePreviewTabs(
                    selected = previewAct,
                    onSelect = { previewAct = it },
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
                PracticePreviewStage(
                    act = previewAct,
                    isDark = isDark,
                    modifier = Modifier
                        .padding(horizontal = 12.dp)
                        .padding(top = 4.dp)
                )
                Text(
                    text = previewCaption(previewAct, state.rhythm),
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.52f),
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
                HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.35f))
                PracticeRulesBlock(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }

            // 开关：不设「认领」标题，行本身即动作
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(cs.surface)
                    .padding(horizontal = 16.dp)
            ) {
                SettingsSwitchRow(
                    title = "开启觉察练习",
                    subtitle = "意图门会话生效",
                    checked = state.enabled,
                    textPrimary = cs.onSurface,
                    textSecondary = cs.onSurface,
                    isDark = isDark,
                    onCheckedChange = viewModel::setEnabled
                )
                if (state.enabled) {
                    HorizontalDivider(color = cs.outlineVariant.copy(alpha = 0.35f))
                    Text(
                        text = "中途节奏",
                        fontSize = 11.sp,
                        color = cs.onSurface.copy(alpha = 0.42f),
                        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                    ) {
                        RhythmOption(
                            rhythm = AwarenessPracticeRhythm.Sparse,
                            selected = state.rhythm == AwarenessPracticeRhythm.Sparse,
                            onClick = { viewModel.setRhythm(AwarenessPracticeRhythm.Sparse) },
                            modifier = Modifier.weight(1f)
                        )
                        RhythmOption(
                            rhythm = AwarenessPracticeRhythm.Normal,
                            selected = state.rhythm == AwarenessPracticeRhythm.Normal,
                            onClick = { viewModel.setRhythm(AwarenessPracticeRhythm.Normal) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            Text(
                text = if (state.enabled) {
                    "离开对照不跟节奏走——开着就问一次。"
                } else {
                    "关着时中途与离开都不问；打开后可选节奏。"
                },
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.42f),
                lineHeight = 18.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

@Composable
private fun PracticePreviewTabs(
    selected: PracticePreviewAct,
    onSelect: (PracticePreviewAct) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Row(modifier = modifier.fillMaxWidth()) {
        PracticePreviewAct.entries.forEach { act ->
            val on = act == selected
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = { onSelect(act) }
                    )
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = when (act) {
                        PracticePreviewAct.Quiet -> "1"
                        PracticePreviewAct.Ask -> "2"
                        PracticePreviewAct.Compare -> "3"
                    },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.8.sp,
                    color = if (on) LogoGreen.copy(alpha = 0.9f) else cs.onSurface.copy(alpha = 0.32f)
                )
                Text(
                    text = when (act) {
                        PracticePreviewAct.Quiet -> "安静"
                        PracticePreviewAct.Ask -> "轻问"
                        PracticePreviewAct.Compare -> "对照"
                    },
                    fontSize = 13.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (on) cs.onSurface else cs.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 2.dp)
                )
                Box(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .width(28.dp)
                        .height(1.5.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(
                            if (on) cs.onSurface.copy(alpha = 0.4f)
                            else Color.Transparent
                        )
                )
            }
        }
    }
}

@Composable
private fun PracticePreviewStage(
    act: PracticePreviewAct,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val stageBrush = if (isDark) {
        Brush.linearGradient(
            listOf(Color(0xFF1A2218), Color(0xFF121610), Color(0xFF0B0C0E))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFE8EDE6), Color(0xFFD5DBD3), Color(0xFFC8CEC6))
        )
    }
    val fake = if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.06f)
    val bar = if (isDark) Color(0xF0181B20) else Color(0xF5FAFAFC)
    val island = if (isDark) Color(0xFF121214) else Color(0xFFF7F7F8)
    val border = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.06f)
    val ink = if (isDark) Color(0xFFF2F2F7) else Color(0xFF1C1C1E)
    val muted = if (isDark) Color(0xFFF2F2F7).copy(alpha = 0.45f) else Color(0xFF1C1C1E).copy(alpha = 0.45f)
    val sampleNaming = "看数据"
    val prompt = SessionAwarenessCopy.midCheckPrompt(
        mode = SessionAwarenessMode.TASK,
        naming = sampleNaming,
        path = CompanionPath.INTENT
    )
    val yes = SessionAwarenessCopy.midCheckYes(SessionAwarenessMode.TASK, CompanionPath.INTENT)
    val no = SessionAwarenessCopy.midCheckNo(SessionAwarenessMode.TASK, CompanionPath.INTENT)
    val endPrompt = SessionAwarenessCopy.endComparePrompt(CompanionPath.INTENT)
    val endYes = SessionAwarenessCopy.endCompareYes(CompanionPath.INTENT)
    val endNo = SessionAwarenessCopy.endCompareNo(CompanionPath.INTENT)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(128.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(stageBrush)
            .border(1.dp, border, RoundedCornerShape(12.dp))
    ) {
        Box(
            modifier = Modifier
                .padding(start = 12.dp, end = 12.dp, top = 16.dp)
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(fake)
        )
        Box(
            modifier = Modifier
                .padding(start = 12.dp, top = 52.dp)
                .fillMaxWidth(0.48f)
                .height(7.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(fake)
        )
        when (act) {
            PracticePreviewAct.Quiet -> {
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 14.dp)
                        .width(140.dp)
                        .height(30.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(bar)
                        .border(1.dp, border, RoundedCornerShape(15.dp))
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Box(
                        modifier = Modifier
                            .width(14.dp)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(LogoGreen.copy(alpha = 0.35f))
                    )
                    Text("02:14", fontSize = 11.sp, color = ink.copy(alpha = 0.88f))
                    Text("停", fontSize = 10.sp, color = muted)
                }
            }
            PracticePreviewAct.Ask -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .width(188.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(island)
                        .border(1.dp, border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = prompt,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = LogoGreen.copy(alpha = 0.92f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = sampleNaming,
                            fontSize = 12.sp,
                            color = ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text(yes, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LogoGreen)
                        Text(no, fontSize = 12.sp, color = muted)
                    }
                }
            }
            PracticePreviewAct.Compare -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 10.dp, end = 10.dp, bottom = 12.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(island)
                        .border(1.dp, border, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp, vertical = 11.dp)
                ) {
                    Text(
                        text = endPrompt,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = ink,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(endYes, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = LogoGreen)
                        Text(endNo, fontSize = 12.sp, color = muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun PracticeRulesBlock(modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val rules = listOf(
        "默认关闭，开了才问",
        "不拦截、不扣分；超时自消不算失败",
        "${BrowseCasualIntent.DISPLAY_LABEL}：中途不问，离开才对照",
        "短会话、临期紧急时中途不打扰",
    )
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rules.forEach { line ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .width(4.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(LogoGreen.copy(alpha = 0.65f))
                )
                Text(
                    text = line,
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.55f),
                    lineHeight = 17.sp
                )
            }
        }
    }
}

private fun previewCaption(act: PracticePreviewAct, rhythm: AwarenessPracticeRhythm): String =
    when (act) {
        PracticePreviewAct.Quiet -> "开着练习后，前几分钟只计时，不打断。"
        PracticePreviewAct.Ask ->
            "约每 ${rhythm.gapSec / 60L} 分钟一句。还在则收；偏了则准备离开。"
        PracticePreviewAct.Compare -> "点停或离开后出现。对齐即收；偏了可再细看。"
    }

@Composable
private fun RhythmOption(
    rhythm: AwarenessPracticeRhythm,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = rhythm.label,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) cs.onSurface else cs.onSurface.copy(alpha = 0.42f)
        )
        Text(
            text = rhythm.detail,
            fontSize = 11.sp,
            color = cs.onSurface.copy(alpha = if (selected) 0.5f else 0.32f),
            modifier = Modifier.padding(top = 3.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(1.5.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(
                    if (selected) cs.onSurface.copy(alpha = 0.4f)
                    else Color.Transparent
                )
        )
    }
}
