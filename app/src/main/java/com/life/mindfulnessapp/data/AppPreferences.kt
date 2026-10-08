package com.life.mindfulnessapp.data

import android.content.Context
import android.content.res.Configuration
import androidx.core.content.edit
import com.life.mindfulnessapp.domain.model.CompanionAppMode
import com.life.mindfulnessapp.domain.model.CompanionBarForm
import com.life.mindfulnessapp.domain.model.MeaningfulThing
import com.life.mindfulnessapp.domain.model.MeaningfulThingsCatalog
import com.life.mindfulnessapp.domain.model.PositiveDestination
import com.life.mindfulnessapp.domain.model.ThemeMode
import com.life.mindfulnessapp.domain.model.ThemePack
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class PendingHaOrder(
    val orderNo: String,
    val planId: String,
    val planTitle: String = "",
    val amountYuan: String = "",
    val listAmountYuan: String = "",
    val earlyBird: Boolean = false,
    val tierLabel: String = "",
    val contactWechat: String = "",
    val remarkText: String = "",
    val benefitSummary: String = "",
    val notified: Boolean = false,
    val createdAt: Long = 0L,
    val phone: String = ""
)

/** 本地缓存的名人（目录/订阅，不含名言正文） */
data class CachedAuthor(
    val id: Int,
    val name: String,
    val bio: String = "",
    val category: String = "",
    val quoteCount: Int = 0,
    val subscribed: Boolean = false
)

/** 推送 / 收藏的格言快照 */
data class CachedPushedQuote(
    val id: Int = 0,
    val content: String,
    val author: String = "",
    val authorId: Int = 0,
    val pushedAt: Long = 0L
)

