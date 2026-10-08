package com.life.mindfulnessapp.ui.features

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.AppTodayGlance
import com.life.mindfulnessapp.ui.applist.AppIcon
import com.life.mindfulnessapp.ui.applist.AppListViewModel
import com.life.mindfulnessapp.ui.applist.CapabilityCopy
import com.life.mindfulnessapp.ui.applist.buildCapabilityAppRowPeek
import com.life.mindfulnessapp.ui.applist.hasCapability
import com.life.mindfulnessapp.ui.theme.CapabilityForm
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.CapabilityMark
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.vip.AccessGateDialog

private val RowShape = RoundedCornerShape(16.dp)

/**
 * 某一能力下的应用列表；可从此处为该能力添加 App。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapabilityAppsScreen(
    capability: CapabilityKind,
    viewModel: AppListViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (packageName: String) -> Unit,
    onNavigateToVip: () -> Unit = {}
) {
    val monitored by viewModel.monitoredApps.collectAsState()
    val glances by viewModel.todayGlancesByPackage.collectAsState()
    val isAtFreeLimit by viewModel.isAtFreeLimit.collectAsState()
    val accessGate by viewModel.accessGate.collectAsState()
    val copy = CapabilityCopy.of(capability)
    val apps = remember(monitored, capability) {
        monitored.filter { it.hasCapability(capability) }
    }
    val cs = MaterialTheme.colorScheme

    var pendingAddAfterUnlock by remember { mutableStateOf(false) }

    LaunchedEffect(isAtFreeLimit, pendingAddAfterUnlock, accessGate.visible) {
        if (pendingAddAfterUnlock && !isAtFreeLimit && !accessGate.visible) {
            pendingAddAfterUnlock = false
            onNavigateToAdd()
        }
    }

    fun tryAdd() {
        if (isAtFreeLimit) {
            pendingAddAfterUnlock = true
            viewModel.requestVipUpgrade()
        } else {
            onNavigateToAdd()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CapabilityMark(
                                kind = capability,
                                form = CapabilityForm.Standard,
                                size = 18.dp
                            )
                            Text(
                                copy.label,
                                fontWeight = FontWeight.SemiBold,
                                color = cs.onSurface,
                                fontSize = 17.sp
                            )
                        }
                        Text(
                            text = if (apps.isEmpty()) {
                                copy.description
                            } else {
                                "共 ${apps.size} 个应用"
                            },
                            fontSize = 11.sp,
                            color = cs.onSurface.copy(alpha = 0.40f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = cs.background)
            )
        },
        floatingActionButton = {
            if (apps.isNotEmpty()) {
                FloatingActionButton(
                    onClick = ::tryAdd,
                    containerColor = LogoGreen,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "添加应用")
                }
            }
        },
        containerColor = cs.background
    ) { padding ->
        if (apps.isEmpty()) {
            EmptyCapabilityApps(
                copy = copy,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                onAddClick = ::tryAdd
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = 88.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = apps,
                    key = { it.listKey }
                ) { app ->
                    CapabilityAppRow(
                        app = app,
                        capability = capability,
                        glance = glances[app.packageName],
                        onClick = { onNavigateToEdit(app.packageName) }
                    )
                }
            }
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
                viewModel.dismissVipUpgradeDialog()
                onNavigateToVip()
            },
            onBackToGate = viewModel::backToAccessGate,
            onConsumeUnlockToast = viewModel::consumeAccessUnlockToast
        )
    }
}

@Composable
private fun EmptyCapabilityApps(
    copy: CapabilityCopy,
    modifier: Modifier = Modifier,
    onAddClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        CapabilityMark(
            kind = copy.kind,
            form = CapabilityForm.Emphasis,
            size = 36.dp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "还没有应用",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = cs.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = copy.description,
            fontSize = 13.sp,
            color = cs.onSurface.copy(alpha = 0.42f),
            lineHeight = 18.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(LogoGreen.copy(alpha = 0.12f))
                .clickable(onClick = onAddClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = LogoGreen,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "去添加",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = LogoGreen
            )
        }
    }
}

@Composable
private fun CapabilityAppRow(
    app: AppInfo,
    capability: CapabilityKind,
    glance: AppTodayGlance?,
    onClick: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    val peek = remember(app, capability, glance) {
        buildCapabilityAppRowPeek(capability, app, glance)
    }
    val nameColor = if (app.isUninstalled) {
        cs.onSurface.copy(alpha = 0.36f)
    } else {
        cs.onSurface
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RowShape)
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.12f), RowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        AppIcon(
            drawable = app.icon,
            modifier = Modifier.size(44.dp)
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = app.appName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = nameColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (app.isUninstalled) {
                    Text(
                        text = "已卸载",
                        fontSize = 12.sp,
                        color = cs.onSurface.copy(alpha = 0.35f)
                    )
                }
            }
            Text(
                text = peek.primary,
                fontSize = 12.sp,
                color = cs.onSurface.copy(alpha = 0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            peek.secondary?.let { secondary ->
                Text(
                    text = secondary,
                    fontSize = 11.sp,
                    color = LogoGreen.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = cs.onSurface.copy(alpha = 0.28f),
            modifier = Modifier.size(20.dp)
        )
    }
}
