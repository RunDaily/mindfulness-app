package com.life.mindfulnessapp.ui.settings

import android.widget.Toast
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.ui.theme.MindfulGreen40
import com.life.mindfulnessapp.ui.theme.themeChrome

/**
 * 设置页：只保留用户会主动改的偏好。
 * 监控服务与加强保活默认常开，不在此暴露。
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateToTheme: () -> Unit = {},
    onNavigateToPlayground: () -> Unit = {},
    onNavigateToSearchDeepLinkTest: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val capsuleUsedShowSeconds by viewModel.capsuleUsedShowSeconds.collectAsState()
    val desktopAnchorEnabled by viewModel.desktopAnchorEnabled.collectAsState()
    val capsuleMiniSize by viewModel.capsuleMiniSize.collectAsState()
    val awayCountdownSeconds by viewModel.awayCountdownSeconds.collectAsState()
    val themePack by viewModel.themePack.collectAsState()
    val themeFollowSystem by viewModel.themeFollowSystem.collectAsState()
    val chrome = themeChrome()
    val isDarkTheme = chrome.isDark

    val bgColor = chrome.bg
    val cardColor = chrome.card
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val borderColor = chrome.border
    val accentGreen = chrome.accent

    var showClearDataDialog by remember { mutableStateOf(false) }
    val isClearingData by viewModel.isClearingData.collectAsState()

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textSecondary
                    )
                }
                Text(
                    text = "设置",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                    modifier = Modifier.padding(start = 2.dp)
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 40.dp)
        ) {
            val sectionLab = Modifier.padding(top = 18.dp, bottom = 2.dp)
            SettingsSectionLabel("通用", textSecondary, sectionLab)
            SettingsGroup(cardColor = cardColor, borderColor = borderColor) {
                SettingsNavRow(
                    title = "外观",
                    subtitle = "",
                    trailing = when {
                        themeFollowSystem && themePack.allowsFollowSystem -> "跟随系统"
                        else -> themePack.title
                    },
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = onNavigateToTheme
                )
                GroupDivider(borderColor)
            }

            SettingsSectionLabel("胶囊", textSecondary, sectionLab)
            SettingsGroup(cardColor = cardColor, borderColor = borderColor) {
                SettingsSwitchRow(
                    title = "桌面心锚",
                    subtitle = if (desktopAnchorEnabled) {
                        "监测开启时桌面微粒 · 点开今日快板"
                    } else {
                        "已关闭 · 会话胶囊不受影响"
                    },
                    checked = desktopAnchorEnabled,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    isDark = isDarkTheme,
                    onCheckedChange = viewModel::setDesktopAnchorEnabled
                )
                GroupDivider(borderColor)
                SettingsStringSegmentRow(
                    title = "悬浮圆球大小",
                    options = listOf(
                        AppPreferences.CAPSULE_MINI_SIZE_STANDARD to "标准",
                        AppPreferences.CAPSULE_MINI_SIZE_COMPACT to "紧凑"
                    ),
                    selected = capsuleMiniSize,
                    accentGreen = accentGreen,
                    textPrimary = textPrimary,
                    onSelect = viewModel::setCapsuleMiniSize
                )
                GroupDivider(borderColor)
                SettingsSwitchRow(
                    title = "已用显示到秒",
                    subtitle = if (capsuleUsedShowSeconds) "如 12:34/60分" else "如 12/60分",
                    checked = capsuleUsedShowSeconds,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    isDark = isDarkTheme,
                    onCheckedChange = viewModel::setCapsuleUsedShowSeconds
                )
                GroupDivider(borderColor)
                SettingsIntSegmentRow(
                    title = "暂停等待时长",
                    options = listOf(60 to "1 分", 120 to "2 分", 300 to "5 分"),
                    selected = awayCountdownSeconds,
                    accentGreen = accentGreen,
                    textPrimary = textPrimary,
                    onSelect = viewModel::setAwayCountdownSeconds
                )
                GroupDivider(borderColor)
            }

            SettingsSectionLabel("试验", textSecondary, sectionLab)
            SettingsGroup(cardColor = cardColor, borderColor = borderColor) {
                SettingsNavRow(
                    title = "试玩",
                    subtitle = "壁纸、格言",
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = onNavigateToPlayground
                )
                GroupDivider(borderColor)
            }

            SettingsSectionLabel("数据", textSecondary, sectionLab)
            SettingsGroup(cardColor = cardColor, borderColor = borderColor) {
                SettingsNavRow(
                    title = "清除本地数据",
                    subtitle = "删除使用记录，限额保留",
                    titleColor = Color(0xFFC47A6A),
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { showClearDataDialog = true }
                )
                GroupDivider(borderColor)
            }
        }
    }

    if (showClearDataDialog) {
        ClearDataDialog(
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            borderColor = borderColor,
            isClearing = isClearingData,
            onDismiss = { showClearDataDialog = false },
            onConfirm = {
                viewModel.clearLocalUsageData {
                    showClearDataDialog = false
                    Toast.makeText(context, "本地数据已清除", Toast.LENGTH_SHORT).show()
                }
            }
        )
    }
}

@Composable
internal fun SettingsSectionLabel(
    title: String,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    Text(
        text = title,
        fontSize = 10.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.1.sp,
        color = textSecondary.copy(alpha = 0.55f),
        modifier = modifier.padding(start = 20.dp, top = 18.dp, end = 20.dp, bottom = 2.dp)
    )
}

/** 脊线容器：不加卡片描边；[cardColor]/[borderColor] 保留参数以兼容旧调用。 */
@Composable
internal fun SettingsGroup(
    cardColor: Color,
    borderColor: Color,
    content: @Composable () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        content()
    }
}

