package com.life.mindfulnessapp.domain.usecase

import android.util.Log
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.network.DeepSeekChatRequest
import com.life.mindfulnessapp.data.network.DeepSeekClient
import com.life.mindfulnessapp.data.network.DeepSeekMessage
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 意图质量分类。
 *
 * MVP 入场路径只用 [classifyLocal]：无网、即时、拦住明显无目的/含糊意图。
 * [classify] 保留「本地快判 + DeepSeek 精判」能力，供后续可选增强。
 */
@Singleton
class IntentPurposeClassifier @Inject constructor() {

    enum class Verdict {
        PURPOSEFUL,
        PURPOSELESS,
        UNCLEAR
    }

    data class Result(
        val verdict: Verdict,
        /** 不通过/含糊时的轻量纠偏文案；通过时为 null */
        val tip: String? = null
    ) {
        val blocksEntry: Boolean
            get() = verdict == Verdict.PURPOSELESS || verdict == Verdict.UNCLEAR
    }

    /** 本地 + 可选云端；入场 MVP 请优先用 [classifyLocal]。 */
    suspend fun classify(purpose: String): Result {
        val text = purpose.trim()
        if (text.isEmpty()) {
            return Result(Verdict.PURPOSELESS, FALLBACK_EMPTY)
        }
        if (text.length < 2) {
            return Result(Verdict.PURPOSELESS, FALLBACK_TOO_SHORT)
        }

        val local = classifyLocal(text)
        if (local.verdict == Verdict.PURPOSELESS) return local

        if (!DeepSeekClient.isConfigured) {
            Log.d(TAG, "DeepSeek 未配置，使用本地规则")
            return local
        }

        return try {
            classifyWithDeepSeek(text) ?: local
        } catch (e: Exception) {
            Log.w(TAG, "DeepSeek 分类失败，回退本地", e)
            local
        }
    }

    /** 同步本地规则：空/过短/黑名单词 → 拦；其余放行。 */
    fun classifyLocal(purpose: String): Result {
        val text = purpose.trim()
        if (text.isEmpty()) return Result(Verdict.PURPOSELESS, FALLBACK_EMPTY)
        if (text.length < 2) return Result(Verdict.PURPOSELESS, FALLBACK_TOO_SHORT)

        val normalized = text
            .lowercase()
            .replace(Regex("[\\s\\p{Punct}]+"), "")

        if (normalized.isEmpty()) {
            return Result(Verdict.PURPOSELESS, FALLBACK_VAGUE)
        }

        if (PURPOSELESS_EXACT.any { it == normalized || it == text.trim() }) {
            return Result(Verdict.PURPOSELESS, tipForVague(text))
        }
        if (PURPOSELESS_CONTAINS.any { normalized.contains(it) } && text.length <= 8) {
            return Result(Verdict.PURPOSELESS, tipForVague(text))
        }
        if (text.length <= 2 && !hasSubstanceHint(text)) {
            return Result(Verdict.PURPOSELESS, FALLBACK_TOO_SHORT)
        }
        if (text.length <= 4 && PURPOSELESS_SOFT.any { normalized.contains(it) }) {
            return Result(Verdict.UNCLEAR, tipForVague(text))
        }
        return Result(Verdict.PURPOSEFUL)
    }

    private suspend fun classifyWithDeepSeek(purpose: String): Result? {
        val response = DeepSeekClient.api.chatCompletions(
            DeepSeekChatRequest(
                model = BuildConfig.DEEPSEEK_MODEL,
                messages = listOf(
                    DeepSeekMessage(role = "system", content = SYSTEM_PROMPT),
                    DeepSeekMessage(role = "user", content = purpose)
                ),
                temperature = 0.2,
                maxTokens = 80
            )
        )
        val raw = response.choices?.firstOrNull()?.message?.content?.trim().orEmpty()
        if (raw.isEmpty()) return null
        return parseModelOutput(raw, purpose)
    }

    private fun parseModelOutput(raw: String, purpose: String): Result? {
        val lines = raw.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return null
        val first = lines.first().uppercase()
            .replace(Regex("[^A-Z_]"), "")
        val tipFromModel = lines.drop(1).joinToString(" ").trim()
            .ifBlank { null }
            ?.take(60)

        return when {
            first.contains("PURPOSELESS") -> Result(
                Verdict.PURPOSELESS,
                tipFromModel ?: tipForVague(purpose)
            )
            first.contains("UNCLEAR") -> Result(
                Verdict.UNCLEAR,
                tipFromModel ?: tipForVague(purpose)
            )
            first.contains("PURPOSEFUL") -> Result(Verdict.PURPOSEFUL)
            else -> {
                Log.d(TAG, "无法解析模型输出: $raw")
                null
            }
        }
    }

    private fun tipForVague(purpose: String): String {
        val t = purpose.trim()
        return when {
            t.contains("看看") || t.contains("随便") || t.contains("无聊") ->
                "「$t」很容易滑进刷屏。试着写清：你真正想完成的那一件事是什么？"
            t.length <= 4 ->
                "再具体一点：要完成什么、找谁、或查什么？"
            else ->
                FALLBACK_VAGUE
        }.take(60)
    }

    private fun hasSubstanceHint(text: String): Boolean =
        text.any { it in '一'..'龥' || it.isLetter() }

    companion object {
        private const val TAG = "IntentClassifier"

        private const val FALLBACK_EMPTY = "先写下一句具体打算，再进入。"
        private const val FALLBACK_TOO_SHORT = "再写清楚一点：这一次要做什么？"
        private const val FALLBACK_VAGUE =
            "这句话还不太像一次具体打算。试着写清：要完成什么、或找谁？"

        private val SYSTEM_PROMPT = """
你是正念 App「心锚」的意图教练。用户即将打开容易分心的 App，刚写下「这一次的意图」。
你的目标是帮助用户写出可执行的具体意图，减少含糊刷看。不要嘲笑用户。

请严格按两行回复：
第1行只能是：PURPOSEFUL 或 PURPOSELESS 或 UNCLEAR
第2行：仅当第1行不是 PURPOSEFUL 时，写一句简短中文纠偏提示（≤40字），推动用户重写意图；PURPOSEFUL 时不要写第2行。

判定：
PURPOSEFUL — 有具体可执行目的（回消息给某人、查时刻、做题等）
PURPOSELESS — 明显随意刷看（看看、无聊、刷一下、随便）
UNCLEAR — 含糊、信息不足

示例用户输入「看看」：
PURPOSELESS
「看看」很容易滑进刷屏。你真正想做的那一件事是什么？
""".trimIndent()

        private val PURPOSELESS_EXACT = setOf(
            "看看", "随便看看", "随便", "无聊", "刷一下", "刷刷", "玩玩",
            "没事", "打发时间", "摸鱼", "消遣", "溜达", "逛逛", "无",
            "没有", "没有目的", "没目的", "不知道", "不晓得", "随意",
            "看一下", "看一眼", "点开看看", "打开看看", "闲逛", "瞎逛",
            "look", "browse", "bored", "idk", "nothing", "just looking"
        )

        private val PURPOSELESS_CONTAINS = listOf(
            "随便看看", "没什么事", "没有目的", "没目的", "打发时间",
            "就是看看", "无聊看看"
        )

        private val PURPOSELESS_SOFT = listOf("看看", "刷", "玩", "逛")
    }
}
