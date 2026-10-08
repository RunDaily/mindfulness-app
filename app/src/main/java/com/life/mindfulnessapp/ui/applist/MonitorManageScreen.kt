package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.vip.AccessGateDialog

private val CapabilityBlockShape = RoundedCornerShape(18.dp)
private const val GridColumns = 4

/**
 * 坑位管理：三能力各自成区；区内网格；区头「添加」进批量系锚。
 * [embedded] = true 时作为「能力」Tab 内视图，不带顶栏。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitorManageScreen(
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit = {},
    onNavigateToAdd: (CapabilityKind?) -> Unit,
    onNavigateToEdit: (packageName: String) -> Unit,
    onNavigateToReorder: () -> Unit = {},
    onNavigateToVip: () -> Unit = {},
    embedded: Boolean = false,
    /** 变化时滚回列表顶部（能力子 Tab 切换） */
    scrollToTopToken: Any? = null,
    modifier: Modifier = Modifier
) {
    val monitored by viewModel.monitoredApps.collectAsState()
    val isAtFreeLimit by viewModel.isAtFreeLimit.collectAsState()
    val vipLevel by viewModel.vipLevel.collectAsState()
    val accessGate by viewModel.accessGate.collectAsState()

    var pendingAddKind by remember { mutableStateOf<CapabilityKind?>(null) }
    var pendingAddAfterUnlock by remember { mutableStateOf(false) }

    LaunchedEffect(isAtFreeLimit, pendingAddAfterUnlock, accessGate.visible) {
        if (pendingAddAfterUnlock && !isAtFreeLimit && !accessGate.visible) {
            pendingAddAfterUnlock = false
            val kind = pendingAddKind
            pendingAddKind = null
            onNavigateToAdd(kind)
        }
    }

    fun tryAddMonitor(kind: CapabilityKind?) {
        // 批量页可叠加已系锚 App（不占新坑）；仅「完全满且无叠加空间」时再拦
        if (kind != null) {
            onNavigateToAdd(kind)
            return
        }
        if (isAtFreeLimit) {
            pendingAddKind = kind
            pendingAddAfterUnlock = true
            viewModel.requestVipUpgrade()
        } else {
            onNavigateToAdd(kind)
        }
    }

    val cs = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    val sections = remember(monitored) {
        CapabilityKind.entries.map { kind ->
            kind to monitored.filter { it.hasCapability(kind) }
        }
    }

    LaunchedEffect(scrollToTopToken) {
        if (scrollToTopToken != null) {
            listState.scrollToItem(0)
        }
    }

    val listContent = @Composable { listModifier: Modifier ->
        LazyColumn(
            state = listState,
            modifier = listModifier,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = if (embedded) 4.dp else 8.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            items(
                items = sections,
                key = { (kind, _) -> kind.name }
            ) { (kind, apps) ->
                CapabilityBlock(
                    kind = kind,
                    apps = apps,
                    cs = cs,
                    onAppClick = { onNavigateToEdit(it.packageName) },
                    onAddClick = { tryAddMonitor(kind) }
                )
            }
        }
    }

    if (embedded) {
        listContent(modifier.fillMaxSize())
    } else {
        Scaffold(
            modifier = modifier,
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "坑位",
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface,
                                fontSize = 17.sp
                            )
                            Text(
                                text = buildString {
                                    if (monitored.isEmpty()) {
                                        append("按能力把 App 放进坑位")
                                    } else if (vipLevel <= 0) {
                                        append("${monitored.size} / ${AppPreferences.FREE_MONITOR_LIMIT}")
                                        append(" · 按能力查看")
                                    } else {
                                        append("系着 ${monitored.size} 只 · 按能力查看")
                                    }
                                },
                                fontSize = 11.sp,
                                color = when {
                                    isAtFreeLimit -> Color(0xFFE8941A)
                                    else -> cs.onSurface.copy(alpha = 0.40f)
                                }
                            )
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
                    actions = {
                        if (monitored.isNotEmpty()) {
                            TextButton(onClick = onNavigateToReorder) {
                                Text(
                                    "排序",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = cs.onSurface.copy(alpha = 0.55f)
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
                )
            },
            containerColor = cs.background
        ) { padding ->
            listContent(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            )
        }
    }

    if (accessGate.visible || accessGate.unlockToast != null) {
        AccessGateDialog(
            state = accessGate,
            cardColor = cs.surface,
            textPrimary = cs.onSurface,
            textSecondary = cs.onSurfaceVariant,
            borderColor = cs.outline,
            accentGreen = LogoGreen,
            onDismiss = {
                pendingAddAfterUnlock = false
                pendingAddKind = null
                viewModel.dismissVipUpgradeDialog()
            },
            onCodeChange = viewModel::onAccessCodeChange,
            onRedeem = viewModel::redeemAccessCode,
            onOpenClaim = viewModel::openAccessClaimStep,
            onOpenRedeem = viewModel::openAccessRedeemStep,
            onClaimChannelChange = viewModel::onAccessClaimChannelChange,
            onClaimContactChange = viewModel::onAccessClaimContactChange,
            onSubmitClaim = viewModel::submitAccessClaim,
            onRedeemIssued = viewModel::redeemIssuedAccessCode,
            onViewMembership = {
                pendingAddAfterUnlock = false
                pendingAddKind = null
                viewModel.dismissVipUpgradeDialog()
                onNavigateToVip()
            },
            onBackToGate = viewModel::backToAccessGate,
            onConsumeUnlockToast = viewModel::consumeAccessUnlockToast
        )
    }
}

fun AppInfo.hasCapability(kind: CapabilityKind): Boolean {
    // 未系锚 App 的能力字段只是占位默认值，不能当作已开启
    if (!isMonitored) return false
    return when (kind) {
        CapabilityKind.IntentGate -> requireIntentOnOpen
        CapabilityKind.TimeLock -> timeLimitEnabled
        CapabilityKind.PeriodLock -> periodLockEnabled
    }
}


@Composable
private fun CapabilityBlock(
    kind: CapabilityKind,
    apps: List<AppInfo>,
    cs: ColorScheme,
    onAppClick: (AppInfo) -> Unit,
    onAddClick: () -> Unit
) {
    val copy = CapabilityCopy.of(kind)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(CapabilityBlockShape)
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.14f), CapabilityBlockShape)
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CapabilityMark(
                kind = kind,
                form = CapabilityForm.Standard,
                size = 18.dp
            )
            Text(
                copy.label,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onSurface
            )
            Text(
                "· ${apps.size}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = cs.onSurface.copy(alpha = 0.35f)
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            copy.description,
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.40f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(14.dp))

        CapabilityAppGrid(
            apps = apps,
            cs = cs,
            onAppClick = onAppClick,
            onAddClick = onAddClick
        )
    }
}