@Composable
internal fun GroupDivider(borderColor: Color) {
    HorizontalDivider(color = borderColor.copy(alpha = 0.35f))
}

@Composable
private fun SettingsStringSegmentRow(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    accentGreen: Color,
    textPrimary: Color,
    onSelect: (String) -> Unit
) {
    SettingsSegmentRowBase(
        title = title,
        labels = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        accentGreen = accentGreen,
        textPrimary = textPrimary,
        onSelectIndex = { onSelect(options[it].first) }
    )
}

@Composable
private fun SettingsIntSegmentRow(
    title: String,
    options: List<Pair<Int, String>>,
    selected: Int,
    accentGreen: Color,
    textPrimary: Color,
    onSelect: (Int) -> Unit
) {
    SettingsSegmentRowBase(
        title = title,
        labels = options.map { it.second },
        selectedIndex = options.indexOfFirst { it.first == selected }.coerceAtLeast(0),
        accentGreen = accentGreen,
        textPrimary = textPrimary,
        onSelectIndex = { onSelect(options[it].first) }
    )
}

@Composable
private fun SettingsSegmentRowBase(
    title: String,
    labels: List<String>,
    selectedIndex: Int,
    accentGreen: Color,
    textPrimary: Color,
    onSelectIndex: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textPrimary,
            modifier = Modifier.padding(end = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            labels.forEachIndexed { index, label ->
                val isSelected = index == selectedIndex
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) textPrimary else textPrimary.copy(alpha = 0.38f),
                    modifier = Modifier.clickable { onSelectIndex(index) }
                )
            }
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    isDark: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = textPrimary)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.5f),
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        ThemedSwitch(checked = checked, onCheckedChange = onCheckedChange, isDark = isDark)
    }
}

@Composable
internal fun SettingsNavRow(
    icon: ImageVector? = null,
    iconTint: Color = Color.Transparent,
    title: String,
    subtitle: String,
    textPrimary: Color,
    textSecondary: Color,
    titleColor: Color = textPrimary,
    trailing: String = "",
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = titleColor)
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.5f),
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
        }
        if (trailing.isNotBlank()) {
            Text(
                trailing,
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
internal fun ThemedSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isDark: Boolean,
    enabled: Boolean = true
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = MindfulGreen40,
            uncheckedThumbColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color(0xFF9E9E9E),
            uncheckedTrackColor = if (isDark) Color(0xFF2A3347) else Color(0xFFDDDDDD),
            disabledUncheckedThumbColor = if (isDark) Color.White.copy(alpha = 0.28f) else Color(0xFFBDBDBD),
            disabledUncheckedTrackColor = if (isDark) Color(0xFF222A3A) else Color(0xFFE8E8E8)
        )
    )
}

@Composable
internal fun ClearDataDialog(
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    isClearing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    val dangerColor = Color(0xFFE74C3C)

    Dialog(onDismissRequest = { if (!isClearing) onDismiss() }) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(cardColor)
                .padding(24.dp)
        ) {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(dangerColor.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DeleteSweep,
                            contentDescription = null,
                            tint = dangerColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text("清除本地数据", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = textPrimary)
                        Text("此操作不可撤销", fontSize = 12.sp, color = dangerColor.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(dangerColor.copy(alpha = 0.06f))
                        .border(1.dp, dangerColor.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("将会清除：", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = textPrimary)
                        listOf("全部本地使用记录（包含历史统计）", "清除后无法找回").forEach { hint ->
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(dangerColor.copy(alpha = 0.6f))
                                )
                                Text(
                                    hint,
                                    fontSize = 12.sp,
                                    color = textPrimary.copy(alpha = 0.65f),
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "限额设置不受影响。",
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.45f),
                    lineHeight = 16.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        enabled = !isClearing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = textPrimary.copy(alpha = 0.6f)
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            borderColor.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) { Text("取消", fontSize = 14.sp) }

                    Button(
                        onClick = onConfirm,
                        enabled = !isClearing,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = dangerColor,
                            contentColor = Color.White,
                            disabledContainerColor = dangerColor.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isClearing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        Text(
                            if (isClearing) "清除中..." else "确认清除",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}
