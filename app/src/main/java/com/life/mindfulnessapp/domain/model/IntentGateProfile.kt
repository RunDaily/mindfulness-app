package com.life.mindfulnessapp.domain.model

import com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
import com.life.mindfulnessapp.data.deeplink.SearchDeepLinkCatalog

/** 意图门形态：探索型搜为主 / 事务型做为主 / 经典手写 */
enum class IntentGateKind {
    EXPLORE,
    TASK,
    CLASSIC,
}

/** 确认进入后的落地方式 */
enum class IntentLandMode {
    /** 尽量深链到对方搜索结果 */
    SEARCH,
    /** 深链到对方固定页面（收藏 / 消息等） */
    DEEPLINK,
    /** 普通恢复 / 打开 App */
    NORMAL,
}

data class IntentGateAction(
    val id: String,
    val label: String,
    /** 绑定 [com.life.mindfulnessapp.data.deeplink.AppDeepLinkEntry.id]；空则仅填意图 */
    val deepLinkId: String? = null,
) {
    val canDeepLink: Boolean get() = !deepLinkId.isNullOrBlank()
}

/**
 * 某 App 的意图门形态配置。
 * 内置名单定分型与「其他意图 / 常用操作」。
 */
data class IntentGateProfile(
    val kind: IntentGateKind,
    val askQuestion: String,
    val fieldHint: String,
    val collapsedHint: String,
    /**
     * 探索门「其他意图」预设（不含随便看看），最多 [MAX_EXPLORE_OTHER_OPS]。
     * 随便看看由 UI 降权单独展示。
     */
    val secondaryActions: List<IntentGateAction> = emptyList(),
    /** 事务：常用操作（末格可为搜一下） */
    val primaryActions: List<IntentGateAction> = emptyList(),
    val preferSearchLanding: Boolean = false,
) {
    val canSearchLand: Boolean get() = preferSearchLanding
}

object IntentGateProfiles {
    const val MAX_EXPLORE_OTHER_OPS = 3
    const val MAX_QUICK_INTENT_TAGS = 6
    const val MAX_TASK_COMMON_OPS = 5

    val searchAction = IntentGateAction("search", "搜一下…")

    private val classic = IntentGateProfile(
        kind = IntentGateKind.CLASSIC,
        askQuestion = "此刻打开它，是为了什么？",
        fieldHint = "写下这一次要做的事",
        collapsedHint = "写下这一次要做的事",
    )

    fun resolve(packageName: String): IntentGateProfile {
        val base = resolveBase(packageName)
        return when (base.kind) {
            IntentGateKind.EXPLORE -> base.copy(
                secondaryActions = base.secondaryActions.take(MAX_EXPLORE_OTHER_OPS)
            )
            IntentGateKind.TASK -> {
                val core = base.primaryActions.filterNot { isSearchAction(it) }
                    .take(MAX_TASK_COMMON_OPS)
                base.copy(
                    primaryActions = (core + searchAction).distinctBy { it.label }
                )
            }
            IntentGateKind.CLASSIC -> base
        }
    }

    /**
     * 写下意图路上的快捷标签（完整列表，不裁成旧「其它进法」的 3 个上限）。
     * 冷启动种子；用户改过以后读 [com.life.mindfulnessapp.data.repository.AppLimitRepository.getCommonIntents]。
     */
    fun quickIntentTags(packageName: String): List<IntentGateAction> =
        resolveBase(packageName).secondaryActions
            .filterNot { isBrowseLikeLabel(it.label) || it.id == BrowseCasualIntent.ACTION_ID }
            .filterNot { isSearchAction(it) }
            .distinctBy { it.label.trim() }
            .take(MAX_QUICK_INTENT_TAGS)
            .map { action ->
                val entry = AppDeepLinkCatalog.findByLabel(packageName, action.label)
                    ?: AppDeepLinkCatalog.resolveFromPurpose(packageName, action.label)
                if (entry == null) action
                else action.copy(
                    label = if (action.label.trim().equals(entry.displayName, ignoreCase = true)) {
                        entry.displayName
                    } else {
                        action.label
                    },
                    deepLinkId = entry.id
                )
            }

