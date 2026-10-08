package com.life.mindfulnessapp.ui.applist

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.IntentCategory
import com.life.mindfulnessapp.domain.model.IntentPoolItem
import com.life.mindfulnessapp.ui.theme.LogoGreen
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IntentPoolManageScreen(
    packageName: String,
    onNavigateBack: () -> Unit,
    viewModel: IntentPoolViewModel = hiltViewModel()
) {
    LaunchedEffect(packageName) {
        viewModel.load(packageName)
    }

    val snapshot by viewModel.snapshot.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    var showAddCategory by remember { mutableStateOf(false) }
    var categoryToDelete by remember { mutableStateOf<IntentCategory?>(null) }
    var mergeSource by remember { mutableStateOf<IntentPoolItem?>(null) }

    LaunchedEffect(actionMessage) {
        val msg = actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.consumeActionMessage()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("整理意图池") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        containerColor = cs.background
    ) { padding ->
        val data = snapshot
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    text = "分类",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface.copy(alpha = 0.40f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "给意图分组，更容易看清这个 App 在你生活里的角色。",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.45f),
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val categories = data?.categoryBreakdown?.mapNotNull { it.category }.orEmpty()
            if (categories.isEmpty()) {
                item {
                    ManageTemplateCard(
                        onApplyTemplate = { viewModel.applyCategoryTemplate() }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            } else {
                items(categories, key = { it.id }) { cat ->
                    CategoryManageRow(
                        category = cat,
                        entryCount = data?.items?.count { it.category?.id == cat.id } ?: 0,
                        onDelete = { categoryToDelete = cat }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            item {
                TextButton(onClick = { showAddCategory = true }) {
                    Icon(Icons.Outlined.Add, contentDescription = null, tint = LogoGreen)
                    Text("添加分类", color = LogoGreen, modifier = Modifier.padding(start = 4.dp))
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "合并意图",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = cs.onSurface.copy(alpha = 0.40f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "把说法不同但意思相近的意图合在一起统计。原始记录不会改动。",
                    fontSize = 13.sp,
                    color = cs.onSurface.copy(alpha = 0.45f),
                    lineHeight = 19.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            val mergeCandidates = data?.items.orEmpty().filter { it.aliases.size <= 2 }
            items(mergeCandidates.take(12), key = { it.entry.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(cs.surface)
                        .clickable { mergeSource = item }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.entry.displayName,
                        fontSize = 15.sp,
                        color = cs.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "合并 ›",
                        fontSize = 13.sp,
                        color = LogoGreen.copy(alpha = 0.85f)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
            }

            if ((data?.hiddenCount ?: 0) > 0) {
                item {
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "已隐藏 ${data?.hiddenCount} 条",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.42f)
                    )
                }
            }
        }
    }

    if (showAddCategory) {
        AddCategoryDialog(
            onDismiss = { showAddCategory = false },
            onConfirm = { name, emoji ->
                showAddCategory = false
                viewModel.createCategory(name, emoji)
            }
        )
    }

    categoryToDelete?.let { cat ->
        AlertDialog(
            onDismissRequest = { categoryToDelete = null },
            title = { Text("删除分类「${cat.name}」？") },
            text = { Text("该分类下的意图将变为未分类，不会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteCategory(cat.id)
                    categoryToDelete = null
                }) { Text("删除", color = LogoGreen) }
            },
            dismissButton = {
                TextButton(onClick = { categoryToDelete = null }) { Text("取消") }
            }
        )
    }

    mergeSource?.let { source ->
        val others = snapshot?.items?.filter { it.entry.id != source.entry.id }.orEmpty()
        IntentMergePickerSheet(
            sourceName = source.entry.displayName,
            candidates = others,
            onDismiss = { mergeSource = null },
            onMergeIntoExisting = { targetId ->
                mergeSource = null
                viewModel.mergeEntries(source.entry.id, targetId, null)
            }
        )
    }
}

@Composable
private fun ManageTemplateCard(onApplyTemplate: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(LogoGreen.copy(alpha = 0.06f))
            .border(1.dp, LogoGreen.copy(alpha = 0.16f), RoundedCornerShape(16.dp))
            .clickable(onClick = onApplyTemplate)
            .padding(16.dp)
    ) {
        Text(
            text = "一键创建常用分类",
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface.copy(alpha = 0.88f)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "工作 · 社交 · 学习 · 消遣 · 其他",
            fontSize = 13.sp,
            color = cs.onSurface.copy(alpha = 0.50f)
        )
    }
}

@Composable
private fun CategoryManageRow(
    category: IntentCategory,
    entryCount: Int,
    onDelete: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category.emoji?.let { "$it ${category.name}" } ?: category.name,
            fontSize = 15.sp,
            color = cs.onSurface.copy(alpha = 0.85f),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "$entryCount 条",
            fontSize = 13.sp,
            color = cs.onSurface.copy(alpha = 0.42f),
            modifier = Modifier.padding(end = 8.dp)
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "删除分类",
                tint = cs.onSurface.copy(alpha = 0.35f)
            )
        }
    }
}

@Composable
private fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, emoji: String?) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var emoji by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加分类") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(16) },
                    label = { Text("名称") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = emoji,
                    onValueChange = { emoji = it.take(2) },
                    label = { Text("图标（可选）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            val n = name.trim()
                            if (n.isNotEmpty()) onConfirm(n, emoji.trim().ifBlank { null })
                        }
                    )
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val n = name.trim()
                if (n.isNotEmpty()) onConfirm(n, emoji.trim().ifBlank { null })
            }) { Text("添加", color = LogoGreen) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun IntentMergePickerSheet(
    sourceName: String,
    candidates: List<IntentPoolItem>,
    onDismiss: () -> Unit,
    onMergeIntoExisting: (String) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = cs.surface,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = "合并「$sourceName」",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.92f)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "合并后时长加在一起，原始记录各自保留。",
                fontSize = 13.sp,
                color = cs.onSurface.copy(alpha = 0.48f),
                lineHeight = 19.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "并入已有意图",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface.copy(alpha = 0.40f)
            )
            Spacer(modifier = Modifier.height(8.dp))
            candidates.take(8).forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable {
                            scope.launch {
                                sheetState.hide()
                                onMergeIntoExisting(item.entry.id)
                            }
                        }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.entry.displayName,
                        fontSize = 15.sp,
                        color = cs.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = formatIntentPoolDurationShort(item.totalSeconds),
                        fontSize = 13.sp,
                        color = cs.onSurface.copy(alpha = 0.42f)
                    )
                }
            }
            if (candidates.isEmpty()) {
                Text(
                    text = "暂无其它意图可并入",
                    fontSize = 14.sp,
                    color = cs.onSurface.copy(alpha = 0.42f),
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            TextButton(onClick = {
                scope.launch {
                    sheetState.hide()
                    onDismiss()
                }
            }) {
                Text("取消", color = cs.onSurface.copy(alpha = 0.55f))
            }
        }
    }
}
