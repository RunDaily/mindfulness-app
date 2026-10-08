package com.life.mindfulnessapp.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import com.life.mindfulnessapp.ui.theme.CapabilityKind

sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Home : Screen("home")
    /** 「能力」Tab：顶栏【入门 | 管理 | 探索】 */
    object Features : Screen("features")
    /** 全量榜 · 与今日热同源的近 7 日系统用量（可按打开排） */
    object ExploreUsageRank : Screen("explore_usage_rank")
    /** 探索 · 时间之尺：多坑位 App 日/周占用面积 */
    object ExploreTimeRuler : Screen("explore_time_ruler")
    /** 探索 · 步行觉察：边走边看时的路况锚点 */
    object WalkAwareness : Screen("walk_awareness")
    /** 守计划：按时段安排事务并限制干扰 App */
    object PlanBlocks : Screen("plan_blocks")
    /** 守计划 · 新建 / 编辑 */
    object PlanBlockEdit : Screen("plan_block_edit/{planId}") {
        fun createRoute(planId: String? = null): String =
            "plan_block_edit/${planId?.takeIf { it.isNotBlank() } ?: "new"}"
    }
    /** 探索 · 单 App 近 7 日系统用量（按日 / 按次） */
    object ExploreAppDetail : Screen("explore_app/{packageName}") {
        fun createRoute(packageName: String) = "explore_app/$packageName"
    }
    /** 单 App · 近 7 日 × 24 小时使用节奏（不限是否监控） */
    object AppWeekRhythm : Screen("app_week_rhythm/{packageName}") {
        fun createRoute(packageName: String) = "app_week_rhythm/$packageName"
    }
    /** 监控 App · 走势细看（加入前→今日 · 支持横屏） */
    object AppTrend : Screen("app_trend/{packageName}") {
        fun createRoute(packageName: String) = "app_trend/$packageName"
    }
    /** 能力入门详情：设计理念 + 功能说明（独立 Activity） */
    object CapabilityGuideDetail : Screen("capability_guide/{capability}") {
        fun createRoute(capability: CapabilityKind) =
            "capability_guide/${capability.name}"
    }
    /** 能力下的应用列表（从能力卡片进入，独立 Activity） */
    object CapabilityApps : Screen("capability_apps/{capability}") {
        fun createRoute(capability: CapabilityKind) =
            "capability_apps/${capability.name}"
    }
    /** 坑位管理：按能力维度分段网格 */
    object MonitorManage : Screen("monitor_manage")
    /** 坑位排序（二级）：拖动手柄调整首页坑位顺序 */
    object MonitorReorder : Screen("monitor_reorder")
    /** 添加监控应用（全机挑选器，旁路） */
    object AppList : Screen("app_list")
    /**
     * 能力 × App 绑定：引导后首绑选能力；选完进批量网格。
     * 带 seed / 能力区添加请用 [CapabilityBatchPick]。
     */
    object CapabilityBind : Screen("capability_bind") {
        fun createRoute(seed: CapabilityKind? = null): String =
            if (seed == null) route else "$route?seed=${seed.name}"
    }
    /**
     * 能力批量系锚：4 列网格多选，默认配置一键写入。
     */
    object CapabilityBatchPick : Screen("capability_batch_pick/{capability}") {
        fun createRoute(capability: CapabilityKind) =
            "capability_batch_pick/${capability.name}"
    }
    /**
     * 新增监控 · 单能力配置全屏页。
     * [seed] = 主能力名（[CapabilityKind.name]）；`all` / 未知时回退为意图门。
     */
    object AppLimitAdd : Screen("app_limit_add/{packageName}/{seed}") {
        const val SeedAll = "all"
        fun createRoute(packageName: String, seed: String = SeedAll) =
            "app_limit_add/$packageName/$seed"
    }
    /** 已监控 App 详情：三能力仪表盘 */
    object AppLimitEdit : Screen("app_limit_edit/{packageName}") {
        fun createRoute(packageName: String) = "app_limit_edit/$packageName"
    }
    /** 单能力配置（从详情箭头进入） */
    object AppCapabilityEdit : Screen("app_limit_capability/{packageName}/{capability}") {
        fun createRoute(packageName: String, capability: String) =
            "app_limit_capability/$packageName/$capability"
    }
    /** 单 App 使用记录（按日倒序）；[recordId] >0 高亮该条，0 高亮今日最新，-1 不高亮 */
    object AppHistory : Screen("app_history/{packageName}?recordId={recordId}") {
        fun createRoute(packageName: String, recordId: Long = -1L) =
            "app_history/$packageName?recordId=$recordId"
    }
    /** 意图池 · 单条详情 */
    object IntentPoolDetail : Screen("intent_pool/{packageName}/{entryId}") {
        fun createRoute(packageName: String, entryId: String) =
            "intent_pool/$packageName/$entryId"
    }
    /** 意图池 · 整理（分类 / 合并） */
    object IntentPoolManage : Screen("intent_pool_manage/{packageName}") {
        fun createRoute(packageName: String) = "intent_pool_manage/$packageName"
    }
    /** 写下意图 · 快捷标签个性化 */
    object QuickIntentTags : Screen("quick_intent_tags/{packageName}?appName={appName}") {
        fun createRoute(packageName: String, appName: String = "") =
            "quick_intent_tags/$packageName?appName=${android.net.Uri.encode(appName)}"
    }
    /** 方案 · 本 App 固定页直达一览 */
    object DeepLinkGlance : Screen("deep_link_glance/{packageName}?appName={appName}") {
        fun createRoute(packageName: String, appName: String = "") =
            "deep_link_glance/$packageName?appName=${android.net.Uri.encode(appName)}"
    }
    /** 「发现」Tab：低频工具目录 */
    object Discover : Screen("discover")
    /**
     * 发现 · 总览（近 7 日 · 次数 / 用量 两镜头）。
     * [lens]：gate | usage；[cut] 随镜头：event/app/day 或 total/app/day；
     * [dateMs] > 0 时按日刀选中该日。
     */
    object Overview : Screen("overview?lens={lens}&cut={cut}&dateMs={dateMs}") {
        fun createRoute(
            lens: String = "gate",
            cut: String = "",
            dateMs: Long = 0L
        ): String =
            "overview?lens=${android.net.Uri.encode(lens)}" +
                "&cut=${android.net.Uri.encode(cut)}&dateMs=$dateMs"

        /** 次数镜头（原「这一周」） */
        fun createGateRoute(cut: String = "", dateMs: Long = 0L): String =
            createRoute(lens = "gate", cut = cut, dateMs = dateMs)

        /** 用量镜头 */
        fun createUsageRoute(cut: String = "", dateMs: Long = 0L): String =
            createRoute(lens = "usage", cut = cut, dateMs = dateMs)
    }
    /** 发现 · 日程锁（原 Tab，现为子页） */
    object Schedule : Screen("schedule")
    /** 发现 · 觉察练习（中途轻问 + 离开对照 · 默认关） */
    object AwarenessPractice : Screen("awareness_practice")
    /** 「我」Tab */
    object Profile : Screen("profile")
    /**
     * 「今天」收据（门口决策脊线）。
     * [packageName] 空 = 全日；非空 = 单 App narrow。
     * [dateMs] > 0 时打开对应自然日（否则今天）。
     */
    object DayReport : Screen("day_report?packageName={packageName}&dateMs={dateMs}") {
        fun createRoute(packageName: String? = null, dateMs: Long = 0L): String {
            val pkg = packageName?.trim().orEmpty()
            return "day_report?packageName=${android.net.Uri.encode(pkg)}&dateMs=$dateMs"
        }
    }
    /** 周回望 */
    object WeekLookback : Screen("week_lookback")
    /** 使用日志：一日注意力转换流水（原首页记录列表） */
    object UsageLog : Screen("usage_log")
    /** 全部记录：月历选天 + 当日流水 */
    object RecordHistory : Screen("record_history")
    /** 今日 · 单 App 只读效果 */
    object TodayAppDetail : Screen("today_app/{packageName}") {
        fun createRoute(packageName: String) = "today_app/$packageName"
    }
    /** 二级设置页（从「我」进入） */
    object Settings : Screen("settings")
    /** 试玩：beta 玩法开关列表（从「我」进入） */
    object Playground : Screen("playground")
    /** 试玩 · 桌面静壁纸工作室 */
    object WallpaperStudio : Screen("wallpaper_studio")
    /** 试玩 · 格言（门页快关 · 收藏 · 订阅） */
    object QuotePlay : Screen("quote_play")
    /** 试玩 · 格言 · 从池里选一句收藏 */
    object QuoteBrowse : Screen("quote_browse")
    /** Debug：第三方搜索深链试跳（仅 DEBUG 入口） */
    object SearchDeepLinkTest : Screen("debug_search_deeplink")
    object Theme : Screen("theme")
    /** 名人目录（订阅） */
    object QuoteLibrary : Screen("quote_library")
    /** 格言推送时段（开始 / 截止 / 间隔） */
    object QuotePushSchedule : Screen("quote_push_schedule")
    /** 通知点入的格言时刻页 */
    object QuoteMoment : Screen(
        "quote_moment?id={id}&content={content}&author={author}&authorId={authorId}"
    ) {
        fun create(
            id: Int = 0,
            content: String = "",
            author: String = "",
            authorId: Int = 0
        ): String {
            val encContent = android.net.Uri.encode(content)
            val encAuthor = android.net.Uri.encode(author)
            return "quote_moment?id=$id&content=$encContent&author=$encAuthor&authorId=$authorId"
        }
    }
    /** 有意义的事：预置 + 自定义列表（拦截页与 App 共用） */
    object MeaningfulThings : Screen("meaningful_things")
    /** 想去的地方：离开后的正向 App 归属（可由 [AppPreferences.POSITIVE_DESTINATIONS_ENABLED] 屏蔽） */
    object PositiveDestinations : Screen("positive_destinations")
    /** 后台保活指南（厂商白名单 + 可检测清单） */
    object KeepAliveGuide : Screen("keep_alive_guide")
    /** vivo OriginOS 完整六步保活指南 */
    object VivoOriginOsKeepAliveGuide : Screen("vivo_originos_keep_alive_guide")
    /** 关于：版本、法务与官网 */
    object About : Screen("about")
    /** 产品说明书：原生壳 + 本地 H5 */
    object ProductManual : Screen("product_manual")
    /** 检查更新（从「我」进入） */
    object AppUpdate : Screen("app_update")
    /** 意见反馈：独立全屏页 */
    object Feedback : Screen("feedback")
    object Vip : Screen("vip")
}

