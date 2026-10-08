package com.life.mindfulnessapp.ui.applist

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
import com.life.mindfulnessapp.domain.model.CommonIntentItem
import com.life.mindfulnessapp.domain.model.CommonIntentsCodec
import com.life.mindfulnessapp.domain.model.IntentGateProfiles
import com.life.mindfulnessapp.ui.theme.LogoGreen
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

/**
 * 快捷标签管理：写意图门上的可点词。
 * 按 App 私有；最多门上 [CommonIntentsCodec.MAX_GATE_VISIBLE] 个。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickIntentTagsScreen(
    packageName: String,
    appName: String,
    onNavigateBack: () -> Unit,
    viewModel: QuickIntentTagsViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val cs = MaterialTheme.colorScheme
    val context = LocalContext.current
    var confirmRestore by remember { mutableStateOf(false) }

    LaunchedEffect(packageName, appName) {
        viewModel.load(packageName, appName)
    }
    LaunchedEffect(state.toast) {
        val msg = state.toast ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.consumeToast()
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            title = { Text("恢复预设？") },
            text = { Text("将覆盖你现在的快捷标签，恢复为该 App 的默认词。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRestore = false
                        viewModel.restorePresets()
                    }
                ) { Text("恢复", color = LogoGreen) }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = if (state.showingAdd) "添加标签" else "快捷标签",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp
                        )
                        if (!state.showingAdd && state.appName.isNotBlank()) {
                            Text(
                                text = "${state.appName} · 写意图时点一下就填入",
                                fontSize = 12.sp,
                                color = cs.onSurface.copy(alpha = 0.42f)
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (state.showingAdd) viewModel.closeAdd() else onNavigateBack()
                        }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        if (state.showingAdd) {
            QuickIntentTagsAddPane(
                state = state,
                onAddCustom = viewModel::addCustom,
                onAddPreset = viewModel::addPreset,
                onDone = viewModel::closeAdd,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
            )
        } else {
            QuickIntentTagsMinePane(
                state = state,
                onCommitOrder = viewModel::commitOrder,
                onToggle = viewModel::setOnGate,
                onRemove = viewModel::removeAt,
                onAdd = viewModel::openAdd,
                onRestore = { confirmRestore = true },
                onDone = onNavigateBack,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .navigationBarsPadding()
            )
        }
    }
}

@Composable
private fun QuickIntentTagsMinePane(
    state: QuickIntentTagsUiState,
    onCommitOrder: (List<CommonIntentItem>) -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onAdd: () -> Unit,
    onRestore: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var list by remember { mutableStateOf(state.items) }
    var isDragging by remember { mutableStateOf(false) }
    LaunchedEffect(state.items) {
        if (!isDragging) list = state.items
    }
    val listState = rememberLazyListState()
    val reorderable = rememberReorderableLazyListState(listState) { from, to ->
        list = list.toMutableList().apply {
            add(to.index, removeAt(from.index))
        }
    }

    Column(modifier = modifier) {
        Text(
            text = "已启用 ${state.onGateCount}/${CommonIntentsCodec.MAX_GATE_VISIBLE}",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.42f),
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp)
        ) {
            if (list.isEmpty()) {
                item {
                    Text(
                        text = "还没有标签",
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.5f),
                        lineHeight = 22.sp,
                        modifier = Modifier.padding(top = 12.dp, bottom = 24.dp)
                    )
                }
            }
            items(list, key = { it.label.lowercase() }) { item ->
                val index = list.indexOfFirst {
                    it.label.equals(item.label, ignoreCase = true)
                }
                ReorderableItem(reorderable, key = item.label.lowercase()) {
                    val handleInteraction = remember { MutableInteractionSource() }
                    QuickTagRow(
                        item = item,
                        packageName = state.packageName,
                        onToggle = { on -> if (index >= 0) onToggle(index, on) },
                        onRemove = { if (index >= 0) onRemove(index) },
                        dragHandleModifier = Modifier.draggableHandle(
                            interactionSource = handleInteraction,
                            onDragStarted = { isDragging = true },
                            onDragStopped = {
                                isDragging = false
                                onCommitOrder(list)
                            }
                        )
                    )
                }
            }
        }
        Text(
            text = if (state.canAddMore) "＋ 添加标签" else "已达上限",
            fontSize = 15.sp,
            color = if (state.canAddMore) LogoGreen else cs.onSurface.copy(alpha = 0.35f),
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .clickable(enabled = state.canAddMore, role = Role.Button, onClick = onAdd)
        )
        HorizontalDivider(color = cs.outline.copy(alpha = 0.12f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "恢复预设",
                fontSize = 15.sp,
                color = cs.onSurface.copy(alpha = 0.45f),
                modifier = Modifier.clickable(role = Role.Button, onClick = onRestore)
            )
            Text(
                text = "完成",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = LogoGreen,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDone)
            )
        }
    }
}

@Composable
private fun QuickTagRow(
    item: CommonIntentItem,
    packageName: String,
    onToggle: (Boolean) -> Unit,
    onRemove: () -> Unit,
    dragHandleModifier: Modifier
) {
    val cs = MaterialTheme.colorScheme
    val preset = IntentGateProfiles.isPresetLabel(packageName, item.label)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.DragHandle,
            contentDescription = "拖动排序",
            tint = cs.onSurface.copy(alpha = 0.28f),
            modifier = dragHandleModifier.size(22.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.label,
                fontSize = 15.sp,
                color = cs.onSurface.copy(alpha = if (item.showOnGate) 0.92f else 0.45f)
            )
            val caption = AppDeepLinkCatalog.tagManageCaption(
                packageName,
                item,
                if (preset) "预设" else "自制"
            )
            val canLand = AppDeepLinkCatalog.resolveForCommonIntent(packageName, item) != null
            Text(
                text = caption,
                fontSize = 12.sp,
                color = if (canLand) LogoGreen.copy(alpha = 0.9f) else cs.onSurface.copy(alpha = 0.38f),
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        if (!preset) {
            Text(
                text = "删除",
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.4f),
                modifier = Modifier
                    .clickable(role = Role.Button, onClick = onRemove)
                    .padding(horizontal = 4.dp, vertical = 4.dp)
            )
        }
        Text(
            text = if (item.showOnGate) "开" else "关",
            fontSize = 13.sp,
            color = if (item.showOnGate) LogoGreen else cs.onSurface.copy(alpha = 0.4f),
            modifier = Modifier
                .clickable(role = Role.Button) { onToggle(!item.showOnGate) }
                .padding(horizontal = 4.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun QuickIntentTagsAddPane(
    state: QuickIntentTagsUiState,
    onAddCustom: (String) -> Unit,
    onAddPreset: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val cs = MaterialTheme.colorScheme
    var input by remember { mutableStateOf("") }
    val left = (CommonIntentsCodec.MAX_ITEMS - state.items.size).coerceAtLeast(0)

    Column(modifier = modifier.padding(horizontal = 20.dp)) {
        Text(
            text = if (left > 0) "最多再加 $left 个" else "已达上限",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.42f),
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp)
        )
        Text(
            text = "手写",
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = cs.onSurface.copy(alpha = 0.4f)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = {
                    if (it.length <= CommonIntentsCodec.MAX_LABEL) input = it
                },
                singleLine = true,
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = cs.onSurface
                ),
                cursorBrush = SolidColor(LogoGreen),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onAddCustom(input)
                        input = ""
                    }
                ),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (input.isEmpty()) {
                            Text(
                                "例如：看数据后台",
                                fontSize = 15.sp,
                                color = cs.onSurface.copy(alpha = 0.35f)
                            )
                        }
                        inner()
                    }
                }
            )
            Text(
                text = "加入",
                fontSize = 14.sp,
                color = if (input.trim().isNotEmpty() && left > 0) {
                    LogoGreen
                } else {
                    cs.onSurface.copy(alpha = 0.3f)
                },
                modifier = Modifier
                    .clickable(
                        enabled = input.trim().isNotEmpty() && left > 0,
                        role = Role.Button
                    ) {
                        onAddCustom(input)
                        input = ""
                    }
                    .padding(start = 12.dp, top = 4.dp, bottom = 4.dp)
            )
        }
        HorizontalDivider(color = cs.outline.copy(alpha = 0.18f))

        if (state.presetPool.isNotEmpty()) {
            Text(
                text = "来自预设",
                fontSize = 11.sp,
                letterSpacing = 0.8.sp,
                color = cs.onSurface.copy(alpha = 0.4f),
                modifier = Modifier.padding(top = 22.dp, bottom = 4.dp)
            )
            state.presetPool.forEach { label ->
                val canLand = AppDeepLinkCatalog.findByLabel(state.packageName, label) != null ||
                    AppDeepLinkCatalog.resolveFromPurpose(state.packageName, label) != null
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = left > 0, role = Role.Button) {
                            onAddPreset(label)
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(label, fontSize = 15.sp, color = cs.onSurface.copy(alpha = 0.9f))
                        if (canLand) {
                            Text(
                                text = "可直达",
                                fontSize = 12.sp,
                                color = LogoGreen.copy(alpha = 0.9f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = if (left > 0) "选取" else "已满",
                        fontSize = 13.sp,
                        color = if (left > 0) LogoGreen else cs.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
        }

        Spacer(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "完成",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = LogoGreen,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDone)
            )
        }
    }
}
