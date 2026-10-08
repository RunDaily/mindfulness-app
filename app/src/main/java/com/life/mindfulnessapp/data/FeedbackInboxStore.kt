package com.life.mindfulnessapp.data

import android.content.Context
import androidx.core.content.edit
import com.life.mindfulnessapp.data.analytics.FeedbackThread
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 本机反馈收件箱：保存最近提交与开发者回复，供红点、记录页与详情使用。
 * 不做完整工单历史；最多保留 [MAX_THREADS] 条。
 */
@Singleton
class FeedbackInboxStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _threads = MutableStateFlow(loadThreads())
    val threads: StateFlow<List<FeedbackThread>> = _threads

    private val _lastNotifiedKeys = MutableStateFlow(loadNotifiedKeys())

    fun getThreads(): List<FeedbackThread> = _threads.value

    fun getThread(id: Long): FeedbackThread? =
        _threads.value.firstOrNull { it.id == id }

    fun unreadCount(): Int = _threads.value.count { it.isUnreadReply }

    fun latestReplied(): FeedbackThread? =
        _threads.value
            .filter { it.hasReply }
            .maxByOrNull { it.repliedAtMs.takeIf { t -> t > 0L } ?: it.submittedAtMs }

    fun upsertSubmitted(
        id: Long,
        category: String,
        content: String,
        contact: String = "",
        submittedAtMs: Long = System.currentTimeMillis(),
        localImagePaths: List<String> = emptyList()
    ) {
        if (id <= 0L) return
        val current = _threads.value.toMutableList()
        val idx = current.indexOfFirst { it.id == id }
        if (idx >= 0) {
            val old = current[idx]
            current[idx] = old.copy(
                category = category.ifBlank { old.category },
                content = content.ifBlank { old.content },
                contact = contact.ifBlank { old.contact },
                submittedAtMs = if (old.submittedAtMs > 0L) old.submittedAtMs else submittedAtMs,
                localImagePaths = localImagePaths.ifEmpty { old.localImagePaths }
            )
        } else {
            current.add(
                0,
                FeedbackThread(
                    id = id,
                    category = category,
                    content = content,
                    contact = contact,
                    submittedAtMs = submittedAtMs,
                    localImagePaths = localImagePaths
                )
            )
        }
        persist(trim(current))
    }

    /**
     * 用服务端已回复条目合并本地。
     * @return 新出现、尚未发过通知的回复列表（调用方负责发通知）
     */
    fun mergeRemoteReplies(remote: List<FeedbackThread>): List<FeedbackThread> {
        if (remote.isEmpty()) return emptyList()
        val current = _threads.value.associateBy { it.id }.toMutableMap()
        val newlyReplied = mutableListOf<FeedbackThread>()
        val notified = _lastNotifiedKeys.value.toMutableSet()

        for (item in remote) {
            if (item.id <= 0L || !item.hasReply) continue
            val existing = current[item.id]
            val merged = if (existing != null) {
                existing.copy(
                    category = item.category.ifBlank { existing.category },
                    content = item.content.ifBlank { existing.content },
                    reply = item.reply,
                    repliedAtMs = item.repliedAtMs.takeIf { it > 0L }
                        ?: existing.repliedAtMs,
                    remoteImageUrls = item.remoteImageUrls.ifEmpty { existing.remoteImageUrls },
                    localImagePaths = existing.localImagePaths,
                    // 若回复内容变更，重新标为未读
                    readReplyAtMs = when {
                        existing.reply == item.reply && existing.readReplyAtMs > 0L ->
                            existing.readReplyAtMs
                        else -> 0L
                    }
                )
            } else {
                item.copy(readReplyAtMs = 0L)
            }
            val replyChanged = existing == null ||
                existing.reply != merged.reply ||
                existing.repliedAtMs != merged.repliedAtMs
            current[item.id] = merged
            if (replyChanged && merged.notifyKey.isNotBlank() && merged.notifyKey !in notified) {
                newlyReplied.add(merged)
            }
        }

        val sorted = current.values
            .sortedByDescending {
                maxOf(it.repliedAtMs, it.submittedAtMs)
            }
        persist(trim(sorted))
        return newlyReplied
    }

    fun markReplyRead(id: Long) {
        val now = System.currentTimeMillis()
        val updated = _threads.value.map {
            if (it.id == id && it.hasReply && it.readReplyAtMs <= 0L) {
                it.copy(readReplyAtMs = now)
            } else it
        }
        persist(updated)
    }

    fun markNotified(threads: List<FeedbackThread>) {
        if (threads.isEmpty()) return
        val keys = _lastNotifiedKeys.value.toMutableSet()
        threads.forEach { t ->
            if (t.notifyKey.isNotBlank()) keys.add(t.notifyKey)
        }
        while (keys.size > MAX_NOTIFIED_KEYS) {
            keys.remove(keys.first())
        }
        prefs.edit { putStringSet(KEY_NOTIFIED, keys) }
        _lastNotifiedKeys.value = keys
    }

    private fun persist(threads: List<FeedbackThread>) {
        val arr = JSONArray()
        threads.forEach { t ->
            arr.put(
                JSONObject().apply {
                    put(KEY_ID, t.id)
                    put(KEY_CATEGORY, t.category)
                    put(KEY_CONTENT, t.content)
                    put(KEY_CONTACT, t.contact)
                    put(KEY_SUBMITTED_AT, t.submittedAtMs)
                    put(KEY_REPLY, t.reply)
                    put(KEY_REPLIED_AT, t.repliedAtMs)
                    put(KEY_READ_REPLY_AT, t.readReplyAtMs)
                    put(KEY_LOCAL_IMAGES, JSONArray(t.localImagePaths))
                    put(KEY_REMOTE_IMAGES, JSONArray(t.remoteImageUrls))
                }
            )
        }
        prefs.edit { putString(KEY_THREADS, arr.toString()) }
        _threads.value = threads
    }

    private fun loadThreads(): List<FeedbackThread> {
        val raw = prefs.getString(KEY_THREADS, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val id = o.optLong(KEY_ID, 0L)
                    if (id <= 0L) continue
                    add(
                        FeedbackThread(
                            id = id,
                            category = o.optString(KEY_CATEGORY, "general"),
                            content = o.optString(KEY_CONTENT, ""),
                            contact = o.optString(KEY_CONTACT, ""),
                            submittedAtMs = o.optLong(KEY_SUBMITTED_AT, 0L),
                            reply = o.optString(KEY_REPLY, ""),
                            repliedAtMs = o.optLong(KEY_REPLIED_AT, 0L),
                            readReplyAtMs = o.optLong(KEY_READ_REPLY_AT, 0L),
                            localImagePaths = o.optJSONArray(KEY_LOCAL_IMAGES).toStringList(),
                            remoteImageUrls = o.optJSONArray(KEY_REMOTE_IMAGES).toStringList()
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun loadNotifiedKeys(): Set<String> =
        prefs.getStringSet(KEY_NOTIFIED, emptySet())?.toSet() ?: emptySet()

    private fun trim(threads: List<FeedbackThread>): List<FeedbackThread> =
        threads.take(MAX_THREADS)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                val s = optString(i, "").trim()
                if (s.isNotBlank()) add(s)
            }
        }
    }

    companion object {
        private const val PREFS_NAME = "feedback_inbox"
        private const val KEY_THREADS = "threads_json"
        private const val KEY_NOTIFIED = "notified_keys"
        private const val MAX_THREADS = 30
        private const val MAX_NOTIFIED_KEYS = 60

        private const val KEY_ID = "id"
        private const val KEY_CATEGORY = "category"
        private const val KEY_CONTENT = "content"
        private const val KEY_CONTACT = "contact"
        private const val KEY_SUBMITTED_AT = "submitted_at"
        private const val KEY_REPLY = "reply"
        private const val KEY_REPLIED_AT = "replied_at"
        private const val KEY_READ_REPLY_AT = "read_reply_at"
        private const val KEY_LOCAL_IMAGES = "local_images"
        private const val KEY_REMOTE_IMAGES = "remote_images"
    }
}