/** 底部导航：今日 + 方案 + 发现 + 我。选中用实心图标，无胶囊底。 */
sealed class BottomTab(
    val screen: Screen,
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector
) {
    object Home : BottomTab(
        Screen.Home, "今日", Icons.Outlined.Home, Icons.Filled.Home
    )
    object Features : BottomTab(
        Screen.Features, "方案", Icons.Outlined.GridView, Icons.Filled.GridView
    )
    object Discover : BottomTab(
        Screen.Discover, "发现", Icons.Outlined.Explore, Icons.Filled.Explore
    )
    object Profile : BottomTab(
        Screen.Profile, "我", Icons.Outlined.Person, Icons.Filled.Person
    )

    companion object {
        val all = listOf(Home, Features, Discover, Profile)
    }
}

/** 监控配置页：自底部升起 / 向下收起，接近全屏 Sheet 的沉稳进场 */
object AppLimitTransitions {
    private const val EnterMs = 480
    private const val ExitMs = 380

    fun enter(): EnterTransition =
        slideInVertically(
            animationSpec = tween(EnterMs, easing = FastOutSlowInEasing),
            initialOffsetY = { it }
        ) + fadeIn(animationSpec = tween(300))

    fun popExit(): ExitTransition =
        slideOutVertically(
            animationSpec = tween(ExitMs, easing = FastOutSlowInEasing),
            targetOffsetY = { it }
        ) + fadeOut(animationSpec = tween(260))

