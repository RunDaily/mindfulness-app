package com.life.mindfulnessapp.data.deeplink

import com.life.mindfulnessapp.domain.model.CommonIntentItem
import com.life.mindfulnessapp.domain.model.CommonIntentsCodec

/**
 * 第三方 App「固定页面」深链。
 *
 * 可作为常用意图的动作模板（绑定 [id]）；写意图时仍可按文案语义匹配作兜底。
 * 社区/公开资料汇总，非官方契约，版本升级可能失效——只收录不依赖 userId 的入口。
 *
 * 小红书文档：https://pages.xiaohongshu.com/activity/deeplink
 */
data class AppDeepLinkEntry(
    val id: String,
    val displayName: String,
    val packageName: String,
    /** 按序尝试；第一条为展示用主 scheme */
    val schemes: List<String>,
    /**
     * 意图语义匹配词（含展示名）。
     * [resolveFromPurpose] 会用「整句相等 / 去口语后相等 / 短句包含」命中。
     */
    val matchKeys: List<String> = emptyList(),
    /** 管理页说明：落到哪、适合什么意图 */
    val note: String? = null,
) {
    val scheme: String get() = schemes.first()

    fun allMatchKeys(): List<String> =
        (listOf(displayName) + matchKeys)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
}

object AppDeepLinkCatalog {
    /** 曾误推过、应清理出常用意图的文案（小红书无效项） */
    private val retiredLabels = setOf(
        "看收藏", "看专辑", "我的主页", "浏览记录", "去搜索"
    )

    /** 口语填充，去掉后再比意图核心 */
    private val fillerPhrases = listOf(
        "我想要", "我想", "我要", "想要", "打算",
        "打开", "进入进入", "进入", "去看看", "去下", "去一下",
        "看一下", "看一眼", "一下", "一会儿", "一会",
        "先", "就", "再", "把", "把下",
        "页面", "那个", "这个"
    )

