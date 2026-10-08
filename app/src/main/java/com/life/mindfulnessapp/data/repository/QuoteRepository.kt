package com.life.mindfulnessapp.data.repository

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.CachedAuthor
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.AuthorDeviceRequest
import com.life.mindfulnessapp.data.network.AuthorNoticesReadBody
import com.life.mindfulnessapp.data.network.AuthorRequestBody
import com.life.mindfulnessapp.data.network.AuthorRequestResult
import com.life.mindfulnessapp.data.network.RemoteAuthor
import com.life.mindfulnessapp.data.network.RemoteQuote
import com.life.mindfulnessapp.util.AuthorUpdateNotifier
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** 拦截页展示用名言；id=0 为兜底，authorId=0 不可订阅名人 */
data class DisplayQuote(
    val id: Int = 0,
    val content: String,
    val author: String = "",
    val authorId: Int = 0
)

val FALLBACK_QUOTES: List<DisplayQuote> = listOf(
    DisplayQuote(content = "注意力是最稀缺的资源。", author = "卡尔·纽波特"),
    DisplayQuote(content = "深度工作比浅层忙碌更有价值。", author = "卡尔·纽波特"),
    DisplayQuote(content = "你的专注是你最宝贵的资产。"),
    DisplayQuote(content = "慢下来，才能看见更多。", author = "禅语"),
    DisplayQuote(content = "真正的休息是给大脑留白，而非换个刺激。"),
    DisplayQuote(content = "每一次打开手机，都是一次主动的选择。"),
    DisplayQuote(content = "此刻的专注，是对未来的最好投资。"),
    DisplayQuote(content = "克制是一种能力，也是一种自由。"),
    DisplayQuote(content = "意识到习惯的存在，是改变的第一步。", author = "查尔斯·杜希格"),
    DisplayQuote(content = "清醒地选择，比随机漫游更有意义。"),
    DisplayQuote(content = "自律不是限制，而是对自己最深的爱。"),
)

/** 精选公共池：专注 / 时间管理 / 成长 / 正念 / 数字健康 */
private val CURATED_CATEGORIES = setOf("专注", "时间管理", "成长", "正念", "数字健康", "亲密关系")

private const val TAG = "QuoteRepository"
private const val CATALOG_TTL_MS = 6 * 60 * 60 * 1000L