    /** 下层页保持不动，避免和上滑叠成「双滑」 */
    fun holdExit(): ExitTransition = ExitTransition.None

    fun holdPopEnter(): EnterTransition = EnterTransition.None
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.isNavigatingToAppLimit(): Boolean {
    val route = targetState.destination.route ?: return false
    return route.startsWith("app_limit_add") ||
        route.startsWith("app_limit_edit") ||
        route.startsWith("app_limit_capability") ||
        route.startsWith("app_history") ||
        route.startsWith("intent_pool") ||
        route.startsWith("quick_intent_tags") ||
        route.startsWith("explore_usage_rank") ||
        route.startsWith("explore_time_ruler") ||
        route.startsWith("walk_awareness") ||
        route.startsWith("explore_app")
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.isPoppingFromAppLimit(): Boolean {
    val route = initialState.destination.route ?: return false
    return route.startsWith("app_limit_add") ||
        route.startsWith("app_limit_edit") ||
        route.startsWith("app_limit_capability") ||
        route.startsWith("app_history") ||
        route.startsWith("intent_pool") ||
        route.startsWith("quick_intent_tags") ||
        route.startsWith("explore_usage_rank") ||
        route.startsWith("explore_time_ruler") ||
        route.startsWith("walk_awareness") ||
        route.startsWith("explore_app")
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.isNavigatingToCapabilityBind(): Boolean {
    val route = targetState.destination.route ?: return false
    return route.startsWith("capability_bind") || route.startsWith("capability_batch_pick")
}

fun AnimatedContentTransitionScope<NavBackStackEntry>.isPoppingFromCapabilityBind(): Boolean {
    val route = initialState.destination.route ?: return false
    return route.startsWith("capability_bind") || route.startsWith("capability_batch_pick")
}

/** 收据 → 总览：把选中日/镜头写入底下总览的 SavedStateHandle，再 pop 露出。 */
object OverviewNavBridge {
    const val DATE_MS = "overview_bridge_date_ms"
    const val LENS = "overview_bridge_lens"
    const val CUT = "overview_bridge_cut"
}

/**
 * 总览 ↔ 收据贯通（隐藏为主）：
 * - 栈里已有总览：收据只是盖住它；回总览 = 写入桥接参数 + pop 露出（保留镜头/滚动）
 * - 否则（如从今日进收据）：用总览换掉收据，避免多一层
 */
fun NavController.navigateOverviewBridge(route: String) {
    val (lens, cut, dateMs) = parseOverviewRouteArgs(route)
    val hasOverview = runCatching { getBackStackEntry(Screen.Overview.route) }.isSuccess
    if (hasOverview) {
        val entry = getBackStackEntry(Screen.Overview.route)
        entry.savedStateHandle[OverviewNavBridge.LENS] = lens
        entry.savedStateHandle[OverviewNavBridge.CUT] = cut
        entry.savedStateHandle[OverviewNavBridge.DATE_MS] = dateMs
        popBackStack(Screen.Overview.route, inclusive = false)
    } else {
        navigate(route) {
            val hasReport = runCatching { getBackStackEntry(Screen.DayReport.route) }.isSuccess
            if (hasReport) {
                popUpTo(Screen.DayReport.route) { inclusive = true }
            }
            launchSingleTop = true
        }
    }
}

/**
 * 总览 → 收据：压在总览之上（隐藏总览）。
 * 若已有收据层则只换掉收据，不拆底下总览。
 */
fun NavController.navigateDayReportBridge(route: String) {
    val hasReport = runCatching { getBackStackEntry(Screen.DayReport.route) }.isSuccess
    navigate(route) {
        launchSingleTop = true
        if (hasReport) {
            popUpTo(Screen.DayReport.route) { inclusive = true }
        }
    }
}

private fun parseOverviewRouteArgs(route: String): Triple<String, String, Long> {
    val query = route.substringAfter('?', missingDelimiterValue = "")
    if (query.isEmpty()) return Triple("gate", "", 0L)
    val map = query.split('&').mapNotNull { part ->
        val idx = part.indexOf('=')
        if (idx <= 0) null
        else {
            val key = part.substring(0, idx)
            val value = android.net.Uri.decode(part.substring(idx + 1))
            key to value
        }
    }.toMap()
    return Triple(
        map["lens"].orEmpty().ifBlank { "gate" },
        map["cut"].orEmpty(),
        map["dateMs"]?.toLongOrNull() ?: 0L
    )
}