    val all: List<AppDeepLinkEntry> = listOf(
        // ── 小红书 ──────────────────────────────────────────
        AppDeepLinkEntry(
            id = "xhs_message",
            displayName = "回消息",
            packageName = "com.xingin.xhs",
            schemes = listOf("xhsdiscover://message/center"),
            matchKeys = listOf(
                "看消息", "消息中心", "私信", "回私信", "收消息",
                "消息", "聊聊", "回信"
            ),
            note = "消息中心（官方）",
        ),
        AppDeepLinkEntry(
            id = "xhs_store",
            displayName = "逛商城",
            packageName = "com.xingin.xhs",
            schemes = listOf("xhsdiscover://home/store"),
            matchKeys = listOf(
                "商城", "购物", "买东西", "小红书商城", "去买", "逛店"
            ),
            note = "商城 Tab（官方）",
        ),
        AppDeepLinkEntry(
            id = "xhs_follow",
            displayName = "看关注",
            packageName = "com.xingin.xhs",
            schemes = listOf("xhsdiscover://home/follow"),
            matchKeys = listOf(
                "关注", "刷关注", "关注页", "关注流", "关注列表",
                "关注的人", "关注动态", "好友动态"
            ),
            note = "关注流（官方）",
        ),
        AppDeepLinkEntry(
            id = "xhs_explore",
            displayName = "看发现",
            packageName = "com.xingin.xhs",
            schemes = listOf("xhsdiscover://home/explore"),
            matchKeys = listOf("发现", "发现页", "刷发现", "推荐页"),
            note = "发现 Tab（官方）",
        ),
        AppDeepLinkEntry(
            id = "xhs_edit_profile",
            displayName = "编辑资料",
            packageName = "com.xingin.xhs",
            schemes = listOf("xhsdiscover://me/profile"),
            matchKeys = listOf(
                "改资料", "修改资料", "个人资料", "编辑个人信息",
                "改头像", "换头像", "改昵称", "换昵称", "资料页",
                "编辑主页", "改简介"
            ),
            note = "个人资料编辑（官方）",
        ),

        // ── 抖音 / 极速版 ─────────────────────────────────────
        AppDeepLinkEntry(
            id = "dy_mine",
            displayName = "我的",
            packageName = "com.ss.android.ugc.aweme",
            schemes = listOf(
                "snssdk1128://user/profile",
                "snssdk1128://user/homepage",
            ),
            matchKeys = listOf("个人主页", "我的主页", "个人中心", "主页"),
            note = "我的页（社区）",
        ),
        AppDeepLinkEntry(
            id = "dy_trending",
            displayName = "看热搜",
            packageName = "com.ss.android.ugc.aweme",
            schemes = listOf("snssdk1128://search/trending"),
            matchKeys = listOf("热搜", "热榜", "热搜榜", "抖音热搜"),
            note = "热搜榜（社区）",
        ),
        AppDeepLinkEntry(
            id = "dy_watch_later",
            displayName = "稍后再看",
            packageName = "com.ss.android.ugc.aweme",
            schemes = listOf("snssdk1128://aweme/watch_later_list"),
            matchKeys = listOf("稍后看", "待看", "稍候再看"),
            note = "稍后再看列表（社区）",
        ),
        AppDeepLinkEntry(
            id = "dy_message",
            displayName = "回消息",
            packageName = "com.ss.android.ugc.aweme",
            schemes = listOf("snssdk1128://land_tab?tabid=homepage_notification"),
            matchKeys = listOf("看消息", "消息", "私信", "通知"),
            note = "消息 Tab（社区）",
        ),
        AppDeepLinkEntry(
            id = "dy_edit_profile",
            displayName = "编辑主页",
            packageName = "com.ss.android.ugc.aweme",
            schemes = listOf("snssdk1128://profile_edit"),
            matchKeys = listOf("改资料", "编辑资料", "改头像", "改昵称"),
            note = "编辑主页（社区）",
        ),
        AppDeepLinkEntry(
            id = "dy_lite_mine",
            displayName = "我的",
            packageName = "com.ss.android.ugc.aweme.lite",
            schemes = listOf(
                "snssdk2329://user/profile",
                "snssdk2329://user/homepage",
            ),
            matchKeys = listOf("个人主页", "我的主页", "个人中心", "主页"),
            note = "极速版 · 我的",
        ),
        AppDeepLinkEntry(
            id = "dy_lite_trending",
            displayName = "看热搜",
            packageName = "com.ss.android.ugc.aweme.lite",
            schemes = listOf("snssdk2329://search/trending"),
            matchKeys = listOf("热搜", "热榜", "热搜榜"),
            note = "极速版 · 热搜",
        ),
        AppDeepLinkEntry(
            id = "dy_lite_watch_later",
            displayName = "稍后再看",
            packageName = "com.ss.android.ugc.aweme.lite",
            schemes = listOf("snssdk2329://aweme/watch_later_list"),
            matchKeys = listOf("稍后看", "待看"),
            note = "极速版 · 稍后再看",
        ),
        AppDeepLinkEntry(
            id = "dy_lite_message",
            displayName = "回消息",
            packageName = "com.ss.android.ugc.aweme.lite",
            schemes = listOf("snssdk2329://land_tab?tabid=homepage_notification"),
            matchKeys = listOf("看消息", "消息", "私信", "通知"),
            note = "极速版 · 消息",
        ),

        // ── 快手 / 极速版 ─────────────────────────────────────
        AppDeepLinkEntry(
            id = "ks_follow",
            displayName = "看关注",
            packageName = "com.smile.gifmaker",
            schemes = listOf("kwai://home/following"),
            matchKeys = listOf("关注", "刷关注", "关注页", "关注流"),
            note = "关注 Tab（社区）",
        ),
        AppDeepLinkEntry(
            id = "ks_message",
            displayName = "回消息",
            packageName = "com.smile.gifmaker",
            schemes = listOf(
                "kwai://messages/",
                "kwai://notifications/",
            ),
            matchKeys = listOf("看消息", "私信", "消息", "消息中心"),
            note = "私信/消息（社区）",
        ),
        AppDeepLinkEntry(
            id = "ks_discover",
            displayName = "看发现",
            packageName = "com.smile.gifmaker",
            schemes = listOf("kwai://home/hot"),
            matchKeys = listOf("发现", "发现页", "热门"),
            note = "发现/热门（社区）",
        ),
        AppDeepLinkEntry(
            id = "ks_lite_follow",
            displayName = "看关注",
            packageName = "com.kuaishou.nebula",
            schemes = listOf("ksnebula://home/following"),
            matchKeys = listOf("关注", "刷关注", "关注页", "关注流"),
            note = "极速版 · 关注",
        ),
        AppDeepLinkEntry(
            id = "ks_lite_message",
            displayName = "回消息",
            packageName = "com.kuaishou.nebula",
            schemes = listOf(
                "ksnebula://messages/",
                "ksnebula://notifications/",
            ),
            matchKeys = listOf("看消息", "私信", "消息"),
            note = "极速版 · 消息",
        ),

        // ── 微博 ──────────────────────────────────────────────
        AppDeepLinkEntry(
            id = "wb_message",
            displayName = "回消息",
            packageName = "com.sina.weibo",
            schemes = listOf("sinaweibo://message"),
            matchKeys = listOf("看消息", "消息", "私信", "通知"),
            note = "消息页（社区）",
        ),
        AppDeepLinkEntry(
            id = "wb_mine",
            displayName = "我的",
            packageName = "com.sina.weibo",
            schemes = listOf("sinaweibo://myprofile"),
            matchKeys = listOf("个人主页", "我的主页", "个人中心"),
            note = "我的（社区）",
        ),
        AppDeepLinkEntry(
            id = "wb_discover",
            displayName = "看发现",
            packageName = "com.sina.weibo",
            schemes = listOf("sinaweibo://discover"),
            matchKeys = listOf("发现", "发现页"),
            note = "发现（社区）",
        ),
        AppDeepLinkEntry(
            id = "wb_home",
            displayName = "看首页",
            packageName = "com.sina.weibo",
            schemes = listOf("sinaweibo://gotohome"),
            matchKeys = listOf("首页", "刷微博", "时间线"),
            note = "首页（社区）",
        ),
        AppDeepLinkEntry(
            id = "wb_at",
            displayName = "看@我",
            packageName = "com.sina.weibo",
            schemes = listOf("sinaweibo://atlist"),
            matchKeys = listOf("@我", "艾特我", "提到我"),
            note = "消息 · @我（社区）",
        ),

        // ── 知乎 ──────────────────────────────────────────────
        AppDeepLinkEntry(
            id = "zh_ask",
            displayName = "去提问",
            packageName = "com.zhihu.android",
            schemes = listOf("zhihu://ask"),
            matchKeys = listOf("提问", "问一个问题", "发问题"),
            note = "提问（社区）",
        ),
        AppDeepLinkEntry(
            id = "zh_settings",
            displayName = "设置",
            packageName = "com.zhihu.android",
            schemes = listOf("zhihu://settings"),
            matchKeys = listOf("设置页", "偏好设置"),
            note = "设置（社区）",
        ),
        AppDeepLinkEntry(
            id = "zh_feed",
            displayName = "看推荐",
            packageName = "com.zhihu.android",
            schemes = listOf("zhihu://feed/item/recommend"),
            matchKeys = listOf("推荐", "推荐流", "首页推荐"),
            note = "推荐流（社区）",
        ),

        // ── B站 ───────────────────────────────────────────────
        AppDeepLinkEntry(
            id = "bili_favorite",
            displayName = "看收藏",
            packageName = "tv.danmaku.bili",
            schemes = listOf("bilibili://main/favorite"),
            matchKeys = listOf("收藏", "收藏夹", "我的收藏"),
            note = "收藏（社区）",
        ),
        AppDeepLinkEntry(
            id = "bili_mine",
            displayName = "我的",
            packageName = "tv.danmaku.bili",
            schemes = listOf("bilibili://user_center"),
            matchKeys = listOf("个人中心", "我的主页", "个人主页"),
            note = "个人中心（社区）",
        ),
        AppDeepLinkEntry(
            id = "bili_home",
            displayName = "看首页",
            packageName = "tv.danmaku.bili",
            schemes = listOf("bilibili://home", "bilibili://root"),
            matchKeys = listOf("首页", "推荐"),
            note = "首页（社区）",
        ),
        AppDeepLinkEntry(
            id = "bili_in_favorite",
            displayName = "看收藏",
            packageName = "com.bilibili.app.in",
            schemes = listOf("bilibili://main/favorite"),
            matchKeys = listOf("收藏", "收藏夹", "我的收藏"),
            note = "国际版 · 收藏",
        ),
        AppDeepLinkEntry(
            id = "bili_in_mine",
            displayName = "我的",
            packageName = "com.bilibili.app.in",
            schemes = listOf("bilibili://user_center"),
            matchKeys = listOf("个人中心", "我的主页"),
            note = "国际版 · 个人中心",
        ),

        // ── 淘宝 ──────────────────────────────────────────────
        AppDeepLinkEntry(
            id = "tb_orders",
            displayName = "看订单",
            packageName = "com.taobao.taobao",
            schemes = listOf("taobao://go/my_orders"),
            matchKeys = listOf("订单", "我的订单", "查订单", "物流"),
            note = "我的订单（社区）",
        ),
        AppDeepLinkEntry(
            id = "tb_cart",
            displayName = "购物车",
            packageName = "com.taobao.taobao",
            schemes = listOf("taobao://cart.taobao.com/my_cart.htm"),
            matchKeys = listOf("看购物车", "车子", "加购"),
            note = "购物车（社区）",
        ),
        AppDeepLinkEntry(
            id = "tb_message",
            displayName = "回消息",
            packageName = "com.taobao.taobao",
            schemes = listOf("taobao://message/root"),
            matchKeys = listOf("看消息", "旺旺", "消息", "客服消息"),
            note = "消息（社区）",
        ),
        AppDeepLinkEntry(
            id = "tb_mine",
            displayName = "我的淘宝",
            packageName = "com.taobao.taobao",
            schemes = listOf("taobao://my.m.taobao.com/myTaobao.htm"),
            matchKeys = listOf("我的", "个人中心", "淘宝主页"),
            note = "我的淘宝（社区）",
        ),

        // ── 美团 ──────────────────────────────────────────────
        AppDeepLinkEntry(
            id = "mt_message",
            displayName = "回消息",
            packageName = "com.sankuai.meituan",
            schemes = listOf("imeituan://www.meituan.com/messagecenter"),
            matchKeys = listOf("看消息", "消息", "消息中心"),
            note = "消息中心（社区）",
        ),
        AppDeepLinkEntry(
            id = "mt_mine",
            displayName = "我的",
            packageName = "com.sankuai.meituan",
            schemes = listOf("imeituan://www.meituan.com/user"),
            matchKeys = listOf("个人中心", "我的主页"),
            note = "我的（社区）",
        ),
        AppDeepLinkEntry(
            id = "mt_cart",
            displayName = "购物车",
            packageName = "com.sankuai.meituan",
            schemes = listOf("imeituan://www.meituan.com/tabshoppingcart"),
            matchKeys = listOf("看购物车", "车子"),
            note = "购物车（社区）",
        ),
        AppDeepLinkEntry(
            id = "mt_food",
            displayName = "找美食",
            packageName = "com.sankuai.meituan",
            schemes = listOf("imeituan://www.meituan.com/food/homepage"),
            matchKeys = listOf("美食", "吃饭", "餐馆", "饭店"),
            note = "美食首页（社区）",
        ),
    )

