package com.life.mindfulnessapp.data.deeplink

import android.net.Uri

/**
 * 第三方 App「带词搜索」深链条目。
 * `{q}` 由 [buildUri] 替换为 URL 编码关键词；[uriKind] 处理京东 / 值得买等特殊形态。
 *
 * 权威源在管理台 `ha_search_deeplinks`；本对象的 [builtin] 作离线兜底，
 * 远端同步后由 [SearchDeepLinkRepository] 调用 [replaceEffective]。
 */
data class SearchDeepLinkEntry(
    val id: String,
    val displayName: String,
    val packageName: String,
    val category: String,
    /** 展示与默认构建用模板 */
    val schemeTemplate: String,
    val supportsKeyword: Boolean,
    /** 刷逛门是否默认出「搜索直达」面（不可靠链应 false） */
    val preferSearchLanding: Boolean = supportsKeyword,
    /** 同包多条时，带词主条目优先 */
    val isPrimary: Boolean = false,
    val uriKind: String = URI_KIND_Q,
    val note: String? = null,
    val sortOrder: Int = 0,
) {
    fun buildUri(query: String): String {
        val q = query.trim()
        return when (uriKind) {
            URI_KIND_JD -> {
                val json =
                    """{"des":"productList","keyWord":"${q.replace("\"", "")}","from":"search","category":"jump"}"""
                "openapp.jdmobile://virtual?params=" + Uri.encode(json)
            }
            URI_KIND_SMZDM -> {
                val json =
                    """{"channelName":"home","search_type":"1","keyWord":"${q.replace("\"", "")}"}"""
                "smzdm://search?json=" + Uri.encode(json)
            }
            else -> schemeTemplate.replace("{q}", Uri.encode(q))
        }
    }

    companion object {
        const val URI_KIND_Q = "q"
        const val URI_KIND_JD = "jd"
        const val URI_KIND_SMZDM = "smzdm"
    }
}

