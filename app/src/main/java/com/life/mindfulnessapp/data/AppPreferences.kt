package com.life.mindfulnessapp.data

import android.content.Context
import androidx.core.content.edit
import com.life.mindfulnessapp.BuildConfig
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 轻量级偏好存储，基于 SharedPreferences。
 * 目前管理：
 *   - 每小时使用提醒开关（hourly_usage_reminder_enabled）
 *   - 主题模式（dark_theme_enabled）：true = 夜间，false = 日间
 *
 * 注：每个被监控的 App 进入前默认要求填写使用目的，无需任何全局开关。
 */
@Singleton
class AppPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("mindfulness_prefs", Context.MODE_PRIVATE)

    // ── 每小时使用提醒 ───────────────────────────────────────────────────────

    private val _hourlyReminderEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_HOURLY_REMINDER, true)
    )

    /** 每小时使用提醒是否开启（10:00～22:00 整点推送当日使用时长）*/
    val hourlyReminderEnabled: StateFlow<Boolean> = _hourlyReminderEnabled

    fun setHourlyReminderEnabled(enabled: Boolean) {
        prefs.edit { putBoolean(KEY_HOURLY_REMINDER, enabled) }
        _hourlyReminderEnabled.value = enabled
    }

    fun isHourlyReminderEnabled(): Boolean = _hourlyReminderEnabled.value

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

    // ── 探索彩蛋：是否已解锁【探索】Tab ────────────────────────────────────────

    private val _isExploreUnlocked = MutableStateFlow(
        prefs.getBoolean(KEY_EXPLORE_UNLOCKED, false)
    )

    /** 是否已解锁【探索】Tab（彩蛋触发后永久保存）*/
    val isExploreUnlocked: StateFlow<Boolean> = _isExploreUnlocked

    fun setExploreUnlocked(unlocked: Boolean) {
        prefs.edit { putBoolean(KEY_EXPLORE_UNLOCKED, unlocked) }
        _isExploreUnlocked.value = unlocked
    }

    fun isExploreUnlocked(): Boolean = _isExploreUnlocked.value

    // ── 开放所有拦截主题 ────────────────────────────────────────────────────────

    private val _allThemesUnlocked = MutableStateFlow(
        prefs.getBoolean(KEY_ALL_THEMES_UNLOCKED, false)
    )

    /** 是否开放全部拦截主题（设置页开关控制） */
    val allThemesUnlocked: StateFlow<Boolean> = _allThemesUnlocked

    fun setAllThemesUnlocked(unlocked: Boolean) {
        prefs.edit { putBoolean(KEY_ALL_THEMES_UNLOCKED, unlocked) }
        _allThemesUnlocked.value = unlocked
    }

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

    // ── 已解锁的主题列表 ────────────────────────────────────────────────────
    // Debug 模式下全部解锁，方便测试各主题动效
    private val _unlockedThemeIds = MutableStateFlow(
        if (BuildConfig.DEBUG) {
            setOf("default", "deep_sea", "cyberpunk", "lava", "sakura", "moon", "glitch", "rpg")
        } else {
            prefs.getStringSet(KEY_UNLOCKED_THEMES, setOf("default", "deep_sea", "cyberpunk"))?.toSet()
                ?: setOf("default", "deep_sea", "cyberpunk")
        }
    )

    val unlockedThemeIds: StateFlow<Set<String>> = _unlockedThemeIds

    fun unlockTheme(themeId: String) {
        val current = _unlockedThemeIds.value.toMutableSet()
        current.add(themeId)
        prefs.edit { putStringSet(KEY_UNLOCKED_THEMES, current) }
        _unlockedThemeIds.value = current
    }

    fun isThemeUnlocked(themeId: String): Boolean = _unlockedThemeIds.value.contains(themeId)

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
    }

    companion object {
        private const val KEY_HOURLY_REMINDER  = "hourly_usage_reminder_enabled"
        private const val KEY_DARK_THEME       = "dark_theme_enabled"
        private const val KEY_EXPLORE_UNLOCKED = "explore_tab_unlocked"
        private const val KEY_INTERCEPT_THEME     = "intercept_theme_id"
        private const val KEY_UNLOCKED_THEMES      = "unlocked_theme_ids"
        private const val KEY_ALL_THEMES_UNLOCKED  = "all_themes_unlocked"
        // 账号相关
        private const val KEY_TOKEN        = "ha_token"
        private const val KEY_USERNAME     = "ha_username"
        private const val KEY_NICKNAME     = "ha_nickname"
        private const val KEY_AVATAR_EMOJI = "ha_avatar_emoji"
    }
}
