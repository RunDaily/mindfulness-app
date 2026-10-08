package com.life.mindfulnessapp.ui.applist

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.life.mindfulnessapp.domain.model.IntentCategory
import com.life.mindfulnessapp.domain.model.IntentPoolItem
import com.life.mindfulnessapp.ui.theme.LogoGreen

/**
 * App 关系页 · 意图 Tab。
 * 设计原则：反思而非审判；默认只读，整理进「管理」。
 */
@Composable
fun IntentPoolTab(
    packageName: String,
    contentPadding: PaddingValues,
    onItemClick: (String) -> Unit,
    onManageClick: () -> Unit,
    compact: Boolean = false,
    viewModel: IntentPoolViewModel = hiltViewModel()
) {
    LaunchedEffect(packageName) {
        viewModel.load(packageName, trackView = true)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, packageName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.load(packageName, trackView = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val snapshot by viewModel.snapshot.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val timeScope by viewModel.timeScope.collectAsState()
    val sort by viewModel.sort.collectAsState()
    val categoryFilterId by viewModel.categoryFilterId.collectAsState()
    val actionMessage by viewModel.actionMessage.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(actionMessage) {
        val msg = actionMessage ?: return@LaunchedEffect
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        viewModel.consumeActionMessage()
    }

    when {
        isLoading && snapshot == null -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LogoGreen, strokeWidth = 2.dp)
            }
        }
        snapshot == null || snapshot!!.totalEntryCount == 0 && snapshot!!.hiddenCount == 0 -> {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
            ) {
                IntentPoolEmptyState()
            }
        }
        else -> {
            val data = snapshot!!
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = contentPadding.calculateTopPadding())
                    .navigationBarsPadding()
                    .padding(bottom = contentPadding.calculateBottomPadding()),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)
            ) {
                item {
                    if (!compact) {
                        IntentPoolSummaryCard(snapshot = data)
                        Spacer(modifier = Modifier.height(16.dp))
                        IntentPoolScopeSortRow(
                            timeScope = timeScope,
                            sort = sort,
                            onTimeScopeChange = viewModel::setTimeScope,
                            onSortChange = viewModel::setSort
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                    } else {
                        Text(
                            text = "${data.totalEntryCount} 种意图 · ${formatIntentPoolDuration(data.totalMindfulSeconds)}",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
                            modifier = Modifier.padding(bottom = 10.dp)
                        )
                    }
                    if (data.categoryBreakdown.isNotEmpty()) {
                        IntentPoolCategoryChips(
                            breakdown = data.categoryBreakdown,
                            selectedFilterId = categoryFilterId,
                            onFilterSelected = viewModel::setCategoryFilter
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    RowManageEntry(onManageClick = onManageClick)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val grouped = groupItemsByCategory(data.items)
                if (grouped.size <= 1) {
                    items(data.items, key = { it.entry.id }) { item ->
                        IntentPoolItemRow(
                            item = item,
                            timeScope = timeScope,
                            onClick = { onItemClick(item.entry.id) },
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                } else {
                    grouped.forEach { (category, items) ->
                        item(key = "header_${category?.id ?: "none"}") {
                            IntentPoolSectionHeader(category = category)
                        }
                        items(items, key = { it.entry.id }) { item ->
                            IntentPoolItemRow(
                                item = item,
                                timeScope = timeScope,
                                onClick = { onItemClick(item.entry.id) },
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                        }
                        item(key = "gap_${category?.id ?: "none"}") {
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }
                }

                if (data.hiddenCount > 0) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "另有 ${data.hiddenCount} 条已隐藏 · 可在管理中恢复",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RowManageEntry(onManageClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    TextButton(
        onClick = onManageClick,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = "整理分类 · 合并意图",
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = cs.onSurface.copy(alpha = 0.55f)
        )
    }
}

private fun groupItemsByCategory(
    items: List<IntentPoolItem>
): List<Pair<IntentCategory?, List<IntentPoolItem>>> {
    if (items.none { it.category != null }) return listOf(null to items)
    val grouped = items.groupBy { it.category?.id }
    val ordered = mutableListOf<Pair<IntentCategory?, List<IntentPoolItem>>>()
    items.mapNotNull { it.category }.distinctBy { it.id }.sortedBy { it.sortOrder }.forEach { cat ->
        grouped[cat.id]?.let { ordered += cat to it }
    }
    grouped[null]?.let { ordered += null to it }
    return ordered
}