    /** 预设 → 常用意图存储形态（门上全开）。优先固定页深链目录，文案与 id 对齐。 */
    fun presetCommonIntents(packageName: String): List<CommonIntentItem> {
        val fromCatalog = AppDeepLinkCatalog.forPackage(packageName)
            .take(MAX_QUICK_INTENT_TAGS)
            .map {
                CommonIntentItem(
                    label = it.displayName,
                    showOnGate = true,
                    deepLinkId = it.id
                )
            }
        if (fromCatalog.isNotEmpty()) return fromCatalog
        return quickIntentTags(packageName).map { action ->
            val entry = AppDeepLinkCatalog.findByLabel(packageName, action.label)
                ?: AppDeepLinkCatalog.resolveFromPurpose(packageName, action.label)
            CommonIntentItem(
                label = action.label,
                showOnGate = true,
                deepLinkId = entry?.id
            )
        }
    }

    fun isPresetLabel(packageName: String, label: String): Boolean {
        val key = label.trim()
        if (key.isEmpty()) return false
        if (AppDeepLinkCatalog.findByLabel(packageName, key) != null) return true
        return quickIntentTags(packageName).any {
            it.label.trim().equals(key, ignoreCase = true)
        }
    }

    private fun resolveBase(packageName: String): IntentGateProfile {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return classic
        builtins[pkg]?.let { return it }
        if (SearchDeepLinkCatalog.supportsKeywordSearch(pkg)) {
            return exploreDefault(pkg)
        }
        return classic
    }

    private fun exploreDefault(packageName: String) = IntentGateProfile(
        kind = IntentGateKind.EXPLORE,
        askQuestion = "此刻要查什么",
        fieldHint = "输入内容，直达搜索",
        collapsedHint = "输入内容，直达搜索",
        secondaryActions = defaultExploreSecondary(packageName),
        preferSearchLanding = true,
    )

    /** 预设「其他意图」；刷关注 / 随便看看等流式入口不放这里 */
    private fun defaultExploreSecondary(packageName: String): List<IntentGateAction> =
        when (packageName) {
            "com.xingin.xhs" -> listOf(
                IntentGateAction("xhs_message", "回消息", deepLinkId = "xhs_message"),
                IntentGateAction("xhs_edit_profile", "编辑资料", deepLinkId = "xhs_edit_profile"),
                IntentGateAction("xhs_follow", "看关注", deepLinkId = "xhs_follow"),
                IntentGateAction("xhs_store", "逛商城", deepLinkId = "xhs_store"),
                IntentGateAction("xhs_explore", "看发现", deepLinkId = "xhs_explore"),
                IntentGateAction("post", "发布内容"),
            )
            "tv.danmaku.bili" -> listOf(
                IntentGateAction("bili_favorite", "看收藏", deepLinkId = "bili_favorite"),
                IntentGateAction("bili_mine", "我的", deepLinkId = "bili_mine"),
                IntentGateAction("bili_home", "看首页", deepLinkId = "bili_home"),
                IntentGateAction("msg", "回消息"),
                IntentGateAction("history", "看历史"),
            )
            "com.bilibili.app.in" -> listOf(
                IntentGateAction("bili_in_favorite", "看收藏", deepLinkId = "bili_in_favorite"),
                IntentGateAction("bili_in_mine", "我的", deepLinkId = "bili_in_mine"),
                IntentGateAction("msg", "回消息"),
                IntentGateAction("history", "看历史"),
            )
            "com.ss.android.ugc.aweme" -> listOf(
                IntentGateAction("dy_message", "回消息", deepLinkId = "dy_message"),
                IntentGateAction("dy_watch_later", "稍后再看", deepLinkId = "dy_watch_later"),
                IntentGateAction("dy_trending", "看热搜", deepLinkId = "dy_trending"),
                IntentGateAction("dy_mine", "我的", deepLinkId = "dy_mine"),
                IntentGateAction("dy_edit_profile", "编辑主页", deepLinkId = "dy_edit_profile"),
            )
            "com.ss.android.ugc.aweme.lite" -> listOf(
                IntentGateAction("dy_lite_message", "回消息", deepLinkId = "dy_lite_message"),
                IntentGateAction("dy_lite_watch_later", "稍后再看", deepLinkId = "dy_lite_watch_later"),
                IntentGateAction("dy_lite_trending", "看热搜", deepLinkId = "dy_lite_trending"),
                IntentGateAction("dy_lite_mine", "我的", deepLinkId = "dy_lite_mine"),
            )
            "com.smile.gifmaker" -> listOf(
                IntentGateAction("ks_message", "回消息", deepLinkId = "ks_message"),
                IntentGateAction("ks_follow", "看关注", deepLinkId = "ks_follow"),
                IntentGateAction("ks_discover", "看发现", deepLinkId = "ks_discover"),
                IntentGateAction("post", "发布内容"),
            )
            "com.kuaishou.nebula" -> listOf(
                IntentGateAction("ks_lite_message", "回消息", deepLinkId = "ks_lite_message"),
                IntentGateAction("ks_lite_follow", "看关注", deepLinkId = "ks_lite_follow"),
                IntentGateAction("post", "发布内容"),
            )
            "com.sina.weibo" -> listOf(
                IntentGateAction("wb_message", "回消息", deepLinkId = "wb_message"),
                IntentGateAction("wb_at", "看@我", deepLinkId = "wb_at"),
                IntentGateAction("wb_discover", "看发现", deepLinkId = "wb_discover"),
                IntentGateAction("wb_home", "看首页", deepLinkId = "wb_home"),
                IntentGateAction("post", "发微博"),
            )
            else -> listOf(
                IntentGateAction("msg", "回消息"),
            )
        }