object SearchDeepLinkCatalog {
    val builtin: List<SearchDeepLinkEntry> = listOf(
        // ── 内容社区 ──────────────────────────────────────────
        SearchDeepLinkEntry(
            id = "xhs_search",
            displayName = "小红书",
            packageName = "com.xingin.xhs",
            category = "内容",
            schemeTemplate = "xhsdiscover://search/result?keyword={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 10,
        ),
        SearchDeepLinkEntry(
            id = "xhs_shop",
            displayName = "小红书 · 商品搜",
            packageName = "com.xingin.xhs",
            category = "内容",
            schemeTemplate = "xhsdiscover://instore_search/result?keyword={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = false,
            note = "商城搜索",
            sortOrder = 11,
        ),
        SearchDeepLinkEntry(
            id = "kuaishou",
            displayName = "快手",
            packageName = "com.smile.gifmaker",
            category = "内容",
            schemeTemplate = "kwai://search?keyword={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 20,
        ),
        SearchDeepLinkEntry(
            id = "kuaishou_lite",
            displayName = "快手极速版",
            packageName = "com.kuaishou.nebula",
            category = "内容",
            schemeTemplate = "ksnebula://search?keyword={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 21,
        ),
        SearchDeepLinkEntry(
            id = "bilibili",
            displayName = "哔哩哔哩",
            packageName = "tv.danmaku.bili",
            category = "内容",
            schemeTemplate = "bilibili://search?keyword={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 30,
        ),
        SearchDeepLinkEntry(
            id = "weibo",
            displayName = "微博",
            packageName = "com.sina.weibo",
            category = "内容",
            schemeTemplate = "sinaweibo://searchall?q={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 40,
        ),
        SearchDeepLinkEntry(
            id = "weibo_lite",
            displayName = "微博极速版",
            packageName = "com.sina.weibolite",
            category = "内容",
            schemeTemplate = "weibolite://searchall?q={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 41,
        ),
        SearchDeepLinkEntry(
            id = "zhihu",
            displayName = "知乎",
            packageName = "com.zhihu.android",
            category = "内容",
            schemeTemplate = "zhihu://search?q={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 50,
        ),
        SearchDeepLinkEntry(
            id = "douban",
            displayName = "豆瓣",
            packageName = "com.douban.frodo",
            category = "内容",
            schemeTemplate = "douban:///search?q={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            note = "三斜杠 scheme",
            sortOrder = 60,
        ),
        SearchDeepLinkEntry(
            id = "douyin_search_page",
            displayName = "抖音 · 搜索页",
            packageName = "com.ss.android.ugc.aweme",
            category = "内容",
            schemeTemplate = "snssdk1128://search",
            supportsKeyword = false,
            preferSearchLanding = false,
            note = "公开资料仅能打开搜索页，不带词",
            sortOrder = 70,
        ),
        SearchDeepLinkEntry(
            id = "douyin_keyword_try",
            displayName = "抖音 · 带词试探",
            packageName = "com.ss.android.ugc.aweme",
            category = "内容",
            schemeTemplate = "snssdk1128://search?keyword={q}",
            supportsKeyword = true,
            preferSearchLanding = false,
            isPrimary = true,
            note = "非官方，大概率无效，仅供试探",
            sortOrder = 71,
        ),
        SearchDeepLinkEntry(
            id = "douyin_trending",
            displayName = "抖音 · 热搜",
            packageName = "com.ss.android.ugc.aweme",
            category = "内容",
            schemeTemplate = "snssdk1128://search/trending",
            supportsKeyword = false,
            preferSearchLanding = false,
            sortOrder = 72,
        ),
        SearchDeepLinkEntry(
            id = "douyin_lite_search",
            displayName = "抖音极速版 · 搜索页",
            packageName = "com.ss.android.ugc.aweme.lite",
            category = "内容",
            schemeTemplate = "snssdk2329://search",
            supportsKeyword = false,
            preferSearchLanding = false,
            sortOrder = 80,
        ),
        SearchDeepLinkEntry(
            id = "douyin_lite_keyword",
            displayName = "抖音极速版 · 带词试探",
            packageName = "com.ss.android.ugc.aweme.lite",
            category = "内容",
            schemeTemplate = "snssdk2329://search?keyword={q}",
            supportsKeyword = true,
            preferSearchLanding = false,
            isPrimary = true,
            note = "非官方试探",
            sortOrder = 81,
        ),

        // ── 电商 ──────────────────────────────────────────────
        SearchDeepLinkEntry(
            id = "taobao",
            displayName = "淘宝",
            packageName = "com.taobao.taobao",
            category = "电商",
            schemeTemplate = "taobao://s.taobao.com?q={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 100,
        ),
        SearchDeepLinkEntry(
            id = "tmall",
            displayName = "天猫",
            packageName = "com.tmall.wireless",
            category = "电商",
            schemeTemplate = "tmall://page.tm/search?q={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 110,
        ),
        SearchDeepLinkEntry(
            id = "jd",
            displayName = "京东",
            packageName = "com.jingdong.app.mall",
            category = "电商",
            schemeTemplate = "openapp.jdmobile://virtual?params={json}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            uriKind = SearchDeepLinkEntry.URI_KIND_JD,
            note = "params 为 JSON",
            sortOrder = 120,
        ),
        SearchDeepLinkEntry(
            id = "pdd",
            displayName = "拼多多",
            packageName = "com.xunmeng.pinduoduo",
            category = "电商",
            schemeTemplate = "pinduoduo://com.xunmeng.pinduoduo/search_result.html?search_key={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 130,
        ),
        SearchDeepLinkEntry(
            id = "smzdm",
            displayName = "什么值得买",
            packageName = "com.smzdm.client.android",
            category = "电商",
            schemeTemplate = "smzdm://search?json={json}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            uriKind = SearchDeepLinkEntry.URI_KIND_SMZDM,
            sortOrder = 140,
        ),

        // ── 本地生活 ──────────────────────────────────────────
        SearchDeepLinkEntry(
            id = "meituan",
            displayName = "美团",
            packageName = "com.sankuai.meituan",
            category = "本地生活",
            schemeTemplate = "imeituan://www.meituan.com/search?q={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 200,
        ),
        SearchDeepLinkEntry(
            id = "meituan_waimai",
            displayName = "美团外卖",
            packageName = "com.sankuai.meituan.takeoutnew",
            category = "本地生活",
            schemeTemplate = "meituanwaimai://waimai.meituan.com/search?query={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 210,
        ),
        SearchDeepLinkEntry(
            id = "dianping",
            displayName = "大众点评",
            packageName = "com.dianping.v1",
            category = "本地生活",
            schemeTemplate = "dianping://searchshoplist?keyword={q}",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            sortOrder = 220,
        ),
        SearchDeepLinkEntry(
            id = "baidumap",
            displayName = "百度地图",
            packageName = "com.baidu.BaiduMap",
            category = "本地生活",
            schemeTemplate = "baidumap://map/place/search?query={q}&src=andr.xinmao.debug",
            supportsKeyword = true,
            preferSearchLanding = true,
            isPrimary = true,
            note = "官方 URI（src 为统计来源）",
            sortOrder = 230,
        ),
    )

    val categoryOrder = listOf("内容", "电商", "本地生活")

    @Volatile
    private var effective: List<SearchDeepLinkEntry> = builtin

    /** 当前生效目录（远端缓存或 builtin） */
    val all: List<SearchDeepLinkEntry>
        get() = effective

    fun replaceEffective(entries: List<SearchDeepLinkEntry>) {
        effective = if (entries.isEmpty()) builtin else entries.sortedWith(
            compareBy<SearchDeepLinkEntry> { it.sortOrder }.thenBy { it.id }
        )
    }

    fun resetToBuiltin() {
        effective = builtin
    }

    fun findById(id: String): SearchDeepLinkEntry? =
        all.firstOrNull { it.id == id }

    /** 某包优先用的「带词搜索」条目（无则 null） */
    fun primaryKeywordEntry(packageName: String): SearchDeepLinkEntry? {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return null
        val candidates = all.filter { it.packageName == pkg && it.supportsKeyword }
        if (candidates.isEmpty()) return null
        return candidates.firstOrNull { it.isPrimary } ?: candidates.first()
    }

    fun supportsKeywordSearch(packageName: String): Boolean =
        primaryKeywordEntry(packageName) != null

    /** 目录是否建议默认出搜索面（不含本机 resolve / 用户开关） */
    fun preferSearchLanding(packageName: String): Boolean =
        primaryKeywordEntry(packageName)?.preferSearchLanding == true
}
