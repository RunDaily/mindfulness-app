package com.life.mindfulnessapp.data.analytics

/**
 * 本机反馈线程（轻量闭环，非完整工单）。
 * 提交后先落本地；服务端回复后通过拉取合并 [reply]。
 */
data class FeedbackThread(
    val id: Long,
    val category: String,
    val content: String,
    val contact: String = "",
    val submittedAtMs: Long = System.currentTimeMillis(),
    val reply: String = "",
    val repliedAtMs: Long = 0L,
    /** 用户在 App 内打开回复详情的时间；0 = 未读 */
    val readReplyAtMs: Long = 0L,
    /** 本机压缩后的截图路径（用于预览；重装后可能失效） */
    val localImagePaths: List<String> = emptyList(),
    /** 服务端回传的图片 URL / 可展示地址（管理台同步） */
    val remoteImageUrls: List<String> = emptyList(),
) {
    val hasReply: Boolean get() = reply.isNotBlank()

    val isUnreadReply: Boolean get() = hasReply && readReplyAtMs <= 0L

    val displayImages: List<String>
        get() = localImagePaths.filter { it.isNotBlank() }
            .ifEmpty { remoteImageUrls.filter { it.isNotBlank() } }

    /** 用于去重通知：同一回复内容只提醒一次 */
    val notifyKey: String
        get() = if (!hasReply) "" else "$id:$repliedAtMs:${reply.hashCode()}"
}