    fun isBrowseLikeLabel(label: String): Boolean {
        val t = label.trim()
        return t.contains("随便看看") || t.contains("刷关注") || t == "看动态"
    }

    private val builtins: Map<String, IntentGateProfile> = mapOf(
        "com.xingin.xhs" to exploreDefault("com.xingin.xhs"),
        "tv.danmaku.bili" to exploreDefault("tv.danmaku.bili"),
        "com.bilibili.app.in" to exploreDefault("com.bilibili.app.in"),
        "com.ss.android.ugc.aweme" to IntentGateProfile(
            kind = IntentGateKind.EXPLORE,
            askQuestion = "此刻要查什么",
            fieldHint = "输入内容，直达搜索",
            collapsedHint = "输入内容，直达搜索",
            secondaryActions = listOf(
                IntentGateAction("dy_message", "回消息", deepLinkId = "dy_message"),
                IntentGateAction("dy_watch_later", "稍后再看", deepLinkId = "dy_watch_later"),
                IntentGateAction("dy_trending", "看热搜", deepLinkId = "dy_trending"),
                IntentGateAction("dy_mine", "我的", deepLinkId = "dy_mine"),
                IntentGateAction("dy_edit_profile", "编辑主页", deepLinkId = "dy_edit_profile"),
            ),
            preferSearchLanding = false,
        ),
        "com.ss.android.ugc.aweme.lite" to IntentGateProfile(
            kind = IntentGateKind.EXPLORE,
            askQuestion = "此刻要查什么",
            fieldHint = "输入内容，直达搜索",
            collapsedHint = "输入内容，直达搜索",
            secondaryActions = listOf(
                IntentGateAction("dy_lite_message", "回消息", deepLinkId = "dy_lite_message"),
                IntentGateAction("dy_lite_watch_later", "稍后再看", deepLinkId = "dy_lite_watch_later"),
                IntentGateAction("dy_lite_trending", "看热搜", deepLinkId = "dy_lite_trending"),
                IntentGateAction("dy_lite_mine", "我的", deepLinkId = "dy_lite_mine"),
            ),
            preferSearchLanding = false,
        ),
        "com.smile.gifmaker" to exploreDefault("com.smile.gifmaker").copy(
            secondaryActions = listOf(
                IntentGateAction("ks_message", "回消息", deepLinkId = "ks_message"),
                IntentGateAction("ks_follow", "看关注", deepLinkId = "ks_follow"),
                IntentGateAction("ks_discover", "看发现", deepLinkId = "ks_discover"),
            )
        ),
        "com.kuaishou.nebula" to exploreDefault("com.kuaishou.nebula").copy(
            secondaryActions = listOf(
                IntentGateAction("ks_lite_message", "回消息", deepLinkId = "ks_lite_message"),
                IntentGateAction("ks_lite_follow", "看关注", deepLinkId = "ks_lite_follow"),
            )
        ),
        "com.sina.weibo" to exploreDefault("com.sina.weibo").copy(
            secondaryActions = listOf(
                IntentGateAction("wb_message", "回消息", deepLinkId = "wb_message"),
                IntentGateAction("wb_at", "看@我", deepLinkId = "wb_at"),
                IntentGateAction("wb_discover", "看发现", deepLinkId = "wb_discover"),
            )
        ),
        "com.taobao.taobao" to exploreDefault("com.taobao.taobao").copy(
            secondaryActions = listOf(
                IntentGateAction("tb_orders", "看订单", deepLinkId = "tb_orders"),
                IntentGateAction("tb_cart", "购物车", deepLinkId = "tb_cart"),
                IntentGateAction("tb_message", "回消息", deepLinkId = "tb_message"),
                IntentGateAction("tb_mine", "我的淘宝", deepLinkId = "tb_mine"),
            )
        ),
        "com.tmall.wireless" to exploreDefault("com.tmall.wireless").copy(
            secondaryActions = listOf(
                IntentGateAction("order", "看订单"),
                IntentGateAction("cart", "购物车"),
            )
        ),
        "com.zhihu.android" to exploreDefault("com.zhihu.android").copy(
            secondaryActions = listOf(
                IntentGateAction("zh_ask", "去提问", deepLinkId = "zh_ask"),
                IntentGateAction("zh_feed", "看推荐", deepLinkId = "zh_feed"),
                IntentGateAction("zh_settings", "设置", deepLinkId = "zh_settings"),
            )
        ),
        "com.sankuai.meituan" to exploreDefault("com.sankuai.meituan").copy(
            secondaryActions = listOf(
                IntentGateAction("mt_message", "回消息", deepLinkId = "mt_message"),
                IntentGateAction("mt_food", "找美食", deepLinkId = "mt_food"),
                IntentGateAction("mt_cart", "购物车", deepLinkId = "mt_cart"),
                IntentGateAction("mt_mine", "我的", deepLinkId = "mt_mine"),
            )
        ),
        "com.tencent.mm" to IntentGateProfile(
            kind = IntentGateKind.TASK,
            askQuestion = "这次要做什么？",
            fieldHint = "补充一句（可选）",
            collapsedHint = "或写一句这次要做的事",
            primaryActions = listOf(
                IntentGateAction("reply", "回消息"),
                IntentGateAction("send", "发消息"),
                IntentGateAction("oa", "看公众号"),
                IntentGateAction("scan", "扫一扫"),
                IntentGateAction("channel", "看视频号"),
            ),
            preferSearchLanding = false,
        ),
        "com.tencent.wework" to IntentGateProfile(
            kind = IntentGateKind.TASK,
            askQuestion = "这次要做什么？",
            fieldHint = "补充一句（可选）",
            collapsedHint = "或写一句这次要做的事",
            primaryActions = listOf(
                IntentGateAction("reply", "回消息"),
                IntentGateAction("send", "发消息"),
                IntentGateAction("meeting", "开会"),
                IntentGateAction("doc", "看文档"),
            ),
            preferSearchLanding = false,
        ),
        "com.eg.android.AlipayGphone" to IntentGateProfile(
            kind = IntentGateKind.TASK,
            askQuestion = "这次要做什么？",
            fieldHint = "补充一句（可选）",
            collapsedHint = "或写一句这次要做的事",
            primaryActions = listOf(
                IntentGateAction("pay", "付款"),
                IntentGateAction("collect", "收款"),
                IntentGateAction("bill", "看账单"),
                IntentGateAction("transfer", "转账"),
            ),
            preferSearchLanding = false,
        ),
    )

    fun isSearchAction(action: IntentGateAction): Boolean =
        action.id == "search" || isSearchLikeLabel(action.label)

    fun isSearchLikeLabel(label: String): Boolean {
        val t = label.trim()
        return t.startsWith("搜") || t.contains("搜一下")
    }
}
