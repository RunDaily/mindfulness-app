package com.life.mindfulnessapp.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.widget.Toast
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.CachedAuthor
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
import kotlinx.coroutines.launch

/**
 * 名人目录：只展示名人与订阅状态，不展示名言全文列表。
 */
@Composable
fun AuthorCatalogScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val chrome = themeChrome()
    val isDarkTheme = chrome.isDark
    val authors by viewModel.authorCatalog.collectAsState()

    val bgColor = chrome.bg
    val cardColor = chrome.card
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val borderColor = chrome.border
    val accentGreen = chrome.accent

    var tabSubscribedOnly by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var showRequestDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refreshAuthorCatalog()
    }

    val categories = remember(authors) {
        authors.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
    }
    val visible = remember(authors, tabSubscribedOnly, selectedCategory) {
        authors.filter { a ->
            (!tabSubscribedOnly || a.subscribed) &&
                (selectedCategory == null || a.category == selectedCategory)
        }
    }
    val grouped = remember(visible) {
        visible.groupBy { it.category.ifBlank { "其他" } }
            .toList()
            .sortedBy { it.first }
    }

    Scaffold(containerColor = bgColor) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textPrimary
                    )
                }
                Text(
                    text = "名人目录",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    modifier = Modifier.weight(1f).padding(start = 4.dp)
                )
                IconButton(onClick = { showRequestDialog = true }) {
                    Icon(
                        imageVector = Icons.Default.PersonAdd,
                        contentDescription = "求名人",
                        tint = accentGreen
                    )
                }
            }

            Text(
                text = when {
                    authors.none { it.subscribed } -> "订阅后优先用他们的句子；未订阅用精选池"
                    else -> "已订阅优先；池空时用精选补充"
                },
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                lineHeight = 18.sp
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = !tabSubscribedOnly,
                    onClick = { tabSubscribedOnly = false },
                    label = { Text("全部") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentGreen.copy(alpha = 0.18f),
                        selectedLabelColor = accentGreen
                    )
                )
                FilterChip(
                    selected = tabSubscribedOnly,
                    onClick = { tabSubscribedOnly = true },
                    label = { Text("已订阅 ${authors.count { it.subscribed }}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = accentGreen.copy(alpha = 0.18f),
                        selectedLabelColor = accentGreen
                    )
                )
            }

            if (categories.isNotEmpty() && !tabSubscribedOnly) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = selectedCategory == null,
                        onClick = { selectedCategory = null },
                        label = { Text("全部分类") },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accentGreen.copy(alpha = 0.18f),
                            selectedLabelColor = accentGreen
                        )
                    )
                    categories.take(6).forEach { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = {
                                selectedCategory = if (selectedCategory == cat) null else cat
                            },
                            label = { Text(cat) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = accentGreen.copy(alpha = 0.18f),
                                selectedLabelColor = accentGreen
                            )
                        )
                    }
                }
            }

            if (grouped.isEmpty()) {
                Text(
                    text = if (tabSubscribedOnly) "还没有订阅" else "暂无名人，下拉刷新或稍后再试",
                    fontSize = 14.sp,
                    color = textSecondary.copy(alpha = 0.55f),
                    modifier = Modifier.padding(20.dp)
                )
            } else {
                grouped.forEach { (category, list) ->
                    SettingsSectionLabel(category, textSecondary)
                    SettingsGroup(cardColor = cardColor, borderColor = borderColor) {
                        list.forEachIndexed { index, author ->
                            if (index > 0) GroupDivider(borderColor)
                            AuthorRow(
                                author = author,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                accentGreen = accentGreen,
                                onToggle = {
                                    scope.launch {
                                        val now = viewModel.toggleAuthorSubscription(author.id)
                                        Toast.makeText(
                                            context,
                                            if (now) "已订阅 ${author.name}，将优先展示" else "已取消订阅",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    }

    if (showRequestDialog) {
        RequestAuthorDialog(
            isDark = isDarkTheme,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            accentGreen = accentGreen,
            onDismiss = { showRequestDialog = false },
            onConfirm = { name, note ->
                scope.launch {
                    val result = viewModel.requestAuthor(name, note)
                    showRequestDialog = false
                    val msg = when {
                        !result.success -> result.error ?: "提交失败"
                        result.already_exists -> result.message ?: "该名人已在目录，可直接订阅"
                        result.already_requested -> "你已求过这位，我们会尽快处理"
                        else -> "已提交，上线后会通知你"
                    }
                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    if (result.already_exists && (result.author_id ?: 0) > 0) {
                        viewModel.refreshAuthorCatalog()
                    }
                }
            }
        )
    }
}

@Composable
private fun AuthorRow(
    author: CachedAuthor,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = author.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
            if (author.bio.isNotBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = author.bio,
                    fontSize = 12.sp,
                    color = textSecondary.copy(alpha = 0.7f),
                    lineHeight = 17.sp
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${author.quoteCount} 句",
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.45f)
            )
        }
        TextButton(onClick = onToggle) {
            Text(
                text = if (author.subscribed) "已订阅" else "订阅",
                color = if (author.subscribed) accentGreen else textPrimary.copy(alpha = 0.7f),
                fontWeight = if (author.subscribed) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
private fun RequestAuthorDialog(
    isDark: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    accentGreen: Color,
    onDismiss: () -> Unit,
    onConfirm: (name: String, note: String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    val canSave = name.trim().isNotEmpty()
    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = accentGreen,
        unfocusedBorderColor = textSecondary.copy(alpha = 0.25f),
        focusedTextColor = textPrimary,
        unfocusedTextColor = textPrimary,
        cursorColor = accentGreen,
        focusedLabelColor = accentGreen,
        unfocusedLabelColor = textSecondary
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = if (isDark) NightCardBg else DayCardBg,
        title = {
            Text("求收录名人", fontWeight = FontWeight.SemiBold, color = textPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "目录里没有你喜欢的人？告诉我们，上线后会提醒你。",
                    fontSize = 13.sp,
                    color = textSecondary.copy(alpha = 0.8f),
                    lineHeight = 18.sp
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("姓名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors
                )
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 120) note = it },
                    label = { Text("备注（可选）") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = fieldColors
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, note) },
                enabled = canSave
            ) {
                Text("提交", color = if (canSave) accentGreen else textSecondary.copy(alpha = 0.4f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = textSecondary)
            }
        }
    )
}
