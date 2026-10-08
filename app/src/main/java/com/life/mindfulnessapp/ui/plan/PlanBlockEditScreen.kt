package com.life.mindfulnessapp.ui.plan

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.PlanBlock
import com.life.mindfulnessapp.ui.applist.PeriodDaysPickerContent
import com.life.mindfulnessapp.ui.applist.PeriodRangePickerContent
import com.life.mindfulnessapp.ui.theme.LogoGreen

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlanBlockEditScreen(
    planId: String?,
    onNavigateBack: () -> Unit,
    viewModel: PlanBlockEditViewModel = hiltViewModel()
) {
    val draft by viewModel.draft.collectAsState()
    val apps by viewModel.installedApps.collectAsState()
    val saveError by viewModel.saveError.collectAsState()
    val saved by viewModel.saved.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val cs = MaterialTheme.colorScheme
    val isNew = planId.isNullOrBlank() || planId == "new"
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(planId) { viewModel.load(planId) }
    LaunchedEffect(saved) {
        if (saved) onNavigateBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (isNew) "新建计划" else "编辑计划",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp
                    )
                },
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
        if (loading || draft == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = LogoGreen)
            }
            return@Scaffold
        }
        val plan = draft!!

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                item {
                    Text(
                        text = "计划名称",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = plan.title,
                        onValueChange = viewModel::updateTitle,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：深度工作、写作业") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LogoGreen,
                            unfocusedBorderColor = cs.outline.copy(alpha = 0.35f)
                        )
                    )
                }

                item {
                    Text(
                        text = "场景",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        com.life.mindfulnessapp.domain.model.PlanScene.entries.forEach { scene ->
                            val selected = plan.scene == scene
                            Text(
                                text = scene.label,
                                fontSize = 13.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) Color.White else cs.onSurface.copy(alpha = 0.7f),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(
                                        if (selected) LogoGreen else cs.surfaceVariant.copy(alpha = 0.55f)
                                    )
                                    .clickable { viewModel.updateScene(scene) }
                                    .padding(horizontal = 14.dp, vertical = 8.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "为了（可选）",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = plan.why,
                        onValueChange = viewModel::updateWhy,
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("例如：学吉他、起床后先清醒") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = LogoGreen,
                            unfocusedBorderColor = cs.outline.copy(alpha = 0.35f)
                        )
                    )
                }

                if (isNew) {
                    item {
                        Text(
                            text = "快速模板",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = cs.onSurface.copy(alpha = 0.45f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PlanBlock.templates().forEach { template ->
                                TemplateChip(
                                    label = template.title,
                                    onClick = { viewModel.applyTemplate(template) }
                                )
                            }
                        }
                    }
                }

                item {
                    Text(
                        text = "时段",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PeriodRangePickerContent(
                        initialStartMinute = plan.startMinute,
                        initialEndMinute = plan.endMinute,
                        onRangeChange = viewModel::updateRange
                    )
                }

                item {
                    Text(
                        text = "重复",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    PeriodDaysPickerContent(
                        selectedMask = plan.daysMask,
                        onMaskChange = viewModel::updateDaysMask
                    )
                }

                item {
                    Text(
                        text = "限制的 App",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = cs.onSurface.copy(alpha = 0.45f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "已选 ${plan.packageNames.size} 个 · 到点后硬挡进入",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.36f)
                    )
                }

                items(apps, key = { it.listKey }) { app ->
                    AppPickRow(
                        app = app,
                        selected = app.packageName in plan.packageNames,
                        onToggle = { viewModel.togglePackage(app.packageName) }
                    )
                }

                item { Spacer(modifier = Modifier.height(12.dp)) }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 12.dp)
            ) {
                if (saveError != null) {
                    Text(
                        text = saveError.orEmpty(),
                        fontSize = 13.sp,
                        color = androidx.compose.ui.graphics.Color(0xFFE74C3C),
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = LogoGreen)
                ) {
                    Text("保存计划", fontWeight = FontWeight.SemiBold)
                }
                if (!isNew) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("删除此计划", color = androidx.compose.ui.graphics.Color(0xFFE74C3C))
                    }
                }
            }
        }
    }

    if (showDeleteConfirm) {
        val title = draft?.title?.trim().orEmpty()
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = {
                Text(
                    if (title.isNotEmpty()) "删除「$title」？" else "删除此计划？"
                )
            },
            text = {
                Text(
                    "删除后不再按此时段执行",
                    color = cs.onSurfaceVariant,
                    fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteAndFinish(onNavigateBack)
                    }
                ) {
                    Text("删除", color = cs.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("取消", color = cs.onSurfaceVariant)
                }
            }
        )
    }
}

@Composable
private fun TemplateChip(label: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(cs.onSurface.copy(alpha = 0.05f))
            .border(1.dp, cs.outline.copy(alpha = 0.22f), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(text = label, fontSize = 13.sp, color = cs.onSurface.copy(alpha = 0.75f))
    }
}

@Composable
private fun AppPickRow(
    app: AppInfo,
    selected: Boolean,
    onToggle: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val iconBitmap = remember(app.packageName, app.icon) {
        app.icon?.toBitmap(96, 96)?.asImageBitmap()
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) LogoGreen.copy(alpha = 0.08f) else cs.surface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconBitmap != null) {
            Image(
                bitmap = iconBitmap,
                contentDescription = null,
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cs.outline.copy(alpha = 0.15f))
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = app.appName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = cs.onSurface
            )
            if (app.isMonitored) {
                Text(
                    text = "已系锚",
                    fontSize = 11.sp,
                    color = LogoGreen.copy(alpha = 0.75f)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(if (selected) LogoGreen else cs.outline.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = cs.onPrimary,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }
}
