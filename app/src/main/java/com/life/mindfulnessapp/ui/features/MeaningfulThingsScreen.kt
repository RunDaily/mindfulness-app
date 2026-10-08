package com.life.mindfulnessapp.ui.features

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.life.mindfulnessapp.domain.model.AppInfo
import com.life.mindfulnessapp.domain.model.MeaningfulThing
import com.life.mindfulnessapp.domain.model.MeaningfulThingsCatalog
import com.life.mindfulnessapp.overlay.InterceptOverlayEntryPoint
import com.life.mindfulnessapp.ui.theme.DayBg
import com.life.mindfulnessapp.ui.theme.DayBorder
import com.life.mindfulnessapp.ui.theme.DayCardBg
import com.life.mindfulnessapp.ui.theme.DayTextPrimary
import com.life.mindfulnessapp.ui.theme.DayTextSecondary
import com.life.mindfulnessapp.ui.theme.LogoGreen
import com.life.mindfulnessapp.ui.theme.NightBg
import com.life.mindfulnessapp.ui.theme.NightBorder
import com.life.mindfulnessapp.ui.theme.NightCardBg
import com.life.mindfulnessapp.ui.theme.NightTextPrimary
import com.life.mindfulnessapp.ui.theme.NightTextSecondary
import com.life.mindfulnessapp.ui.theme.themeChrome
import com.life.mindfulnessapp.util.AppNameSearch
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 「有意义的事」：预置 / 自定义均可绑定 App。
 * [selectToAct]=true 时点选即回调（拦截页离开并可选打开 App）。
 */