    fun forPackage(packageName: String): List<AppDeepLinkEntry> =
        all.filter { it.packageName == packageName.trim() }

    fun findById(id: String): AppDeepLinkEntry? =
        all.firstOrNull { it.id == id }

    fun findByLabel(packageName: String, label: String): AppDeepLinkEntry? {
        val t = label.trim()
        if (t.isEmpty()) return null
        return forPackage(packageName).firstOrNull {
            it.displayName.equals(t, ignoreCase = true)
        }
    }

    /**
     * 按意图文案语义解析固定页深链。
     * 例：「想看关注」「改一下资料」→ 对应入口；搜词类长句不误伤。
     */
    fun resolveFromPurpose(packageName: String, purpose: String): AppDeepLinkEntry? {
        val raw = purpose.trim()
        if (raw.isEmpty()) return null
        findByLabel(packageName, raw)?.let { return it }

        val normalized = normalizePurpose(raw)
        if (normalized.isEmpty()) return null

        // 明显是「去搜某某」时不做固定页匹配，留给搜索落地
        if (looksLikeSearchQuery(raw, normalized)) return null

        var best: AppDeepLinkEntry? = null
        var bestScore = 0
        for (entry in forPackage(packageName)) {
            val score = scorePurpose(raw, normalized, entry) ?: continue
            if (score > bestScore) {
                bestScore = score
                best = entry
            }
        }
        return best.takeIf { bestScore >= 60 }
    }

