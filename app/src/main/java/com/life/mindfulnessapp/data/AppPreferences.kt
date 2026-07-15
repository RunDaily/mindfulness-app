package com.life.mindfulnessapp.data

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 轻量级偏好存储，基于 SharedPreferences。
 * 目前管理：
 *   - 每日简报推送开关（daily_brief_enabled）
 *   - 每日简报推送时间（daily_brief_hour / daily_brief_minute）
 *   - 主题模式（dark_theme_enabled）：true = 夜间，false = 日间
 *
 * 注：每个被监控的 App 进入前默认要求填写使用目的，无需任何全局开关。
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("mindfulness_prefs", Context.MODE_PRIVATE)

    // ── 每日简报推送 ─────────────────────────────────────────────────────────

    private val _dailyBriefEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_DAILY_BRIEF_ENABLED, true)
    )

    /** 每日简报推送是否开启（在指定时间推送今日使用总结）*/
    val dailyBriefEnabled: StateFlow<Boolean> = _dailyBriefEnabled

    fun setDailyBriefEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_DAILY_BRIEF_ENABLED, enabled) }
        _dailyBriefEnabled.value = enabled
    }

    fun isDailyBriefEnabled(): Boolean = _dailyBriefEnabled.value

    // ── 每日简报推送时间（小时 + 分钟）──────────────────────────────────────

    private val _dailyBriefHour = MutableStateFlow(
        prefs.getInt(KEY_DAILY_BRIEF_HOUR, 21)  // 默认晚上 21:00
    )

    /** 每日简报推送小时（0~23，默认 21）*/
    val dailyBriefHour: StateFlow<Int> = _dailyBriefHour

    fun setDailyBriefHour(hour: Int) {
        prefs.edit { putInt(KEY_DAILY_BRIEF_HOUR, hour) }
        _dailyBriefHour.value = hour
    }

    fun getDailyBriefHour(): Int = _dailyBriefHour.value

    private val _dailyBriefMinute = MutableStateFlow(
        prefs.getInt(KEY_DAILY_BRIEF_MINUTE, 0)  // 默认 0 分
    )

    /** 每日简报推送分钟（0~59，默认 0）*/
    val dailyBriefMinute: StateFlow<Int> = _dailyBriefMinute

    fun setDailyBriefMinute(minute: Int) {
        prefs.edit { putInt(KEY_DAILY_BRIEF_MINUTE, minute) }
        _dailyBriefMinute.value = minute
    }

    fun getDailyBriefMinute(): Int = _dailyBriefMinute.value

    // ── 格言推送 ─────────────────────────────────────────────────────────────

    private val _quoteReminderEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_QUOTE_REMINDER_ENABLED, false)  // 默认关闭，需用户主动开启
    )

    /** 格言推送是否开启（从收藏中随机推送一条，需收藏 ≥ 3 条才可开启）*/
    val quoteReminderEnabled: StateFlow<Boolean> = _quoteReminderEnabled

    fun setQuoteReminderEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_QUOTE_REMINDER_ENABLED, enabled) }
        _quoteReminderEnabled.value = enabled
    }

    fun isQuoteReminderEnabled(): Boolean = _quoteReminderEnabled.value

    /**
     * 格言推送间隔（小时）。
     * 有效值：2 / 4 / 8（对应「勤快 / 适中 / 清淡」）。
     * 默认 4 小时。
     */
    private val _quoteReminderIntervalHours = MutableStateFlow(
        prefs.getInt(KEY_QUOTE_REMINDER_INTERVAL_HOURS, 4)
    )

    val quoteReminderIntervalHours: StateFlow<Int> = _quoteReminderIntervalHours

    fun setQuoteReminderIntervalHours(hours: Int) {
        prefs.edit { putInt(KEY_QUOTE_REMINDER_INTERVAL_HOURS, hours) }
        _quoteReminderIntervalHours.value = hours
    }

    fun getQuoteReminderIntervalHours(): Int = _quoteReminderIntervalHours.value

    /**
     * 格言推送活跃时段起始小时（0~23）。
     * 结束固定为 22:00，避免深夜打扰。
     * 默认 8（早上 8 点起推）。
     */
    private val _quoteReminderStartHour = MutableStateFlow(
        prefs.getInt(KEY_QUOTE_REMINDER_START_HOUR, 8)
    )

    val quoteReminderStartHour: StateFlow<Int> = _quoteReminderStartHour

    fun setQuoteReminderStartHour(hour: Int) {
        prefs.edit { putInt(KEY_QUOTE_REMINDER_START_HOUR, hour) }
        _quoteReminderStartHour.value = hour
    }

    fun getQuoteReminderStartHour(): Int = _quoteReminderStartHour.value

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

    // ── 拦截主题 ──────────────────────────────────────────────────────────────

    private val _interceptThemeId = MutableStateFlow(
        prefs.getString(KEY_INTERCEPT_THEME, "default") ?: "default"
    )

    /** 当前选中的拦截主题 ID */
    val interceptThemeId: StateFlow<String> = _interceptThemeId

    fun setInterceptThemeId(themeId: String) {
        prefs.edit { putString(KEY_INTERCEPT_THEME, themeId) }
        _interceptThemeId.value = themeId
    }

    fun getInterceptThemeId(): String = _interceptThemeId.value

    // ── 账号信息（云端同步用）──────────────────────────────────────────────

    /** 当前登录的 JWT Token（null 表示未登录）*/
    val savedToken: String? get() = prefs.getString(KEY_TOKEN, null)

    /** 当前登录的用户名（手机号）*/
    val savedUsername: String? get() = prefs.getString(KEY_USERNAME, null)

    /** 当前登录的昵称 */
    val savedNickname: String? get() = prefs.getString(KEY_NICKNAME, null)

    /** 当前登录的头像 emoji */
    val savedAvatarEmoji: String? get() = prefs.getString(KEY_AVATAR_EMOJI, "⚓")

    /** 是否已登录 */
    val isLoggedIn: Boolean get() = !savedToken.isNullOrBlank()

    private val _loginState = MutableStateFlow(isLoggedIn)
    /** 登录状态变化流（供 ViewModel 监听）*/
    val loginState: StateFlow<Boolean> = _loginState

    fun saveAccount(token: String, username: String, nickname: String, avatarEmoji: String) {
        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_USERNAME, username)
            .putString(KEY_NICKNAME, nickname)
            .putString(KEY_AVATAR_EMOJI, avatarEmoji)
            .apply()
        _loginState.value = true
    }

    fun clearAccount() {
        prefs.edit()
            .remove(KEY_TOKEN)
            .remove(KEY_USERNAME)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR_EMOJI)
            .apply()
        _loginState.value = false
        // 清除账号时同步清除VIP状态和邀请码缓存
        clearVipStatus()
        clearInviteCache()
    }

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

    /** 清除 VIP 状态（退出登录或订阅过期时调用） */
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

    // ── 邀请码 ──────────────────────────────────────────────────────────────

    /** 当前用户自己的邀请码（服务端生成，本地缓存） */
    var myInviteCode: String
        get() = prefs.getString(KEY_MY_INVITE_CODE, "") ?: ""
        set(value) { prefs.edit { putString(KEY_MY_INVITE_CODE, value) } }

    /** 已成功邀请的人数（本地缓存） */
    var invitedCount: Int
        get() = prefs.getInt(KEY_INVITED_COUNT, 0)
        set(value) { prefs.edit { putInt(KEY_INVITED_COUNT, value) } }

    /** 当前用户是否已使用过别人的邀请码（防止重复使用） */
    var hasRedeemedInvite: Boolean
        get() = prefs.getBoolean(KEY_HAS_REDEEMED_INVITE, false)
        set(value) { prefs.edit { putBoolean(KEY_HAS_REDEEMED_INVITE, value) } }

    /** 清除邀请码缓存（退出登录时调用） */
    fun clearInviteCache() {
        prefs.edit {
            remove(KEY_MY_INVITE_CODE)
            putInt(KEY_INVITED_COUNT, 0)
            putBoolean(KEY_HAS_REDEEMED_INVITE, false)
        }
    }

    companion object {
        private const val KEY_DAILY_BRIEF_ENABLED = "daily_brief_enabled"
        private const val KEY_DAILY_BRIEF_HOUR    = "daily_brief_hour"
        private const val KEY_DAILY_BRIEF_MINUTE  = "daily_brief_minute"
        // 格言推送
        private const val KEY_QUOTE_REMINDER_ENABLED        = "quote_reminder_enabled"
        private const val KEY_QUOTE_REMINDER_INTERVAL_HOURS = "quote_reminder_interval_hours"
        private const val KEY_QUOTE_REMINDER_START_HOUR     = "quote_reminder_start_hour"
        private const val KEY_DARK_THEME           = "dark_theme_enabled"
        private const val KEY_INTERCEPT_THEME      = "intercept_theme_id"
        // 加强保活
        private const val KEY_ENHANCED_KEEP_ALIVE = "enhanced_keep_alive"
        // 账号相关
        private const val KEY_TOKEN        = "ha_token"
        private const val KEY_USERNAME     = "ha_username"
        private const val KEY_NICKNAME     = "ha_nickname"
        private const val KEY_AVATAR_EMOJI = "ha_avatar_emoji"
        // VIP 相关
        private const val KEY_VIP_LEVEL       = "vip_level"
        private const val KEY_VIP_EXPIRE_TIME = "vip_expire_time"
        // 邀请码相关
        private const val KEY_MY_INVITE_CODE      = "my_invite_code"
        private const val KEY_INVITED_COUNT       = "invited_count"
        private const val KEY_HAS_REDEEMED_INVITE = "has_redeemed_invite"

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
