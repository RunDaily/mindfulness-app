package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.ui.common.SecondaryPageScaffold
import com.life.mindfulnessapp.ui.theme.LogoGreen

@Composable
fun PlanAddAppScreen(
    onDone: () -> Unit,
    preselectPackage: String? = null,
    viewModel: PlanAddAppViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val colors = MaterialTheme.colorScheme
    val focus = LocalFocusManager.current
    LaunchedEffect(preselectPackage) { viewModel.load(preselectPackage) }
    state.unsuitableMessage?.let { msg ->
        AlertDialog(
            onDismissRequest = viewModel::dismissUnsuitable,
            title = { Text("不适合加入") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissUnsuitable) {
                    Text("知道了", color = LogoGreen)
                }
            }
        )
    }
    if (state.configuring) {
        val draft = state.queue.getOrNull(state.configIndex) ?: return
        val nextName = state.queue.getOrNull(state.configIndex + 1)?.appName
        var editing by remember(draft.packageName, state.configIndex) { mutableStateOf(draft) }
        LaunchedEffect(state.configIndex) {
            val current = state.queue.getOrNull(state.configIndex) ?: return@LaunchedEffect
            editing = current
        }
        InstrumentConfigScreen(
            draft = editing,
            onChange = {
                editing = it
                viewModel.updateDraft(it)
            },
            onBack = viewModel::backConfig,
            backLabel = if (state.configIndex == 0) "‹ 返回挑选" else "‹ 上一个",
            indexLabel = "${state.configIndex + 1} / ${state.queue.size}",
            primaryLabel = if (nextName != null) "保存，去$nextName" else "保存",
            onPrimary = {
                viewModel.updateDraft(editing)
                viewModel.saveCurrent(onDone)
            }
        )
        return
    }

    SecondaryPageScaffold(
        title = "选择应用",
        onBack = onDone,
        subtitle = "近 7 日"
    ) { padding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 8.dp)
    ) {
        OutlinedTextField(
            value = state.query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("搜索应用名") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() })
        )
        Spacer(Modifier.height(8.dp))
        if (state.loading && state.rows.isEmpty()) {
            CircularProgressIndicator(
                color = LogoGreen,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 32.dp)
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.rows, key = { it.listKey }) { row ->
                    val mark = when {
                        row.alreadyInPlan -> "已在方案"
                        row.unsuitable -> "不适合"
                        row.selected -> "●"
                        else -> "○"
                    }
                    HairlineRow(
                        title = row.appName,
                        meta = row.usageLabel,
                        trailing = mark,
                        trailingSub = "",
                        packageName = row.packageName,
                        onClick = if (row.alreadyInPlan) {
                            null
                        } else {
                            { viewModel.toggle(row.packageName) }
                        }
                    )
                }
                if (state.rows.isEmpty() && !state.loading) {
                    item {
                        Text(
                            if (state.query.isBlank()) "没有可添加的应用" else "没有匹配的应用",
                            color = colors.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 24.dp)
                        )
                    }
                }
            }
        }
        if (state.selectedCount > 0) {
            Text(
                "下一步 · 配置 ${state.selectedCount} 个",
                color = LogoGreen,
                fontSize = 16.sp,
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .clickable(enabled = !state.saving) {
                        viewModel.beginConfig()
                    }
            )
        }
    }
    }
}
