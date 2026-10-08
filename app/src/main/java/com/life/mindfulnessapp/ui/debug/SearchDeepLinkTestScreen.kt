package com.life.mindfulnessapp.ui.debug

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.deeplink.InstalledSearchDeepLink
import com.life.mindfulnessapp.data.deeplink.SearchDeepLinkCatalog
import com.life.mindfulnessapp.data.deeplink.SearchDeepLinkLauncher
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
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
 * Debug：本机已安装、且在搜索深链白名单内的 App，可一键试跳。
 */
@Composable
fun SearchDeepLinkTestScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val chrome = themeChrome()
    val isDarkTheme = chrome.isDark
    val bgColor = chrome.bg
    val cardColor = chrome.card
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val borderColor = chrome.border
    val accent = if (isDarkTheme) LogoGreen else Color(0xFF27AE60)

    var query by remember { mutableStateOf("露营") }
    var refreshTick by remember { mutableIntStateOf(0) }
    val installed = remember(refreshTick) {
        SearchDeepLinkLauncher.listInstalled(context)
    }
    val grouped = remember(installed) {
        SearchDeepLinkCatalog.categoryOrder.mapNotNull { cat ->
            val rows = installed.filter { it.entry.category == cat }
            if (rows.isEmpty()) null else cat to rows
        }
    }
    val catalogPackageCount = remember {
        SearchDeepLinkCatalog.all.map { it.packageName }.distinct().size
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = "搜索深链测试",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Text(
                        text = "本机命中 ${installed.size} 条 · 目录 ${SearchDeepLinkCatalog.all.size} / $catalogPackageCount 包",
                        fontSize = 12.sp,
                        color = textSecondary.copy(alpha = 0.7f)
                    )
                }
                TextButton(onClick = { refreshTick++ }) {
                    Text("刷新", color = accent, fontSize = 14.sp)
                }
            }
        }
    ) { inner ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Text(
                    text = "测试关键词",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary.copy(alpha = 0.55f),
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardColor)
                        .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = textPrimary,
                            fontSize = 16.sp
                        ),
                        cursorBrush = SolidColor(accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { focusManager.clearFocus() }
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { innerField ->
                            if (query.isEmpty()) {
                                Text(
                                    "输入关键词，如：露营",
                                    color = textSecondary.copy(alpha = 0.35f),
                                    fontSize = 16.sp
                                )
                            }
                            innerField()
                        }
                    )
                }
                Text(
                    text = "仅列出本机已安装且在白名单内的 App。深链非官方，失败属正常。",
                    fontSize = 12.sp,
                    color = textSecondary.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
            }

            if (grouped.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(cardColor)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "本机未安装目录中的任何 App",
                            color = textSecondary,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            grouped.forEach { (category, rows) ->
                item {
                    Text(
                        text = category,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = textSecondary.copy(alpha = 0.55f),
                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                    )
                }
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(cardColor)
                            .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                    ) {
                        rows.forEachIndexed { index, row ->
                            DeepLinkTestRow(
                                row = row,
                                query = query,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary,
                                accent = accent,
                                onTest = {
                                    focusManager.clearFocus()
                                    val err = SearchDeepLinkLauncher.launch(
                                        context,
                                        row.entry,
                                        query
                                    )
                                    Toast.makeText(
                                        context,
                                        err ?: "已发起：${row.entry.displayName}",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            )
                            if (index < rows.lastIndex) {
                                HorizontalDivider(
                                    color = borderColor.copy(alpha = 0.35f),
                                    modifier = Modifier.padding(start = 66.dp)
                                )
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun DeepLinkTestRow(
    row: InstalledSearchDeepLink,
    query: String,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onTest: () -> Unit
) {
    val context = LocalContext.current
    val uriPreview = remember(row.entry.id, query) {
        SearchDeepLinkLauncher.buildUriString(row.entry, query.ifBlank { "测试" })
    }
    val resolvable = remember(row.entry.id, query) {
        SearchDeepLinkLauncher.canResolve(context, row.entry, query.ifBlank { "测试" })
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(drawable = row.icon, modifier = Modifier.size(40.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.entry.displayName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (!row.entry.supportsKeyword) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "无关键词",
                        fontSize = 10.sp,
                        color = textSecondary.copy(alpha = 0.65f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(textSecondary.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
                if (!resolvable) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "可能失效",
                        fontSize = 10.sp,
                        color = Color(0xFFE67E22),
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFE67E22).copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            Text(
                text = row.entry.packageName,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = uriPreview,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = textSecondary.copy(alpha = 0.7f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
            row.entry.note?.let { note ->
                Text(
                    text = note,
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        TextButton(
            onClick = onTest,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text("测试", color = accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}
