package com.life.mindfulnessapp.ui.applist

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MindfulGreen40
import com.life.mindfulnessapp.ui.vip.VipUpgradeDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAddLimit: (packageName: String) -> Unit,
    onNavigateToVip: () -> Unit = {}
) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val focusManager = LocalFocusManager.current
    val isAtFreeLimit by viewModel.isAtFreeLimit.collectAsState()
    val showVipUpgradeDialog by viewModel.showVipUpgradeDialog.collectAsState()
    val vipLevel by viewModel.vipLevel.collectAsState()
    val monitoredCount by viewModel.monitoredCount.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadApps()
    }

    // 挑选器：只展示尚未监控的应用
    val candidates = remember(apps) { apps.filter { !it.isMonitored } }
    val cs = MaterialTheme.colorScheme
    val isDark = cs.background.red < 0.5f

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "添加监控应用",
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onSurface,
                            fontSize = 17.sp
                        )
                        if (vipLevel <= 0) {
                            Text(
                                "已监控 $monitoredCount / ${AppPreferences.FREE_MONITOR_LIMIT}（免费版）",
                                fontSize = 11.sp,
                                color = if (isAtFreeLimit) Color(0xFFE8941A)
                                        else cs.onSurface.copy(alpha = 0.40f)
                            )
                        } else if (monitoredCount > 0) {
                            Text(
                                "已监控 $monitoredCount 个",
                                fontSize = 11.sp,
                                color = cs.onSurface.copy(alpha = 0.40f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = cs.onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                placeholder = {
                    Text("搜索应用名称", color = cs.onBackground.copy(alpha = 0.28f), fontSize = 14.sp)
                },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = cs.onBackground.copy(alpha = 0.28f))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }) {
                            Icon(Icons.Default.Close, contentDescription = "清除", tint = cs.onBackground.copy(alpha = 0.5f))
                        }
                    }
                },
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = LogoGreen,
                    unfocusedBorderColor = cs.outline.copy(alpha = 0.4f),
                    focusedContainerColor = cs.surface,
                    unfocusedContainerColor = cs.surface,
                    focusedTextColor = cs.onSurface,
                    unfocusedTextColor = cs.onSurface
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() })
            )

            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MindfulGreen40, strokeWidth = 2.dp)
                }
            } else if (candidates.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "没有找到匹配的应用"
                        else "可添加的应用都已在监控中",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.38f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                    item {
                        if (monitoredCount == 0) {
                            Text(
                                text = "先选一个常刷的 App。下一步可为它开启意图门、时长锁或时段锁。",
                                fontSize = 13.sp,
                                color = cs.onSurface.copy(alpha = 0.48f),
                                lineHeight = 19.sp,
                                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp)
                            )
                        }
                        Text(
                            text = "选择要监控的应用",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = cs.onBackground.copy(alpha = 0.35f),
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            letterSpacing = 0.8.sp
                        )
                    }
                    items(candidates, key = { it.packageName }) { app ->
                        AddAppListItem(
                            appInfo = app,
                            cs = cs,
                            onAdd = { onNavigateToAddLimit(app.packageName) }
                        )
                    }
                }
            }
        }
    }

    if (showVipUpgradeDialog) {
        VipUpgradeDialog(
            isDarkTheme = isDark,
            cardColor = cs.surface,
            textPrimary = cs.onSurface,
            textSecondary = cs.onSurfaceVariant,
            borderColor = cs.outline,
            accentGreen = LogoGreen,
            onDismiss = { viewModel.dismissVipUpgradeDialog() },
            onUpgrade = {
                viewModel.dismissVipUpgradeDialog()
                onNavigateToVip()
            }
        )
    }
}

@Composable
private fun AddAppListItem(
    appInfo: AppInfo,
    cs: ColorScheme,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cs.surface)
            .clickable(onClick = onAdd)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        AppIcon(drawable = appInfo.icon, modifier = Modifier.size(44.dp))
        Text(
            text = appInfo.appName,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface,
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(LogoGreen.copy(alpha = 0.12f))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                "添加",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = LogoGreen
            )
        }
    }
}

@Composable
fun AppIcon(drawable: Drawable?, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    if (drawable != null) {
        val bitmap = remember(drawable) {
            runCatching { drawable.toBitmap(96, 96) }.getOrNull()
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = modifier.clip(RoundedCornerShape(12.dp))
            )
            return
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Android, contentDescription = null, tint = cs.onSurface.copy(alpha = 0.22f))
    }
}
