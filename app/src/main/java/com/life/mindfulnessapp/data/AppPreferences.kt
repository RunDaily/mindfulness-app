package com.life.mindfulnessapp.data

import android.content.Context
import androidx.core.content.edit
import com.life.mindfulnessapp.domain.model.PositiveDestination
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 轻量级偏好存储，基于 SharedPreferences。
 * 目前管理：
 *   - 主题模式（dark_theme_enabled）：true = 夜间，false = 日间
 *   - 胶囊已用时长显示到秒（capsule_used_show_seconds）
 *   - 迷你胶囊尺寸档（capsule_mini_size）：standard / compact
 *   - 胶囊停靠位置（capsule_dock_position）：left / center / right
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

    // ── 主题模式：夜间 / 日间 ────────────────────────────────────────────────

    private val _isDarkTheme = MutableStateFlow(
        prefs.getBoolean(KEY_DARK_THEME, true)   // 默认夜间主题
    )

    /** 当前是否为夜间主题（true = 夜间，false = 日间）*/
    val isDarkTheme: StateFlow<Boolean> = _isDarkTheme

    fun setDarkTheme(dark: Boolean) {
        prefs.edit { putBoolean(KEY_DARK_THEME, dark) }
        _isDarkTheme.value = dark
    }

    fun isDarkThemeEnabled(): Boolean = _isDarkTheme.value

    // ── 加强保活（守护前台服务）────────────────────────────────────────────────

    private val _enhancedKeepAlive = MutableStateFlow(
        prefs.getBoolean(KEY_ENHANCED_KEEP_ALIVE, false)
    )

    /** 是否开启加强保活（启动独立守护前台服务）*/
    val enhancedKeepAlive: StateFlow<Boolean> = _enhancedKeepAlive

    fun setEnhancedKeepAlive(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_ENHANCED_KEEP_ALIVE, enabled) }
        _enhancedKeepAlive.value = enabled
    }

    fun isEnhancedKeepAliveEnabled(): Boolean = _enhancedKeepAlive.value

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

    // ── 胶囊水平停靠（左 / 中 / 右）──────────────────────────────────────────

    private val _capsuleDockPosition = MutableStateFlow(
        normalizeCapsuleDockPosition(
            prefs.getString(KEY_CAPSULE_DOCK_POSITION, CAPSULE_DOCK_LEFT)
                ?: CAPSULE_DOCK_LEFT
        )
    )

    /**
     * 收起态顶部停靠点：
     * - [CAPSULE_DOCK_LEFT] / [CAPSULE_DOCK_CENTER] / [CAPSULE_DOCK_RIGHT]
     * 拖拽松手后会写回；设置页可改默认；再次进入按此恢复。
     */
    val capsuleDockPosition: StateFlow<String> = _capsuleDockPosition

    fun setCapsuleDockPosition(position: String) {
        val normalized = normalizeCapsuleDockPosition(position)
        prefs.edit { putString(KEY_CAPSULE_DOCK_POSITION, normalized) }
        _capsuleDockPosition.value = normalized
    }

    fun getCapsuleDockPosition(): String = _capsuleDockPosition.value

    // ── 意图门离开倒计时（秒）────────────────────────────────────────────────

    private val _awayCountdownSeconds = MutableStateFlow(
        normalizeAwayCountdownSeconds(
            prefs.getInt(KEY_AWAY_COUNTDOWN_SECONDS, DEFAULT_AWAY_COUNTDOWN_SECONDS)
        )
    )

    /**
     * 含意图门时，切到桌面后暂停胶囊上的离开倒计时秒数。
     * 有效值：60 / 120 / 300；默认 120。纯时长锁静默收口等待与此同量级。
     */
    val awayCountdownSeconds: StateFlow<Int> = _awayCountdownSeconds

    fun setAwayCountdownSeconds(seconds: Int) {
        val normalized = normalizeAwayCountdownSeconds(seconds)
        prefs.edit { putInt(KEY_AWAY_COUNTDOWN_SECONDS, normalized) }
        _awayCountdownSeconds.value = normalized
    }

    fun getAwayCountdownSeconds(): Int = _awayCountdownSeconds.value

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
     * 免费公测期（FREE_PERIOD_ENABLED = true）时始终返回 true，全功能开放。
     */
    fun isVipActive(): Boolean = FREE_PERIOD_ENABLED || computeIsVip()

    /**
     * 当前是否高级版（等级 2）。
     * 免费公测期时同样视为高级版，所有 Premium 功能全部开放。
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

    companion object {
        private const val KEY_DARK_THEME           = "dark_theme_enabled"
        private const val KEY_INTERCEPT_THEME      = "intercept_theme_id"
        // 加强保活
        private const val KEY_ENHANCED_KEEP_ALIVE = "enhanced_keep_alive"
        // 胶囊已用显示秒
        private const val KEY_CAPSULE_USED_SHOW_SECONDS = "capsule_used_show_seconds"
        // 迷你胶囊尺寸
        private const val KEY_CAPSULE_MINI_SIZE = "capsule_mini_size"
        const val CAPSULE_MINI_SIZE_STANDARD = "standard"
        const val CAPSULE_MINI_SIZE_COMPACT = "compact"
        // 胶囊水平停靠
        private const val KEY_CAPSULE_DOCK_POSITION = "capsule_dock_position"
        const val CAPSULE_DOCK_LEFT = "left"
        const val CAPSULE_DOCK_CENTER = "center"
        const val CAPSULE_DOCK_RIGHT = "right"
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
        // VIP 相关
        private const val KEY_VIP_LEVEL       = "vip_level"
        private const val KEY_VIP_EXPIRE_TIME = "vip_expire_time"
        private const val KEY_HAS_USED_TRIAL  = "has_used_trial"

        fun normalizeCapsuleMiniSize(size: String): String =
            if (size == CAPSULE_MINI_SIZE_COMPACT) CAPSULE_MINI_SIZE_COMPACT
            else CAPSULE_MINI_SIZE_STANDARD

        fun normalizeCapsuleDockPosition(position: String): String = when (position) {
            CAPSULE_DOCK_CENTER -> CAPSULE_DOCK_CENTER
            CAPSULE_DOCK_RIGHT -> CAPSULE_DOCK_RIGHT
            else -> CAPSULE_DOCK_LEFT
        }

        fun normalizeAwayCountdownSeconds(seconds: Int): Int =
            AWAY_COUNTDOWN_OPTIONS.minByOrNull { kotlin.math.abs(it - seconds) }
                ?: DEFAULT_AWAY_COUNTDOWN_SECONDS

        /**
         * 免费公测期开关。
         *
         * true  = 上线初期免费阶段：全功能开放，隐藏所有付费入口，
         *         isVipActive() / isPremium() 始终返回 true。
         * false = 正式收费阶段：恢复 VIP 门禁，展示购买入口。
         *
         * 后续开启收费只需将此处改为 false 并发版更新即可。
         */
        const val FREE_PERIOD_ENABLED = true

        /** 免费版最多监控的 App 数量（FREE_PERIOD_ENABLED=true 时此限制不生效） */
        const val FREE_MONITOR_LIMIT = 3
    }
}
