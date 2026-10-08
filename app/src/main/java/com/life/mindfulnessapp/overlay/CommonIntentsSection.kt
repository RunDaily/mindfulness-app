package com.life.mindfulnessapp.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
import com.life.mindfulnessapp.data.deeplink.AppDeepLinkEntry
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.BrowseCasualPolicy
import com.life.mindfulnessapp.domain.model.CommonIntentItem
import com.life.mindfulnessapp.domain.model.CommonIntentsCodec
import com.life.mindfulnessapp.domain.model.PeriodDays
import com.life.mindfulnessapp.domain.model.PeriodWindow

/**
 * 写意图态：系统「随意浏览」+ 钉在门上的常用意图（可多行）+ 设定入口。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommonIntentsRow(
    themeConfig: InterceptThemeConfig,
    intents: List<CommonIntentItem>,
    selectedText: String,
    enabled: Boolean,
    onSelect: (CommonIntentItem) -> Unit,
    onOpenConfig: () -> Unit,
    onOpenBrowseCasualSettings: () -> Unit = onOpenConfig,
    modifier: Modifier = Modifier
) {
    val onGate = remember(intents) {
        CommonIntentsCodec.gateItems(intents)
    }
    val browseSelected = BrowseCasualIntent.isBrowseLike(selectedText)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "常用意图",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = themeConfig.textTertiary.copy(alpha = 0.9f),
                letterSpacing = 0.3.sp
            )
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = enabled,
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onOpenConfig
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "设定常用意图",
                    tint = themeConfig.textSecondary.copy(alpha = if (enabled) 0.9f else 0.4f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CommonIntentChip(
                label = BrowseCasualIntent.DISPLAY_LABEL,
                defaultMinutes = 0,
                selected = browseSelected,
                enabled = enabled,
                themeConfig = themeConfig,
                accent = true,
                onClick = {
                    onSelect(
                        CommonIntentItem(
                            label = BrowseCasualIntent.LABEL,
                            showOnGate = true,
                            defaultMinutes = 0
                        )
                    )
                },
                onLongClick = onOpenBrowseCasualSettings
            )
            onGate.forEach { item ->
                CommonIntentChip(
                    label = item.label,
                    defaultMinutes = item.defaultMinutes,
                    selected = selectedText.trim() == item.label,
                    enabled = enabled,
                    themeConfig = themeConfig,
                    accent = !item.deepLinkId.isNullOrBlank(),
                    onClick = { onSelect(item) }
                )
            }
        }
        if (onGate.isEmpty()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "还没有钉在门上的意图 · 点右上角设定",
                fontSize = 12.sp,
                color = themeConfig.textTertiary.copy(alpha = 0.75f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled, onClick = onOpenConfig)
                    .padding(vertical = 8.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun CommonIntentChip(
    label: String,
    defaultMinutes: Int,
    selected: Boolean,
    enabled: Boolean,
    themeConfig: InterceptThemeConfig,
    accent: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    @Suppress("UNUSED_PARAMETER")
    val absorb = onLongClick
    val display = if (defaultMinutes > 0) "$label ${defaultMinutes}′" else label
    Text(
        text = display,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
        color = when {
            !enabled -> themeConfig.textTertiary.copy(alpha = 0.4f)
            selected -> themeConfig.accentColor
            else -> themeConfig.textSecondary
        },
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .heightIn(min = 40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    selected -> themeConfig.accentColor.copy(alpha = 0.16f)
                    accent -> themeConfig.accentColor.copy(alpha = 0.08f)
                    else -> themeConfig.bgColor.copy(alpha = 0.35f)
                }
            )
            .border(
                width = 1.dp,
                color = when {
                    selected -> themeConfig.accentColor.copy(alpha = 0.55f)
                    accent -> themeConfig.accentColor.copy(alpha = 0.28f)
                    else -> themeConfig.dividerColor.copy(alpha = 0.4f)
                },
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    )
}

/**
 * 拦截页内：常用意图管理（全屏）。
 *
 * 形态：系统「随意浏览」→ 我的常用（统一列表）→ 可直达动作模板（已添加标出）→ 底栏自定义添加。
 * 深链动作以模板加入常用意图，绑定 deepLinkId；进入后按 id 直达。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CommonIntentsConfigPanel(
    themeConfig: InterceptThemeConfig,
    intents: List<CommonIntentItem>,
    browsePolicy: BrowseCasualPolicy,
    onSave: (List<CommonIntentItem>) -> Unit,
    onSaveBrowsePolicy: (BrowseCasualPolicy) -> Unit,
    onClose: () -> Unit,
    packageName: String = "",
    /** 返回 true 表示已消费（如关掉「随意浏览」设定子页） */
    onBackHandlerChange: ((() -> Boolean)?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var draft by remember(intents) {
        mutableStateOf(
            intents.filterNot { BrowseCasualIntent.isBrowseLike(it.label) }
        )
    }
    var policy by remember(browsePolicy) { mutableStateOf(browsePolicy) }
    var showBrowseSettings by remember { mutableStateOf(false) }
    var input by remember { mutableStateOf("") }
    var addError by remember { mutableStateOf<String?>(null) }
    val canAdd = CommonIntentsCodec.normalizeLabel(input).isNotEmpty() &&
        draft.size < CommonIntentsCodec.MAX_ITEMS
    val onGateCount = CommonIntentsCodec.countOnGate(draft)
    val deepLinkActions = remember(packageName) {
        AppDeepLinkCatalog.forPackage(packageName)
    }
    val addedDeepLinkIds = remember(draft) {
        draft.mapNotNull { CommonIntentsCodec.sanitizeDeepLinkId(it.deepLinkId) }.toSet()
    }

    androidx.compose.runtime.DisposableEffect(showBrowseSettings) {
        onBackHandlerChange {
            if (showBrowseSettings) {
                showBrowseSettings = false
                true
            } else {
                false
            }
        }
        onDispose { onBackHandlerChange(null) }
    }

    if (showBrowseSettings) {
        BrowseCasualSettingsPanel(
            themeConfig = themeConfig,
            policy = policy,
            onSave = {
                policy = it
                onSaveBrowsePolicy(it)
            },
            onClose = { showBrowseSettings = false }
        )
        return
    }

    fun persist(next: List<CommonIntentItem>) {
        val cleaned = next.filterNot { BrowseCasualIntent.isBrowseLike(it.label) }
        val (enriched, _) = AppDeepLinkCatalog.enrichCommonIntents(packageName, cleaned)
        draft = enriched
        onSave(enriched)
    }

    fun tryAddLabel(
        raw: String,
        showOnGate: Boolean = true,
        deepLinkId: String? = null
    ): Boolean {
        val label = CommonIntentsCodec.normalizeLabel(raw)
        if (label.isEmpty() || draft.size >= CommonIntentsCodec.MAX_ITEMS) {
            if (draft.size >= CommonIntentsCodec.MAX_ITEMS) {
                addError = "常用意图最多 ${CommonIntentsCodec.MAX_ITEMS} 条"
            }
            return false
        }
        if (BrowseCasualIntent.isBrowseLike(label)) {
            addError = "「看看 / 刷刷」请用系统意图「${BrowseCasualIntent.DISPLAY_LABEL}」，可在上方设定限额与时段"
            return false
        }
        if (draft.any { it.label.equals(label, ignoreCase = true) }) {
            addError = "已有相同意图"
            return false
        }
        val boundId = CommonIntentsCodec.sanitizeDeepLinkId(deepLinkId)
        if (boundId != null && draft.any {
                CommonIntentsCodec.sanitizeDeepLinkId(it.deepLinkId) == boundId
            }
        ) {
            addError = "该动作已在常用里"
            return false
        }
        val pin = showOnGate && CommonIntentsCodec.canPinMore(draft)
        addError = null
        persist(
            draft + CommonIntentItem(
                label = label,
                showOnGate = pin,
                deepLinkId = boundId
            )
        )
        return true
    }

    fun tryAdd() {
        if (tryAddLabel(input)) input = ""
    }

    fun tryAddDeepLink(entry: AppDeepLinkEntry) {
        if (addedDeepLinkIds.contains(entry.id)) return
        tryAddLabel(
            raw = entry.displayName,
            showOnGate = true,
            deepLinkId = entry.id
        )
    }

    fun setShowOnGate(index: Int, on: Boolean) {
        if (on && !CommonIntentsCodec.canPinMore(draft) && !draft[index].showOnGate) {
            addError = "门上最多 ${CommonIntentsCodec.MAX_GATE_VISIBLE} 条"
            return
        }
        addError = null
        persist(
            draft.toMutableList().also { list ->
                list[index] = list[index].copy(showOnGate = on)
            }
        )
    }

    fun setDefaultMinutes(index: Int, minutes: Int) {
        addError = null
        persist(
            draft.toMutableList().also { list ->
                list[index] = list[index].copy(
                    defaultMinutes = CommonIntentsCodec.sanitizeDefaultMinutes(minutes)
                )
            }
        )
    }

    val defaultMinuteOptions = listOf(0, 5, 10, 15, 20)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeConfig.bgColor)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .clickable(
                            indication = null,
                            interactionSource = remember { MutableInteractionSource() },
                            onClick = onClose
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "返回",
                        tint = themeConfig.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Text(
                    text = "常用意图",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeConfig.textPrimary,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp)
                )
                Text(
                    text = "门上 $onGateCount/${CommonIntentsCodec.MAX_GATE_VISIBLE}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = themeConfig.accentColor,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(themeConfig.accentColor.copy(alpha = 0.14f))
                        .border(
                            1.dp,
                            themeConfig.accentColor.copy(alpha = 0.38f),
                            RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (deepLinkActions.isNotEmpty()) {
                    "打开「门上」会出现在拦截页。绑定了可直达的，点选进入后落到对应页面；自己手写的近因也会出现在门上。"
                } else {
                    "打开「门上」的意图会出现在拦截页。自己写过的近因也会出现；最多钉 ${CommonIntentsCodec.MAX_GATE_VISIBLE} 条常用。"
                },
                fontSize = 13.sp,
                color = themeConfig.textSecondary,
                lineHeight = 19.sp,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))
            BrowseCasualManageCard(
                themeConfig = themeConfig,
                policy = policy,
                onClick = { showBrowseSettings = true }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                contentPadding = PaddingValues(top = 22.dp, bottom = 12.dp)
            ) {
                item {
                    SectionHeader(
                        title = "我的常用",
                        trailing = "${draft.size}/${CommonIntentsCodec.MAX_ITEMS}",
                        themeConfig = themeConfig
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (draft.isEmpty()) {
                    item {
                        Text(
                            text = if (deepLinkActions.isNotEmpty()) {
                                "还没有常用条目\n可从下方「可直达动作」加入，或自定义一条"
                            } else {
                                "还没有自定义条目，在底部添加一条吧"
                            },
                            fontSize = 14.sp,
                            color = themeConfig.textTertiary,
                            lineHeight = 21.sp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 28.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }

                itemsIndexed(
                    draft,
                    key = { index, item ->
                        "${item.deepLinkId.orEmpty()}|$index|${item.label}"
                    }
                ) { index, item ->
                    val deepLink = AppDeepLinkCatalog.resolveForCommonIntent(packageName, item)
                    CommonIntentManageCard(
                        themeConfig = themeConfig,
                        item = item,
                        deepLink = deepLink,
                        defaultMinuteOptions = defaultMinuteOptions,
                        onShowOnGateChange = { setShowOnGate(index, it) },
                        onDefaultMinutesChange = { setDefaultMinutes(index, it) },
                        onRemove = {
                            persist(draft.toMutableList().also { it.removeAt(index) })
                        }
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }

                if (deepLinkActions.isNotEmpty()) {
                    item {
                        Spacer(modifier = Modifier.height(10.dp))
                        SectionHeader(
                            title = "可直达动作",
                            trailing = "本 App",
                            themeConfig = themeConfig
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "点「加入」即成为一条常用意图；已加入的标为「已添加」。",
                            fontSize = 12.sp,
                            color = themeConfig.textTertiary,
                            lineHeight = 17.sp,
                            modifier = Modifier.padding(bottom = 10.dp, start = 2.dp, end = 2.dp)
                        )
                    }
                    items(
                        count = deepLinkActions.size,
                        key = { deepLinkActions[it].id }
                    ) { i ->
                        val entry = deepLinkActions[i]
                        val already = addedDeepLinkIds.contains(entry.id)
                        DeepLinkActionRow(
                            themeConfig = themeConfig,
                            entry = entry,
                            alreadyAdded = already,
                            enabled = !already && draft.size < CommonIntentsCodec.MAX_ITEMS,
                            onAdd = { tryAddDeepLink(entry) }
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(themeConfig.bgColor.copy(alpha = 0.96f))
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            if (addError != null) {
                Text(
                    text = addError.orEmpty(),
                    fontSize = 12.sp,
                    color = themeConfig.limitAccentColor.copy(alpha = 0.92f),
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = {
                        if (it.length <= CommonIntentsCodec.MAX_LABEL) {
                            input = it
                            addError = null
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 52.dp),
                    singleLine = true,
                    placeholder = {
                        Text(
                            "自定义意图…",
                            color = themeConfig.textTertiary.copy(alpha = 0.8f)
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { tryAdd() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = themeConfig.accentColor.copy(alpha = 0.55f),
                        unfocusedBorderColor = themeConfig.dividerColor.copy(alpha = 0.5f),
                        focusedContainerColor = themeConfig.surfaceColor,
                        unfocusedContainerColor = themeConfig.surfaceColor,
                        cursorColor = themeConfig.accentColor,
                        focusedTextColor = themeConfig.textPrimary,
                        unfocusedTextColor = themeConfig.textPrimary
                    ),
                    shape = RoundedCornerShape(14.dp)
                )
                TextButton(
                    onClick = { tryAdd() },
                    enabled = canAdd,
                    modifier = Modifier.heightIn(min = 52.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = null,
                        tint = if (canAdd) themeConfig.accentColor else themeConfig.textTertiary
                    )
                    Text(
                        text = "添加",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (canAdd) themeConfig.accentColor else themeConfig.textTertiary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    trailing: String,
    themeConfig: InterceptThemeConfig
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeConfig.textTertiary,
            letterSpacing = 0.4.sp
        )
        Text(
            text = trailing,
            fontSize = 11.sp,
            color = themeConfig.textTertiary
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CommonIntentManageCard(
    themeConfig: InterceptThemeConfig,
    item: CommonIntentItem,
    deepLink: AppDeepLinkEntry?,
    defaultMinuteOptions: List<Int>,
    onShowOnGateChange: (Boolean) -> Unit,
    onDefaultMinutesChange: (Int) -> Unit,
    onRemove: () -> Unit
) {
    val landLine = deepLink?.note?.takeIf { it.isNotBlank() } ?: "进入后直达对应页面"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(themeConfig.surfaceColor)
            .border(
                1.dp,
                if (item.showOnGate) themeConfig.accentColor.copy(alpha = 0.35f)
                else themeConfig.dividerColor.copy(alpha = 0.4f),
                RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (deepLink != null) {
                        Box(
                            modifier = Modifier
                                .padding(end = 7.dp)
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(themeConfig.accentColor)
                        )
                    }
                    Text(
                        text = item.label,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = themeConfig.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = when {
                        deepLink != null && item.showOnGate -> "门上 · $landLine"
                        deepLink != null -> landLine
                        item.showOnGate -> "已钉在拦截页"
                        else -> "仅保存在常用池"
                    },
                    fontSize = 12.sp,
                    color = if (item.showOnGate || deepLink != null) {
                        themeConfig.accentColor.copy(alpha = 0.88f)
                    } else {
                        themeConfig.textTertiary
                    },
                    modifier = Modifier.padding(top = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = 16.sp
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "门上",
                    fontSize = 10.sp,
                    color = themeConfig.textTertiary
                )
                Switch(
                    checked = item.showOnGate,
                    onCheckedChange = onShowOnGateChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = themeConfig.accentForeground,
                        checkedTrackColor = themeConfig.accentColor,
                        uncheckedThumbColor = themeConfig.textTertiary,
                        uncheckedTrackColor = themeConfig.dividerColor
                    )
                )
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "删除",
                    tint = themeConfig.textTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "默认时长",
            fontSize = 10.sp,
            color = themeConfig.textTertiary,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            defaultMinuteOptions.forEach { mins ->
                val selected = item.defaultMinutes == mins
                Text(
                    text = if (mins == 0) "无" else "${mins}′",
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (selected) themeConfig.accentColor else themeConfig.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) themeConfig.accentColor.copy(alpha = 0.16f)
                            else themeConfig.bgColor.copy(alpha = 0.35f)
                        )
                        .border(
                            1.dp,
                            if (selected) themeConfig.accentColor.copy(alpha = 0.45f)
                            else themeConfig.dividerColor.copy(alpha = 0.4f),
                            RoundedCornerShape(999.dp)
                        )
                        .clickable { onDefaultMinutesChange(mins) }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun DeepLinkActionRow(
    themeConfig: InterceptThemeConfig,
    entry: AppDeepLinkEntry,
    alreadyAdded: Boolean,
    enabled: Boolean,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(themeConfig.surfaceColor.copy(alpha = if (alreadyAdded) 0.55f else 0.92f))
            .border(
                1.dp,
                themeConfig.dividerColor.copy(alpha = if (alreadyAdded) 0.28f else 0.42f),
                RoundedCornerShape(14.dp)
            )
            .then(
                if (enabled) Modifier.clickable(onClick = onAdd) else Modifier
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.displayName,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (alreadyAdded) {
                    themeConfig.textSecondary.copy(alpha = 0.75f)
                } else {
                    themeConfig.textPrimary
                }
            )
            Text(
                text = entry.note?.takeIf { it.isNotBlank() } ?: "进入后直达对应页面",
                fontSize = 12.sp,
                color = themeConfig.textTertiary,
                lineHeight = 16.sp,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = if (alreadyAdded) "已添加" else "加入",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (alreadyAdded) themeConfig.textTertiary else themeConfig.accentColor,
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .background(
                    if (alreadyAdded) Color.Transparent
                    else themeConfig.accentColor.copy(alpha = 0.14f)
                )
                .border(
                    1.dp,
                    if (alreadyAdded) themeConfig.dividerColor.copy(alpha = 0.4f)
                    else themeConfig.accentColor.copy(alpha = 0.38f),
                    RoundedCornerShape(999.dp)
                )
                .padding(horizontal = 12.dp, vertical = 7.dp)
        )
    }
}

@Composable
private fun BrowseCasualManageCard(
    themeConfig: InterceptThemeConfig,
    policy: BrowseCasualPolicy,
    onClick: () -> Unit
) {
    val limitText = if (policy.dailyLimitMinutes <= 0) {
        "日限额不限"
    } else {
        "日限额 ${policy.dailyLimitMinutes} 分"
    }
    val windowText = when {
        !policy.windowsEnabled -> "时段不限"
        policy.windows.isEmpty() -> "已开时段限制 · 尚未添加"
        else -> "可刷 ${policy.windows.joinToString("、") { it.label() }}"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(themeConfig.accentColor.copy(alpha = 0.10f))
            .border(1.dp, themeConfig.accentColor.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = BrowseCasualIntent.DISPLAY_LABEL,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "$limitText · $windowText",
                fontSize = 12.sp,
                color = themeConfig.textSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 17.sp
            )
        }
        Icon(
            imageVector = Icons.Outlined.Tune,
            contentDescription = "设定${BrowseCasualIntent.DISPLAY_LABEL}",
            tint = themeConfig.accentColor,
            modifier = Modifier.size(22.dp)
        )
    }
}

/**
 * 「随意浏览」专属设定：日限额 + 可刷时段。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun BrowseCasualSettingsPanel(
    themeConfig: InterceptThemeConfig,
    policy: BrowseCasualPolicy,
    onSave: (BrowseCasualPolicy) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    var draft by remember(policy) { mutableStateOf(policy) }
    val limitPresets = listOf(0, 15, 30, 45, 60, 90)

    fun persist(next: BrowseCasualPolicy) {
        draft = next
        onSave(next)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(themeConfig.bgColor)
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onClose
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "返回",
                    tint = themeConfig.textPrimary,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = "${BrowseCasualIntent.DISPLAY_LABEL} · 设定",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.textPrimary,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "这是无明确目的的刷看入口。限额与时段只约束「${BrowseCasualIntent.DISPLAY_LABEL}」，不影响其它意图。",
            fontSize = 14.sp,
            color = themeConfig.textSecondary,
            lineHeight = 21.sp
        )

        Spacer(modifier = Modifier.height(22.dp))
        Text(
            text = "每日可刷时长",
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeConfig.textTertiary
        )
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            limitPresets.forEach { minutes ->
                val selected = draft.dailyLimitMinutes == minutes
                Text(
                    text = if (minutes == 0) "不限" else "${minutes} 分",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) themeConfig.accentColor else themeConfig.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) themeConfig.accentColor.copy(alpha = 0.16f)
                            else themeConfig.surfaceColor
                        )
                        .border(
                            1.dp,
                            if (selected) themeConfig.accentColor.copy(alpha = 0.5f)
                            else themeConfig.dividerColor.copy(alpha = 0.4f),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { persist(draft.copy(dailyLimitMinutes = minutes)) }
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "仅允许在特定时段刷",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeConfig.textPrimary
                )
                Text(
                    text = "关闭则全天可选「${BrowseCasualIntent.DISPLAY_LABEL}」",
                    fontSize = 13.sp,
                    color = themeConfig.textTertiary,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            Switch(
                checked = draft.windowsEnabled,
                onCheckedChange = { on ->
                    val nextWindows = if (on && draft.windows.isEmpty()) {
                        listOf(
                            PeriodWindow(
                                startMinute = 12 * 60,
                                endMinute = 14 * 60,
                                daysMask = PeriodDays.EVERY_DAY
                            ),
                            PeriodWindow(
                                startMinute = 20 * 60,
                                endMinute = 22 * 60,
                                daysMask = PeriodDays.EVERY_DAY
                            )
                        )
                    } else draft.windows
                    persist(draft.copy(windowsEnabled = on, windows = nextWindows))
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = themeConfig.accentForeground,
                    checkedTrackColor = themeConfig.accentColor,
                    uncheckedThumbColor = themeConfig.textTertiary,
                    uncheckedTrackColor = themeConfig.dividerColor
                )
            )
        }

        if (draft.windowsEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            draft.windows.forEachIndexed { index, window ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(themeConfig.surfaceColor)
                        .border(
                            1.dp,
                            themeConfig.dividerColor.copy(alpha = 0.4f),
                            RoundedCornerShape(16.dp)
                        )
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = window.label(),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = themeConfig.textPrimary
                        )
                        Text(
                            text = window.daysLabel(),
                            fontSize = 12.sp,
                            color = themeConfig.textTertiary,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable {
                                persist(
                                    draft.copy(
                                        windows = draft.windows.toMutableList()
                                            .also { it.removeAt(index) }
                                    )
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "删除时段",
                            tint = themeConfig.textTertiary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
            Text(
                text = "+ 添加午间 12–14",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        persist(
                            draft.copy(
                                windows = draft.windows + PeriodWindow(
                                    startMinute = 12 * 60,
                                    endMinute = 14 * 60,
                                    daysMask = PeriodDays.EVERY_DAY
                                )
                            )
                        )
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
            )
            Text(
                text = "+ 添加晚间 20–22",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = themeConfig.accentColor,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        persist(
                            draft.copy(
                                windows = draft.windows + PeriodWindow(
                                    startMinute = 20 * 60,
                                    endMinute = 22 * 60,
                                    daysMask = PeriodDays.EVERY_DAY
                                )
                            )
                        )
                    }
                    .padding(vertical = 10.dp, horizontal = 4.dp)
            )
        }
    }
}
