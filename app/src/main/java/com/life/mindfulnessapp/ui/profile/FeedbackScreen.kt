package com.life.mindfulnessapp.ui.profile

import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.life.mindfulnessapp.data.analytics.FeedbackCategory
import com.life.mindfulnessapp.data.analytics.FeedbackThread
import com.life.mindfulnessapp.ui.settings.SettingsViewModel
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
import com.life.mindfulnessapp.util.FeedbackImageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private sealed class ChatEntry {
    abstract val key: String

    data object Welcome : ChatEntry() {
        override val key: String = "welcome"
    }

    data class UserMessage(
        val threadId: Long,
        val text: String,
        val category: String,
        val images: List<String>,
        val timeMs: Long,
        val awaitingReply: Boolean
    ) : ChatEntry() {
        override val key: String = "user_$threadId"
    }

    data class TeamMessage(
        val threadId: Long,
        val text: String,
        val timeMs: Long,
        val isUnread: Boolean
    ) : ChatEntry() {
        override val key: String = "team_$threadId"
    }
}

private fun buildChatTimeline(threads: List<FeedbackThread>): List<ChatEntry> {
    val items = mutableListOf<ChatEntry>()
    items.add(ChatEntry.Welcome)
    val sorted = threads.sortedBy { it.submittedAtMs }
    for (thread in sorted) {
        items.add(
            ChatEntry.UserMessage(
                threadId = thread.id,
                text = thread.content.ifBlank { "（内容未保存在本机）" },
                category = thread.category,
                images = thread.displayImages,
                timeMs = thread.submittedAtMs,
                awaitingReply = !thread.hasReply
            )
        )
        if (thread.hasReply) {
            items.add(
                ChatEntry.TeamMessage(
                    threadId = thread.id,
                    text = thread.reply,
                    timeMs = thread.repliedAtMs,
                    isUnread = thread.isUnreadReply
                )
            )
        }
    }
    return items
}