@Composable
private fun CapabilityAppGrid(
    apps: List<AppInfo>,
    cs: ColorScheme,
    onAppClick: (AppInfo) -> Unit,
    onAddClick: () -> Unit
) {
    val cells: List<AppInfo?> = apps + null // trailing add slot
    val rows = cells.chunked(GridColumns)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                row.forEach { app ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (app == null) {
                            CapabilityAddCell(cs = cs, onClick = onAddClick)
                        } else {
                            CapabilityAppGridCell(
                                app = app,
                                cs = cs,
                                onClick = { onAppClick(app) }
                            )
                        }
                    }
                }
                repeat(GridColumns - row.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** 占用坑位格：图标 + 名称 */
@Composable
private fun CapabilityAppGridCell(
    app: AppInfo,
    cs: ColorScheme,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AppIcon(
            drawable = app.icon,
            modifier = Modifier.size(46.dp)
        )
        Text(
            text = app.appName,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = if (app.isUninstalled)
                cs.onSurface.copy(alpha = 0.36f)
            else
                cs.onSurface.copy(alpha = 0.78f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 14.sp,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 区内添加：虚线槽，语义对齐首页「+」坑 */
@Composable
private fun CapabilityAddCell(
    cs: ColorScheme,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(46.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = Stroke(
                    width = 1.4.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(
                        floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                        0f
                    )
                )
                drawRoundRect(
                    color = LogoGreen.copy(alpha = 0.45f),
                    cornerRadius = CornerRadius(12.dp.toPx()),
                    style = stroke
                )
            }
            Icon(
                Icons.Default.Add,
                contentDescription = "添加应用",
                tint = LogoGreen.copy(alpha = 0.85f),
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = "添加",
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            color = LogoGreen.copy(alpha = 0.80f),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
