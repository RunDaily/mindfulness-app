package com.life.mindfulnessapp.ui.applist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
import com.life.mindfulnessapp.data.deeplink.AppDeepLinkEntry
import com.life.mindfulnessapp.data.repository.AppLimitRepository
import com.life.mindfulnessapp.domain.model.CommonIntentItem
import com.life.mindfulnessapp.ui.theme.LogoGreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 方案 · 本 App「直达一览」：哪些标签能落到固定页，哪些只填意图。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeepLinkGlanceScreen(
    packageName: String,
    appName: String,
    onNavigateBack: () -> Unit,
    viewModel: DeepLinkGlanceViewModel = hiltViewModel()
) {
    val cs = MaterialTheme.colorScheme
    var catalog by remember { mutableStateOf<List<AppDeepLinkEntry>>(emptyList()) }
    var plainTags by remember { mutableStateOf<List<CommonIntentItem>>(emptyList()) }
    var resolvedName by remember { mutableStateOf(appName) }

    LaunchedEffect(packageName, appName) {
        viewModel.load(packageName, appName) { name, entries, plains ->
            resolvedName = name
            catalog = entries
            plainTags = plains
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回",
                            tint = cs.onSurface.copy(alpha = 0.7f)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = cs.background
                )
            )
        },
        containerColor = cs.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = 22.dp, vertical = 8.dp)
        ) {
            item {
                Text(
                    text = resolvedName.ifBlank { "本 App" },
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.42f),
                    letterSpacing = 0.4.sp
                )
                Text(
                    text = "直达一览",
                    modifier = Modifier.padding(top = 6.dp),
                    fontFamily = FontFamily.Serif,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Medium,
                    color = cs.onSurface.copy(alpha = 0.92f)
                )
                Text(
                    text = "点带叶点的标签进入后，会尽量落到对应页。非官方契约，版本更新可能失效。",
                    modifier = Modifier.padding(top = 8.dp, bottom = 18.dp),
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = cs.onSurface.copy(alpha = 0.45f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    Text("●  可直达", fontSize = 12.sp, color = LogoGreen)
                    Text("○  仅填意图", fontSize = 12.sp, color = cs.onSurface.copy(alpha = 0.4f))
                }
            }

            if (catalog.isEmpty() && plainTags.isEmpty()) {
                item {
                    Text(
                        text = "这个 App 暂无固定页深链目录；门口标签只会填意图文案。",
                        modifier = Modifier.padding(top = 24.dp),
                        fontSize = 14.sp,
                        color = cs.onSurface.copy(alpha = 0.5f),
                        lineHeight = 22.sp
                    )
                }
            }

            items(catalog, key = { it.id }) { entry ->
                DeepLinkGlanceRow(
                    title = entry.displayName,
                    meta = entry.note
                        ?.substringBefore("（")
                        ?.trim()
                        ?.takeIf { it.isNotEmpty() }
                        ?: "对应页",
                    mark = "可直达",
                    markAccent = true
                )
            }

            if (plainTags.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "你的其它标签",
                        fontSize = 11.sp,
                        letterSpacing = 0.8.sp,
                        color = cs.onSurface.copy(alpha = 0.4f),
                        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                    )
                }
                items(plainTags, key = { "plain_${it.label}" }) { item ->
                    DeepLinkGlanceRow(
                        title = item.label,
                        meta = "暂无可靠链",
                        mark = "仅填意图",
                        markAccent = false
                    )
                }
            }

            item { Spacer(Modifier.height(40.dp)) }
        }
    }
}

@Composable
private fun DeepLinkGlanceRow(
    title: String,
    meta: String,
    mark: String,
    markAccent: Boolean
) {
    val cs = MaterialTheme.colorScheme
    Column(modifier = Modifier.fillMaxWidth()) {
        HorizontalDivider(color = cs.outline.copy(alpha = 0.12f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                Text(
                    text = title,
                    fontSize = 15.sp,
                    color = cs.onSurface.copy(alpha = 0.9f)
                )
                Text(
                    text = meta,
                    fontSize = 12.sp,
                    color = cs.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Text(
                text = mark,
                fontSize = 12.sp,
                color = if (markAccent) LogoGreen else cs.onSurface.copy(alpha = 0.4f)
            )
        }
    }
}

@HiltViewModel
class DeepLinkGlanceViewModel @Inject constructor(
    private val appLimitRepository: AppLimitRepository
) : ViewModel() {

    fun load(
        packageName: String,
        appName: String,
        onReady: (String, List<AppDeepLinkEntry>, List<CommonIntentItem>) -> Unit
    ) {
        viewModelScope.launch {
            val entity = appLimitRepository.getAppLimit(packageName)
            val name = appName.ifBlank { entity?.appName.orEmpty() }
            val entries = AppDeepLinkCatalog.forPackage(packageName)
            val entryIds = entries.map { it.id }.toSet()
            val entryNames = entries.map { it.displayName.lowercase() }.toSet()
            val plains = appLimitRepository.getCommonIntents(packageName)
                .filter { item ->
                    val id = item.deepLinkId?.trim().orEmpty()
                    when {
                        id.isNotEmpty() && id in entryIds -> false
                        item.label.trim().lowercase() in entryNames -> false
                        AppDeepLinkCatalog.resolveForCommonIntent(packageName, item) != null -> false
                        else -> true
                    }
                }
            onReady(name, entries, plains)
        }
    }
}