@Singleton
class QuoteRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: ApiService,
    private val prefs: AppPreferences,
    private val authorUpdateNotifier: AuthorUpdateNotifier
) {
    private val cacheMutex = Mutex()
    /** 当前生效的展示池（已按优先规则选好） */
    private var cachedQuotes: List<RemoteQuote> = emptyList()
    private var cachedSubscribed: List<RemoteQuote> = emptyList()
    private var cachedCurated: List<RemoteQuote> = emptyList()
    private var cacheTimestamp: Long = 0L
    private var cacheSourceKey: String = ""

    private var shuffledForDay: Int = -1
    private var shuffledIndices: List<Int> = emptyList()
    private var shuffledPoolSize: Int = -1

    private val _authors = MutableStateFlow(prefs.getAuthorCatalog())
    val authors: StateFlow<List<CachedAuthor>> = _authors.asStateFlow()

    /**
     * 拦截页随机名言（意图门入场；当前产品已关闭展示）。
     */
    suspend fun getRandomQuote(): DisplayQuote = withContext(Dispatchers.IO) {
        DisplayQuote(content = "")
    }

    /**
     * 取一句展示用格言：有作者订阅 → 订阅池；否则服务器精选默认池；再否则本地兜底。
     * 收藏不参与抽句。[slotMinute] 参与日洗牌，同一槽稳定。
     */
    suspend fun getPushQuote(slotMinute: Int): DisplayQuote = withContext(Dispatchers.IO) {
        val quotes = ensureDisplayPool()
        if (quotes.isEmpty()) {
            return@withContext quoteForSlot(FALLBACK_QUOTES, slotMinute)
        }
        quoteForSlot(
            quotes.map {
                DisplayQuote(
                    id = it.id,
                    content = it.content,
                    author = it.author,
                    authorId = it.author_id
                )
            },
            slotMinute
        )
    }

    fun isQuoteFavorited(id: Int, content: String): Boolean =
        prefs.isQuoteFavorited(id, content)

    fun toggleFavoriteQuote(quote: DisplayQuote): Boolean =
        prefs.toggleFavoriteQuote(
            id = quote.id,
            content = quote.content,
            author = quote.author,
            authorId = quote.authorId
        )

    fun isStopQuoteEnabled(): Boolean = prefs.isStopQuoteEnabled()

    /** 浏览池：精选公共句（不跟名人订阅走），供手动订阅句子。 */
    suspend fun listBrowseQuotes(): List<DisplayQuote> = withContext(Dispatchers.IO) {
        val remote = try {
            val curated = loadCuratedQuotes()
            if (curated.isNotEmpty()) curated else ensureDisplayPool(force = true)
        } catch (_: Exception) {
            emptyList()
        }
        val mapped = remote.map {
            DisplayQuote(
                id = it.id,
                content = it.content,
                author = it.author,
                authorId = it.author_id
            )
        }.filter { it.content.isNotBlank() }
        if (mapped.isNotEmpty()) mapped.distinctBy { if (it.id > 0) "id:${it.id}" else it.content }
        else FALLBACK_QUOTES
    }

    fun isAuthorSubscribed(authorId: Int): Boolean = prefs.isAuthorSubscribed(authorId)

    fun getCatalog(): List<CachedAuthor> = _authors.value

    fun getSubscribedAuthors(): List<CachedAuthor> =
        _authors.value.filter { it.subscribed }

    /**
     * 切换名人订阅。只影响「优先池」，不独占整库。
     */
    suspend fun toggleAuthorSubscription(authorId: Int): Boolean = withContext(Dispatchers.IO) {
        if (authorId <= 0) return@withContext false
        val nowSubscribed = !prefs.isAuthorSubscribed(authorId)
        prefs.setAuthorSubscribedLocal(authorId, nowSubscribed)
        refreshAuthorsFlow()
        invalidateQuotePool()
        try {
            if (nowSubscribed) {
                api.subscribeAuthor(authorId, deviceRequest())
            } else {
                api.unsubscribeAuthor(authorId, deviceId())
            }
        } catch (e: Exception) {
            Log.w(TAG, "订阅同步失败 author=$authorId: ${e.message}")
        }
        try {
            ensureDisplayPool(force = true)
        } catch (e: Exception) {
            Log.w(TAG, "订阅后刷新名言池失败: ${e.message}")
        }
        nowSubscribed
    }

    suspend fun requestAuthor(name: String, note: String = ""): AuthorRequestResult =
        withContext(Dispatchers.IO) {
            api.requestAuthor(
                AuthorRequestBody(
                    device_id = deviceId(),
                    name = name.trim(),
                    note = note.trim(),
                    app_version = BuildConfig.VERSION_NAME,
                    device_model = deviceModel()
                )
            )
        }

    suspend fun syncAll() = withContext(Dispatchers.IO) {
        try {
            syncAuthorCatalog()
        } catch (e: Exception) {
            Log.w(TAG, "名人目录同步失败: ${e.message}")
        }
        try {
            ensureDisplayPool(force = true)
        } catch (e: Exception) {
            Log.w(TAG, "名言池同步失败: ${e.message}")
        }
        try {
            pollAuthorNotices()
        } catch (e: Exception) {
            Log.w(TAG, "名人更新提醒失败: ${e.message}")
        }
    }

    suspend fun preload() = syncAll()

    suspend fun refreshCatalog() = withContext(Dispatchers.IO) {
        syncAuthorCatalog()
    }

    fun onQuoteSourceChanged() {
        invalidateQuotePool()
    }

    private fun invalidateQuotePool() {
        cacheTimestamp = 0L
        cacheSourceKey = ""
        shuffledForDay = -1
        shuffledPoolSize = -1
    }

    private suspend fun pollAuthorNotices() {
        val resp = api.getAuthorNotices(deviceId = deviceId(), unread = 1)
        if (!resp.success) return
        val unread = resp.data.orEmpty()
        if (unread.isEmpty()) return
        authorUpdateNotifier.notifyUpdates(unread)
        api.markAuthorNoticesRead(
            AuthorNoticesReadBody(
                device_id = deviceId(),
                ids = unread.map { it.id }
            )
        )
    }

    private suspend fun syncAuthorCatalog() {
        val resp = api.getAuthors(deviceId = deviceId())
        if (!resp.success) return
        val mapped = resp.data.orEmpty().map { it.toCached() }
        prefs.setAuthorCatalog(mapped)
        refreshAuthorsFlow()
        Log.d(TAG, "名人目录同步 ${mapped.size} 人，已订阅 ${mapped.count { it.subscribed }}")
    }

    private fun refreshAuthorsFlow() {
        _authors.value = prefs.getAuthorCatalog()
    }

    /**
     * 展示池 = 有已订作者则用订阅池；否则用服务器精选默认池。
     */
    private suspend fun ensureDisplayPool(force: Boolean = false): List<RemoteQuote> =
        cacheMutex.withLock {
            val subscribedIds = prefs.getSubscribedAuthors().map { it.id }.sorted()
            val sourceKey = "priority:${subscribedIds.joinToString(",")}"
            val now = System.currentTimeMillis()
            if (!force &&
                cachedQuotes.isNotEmpty() &&
                cacheSourceKey == sourceKey &&
                now - cacheTimestamp < CATALOG_TTL_MS
            ) {
                return@withLock cachedQuotes
            }
            try {
                val subscribed = if (subscribedIds.isNotEmpty()) {
                    loadSubscribedQuotes(subscribedIds.toSet())
                } else {
                    emptyList()
                }
                val curated = loadCuratedQuotes()
                cachedSubscribed = subscribed
                cachedCurated = curated
                // 优先订阅；订阅池空则精选公共池
                val display = when {
                    subscribed.isNotEmpty() -> subscribed
                    curated.isNotEmpty() -> curated
                    else -> emptyList()
                }
                cachedQuotes = display.distinctBy { it.id }
                cacheTimestamp = now
                cacheSourceKey = sourceKey
                shuffledForDay = -1
                shuffledPoolSize = -1
                Log.d(
                    TAG,
                    "名言池就绪 preferred=${if (subscribed.isNotEmpty()) "subscribed" else "curated"} " +
                        "sub=${subscribed.size} curated=${curated.size} display=${cachedQuotes.size}"
                )
            } catch (e: Exception) {
                Log.w(TAG, "名言池拉取失败: ${e.message}")
            }
            cachedQuotes
        }

    private suspend fun loadAllRemoteQuotes(): List<RemoteQuote> {
        val merged = mutableListOf<RemoteQuote>()
        var offset = 0
        val pageSize = 50
        var total = Int.MAX_VALUE
        while (offset < total) {
            val resp = api.getQuotes(limit = pageSize, offset = offset)
            if (!resp.success) break
            val page = resp.data.orEmpty()
            if (page.isEmpty()) break
            merged.addAll(page)
            total = resp.total.takeIf { it > 0 } ?: merged.size
            offset += page.size
            if (page.size < pageSize) break
        }
        return merged
    }

    /** 精选公共池：主题相关分类 */
    private suspend fun loadCuratedQuotes(): List<RemoteQuote> {
        return loadAllRemoteQuotes().filter { q ->
            val cat = q.category.trim()
            cat.isEmpty() || cat in CURATED_CATEGORIES
        }
    }

    private suspend fun loadSubscribedQuotes(subscribedIds: Set<Int>): List<RemoteQuote> {
        val merged = mutableListOf<RemoteQuote>()
        try {
            var offset = 0
            val pageSize = 50
            var total = Int.MAX_VALUE
            while (offset < total) {
                val resp = api.getQuotes(
                    limit = pageSize,
                    offset = offset,
                    deviceId = deviceId(),
                    source = "subscribed"
                )
                if (!resp.success) break
                val page = resp.data.orEmpty()
                if (page.isEmpty()) break
                merged.addAll(page)
                total = resp.total.takeIf { it > 0 } ?: merged.size
                offset += page.size
                if (page.size < pageSize) break
            }
        } catch (e: Exception) {
            Log.w(TAG, "订阅池批量拉取失败: ${e.message}")
        }

        if (merged.none { it.author_id in subscribedIds }) {
            for (authorId in subscribedIds) {
                try {
                    val resp = api.getQuotes(limit = 50, offset = 0, authorId = authorId)
                    if (resp.success) merged.addAll(resp.data.orEmpty())
                } catch (e: Exception) {
                    Log.w(TAG, "按名人拉取失败 author=$authorId: ${e.message}")
                }
            }
        }

        return merged.filter { it.author_id in subscribedIds && it.author_id > 0 }
    }

    private fun <T> quoteForSlot(quotes: List<T>, slotMinute: Int): T {
        val calendar = java.util.Calendar.getInstance()
        val dayOfYear = calendar.get(java.util.Calendar.DAY_OF_YEAR)
        if (shuffledForDay != dayOfYear ||
            shuffledPoolSize != quotes.size ||
            shuffledIndices.size != quotes.size
        ) {
            shuffledIndices = quotes.indices.toMutableList()
                .also { it.shuffle(java.util.Random(dayOfYear.toLong() * 31 + quotes.size)) }
            shuffledForDay = dayOfYear
            shuffledPoolSize = quotes.size
        }
        return quotes[shuffledIndices[slotMinute % quotes.size]]
    }

    private fun <T> quoteForHour(quotes: List<T>): T {
        val calendar = java.util.Calendar.getInstance()
        val hourOfDay = calendar.get(java.util.Calendar.HOUR_OF_DAY)
        return quoteForSlot(quotes, hourOfDay * 60)
    }

    private fun deviceId(): String {
        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        return androidId?.takeIf { it.isNotBlank() } ?: "unknown-${Build.MODEL}"
    }

    private fun deviceModel(): String =
        "${Build.MANUFACTURER} ${Build.MODEL}".trim()

    private fun deviceRequest() = AuthorDeviceRequest(
        device_id = deviceId(),
        app_version = BuildConfig.VERSION_NAME,
        device_model = deviceModel()
    )

    private fun RemoteAuthor.toCached() = CachedAuthor(
        id = id,
        name = name,
        bio = bio,
        category = category,
        quoteCount = quote_count,
        subscribed = subscribed != 0
    )
}