@Composable
fun FeedbackScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
    openReplyId: Long? = null,
    onOpenReplyHandled: () -> Unit = {},
    onNavigateBack: () -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val chrome = themeChrome()
    val isDarkTheme = chrome.isDark
    val threads by viewModel.feedbackThreads.collectAsState()
    val unreadCount by viewModel.feedbackUnreadCount.collectAsState()

    val bgColor = chrome.bg
    val cardColor = chrome.card
    val textPrimary = chrome.textPrimary
    val textSecondary = chrome.textSecondary
    val borderColor = chrome.border
    val accentGreen = chrome.accent
    val softBlue = if (isDarkTheme) Color(0xFF2A3A48) else Color(0xFFE8F1F8)
    val softBlueSelected = if (isDarkTheme) Color(0xFF314A5C) else Color(0xFFD5E8F4)

    var category by remember { mutableStateOf<FeedbackCategory?>(FeedbackCategory.PROBLEM) }
    var draft by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }
    var showContact by remember { mutableStateOf(false) }
    var imageUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var sending by remember { mutableStateOf(false) }
    var syncing by remember { mutableStateOf(false) }

    val chatEntries = remember(threads) { buildChatTimeline(threads) }
    val listState = rememberLazyListState()
    var scrollNonce by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    var pendingScrollToReplyId by remember { mutableStateOf<Long?>(null) }

    val pickImages = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(FeedbackImageHelper.MAX_IMAGES)
    ) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        imageUris = (imageUris + uris).distinct().take(FeedbackImageHelper.MAX_IMAGES)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshPermissions()
        viewModel.syncFeedbackReplies(notify = false)
    }

    LaunchedEffect(openReplyId) {
        val id = openReplyId ?: return@LaunchedEffect
        pendingScrollToReplyId = id
        viewModel.syncFeedbackReplies(notify = false)
        onOpenReplyHandled()
    }

    LaunchedEffect(pendingScrollToReplyId, chatEntries) {
        val id = pendingScrollToReplyId ?: return@LaunchedEffect
        val teamIdx = chatEntries.indexOfFirst {
            it is ChatEntry.TeamMessage && it.threadId == id
        }
        val userIdx = chatEntries.indexOfFirst {
            it is ChatEntry.UserMessage && it.threadId == id
        }
        val idx = when {
            teamIdx >= 0 -> teamIdx
            userIdx >= 0 -> userIdx
            else -> -1
        }
        if (idx >= 0) {
            listState.animateScrollToItem(idx)
            pendingScrollToReplyId = null
        }
    }

    LaunchedEffect(chatEntries.size, scrollNonce, pendingScrollToReplyId) {
        if (chatEntries.isEmpty()) return@LaunchedEffect
        if (pendingScrollToReplyId != null) return@LaunchedEffect
        listState.animateScrollToItem(chatEntries.lastIndex)
    }

    val canSend = draft.isNotBlank() && !sending
    val sendReady = category != null && draft.isNotBlank() && !sending

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = bgColor,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack, enabled = !sending) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "返回",
                        tint = textPrimary
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 2.dp)) {
                    Text(
                        text = "和小锚聊",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = textPrimary
                    )
                    Text(
                        text = if (unreadCount > 0) "${unreadCount} 条新回复" else "心锚团队 · 真人回复",
                        fontSize = 12.sp,
                        color = if (unreadCount > 0) accentGreen
                        else textSecondary.copy(alpha = 0.55f)
                    )
                }
                IconButton(
                    onClick = {
                        if (syncing) return@IconButton
                        syncing = true
                        scope.launch {
                            viewModel.syncFeedbackRepliesAwait(notify = false)
                            syncing = false
                        }
                    },
                    enabled = !sending && !syncing
                ) {
                    if (syncing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = textSecondary
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "刷新",
                            tint = textSecondary.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 16.dp,
                    vertical = 12.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(chatEntries, key = { it.key }) { entry ->
                    when (entry) {
                        is ChatEntry.Welcome -> WelcomeBubble(
                            textSecondary = textSecondary,
                            cardColor = cardColor,
                            borderColor = borderColor
                        )
                        is ChatEntry.UserMessage -> UserBubble(
                            text = entry.text,
                            category = entry.category,
                            images = entry.images,
                            timeMs = entry.timeMs,
                            awaitingReply = entry.awaitingReply,
                            accentGreen = accentGreen,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                        is ChatEntry.TeamMessage -> {
                            LaunchedEffect(entry.threadId, entry.isUnread) {
                                if (entry.isUnread) {
                                    viewModel.markFeedbackReplyRead(entry.threadId)
                                }
                            }
                            TeamBubble(
                                text = entry.text,
                                timeMs = entry.timeMs,
                                cardColor = cardColor,
                                borderColor = borderColor,
                                accentGreen = accentGreen,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }
                    }
                }
            }

            ChatComposer(
                category = category,
                onCategoryChange = { category = it },
                draft = draft,
                onDraftChange = { if (it.length <= 2000) draft = it },
                contact = contact,
                onContactChange = { if (it.length <= 80) contact = it },
                showContact = showContact,
                onToggleContact = { showContact = !showContact },
                imageUris = imageUris,
                onRemoveImage = { uri -> imageUris = imageUris.filterNot { it == uri } },
                onAddImages = {
                    pickImages.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                sending = sending,
                sendReady = sendReady,
                accentGreen = accentGreen,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                borderColor = borderColor,
                softBlue = softBlue,
                softBlueSelected = softBlueSelected,
                bgColor = bgColor,
                focusManager = focusManager,
                onSend = {
                    if (draft.isBlank()) {
                        Toast.makeText(context, "先写一点内容", Toast.LENGTH_SHORT).show()
                        return@ChatComposer
                    }
                    val selected = category
                    if (selected == null) {
                        Toast.makeText(context, "先选一下：遇到问题还是想法建议", Toast.LENGTH_SHORT).show()
                        return@ChatComposer
                    }
                    if (sending) return@ChatComposer
                    focusManager.clearFocus()
                    sending = true
                    viewModel.submitFeedback(
                        content = draft,
                        contact = contact,
                        category = selected.apiValue,
                        imageUris = imageUris
                    ) { ok, msg ->
                        sending = false
                        if (ok) {
                            draft = ""
                            imageUris = emptyList()
                            scrollNonce++
                            Toast.makeText(context, "已发送", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun WelcomeBubble(
    textSecondary: Color,
    cardColor: Color,
    borderColor: Color
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(cardColor)
                .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "嗨，我是心锚团队。遇到卡住、拦错，或有个想法，都可以直接说。\n真人会看，通常 1–3 个工作日内回复。",
                fontSize = 13.sp,
                color = textSecondary.copy(alpha = 0.75f),
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun UserBubble(
    text: String,
    category: String,
    images: List<String>,
    timeMs: Long,
    awaitingReply: Boolean,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val categoryLabel = FeedbackCategory.fromApi(category)?.label
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End
    ) {
        if (categoryLabel != null) {
            Text(
                text = categoryLabel,
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)
            )
        }
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(16.dp, 16.dp, 4.dp, 16.dp))
                .background(accentGreen)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Column {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    color = Color.White,
                    lineHeight = 22.sp
                )
                if (images.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FeedbackImageStrip(pathsOrUrls = images, onDarkBubble = true)
                }
            }
        }
        Row(
            modifier = Modifier.padding(top = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (awaitingReply) {
                Text(
                    text = "已送达",
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.4f)
                )
            }
            val timeLabel = formatFeedbackTime(timeMs)
            if (timeLabel.isNotBlank()) {
                if (awaitingReply) {
                    Text(
                        text = " · ",
                        fontSize = 11.sp,
                        color = textSecondary.copy(alpha = 0.3f)
                    )
                }
                Text(
                    text = timeLabel,
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun TeamBubble(
    text: String,
    timeMs: Long,
    cardColor: Color,
    borderColor: Color,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(accentGreen.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "锚",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = accentGreen
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.widthIn(max = 280.dp)) {
            Text(
                text = "心锚团队",
                fontSize = 11.sp,
                color = textSecondary.copy(alpha = 0.45f),
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
            )
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    .background(cardColor)
                    .border(1.dp, borderColor.copy(alpha = 0.4f), RoundedCornerShape(4.dp, 16.dp, 16.dp, 16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    color = textPrimary,
                    lineHeight = 22.sp
                )
            }
            val timeLabel = formatFeedbackTime(timeMs)
            if (timeLabel.isNotBlank()) {
                Text(
                    text = timeLabel,
                    fontSize = 11.sp,
                    color = textSecondary.copy(alpha = 0.4f),
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ChatComposer(
    category: FeedbackCategory?,
    onCategoryChange: (FeedbackCategory) -> Unit,
    draft: String,
    onDraftChange: (String) -> Unit,
    contact: String,
    onContactChange: (String) -> Unit,
    showContact: Boolean,
    onToggleContact: () -> Unit,
    imageUris: List<Uri>,
    onRemoveImage: (Uri) -> Unit,
    onAddImages: () -> Unit,
    sending: Boolean,
    sendReady: Boolean,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    borderColor: Color,
    softBlue: Color,
    softBlueSelected: Color,
    bgColor: Color,
    focusManager: androidx.compose.ui.focus.FocusManager,
    onSend: () -> Unit
) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FeedbackCategory.entries.forEach { cat ->
                CategoryChip(
                    category = cat,
                    selected = category == cat,
                    enabled = !sending,
                    softBlue = softBlue,
                    softBlueSelected = softBlueSelected,
                    accentGreen = accentGreen,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    onClick = { onCategoryChange(cat) }
                )
            }
        }

        if (showContact) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = contact,
                onValueChange = onContactChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text("微信、邮箱或手机号（可选）", color = textSecondary.copy(alpha = 0.42f))
                },
                singleLine = true,
                enabled = !sending,
                shape = RoundedCornerShape(10.dp),
                colors = composerFieldColors(accentGreen, textPrimary, borderColor, bgColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() })
            )
        }

        if (imageUris.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            ScreenshotRow(
                uris = imageUris,
                enabled = !sending,
                borderColor = borderColor,
                textSecondary = textSecondary,
                softBlue = softBlue,
                onAdd = onAddImages,
                onRemove = onRemoveImage
            )
        }

        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(
                onClick = {
                    if (imageUris.size < FeedbackImageHelper.MAX_IMAGES) onAddImages()
                    else Toast.makeText(
                        context,
                        "最多 ${FeedbackImageHelper.MAX_IMAGES} 张截图",
                        Toast.LENGTH_SHORT
                    ).show()
                },
                enabled = !sending,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = "添加截图",
                    tint = textSecondary.copy(alpha = 0.65f),
                    modifier = Modifier.size(22.dp)
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .clickable(enabled = !sending, onClick = onToggleContact)
                    .padding(horizontal = 6.dp, vertical = 8.dp)
            ) {
                Text(
                    text = if (showContact) "收起联系方式" else "联系方式",
                    fontSize = 12.sp,
                    color = if (showContact) accentGreen else textSecondary.copy(alpha = 0.55f)
                )
            }
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                placeholder = {
                    Text(
                        "说说遇到什么，或有个想法…",
                        color = textSecondary.copy(alpha = 0.42f),
                        fontSize = 14.sp
                    )
                },
                enabled = !sending,
                shape = RoundedCornerShape(20.dp),
                colors = composerFieldColors(accentGreen, textPrimary, borderColor, bgColor),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() }),
                maxLines = 4
            )
            Spacer(Modifier.width(4.dp))
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(
                        if (sendReady) accentGreen else accentGreen.copy(alpha = 0.35f)
                    )
                    .clickable(enabled = !sending, onClick = onSend),
                contentAlignment = Alignment.Center
            ) {
                if (sending) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = "发送",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
        if (draft.isNotBlank()) {
            Text(
                text = "${draft.length}/2000",
                fontSize = 10.sp,
                color = textSecondary.copy(alpha = 0.32f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, end = 4.dp),
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
private fun CategoryChip(
    category: FeedbackCategory,
    selected: Boolean,
    enabled: Boolean,
    softBlue: Color,
    softBlueSelected: Color,
    accentGreen: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    val icon: ImageVector = when (category) {
        FeedbackCategory.PROBLEM -> Icons.Default.BugReport
        FeedbackCategory.IDEA -> Icons.Default.Lightbulb
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) softBlueSelected else softBlue)
            .border(
                width = if (selected) 1.dp else 0.dp,
                color = if (selected) accentGreen.copy(alpha = 0.5f) else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) accentGreen else textPrimary.copy(alpha = 0.45f),
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = category.label,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) accentGreen else textSecondary.copy(alpha = 0.75f)
        )
    }
}

@Composable
private fun ScreenshotRow(
    uris: List<Uri>,
    enabled: Boolean,
    borderColor: Color,
    textSecondary: Color,
    softBlue: Color,
    onAdd: () -> Unit,
    onRemove: (Uri) -> Unit
) {
    val context = LocalContext.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        uris.forEach { uri ->
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, borderColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            ) {
                val bitmap = remember(uri) {
                    runCatching {
                        context.contentResolver.openInputStream(uri)?.use {
                            BitmapFactory.decodeStream(it)?.asImageBitmap()
                        }
                    }.getOrNull()
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(softBlue))
                }
                if (enabled) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .clickable { onRemove(uri) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "移除",
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }
        if (uris.size < FeedbackImageHelper.MAX_IMAGES) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(softBlue)
                    .border(1.dp, borderColor.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                    .clickable(enabled = enabled, onClick = onAdd),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.AddPhotoAlternate,
                    contentDescription = "添加截图",
                    tint = textSecondary.copy(alpha = 0.55f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun FeedbackImageStrip(
    pathsOrUrls: List<String>,
    onDarkBubble: Boolean = false
) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        pathsOrUrls.take(FeedbackImageHelper.MAX_IMAGES).forEach { path ->
            var bitmap by remember(path) { mutableStateOf<ImageBitmap?>(null) }
            LaunchedEffect(path) {
                bitmap = withContext(Dispatchers.IO) { loadFeedbackImageBitmap(path) }
            }
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (onDarkBubble) Color.White.copy(alpha = 0.15f)
                        else Color(0x11000000)
                    )
            ) {
                val bmp = bitmap
                if (bmp != null) {
                    Image(
                        bitmap = bmp,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private fun loadFeedbackImageBitmap(pathOrUrl: String): ImageBitmap? {
    return runCatching {
        when {
            pathOrUrl.startsWith("http://") || pathOrUrl.startsWith("https://") -> {
                val conn = (URL(pathOrUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    instanceFollowRedirects = true
                }
                conn.inputStream.use { stream ->
                    BitmapFactory.decodeStream(stream)?.asImageBitmap()
                }.also { conn.disconnect() }
            }
            else -> {
                val file = File(pathOrUrl)
                if (file.exists()) BitmapFactory.decodeFile(pathOrUrl)?.asImageBitmap() else null
            }
        }
    }.getOrNull()
}

@Composable
fun UnreadBubble(count: Int, modifier: Modifier = Modifier) {
    val label = if (count > 99) "99+" else count.toString()
    Box(
        modifier = modifier
            .height(18.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(Color(0xFFE74C3C))
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 11.sp
        )
    }
}

@Composable
fun UnreadDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(Color(0xFFE74C3C))
    )
}

@Composable
private fun composerFieldColors(
    accentGreen: Color,
    textPrimary: Color,
    borderColor: Color,
    bgColor: Color
) = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = accentGreen.copy(alpha = 0.6f),
    unfocusedBorderColor = borderColor.copy(alpha = 0.5f),
    focusedTextColor = textPrimary,
    unfocusedTextColor = textPrimary,
    cursorColor = accentGreen,
    focusedContainerColor = bgColor,
    unfocusedContainerColor = bgColor,
    disabledContainerColor = bgColor.copy(alpha = 0.7f)
)

private fun formatFeedbackTime(ms: Long): String {
    if (ms <= 0L) return ""
    return SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(ms))
}