    fun supportsPackage(packageName: String): Boolean =
        forPackage(packageName).isNotEmpty()

    fun isRetiredLabel(label: String): Boolean {
        val t = label.trim()
        // 「看收藏」仅小红书无效；B站等仍可用，故退休名单只在无包上下文时用于旧数据清理
        return retiredLabels.any { it.equals(t, ignoreCase = true) }
    }

    /**
     * 清理过期文案：仅对小红书清掉无效的「看收藏」等；其它包保留。
     */
    fun isRetiredForPackage(packageName: String, label: String): Boolean {
        if (packageName.trim() != "com.xingin.xhs") return false
        return isRetiredLabel(label)
    }

    fun defaultCommonIntents(packageName: String): List<CommonIntentItem> =
        forPackage(packageName)
            .take(CommonIntentsCodec.MAX_GATE_VISIBLE)
            .map {
                CommonIntentItem(
                    label = it.displayName,
                    showOnGate = true,
                    deepLinkId = it.id
                )
            }

    /**
     * 解析常用意图绑定的深链：优先 [CommonIntentItem.deepLinkId]，再按 label 精确名兜底。
     */
    fun resolveForCommonIntent(
        packageName: String,
        item: CommonIntentItem
    ): AppDeepLinkEntry? {
        val byId = CommonIntentsCodec.sanitizeDeepLinkId(item.deepLinkId)
            ?.let { findById(it) }
            ?.takeIf { it.packageName == packageName.trim() }
        if (byId != null) return byId
        return findByLabel(packageName, item.label)
    }