@Composable
fun MeaningfulThingsScreen(
    onNavigateBack: () -> Unit,
    selectToAct: Boolean = false,
    onSelectThing: ((MeaningfulThing) -> Unit)? = null,
) {
    val context = LocalContext.current
    val entry = remember {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            InterceptOverlayEntryPoint::class.java
        )
    }
    val prefs = remember { entry.appPreferences() }
    val getInstalledApps = remember { entry.getInstalledAppsUseCase() }

    val chrome = themeChrome()
    val isDark = chrome.isDark
    val revision by prefs.meaningfulThingsRevision.collectAsState()
    val things = remember(revision) { prefs.getMeaningfulThings() }
    val disabledPresets = remember(revision) { prefs.getDisabledMeaningfulPresetIds() }

    var allApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var appsLoading by remember { mutableStateOf(false) }
    var showEditor by remember { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }
    var editorSeed by remember { mutableStateOf<MeaningfulThing?>(null) }
    /** null=沿用 seed；""=清除；其它=新包名 */
    var pendingBindPkg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        appsLoading = true
        allApps = withContext(Dispatchers.IO) {
            runCatching { getInstalledApps() }.getOrDefault(emptyList())
        }
        appsLoading = false
    }

    val bg = if (isDark) NightBg else DayBg
    val card = if (isDark) NightCardBg else DayCardBg
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val border = if (isDark) NightBorder else DayBorder
    val accent = LogoGreen
    val appMap = remember(allApps) { allApps.associateBy { it.packageName } }

    if (showPicker) {
        MeaningfulAppPicker(
            apps = allApps,
            loading = appsLoading,
            bg = bg,
            card = card,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            border = border,
            accent = accent,
            onClose = { showPicker = false },
            onPick = { app ->
                pendingBindPkg = app.packageName
                showPicker = false
            }
        )
        return
    }

    if (showEditor) {
        MeaningfulThingEditor(
            initial = editorSeed,
            pendingBindPackage = pendingBindPkg,
            appMap = appMap,
            bg = bg,
            card = card,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            border = border,
            accent = accent,
            onPickApp = { showPicker = true },
            onClearBind = { pendingBindPkg = "" },
            onCancel = {
                showEditor = false
                editorSeed = null
                pendingBindPkg = null
            },
            onSave = { title, bindPkg ->
                val existing = editorSeed
                if (existing == null || existing.id.isBlank()) {
                    prefs.addCustomMeaningfulThing(title, bindPkg)
                } else {
                    prefs.updateMeaningfulThing(
                        id = existing.id,
                        title = if (existing.isPreset) null else title,
                        boundPackageName = bindPkg ?: ""
                    )
                }
                showEditor = false
                editorSeed = null
                pendingBindPkg = null
            },
            onDelete = { id ->
                prefs.removeMeaningfulThing(id)
                showEditor = false
                editorSeed = null
                pendingBindPkg = null
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = textPrimary
                )
            }
            Text(
                text = "有意义的事",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (!selectToAct) {
                TextButton(
                    onClick = {
                        editorSeed = null
                        pendingBindPkg = null
                        showEditor = true
                    }
                ) {
                    Text("添加", color = accent, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        MeaningfulThingsListBody(
            things = things,
            disabledPresetIds = disabledPresets,
            appMap = appMap,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
            cardColor = card,
            borderColor = border,
            accent = accent,
            selectToAct = selectToAct,
            onSelect = onSelectThing,
            onEdit = { thing ->
                editorSeed = thing
                pendingBindPkg = null
                showEditor = true
            },
            onRemove = { prefs.removeMeaningfulThing(it) },
            onRestorePreset = { prefs.restoreMeaningfulPreset(it) },
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
    }
}

@Composable
fun MeaningfulThingsListBody(
    things: List<MeaningfulThing>,
    disabledPresetIds: Set<String>,
    appMap: Map<String, AppInfo>,
    textPrimary: Color,
    textSecondary: Color,
    cardColor: Color,
    borderColor: Color,
    accent: Color,
    selectToAct: Boolean,
    onSelect: ((MeaningfulThing) -> Unit)?,
    onEdit: (MeaningfulThing) -> Unit,
    onRemove: (String) -> Unit,
    onRestorePreset: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hiddenPresets = MeaningfulThingsCatalog.PRESETS.filter { it.id in disabledPresetIds }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Text(
                text = if (selectToAct) {
                    "选一件去做。绑了 App 的会直接打开。"
                } else {
                    "预置或自定义都行；可绑定 App，拦截页选中后直达。"
                },
                fontSize = 13.sp,
                color = textSecondary,
                lineHeight = 18.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
        items(things, key = { it.id }) { thing ->
            val bound = thing.boundPackageName?.let { appMap[it] }
            MeaningfulThingRow(
                thing = thing,
                boundAppName = bound?.appName,
                boundIcon = bound?.icon,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                cardColor = cardColor,
                borderColor = borderColor,
                selectToAct = selectToAct,
                onClick = {
                    if (selectToAct) onSelect?.invoke(thing)
                    else onEdit(thing)
                },
                onRemove = { onRemove(thing.id) }
            )
        }
        if (hiddenPresets.isNotEmpty() && !selectToAct) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "已隐藏的预置",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary.copy(alpha = 0.75f)
                )
            }
            items(hiddenPresets, key = { "h_${it.id}" }) { thing ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onRestorePreset(thing.id) }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = thing.title,
                        fontSize = 14.sp,
                        color = textSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    Text("恢复", fontSize = 13.sp, color = accent)
                }
            }
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun MeaningfulThingRow(
    thing: MeaningfulThing,
    boundAppName: String?,
    boundIcon: Drawable?,
    textPrimary: Color,
    textSecondary: Color,
    cardColor: Color,
    borderColor: Color,
    selectToAct: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, borderColor.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .background(cardColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (boundIcon != null) {
            val bmp = remember(boundIcon) { boundIcon.toBitmap(96, 96).asImageBitmap() }
            Image(
                bitmap = bmp,
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 10.dp)
                    .size(36.dp)
                    .clip(RoundedCornerShape(9.dp))
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = thing.title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val sub = when {
                boundAppName != null -> "用 $boundAppName 完成"
                thing.isPreset -> "预置 · 未绑定 App"
                else -> "自定义 · 未绑定 App"
            }
            Text(
                text = sub,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.72f),
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (selectToAct) {
            Text(
                text = if (thing.hasBoundApp) "打开 ›" else "去做 ›",
                fontSize = 13.sp,
                color = textSecondary
            )
        } else {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "移除",
                    tint = textSecondary.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun MeaningfulThingEditor(
    initial: MeaningfulThing?,
    pendingBindPackage: String?,
    appMap: Map<String, AppInfo>,
    bg: Color,
    card: Color,
    textPrimary: Color,
    textSecondary: Color,
    border: Color,
    accent: Color,
    onPickApp: () -> Unit,
    onClearBind: () -> Unit,
    onCancel: () -> Unit,
    onSave: (title: String, bindPackage: String?) -> Unit,
    onDelete: (id: String) -> Unit,
) {
    val isPreset = initial?.isPreset == true
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    val effectivePkg = when (pendingBindPackage) {
        null -> initial?.boundPackageName
        "" -> null
        else -> pendingBindPackage
    }
    val boundApp = effectivePkg?.let { appMap[it] }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCancel) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textPrimary)
            }
            Text(
                text = when {
                    initial == null -> "添加"
                    isPreset -> "编辑预置"
                    else -> "编辑"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = {
                    val t = title.trim()
                    if (isPreset || t.isNotEmpty()) {
                        onSave(if (isPreset) initial!!.title else t, effectivePkg)
                    }
                },
                enabled = isPreset || title.isNotBlank()
            ) {
                Text("保存", color = accent, fontWeight = FontWeight.SemiBold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text("这件事", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textSecondary)
            BasicTextField(
                value = title,
                onValueChange = { if (!isPreset && it.length <= 24) title = it },
                enabled = !isPreset,
                singleLine = true,
                textStyle = TextStyle(fontSize = 16.sp, color = textPrimary),
                cursorBrush = SolidColor(accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .background(card)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                decorationBox = { inner ->
                    Box {
                        if (title.isEmpty()) {
                            Text("例如：晨读半小时", color = textSecondary.copy(alpha = 0.55f), fontSize = 16.sp)
                        }
                        inner()
                    }
                }
            )

            Text("绑定 App（可选）", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = textSecondary)
            Text(
                text = "拦截页选中后，会离开当前门并打开这个 App。",
                fontSize = 12.sp,
                color = textSecondary.copy(alpha = 0.7f),
                lineHeight = 17.sp
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, border.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
                    .background(card)
                    .clickable(onClick = onPickApp)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (boundApp?.icon != null) {
                    val bmp = remember(boundApp.icon) {
                        boundApp.icon!!.toBitmap(96, 96).asImageBitmap()
                    }
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        modifier = Modifier
                            .padding(end = 10.dp)
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = null,
                        tint = textSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.padding(end = 10.dp)
                    )
                }
                Text(
                    text = boundApp?.appName ?: "选择要打开的 App",
                    fontSize = 15.sp,
                    color = if (boundApp != null) textPrimary else textSecondary.copy(alpha = 0.65f),
                    modifier = Modifier.weight(1f)
                )
                if (boundApp != null) {
                    Text(
                        text = "清除",
                        fontSize = 13.sp,
                        color = textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable(onClick = onClearBind)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            if (initial != null && initial.id.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (isPreset) "从列表隐藏此预置" else "删除这件事",
                    fontSize = 14.sp,
                    color = Color(0xFFE74C3C).copy(alpha = 0.9f),
                    modifier = Modifier
                        .clickable { onDelete(initial.id) }
                        .padding(vertical = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun MeaningfulAppPicker(
    apps: List<AppInfo>,
    loading: Boolean,
    bg: Color,
    card: Color,
    textPrimary: Color,
    textSecondary: Color,
    border: Color,
    accent: Color,
    onClose: () -> Unit,
    onPick: (AppInfo) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps
        else apps.filter {
            AppNameSearch.matches(it.appName, it.packageName, q)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = textPrimary)
            }
            Text(
                text = "选择 App",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = textPrimary
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(1.dp, border.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                .background(card)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                null,
                tint = textSecondary.copy(alpha = 0.6f),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            BasicTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                textStyle = TextStyle(fontSize = 15.sp, color = textPrimary),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    Box {
                        if (query.isEmpty()) {
                            Text("搜索应用名", color = textSecondary.copy(alpha = 0.5f), fontSize = 15.sp)
                        }
                        inner()
                    }
                }
            )
        }
        if (loading && apps.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = accent, modifier = Modifier.size(28.dp))
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { it.listKey }) { app ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(card)
                            .clickable { onPick(app) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (app.icon != null) {
                            val bmp = remember(app.icon) {
                                app.icon!!.toBitmap(96, 96).asImageBitmap()
                            }
                            Image(
                                bitmap = bmp,
                                contentDescription = null,
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(border.copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(app.appName.take(1), color = textPrimary)
                            }
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = app.appName,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}
