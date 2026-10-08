package com.life.mindfulnessapp.data.analytics

/**
 * 心锚埋点目录（设备级，不绑定账号）。
 *
 * 设计目标：
 * - 事件流：串成完整使用故事与漏斗
 * - 配置快照（[com.life.mindfulnessapp.data.repository.MonitorProfileReporter]）：
 *   管理台「绑了哪些 App / 各开哪些能力」的当前态真相源
 *
 * - [id]：上报用稳定英文 ID（管理台映射中文）
 * - 内测期上报 app / pkg / 意图摘要；正式版可通过偏好关闭意图全文
 * - 属性以枚举、布尔、短字符串、分桶为主；对照备注全文不上报
 */
object HaEvents {
    // ── 准入与引导 ──────────────────────────────────────────────────────────
    const val APP_OPEN = "app_open"
    /** 会员码首次解锁设备（与 [VIP_CODE_REDEEM] 并存，管理台漏斗用） */
    const val BETA_UNLOCK = "beta_unlock"
    const val PRIVACY_ACCEPT = "privacy_accept"
    const val ONBOARDING_STEP = "onboarding_step"
    const val ONBOARDING_COMPLETE = "onboarding_complete"
    const val PERMISSION_GRANT = "permission_grant"
    const val PERMISSION_SKIP = "permission_skip"
    const val PERMISSION_PROMPT = "permission_prompt"
    const val FIRST_BIND_SKIP = "first_bind_skip"

    // ── 权限与绑定 ──────────────────────────────────────────────────────────
    const val CAPABILITY_BIND = "capability_bind"
    const val CAPABILITY_EDIT = "capability_edit"
    const val CAPABILITY_UNBIND = "capability_unbind"
    const val MONITOR_TOGGLE = "monitor_toggle"
    const val MONITOR_REORDER = "monitor_reorder"

    // ── 拦截与选择 ──────────────────────────────────────────────────────────
    const val INTERCEPT_SHOW = "intercept_show"
    const val GATE_ENTER = "gate_enter"
    const val GATE_HOLD = "gate_hold"
    const val GATE_RESUME = "gate_resume"
    const val GATE_BLOCKED_KEYWORD = "gate_blocked_keyword"
    const val LIMIT_BLOCK = "limit_block"
    const val LIMIT_DECISION = "limit_decision"

    // ── 会话生命周期 ────────────────────────────────────────────────────────
    const val SESSION_START = "session_start"
    const val SESSION_EXTEND = "session_extend"
    const val SESSION_END = "session_end"
    const val COMPARE_SAVE = "compare_save"
    const val AWAY_BAR_ACTION = "away_bar_action"
    /** 使用中轻问岛出现 */
    const val MID_CHECK_SHOW = "mid_check_show"
    /** 轻问回应：still / drift / dismiss */
    const val MID_CHECK_ACTION = "mid_check_action"
    /** SoftExit：end / stay */
    const val SOFT_EXIT_ACTION = "soft_exit_action"

    // ── 运营与反馈 ──────────────────────────────────────────────────────────
    const val UPDATE_CHECK = "update_check"
    const val FEEDBACK_SUBMIT = "feedback_submit"
    const val EXPORT_RECORDS = "export_records"
    const val KEEP_ALIVE_OPEN = "keep_alive_open"
    const val KEEP_ALIVE_TOGGLE = "keep_alive_toggle"
    const val LOOKBACK_VIEW = "lookback_view"
    const val DAY_REPORT_VIEW = "day_report_view"
    const val DAY_TIMELINE_VIEW = "day_timeline_view"
    /** 单 App · 近 7 日 × 24 小时节奏图 */
    const val APP_WEEK_RHYTHM_VIEW = "app_week_rhythm_view"
    const val THEME_CHANGE = "theme_change"
    const val BREATH_GATE_PASS = "breath_gate_pass"

    // ── 桌面心锚微粒 ────────────────────────────────────────────────────────
    const val DESKTOP_ANCHOR_OPEN = "desktop_anchor_open"
    const val DESKTOP_ANCHOR_NOTE = "desktop_anchor_note"
    const val DESKTOP_ANCHOR_OPEN_APP = "desktop_anchor_open_app"
    const val DESKTOP_ANCHOR_TOGGLE = "desktop_anchor_toggle"
    /** 桌面快板工具：note / todo / open_history */
    const val DESKTOP_PANEL_TOOL = "desktop_panel_tool"