/**
 * 轻量级偏好存储，基于 SharedPreferences。
 * 目前管理：
 *   - 外观气质（theme_pack）：night / day / mist；跟随系统仅日↔夜
 *   - 胶囊已用时长显示到秒（capsule_used_show_seconds）
 *   - 迷你胶囊尺寸档（capsule_mini_size）：standard / compact
 *   - 胶囊全局默认停靠为左下；各 App 拖拽后单独记住（capsule_dock_pkg_*）
 *   - 想去的地方（positive_destinations）：离开后的正向 App 归属
 *
 * 注：意图门（打开前写意图）为每个被监控 App 的独立开关，见 AppLimitEntity。
 *     离开倒计时时长为全局偏好（本类）。
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("mindfulness_prefs", Context.MODE_PRIVATE)

    // ── 外观气质：夜锚 / 日间 / 雾青 · 跟随系统（仅日↔夜）──────────────────

    private val _themePack = MutableStateFlow(ThemePack.Night)
    private val _themeFollowSystem = MutableStateFlow(false)

    val themePack: StateFlow<ThemePack> = _themePack
    val themeFollowSystem: StateFlow<Boolean> = _themeFollowSystem

    /** 兼容旧上报 / 调用：由气质 + 跟随推导。 */
    private val _themeMode = MutableStateFlow(ThemeMode.Dark)
    val themeMode: StateFlow<ThemeMode> = _themeMode

    init {
        val loaded = loadThemeAppearance()
        _themePack.value = loaded.pack
        _themeFollowSystem.value = loaded.follow
        _themeMode.value = ThemeMode.fromPack(loaded.pack, loaded.follow)
    }

    fun setThemePack(pack: ThemePack) {
        val follow = if (pack.allowsFollowSystem) _themeFollowSystem.value else false
        persistThemeAppearance(pack, follow)
        // 换气质时带上该套壳透明度；面板仍可再拧
        setCapsuleShellOpacity(pack.shellOpacity)
    }

    fun setThemeFollowSystem(follow: Boolean) {
        var pack = _themePack.value
        if (follow && !pack.allowsFollowSystem) {
            pack = if (isSystemNightMode()) ThemePack.Night else ThemePack.Day
        }
        persistThemeAppearance(pack, follow = follow && pack.allowsFollowSystem)
    }

    fun setThemeMode(mode: ThemeMode) {
        when (mode) {
            ThemeMode.System -> {
                val pack = if (_themePack.value.allowsFollowSystem) {
                    _themePack.value
                } else {
                    ThemePack.Night
                }
                persistThemeAppearance(pack, follow = true)
            }
            ThemeMode.Light -> persistThemeAppearance(ThemePack.Day, follow = false)
            ThemeMode.Dark -> persistThemeAppearance(ThemePack.Night, follow = false)
        }
    }

    /** 解析后的气质（含跟随系统），供悬浮窗 / 服务读取。 */
    fun resolvedThemePack(): ThemePack = ThemePack.resolve(
        preferred = _themePack.value,
        followSystem = _themeFollowSystem.value,
        systemDark = isSystemNightMode(),
    )

    /** 解析后的夜间态，供悬浮窗 / 服务在展示时读取。 */
    fun isDarkThemeEnabled(): Boolean = resolvedThemePack().isDark

    fun getThemePack(): ThemePack = _themePack.value

    fun isThemeFollowSystem(): Boolean = _themeFollowSystem.value

    private data class ThemeAppearance(val pack: ThemePack, val follow: Boolean)

    private fun persistThemeAppearance(pack: ThemePack, follow: Boolean) {
        val normalizedFollow = follow && pack.allowsFollowSystem
        prefs.edit {
            putString(KEY_THEME_PACK, pack.storageKey)
            putBoolean(KEY_THEME_FOLLOW_SYSTEM, normalizedFollow)
            putString(
                KEY_THEME_MODE,
                ThemeMode.fromPack(pack, normalizedFollow).storageValue
            )
        }
        _themePack.value = pack
        _themeFollowSystem.value = normalizedFollow
        _themeMode.value = ThemeMode.fromPack(pack, normalizedFollow)
    }

    private fun loadThemeAppearance(): ThemeAppearance {
        val packStored = prefs.getString(KEY_THEME_PACK, null)
        if (!packStored.isNullOrBlank()) {
            val pack = ThemePack.fromStorage(packStored)
            val follow = prefs.getBoolean(KEY_THEME_FOLLOW_SYSTEM, false) && pack.allowsFollowSystem
            return ThemeAppearance(pack, follow)
        }
        val modeStored = prefs.getString(KEY_THEME_MODE, null)
        if (!modeStored.isNullOrBlank()) {
            return when (ThemeMode.fromStorage(modeStored)) {
                ThemeMode.System -> ThemeAppearance(ThemePack.Night, follow = true)
                ThemeMode.Light -> ThemeAppearance(ThemePack.Day, follow = false)
                ThemeMode.Dark -> ThemeAppearance(ThemePack.Night, follow = false)
            }
        }
        val legacyDark = prefs.getBoolean(KEY_DARK_THEME, true)
        val migrated = if (legacyDark) {
            ThemeAppearance(ThemePack.Night, follow = false)
        } else {
            ThemeAppearance(ThemePack.Day, follow = false)
        }
        persistThemeAppearance(migrated.pack, migrated.follow)
        return migrated
    }

    private fun isSystemNightMode(): Boolean {
        val night = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return night == Configuration.UI_MODE_NIGHT_YES
    }

    // ── 加强保活（守护前台服务）────────────────────────────────────────────────

    private val _enhancedKeepAlive = MutableStateFlow(
        prefs.getBoolean(KEY_ENHANCED_KEEP_ALIVE, true)
    )

    /** 是否开启加强保活（启动独立守护前台服务）；默认开启 */
    val enhancedKeepAlive: StateFlow<Boolean> = _enhancedKeepAlive

    fun setEnhancedKeepAlive(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENHANCED_KEEP_ALIVE, enabled) }
        _enhancedKeepAlive.value = enabled
    }

    fun isEnhancedKeepAliveEnabled(): Boolean = _enhancedKeepAlive.value

    // ── 隐藏最近任务（对抗一键清理）──────────────────────────────────────────

    private val _hideFromRecents = MutableStateFlow(
        if (prefs.contains(KEY_HIDE_FROM_RECENTS)) {
            prefs.getBoolean(KEY_HIDE_FROM_RECENTS, false)
        } else {
            com.life.mindfulnessapp.util.RecentsHider.defaultHideOnThisDevice()
        }
    )

    /**
     * 是否在多任务界面隐藏心锚。
     * 国产严苛机默认开；用户可关。未写过偏好时按机型默认。
     */
    val hideFromRecents: StateFlow<Boolean> = _hideFromRecents

    fun setHideFromRecents(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HIDE_FROM_RECENTS, enabled) }
        _hideFromRecents.value = enabled
    }

    fun isHideFromRecentsEnabled(): Boolean = _hideFromRecents.value

    // ── 步行觉察（全局 · 探索）────────────────────────────────────────────────

    private val _walkAwarenessEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_WALK_AWARENESS_ENABLED, false)
    )

    /** 是否开启步行觉察（边走边看手机时路况锚点）；默认关 */
    val walkAwarenessEnabled: StateFlow<Boolean> = _walkAwarenessEnabled

    fun setWalkAwarenessEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_WALK_AWARENESS_ENABLED, enabled) }
        _walkAwarenessEnabled.value = enabled
    }

    fun isWalkAwarenessEnabled(): Boolean = _walkAwarenessEnabled.value

    // ── 觉察练习（全局 · 发现 · 默认关）──────────────────────────────────────

    private val _awarenessPracticeEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_AWARENESS_PRACTICE_ENABLED, false)
    )

    /** 是否开启觉察练习（中途轻问 + 离开对照）；默认关 */
    val awarenessPracticeEnabled: StateFlow<Boolean> = _awarenessPracticeEnabled

    fun setAwarenessPracticeEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_AWARENESS_PRACTICE_ENABLED, enabled) }
        _awarenessPracticeEnabled.value = enabled
    }

    fun isAwarenessPracticeEnabled(): Boolean = _awarenessPracticeEnabled.value

    private val _awarenessPracticeRhythm = MutableStateFlow(
        com.life.mindfulnessapp.domain.model.AwarenessPracticeRhythm.fromStorage(
            prefs.getString(KEY_AWARENESS_PRACTICE_RHYTHM, null)
        )
    )

    /** 中途轻问节奏：疏 10′ / 常 5′ */
    val awarenessPracticeRhythm:
        StateFlow<com.life.mindfulnessapp.domain.model.AwarenessPracticeRhythm> =
        _awarenessPracticeRhythm

    fun setAwarenessPracticeRhythm(
        rhythm: com.life.mindfulnessapp.domain.model.AwarenessPracticeRhythm
    ) {
        prefs.edit { putString(KEY_AWARENESS_PRACTICE_RHYTHM, rhythm.storageKey) }
        _awarenessPracticeRhythm.value = rhythm
    }

    fun getAwarenessPracticeRhythm():
        com.life.mindfulnessapp.domain.model.AwarenessPracticeRhythm =
        _awarenessPracticeRhythm.value

    // ── 试玩（beta 玩法 · 默认全关）──────────────────────────────────────────

    private val _playgroundFeedShade = MutableStateFlow(
        prefs.getBoolean(KEY_PLAYGROUND_FEED_SHADE, false)
    )

    /** 推荐流遮挡：刷到推荐时先挡一下；未接线前开关无运行时效果 */
    val playgroundFeedShade: StateFlow<Boolean> = _playgroundFeedShade

    fun setPlaygroundFeedShade(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PLAYGROUND_FEED_SHADE, enabled) }
        _playgroundFeedShade.value = enabled
    }

    fun isPlaygroundFeedShadeEnabled(): Boolean = _playgroundFeedShade.value

    private val _playgroundInterceptWord = MutableStateFlow(
        prefs.getBoolean(KEY_PLAYGROUND_INTERCEPT_WORD, false)
    )

    /** 拦截页单词：门口多一行短词；未接线前开关无运行时效果 */
    val playgroundInterceptWord: StateFlow<Boolean> = _playgroundInterceptWord

    fun setPlaygroundInterceptWord(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_PLAYGROUND_INTERCEPT_WORD, enabled) }
        _playgroundInterceptWord.value = enabled
    }

    fun isPlaygroundInterceptWordEnabled(): Boolean = _playgroundInterceptWord.value

    // ── 桌面心锚微粒（监测开启时常驻）────────────────────────────────────────

    private val _desktopAnchorEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_DESKTOP_ANCHOR_ENABLED, true)
    )

    /**
     * 是否显示桌面心锚微粒。
     * 默认开；仍须监测服务在跑 + 悬浮窗权限。会话胶囊逻辑不受此项影响。
     */
    val desktopAnchorEnabled: StateFlow<Boolean> = _desktopAnchorEnabled

    fun setDesktopAnchorEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DESKTOP_ANCHOR_ENABLED, enabled) }
        _desktopAnchorEnabled.value = enabled
    }

    fun isDesktopAnchorEnabled(): Boolean = _desktopAnchorEnabled.value

    /** 桌面自由态记忆坐标：相对屏幕水平中心的 x 偏移 + 距顶 y（px）。松手后吸左右边。 */
    fun getDesktopAnchorFloatOffset(): Pair<Int, Int>? {
        val raw = prefs.getString(KEY_DESKTOP_ANCHOR_FLOAT, null) ?: return null
        val parts = raw.split(',')
        if (parts.size != 2) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        return x to y
    }

    fun setDesktopAnchorFloatOffset(offsetX: Int, offsetY: Int) {
        prefs.edit { putString(KEY_DESKTOP_ANCHOR_FLOAT, "$offsetX,$offsetY") }
    }

    // ── Debug：强制日限额洗灰（仅 Debug 包设置页暴露）────────────────────────

    private val _debugForceGrayWash = MutableStateFlow(
        prefs.getBoolean(KEY_DEBUG_FORCE_GRAY_WASH, false)
    )

    /** Debug：进入监控 App 立刻满灰（忽略日限额剩余） */
    val debugForceGrayWash: StateFlow<Boolean> = _debugForceGrayWash

    fun setDebugForceGrayWash(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DEBUG_FORCE_GRAY_WASH, enabled) }
        _debugForceGrayWash.value = enabled
    }

    fun isDebugForceGrayWashEnabled(): Boolean = _debugForceGrayWash.value

    // ── 胶囊已用时长精度（分 / 秒）──────────────────────────────────────────

    private val _capsuleUsedShowSeconds = MutableStateFlow(
        prefs.getBoolean(KEY_CAPSULE_USED_SHOW_SECONDS, false)
    )

    /**
     * 纯时长锁迷你态：已用侧是否显示到秒。
     * false → `12/60分`；true → `12:34/60分`。默认关。
     */
    val capsuleUsedShowSeconds: StateFlow<Boolean> = _capsuleUsedShowSeconds

    fun setCapsuleUsedShowSeconds(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_CAPSULE_USED_SHOW_SECONDS, enabled) }
        _capsuleUsedShowSeconds.value = enabled
    }

    fun isCapsuleUsedShowSeconds(): Boolean = _capsuleUsedShowSeconds.value

    // ── 迷你胶囊尺寸档（标准 / 紧凑）────────────────────────────────────────

    private val _capsuleMiniSize = MutableStateFlow(
        normalizeCapsuleMiniSize(
            prefs.getString(KEY_CAPSULE_MINI_SIZE, CAPSULE_MINI_SIZE_STANDARD)
                ?: CAPSULE_MINI_SIZE_STANDARD
        )
    )

    /**
     * 迷你态壳尺寸与字号档：
     * - [CAPSULE_MINI_SIZE_STANDARD]：更舒展（默认）
     * - [CAPSULE_MINI_SIZE_COMPACT]：当前偏省空间的一档
     */
    val capsuleMiniSize: StateFlow<String> = _capsuleMiniSize

    fun setCapsuleMiniSize(size: String) {
        val normalized = normalizeCapsuleMiniSize(size)
        prefs.edit { putString(KEY_CAPSULE_MINI_SIZE, normalized) }
        _capsuleMiniSize.value = normalized
    }

    fun getCapsuleMiniSize(): String = _capsuleMiniSize.value

    fun isCapsuleMiniCompact(): Boolean =
        _capsuleMiniSize.value == CAPSULE_MINI_SIZE_COMPACT

    // ── 陪伴条壳透明度（点开面板可调；换气质时用该套默认值覆盖）────────────

    private val _capsuleShellOpacity = MutableStateFlow(loadCapsuleShellOpacity())

    val capsuleShellOpacity: StateFlow<Float> = _capsuleShellOpacity

    fun getCapsuleShellOpacity(): Float = _capsuleShellOpacity.value

    fun setCapsuleShellOpacity(opacity: Float) {
        val normalized = normalizeCapsuleShellOpacity(opacity)
        prefs.edit { putFloat(KEY_CAPSULE_SHELL_OPACITY, normalized) }
        _capsuleShellOpacity.value = normalized
    }

    private fun loadCapsuleShellOpacity(): Float {
        if (prefs.contains(KEY_CAPSULE_SHELL_OPACITY)) {
            return normalizeCapsuleShellOpacity(
                prefs.getFloat(KEY_CAPSULE_SHELL_OPACITY, CAPSULE_SHELL_OPACITY_DEFAULT)
            )
        }
        return normalizeCapsuleShellOpacity(resolvedThemePack().shellOpacity)
    }

    // ── 使用中陪伴：全局跟规则（意图门挂条 · 形态跟建议）─────────────────────

    fun isCompanionBarEnabled(): Boolean = false

    fun getCompanionBarForm(): String = CompanionBarForm.AUTO.storageKey

    /** @deprecated 陪伴条由规则驱动 */
    @Deprecated("Companion bar follows intent-gate rules")
    fun setCompanionBarEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) {
        // no-op
    }

    /** @deprecated 全局形态固定跟建议 */
    @Deprecated("Global form is always AUTO")
    fun setCompanionBarForm(@Suppress("UNUSED_PARAMETER") form: String) {
        // no-op
    }

    fun getCompanionAppMode(packageName: String): String {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return CompanionAppMode.FOLLOW.storageKey
        return CompanionAppMode.fromStorage(
            prefs.getString(KEY_COMPANION_MODE_PKG_PREFIX + pkg, CompanionAppMode.FOLLOW.storageKey)
        ).storageKey
    }

    fun setCompanionAppMode(packageName: String, mode: String) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        val normalized = CompanionAppMode.fromStorage(mode).storageKey
        prefs.edit {
            if (normalized == CompanionAppMode.FOLLOW.storageKey) {
                remove(KEY_COMPANION_MODE_PKG_PREFIX + pkg)
            } else {
                putString(KEY_COMPANION_MODE_PKG_PREFIX + pkg, normalized)
            }
        }
    }

    // ── 定时格言推送（时段 + 间隔）+ 名人订阅 / 本地收藏 ─────────────────────

    private val _scheduledQuotePushEnabled = MutableStateFlow(loadScheduledQuotePushEnabled())

    /** 是否在时段内按间隔推送格言；默认关。 */
    val scheduledQuotePushEnabled: StateFlow<Boolean> = _scheduledQuotePushEnabled

    private val _scheduledQuotePushStart = MutableStateFlow(loadScheduledQuotePushStart())
    private val _scheduledQuotePushEnd = MutableStateFlow(loadScheduledQuotePushEnd())
    private val _scheduledQuotePushInterval = MutableStateFlow(loadScheduledQuotePushInterval())

    /** 推送时段起点（距 0 点分钟）。 */
    val scheduledQuotePushStart: StateFlow<Int> = _scheduledQuotePushStart

    /** 推送时段终点（距 0 点分钟）；与起点相等视为全天。 */
    val scheduledQuotePushEnd: StateFlow<Int> = _scheduledQuotePushEnd

    /** 间隔分钟数。 */
    val scheduledQuotePushInterval: StateFlow<Int> = _scheduledQuotePushInterval

    fun setScheduledQuotePushEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_SCHEDULED_QUOTE_PUSH_ENABLED, enabled) }
        _scheduledQuotePushEnabled.value = enabled
    }

    /** 永久关闭定时推送（功能已移除）。 */
    fun retireScheduledQuotePush() {
        if (!_scheduledQuotePushEnabled.value &&
            prefs.getBoolean(KEY_SCHEDULED_QUOTE_PUSH_RETIRED, false)
        ) return
        prefs.edit {
            putBoolean(KEY_SCHEDULED_QUOTE_PUSH_ENABLED, false)
            putBoolean(KEY_SCHEDULED_QUOTE_PUSH_RETIRED, true)
        }
        _scheduledQuotePushEnabled.value = false
    }

    fun isScheduledQuotePushEnabled(): Boolean = _scheduledQuotePushEnabled.value

    fun getScheduledQuotePushStart(): Int = _scheduledQuotePushStart.value
    fun getScheduledQuotePushEnd(): Int = _scheduledQuotePushEnd.value
    fun getScheduledQuotePushInterval(): Int = _scheduledQuotePushInterval.value

    fun setScheduledQuotePushWindow(startMinute: Int, endMinute: Int) {
        val s = startMinute.coerceIn(0, 1439)
        val e = endMinute.coerceIn(0, 1439)
        prefs.edit {
            putInt(KEY_SCHEDULED_QUOTE_PUSH_START, s)
            putInt(KEY_SCHEDULED_QUOTE_PUSH_END, e)
        }
        _scheduledQuotePushStart.value = s
        _scheduledQuotePushEnd.value = e
    }

    fun setScheduledQuotePushInterval(minutes: Int) {
        val normalized = normalizeQuotePushInterval(minutes)
        prefs.edit { putInt(KEY_SCHEDULED_QUOTE_PUSH_INTERVAL, normalized) }
        _scheduledQuotePushInterval.value = normalized
    }

    /**
     * 由时段 + 间隔展开今日推送时刻（升序）。
     * 起止相等 = 全天；终点 &lt; 起点 = 跨午夜。
     */
    fun expandScheduledQuotePushSlots(
        startMinute: Int = getScheduledQuotePushStart(),
        endMinute: Int = getScheduledQuotePushEnd(),
        intervalMinutes: Int = getScheduledQuotePushInterval()
    ): List<Int> {
        val interval = normalizeQuotePushInterval(intervalMinutes)
        val start = startMinute.coerceIn(0, 1439)
        val end = endMinute.coerceIn(0, 1439)
        val slots = mutableListOf<Int>()
        if (start == end) {
            // 全天：从 0 点起按间隔
            var m = 0
            while (m < 1440) {
                slots.add(m)
                m += interval
            }
            return slots
        }
        if (end > start) {
            var m = start
            while (m < end) {
                slots.add(m)
                m += interval
            }
            return slots
        }
        // 跨午夜：start → 24:00，再对齐到 [0, end)
        var m = start
        while (m < 1440) {
            slots.add(m)
            m += interval
        }
        var n = m - 1440
        while (n < end) {
            if (n >= 0) slots.add(n)
            n += interval
        }
        return slots.distinct().sorted()
    }

    /** 今日某时刻是否已推送过（防重复）。 */
    fun wasScheduledQuoteSlotFiredToday(minuteOfDay: Int): Boolean {
        val today = todayYyyyMmDd()
        val storedDay = prefs.getString(KEY_SCHEDULED_QUOTE_FIRED_DAY, "") ?: ""
        if (storedDay != today) return false
        val fired = prefs.getString(KEY_SCHEDULED_QUOTE_FIRED_SLOTS, "") ?: ""
        return fired.split(',').any { it.trim().toIntOrNull() == minuteOfDay }
    }

    fun markScheduledQuoteSlotFired(minuteOfDay: Int) {
        val today = todayYyyyMmDd()
        val storedDay = prefs.getString(KEY_SCHEDULED_QUOTE_FIRED_DAY, "") ?: ""
        val slots = if (storedDay == today) {
            prefs.getString(KEY_SCHEDULED_QUOTE_FIRED_SLOTS, "") ?: ""
        } else {
            ""
        }
        val merged = (slots.split(',')
            .mapNotNull { it.trim().toIntOrNull() } + minuteOfDay)
            .distinct()
            .sorted()
            .joinToString(",")
        prefs.edit {
            putString(KEY_SCHEDULED_QUOTE_FIRED_DAY, today)
            putString(KEY_SCHEDULED_QUOTE_FIRED_SLOTS, merged)
        }
    }

    /** 最近一次推送的格言（通知点进详情用）。 */
    fun saveLastPushedQuote(id: Int, content: String, author: String, authorId: Int) {
        prefs.edit {
            putInt(KEY_LAST_PUSHED_QUOTE_ID, id)
            putString(KEY_LAST_PUSHED_QUOTE_CONTENT, content)
            putString(KEY_LAST_PUSHED_QUOTE_AUTHOR, author)
            putInt(KEY_LAST_PUSHED_QUOTE_AUTHOR_ID, authorId)
            putLong(KEY_LAST_PUSHED_QUOTE_AT, System.currentTimeMillis())
        }
    }

    fun getLastPushedQuote(): CachedPushedQuote? {
        val content = prefs.getString(KEY_LAST_PUSHED_QUOTE_CONTENT, null)?.trim().orEmpty()
        if (content.isBlank()) return null
        return CachedPushedQuote(
            id = prefs.getInt(KEY_LAST_PUSHED_QUOTE_ID, 0),
            content = content,
            author = prefs.getString(KEY_LAST_PUSHED_QUOTE_AUTHOR, "") ?: "",
            authorId = prefs.getInt(KEY_LAST_PUSHED_QUOTE_AUTHOR_ID, 0),
            pushedAt = prefs.getLong(KEY_LAST_PUSHED_QUOTE_AT, 0L)
        )
    }

    private val _favoriteQuotesJson = MutableStateFlow(
        prefs.getString(KEY_FAVORITE_QUOTES_JSON, "") ?: ""
    )

    val favoriteQuotesJson: StateFlow<String> = _favoriteQuotesJson

    fun getFavoriteQuotes(): List<CachedPushedQuote> =
        decodeFavoriteQuotes(_favoriteQuotesJson.value)

    fun isQuoteFavorited(id: Int, content: String): Boolean {
        val list = getFavoriteQuotes()
        return if (id > 0) list.any { it.id == id }
        else list.any { it.content == content }
    }

    fun toggleFavoriteQuote(id: Int, content: String, author: String, authorId: Int): Boolean {
        val current = getFavoriteQuotes().toMutableList()
        val idx = if (id > 0) current.indexOfFirst { it.id == id }
        else current.indexOfFirst { it.content == content }
        val nowFavorited = if (idx >= 0) {
            current.removeAt(idx)
            false
        } else {
            current.add(
                0,
                CachedPushedQuote(
                    id = id,
                    content = content.trim(),
                    author = author,
                    authorId = authorId,
                    pushedAt = System.currentTimeMillis()
                )
            )
            true
        }
        val capped = current.take(MAX_FAVORITE_QUOTES)
        val json = encodeFavoriteQuotes(capped)
        prefs.edit { putString(KEY_FAVORITE_QUOTES_JSON, json) }
        _favoriteQuotesJson.value = json
        return nowFavorited
    }

    /**
     * 停住门页（单次到点 / 日时长锁 / 时段锁）是否显示脚注格言。
     * 默认开；格言模块里有快关。
     */
    private val _stopQuoteEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_STOP_QUOTE_ENABLED, true)
    )

    val stopQuoteEnabled: StateFlow<Boolean> = _stopQuoteEnabled

    fun isStopQuoteEnabled(): Boolean = _stopQuoteEnabled.value

    fun setStopQuoteEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_STOP_QUOTE_ENABLED, enabled) }
        _stopQuoteEnabled.value = enabled
    }

    private fun loadScheduledQuotePushEnabled(): Boolean {
        ensureScheduledQuotePushMigrated()
        return prefs.getBoolean(KEY_SCHEDULED_QUOTE_PUSH_ENABLED, false)
    }

    private fun loadScheduledQuotePushStart(): Int {
        ensureScheduledQuotePushMigrated()
        return prefs.getInt(KEY_SCHEDULED_QUOTE_PUSH_START, DEFAULT_SCHEDULED_QUOTE_PUSH_START)
    }

    private fun loadScheduledQuotePushEnd(): Int {
        ensureScheduledQuotePushMigrated()
        return prefs.getInt(KEY_SCHEDULED_QUOTE_PUSH_END, DEFAULT_SCHEDULED_QUOTE_PUSH_END)
    }

    private fun loadScheduledQuotePushInterval(): Int {
        ensureScheduledQuotePushMigrated()
        return normalizeQuotePushInterval(
            prefs.getInt(KEY_SCHEDULED_QUOTE_PUSH_INTERVAL, DEFAULT_SCHEDULED_QUOTE_PUSH_INTERVAL)
        )
    }

    private fun ensureScheduledQuotePushMigrated() {
        if (prefs.getBoolean(KEY_SCHEDULED_QUOTE_PUSH_MIGRATED_V2, false)) return
        val hadInterceptQuote = prefs.getBoolean(KEY_INTERCEPT_QUOTE_ENABLED, true)
        val hadPushEnabledKey = prefs.contains(KEY_SCHEDULED_QUOTE_PUSH_ENABLED)
        // 旧版离散时刻 → 时段 + 间隔
        val legacyTimes = decodeScheduledQuotePushTimes(
            prefs.getString(KEY_SCHEDULED_QUOTE_PUSH_TIMES, "") ?: ""
        )
        val start: Int
        val end: Int
        val interval: Int
        if (legacyTimes.size >= 2) {
            start = legacyTimes.first()
            end = (legacyTimes.last() + 60).coerceAtMost(1439)
            val gaps = legacyTimes.zipWithNext { a, b -> b - a }.filter { it > 0 }
            interval = normalizeQuotePushInterval(gaps.minOrNull() ?: DEFAULT_SCHEDULED_QUOTE_PUSH_INTERVAL)
        } else if (legacyTimes.size == 1) {
            start = legacyTimes.first()
            end = (start + 12 * 60).coerceAtMost(1439)
            interval = DEFAULT_SCHEDULED_QUOTE_PUSH_INTERVAL
        } else {
            start = DEFAULT_SCHEDULED_QUOTE_PUSH_START
            end = DEFAULT_SCHEDULED_QUOTE_PUSH_END
            interval = DEFAULT_SCHEDULED_QUOTE_PUSH_INTERVAL
        }
        val hasStart = prefs.contains(KEY_SCHEDULED_QUOTE_PUSH_START)
        val hasEnd = prefs.contains(KEY_SCHEDULED_QUOTE_PUSH_END)
        val hasInterval = prefs.contains(KEY_SCHEDULED_QUOTE_PUSH_INTERVAL)
        val editor = prefs.edit()
        if (hadInterceptQuote && !hadPushEnabledKey) {
            editor.putBoolean(KEY_SCHEDULED_QUOTE_PUSH_ENABLED, true)
        }
        if (!hasStart) {
            editor.putInt(KEY_SCHEDULED_QUOTE_PUSH_START, start)
        }
        if (!hasEnd) {
            editor.putInt(KEY_SCHEDULED_QUOTE_PUSH_END, end)
        }
        if (!hasInterval) {
            editor.putInt(KEY_SCHEDULED_QUOTE_PUSH_INTERVAL, interval)
        }
        editor.putBoolean(KEY_INTERCEPT_QUOTE_ENABLED, false)
        editor.putBoolean(KEY_SCHEDULED_QUOTE_PUSH_MIGRATED, true)
        editor.putBoolean(KEY_SCHEDULED_QUOTE_PUSH_MIGRATED_V2, true)
        editor.apply()
    }

    private fun todayYyyyMmDd(): String {
        val c = java.util.Calendar.getInstance()
        return "%04d%02d%02d".format(
            c.get(java.util.Calendar.YEAR),
            c.get(java.util.Calendar.MONTH) + 1,
            c.get(java.util.Calendar.DAY_OF_MONTH)
        )
    }

    /** @deprecated 意图门入场格言已下线，改用 [scheduledQuotePushEnabled]。 */
    @Deprecated("Use scheduledQuotePushEnabled")
    val interceptQuoteEnabled: StateFlow<Boolean> = _scheduledQuotePushEnabled

    @Deprecated("Use setScheduledQuotePushEnabled")
    fun setInterceptQuoteEnabled(enabled: Boolean) = setScheduledQuotePushEnabled(enabled)

    @Deprecated("Use isScheduledQuotePushEnabled")
    fun isInterceptQuoteEnabled(): Boolean = isScheduledQuotePushEnabled()

    /** @deprecated 已改为时段+间隔 */
    @Deprecated("Use expandScheduledQuotePushSlots")
    val scheduledQuotePushTimes: StateFlow<List<Int>> = MutableStateFlow(emptyList())

    private val _interceptQuoteSource = MutableStateFlow(loadInterceptQuoteSource())

    /** 名言来源：系统精选 / 我订阅的名人 */
    val interceptQuoteSource: StateFlow<String> = _interceptQuoteSource

    fun setInterceptQuoteSource(source: String) {
        val normalized = when (source) {
            INTERCEPT_QUOTE_SOURCE_LIBRARY -> INTERCEPT_QUOTE_SOURCE_LIBRARY
            else -> INTERCEPT_QUOTE_SOURCE_SYSTEM
        }
        prefs.edit { putString(KEY_INTERCEPT_QUOTE_SOURCE, normalized) }
        _interceptQuoteSource.value = normalized
    }

    fun getInterceptQuoteSource(): String = _interceptQuoteSource.value

    /** 是否使用「我订阅的名人」池 */
    fun usesSubscribedAuthors(): Boolean =
        getInterceptQuoteSource() == INTERCEPT_QUOTE_SOURCE_LIBRARY

    @Deprecated("Use usesSubscribedAuthors", ReplaceWith("usesSubscribedAuthors()"))
    fun usesQuoteLibrary(): Boolean = usesSubscribedAuthors()

    private fun loadInterceptQuoteSource(): String {
        val stored = prefs.getString(KEY_INTERCEPT_QUOTE_SOURCE, null)
        val migrated = when (stored) {
            INTERCEPT_QUOTE_SOURCE_LIBRARY, "custom" -> INTERCEPT_QUOTE_SOURCE_LIBRARY
            else -> INTERCEPT_QUOTE_SOURCE_SYSTEM
        }
        if (stored != migrated) {
            prefs.edit { putString(KEY_INTERCEPT_QUOTE_SOURCE, migrated) }
        }
        return migrated
    }

    private val _authorCatalogJson = MutableStateFlow(
        prefs.getString(KEY_AUTHOR_CATALOG_JSON, "") ?: ""
    )

    val authorCatalogJson: StateFlow<String> = _authorCatalogJson

    fun getAuthorCatalog(): List<CachedAuthor> =
        decodeAuthorCatalog(_authorCatalogJson.value)

    fun setAuthorCatalog(authors: List<CachedAuthor>) {
        val json = encodeAuthorCatalog(authors)
        prefs.edit {
            putString(KEY_AUTHOR_CATALOG_JSON, json)
            putLong(KEY_AUTHOR_CATALOG_SYNCED_AT, System.currentTimeMillis())
        }
        _authorCatalogJson.value = json
    }

    fun getAuthorCatalogSyncedAt(): Long =
        prefs.getLong(KEY_AUTHOR_CATALOG_SYNCED_AT, 0L)

    fun isAuthorSubscribed(authorId: Int): Boolean =
        authorId > 0 && getAuthorCatalog().any { it.id == authorId && it.subscribed }

    fun setAuthorSubscribedLocal(authorId: Int, subscribed: Boolean) {
        if (authorId <= 0) return
        val next = getAuthorCatalog().map {
            if (it.id == authorId) it.copy(subscribed = subscribed) else it
        }
        setAuthorCatalog(next)
    }

    fun getSubscribedAuthors(): List<CachedAuthor> =
        getAuthorCatalog().filter { it.subscribed }

    fun isQuoteSubscribedSourceAutoMigrated(): Boolean =
        prefs.getBoolean(KEY_QUOTE_SUBSCRIBED_SOURCE_AUTO_MIGRATED, false)

    fun markQuoteSubscribedSourceAutoMigrated() {
        prefs.edit { putBoolean(KEY_QUOTE_SUBSCRIBED_SOURCE_AUTO_MIGRATED, true) }
    }

    // ── 胶囊停靠（上/下排 × 左/中/右，共 6 区；全局默认左下）──────────────

    /**
     * 尚未单独拖过的 App 使用的全局默认停靠。
     * 产品固定为左下；设置页不再暴露。拖拽松手写入 [setCapsuleDockPositionForPackage]。
     */
    fun getCapsuleDockPosition(): String = CAPSULE_DOCK_DEFAULT

    /**
     * 该 App 上次拖拽后的停靠；没有单独记录时回退到左下默认。
     */
    fun getCapsuleDockPositionForPackage(packageName: String): String {
        if (packageName.isBlank()) return getCapsuleDockPosition()
        val stored = prefs.getString(KEY_CAPSULE_DOCK_PKG_PREFIX + packageName, null)
        return if (stored != null) normalizeCapsuleDockPosition(stored)
        else getCapsuleDockPosition()
    }

    /** 记住某 App 的停靠位置（拖拽松手时调用） */
    fun setCapsuleDockPositionForPackage(packageName: String, position: String) {
        if (packageName.isBlank()) return
        val normalized = normalizeCapsuleDockPosition(position)
        prefs.edit { putString(KEY_CAPSULE_DOCK_PKG_PREFIX + packageName, normalized) }
    }

    /**
     * 专注圆球自由悬浮坐标：相对屏幕水平中心的 x 偏移 + 距顶 y（px）。
     * 无记录时返回 null，由 Overlay 用默认落点。
     */
    fun getCapsuleFloatOffsetForPackage(packageName: String): Pair<Int, Int>? {
        if (packageName.isBlank()) return null
        val raw = prefs.getString(KEY_CAPSULE_FLOAT_PKG_PREFIX + packageName, null) ?: return null
        val parts = raw.split(',')
        if (parts.size != 2) return null
        val x = parts[0].toIntOrNull() ?: return null
        val y = parts[1].toIntOrNull() ?: return null
        return x to y
    }

    fun setCapsuleFloatOffsetForPackage(packageName: String, offsetX: Int, offsetY: Int) {
        if (packageName.isBlank()) return
        prefs.edit {
            putString(KEY_CAPSULE_FLOAT_PKG_PREFIX + packageName, "$offsetX,$offsetY")
        }
    }

    /** 专注圆球「轻点可看详情」首次提示是否已展示过 */
    fun hasSeenFocusOrbDiscoverHint(): Boolean =
        prefs.getBoolean(KEY_FOCUS_ORB_DISCOVER_HINT_SEEN, false)

    fun markFocusOrbDiscoverHintSeen() {
        prefs.edit { putBoolean(KEY_FOCUS_ORB_DISCOVER_HINT_SEEN, true) }
    }

    /** 门口「最近意图」是否被用户藏起（长按删除）。 */
    fun isGateRecentPurposeHidden(packageName: String, purpose: String): Boolean {
        val key = purpose.trim()
        if (packageName.isBlank() || key.isEmpty()) return false
        return prefs.getStringSet(gateHiddenPurposeKey(packageName), emptySet())
            .orEmpty()
            .contains(key)
    }

    fun hideGateRecentPurpose(packageName: String, purpose: String) {
        val key = purpose.trim()
        if (packageName.isBlank() || key.isEmpty()) return
        val prefKey = gateHiddenPurposeKey(packageName)
        val next = prefs.getStringSet(prefKey, emptySet()).orEmpty().toMutableSet()
        if (!next.add(key)) return
        prefs.edit { putStringSet(prefKey, next) }
    }

    /**
     * 总门教学副句：按 [com.life.mindfulnessapp.domain.model.GatePathTeach] 递减挂载。
     * 每次总门会话调用一次；返回本次是否应显示教学副句。
     */
    fun consumeGatePathTeachShow(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val today = com.life.mindfulnessapp.domain.model.GatePathTeach.todayYmd()
        val firstKey = KEY_GATE_PATH_TEACH_FIRST_DAY_PREFIX + packageName
        val dayKey = KEY_GATE_PATH_TEACH_DAY_PREFIX + packageName
        val countKey = KEY_GATE_PATH_TEACH_COUNT_PREFIX + packageName
        val first = prefs.getString(firstKey, null)?.takeIf { it.length == 8 } ?: today
        val storedDay = prefs.getString(dayKey, null)
        val showsAlready = if (storedDay == today) {
            prefs.getInt(countKey, 0).coerceAtLeast(0)
        } else {
            0
        }
        val show = com.life.mindfulnessapp.domain.model.GatePathTeach.shouldShowTeachingSubtitles(
            firstYmd = first,
            todayYmd = today,
            showsAlreadyToday = showsAlready
        )
        prefs.edit {
            if (prefs.getString(firstKey, null).isNullOrBlank()) {
                putString(firstKey, today)
            }
            putString(dayKey, today)
            putInt(countKey, showsAlready + 1)
        }
        return show
    }

    // ── 意图门离开倒计时（秒）────────────────────────────────────────────────

    private val _awayCountdownSeconds = MutableStateFlow(
        normalizeAwayCountdownSeconds(
            prefs.getInt(KEY_AWAY_COUNTDOWN_SECONDS, DEFAULT_AWAY_COUNTDOWN_SECONDS)
        )
    )

    /**
     * 含意图门时，切到桌面后离开倒计时秒数（驱动暂停胶囊环边；不跟秒显示）。
     * 有效值：60 / 120 / 300；默认 120。纯时长锁静默收口等待与此同量级。
     */
    val awayCountdownSeconds: StateFlow<Int> = _awayCountdownSeconds

    fun setAwayCountdownSeconds(seconds: Int) {
        val normalized = normalizeAwayCountdownSeconds(seconds)
        prefs.edit { putInt(KEY_AWAY_COUNTDOWN_SECONDS, normalized) }
        _awayCountdownSeconds.value = normalized
    }

    fun getAwayCountdownSeconds(): Int = _awayCountdownSeconds.value

    // ── 有意义的事（预置 + 自定义，拦截页与 App 共用）────────────────────────

    private val _meaningfulThingsRevision = MutableStateFlow(0)
    /** 列表变更计数，供 UI collect 刷新 */
    val meaningfulThingsRevision: StateFlow<Int> = _meaningfulThingsRevision

    fun getMeaningfulThings(): List<MeaningfulThing> {
        val disabled = loadDisabledMeaningfulPresetIds()
        val presetBindings = loadMeaningfulPresetBindings()
        val presets = MeaningfulThingsCatalog.PRESETS
            .filter { it.id !in disabled }
            .map { preset ->
                val pkg = presetBindings[preset.id]
                if (pkg.isNullOrBlank()) preset
                else preset.copy(boundPackageName = pkg)
            }
        val customs = loadCustomMeaningfulThings()
        return presets + customs
    }

    fun addCustomMeaningfulThing(
        title: String,
        boundPackageName: String? = null
    ): MeaningfulThing? {
        val t = title.trim().take(24)
        if (t.isEmpty()) return null
        val pkg = boundPackageName?.trim()?.takeIf { it.isNotEmpty() }
        val current = loadCustomMeaningfulThings()
        current.firstOrNull { it.title == t }?.let { existing ->
            if (pkg != null && existing.boundPackageName != pkg) {
                updateMeaningfulThing(existing.id, title = t, boundPackageName = pkg)
                return getMeaningfulThings().firstOrNull { it.id == existing.id }
            }
            return existing
        }
        if (current.size >= MAX_CUSTOM_MEANINGFUL_THINGS) return null
        val item = MeaningfulThing(
            id = MeaningfulThingsCatalog.customId(),
            title = t,
            isPreset = false,
            boundPackageName = pkg
        )
        saveCustomMeaningfulThings(current + item)
        bumpMeaningfulThings()
        return item
    }

    /**
     * 更新自定义标题 / 绑定；预置仅可改绑定（标题固定）。
     * [boundPackageName] 传空字符串表示清除绑定；传 null 表示不改绑定。
     */
    fun updateMeaningfulThing(
        id: String,
        title: String? = null,
        boundPackageName: String? = null
    ) {
        if (id.isBlank()) return
        val isPreset = MeaningfulThingsCatalog.PRESETS.any { it.id == id }
        if (isPreset) {
            if (boundPackageName != null) {
                val bindings = loadMeaningfulPresetBindings().toMutableMap()
                val pkg = boundPackageName.trim()
                if (pkg.isEmpty()) bindings.remove(id) else bindings[id] = pkg
                saveMeaningfulPresetBindings(bindings)
                bumpMeaningfulThings()
            }
            return
        }
        val current = loadCustomMeaningfulThings()
        val idx = current.indexOfFirst { it.id == id }
        if (idx < 0) return
        val old = current[idx]
        val nextTitle = title?.trim()?.take(24)?.takeIf { it.isNotEmpty() } ?: old.title
        val nextPkg = when {
            boundPackageName == null -> old.boundPackageName
            boundPackageName.isBlank() -> null
            else -> boundPackageName.trim()
        }
        val updated = current.toMutableList()
        updated[idx] = old.copy(title = nextTitle, boundPackageName = nextPkg)
        saveCustomMeaningfulThings(updated)
        bumpMeaningfulThings()
    }

    fun setMeaningfulThingBinding(id: String, packageName: String?) {
        updateMeaningfulThing(id, boundPackageName = packageName.orEmpty())
    }

    fun removeMeaningfulThing(id: String) {
        if (id.isBlank()) return
        if (MeaningfulThingsCatalog.PRESETS.any { it.id == id }) {
            val disabled = loadDisabledMeaningfulPresetIds().toMutableSet()
            disabled.add(id)
            prefs.edit {
                putStringSet(KEY_MEANINGFUL_DISABLED_PRESETS, disabled)
            }
            val bindings = loadMeaningfulPresetBindings().toMutableMap()
            if (bindings.remove(id) != null) {
                saveMeaningfulPresetBindings(bindings)
            }
        } else {
            saveCustomMeaningfulThings(loadCustomMeaningfulThings().filterNot { it.id == id })
        }
        bumpMeaningfulThings()
    }

    fun restoreMeaningfulPreset(id: String) {
        if (MeaningfulThingsCatalog.PRESETS.none { it.id == id }) return
        val disabled = loadDisabledMeaningfulPresetIds().toMutableSet()
        if (!disabled.remove(id)) return
        prefs.edit {
            putStringSet(KEY_MEANINGFUL_DISABLED_PRESETS, disabled)
        }
        bumpMeaningfulThings()
    }

    fun getDisabledMeaningfulPresetIds(): Set<String> = loadDisabledMeaningfulPresetIds()

    private fun bumpMeaningfulThings() {
        _meaningfulThingsRevision.value = _meaningfulThingsRevision.value + 1
    }

    private fun loadDisabledMeaningfulPresetIds(): Set<String> =
        prefs.getStringSet(KEY_MEANINGFUL_DISABLED_PRESETS, emptySet())?.toSet().orEmpty()

    private fun loadMeaningfulPresetBindings(): Map<String, String> {
        val raw = prefs.getString(KEY_MEANINGFUL_PRESET_BINDINGS, null).orEmpty()
        if (raw.isBlank()) return emptyMap()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 2) return@mapNotNull null
                val id = parts[0].trim()
                val pkg = parts[1].trim()
                if (id.isEmpty() || pkg.isEmpty()) null else id to pkg
            }
            .toMap()
    }

    private fun saveMeaningfulPresetBindings(map: Map<String, String>) {
        val encoded = map.entries.joinToString("\n") { (id, pkg) -> "$id\t$pkg" }
        prefs.edit { putString(KEY_MEANINGFUL_PRESET_BINDINGS, encoded) }
    }

    private fun loadCustomMeaningfulThings(): List<MeaningfulThing> {
        val raw = prefs.getString(KEY_MEANINGFUL_CUSTOM_JSON, null).orEmpty()
        if (raw.isBlank()) return emptyList()
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size < 2) return@mapNotNull null
                val id = parts[0].trim()
                val title = parts[1].trim()
                val pkg = parts.getOrNull(2)?.trim()?.takeIf { it.isNotEmpty() }
                if (id.isEmpty() || title.isEmpty()) null
                else MeaningfulThing(
                    id = id,
                    title = title,
                    isPreset = false,
                    boundPackageName = pkg
                )
            }
            .distinctBy { it.id }
            .take(MAX_CUSTOM_MEANINGFUL_THINGS)
            .toList()
    }

    private fun saveCustomMeaningfulThings(list: List<MeaningfulThing>) {
        val encoded = list.joinToString("\n") { item ->
            val title = item.title.replace("\t", " ").replace("\n", " ")
            val pkg = item.boundPackageName.orEmpty()
            "${item.id}\t$title\t$pkg"
        }
        prefs.edit { putString(KEY_MEANINGFUL_CUSTOM_JSON, encoded) }
    }

    // ── 想去的地方（正向 App，离开后的归属）────────────────────────────────

    /**
     * 用户配置的正向去处（有序，数量不限）。
     * 轻条最多露出 [MAX_POSITIVE_DISPLAY] 个；别名可选。
     */
    private val _positiveDestinations = MutableStateFlow(loadPositiveDestinations())
    val positiveDestinations: StateFlow<List<PositiveDestination>> = _positiveDestinations

    fun getPositiveDestinations(): List<PositiveDestination> = _positiveDestinations.value

    fun getPositiveDestinationPackages(): List<String> =
        _positiveDestinations.value.map { it.packageName }

    fun setPositiveDestinations(destinations: List<PositiveDestination>) {
        val normalized = destinations
            .map {
                it.copy(
                    packageName = it.packageName.trim(),
                    alias = it.alias?.trim()?.takeIf { a -> a.isNotEmpty() }?.take(12)
                )
            }
            .filter { it.packageName.isNotEmpty() }
            .distinctBy { it.packageName }
        prefs.edit {
            putString(KEY_POSITIVE_DESTINATIONS_JSON, encodePositiveDestinations(normalized))
            remove(KEY_POSITIVE_DESTINATIONS)
        }
        _positiveDestinations.value = normalized
        val preferred = _preferredPositiveDestination.value
        val pkgs = normalized.map { it.packageName }
        when {
            preferred != null && preferred !in pkgs ->
                setPreferredPositiveDestination(pkgs.firstOrNull())
            preferred == null && pkgs.isNotEmpty() ->
                setPreferredPositiveDestination(pkgs.first())
        }
    }

    fun setPositiveDestinationPackages(packages: List<String>) {
        val aliasMap = _positiveDestinations.value.associate { it.packageName to it.alias }
        setPositiveDestinations(
            packages.map { PositiveDestination(packageName = it, alias = aliasMap[it]) }
        )
    }

    fun togglePositiveDestination(packageName: String): Boolean {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return false
        val current = _positiveDestinations.value.toMutableList()
        val idx = current.indexOfFirst { it.packageName == pkg }
        if (idx >= 0) {
            current.removeAt(idx)
            setPositiveDestinations(current)
        } else {
            current.add(PositiveDestination(packageName = pkg))
            setPositiveDestinations(current)
        }
        return true
    }

    fun setPositiveDestinationAlias(packageName: String, alias: String?) {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return
        val updated = _positiveDestinations.value.map {
            if (it.packageName == pkg) {
                it.copy(alias = alias?.trim()?.takeIf { a -> a.isNotEmpty() }?.take(12))
            } else it
        }
        setPositiveDestinations(updated)
    }

    fun getPositiveDestination(packageName: String): PositiveDestination? =
        _positiveDestinations.value.firstOrNull { it.packageName == packageName }

    /** 最近一次主动选择的正向 App（用于轻条默认露出） */
    private val _preferredPositiveDestination = MutableStateFlow(
        prefs.getString(KEY_PREFERRED_POSITIVE_DESTINATION, null)
    )
    val preferredPositiveDestination: StateFlow<String?> = _preferredPositiveDestination

    fun setPreferredPositiveDestination(packageName: String?) {
        prefs.edit {
            if (packageName.isNullOrBlank()) remove(KEY_PREFERRED_POSITIVE_DESTINATION)
            else putString(KEY_PREFERRED_POSITIVE_DESTINATION, packageName)
        }
        _preferredPositiveDestination.value = packageName?.takeIf { it.isNotBlank() }
    }

    fun getPreferredPositiveDestination(): String? {
        val preferred = _preferredPositiveDestination.value
        val list = _positiveDestinations.value.map { it.packageName }
        if (preferred != null && preferred in list) return preferred
        return list.firstOrNull()
    }

    /**
     * 轻条展示用：优先默认项，再按列表顺序凑满 [MAX_POSITIVE_DISPLAY] 个。
     */
    fun getPositiveDestinationsForDisplay(): List<PositiveDestination> {
        val all = _positiveDestinations.value
        if (all.isEmpty()) return emptyList()
        val preferred = getPreferredPositiveDestination()
        val ordered = buildList {
            val primary = all.firstOrNull { it.packageName == preferred } ?: all.first()
            add(primary)
            all.filter { it.packageName != primary.packageName }.forEach { add(it) }
        }
        return ordered.take(MAX_POSITIVE_DISPLAY)
    }

    /** 拦截页主动点「离开」的累计次数（用于未配置时的引导节奏） */
    private val _explicitGateLeaveCount = MutableStateFlow(
        prefs.getInt(KEY_EXPLICIT_GATE_LEAVE_COUNT, 0)
    )
    val explicitGateLeaveCount: StateFlow<Int> = _explicitGateLeaveCount

    fun incrementExplicitGateLeaveCount(): Int {
        val next = _explicitGateLeaveCount.value + 1
        prefs.edit { putInt(KEY_EXPLICIT_GATE_LEAVE_COUNT, next) }
        _explicitGateLeaveCount.value = next
        return next
    }

    fun getExplicitGateLeaveCount(): Int = _explicitGateLeaveCount.value

    /** 上次展示「可设想去的地方」引导时的累计离开次数 */
    private val _lastPositiveSetupNudgeAtLeave = MutableStateFlow(
        prefs.getInt(KEY_LAST_POSITIVE_SETUP_NUDGE_AT_LEAVE, 0)
    )

    fun getLastPositiveSetupNudgeAtLeave(): Int = _lastPositiveSetupNudgeAtLeave.value

    fun markPositiveSetupNudgeShown(atLeaveCount: Int) {
        prefs.edit { putInt(KEY_LAST_POSITIVE_SETUP_NUDGE_AT_LEAVE, atLeaveCount) }
        _lastPositiveSetupNudgeAtLeave.value = atLeaveCount
    }

    /**
     * 未配置正向 App 时，是否在本次主动离开后展示设置引导。
     * 节奏：第 2 次主动离开出现；之后每满 4 次再出现一次，直到配置。
     */
    fun shouldOfferPositiveSetupNudge(): Boolean {
        if (_positiveDestinations.value.isNotEmpty()) return false
        val leaves = _explicitGateLeaveCount.value
        if (leaves < 2) return false
        val last = _lastPositiveSetupNudgeAtLeave.value
        if (last <= 0) return leaves >= 2
        return leaves - last >= 4
    }

    private fun loadPositiveDestinations(): List<PositiveDestination> {
        val json = prefs.getString(KEY_POSITIVE_DESTINATIONS_JSON, null)
        if (!json.isNullOrBlank()) {
            return decodePositiveDestinations(json)
        }
        // 迁移旧版「仅包名」格式
        val legacy = prefs.getString(KEY_POSITIVE_DESTINATIONS, null).orEmpty()
        if (legacy.isBlank()) return emptyList()
        val migrated = legacy.split("|")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .map { PositiveDestination(packageName = it) }
        if (migrated.isNotEmpty()) {
            prefs.edit {
                putString(KEY_POSITIVE_DESTINATIONS_JSON, encodePositiveDestinations(migrated))
                remove(KEY_POSITIVE_DESTINATIONS)
            }
        }
        return migrated
    }

    private fun encodePositiveDestinations(list: List<PositiveDestination>): String {
        // 轻量自研编码：pkg\talias\npkg\talias …（alias 可空）
        return list.joinToString("\n") { dest ->
            val alias = dest.alias.orEmpty().replace("\t", " ").replace("\n", " ")
            "${dest.packageName}\t$alias"
        }
    }

    private fun decodePositiveDestinations(raw: String): List<PositiveDestination> {
        return raw.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split("\t", limit = 2)
                val pkg = parts.getOrNull(0)?.trim().orEmpty()
                if (pkg.isEmpty()) return@mapNotNull null
                val alias = parts.getOrNull(1)?.trim()?.takeIf { it.isNotEmpty() }?.take(12)
                PositiveDestination(packageName = pkg, alias = alias)
            }
            .distinctBy { it.packageName }
            .toList()
    }

    // ── 拦截主题（MVP 固定极简；保留 API 兼容旧调用）──────────────────────────

    private val _interceptThemeId = MutableStateFlow("simple").also { flow ->
        val stored = prefs.getString(KEY_INTERCEPT_THEME, "simple") ?: "simple"
        if (stored != "simple") {
            prefs.edit { putString(KEY_INTERCEPT_THEME, "simple") }
        }
        flow.value = "simple"
    }

    /** 当前拦截主题 ID（MVP 始终为 simple） */
    val interceptThemeId: StateFlow<String> = _interceptThemeId

    fun setInterceptThemeId(themeId: String) {
        prefs.edit { putString(KEY_INTERCEPT_THEME, "simple") }
        _interceptThemeId.value = "simple"
    }

    fun getInterceptThemeId(): String = "simple"

    // ── VIP 状态 ──────────────────────────────────────────────────────────────

    /**
     * VIP 等级：
     *   0 = 免费版
     *   1 = 标准版（Standard）
     *   2 = 高级版（Premium）
     */
    private val _vipLevel = MutableStateFlow(
        prefs.getInt(KEY_VIP_LEVEL, 0)
    )
    val vipLevel: StateFlow<Int> = _vipLevel

    /** VIP 过期时间戳（毫秒），0 表示永久有效（买断制）或未激活 */
    private val _vipExpireTime = MutableStateFlow(
        prefs.getLong(KEY_VIP_EXPIRE_TIME, 0L)
    )
    val vipExpireTime: StateFlow<Long> = _vipExpireTime

    /** 是否为 VIP（等级 ≥ 1 且未过期） */
    val isVip: StateFlow<Boolean> = MutableStateFlow(computeIsVip()).also { flow ->
        // 每次修改 vipLevel 或 vipExpireTime 时重新计算
    }

    private fun computeIsVip(): Boolean {
        val level = _vipLevel.value
        val expire = _vipExpireTime.value
        if (level <= 0) return false
        // 0 表示永久（买断），否则检查是否过期
        return expire == 0L || expire > System.currentTimeMillis()
    }

    /**
     * 当前是否 VIP（非 Flow 版，供同步调用）。
     * 免费全开期（FREE_PERIOD_ENABLED = true）时始终返回 true。
     * 正式版：邀请码兑换或购买后为 VIP。
     */
    fun isVipActive(): Boolean = FREE_PERIOD_ENABLED || computeIsVip()

    /**
     * 当前是否高级版（等级 2）。
     * 免费全开期时同样视为高级版。
     */
    fun isPremium(): Boolean = FREE_PERIOD_ENABLED || (_vipLevel.value >= 2 && computeIsVip())

    /** 获取 VIP 等级 */
    fun getVipLevel(): Int = _vipLevel.value

    /** 保存 VIP 状态（由 VipRepository 在购买/验证后调用） */
    fun saveVipStatus(level: Int, expireTime: Long) {
        prefs.edit {
            putInt(KEY_VIP_LEVEL, level)
            putLong(KEY_VIP_EXPIRE_TIME, expireTime)
        }
        _vipLevel.value = level
        _vipExpireTime.value = expireTime
    }

    /** 清除 VIP 状态（订阅过期时调用） */
    fun clearVipStatus() {
        prefs.edit {
            putInt(KEY_VIP_LEVEL, 0)
            putLong(KEY_VIP_EXPIRE_TIME, 0L)
        }
        _vipLevel.value = 0
        _vipExpireTime.value = 0L
    }

    /** 免费版 App 监控数量上限 */
    val freeMonitorLimit: Int get() = FREE_MONITOR_LIMIT

    /** 本机是否已使用过 7 天免费试用 */
    var hasUsedTrial: Boolean
        get() = prefs.getBoolean(KEY_HAS_USED_TRIAL, false)
        set(value) { prefs.edit { putBoolean(KEY_HAS_USED_TRIAL, value) } }

    // ── 邀请码（设备级 VIP 兑换，无账号）──────────────────────────────────

    private val _claimedInviteCode = MutableStateFlow(
        prefs.getString(KEY_CLAIMED_INVITE_CODE, null)
            ?: prefs.getString(KEY_BETA_CODE, "")
            ?: ""
    )
    val claimedInviteCode: StateFlow<String> = _claimedInviteCode

    private val _betaUnlocked = MutableStateFlow(
        prefs.getBoolean(KEY_BETA_UNLOCKED, false)
    )

    /** 本机是否已兑换过邀请码（开通 VIP） */
    val betaUnlocked: StateFlow<Boolean> = _betaUnlocked

    fun isBetaUnlocked(): Boolean = _betaUnlocked.value

    fun setBetaUnlocked(unlocked: Boolean, code: String = "") {
        prefs.edit {
            putBoolean(KEY_BETA_UNLOCKED, unlocked)
            if (code.isNotBlank()) {
                putString(KEY_BETA_CODE, code.trim().uppercase())
                putString(KEY_CLAIMED_INVITE_CODE, code.trim().uppercase())
            }
            if (unlocked && !prefs.contains(KEY_BETA_REDEEMED_AT)) {
                putLong(KEY_BETA_REDEEMED_AT, System.currentTimeMillis())
            }
        }
        _betaUnlocked.value = unlocked
        if (code.isNotBlank()) {
            _claimedInviteCode.value = code.trim().uppercase()
        }
    }

    /** 记录会员码兑换时间（毫秒）；用于本地早鸟档位兜底估算 */
    fun setBetaRedeemedAt(epochMs: Long) {
        if (epochMs <= 0L) return
        prefs.edit { putLong(KEY_BETA_REDEEMED_AT, epochMs) }
    }

    fun getBetaRedeemedAt(): Long = prefs.getLong(KEY_BETA_REDEEMED_AT, 0L)

    fun getBetaCode(): String = prefs.getString(KEY_BETA_CODE, "") ?: ""

    fun getClaimedInviteCode(): String = _claimedInviteCode.value

    /**
     * 埋点是否上报意图原文（≤80 字）。
     * 内测默认开启；正式版可关闭，仅保留 clarity / len。
     */
    var analyticsReportPurposeFullText: Boolean
        get() = prefs.getBoolean(KEY_ANALYTICS_PURPOSE_FULL, true)
        set(value) {
            prefs.edit { putBoolean(KEY_ANALYTICS_PURPOSE_FULL, value) }
        }

    /** 某包名上次成功上报的图标 hash；相同则跳过。 */
    fun getAnalyticsAppIconHash(pkg: String): String {
        val key = pkg.trim()
        if (key.isEmpty()) return ""
        return analyticsAppIconHashes()[key].orEmpty()
    }

    fun setAnalyticsAppIconHash(pkg: String, hash: String) {
        val key = pkg.trim()
        if (key.isEmpty()) return
        val next = analyticsAppIconHashes().toMutableMap()
        if (hash.isBlank()) next.remove(key) else next[key] = hash.take(64)
        while (next.size > 80) {
            val oldest = next.keys.firstOrNull() ?: break
            next.remove(oldest)
        }
        val obj = org.json.JSONObject()
        next.forEach { (k, v) -> obj.put(k, v) }
        prefs.edit { putString(KEY_ANALYTICS_APP_ICON_HASHES, obj.toString()) }
    }

    private fun analyticsAppIconHashes(): Map<String, String> {
        val raw = prefs.getString(KEY_ANALYTICS_APP_ICON_HASHES, "").orEmpty()
        if (raw.isBlank()) return emptyMap()
        return runCatching {
            val obj = org.json.JSONObject(raw)
            buildMap {
                obj.keys().forEach { k ->
                    val v = obj.optString(k, "")
                    if (k.isNotBlank() && v.isNotBlank()) put(k, v)
                }
            }
        }.getOrDefault(emptyMap())
    }

    fun setClaimedInviteCode(code: String) {
        val normalized = code.trim().uppercase()
        prefs.edit {
            if (normalized.isBlank()) remove(KEY_CLAIMED_INVITE_CODE)
            else putString(KEY_CLAIMED_INVITE_CODE, normalized)
        }
        _claimedInviteCode.value = normalized
    }

    /** 是否已是永久会员（等级≥1 且 expire=0） */
    fun isLifetimeVip(): Boolean = _vipLevel.value > 0 && _vipExpireTime.value == 0L

    /**
     * 旧版「邀请码仅准入」用户：已兑码但尚未写入 VIP 时，补发 VIP，
     * 避免关闭免费期后被误判为免费版。
     */
    fun migrateInviteUnlockToVipIfNeeded() {
        if (!_betaUnlocked.value || computeIsVip()) return
        // 旧版已兑码用户：保留永久会员，避免权益回退
        saveVipStatus(level = 1, expireTime = 0L)
    }

    // ── 格言单向点赞（本机已赞集合，不可取消）────────────────────────────────

    fun hasLikedQuote(quoteId: Int): Boolean {
        if (quoteId <= 0) return false
        return prefs.getStringSet(KEY_LIKED_QUOTE_IDS, emptySet()).orEmpty().contains(quoteId.toString())
    }

    fun markQuoteLiked(quoteId: Int) {
        if (quoteId <= 0) return
        val next = prefs.getStringSet(KEY_LIKED_QUOTE_IDS, emptySet()).orEmpty().toMutableSet()
        if (!next.add(quoteId.toString())) return
        prefs.edit { putStringSet(KEY_LIKED_QUOTE_IDS, next) }
    }

    // ── 应用内更新：跳过的 versionCode ──────────────────────────────────────

    fun getSkippedUpdateVersionCode(): Int =
        prefs.getInt(KEY_SKIPPED_UPDATE_VERSION_CODE, 0)

    /** 已在首页弹窗提示过的可选更新 versionCode（每个版本只弹一次） */
    fun getLastPromptedUpdateVersionCode(): Int =
        prefs.getInt(KEY_LAST_PROMPTED_UPDATE_VERSION_CODE, 0)

    fun setLastPromptedUpdateVersionCode(versionCode: Int) {
        prefs.edit {
            putInt(KEY_LAST_PROMPTED_UPDATE_VERSION_CODE, versionCode.coerceAtLeast(0))
        }
    }

    // ── 官网渠道：未完成转账订单（关弹窗后仍可恢复）────────────────────────

    fun savePendingHaOrder(order: PendingHaOrder) {
        prefs.edit {
            putString(KEY_PENDING_HA_ORDER_NO, order.orderNo)
            putString(KEY_PENDING_HA_PLAN_ID, order.planId)
            putString(KEY_PENDING_HA_PLAN_TITLE, order.planTitle)
            putString(KEY_PENDING_HA_AMOUNT_YUAN, order.amountYuan)
            putString(KEY_PENDING_HA_LIST_AMOUNT_YUAN, order.listAmountYuan)
            putBoolean(KEY_PENDING_HA_EARLY_BIRD, order.earlyBird)
            putString(KEY_PENDING_HA_TIER_LABEL, order.tierLabel)
            putString(KEY_PENDING_HA_CONTACT, order.contactWechat)
            putString(KEY_PENDING_HA_REMARK, order.remarkText)
            putString(KEY_PENDING_HA_BENEFIT, order.benefitSummary)
            putBoolean(KEY_PENDING_HA_NOTIFIED, order.notified)
            putLong(KEY_PENDING_HA_CREATED_AT, order.createdAt)
            putString(KEY_PENDING_HA_PHONE, order.phone)
        }
    }

    fun getPendingHaOrder(): PendingHaOrder? {
        val orderNo = prefs.getString(KEY_PENDING_HA_ORDER_NO, "") ?: ""
        if (orderNo.isBlank()) return null
        return PendingHaOrder(
            orderNo = orderNo,
            planId = prefs.getString(KEY_PENDING_HA_PLAN_ID, "") ?: "",
            planTitle = prefs.getString(KEY_PENDING_HA_PLAN_TITLE, "") ?: "",
            amountYuan = prefs.getString(KEY_PENDING_HA_AMOUNT_YUAN, "") ?: "",
            listAmountYuan = prefs.getString(KEY_PENDING_HA_LIST_AMOUNT_YUAN, "") ?: "",
            earlyBird = prefs.getBoolean(KEY_PENDING_HA_EARLY_BIRD, false),
            tierLabel = prefs.getString(KEY_PENDING_HA_TIER_LABEL, "") ?: "",
            contactWechat = prefs.getString(KEY_PENDING_HA_CONTACT, CONTACT_WECHAT)
                ?: CONTACT_WECHAT,
            remarkText = prefs.getString(KEY_PENDING_HA_REMARK, "") ?: "",
            benefitSummary = prefs.getString(KEY_PENDING_HA_BENEFIT, "") ?: "",
            notified = prefs.getBoolean(KEY_PENDING_HA_NOTIFIED, false),
            createdAt = prefs.getLong(KEY_PENDING_HA_CREATED_AT, 0L),
            phone = prefs.getString(KEY_PENDING_HA_PHONE, "") ?: ""
        )
    }

    fun markPendingHaOrderNotified() {
        if ((prefs.getString(KEY_PENDING_HA_ORDER_NO, "") ?: "").isBlank()) return
        prefs.edit { putBoolean(KEY_PENDING_HA_NOTIFIED, true) }
    }

    fun clearPendingHaOrder() {
        prefs.edit {
            remove(KEY_PENDING_HA_ORDER_NO)
            remove(KEY_PENDING_HA_PLAN_ID)
            remove(KEY_PENDING_HA_PLAN_TITLE)
            remove(KEY_PENDING_HA_AMOUNT_YUAN)
            remove(KEY_PENDING_HA_LIST_AMOUNT_YUAN)
            remove(KEY_PENDING_HA_EARLY_BIRD)
            remove(KEY_PENDING_HA_TIER_LABEL)
            remove(KEY_PENDING_HA_CONTACT)
            remove(KEY_PENDING_HA_REMARK)
            remove(KEY_PENDING_HA_BENEFIT)
            remove(KEY_PENDING_HA_NOTIFIED)
            remove(KEY_PENDING_HA_CREATED_AT)
            remove(KEY_PENDING_HA_PHONE)
        }
    }

    fun setBoundPhone(phone: String) {
        val normalized = phone.filter { it.isDigit() }.let { digits ->
            when {
                digits.length == 11 && digits.startsWith("1") -> digits
                digits.length == 13 && digits.startsWith("86") -> digits.substring(2)
                else -> digits.takeLast(11).takeIf { it.length == 11 && it.startsWith("1") } ?: ""
            }
        }
        prefs.edit {
            if (normalized.isBlank()) remove(KEY_BOUND_PHONE)
            else putString(KEY_BOUND_PHONE, normalized)
        }
    }

    fun getBoundPhone(): String = prefs.getString(KEY_BOUND_PHONE, "") ?: ""

    fun setSkippedUpdateVersionCode(versionCode: Int) {
        prefs.edit { putInt(KEY_SKIPPED_UPDATE_VERSION_CODE, versionCode.coerceAtLeast(0)) }
    }

    // ── 升级后「本版更新说明」────────────────────────────────────────────────

    /**
     * 用户已看过更新说明的 versionCode。
     * `-1` 表示从未写入（新装或功能首次上线）：应静默种子为当前版本，不弹窗。
     */
    fun getLastSeenWhatsNewVersionCode(): Int =
        prefs.getInt(KEY_LAST_SEEN_WHATS_NEW_VERSION_CODE, -1)

    fun markWhatsNewSeen(versionCode: Int) {
        val cachedCode = prefs.getInt(KEY_CACHED_WHATS_NEW_VERSION_CODE, 0)
        prefs.edit {
            putInt(KEY_LAST_SEEN_WHATS_NEW_VERSION_CODE, versionCode.coerceAtLeast(0))
            if (cachedCode == versionCode) {
                remove(KEY_CACHED_WHATS_NEW_VERSION_CODE)
                remove(KEY_CACHED_WHATS_NEW_VERSION_NAME)
                remove(KEY_CACHED_WHATS_NEW_CHANGELOG)
            }
        }
    }

    /** 升级前提示时缓存 changelog，安装后可离线展示 */
    fun cacheWhatsNewNotes(versionCode: Int, versionName: String, changelog: String) {
        val notes = changelog.trim()
        if (versionCode <= 0 || notes.isEmpty()) return
        prefs.edit {
            putInt(KEY_CACHED_WHATS_NEW_VERSION_CODE, versionCode)
            putString(KEY_CACHED_WHATS_NEW_VERSION_NAME, versionName)
            putString(KEY_CACHED_WHATS_NEW_CHANGELOG, notes)
        }
    }

    fun getCachedWhatsNewNotes(forVersionCode: Int): Triple<Int, String, String>? {
        val code = prefs.getInt(KEY_CACHED_WHATS_NEW_VERSION_CODE, 0)
        if (code != forVersionCode || code <= 0) return null
        val name = prefs.getString(KEY_CACHED_WHATS_NEW_VERSION_NAME, "") ?: ""
        val notes = prefs.getString(KEY_CACHED_WHATS_NEW_CHANGELOG, "")?.trim().orEmpty()
        if (notes.isEmpty()) return null
        return Triple(code, name, notes)
    }

    // ── 周日「周回望」轻提示（按周 dismiss）────────────────────────────────

    private val _weekLookbackTipDismissedWeekStart = MutableStateFlow(
        prefs.getLong(KEY_WEEK_LOOKBACK_TIP_DISMISSED_WEEK_START, 0L)
    )

    /** 用户关闭轻提示时所在周的 weekStartMs；0 表示从未关闭 */
    val weekLookbackTipDismissedWeekStart: StateFlow<Long> = _weekLookbackTipDismissedWeekStart

    fun dismissWeekLookbackTip(weekStartMs: Long) {
        prefs.edit { putLong(KEY_WEEK_LOOKBACK_TIP_DISMISSED_WEEK_START, weekStartMs) }
        _weekLookbackTipDismissedWeekStart.value = weekStartMs
    }

    // ── 日时长触顶后的唯一宽限（每 App 每日一次）────────────────────────────

    private fun todayDateKey(): String =
        java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault())
            .format(java.util.Date())

    /** 该 App 今日是否已使用过日限触顶延长 */
    fun hasUsedDailyGraceToday(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        val stored = prefs.getString(KEY_DAILY_GRACE_PREFIX + packageName, null) ?: return false
        return stored == todayDateKey()
    }

    /** 今日已生效的延长分钟数（展示用 +N）；未使用则为 0 */
    fun getDailyGraceBonusMinutes(packageName: String): Int {
        if (!hasUsedDailyGraceToday(packageName)) return 0
        return prefs.getInt(KEY_DAILY_GRACE_MINUTES_PREFIX + packageName, 0).coerceAtLeast(0)
    }

    /**
     * 触顶延长后的绝对日限额天花板（秒）。
     * 「延长 N 分钟」= 从点击时已用时长起再给 N 分钟，故 ceiling = usedAtGrant + N*60。
     * 未使用延长则为 0。
     */
    fun getDailyGraceCeilingSeconds(packageName: String): Long {
        if (!hasUsedDailyGraceToday(packageName)) return 0L
        return prefs.getLong(KEY_DAILY_GRACE_CEILING_PREFIX + packageName, 0L).coerceAtLeast(0L)
    }

    /**
     * 记录今日触顶延长。
     * @param minutes 用户感知的延长分钟（胶囊 +N）
     * @param ceilingSeconds 绝对日限额天花板；应传 usedAtGrant + minutes*60
     */
    fun markDailyGraceUsed(
        packageName: String,
        minutes: Int = com.life.mindfulnessapp.domain.model.BreathCostPolicy.DAILY_GRACE_MINUTES,
        ceilingSeconds: Long = 0L
    ) {
        if (packageName.isBlank()) return
        prefs.edit {
            putString(KEY_DAILY_GRACE_PREFIX + packageName, todayDateKey())
            putInt(KEY_DAILY_GRACE_MINUTES_PREFIX + packageName, minutes.coerceAtLeast(0))
            putLong(KEY_DAILY_GRACE_CEILING_PREFIX + packageName, ceilingSeconds.coerceAtLeast(0L))
        }
    }

    // ── 时段 / 日程硬门 · 今日豁免进入（每 App 每日一次）──────────────────

    /** 该 App 今日是否已用过时段硬门豁免进入 */
    fun hasUsedPeriodExemptionToday(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        val stored = prefs.getString(KEY_PERIOD_EXEMPTION_PREFIX + packageName, null) ?: return false
        return stored == todayDateKey()
    }

    /** 今日硬门豁免剩余次数（0 或 [PeriodLockPolicy.EXEMPTIONS_PER_DAY]） */
    fun periodExemptionRemainingToday(packageName: String): Int {
        if (packageName.isBlank()) return 0
        return if (hasUsedPeriodExemptionToday(packageName)) {
            0
        } else {
            com.life.mindfulnessapp.domain.model.PeriodLockPolicy.EXEMPTIONS_PER_DAY
        }
    }

    fun markPeriodExemptionUsed(packageName: String) {
        if (packageName.isBlank()) return
        prefs.edit {
            putString(KEY_PERIOD_EXEMPTION_PREFIX + packageName, todayDateKey())
        }
    }

    /** 时段硬门「紧急进入」是否已选择不再显示首次确认 */
    fun shouldSkipPeriodEmergencyEnterTip(): Boolean =
        prefs.getBoolean(KEY_PERIOD_EMERGENCY_ENTER_TIP_SKIPPED, false)

    fun setSkipPeriodEmergencyEnterTip(skip: Boolean) {
        prefs.edit { putBoolean(KEY_PERIOD_EMERGENCY_ENTER_TIP_SKIPPED, skip) }
    }

    /**
     * 搜索型门 ·「其他意图」今日已用次数（按包名 + 日期）。
     * 搜索直达不计入；手写 / 预设 / 随便看看计入。
     */
    fun getExploreOtherIntentUsedToday(packageName: String): Int {
        if (packageName.isBlank()) return 0
        val raw = prefs.getString(KEY_EXPLORE_OTHER_INTENT_PREFIX + packageName, null) ?: return 0
        val parts = raw.split('|')
        if (parts.size < 2) return 0
        if (parts[0] != todayDateKey()) return 0
        return parts[1].toIntOrNull()?.coerceAtLeast(0) ?: 0
    }

    fun incrementExploreOtherIntentUsedToday(packageName: String): Int {
        if (packageName.isBlank()) return 0
        val next = getExploreOtherIntentUsedToday(packageName) + 1
        prefs.edit {
            putString(KEY_EXPLORE_OTHER_INTENT_PREFIX + packageName, "${todayDateKey()}|$next")
        }
        return next
    }

    /** 该 App 今日是否已播过意图门入场动效（每日首次进入才播） */
    fun hasInterceptEntranceAnimPlayedToday(packageName: String): Boolean {
        if (packageName.isBlank()) return true
        val stored = prefs.getString(KEY_INTERCEPT_ENTRANCE_ANIM_PREFIX + packageName, null) ?: return false
        return stored == todayDateKey()
    }

    fun markInterceptEntranceAnimPlayedToday(packageName: String) {
        if (packageName.isBlank()) return
        prefs.edit { putString(KEY_INTERCEPT_ENTRANCE_ANIM_PREFIX + packageName, todayDateKey()) }
    }

    // ── 日限额临近洗灰（每 App 每日一次武装，续时 / 再进仍保持）──────────────

    /** 该 App 今日是否已武装日限额洗灰 */
    fun hasDailyLimitGrayWashToday(packageName: String): Boolean {
        if (packageName.isBlank()) return false
        val stored = prefs.getString(KEY_DAILY_GRAY_WASH_PREFIX + packageName, null) ?: return false
        return stored == todayDateKey()
    }

    /** 今日洗灰渐变起点（墙钟 ms）；未武装为 0 */
    fun getDailyLimitGrayWashStartedAtMs(packageName: String): Long {
        if (!hasDailyLimitGrayWashToday(packageName)) return 0L
        return prefs.getLong(KEY_DAILY_GRAY_WASH_STARTED_MS_PREFIX + packageName, 0L)
            .coerceAtLeast(0L)
    }

    /**
     * 记录今日首次进入日限额洗灰区。
     * 已武装则保持原起点（不重开 30s 渐变）。
     */
    fun markDailyLimitGrayWashStarted(
        packageName: String,
        startedAtMs: Long = System.currentTimeMillis()
    ) {
        if (packageName.isBlank()) return
        if (hasDailyLimitGrayWashToday(packageName)) return
        prefs.edit {
            putString(KEY_DAILY_GRAY_WASH_PREFIX + packageName, todayDateKey())
            putLong(
                KEY_DAILY_GRAY_WASH_STARTED_MS_PREFIX + packageName,
                startedAtMs.coerceAtLeast(0L)
            )
        }
    }

    // ── 系统真灰度持有（崩溃恢复用）──────────────────────────────────────────

    fun isDisplayGrayscaleHeldByUs(): Boolean =
        prefs.getBoolean(KEY_DISPLAY_GRAYSCALE_HELD, false)

    fun getDisplayGrayscalePreviousEnabled(): Int =
        prefs.getInt(KEY_DISPLAY_GRAYSCALE_PREV_ENABLED, 0)

    fun getDisplayGrayscalePreviousMode(): Int =
        prefs.getInt(KEY_DISPLAY_GRAYSCALE_PREV_MODE, -1)

    fun markDisplayGrayscaleHeld(
        held: Boolean,
        previousEnabled: Int = 0,
        previousMode: Int = -1
    ) {
        prefs.edit {
            putBoolean(KEY_DISPLAY_GRAYSCALE_HELD, held)
            if (held) {
                putInt(KEY_DISPLAY_GRAYSCALE_PREV_ENABLED, previousEnabled)
                putInt(KEY_DISPLAY_GRAYSCALE_PREV_MODE, previousMode)
            } else {
                remove(KEY_DISPLAY_GRAYSCALE_PREV_ENABLED)
                remove(KEY_DISPLAY_GRAYSCALE_PREV_MODE)
            }
        }
    }

    /**
     * 生效的今日限额秒数：max(基础限额, 延长天花板)。
     * 天花板 = 触顶授予时「已用 + N 分钟」；用户随后把基础限额抬高并超过天花板时，
     * 以新基础为准，不再叠加额外的 +N（避免 90 分基础变成 100）。
     */
    fun effectiveDailyLimitSeconds(packageName: String, baseMinutes: Int): Long {
        if (baseMinutes <= 0) return 0L
        val baseSec = baseMinutes * 60L
        val ceiling = getDailyGraceCeilingSeconds(packageName)
        return if (ceiling > 0L) maxOf(baseSec, ceiling) else baseSec
    }

    companion object {
        private const val KEY_DARK_THEME           = "dark_theme_enabled"
        private const val KEY_THEME_MODE           = "theme_mode"
        private const val KEY_THEME_PACK           = "theme_pack"
        private const val KEY_THEME_FOLLOW_SYSTEM  = "theme_follow_system"
        private const val KEY_INTERCEPT_THEME      = "intercept_theme_id"
        // 加强保活
        private const val KEY_ENHANCED_KEEP_ALIVE = "enhanced_keep_alive"
        private const val KEY_HIDE_FROM_RECENTS = "hide_from_recents"
        // 步行觉察（全局）
        private const val KEY_WALK_AWARENESS_ENABLED = "walk_awareness_enabled"
        // 觉察练习（发现 · 默认关）
        private const val KEY_AWARENESS_PRACTICE_ENABLED = "awareness_practice_enabled"
        private const val KEY_AWARENESS_PRACTICE_RHYTHM = "awareness_practice_rhythm"
        // 试玩（beta）
        private const val KEY_PLAYGROUND_FEED_SHADE = "playground_feed_shade"
        private const val KEY_PLAYGROUND_INTERCEPT_WORD = "playground_intercept_word"
        /** 桌面心锚微粒开关（默认开） */
        private const val KEY_DESKTOP_ANCHOR_ENABLED = "desktop_anchor_enabled"
        /** 桌面心锚自由悬浮坐标 */
        private const val KEY_DESKTOP_ANCHOR_FLOAT = "desktop_anchor_float"
        /** Debug：强制日限额洗灰 */
        private const val KEY_DEBUG_FORCE_GRAY_WASH = "debug_force_gray_wash"
        // 胶囊已用显示秒
        private const val KEY_CAPSULE_USED_SHOW_SECONDS = "capsule_used_show_seconds"
        // 迷你胶囊尺寸
        private const val KEY_CAPSULE_MINI_SIZE = "capsule_mini_size"
        private const val KEY_CAPSULE_SHELL_OPACITY = "capsule_shell_opacity"
        private const val KEY_COMPANION_BAR_ENABLED = "companion_bar_enabled"
        private const val KEY_COMPANION_BAR_FORM = "companion_bar_form"
        private const val KEY_COMPANION_MODE_PKG_PREFIX = "companion_mode_pkg_"
        // 定时格言推送（时段 + 间隔）
        private const val KEY_SCHEDULED_QUOTE_PUSH_ENABLED = "scheduled_quote_push_enabled"
        private const val KEY_SCHEDULED_QUOTE_PUSH_RETIRED = "scheduled_quote_push_retired"
        private const val KEY_SCHEDULED_QUOTE_PUSH_TIMES = "scheduled_quote_push_times"
        private const val KEY_SCHEDULED_QUOTE_PUSH_START = "scheduled_quote_push_start"
        private const val KEY_SCHEDULED_QUOTE_PUSH_END = "scheduled_quote_push_end"
        private const val KEY_SCHEDULED_QUOTE_PUSH_INTERVAL = "scheduled_quote_push_interval"
        private const val KEY_SCHEDULED_QUOTE_PUSH_MIGRATED = "scheduled_quote_push_migrated"
        private const val KEY_SCHEDULED_QUOTE_PUSH_MIGRATED_V2 = "scheduled_quote_push_migrated_v2"
        private const val KEY_SCHEDULED_QUOTE_FIRED_DAY = "scheduled_quote_fired_day"
        private const val KEY_SCHEDULED_QUOTE_FIRED_SLOTS = "scheduled_quote_fired_slots"
        private const val KEY_LAST_PUSHED_QUOTE_ID = "last_pushed_quote_id"
        private const val KEY_LAST_PUSHED_QUOTE_CONTENT = "last_pushed_quote_content"
        private const val KEY_LAST_PUSHED_QUOTE_AUTHOR = "last_pushed_quote_author"
        private const val KEY_LAST_PUSHED_QUOTE_AUTHOR_ID = "last_pushed_quote_author_id"
        private const val KEY_LAST_PUSHED_QUOTE_AT = "last_pushed_quote_at"
        private const val KEY_FAVORITE_QUOTES_JSON = "favorite_quotes_json"
        /** 停住门页脚注格言；缺省 true */
        private const val KEY_STOP_QUOTE_ENABLED = "stop_quote_enabled"
        /** 默认：08:00–22:00，每 2 小时 */
        const val DEFAULT_SCHEDULED_QUOTE_PUSH_START = 8 * 60
        const val DEFAULT_SCHEDULED_QUOTE_PUSH_END = 22 * 60
        const val DEFAULT_SCHEDULED_QUOTE_PUSH_INTERVAL = 120
        val QUOTE_PUSH_INTERVAL_OPTIONS = listOf(30, 60, 90, 120, 180, 240)
        const val MAX_FAVORITE_QUOTES = 200
        @Deprecated("Use window+interval")
        val DEFAULT_SCHEDULED_QUOTE_PUSH_TIMES = listOf(8 * 60, 12 * 60 + 30, 21 * 60)
        @Deprecated("Use window+interval")
        const val MAX_SCHEDULED_QUOTE_PUSH_TIMES = 12
        private const val KEY_INTERCEPT_QUOTE_ENABLED = "intercept_quote_enabled"
        private const val KEY_INTERCEPT_QUOTE_SOURCE = "intercept_quote_source"

        fun normalizeQuotePushInterval(minutes: Int): Int {
            val opts = QUOTE_PUSH_INTERVAL_OPTIONS
            return opts.minByOrNull { kotlin.math.abs(it - minutes) } ?: DEFAULT_SCHEDULED_QUOTE_PUSH_INTERVAL
        }

        fun formatQuotePushInterval(minutes: Int): String = when {
            minutes < 60 -> "${minutes} 分钟"
            minutes % 60 == 0 -> "${minutes / 60} 小时"
            else -> "${minutes / 60} 小时 ${minutes % 60} 分"
        }

        fun decodeScheduledQuotePushTimes(raw: String): List<Int> =
            raw.split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .map { it.coerceIn(0, 1439) }
                .distinct()
                .sorted()

        fun encodeFavoriteQuotes(quotes: List<CachedPushedQuote>): String {
            if (quotes.isEmpty()) return ""
            return org.json.JSONArray().apply {
                quotes.forEach { q ->
                    put(org.json.JSONObject().apply {
                        put("id", q.id)
                        put("content", q.content)
                        put("author", q.author)
                        put("authorId", q.authorId)
                        put("pushedAt", q.pushedAt)
                    })
                }
            }.toString()
        }

        fun decodeFavoriteQuotes(json: String): List<CachedPushedQuote> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = org.json.JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.getJSONObject(i)
                        val content = o.optString("content").trim()
                        if (content.isBlank()) continue
                        add(
                            CachedPushedQuote(
                                id = o.optInt("id", 0),
                                content = content,
                                author = o.optString("author"),
                                authorId = o.optInt("authorId", 0),
                                pushedAt = o.optLong("pushedAt", 0L)
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        private const val KEY_AUTHOR_CATALOG_JSON = "author_catalog_json"
        private const val KEY_AUTHOR_CATALOG_SYNCED_AT = "author_catalog_synced_at"
        private const val KEY_QUOTE_SUBSCRIBED_SOURCE_AUTO_MIGRATED =
            "quote_subscribed_source_auto_migrated"
        const val INTERCEPT_QUOTE_SOURCE_SYSTEM = "system"
        /** 使用已订阅名人的名言池 */
        const val INTERCEPT_QUOTE_SOURCE_LIBRARY = "library"
        const val CAPSULE_MINI_SIZE_STANDARD = "standard"
        const val CAPSULE_MINI_SIZE_COMPACT = "compact"
        const val CAPSULE_SHELL_OPACITY_MIN = 0.45f
        const val CAPSULE_SHELL_OPACITY_MAX = 0.90f
        const val CAPSULE_SHELL_OPACITY_DEFAULT = 0.82f
        /** 按包名记录上次停靠：capsule_dock_pkg_<packageName>（旧；圆球化后仅作默认落点映射） */
        private const val KEY_CAPSULE_DOCK_PKG_PREFIX = "capsule_dock_pkg_"
        /** 按包名记录自由悬浮：capsule_float_pkg_<packageName> → "offsetX,y" */
        private const val KEY_CAPSULE_FLOAT_PKG_PREFIX = "capsule_float_pkg_"
        private const val KEY_FOCUS_ORB_DISCOVER_HINT_SEEN = "focus_orb_discover_hint_seen"
        private fun gateHiddenPurposeKey(packageName: String) =
            "gate_hidden_purpose_$packageName"
        const val CAPSULE_DOCK_LEFT = "left"
        const val CAPSULE_DOCK_CENTER = "center"
        const val CAPSULE_DOCK_RIGHT = "right"
        const val CAPSULE_DOCK_LOWER_LEFT = "lower_left"
        const val CAPSULE_DOCK_LOWER_CENTER = "lower_center"
        const val CAPSULE_DOCK_LOWER_RIGHT = "lower_right"
        /** 产品默认：左下 */
        const val CAPSULE_DOCK_DEFAULT = CAPSULE_DOCK_LOWER_LEFT
        val CAPSULE_DOCK_POSITIONS = listOf(
            CAPSULE_DOCK_LEFT,
            CAPSULE_DOCK_CENTER,
            CAPSULE_DOCK_RIGHT,
            CAPSULE_DOCK_LOWER_LEFT,
            CAPSULE_DOCK_LOWER_CENTER,
            CAPSULE_DOCK_LOWER_RIGHT
        )
        // 意图门离开倒计时
        private const val KEY_AWAY_COUNTDOWN_SECONDS = "away_countdown_seconds"
        /** 离开倒计时可选秒数 */
        val AWAY_COUNTDOWN_OPTIONS = listOf(60, 120, 300)
        const val DEFAULT_AWAY_COUNTDOWN_SECONDS = 120
        // 想去的地方（正向 App）
        private const val KEY_POSITIVE_DESTINATIONS = "positive_destinations"
        private const val KEY_POSITIVE_DESTINATIONS_JSON = "positive_destinations_v2"
        private const val KEY_PREFERRED_POSITIVE_DESTINATION = "preferred_positive_destination"
        private const val KEY_EXPLICIT_GATE_LEAVE_COUNT = "explicit_gate_leave_count"
        private const val KEY_LAST_POSITIVE_SETUP_NUDGE_AT_LEAVE = "last_positive_setup_nudge_at_leave"
        /** 离开轻条最多同时露出的去处数量（配置数量不限） */
        const val MAX_POSITIVE_DISPLAY = 3
        // 有意义的事
        private const val KEY_MEANINGFUL_CUSTOM_JSON = "meaningful_things_custom"
        private const val KEY_MEANINGFUL_DISABLED_PRESETS = "meaningful_things_disabled_presets"
        /** 预置条目的 App 绑定：id\tpackage 每行 */
        private const val KEY_MEANINGFUL_PRESET_BINDINGS = "meaningful_things_preset_bindings"
        const val MAX_CUSTOM_MEANINGFUL_THINGS = 20
        // VIP 相关
        private const val KEY_VIP_LEVEL       = "vip_level"
        private const val KEY_VIP_EXPIRE_TIME = "vip_expire_time"
        private const val KEY_HAS_USED_TRIAL  = "has_used_trial"
        private const val KEY_PENDING_HA_ORDER_NO = "pending_ha_order_no"
        private const val KEY_PENDING_HA_PLAN_ID = "pending_ha_plan_id"
        private const val KEY_PENDING_HA_PLAN_TITLE = "pending_ha_plan_title"
        private const val KEY_PENDING_HA_AMOUNT_YUAN = "pending_ha_amount_yuan"
        private const val KEY_PENDING_HA_LIST_AMOUNT_YUAN = "pending_ha_list_amount_yuan"
        private const val KEY_PENDING_HA_EARLY_BIRD = "pending_ha_early_bird"
        private const val KEY_PENDING_HA_TIER_LABEL = "pending_ha_tier_label"
        private const val KEY_PENDING_HA_CONTACT = "pending_ha_contact"
        private const val KEY_PENDING_HA_REMARK = "pending_ha_remark"
        private const val KEY_PENDING_HA_BENEFIT = "pending_ha_benefit"
        private const val KEY_PENDING_HA_NOTIFIED = "pending_ha_notified"
        private const val KEY_PENDING_HA_CREATED_AT = "pending_ha_created_at"
        private const val KEY_PENDING_HA_PHONE = "pending_ha_phone"
        private const val KEY_BOUND_PHONE = "bound_restore_phone"
        // 邀请码（历史 key 名保留，避免已装用户丢状态）
        private const val KEY_BETA_UNLOCKED = "beta_invite_unlocked"
        private const val KEY_BETA_CODE = "beta_invite_code"
        private const val KEY_BETA_REDEEMED_AT = "beta_invite_redeemed_at"
        /** 自助领码缓存（可先于兑换存在） */
        private const val KEY_CLAIMED_INVITE_CODE = "claimed_invite_code"
        private const val KEY_ANALYTICS_PURPOSE_FULL = "analytics_purpose_full_text"
        private const val KEY_ANALYTICS_APP_ICON_HASHES = "analytics_app_icon_hashes"
        // 格言已赞 id（StringSet）
        private const val KEY_LIKED_QUOTE_IDS = "liked_quote_ids"
        /** 用户选择跳过的更新 versionCode（仅非强更） */
        private const val KEY_SKIPPED_UPDATE_VERSION_CODE = "skipped_update_version_code"
        /** 已在首页弹窗提示过的可选更新 versionCode */
        private const val KEY_LAST_PROMPTED_UPDATE_VERSION_CODE = "last_prompted_update_version_code"
        /** 已看过「本版更新说明」的 versionCode；缺省 -1 */
        private const val KEY_LAST_SEEN_WHATS_NEW_VERSION_CODE = "last_seen_whats_new_version_code"
        private const val KEY_CACHED_WHATS_NEW_VERSION_CODE = "cached_whats_new_version_code"
        private const val KEY_CACHED_WHATS_NEW_VERSION_NAME = "cached_whats_new_version_name"
        private const val KEY_CACHED_WHATS_NEW_CHANGELOG = "cached_whats_new_changelog"
        /** 周日周回望轻提示：已关闭的 weekStartMs */
        private const val KEY_WEEK_LOOKBACK_TIP_DISMISSED_WEEK_START =
            "week_lookback_tip_dismissed_week_start"
        /** 搜索门其他意图日次数：explore_other_intent_<pkg> = yyyyMMdd|count */
        private const val KEY_EXPLORE_OTHER_INTENT_PREFIX = "explore_other_intent_"
        /** 日限触顶延长：daily_grace_<packageName> = yyyyMMdd */
        private const val KEY_DAILY_GRACE_PREFIX = "daily_grace_"
        /** 日限触顶延长分钟（展示 +N）：daily_grace_min_<packageName> = Int */
        private const val KEY_DAILY_GRACE_MINUTES_PREFIX = "daily_grace_min_"
        /** 日限触顶延长天花板秒：daily_grace_ceil_<packageName> = Long */
        private const val KEY_DAILY_GRACE_CEILING_PREFIX = "daily_grace_ceil_"
        /** 时段硬门今日豁免：period_exemption_<packageName> = yyyyMMdd */
        private const val KEY_PERIOD_EXEMPTION_PREFIX = "period_exemption_"
        /** 时段硬门紧急进入：已勾选「不再提示」 */
        private const val KEY_PERIOD_EMERGENCY_ENTER_TIP_SKIPPED = "period_emergency_enter_tip_skipped"
        /** 意图门入场动效：intercept_entrance_anim_<packageName> = yyyyMMdd */
        private const val KEY_INTERCEPT_ENTRANCE_ANIM_PREFIX = "intercept_entrance_anim_"
        /** 日限额洗灰武装：daily_gray_wash_<packageName> = yyyyMMdd */
        private const val KEY_DAILY_GRAY_WASH_PREFIX = "daily_gray_wash_"
        /** 日限额洗灰渐变起点：daily_gray_wash_started_<packageName> = Long(ms) */
        private const val KEY_DAILY_GRAY_WASH_STARTED_MS_PREFIX = "daily_gray_wash_started_"
        /** 系统真灰度是否由本 App 持有（崩溃恢复） */
        private const val KEY_DISPLAY_GRAYSCALE_HELD = "display_grayscale_held"
        private const val KEY_DISPLAY_GRAYSCALE_PREV_ENABLED = "display_grayscale_prev_enabled"
        private const val KEY_DISPLAY_GRAYSCALE_PREV_MODE = "display_grayscale_prev_mode"
        private const val KEY_ALIGNED_COMPARE_STREAK = "aligned_compare_streak"
        /** 总门教学副句：首见日 gate_path_teach_first_<pkg> = yyyyMMdd */
        private const val KEY_GATE_PATH_TEACH_FIRST_DAY_PREFIX = "gate_path_teach_first_"
        /** 总门教学副句：计数日 gate_path_teach_day_<pkg> = yyyyMMdd */
        private const val KEY_GATE_PATH_TEACH_DAY_PREFIX = "gate_path_teach_day_"
        /** 总门教学副句：当日已见次数 gate_path_teach_count_<pkg> = Int */
        private const val KEY_GATE_PATH_TEACH_COUNT_PREFIX = "gate_path_teach_count_"
        /** 随意浏览冷却日：browse_cd_day_<pkg> = yyyyMMdd */
        private const val KEY_BROWSE_COOLDOWN_DAY_PREFIX = "browse_cd_day_"
        /** 随意浏览连进次数：browse_cd_streak_<pkg> = Int */
        private const val KEY_BROWSE_COOLDOWN_STREAK_PREFIX = "browse_cd_streak_"
        /** 随意浏览冷却截止：browse_cd_until_<pkg> = Long(ms) */
        private const val KEY_BROWSE_COOLDOWN_UNTIL_PREFIX = "browse_cd_until_"

        fun normalizeCapsuleMiniSize(size: String): String =
            if (size == CAPSULE_MINI_SIZE_COMPACT) CAPSULE_MINI_SIZE_COMPACT
            else CAPSULE_MINI_SIZE_STANDARD

        fun normalizeCapsuleShellOpacity(opacity: Float): Float =
            opacity.coerceIn(CAPSULE_SHELL_OPACITY_MIN, CAPSULE_SHELL_OPACITY_MAX)

        fun normalizeCapsuleDockPosition(position: String): String = when (position) {
            CAPSULE_DOCK_LEFT -> CAPSULE_DOCK_LEFT
            CAPSULE_DOCK_CENTER -> CAPSULE_DOCK_CENTER
            CAPSULE_DOCK_RIGHT -> CAPSULE_DOCK_RIGHT
            CAPSULE_DOCK_LOWER_LEFT -> CAPSULE_DOCK_LOWER_LEFT
            CAPSULE_DOCK_LOWER_CENTER -> CAPSULE_DOCK_LOWER_CENTER
            CAPSULE_DOCK_LOWER_RIGHT -> CAPSULE_DOCK_LOWER_RIGHT
            else -> CAPSULE_DOCK_DEFAULT
        }

        fun isCapsuleDockLowerRow(position: String): Boolean =
            normalizeCapsuleDockPosition(position).startsWith("lower_")

        /** 水平列：left / center / right（忽略上/下排） */
        fun capsuleDockColumn(position: String): String =
            when (normalizeCapsuleDockPosition(position)) {
                CAPSULE_DOCK_CENTER, CAPSULE_DOCK_LOWER_CENTER -> CAPSULE_DOCK_CENTER
                CAPSULE_DOCK_RIGHT, CAPSULE_DOCK_LOWER_RIGHT -> CAPSULE_DOCK_RIGHT
                else -> CAPSULE_DOCK_LEFT
            }

        fun normalizeAwayCountdownSeconds(seconds: Int): Int =
            AWAY_COUNTDOWN_OPTIONS.minByOrNull { kotlin.math.abs(it - seconds) }
                ?: DEFAULT_AWAY_COUNTDOWN_SECONDS

        /**
         * 全功能免费期开关（正式版默认关闭）。
         *
         * true  = 临时全功能开放，隐藏付费/邀请入口。
         * false = 正式形态：免费版最多 [FREE_MONITOR_LIMIT] 个 App，超出需 VIP（邀请码或购买）。
         */
        const val FREE_PERIOD_ENABLED = false

        /**
         * 「想去的地方」产品开关。
         * false = 屏蔽设置入口、离开轻条归属引导与相关深链；页面与数据层保留便于恢复。
         */
        const val POSITIVE_DESTINATIONS_ENABLED = false

        /** 免费版最多监控的 App 数量 */
        const val FREE_MONITOR_LIMIT = 3

        /** 心锚官网落地页（后续可换独立子站） */
        const val HEART_ANCHOR_WEB_URL = "https://happyvillage.cn/heart-anchor.html"
        const val HEART_ANCHOR_PRIVACY_URL = "https://happyvillage.cn/heart-anchor-privacy.html"
        const val HEART_ANCHOR_TERMS_URL = "https://happyvillage.cn/heart-anchor-terms.html"
        const val HEART_ANCHOR_SUPPORT_EMAIL = "wenxistudio.official@gmail.com"

        /** APP 备案编号；须在「关于」等显著位置展示，并链到工信部备案系统 */
        const val HEART_ANCHOR_ICP_NUMBER = "晋ICP备2026007203号-2A"
        const val MIIT_BEIAN_URL = "https://beian.miit.gov.cn/"

        /** 客服微信号（意向开通 / 人工发会员码） */
        const val CONTACT_WECHAT = "botianweichat"

        fun encodeAuthorCatalog(authors: List<CachedAuthor>): String {
            if (authors.isEmpty()) return ""
            return try {
                org.json.JSONArray().apply {
                    authors.forEach { a ->
                        put(
                            org.json.JSONObject()
                                .put("id", a.id)
                                .put("name", a.name)
                                .put("bio", a.bio)
                                .put("category", a.category)
                                .put("quote_count", a.quoteCount)
                                .put("subscribed", a.subscribed)
                        )
                    }
                }.toString()
            } catch (_: Exception) {
                ""
            }
        }

        fun decodeAuthorCatalog(json: String): List<CachedAuthor> {
            if (json.isBlank()) return emptyList()
            return try {
                val arr = org.json.JSONArray(json)
                buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optInt("id", 0)
                        val name = o.optString("name").trim()
                        if (id <= 0 || name.isEmpty()) continue
                        add(
                            CachedAuthor(
                                id = id,
                                name = name,
                                bio = o.optString("bio").trim(),
                                category = o.optString("category").trim(),
                                quoteCount = o.optInt("quote_count", 0),
                                subscribed = o.optBoolean("subscribed", false)
                            )
                        )
                    }
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
    }

    // ── 对照熟练度（连续「没跑偏」→ 横条等处可显示轻提示）────────────────────

    fun alignedCompareStreak(): Int =
        prefs.getInt(KEY_ALIGNED_COMPARE_STREAK, 0).coerceAtLeast(0)

    fun qualifiesFastCompareHint(): Boolean = alignedCompareStreak() >= 3

    fun recordCompareOutcome(level: Int?) {
        val streak = if (level == com.life.mindfulnessapp.data.db.entity.UsageRecordEntity.MindfulnessLevel.ALIGNED) {
            alignedCompareStreak() + 1
        } else {
            0
        }
        prefs.edit { putInt(KEY_ALIGNED_COMPARE_STREAK, streak.coerceAtMost(99)) }
    }

    // ── 随意浏览冷却（每 App：日 key · 连进 · 冷却截止）──────────────────────

    fun getBrowseCasualCooldown(packageName: String): com.life.mindfulnessapp.domain.model.BrowseCasualCooldown.Persisted {
        if (packageName.isBlank()) {
            return com.life.mindfulnessapp.domain.model.BrowseCasualCooldown.Persisted()
        }
        return com.life.mindfulnessapp.domain.model.BrowseCasualCooldown.Persisted(
            dayKey = prefs.getString(KEY_BROWSE_COOLDOWN_DAY_PREFIX + packageName, "").orEmpty(),
            streakCount = prefs.getInt(KEY_BROWSE_COOLDOWN_STREAK_PREFIX + packageName, 0)
                .coerceAtLeast(0),
            cooldownUntilMs = prefs.getLong(KEY_BROWSE_COOLDOWN_UNTIL_PREFIX + packageName, 0L)
                .coerceAtLeast(0L)
        )
    }

    fun setBrowseCasualCooldown(
        packageName: String,
        state: com.life.mindfulnessapp.domain.model.BrowseCasualCooldown.Persisted
    ) {
        if (packageName.isBlank()) return
        prefs.edit {
            putString(KEY_BROWSE_COOLDOWN_DAY_PREFIX + packageName, state.dayKey)
            putInt(
                KEY_BROWSE_COOLDOWN_STREAK_PREFIX + packageName,
                state.streakCount.coerceAtLeast(0)
            )
            putLong(
                KEY_BROWSE_COOLDOWN_UNTIL_PREFIX + packageName,
                state.cooldownUntilMs.coerceAtLeast(0L)
            )
        }
    }

    fun browseCasualCooldownTodayKey(): String = todayDateKey()
}
