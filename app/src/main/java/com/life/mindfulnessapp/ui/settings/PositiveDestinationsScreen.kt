package com.life.mindfulnessapp.ui.settings

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.DayBorder
import com.life.mindfulnessapp.ui.theme.DayCardBg
import com.life.mindfulnessapp.ui.theme.DayDivider
import com.life.mindfulnessapp.ui.theme.DayTextPrimary
import com.life.mindfulnessapp.ui.theme.DayTextSecondary
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.NightBg
import com.life.mindfulnessapp.ui.theme.NightBorder
import com.life.mindfulnessapp.ui.theme.NightCardBg
import com.life.mindfulnessapp.ui.theme.NightDivider
import com.life.mindfulnessapp.ui.theme.NightTextPrimary
import com.life.mindfulnessapp.ui.theme.NightTextSecondary

/**
 * 「想去的地方」
 *
 * 主形态：我的去处列表（别名 / 默认 / 移除）+ 添加入口。
 * 挑选 App 为次级全屏，不把整页做成监控列表克隆。
 */
@Composable
fun PositiveDestinationsScreen(
    viewModel: PositiveDestinationsViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {}
) {
    val isDarkTheme by viewModel.isDarkTheme.collectAsState()
    val destinations by viewModel.destinations.collectAsState()
    val picking by viewModel.isPicking.collectAsState()
    val loading by viewModel.isLoading.collectAsState()

    val bgColor = if (isDarkTheme) NightBg else DayBg
    val cardColor = if (isDarkTheme) NightCardBg else DayCardBg
    val textPrimary = if (isDarkTheme) NightTextPrimary else DayTextPrimary
    val textSecondary = if (isDarkTheme) NightTextSecondary else DayTextSecondary
    val borderColor = if (isDarkTheme) NightBorder else DayBorder
    val dividerColor = if (isDarkTheme) NightDivider else DayDivider
    val accent = if (isDarkTheme) LogoGreen else Color(0xFF27AE60)

    if (picking) {
        PositiveDestinationPicker(
            viewModel = viewModel,
            bgColor = bgColor,
            cardColor = cardColor,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            borderColor = borderColor,
            dividerColor = dividerColor,
            accent = accent,
            onClose = { viewModel.closePicker() }
        )
        return
    }

    Scaffold(containerColor = bgColor) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
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
                        imageVector = Icons.Default.ArrowBackIosNew,
                        contentDescription = "返回",
                        tint = textPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Text(
                    text = "想去的地方",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = textPrimary,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }

            HorizontalDivider(
                color = dividerColor,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "离开之后，去哪儿？",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "在拦截页主动选择离开时，会轻轻递一个去处。" +
                    "可以多选；离开时最多露出 3 个。起个别名，会更像你真正想去的地方。",
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.72f),
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (loading && destinations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                }
            } else if (destinations.isEmpty()) {
                EmptyDestinationsCard(
                    cardColor = cardColor,
                    borderColor = borderColor,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    accent = accent,
                    onAdd = { viewModel.openPicker() }
                )
            } else {
                Text(
                    text = "我的去处 · ${destinations.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
                Spacer(modifier = Modifier.height(6.dp))
                destinations.forEach { item ->
                    DestinationCard(
                        item = item,
                        cardColor = cardColor,
                        borderColor = borderColor,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        accent = accent,
                        onSetDefault = { viewModel.setAsDefault(item.packageName) },
                        onAliasChange = { viewModel.setAlias(item.packageName, it) },
                        onRemove = { viewModel.remove(item.packageName) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Text(
                    text = "离开轻条最多露出 ${AppPreferences.MAX_POSITIVE_DISPLAY} 个；其余可在「更多」里管理。",
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .clickable { viewModel.openPicker() }
                    .padding(vertical = 14.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = if (destinations.isEmpty()) "添加想去的地方" else "继续添加",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier.padding(start = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun EmptyDestinationsCard(
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onAdd: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "还没有去处",
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary
        )
        Text(
            text = "选几个你真正想多花时间的 App。起个别名也很好，比如把阅读 App 叫作「晨读」。",
            fontSize = 13.sp,
            color = textSecondary.copy(alpha = 0.7f),
            lineHeight = 19.sp
        )
        Text(
            text = "去添加",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
            modifier = Modifier
                .clickable(onClick = onAdd)
                .padding(vertical = 4.dp)
        )
    }
}

@Composable
private fun DestinationCard(
    item: PositiveDestinationUi,
    cardColor: Color,
    borderColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    accent: Color,
    onSetDefault: () -> Unit,
    onAliasChange: (String?) -> Unit,
    onRemove: () -> Unit
) {
    var aliasDraft by remember(item.packageName, item.alias) {
        mutableStateOf(item.alias.orEmpty())
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor)
            .border(
                1.dp,
                if (item.isDefault) accent.copy(alpha = 0.45f) else borderColor.copy(alpha = 0.45f),
                RoundedCornerShape(16.dp)
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DestinationIcon(drawable = item.icon)
            Column(
                modifier = Modifier
                    .padding(start = 12.dp)
                    .weight(1f)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.displayLabel,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.isDefault) {
                        Icon(
                            Icons.Default.Star,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier
                                .padding(start = 6.dp)
                                .size(14.dp)
                        )
                    }
                }
                if (!item.alias.isNullOrBlank() && item.alias != item.appName) {
                    Text(
                        text = item.appName,
                        fontSize = 11.sp,
                        color = textSecondary.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Text(
                text = "移除",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.7f),
                modifier = Modifier
                    .clickable(onClick = onRemove)
                    .padding(8.dp)
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "别名",
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.65f),
                fontWeight = FontWeight.Medium
            )
            BasicTextField(
                value = aliasDraft,
                onValueChange = {
                    if (it.length <= 12) {
                        aliasDraft = it
                        onAliasChange(it.trim().ifEmpty { null })
                    }
                },
                singleLine = true,
                textStyle = TextStyle(color = textPrimary, fontSize = 14.sp),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(textPrimary.copy(alpha = 0.05f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                decorationBox = { inner ->
                    Box {
                        if (aliasDraft.isEmpty()) {
                            Text(
                                text = "例如：晨读、深工作、写日记",
                                color = textSecondary.copy(alpha = 0.4f),
                                fontSize = 14.sp
                            )
                        }
                        inner()
                    }
                }
            )
        }

        if (!item.isDefault) {
            Text(
                text = "设为离开时默认",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = accent,
                modifier = Modifier
                    .clickable(onClick = onSetDefault)
                    .padding(vertical = 2.dp)
            )
        } else {
            Text(
                text = "离开时会先露出这个",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun PositiveDestinationPicker(
    viewModel: PositiveDestinationsViewModel,
    bgColor: Color,
    cardColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    dividerColor: Color,
    accent: Color,
    onClose: () -> Unit
) {
    val apps by viewModel.pickerApps.collectAsState()
    val selected by viewModel.selectedPackages.collectAsState()
    val query by viewModel.searchQuery.collectAsState()
    val loading by viewModel.isLoading.collectAsState()

    Scaffold(containerColor = bgColor) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "完成",
                        tint = textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                    Text(
                        text = "添加想去的地方",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = textPrimary
                    )
                    Text(
                        text = "已选 ${selected.size} · 不限数量",
                        fontSize = 12.sp,
                        color = textSecondary.copy(alpha = 0.65f)
                    )
                }
                Text(
                    text = "完成",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                    modifier = Modifier
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }

            HorizontalDivider(
                color = dividerColor,
                thickness = 0.5.dp,
                modifier = Modifier.padding(horizontal = 20.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(cardColor)
                    .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = textSecondary.copy(alpha = 0.6f),
                    modifier = Modifier.size(18.dp)
                )
                Box(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            text = "搜索 App",
                            color = textSecondary.copy(alpha = 0.45f),
                            fontSize = 14.sp
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = viewModel::setSearchQuery,
                        singleLine = true,
                        textStyle = TextStyle(color = textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            if (loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accent, strokeWidth = 2.dp)
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(apps, key = { it.packageName }) { app ->
                        PickerAppRow(
                            app = app,
                            selected = app.packageName in selected,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary,
                            cardColor = cardColor,
                            accent = accent,
                            onClick = { viewModel.toggle(app.packageName) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }
}

@Composable
private fun PickerAppRow(
    app: AppInfo,
    selected: Boolean,
    textPrimary: Color,
    textSecondary: Color,
    cardColor: Color,
    accent: Color,
    onClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) accent.copy(alpha = 0.10f)
                else cardColor.copy(alpha = 0.55f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        DestinationIcon(drawable = app.icon)
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .weight(1f)
        ) {
            Text(
                text = app.appName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (app.isMonitored) {
                Text(
                    text = "已在监控列表",
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.55f)
                )
            }
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) accent else textPrimary.copy(alpha = 0.08f))
        ) {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}

@Composable
private fun DestinationIcon(drawable: Drawable?) {
    val bitmap = remember(drawable) {
        try {
            drawable?.toBitmap(96, 96)?.asImageBitmap()
        } catch (_: Exception) {
            null
        }
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
        )
    } else {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF3A3A3A))
        )
    }
}