    // ── 意图池 ──────────────────────────────────────────────────────────────
    const val INTENT_POOL_TAB_VIEW = "intent_pool_tab_view"
    const val INTENT_ENTRY_DETAIL_VIEW = "intent_entry_detail_view"
    const val INTENT_CATEGORY_CREATE = "intent_category_create"
    const val INTENT_CATEGORY_ASSIGN = "intent_category_assign"
    const val INTENT_CATEGORY_DELETE = "intent_category_delete"
    const val INTENT_MERGE = "intent_merge"
    const val INTENT_HIDE = "intent_hide"
    const val INTENT_UNHIDE = "intent_unhide"

    // ── 会员漏斗 ────────────────────────────────────────────────────────────
    const val VIP_PAGE_VIEW = "vip_page_view"
    const val VIP_PLAN_SELECT = "vip_plan_select"
    const val VIP_PAY_CLICK = "vip_pay_click"
    const val VIP_INTENT_SHOW = "vip_intent_show"
    const val VIP_INTENT_SUBMIT = "vip_intent_submit"
    const val VIP_PAY_ABANDON = "vip_pay_abandon"
    const val VIP_CODE_REDEEM = "vip_code_redeem"
    /** 自助留资领取会员码（尚未兑换） */
    const val VIP_CODE_CLAIM = "vip_code_claim"
    const val VIP_PAID_SUCCESS = "vip_paid_success"

    object Prop {
        const val ALREADY = "already"
        const val HAS_UPDATE = "has_update"
        const val CATEGORY = "category"
        const val PERMISSION = "permission"
        const val SOURCE = "source"
        const val STEP = "step"
        const val INTENT = "intent"
        const val TIME = "time"
        const val PERIOD = "period"
        const val SESSION = "session"
        const val KEYWORDS = "keywords"
        const val IS_NEW = "is_new"
        const val TYPE = "type"
        const val HAS_SESSION_LIMIT = "has_session_limit"
        const val PLAN = "plan"
        const val CHANNEL = "channel"
        const val REF = "ref"
        const val OVERLAY = "overlay"
        const val USAGE = "usage"
        const val BATTERY = "battery"
        const val NOTIFICATION = "notification"
        const val SKIPPED = "skipped"
        const val HAS_NOTE = "has_note"
        const val HAS_LEVEL = "has_level"
        const val RANGE = "range"
        const val ACTION = "action"
        const val FORMAT = "format"
        const val APP = "app"
        const val PKG = "pkg"
        const val INTERCEPT_ID = "intercept_id"
        const val SESSION_ID = "session_id"
        const val PURPOSE = "purpose"
        const val PURPOSE_LEN = "purpose_len"
        const val PURPOSE_CLARITY = "purpose_clarity"
        const val INTENT_KIND = "intent_kind"
        const val SESSION_MIN = "session_min"
        const val DAILY_MIN_BUCKET = "daily_min_bucket"
        const val DAILY_LIMIT_MIN = "daily_limit_min"
        const val DEFAULT_SESSION_MIN = "default_session_min"
        const val PERIOD_WINDOW_COUNT = "period_window_count"
        const val PERIOD_LOCK_HOURS = "period_lock_hours"
        const val KEYWORD_COUNT = "keyword_count"
        const val BIND_SLOT = "bind_slot"
        const val MONITORED_LEFT = "monitored_left"
        const val MONITORED_COUNT = "monitored_count"
        const val ENABLED = "enabled"
        const val ENTRY = "entry"
        const val END_REASON = "end_reason"
        const val DUR_BUCKET = "dur_bucket"
        const val DURATION_SEC = "duration_sec"
        const val EXTENDED = "extended"
        const val EXTEND_MIN = "extend_min"
        const val REMAIN_SEC = "remain_sec"
        const val HAD_PURPOSE = "had_purpose"
        const val HAD_DRAFT_PURPOSE = "had_draft_purpose"
        const val LEVEL = "level"
        const val PRIOR_END_REASON = "prior_end_reason"
        const val HOUR_BUCKET = "hour_bucket"
        const val WEEKDAY = "weekday"
        const val CAP_INTENT = "cap_intent"
        const val CAP_TIME = "cap_time"
        const val CAP_PERIOD = "cap_period"
        const val CAP_SESSION = "cap_session"
        const val THEME = "theme"
        const val WEEK_OFFSET = "week_offset"
        const val REASON = "reason"
        /** 使用中轻问序号（0-based） */
        const val CHECK_INDEX = "check_index"
        /** 觉察轨：task / urge */
        const val AWARENESS_MODE = "awareness_mode"
    }

