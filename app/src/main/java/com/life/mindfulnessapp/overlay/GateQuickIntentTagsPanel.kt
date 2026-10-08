package com.life.mindfulnessapp.overlay

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
import com.life.mindfulnessapp.domain.model.BrowseCasualIntent
import com.life.mindfulnessapp.domain.model.CommonIntentItem
import com.life.mindfulnessapp.domain.model.CommonIntentsCodec
import com.life.mindfulnessapp.domain.model.IntentGateProfiles
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 拦截层内 · 快捷标签管理。
 * 与心锚 App 管理页同数据；形态跟软门墨色，不跳出到主界面。
 */
@Composable
internal fun GateQuickIntentTagsPanel(
    packageName: String,
    appName: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<CommonIntentItem>>(emptyList()) }
    var presetPool by remember { mutableStateOf<List<String>>(emptyList()) }
    var showingAdd by remember { mutableStateOf(false) }
    var hint by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }

    fun refreshFrom(next: List<CommonIntentItem>) {
        items = next
        val owned = next.map { it.label.trim().lowercase() }.toSet()
        presetPool = IntentGateProfiles.quickIntentTags(packageName)
            .map { it.label.trim() }
            .filter { it.isNotEmpty() && it.lowercase() !in owned }
    }

    suspend fun load() {
        val entry = EntryPointAccessors.fromApplication(
            context.applicationContext,
            InterceptOverlayEntryPoint::class.java
        )
        val list = entry.appLimitRepository().getCommonIntents(packageName)
        refreshFrom(list)
        loaded = true
    }

    fun persist(next: List<CommonIntentItem>, toast: String? = null, closeAdd: Boolean = false) {
        scope.launch {
            val entry = EntryPointAccessors.fromApplication(
                context.applicationContext,
                InterceptOverlayEntryPoint::class.java
            )
            withContext(Dispatchers.IO) {
                entry.appLimitRepository().setCommonIntents(packageName, next)
            }
            refreshFrom(next)
            if (closeAdd) showingAdd = false
            hint = toast
        }
    }

    LaunchedEffect(packageName) {
        withContext(Dispatchers.IO) { load() }
    }

    Column(modifier = modifier.fillMaxSize()) {
        if (showingAdd) {
            GateTagsAddBody(
                packageName = packageName,
                items = items,
                presetPool = presetPool,
                hint = hint,
                onClearHint = { hint = null },
                onBack = { showingAdd = false },
                onAddCustom = { raw ->
                    val label = CommonIntentsCodec.normalizeLabel(raw)
                    when {
                        label.isEmpty() -> hint = "写一下标签"
                        BrowseCasualIntent.isBrowseLike(label) ->
                            hint = "刷类意图请走「${BrowseCasualIntent.DISPLAY_LABEL}」"
                        items.any { it.label.equals(label, ignoreCase = true) } ->
                            hint = "已有这个标签"
                        items.size >= CommonIntentsCodec.MAX_ITEMS ->
                            hint = "最多 ${CommonIntentsCodec.MAX_ITEMS} 个"
                        else -> {
                            val pin = CommonIntentsCodec.canPinMore(items)
                            persist(
                                items + AppDeepLinkCatalog.bindDeepLinkOnCreate(
                                    packageName, label, pin
                                ),
                                toast = if (!pin) "已加入，门上已满可稍后打开" else null,
                                closeAdd = true
                            )
                        }
                    }
                },
                onAddPreset = { label ->
                    if (items.size >= CommonIntentsCodec.MAX_ITEMS) {
                        hint = "最多 ${CommonIntentsCodec.MAX_ITEMS} 个"
                    } else {
                        val pin = CommonIntentsCodec.canPinMore(items)
                        persist(
                            items + AppDeepLinkCatalog.bindDeepLinkOnCreate(
                                packageName, label, pin
                            ),
                            toast = if (!pin) "已加入，门上已满可稍后打开" else null,
                            closeAdd = true
                        )
                    }
                },
                onDone = onDone
            )
        } else {
            GateTagsMineBody(
                appName = appName,
                items = items,
                packageName = packageName,
                loaded = loaded,
                hint = hint,
                onClearHint = { hint = null },
                onToggle = { index, on ->
                    if (index !in items.indices) return@GateTagsMineBody
                    if (on && !CommonIntentsCodec.canPinMore(items) && !items[index].showOnGate) {
                        hint = "门上最多 ${CommonIntentsCodec.MAX_GATE_VISIBLE} 个"
                        return@GateTagsMineBody
                    }
                    val next = items.toMutableList()
                    next[index] = next[index].copy(showOnGate = on)
                    persist(next)
                },
                onRemove = { index ->
                    if (index !in items.indices) return@GateTagsMineBody
                    persist(items.toMutableList().also { it.removeAt(index) })
                },
                onMove = { index, delta ->
                    val to = index + delta
                    if (index !in items.indices || to !in items.indices) return@GateTagsMineBody
                    val next = items.toMutableList()
                    val item = next.removeAt(index)
                    next.add(to, item)
                    persist(next)
                },
                onAdd = { showingAdd = true },
                onRestore = {
                    scope.launch {
                        val entry = EntryPointAccessors.fromApplication(
                            context.applicationContext,
                            InterceptOverlayEntryPoint::class.java
                        )
                        withContext(Dispatchers.IO) {
                            entry.appLimitRepository().resetCommonIntentsToPresets(packageName)
                        }
                        load()
                        hint = "已恢复预设"
                    }
                },
                onDone = onDone
            )
        }
    }
}

