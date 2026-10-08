package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.FeedbackInboxStore
import com.life.mindfulnessapp.data.analytics.FeedbackThread
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.FeedbackReplyItemDto
import com.life.mindfulnessapp.util.FeedbackReplyNotifier
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 反馈收件箱：提交落本地、拉取开发者回复、可选发通知。
 */
@Singleton
class FeedbackInboxRepository @Inject constructor(
    private val apiService: ApiService,
    private val analyticsRepository: AnalyticsRepository,
    private val inboxStore: FeedbackInboxStore,
    private val notifier: FeedbackReplyNotifier
) {
    private val syncMutex = Mutex()
    @Volatile private var lastSyncAtMs: Long = 0L

    val threads: StateFlow<List<FeedbackThread>> = inboxStore.threads

    fun unreadCount(): Int = inboxStore.unreadCount()

    fun latestReplied(): FeedbackThread? = inboxStore.latestReplied()

    fun getThread(id: Long): FeedbackThread? = inboxStore.getThread(id)

    fun rememberSubmitted(
        id: Long,
        category: String,
        content: String,
        contact: String,
        localImagePaths: List<String> = emptyList()
    ) {
        inboxStore.upsertSubmitted(
            id = id,
            category = category,
            content = content,
            contact = contact,
            localImagePaths = localImagePaths
        )
    }

    fun markReplyRead(id: Long) {
        inboxStore.markReplyRead(id)
        if (inboxStore.unreadCount() == 0) {
            notifier.cancel()
        }
    }

    /**
     * 拉取服务端已回复条目并合并。
     * @param notify 是否对新增回复发本地通知（前台手动刷新可关）
     * @param minIntervalMs 节流，避免进页/服务轮询过密
     */
    suspend fun syncReplies(
        notify: Boolean = true,
        minIntervalMs: Long = 30_000L
    ): Result<Int> = syncMutex.withLock {
        val now = System.currentTimeMillis()
        if (minIntervalMs > 0L && now - lastSyncAtMs < minIntervalMs) {
            return Result.success(0)
        }
        return try {
            val resp = apiService.getFeedbackReplies(analyticsRepository.deviceId())
            if (!resp.success && resp.error != null) {
                return Result.failure(IllegalStateException(resp.error))
            }
            lastSyncAtMs = now
            val remote = resp.items.mapNotNull { it.toThreadOrNull() }
            val newly = inboxStore.mergeRemoteReplies(remote)
            val unread = inboxStore.unreadCount()
            if (notify && newly.isNotEmpty()) {
                notifier.notifyNewReplies(newly, unreadTotal = unread)
                inboxStore.markNotified(newly)
            } else if (newly.isNotEmpty()) {
                // 前台已打开时不弹通知，但仍记已通知，避免稍后重复弹
                inboxStore.markNotified(newly)
            } else if (unread == 0) {
                notifier.cancel()
            }
            Result.success(newly.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun FeedbackReplyItemDto.toThreadOrNull(): FeedbackThread? {
        if (id <= 0L) return null
        val replyText = reply.trim()
        if (replyText.isEmpty()) return null
        return FeedbackThread(
            id = id,
            category = category.ifBlank { "general" },
            content = content,
            reply = replyText,
            submittedAtMs = parseTimestamp(created_at),
            repliedAtMs = parseTimestamp(replied_at).takeIf { it > 0L }
                ?: System.currentTimeMillis(),
            remoteImageUrls = images.map { it.trim() }.filter { it.isNotBlank() }
        )
    }

    private fun parseTimestamp(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        val trimmed = raw.trim()
        trimmed.toLongOrNull()?.let { num ->
            // 秒级时间戳兜底
            return if (num in 1..9_999_999_999L) num * 1000L else num
        }
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (p in patterns) {
            try {
                val fmt = SimpleDateFormat(p, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                    isLenient = true
                }
                fmt.parse(trimmed)?.time?.let { return it }
            } catch (_: Exception) {
                // try next
            }
        }
        return 0L
    }
}