    /**
     * 为旧数据补上 deepLinkId（label 精确命中 catalog 时），并清掉失效 id。
     * @return Pair(enriched, changed)
     */
    fun enrichCommonIntents(
        packageName: String,
        items: List<CommonIntentItem>
    ): Pair<List<CommonIntentItem>, Boolean> {
        var changed = false
        val enriched = items.map { item ->
            val stored = CommonIntentsCodec.sanitizeDeepLinkId(item.deepLinkId)
            if (stored != null) {
                val entry = findById(stored)
                if (entry != null && entry.packageName == packageName.trim()) {
                    if (stored != item.deepLinkId) {
                        changed = true
                        item.copy(deepLinkId = stored)
                    } else {
                        item
                    }
                } else {
                    changed = true
                    item.copy(deepLinkId = null)
                }
            } else {
                val byLabel = findByLabel(packageName, item.label)
                if (byLabel != null) {
                    changed = true
                    item.copy(deepLinkId = byLabel.id)
                } else {
                    item
                }
            }
        }
        return enriched to changed
    }

    /**
     * 管理页副文：可直达 · 落到哪 / 来源 · 仅填意图
     * @param originLabel 「预设」或「自制」
     */
    fun tagManageCaption(
        packageName: String,
        item: CommonIntentItem,
        originLabel: String
    ): String {
        val entry = resolveForCommonIntent(packageName, item)
        return if (entry != null) {
            val dest = entry.note
                ?.substringBefore("（")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: "对应页"
            "可直达 · $dest"
        } else {
            "$originLabel · 仅填意图"
        }
    }

    /**
     * 新建标签时尽量绑 deepLinkId（文案命中目录）。
     */
    fun bindDeepLinkOnCreate(
        packageName: String,
        label: String,
        showOnGate: Boolean
    ): CommonIntentItem {
        val entry = findByLabel(packageName, label)
            ?: resolveFromPurpose(packageName, label)
        return CommonIntentItem(
            label = entry?.displayName?.takeIf {
                label.trim().equals(it, ignoreCase = true)
            } ?: label,
            showOnGate = showOnGate,
            deepLinkId = entry?.id
        )
    }

    private fun normalizePurpose(raw: String): String {
        var t = raw.lowercase()
            .replace(Regex("[\\s\\p{Punct}，。！？、；：\"\"''（）()【】\\[\\]…—-]"), "")
        fillerPhrases.sortedByDescending { it.length }.forEach { f ->
            t = t.replace(f, "")
        }
        if (t.startsWith("去") && t.length > 1) t = t.removePrefix("去")
        return t.trim()
    }

    private fun looksLikeSearchQuery(raw: String, normalized: String): Boolean {
        val t = raw.trim()
        if (t.length >= 18) return true
        if (normalized.length >= 14) return true
        val searchHints = listOf("搜", "搜索", "查找", "查一下", "找一下", "关键词")
        if (searchHints.any { t.contains(it) }) return true
        if (normalized.length >= 8 &&
            (normalized.startsWith("关注") || normalized.startsWith("发现")) &&
            !normalized.endsWith("关注") &&
            !normalized.endsWith("发现") &&
            !normalized.endsWith("页") &&
            !normalized.endsWith("流") &&
            !normalized.endsWith("列表")
        ) {
            return true
        }
        return false
    }

    private fun scorePurpose(
        raw: String,
        normalized: String,
        entry: AppDeepLinkEntry
    ): Int? {
        var best: Int? = null
        for (key in entry.allMatchKeys()) {
            val k = key.trim()
            if (k.isEmpty()) continue
            val kNorm = normalizePurpose(k).ifEmpty { k.lowercase() }
            val score = when {
                raw.equals(k, ignoreCase = true) -> 100
                normalized == kNorm -> 95
                normalized.length <= 10 && normalized.contains(kNorm) && kNorm.length >= 2 ->
                    70 + kNorm.length.coerceAtMost(20)
                normalized.length <= 12 && normalized.endsWith(kNorm) && kNorm.length >= 2 ->
                    75 + kNorm.length.coerceAtMost(15)
                else -> null
            } ?: continue
            if (best == null || score > best) best = score
        }
        return best
    }
}