@Composable
private fun GateTagsMineBody(
    appName: String,
    items: List<CommonIntentItem>,
    packageName: String,
    loaded: Boolean,
    hint: String?,
    onClearHint: () -> Unit,
    onToggle: (Int, Boolean) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
    onRestore: () -> Unit,
    onDone: () -> Unit,
) {
    val onGate = CommonIntentsCodec.countOnGate(items)
    val canAdd = items.size < CommonIntentsCodec.MAX_ITEMS

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "‹ 返回",
            fontSize = 13.sp,
            color = LockInk.mist,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onDone)
                .padding(vertical = 4.dp)
        )
        Text(
            text = "快捷标签",
            modifier = Modifier.padding(top = 10.dp),
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = LockInk.paper
        )
        if (appName.isNotBlank()) {
            Text(
                text = "$appName · 写意图时点一下就填入",
                modifier = Modifier.padding(top = 6.dp),
                fontSize = 12.sp,
                color = LockInk.fog
            )
        }
        Text(
            text = "已启用 $onGate/${CommonIntentsCodec.MAX_GATE_VISIBLE}",
            modifier = Modifier.padding(top = 14.dp),
            fontSize = 12.sp,
            color = LockInk.mist
        )
        if (hint != null) {
            Text(
                text = hint,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(role = Role.Button, onClick = onClearHint),
                fontSize = 12.sp,
                color = LockInk.leafText.copy(alpha = 0.85f)
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            if (loaded && items.isEmpty()) {
                Text(
                    text = "还没有标签",
                    modifier = Modifier.padding(top = 16.dp),
                    fontSize = 14.sp,
                    color = LockInk.fog,
                    lineHeight = 22.sp
                )
            }
            items.forEachIndexed { index, item ->
                val preset = IntentGateProfiles.isPresetLabel(packageName, item.label)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.label,
                            fontSize = 15.sp,
                            color = if (item.showOnGate) LockInk.paper else LockInk.mist
                        )
                        Text(
                            text = AppDeepLinkCatalog.tagManageCaption(
                                packageName,
                                item,
                                if (preset) "预设" else "自制"
                            ),
                            modifier = Modifier.padding(top = 2.dp),
                            fontSize = 12.sp,
                            color = if (!item.deepLinkId.isNullOrBlank() ||
                                AppDeepLinkCatalog.resolveForCommonIntent(packageName, item) != null
                            ) {
                                LockInk.leafText.copy(alpha = 0.85f)
                            } else {
                                LockInk.mist
                            }
                        )
                    }
                    Text(
                        text = "↑",
                        fontSize = 14.sp,
                        color = if (index > 0) LockInk.fog else LockInk.mist.copy(alpha = 0.35f),
                        modifier = Modifier
                            .clickable(
                                enabled = index > 0,
                                role = Role.Button
                            ) { onMove(index, -1) }
                            .padding(4.dp)
                    )
                    Text(
                        text = "↓",
                        fontSize = 14.sp,
                        color = if (index < items.lastIndex) {
                            LockInk.fog
                        } else {
                            LockInk.mist.copy(alpha = 0.35f)
                        },
                        modifier = Modifier
                            .clickable(
                                enabled = index < items.lastIndex,
                                role = Role.Button
                            ) { onMove(index, 1) }
                            .padding(4.dp)
                    )
                    if (!preset) {
                        Text(
                            text = "删除",
                            fontSize = 13.sp,
                            color = LockInk.mist,
                            modifier = Modifier
                                .clickable(role = Role.Button) { onRemove(index) }
                                .padding(4.dp)
                        )
                    }
                    Text(
                        text = if (item.showOnGate) "开" else "关",
                        fontSize = 13.sp,
                        color = if (item.showOnGate) LockInk.leafText else LockInk.mist,
                        modifier = Modifier
                            .clickable(role = Role.Button) {
                                onToggle(index, !item.showOnGate)
                            }
                            .padding(4.dp)
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = if (canAdd) "＋ 添加标签" else "已达上限",
                fontSize = 15.sp,
                color = if (canAdd) LockInk.leafText else LockInk.mist.copy(alpha = 0.45f),
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .clickable(enabled = canAdd, role = Role.Button, onClick = onAdd)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "恢复预设",
                fontSize = 15.sp,
                color = LockInk.mist,
                modifier = Modifier.clickable(role = Role.Button, onClick = onRestore)
            )
            Text(
                text = "完成",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = LockInk.leafText,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDone)
            )
        }
    }
}

