package com.life.mindfulnessapp.domain.model

import org.json.JSONArray

/**
 * 用户自定义的意图限制关键词。
 * 开启意图检验后：意图文案包含任一关键词则不能进入。
 */
object IntentBlockKeywords {

    const val MAX_KEYWORDS = 20
    const val MAX_KEYWORD_LENGTH = 12

    /** 配置页一键采用的示例（用户可删改） */
    val SUGGESTIONS: List<String> = listOf(
        "看看", "随便", "无聊", "刷一下", "打发时间"
    )

    fun normalize(raw: String): String =
        raw.trim().take(MAX_KEYWORD_LENGTH)

    fun sanitizeList(keywords: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        for (raw in keywords) {
            val n = normalize(raw)
            if (n.isEmpty()) continue
            // 去重：忽略大小写，保留首次写法
            val key = n.lowercase()
            if (seen.none { it.lowercase() == key }) {
                seen.add(n)
            }
            if (seen.size >= MAX_KEYWORDS) break
        }
        return seen.toList()
    }

    /**
     * 若意图命中限制词，返回命中的那一个（优先更长匹配）；否则 null。
     * 匹配：去空白后包含（忽略大小写）。
     */
    fun findMatch(purpose: String, keywords: List<String>): String? {
        val haystack = purpose.trim().lowercase().replace(Regex("\\s+"), "")
        if (haystack.isEmpty()) return null
        val cleaned = sanitizeList(keywords)
        return cleaned
            .sortedByDescending { it.length }
            .firstOrNull { kw ->
                val needle = kw.lowercase().replace(Regex("\\s+"), "")
                needle.isNotEmpty() && haystack.contains(needle)
            }
    }

    fun tipFor(matchedKeyword: String): String =
        "意图里含有你设的「${matchedKeyword.take(MAX_KEYWORD_LENGTH)}」，换个具体说法再进。"

    fun encode(keywords: List<String>): String {
        val list = sanitizeList(keywords)
        if (list.isEmpty()) return ""
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decode(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            sanitizeList(
                buildList {
                    for (i in 0 until arr.length()) {
                        val s = arr.optString(i, "").trim()
                        if (s.isNotEmpty()) add(s)
                    }
                }
            )
        } catch (_: Exception) {
            // 兼容误存的逗号分隔纯文本
            sanitizeList(json.split(',', '，', '\n').map { it.trim() })
        }
    }

    fun summaryLabel(keywords: List<String>): String {
        val list = sanitizeList(keywords)
        return when {
            list.isEmpty() -> "尚未添加限制词"
            list.size <= 3 -> list.joinToString("、")
            else -> list.take(3).joinToString("、") + " 等 ${list.size} 个"
        }
    }
}
