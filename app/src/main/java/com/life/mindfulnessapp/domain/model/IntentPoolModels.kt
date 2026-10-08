package com.life.mindfulnessapp.domain.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** 用户为某 App 定义的意图分类 */
data class IntentCategory(
    val id: String,
    val name: String,
    val emoji: String? = null,
    val sortOrder: Int = 0
)

/** 意图池条目：展示与统计的基本单元 */
data class IntentEntry(
    val id: String,
    val displayName: String,
    val categoryId: String? = null,
    val isHidden: Boolean = false,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/** raw purpose 文案 → 意图条目 */
data class IntentAlias(
    val rawPurpose: String,
    val entryId: String
)

data class IntentPoolConfig(
    val entries: List<IntentEntry> = emptyList(),
    val aliases: List<IntentAlias> = emptyList(),
    val categories: List<IntentCategory> = emptyList()
)

enum class IntentPoolSort {
    Duration,
    Count,
    Recent
}

enum class IntentPoolTimeScope {
    All,
    Today
}

data class IntentPoolItem(
    val entry: IntentEntry,
    val aliases: List<String>,
    val totalSeconds: Long,
    val todaySeconds: Long,
    val useCount: Int,
    val firstUsedAt: Long,
    val lastUsedAt: Long,
    val shareOfMindful: Float,
    val category: IntentCategory?
)

data class CategoryBreakdown(
    val category: IntentCategory?,
    val totalSeconds: Long,
    val entryCount: Int,
    val share: Float
)

data class IntentPoolSnapshot(
    val items: List<IntentPoolItem>,
    val hiddenCount: Int,
    val uncategorizedCount: Int,
    val categoryBreakdown: List<CategoryBreakdown>,
    val totalMindfulSeconds: Long,
    val totalEntryCount: Int,
    val timeScope: IntentPoolTimeScope,
    val sort: IntentPoolSort
)

data class IntentPoolItemDetail(
    val item: IntentPoolItem,
    val sessions: List<IntentPoolSession>
)

data class IntentPoolSession(
    val recordId: Long,
    val purpose: String,
    val startTime: Long,
    val endTime: Long,
    val durationSeconds: Long
)

object IntentPoolCodec {
    const val MAX_CATEGORIES = 12
    const val MAX_DISPLAY_NAME = 40

    val categoryTemplates: List<Pair<String, String>> = listOf(
        "工作" to "💼",
        "社交" to "👥",
        "学习" to "📚",
        "消遣" to "🎮",
        "其他" to "📌"
    )

    fun decode(json: String?): IntentPoolConfig {
        if (json.isNullOrBlank()) return IntentPoolConfig()
        return try {
            val root = JSONObject(json)
            IntentPoolConfig(
                entries = decodeEntries(root.optJSONArray("entries")),
                aliases = decodeAliases(root.optJSONArray("aliases")),
                categories = decodeCategories(root.optJSONArray("categories"))
            )
        } catch (_: Exception) {
            IntentPoolConfig()
        }
    }

    fun encode(config: IntentPoolConfig): String {
        if (config.entries.isEmpty() && config.aliases.isEmpty() && config.categories.isEmpty()) {
            return ""
        }
        return JSONObject()
            .put("entries", encodeEntries(config.entries))
            .put("aliases", encodeAliases(config.aliases))
            .put("categories", encodeCategories(config.categories))
            .toString()
    }

    fun normalizePurpose(raw: String): String = raw.trim().take(MAX_DISPLAY_NAME)

    fun newEntryId(): String = UUID.randomUUID().toString()

    fun newCategoryId(): String = UUID.randomUUID().toString()

    private fun decodeEntries(arr: JSONArray?): List<IntentEntry> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("displayName").trim()
                if (name.isEmpty()) continue
                add(
                    IntentEntry(
                        id = o.optString("id").ifBlank { newEntryId() },
                        displayName = name.take(MAX_DISPLAY_NAME),
                        categoryId = o.optString("categoryId").trim().ifBlank { null },
                        isHidden = o.optBoolean("isHidden", false),
                        sortOrder = o.optInt("sortOrder", 0),
                        createdAt = o.optLong("createdAt", System.currentTimeMillis()),
                        updatedAt = o.optLong("updatedAt", System.currentTimeMillis())
                    )
                )
            }
        }
    }

    private fun decodeAliases(arr: JSONArray?): List<IntentAlias> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val raw = normalizePurpose(o.optString("rawPurpose"))
                val entryId = o.optString("entryId").trim()
                if (raw.isEmpty() || entryId.isEmpty()) continue
                add(IntentAlias(rawPurpose = raw, entryId = entryId))
            }
        }
    }

    private fun decodeCategories(arr: JSONArray?): List<IntentCategory> {
        if (arr == null) return emptyList()
        return buildList {
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val name = o.optString("name").trim()
                if (name.isEmpty()) continue
                add(
                    IntentCategory(
                        id = o.optString("id").ifBlank { newCategoryId() },
                        name = name.take(16),
                        emoji = o.optString("emoji").trim().ifBlank { null },
                        sortOrder = o.optInt("sortOrder", 0)
                    )
                )
            }
        }
    }

    private fun encodeEntries(entries: List<IntentEntry>): JSONArray {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("displayName", e.displayName)
                    .put("categoryId", e.categoryId ?: JSONObject.NULL)
                    .put("isHidden", e.isHidden)
                    .put("sortOrder", e.sortOrder)
                    .put("createdAt", e.createdAt)
                    .put("updatedAt", e.updatedAt)
            )
        }
        return arr
    }

    private fun encodeAliases(aliases: List<IntentAlias>): JSONArray {
        val arr = JSONArray()
        aliases.forEach { a ->
            arr.put(
                JSONObject()
                    .put("rawPurpose", a.rawPurpose)
                    .put("entryId", a.entryId)
            )
        }
        return arr
    }

    private fun encodeCategories(categories: List<IntentCategory>): JSONArray {
        val arr = JSONArray()
        categories.forEach { c ->
            arr.put(
                JSONObject()
                    .put("id", c.id)
                    .put("name", c.name)
                    .put("emoji", c.emoji ?: JSONObject.NULL)
                    .put("sortOrder", c.sortOrder)
            )
        }
        return arr
    }
}
