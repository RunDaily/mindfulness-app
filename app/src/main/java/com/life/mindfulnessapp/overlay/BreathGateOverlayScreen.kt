package com.life.mindfulnessapp.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.IntentGateAction
import com.life.mindfulnessapp.domain.model.PendingInterrupt
import com.life.mindfulnessapp.domain.model.SessionLimitPolicy
import com.life.mindfulnessapp.domain.model.ThemePack
import com.life.mindfulnessapp.ui.theme.MindfulnessAppTheme
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 软门放行。
 * - 搜索 / 写下意图：不定本次时长（[minutes] = 0）
 * - 随意浏览：必须选时长
 */
data class SoftDoorChoice(
    val search: Boolean,
    val text: String,
    val minutes: Int,
    /** 固定页深链 id；写意图点了可直达标签或文案命中目录时带上 */
    val deepLinkId: String? = null,
)

/**
 * 刷逛软门：总门分流。
 * 搜索直达 / 写下意图不定时长；只有随意浏览认本次多久。
 */
@Composable
fun BreathGateOverlayScreen(
    appName: String,
    remainingLabel: String,
    canSearch: Boolean,
    /**
     * 是否提供「随意浏览」路。
     * 仅目录具备可靠搜索深链的刷逛型 App 为 true；普通 App 不适合。
     */
    offerBrowse: Boolean = false,
    intentTags: List<IntentGateAction> = emptyList(),
    /** 今日已刷整分（随意浏览账） */
    browseUsedMinutes: Int = 0,
    /** 随意浏览日限；null / ≤0 = 不限，门上不写 x/y */
    browseLimitMinutes: Int? = null,
    maxSessionMinutes: Int = SessionLimitPolicy.MAX_SESSION_MINUTES,
    /** 随意浏览冷却剩余整分；>0 时不可再进随意浏览 */
    browseCooldownMinutes: Int = 0,
    packageName: String = "",
    themePack: ThemePack = ThemePack.Night,
    /** 有名意图 / 搜索未闭环快照；随意浏览不会传入 */
    pendingInterrupt: PendingInterrupt? = null,
    onResumePrevious: (() -> Unit)? = null,
    /** 门内呼气收束中：离开字略亮、不可再点 */
    leaveHolding: Boolean = false,
    onAdmit: (SoftDoorChoice, (Boolean) -> Unit) -> Unit,
    onLeave: () -> Unit,
) {
    var face by remember { mutableStateOf(BreathGateFace.Fork) }
    var searchText by remember { mutableStateOf("") }
    var intentText by remember { mutableStateOf("") }
    var browseMinutes by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var recentIntents by remember { mutableStateOf<List<GateRecentItem>>(emptyList()) }
    var recentSearches by remember { mutableStateOf<List<GateRecentItem>>(emptyList()) }
    var liveTags by remember { mutableStateOf(intentTags) }
    var recentHiddenTick by remember { mutableIntStateOf(0) }
    /** 点了可直达标签时带上；手改文案后若不再匹配则清空 */
    var boundDeepLinkId by remember { mutableStateOf<String?>(null) }
    /** 本门会话是否挂教学副句；默认 true 避免首帧闪空 */
    var showTeachSubtitles by remember { mutableStateOf(true) }
    val rulerMax = maxSessionMinutes.coerceIn(0, SessionLimitPolicy.MAX_SESSION_MINUTES)
    val cooldownMin = browseCooldownMinutes.coerceAtLeast(0)
    val canBrowse = offerBrowse && browseDoorMarks(rulerMax).isNotEmpty()
    val browseReady = canBrowse && cooldownMin <= 0
    val enabled = !busy && !leaveHolding
    val context = LocalContext.current

    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    fun dismissKeyboard() {
        focusManager.clearFocus(force = true)
        keyboard?.hide()
    }

    LaunchedEffect(Unit) {
        dismissKeyboard()
    }

    LaunchedEffect(packageName) {
        if (packageName.isBlank()) return@LaunchedEffect
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            InterceptOverlayEntryPoint::class.java
        )
        showTeachSubtitles = withContext(Dispatchers.IO) {
            entry.appPreferences().consumeGatePathTeachShow(packageName)
        }
    }

    LaunchedEffect(rulerMax, face) {
        if (face == BreathGateFace.Browse && browseMinutes > rulerMax) {
            browseMinutes = 0
        }
    }

    LaunchedEffect(packageName, face, recentHiddenTick) {
        if (packageName.isBlank()) return@LaunchedEffect
        if (face != BreathGateFace.Intent && face != BreathGateFace.Search) return@LaunchedEffect
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            InterceptOverlayEntryPoint::class.java
        )
        withContext(Dispatchers.IO) {
            val prefs = entry.appPreferences()
            when (face) {
                BreathGateFace.Intent -> {
                    val tags = com.life.mindfulnessapp.domain.model.CommonIntentsCodec
                        .gateItems(entry.appLimitRepository().getCommonIntents(packageName))
                        .map {
                            IntentGateAction(
                                id = it.deepLinkId ?: it.label,
                                label = it.label,
                                deepLinkId = it.deepLinkId
                            )
                        }
                    val recent = entry.usageRecordRepository()
                        .getRecentPurposeStats(packageName, 24)
                        .asSequence()
                        .filter { it.purpose.trim().isNotEmpty() }
                        .filterNot { BrowseCasualIntent.isBrowseLike(it.purpose) }
                        .filterNot { prefs.isGateRecentPurposeHidden(packageName, it.purpose) }
                        .distinctBy { it.purpose.trim() }
                        .take(5)
                        .map {
                            GateRecentItem(
                                purpose = it.purpose.trim(),
                                whenLabel = formatGateRecentWhen(it.lastUsedAt)
                            )
                        }
                        .toList()
                    liveTags = tags
                    recentIntents = recent
                }
                BreathGateFace.Search -> {
                    recentSearches = entry.usageRecordRepository()
                        .getRecentSearchPurposeStats(packageName, 24)
                        .asSequence()
                        .filter { it.purpose.trim().isNotEmpty() }
                        .filterNot { prefs.isGateRecentPurposeHidden(packageName, it.purpose) }
                        .distinctBy { it.purpose.trim() }
                        .take(5)
                        .map {
                            GateRecentItem(
                                purpose = it.purpose.trim(),
                                whenLabel = formatGateRecentWhen(it.lastUsedAt)
                            )
                        }
                        .toList()
                }
                else -> Unit
            }
        }
    }

    fun admit(choice: SoftDoorChoice) {
        if (busy) return
        busy = true
        onAdmit(choice) { ok ->
            if (!ok) busy = false
        }
    }

    MindfulnessAppTheme(themePack = themePack) {
      ProvideGateInk(themePack) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(LockInk.bg)
                }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = { dismissKeyboard() }
                )
                .windowInsetsPadding(
                    WindowInsets.statusBars
                        .union(WindowInsets.displayCutout)
                        .union(WindowInsets.navigationBars)
                )
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 26.dp)
                    .padding(top = 18.dp, bottom = 16.dp)
            ) {
                if (face != BreathGateFace.TagsManage) {
                    SoftDoorAppIdentity(packageName = packageName, appName = appName)
                    if (face == BreathGateFace.Fork && remainingLabel.isNotBlank()) {
                        Text(
                            text = remainingLabel,
                            modifier = Modifier.padding(top = 6.dp),
                            fontSize = 12.sp,
                            color = LockInk.fog
                        )
                    }
                }

                AnimatedContent(
                    targetState = face,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(160))
                    },
                    label = "breath_gate_face"
                ) { current ->
                    when (current) {
                        BreathGateFace.Fork -> {
                            val resumeLabel = pendingInterrupt
                                ?.takeIf { it.isStrongResumeEligible() }
                                ?.gateResumePurposeLabel()
                                .orEmpty()
                            BreathGateForkFace(
                                factsLabel = "",
                                canSearch = canSearch,
                                enabled = enabled,
                                offerBrowse = offerBrowse,
                                canBrowse = canBrowse,
                                browseCooldownMinutes = cooldownMin,
                                resumePurposeLabel = resumeLabel,
                                leaveHolding = leaveHolding,
                                showTeachSubtitles = showTeachSubtitles,
                                onSearch = {
                                    dismissKeyboard()
                                    searchText = ""
                                    face = BreathGateFace.Search
                                },
                                onIntent = {
                                    dismissKeyboard()
                                    intentText = ""
                                    boundDeepLinkId = null
                                    face = BreathGateFace.Intent
                                },
                                onBrowse = {
                                    if (!browseReady) return@BreathGateForkFace
                                    dismissKeyboard()
                                    browseMinutes = 0
                                    face = BreathGateFace.Browse
                                },
                                onResume = if (resumeLabel.isNotBlank() && onResumePrevious != null) {
                                    {
                                        if (!busy) {
                                            busy = true
                                            onResumePrevious()
                                        }
                                    }
                                } else {
                                    null
                                },
                                onCancel = {
                                    if (!busy && !leaveHolding) onLeave()
                                }
                            )
                        }
                        BreathGateFace.Search -> BreathGateSearchFace(
                            query = searchText,
                            onQueryChange = { searchText = it },
                            recentItems = recentSearches,
                            enabled = enabled,
                            onSearch = {
                                val q = searchText.trim()
                                if (q.replace(" ", "").length < 2) return@BreathGateSearchFace
                                admit(
                                    SoftDoorChoice(
                                        search = true,
                                        text = q,
                                        minutes = 0
                                    )
                                )
                            },
                            onBack = {
                                dismissKeyboard()
                                face = BreathGateFace.Fork
                            },
                            onDeleteRecent = { purpose ->
                                val entry = EntryPointAccessors.fromApplication(
                                    context.applicationContext,
                                    InterceptOverlayEntryPoint::class.java
                                )
                                entry.appPreferences().hideGateRecentPurpose(packageName, purpose)
                                if (searchText.trim() == purpose.trim()) {
                                    searchText = ""
                                }
                                recentHiddenTick++
                            }
                        )
                        BreathGateFace.Intent -> BreathGateIntentFace(
                            text = intentText,
                            onTextChange = { next ->
                                intentText = next
                                val match = liveTags.firstOrNull {
                                    it.canDeepLink &&
                                        it.label.trim().equals(next.trim(), ignoreCase = true)
                                }
                                boundDeepLinkId = match?.deepLinkId
                            },
                            tags = liveTags,
                            recentItems = recentIntents,
                            enabled = enabled,
                            onEnter = {
                                val t = intentText.trim()
                                if (t.isEmpty()) return@BreathGateIntentFace
                                val landId = boundDeepLinkId
                                    ?: liveTags.firstOrNull {
                                        it.canDeepLink &&
                                            it.label.trim().equals(t, ignoreCase = true)
                                    }?.deepLinkId
                                admit(
                                    SoftDoorChoice(
                                        search = false,
                                        text = t,
                                        minutes = 0,
                                        deepLinkId = landId
                                    )
                                )
                            },
                            onBack = {
                                dismissKeyboard()
                                face = BreathGateFace.Fork
                            },
                            onDeleteRecent = { purpose ->
                                val entry = EntryPointAccessors.fromApplication(
                                    context.applicationContext,
                                    InterceptOverlayEntryPoint::class.java
                                )
                                entry.appPreferences().hideGateRecentPurpose(packageName, purpose)
                                if (intentText.trim() == purpose.trim()) {
                                    intentText = ""
                                    boundDeepLinkId = null
                                }
                                recentHiddenTick++
                            },
                            onAdjustTags = {
                                dismissKeyboard()
                                face = BreathGateFace.TagsManage
                            },
                            onPickTag = { tag ->
                                val on = intentText.trim() == tag.label.trim()
                                if (on) {
                                    intentText = ""
                                    boundDeepLinkId = null
                                } else {
                                    intentText = tag.label
                                    boundDeepLinkId = tag.deepLinkId
                                }
                            }
                        )
                        BreathGateFace.Browse -> BreathGateBrowseFace(
                            selectedMinutes = browseMinutes,
                            onSelectMinutes = { browseMinutes = it },
                            usedMinutes = browseUsedMinutes,
                            limitMinutes = browseLimitMinutes,
                            maxSessionMinutes = rulerMax,
                            cooldownMinutes = cooldownMin,
                            enabled = enabled && browseReady,
                            onEnter = {
                                if (!browseReady || browseMinutes <= 0) return@BreathGateBrowseFace
                                admit(
                                    SoftDoorChoice(
                                        search = false,
                                        text = BrowseCasualIntent.LABEL,
                                        minutes = browseMinutes
                                    )
                                )
                            },
                            onBack = {
                                face = BreathGateFace.Fork
                            }
                        )
                        BreathGateFace.TagsManage -> GateQuickIntentTagsPanel(
                            packageName = packageName,
                            appName = appName,
                            onDone = {
                                dismissKeyboard()
                                face = BreathGateFace.Intent
                            }
                        )
                    }
                }
            }
        }
      }
    }
}

@Composable
private fun SoftDoorAppIdentity(
    packageName: String,
    appName: String
) {
    @Suppress("UNUSED_PARAMETER")
    val absorbPkg = packageName
    Text(
        text = appName.ifBlank { absorbPkg },
        fontSize = 12.sp,
        letterSpacing = 0.4.sp,
        color = LockInk.mist
    )
}
