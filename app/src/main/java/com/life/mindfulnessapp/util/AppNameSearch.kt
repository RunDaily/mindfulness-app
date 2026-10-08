package com.life.mindfulnessapp.util

import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.exception.BadHanyuPinyinOutputFormatCombination
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * 应用名搜索 / 排序：支持中文名、包名、全拼与简拼（如「xhs」匹配「小红书」）。
 */
object AppNameSearch {

    private data class Keys(
        val fullPinyin: String,
        val initials: String
    )

    private val cache = ConcurrentHashMap<String, Keys>()

    private val format: HanyuPinyinOutputFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITHOUT_TONE
    }

    /** 字母序排序键：拼音小写，英文/数字保持小写原文。 */
    fun sortKey(appName: String): String = keysFor(appName).fullPinyin

    fun matches(appName: String, packageName: String, query: String): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true
        if (appName.contains(q, ignoreCase = true)) return true
        if (packageName.contains(q, ignoreCase = true)) return true
        // 搜「分身 / 双开」时也能命中系统分身展示行或主名
        if ((q == "分身" || q == "双开" || q.equals("clone", true) || q.equals("dual", true)) &&
            (appName.contains("分身") || packageName.contains("#system_dual"))
        ) {
            return true
        }

        val qNorm = q.lowercase(Locale.ROOT).replace(" ", "")
        if (qNorm.isEmpty()) return true

        val keys = keysFor(appName)
        return keys.fullPinyin.contains(qNorm) || keys.initials.contains(qNorm)
    }

    private fun keysFor(appName: String): Keys =
        cache.getOrPut(appName) {
            val full = StringBuilder(appName.length * 4)
            val initials = StringBuilder(appName.length)
            for (ch in appName) {
                when {
                    ch.code in 0x4E00..0x9FFF -> {
                        val py = toPinyin(ch)
                        if (py != null) {
                            full.append(py)
                            initials.append(py[0])
                        } else {
                            full.append(ch)
                            initials.append(ch)
                        }
                    }
                    ch.isLetterOrDigit() -> {
                        val lower = ch.lowercaseChar()
                        full.append(lower)
                        initials.append(lower)
                    }
                    else -> {
                        // 忽略空格、符号，便于简拼连续匹配
                    }
                }
            }
            Keys(fullPinyin = full.toString(), initials = initials.toString())
        }

    private fun toPinyin(ch: Char): String? {
        return try {
            PinyinHelper.toHanyuPinyinStringArray(ch, format)?.firstOrNull()
        } catch (_: BadHanyuPinyinOutputFormatCombination) {
            null
        }
    }
}
