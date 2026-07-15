package com.life.mindfulnessapp.data.repository

import android.util.Log
import com.life.mindfulnessapp.data.db.dao.FavoriteQuoteDao
import com.life.mindfulnessapp.data.db.entity.FavoriteQuoteEntity
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.RemoteQuote
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

// ════════════════════════════════════════════
//  QuoteRepository
//  ‣ 从后端拉取拦截页名言，内存缓存 + 本地兜底
//  ‣ 缓存有效期 6 小时，超时后下次请求自动刷新
//  ‣ 名言按「日历小时」切换：同一小时内固定同一句，
//    整点自动切换，无论拦截页被打开多少次
// ════════════════════════════════════════════

/** 本地兜底名言（网络不可用时使用） */
val FALLBACK_QUOTES: List<Pair<String, String>> = listOf(
    Pair("注意力是最稀缺的资源。", "卡尔·纽波特"),
    Pair("深度工作比浅层忙碌更有价值。", "卡尔·纽波特"),
    Pair("你的专注是你最宝贵的资产。", ""),
    Pair("慢下来，才能看见更多。", "禅语"),
    Pair("真正的休息是给大脑留白，而非换个刺激。", ""),
    Pair("花时间思考，而不仅仅是消费。", ""),
    Pair("停下来，不是放弃，而是更好地出发。", ""),
    Pair("每一次打开手机，都是一次主动的选择。", ""),
    Pair("此刻的专注，是对未来的最好投资。", ""),
    Pair("克制是一种能力，也是一种自由。", ""),
    Pair("意识到习惯的存在，是改变的第一步。", "查尔斯·杜希格"),
    Pair("你永远无法同时做两件事而都做好。", "朱迪思·西博尔德"),
    Pair("清醒地选择，比随机漫游更有意义。", ""),
    Pair("知道自己在做什么，比做了什么更重要。", ""),
    Pair("自律不是限制，而是对自己最深的爱。", ""),
)

private const val TAG = "QuoteRepository"

/** 缓存有效期（毫秒），6 小时刷新一次远端内容 */
private const val CACHE_TTL_MS = 6 * 60 * 60 * 1000L

@Singleton
class QuoteRepository @Inject constructor(
    private val api: ApiService,
    private val favoriteQuoteDao: FavoriteQuoteDao
) {
    // ── 内存缓存 ──────────────────────────────
    private val cacheMutex = Mutex()
    private var cachedQuotes: List<RemoteQuote> = emptyList()
    private var cacheTimestamp: Long = 0L

    // ── 当天的打乱顺序（以「今天的日期」为种子，每天不同） ──
    private var shuffledForDay: Int = -1          // 上次打乱对应的 dayOfYear
    private var shuffledIndices: List<Int> = emptyList()

    /**
     * 获取当前小时对应的名言。
     * ‣ 同一小时内（0~59 分钟）始终返回同一句，整点后自动切换。
     * ‣ 优先使用内存缓存；缓存过期时尝试刷新；网络失败则降级到本地兜底。
     * @return Pair<名言正文, 作者>
     */
    suspend fun getRandomQuote(): Pair<String, String> = withContext(Dispatchers.IO) {
        val quotes = ensureCache()
        if (quotes.isEmpty()) {
            return@withContext quoteForHour(FALLBACK_QUOTES)
        }
        quoteForHour(quotes.map { Pair(it.content, it.author) })
    }

    /**
     * 预加载名言缓存（App 启动或进入监控模式时调用，静默失败）。
     */
    suspend fun preload() = withContext(Dispatchers.IO) {
        try { ensureCache() } catch (_: Exception) {}
    }

    // ── 收藏相关 ──────────────────────────────

    /** 收藏一条名言 */
    suspend fun addFavorite(content: String, author: String) = withContext(Dispatchers.IO) {
        favoriteQuoteDao.insert(FavoriteQuoteEntity(content = content, author = author))
    }

    /** 取消收藏 */
    suspend fun removeFavorite(content: String) = withContext(Dispatchers.IO) {
        favoriteQuoteDao.deleteByContent(content)
    }

    /** 获取所有收藏（Flow，UI 层实时观察） */
    fun getAllFavorites(): Flow<List<FavoriteQuoteEntity>> =
        favoriteQuoteDao.getAllFavorites()

    /** 检查某条名言是否已被收藏（Flow） */
    fun isFavorite(content: String): Flow<Boolean> =
        favoriteQuoteDao.isFavorite(content)

    // ── 内部：确保缓存有效 ───────────────────

    private suspend fun ensureCache(): List<RemoteQuote> = cacheMutex.withLock {
        val now = System.currentTimeMillis()
        if (cachedQuotes.isNotEmpty() && now - cacheTimestamp < CACHE_TTL_MS) {
            return@withLock cachedQuotes
        }
        // 缓存过期或为空，尝试从后端拉取
        try {
            val resp = api.getQuotes(limit = 200, offset = 0)
            if (resp.success && !resp.data.isNullOrEmpty()) {
                cachedQuotes = resp.data
                cacheTimestamp = now
                // 重置打乱状态，下次调用 quoteForHour 时会重新洗牌
                shuffledForDay = -1
                Log.d(TAG, "名言缓存刷新成功，共 ${cachedQuotes.size} 条")
            }
        } catch (e: Exception) {
            Log.w(TAG, "名言拉取失败，使用缓存或兜底: ${e.message}")
        }
        cachedQuotes
    }

    // ── 内部：按「日历小时」返回对应名言 ────────
    //
    // 算法：
    //   1. 以「今天是今年第几天」为随机种子，对列表做固定洗牌
    //      → 保证每天顺序不同，但同一天内顺序稳定
    //   2. 用「今天已过去的小时数（0~23）」作为下标
    //      → 每小时取一句，整点自动切换
    //
    private fun <T> quoteForHour(quotes: List<T>): T {
        val calendar = java.util.Calendar.getInstance()
        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        val hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY)

        // 每天重新洗牌一次
        if (shuffledForDay != dayOfYear || shuffledIndices.size != quotes.size) {
            shuffledIndices = quotes.indices.toMutableList()
                .also { it.shuffle(java.util.Random(dayOfYear.toLong())) }
            shuffledForDay = dayOfYear
        }

        // 用小时数取模拿下标（超过 24 条时循环回绕）
        val idx = shuffledIndices[hourOfDay % quotes.size]
        return quotes[idx]
    }
}