    object VipChannel {
        const val WECHAT_CONTACT = "wechat_contact"
        const val WEB_APPLY = "web_apply"
        const val HAS_CODE = "has_code"
        const val WECHAT_PAY = "wechat_pay"
        const val PLAY = "play"
    }

    object Permission {
        const val OVERLAY = "overlay"
        const val USAGE = "usage"
        const val BATTERY = "battery"
        const val NOTIFICATION = "notification"
        const val ACCESSIBILITY = "accessibility"
    }

    object Source {
        const val ONBOARDING = "onboarding"
        const val HOME = "home"
        const val SETTINGS = "settings"
        const val PROFILE = "profile"
        const val BIND = "bind"
        const val EDIT = "edit"
        const val REORDER = "reorder"
        const val ENABLE_MONITOR = "enable_monitor"
        const val CAPSULE = "capsule"
        const val SESSION_LIMIT = "session_limit"
        const val AWAY_BAR = "away_bar"
    }

    /** config-snapshot 的 reason 字段 */
    object SnapshotReason {
        const val COLD_START = "cold_start"
        const val BIND = "bind"
        const val EDIT = "edit"
        const val UNBIND = "unbind"
        const val REORDER = "reorder"
        const val MONITOR_TOGGLE = "monitor_toggle"
        const val HOME_LIMIT = "home_limit"
        const val SYNC = "sync"
    }

    /** breath_gate_pass 的 reason */
    object BreathReason {
        const val PERIOD_DISABLE = "period_disable"
        const val PERIOD_WINDOW = "period_window"
        const val SCHEDULE_WEAKEN = "schedule_weaken"
        const val STOP_MONITOR_LOCKED = "stop_monitor_locked"
        const val DAILY_LOOSEN = "daily_loosen"
    }

    object OnboardingStep {
        const val WELCOME = "welcome"
        const val USAGE_ACCESS = "usage_access"
        const val MIRROR = "mirror"
        const val GUIDE = "guide"
        const val FINISH_PERMISSIONS = "finish_permissions"
        /** @deprecated 旧版步骤，保留兼容埋点 */
        const val FEATURES = "features"
        /** @deprecated 旧版步骤，保留兼容埋点 */
        const val PERMISSION = "permission"
    }

    object InterceptType {
        const val INTENT = "intent"
        const val BREATH = "breath"
        const val PERIOD = "period"
        const val DAILY_LIMIT = "daily_limit"
        const val OPEN_LIMIT = "open_limit"
        const val SESSION_LIMIT = "session_limit"
    }

    object ExportAction {
        const val SHARE = "share"
        const val SAVE = "save"
        const val BACKUP = "backup"
        const val RESTORE = "restore"
    }

    object ExportFormat {
        const val CSV = "csv"
        const val BACKUP = "backup"
        const val LOOKBACK_CARD = "lookback_card"
    }

    object Clarity {
        const val EMPTY = "empty"
        const val VAGUE = "vague"
        const val CONCRETE = "concrete"
    }

    object Entry {
        const val GATE = "gate"
        const val DIRECT = "direct"
        const val GRACE = "grace"
        const val RESUME = "resume"
    }

    object LimitAction {
        const val LEAVE = "leave"
        /** 时段硬门：今日豁免进入一次（不拆锁） */
        const val EXEMPT = "exempt"
        /** 日限触顶：写意图 + 选档延长进入 */
        const val GRACE = "grace"
        /** @deprecated 旧固定 10 分延长；新埋点用 [GRACE] */
        const val GRACE_10M = "grace_10m"
        const val COMPARE_AND_EXIT = "compare_and_exit"
        /** 意图时长到点：再用一次续时额度 */
        const val EXTEND = "extend"
        /** 意图时长到点：稍后对照，延后系统通知 */
        const val REVIEW_LATER = "review_later"
    }

    object AwayAction {
        const val COMPARE = "compare"
        const val KEEP_RESUME = "keep_resume"
        const val DISMISS = "dismiss"
    }
}
