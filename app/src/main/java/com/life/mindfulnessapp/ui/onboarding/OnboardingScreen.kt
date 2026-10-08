package com.life.mindfulnessapp.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.ui.applist.AppListViewModel
import com.life.mindfulnessapp.ui.applist.CapabilityBindConfigSheet
import com.life.mindfulnessapp.ui.applist.CapabilityExperiencePreview
import com.life.mindfulnessapp.ui.theme.CapabilityKind
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.MindfulGreen40

/**
 * 冷启动引导：
 * 承诺 → 使用情况权限 → 用量镜子 → 能力导读与推荐 → 首绑配置 → 悬浮窗等收尾权限 → 进入 App
 */
@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel = hiltViewModel(),
    appListViewModel: AppListViewModel = hiltViewModel(),
    initialPage: Int = 0,
    isDarkTheme: Boolean = true,
    onPrivacyAccept: () -> Unit = {},
    onPrivacyDecline: () -> Unit = {},
    onComplete: () -> Unit
) {
    val context = LocalContext.current
    val phase by viewModel.phase.collectAsState()
    val permStatus by viewModel.permissionStatus.collectAsState()
    val mirrorRows by viewModel.mirrorRows.collectAsState()
    val sortMode by viewModel.sortMode.collectAsState()
    val usageLoading by viewModel.usageLoading.collectAsState()
    val insights by viewModel.insights.collectAsState()
    val recommendations by viewModel.recommendations.collectAsState()

    var bindingTarget by remember { mutableStateOf<Pair<AppInfo, CapabilityKind>?>(null) }
    var pendingUsageReturn by remember { mutableStateOf(false) }

    val usageLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refreshPermissions()
    }
    val overlayLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }
    val batteryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }
    val accessibilityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { viewModel.refreshPermissions() }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
        if (initialPage >= 1) {
            viewModel.goToPhase(OnboardingPhase.UsageAccess)
        }
    }

    LaunchedEffect(permStatus.hasUsageStats, pendingUsageReturn, phase) {
        if (
            pendingUsageReturn &&
            phase == OnboardingPhase.UsageAccess &&
            permStatus.hasUsageStats
        ) {
            pendingUsageReturn = false
            viewModel.advanceFromUsageAccess(skippedUsage = false)
        }
    }

    LaunchedEffect(bindingTarget) {
        bindingTarget?.let { (app, _) ->
            appListViewModel.refreshPermissions()
            appListViewModel.loadApp(app.packageName)
        }
    }

    LaunchedEffect(phase) {
        if (phase == OnboardingPhase.Guide) {
            viewModel.ensureDataForGuide()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AnimatedContent(
            targetState = phase,
            transitionSpec = {
                (slideInHorizontally { it / 4 } + fadeIn()).togetherWith(
                    slideOutHorizontally { -it / 4 } + fadeOut()
                )
            },
            label = "onboarding_phase"
        ) { current ->
            when (current) {
                OnboardingPhase.Welcome -> OnboardingWelcomePage(
                    isDarkTheme = isDarkTheme,
                    onNext = {
                        onPrivacyAccept()
                        viewModel.advanceFromWelcome()
                    },
                    onDecline = onPrivacyDecline
                )

                OnboardingPhase.UsageAccess -> OnboardingUsageAccessPage(
                    hasUsageStats = permStatus.hasUsageStats,
                    onGrantUsage = {
                        pendingUsageReturn = true
                        viewModel.onUsageAccessOpened()
                        usageLauncher.launch(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    },
                    onContinue = { viewModel.advanceFromUsageAccess(skippedUsage = false) },
                    onSkip = { viewModel.advanceFromUsageAccess(skippedUsage = true) },
                    onBack = { viewModel.goToPhase(OnboardingPhase.Welcome) }
                )

                OnboardingPhase.Mirror -> OnboardingUsageMirrorPage(
                    insights = insights,
                    rows = mirrorRows,
                    sortMode = sortMode,
                    usageLoading = usageLoading,
                    onSortModeChange = viewModel::setSortMode,
                    onContinue = { viewModel.advanceFromMirror() },
                    onBack = { viewModel.goToPhase(OnboardingPhase.UsageAccess) }
                )

                OnboardingPhase.Guide -> OnboardingGuidePage(
                    hasUsageData = permStatus.hasUsageStats && insights != null,
                    recommendations = recommendations,
                    onPickRecommendation = { fit ->
                        bindingTarget = fit.app to fit.primary
                    },
                    onSkip = {
                        if (!viewModel.finishOnboardingIfReady(onComplete)) {
                            /* navigated to finish */
                        }
                    },
                    onBack = {
                        if (permStatus.hasUsageStats && insights != null) {
                            viewModel.goToPhase(OnboardingPhase.Mirror)
                        } else {
                            viewModel.goToPhase(OnboardingPhase.UsageAccess)
                        }
                    }
                )

                OnboardingPhase.FinishPermissions -> OnboardingFinishPermissionsPage(
                    permStatus = permStatus,
                    missing = viewModel.missingOptionalPermissions(permStatus),
                    usageAlreadyGranted = permStatus.hasUsageStats,
                    onGrantOverlay = {
                        overlayLauncher.launch(
                            Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    },
                    onGrantBattery = {
                        batteryLauncher.launch(
                            Intent(
                                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:${context.packageName}")
                            )
                        )
                    },
                    onGrantAccessibility = {
                        accessibilityLauncher.launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    },
                    onComplete = { viewModel.completeOnboarding(onComplete) },
                    onBack = { viewModel.goToPhase(OnboardingPhase.Guide) }
                )
            }
        }

        bindingTarget?.let { (app, capability) ->
            key(app.packageName, capability.name) {
                CapabilityBindConfigSheet(
                    app = app,
                    capability = capability,
                    viewModel = appListViewModel,
                    usageAlreadyGranted = permStatus.hasUsageStats,
                    onDismiss = { bindingTarget = null },
                    onSaved = {
                        bindingTarget = null
                        viewModel.finishOnboardingIfReady(onComplete)
                    },
                    onNavigateToVip = { /* 首启无 VIP 跳转 */ }
                )
            }
        }
    }
}

@Composable
private fun OnboardingWelcomePage(
    isDarkTheme: Boolean,
    onNext: () -> Unit,
    onDecline: () -> Unit
) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp)
            .padding(top = 28.dp, bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "心锚",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = cs.onBackground.copy(alpha = 0.42f),
                letterSpacing = 3.sp
            )
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "打开之前，先停一下",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = cs.onBackground,
                textAlign = TextAlign.Center,
                lineHeight = 34.sp,
                letterSpacing = 0.4.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "先看见手机里的真实用量，再为值得守护的 App 系上锚。",
                fontSize = 14.sp,
                color = cs.onBackground.copy(alpha = 0.48f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )
            Spacer(modifier = Modifier.height(22.dp))
            CapabilityExperiencePreview(
                kind = CapabilityKind.IntentGate,
                isDarkTheme = isDarkTheme,
                tall = true,
                showCaption = false
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onNext,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MindfulGreen40),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                "开始",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        PrivacyConsentLine(
            onOpenPrivacy = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(com.life.mindfulnessapp.data.AppPreferences.HEART_ANCHOR_PRIVACY_URL)
                    )
                )
            },
            onOpenTerms = {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse(com.life.mindfulnessapp.data.AppPreferences.HEART_ANCHOR_TERMS_URL)
                    )
                )
            },
            onDecline = onDecline
        )
    }
}

@Composable
private fun PrivacyConsentLine(
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
    onDecline: () -> Unit
) {
    val cs = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("开始即表示同意", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.45f))
            Text(
                "《隐私政策》",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = LogoGreen,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onOpenPrivacy)
            )
            Text("与", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.45f))
            Text(
                "《用户协议》",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = LogoGreen,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(onClick = onOpenTerms)
            )
        }
        Text(
            "不同意并退出",
            fontSize = 12.sp,
            color = cs.onSurface.copy(alpha = 0.32f),
            modifier = Modifier.clickable(onClick = onDecline)
        )
    }
}