@Composable
private fun GateTagsAddBody(
    packageName: String,
    items: List<CommonIntentItem>,
    presetPool: List<String>,
    hint: String?,
    onClearHint: () -> Unit,
    onBack: () -> Unit,
    onAddCustom: (String) -> Unit,
    onAddPreset: (String) -> Unit,
    onDone: () -> Unit,
) {
    var input by remember { mutableStateOf("") }
    val left = (CommonIntentsCodec.MAX_ITEMS - items.size).coerceAtLeast(0)

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "‹ 返回",
            fontSize = 13.sp,
            color = LockInk.mist,
            modifier = Modifier
                .clickable(role = Role.Button, onClick = onBack)
                .padding(vertical = 4.dp)
        )
        Text(
            text = "添加标签",
            modifier = Modifier.padding(top = 10.dp),
            fontFamily = FontFamily.Serif,
            fontSize = 24.sp,
            fontWeight = FontWeight.Medium,
            color = LockInk.paper
        )
        Text(
            text = if (left > 0) "最多再加 $left 个" else "已达上限",
            modifier = Modifier.padding(top = 6.dp),
            fontSize = 12.sp,
            color = LockInk.fog
        )
        if (hint != null) {
            Text(
                text = hint,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable(role = Role.Button, onClick = onClearHint),
                fontSize = 12.sp,
                color = LockInk.leafText.copy(alpha = 0.85f)
            )
        }

        Text(
            text = "手写",
            modifier = Modifier.padding(top = 22.dp),
            fontSize = 11.sp,
            letterSpacing = 0.8.sp,
            color = LockInk.mist
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            BasicTextField(
                value = input,
                onValueChange = {
                    if (it.length <= CommonIntentsCodec.MAX_LABEL) input = it
                },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = LockInk.paper),
                cursorBrush = SolidColor(LockInk.leafText),
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
                            Text("例如：看数据后台", fontSize = 15.sp, color = LockInk.mist)
                        }
                        inner()
                    }
                }
            )
            Text(
                text = "加入",
                fontSize = 14.sp,
                color = if (input.trim().isNotEmpty() && left > 0) {
                    LockInk.leafText
                } else {
                    LockInk.mist.copy(alpha = 0.4f)
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp)
                .height(1.dp)
                .background(LockInk.paper.copy(alpha = 0.18f))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
        ) {
            if (presetPool.isNotEmpty()) {
                Text(
                    text = "来自预设",
                    modifier = Modifier.padding(top = 22.dp, bottom = 4.dp),
                    fontSize = 11.sp,
                    letterSpacing = 0.8.sp,
                    color = LockInk.mist
                )
                presetPool.forEach { label ->
                    val canLand = AppDeepLinkCatalog.findByLabel(packageName, label) != null ||
                        AppDeepLinkCatalog.resolveFromPurpose(packageName, label) != null
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
                            Text(label, fontSize = 15.sp, color = LockInk.paper)
                            if (canLand) {
                                Text(
                                    text = "可直达",
                                    modifier = Modifier.padding(top = 2.dp),
                                    fontSize = 12.sp,
                                    color = LockInk.leafText.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Text(
                            text = if (left > 0) "选取" else "已满",
                            fontSize = 13.sp,
                            color = if (left > 0) LockInk.leafText else LockInk.mist
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = "完成",
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = LockInk.leafText,
                modifier = Modifier.clickable(role = Role.Button, onClick = onDone)
            )
        }
    }
}
