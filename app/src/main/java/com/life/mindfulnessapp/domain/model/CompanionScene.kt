package com.life.mindfulnessapp.domain.model

import android.content.pm.ApplicationInfo

/**
 * 使用中陪伴：无意图门时默认圆球；从拦截页进来的会话默认挂陪伴条。
 *
 * 结束键给意图门会话（主动收口）或纯音乐（停播并离开）。
 * 后台音乐不另起一条，挂在当前圆球/条上。
 */
enum class CompanionBarForm(val storageKey: String, val title: String, val hint: String) {
    AUTO("auto", "跟建议", "按应用类型自动选形态"),
    STANDARD("standard", "标准条", "双行：本次/限额 + 意图，有结束键"),
    LIGHT("light", "轻条", "单行本次或今日，更细；有意图时仍可结束"),
    IMMERSIVE("immersive", "沉浸点", "接近圆球，少挡画面"),
    ;

    companion object {
        fun fromStorage(raw: String?): CompanionBarForm =
            entries.firstOrNull { it.storageKey == raw } ?: AUTO
    }
}

enum class CompanionAppMode(val storageKey: String, val title: String, val hint: String, val chip: String) {
    FOLLOW("follow", "跟随全局", "意图门挂条，其余圆球；形态跟建议", "跟随"),
    ORB("orb", "只用圆球", "这个 App 始终桌面那种圆球", "圆球"),
    AUTO("auto", "跟建议", "按这个 App 的类型自动选", "建议"),
    STANDARD("standard", "标准条", "双行：本次/限额 + 意图，有结束键", "标准"),
    LIGHT("light", "轻条", "单行更细，不挡画面；有意图时仍可结束", "轻条"),
    IMMERSIVE("immersive", "沉浸点", "接近圆球，少挡画面", "沉浸"),
    ;

    companion object {
        fun fromStorage(raw: String?): CompanionAppMode =
            entries.firstOrNull { it.storageKey == raw } ?: FOLLOW

        fun next(current: CompanionAppMode): CompanionAppMode {
            val all = entries
            return all[(all.indexOf(current) + 1) % all.size]
        }
    }
}

enum class CompanionAppKind {
    GENERAL,
    MIXED,
    VIDEO,
    MUSIC,
    GAME,
    ;

    val isPureMusic: Boolean get() = this == MUSIC
}

data class CapsuleCompanionPrefs(
    val barEnabled: Boolean = false,
    val form: String = CompanionBarForm.AUTO.storageKey,
    val appMode: String = CompanionAppMode.FOLLOW.storageKey,
)

data class CompanionResolved(
    val showBar: Boolean,
    val form: CompanionBarForm,
    val kind: CompanionAppKind,
    val emphasizeSession: Boolean,
    val showStop: Boolean,
    val stopIsMusicExit: Boolean,
) {
    val isPureMusic: Boolean get() = kind.isPureMusic
}

object CompanionScene {

    fun resolve(
        packageName: String,
        prefs: CapsuleCompanionPrefs,
        hasIntentGate: Boolean,
        thisMediaPlaying: Boolean = false,
        applicationCategory: Int? = null,
    ): CompanionResolved {
        val kind = kindOf(packageName, applicationCategory)
        val override = CompanionAppMode.fromStorage(prefs.appMode)
        val formSpec: CompanionBarForm? = when (override) {
            // 跟随全局：仅意图门会话挂条；形态固定跟建议（设置页不再开开关）。
            CompanionAppMode.FOLLOW -> if (hasIntentGate) CompanionBarForm.AUTO else null
            // 拦截页会话即使这个 App 选了「只用圆球」，也要挂出时长条。
            CompanionAppMode.ORB -> if (hasIntentGate) CompanionBarForm.STANDARD else null
            CompanionAppMode.AUTO -> CompanionBarForm.AUTO
            CompanionAppMode.STANDARD -> CompanionBarForm.STANDARD
            CompanionAppMode.LIGHT -> CompanionBarForm.LIGHT
            CompanionAppMode.IMMERSIVE -> CompanionBarForm.IMMERSIVE
        }
        if (formSpec == null) {
            return CompanionResolved(
                showBar = false,
                form = CompanionBarForm.IMMERSIVE,
                kind = kind,
                emphasizeSession = hasIntentGate,
                showStop = false,
                stopIsMusicExit = false,
            )
        }
        val form = if (formSpec == CompanionBarForm.AUTO) {
            suggestedForm(kind, hasIntentGate, thisMediaPlaying)
        } else {
            formSpec
        }
        val shown = if (hasIntentGate && form == CompanionBarForm.IMMERSIVE) {
            CompanionBarForm.STANDARD
        } else {
            form
        }
        val musicExit = kind.isPureMusic
        val showStop = when {
            hasIntentGate -> true
            shown == CompanionBarForm.IMMERSIVE -> false
            musicExit -> true
            else -> false
        }
        return CompanionResolved(
            showBar = true,
            form = shown,
            kind = kind,
            emphasizeSession = hasIntentGate,
            showStop = showStop,
            stopIsMusicExit = musicExit && showStop,
        )
    }

    /** 使用中点开面板：当前选中的形态（跟随全局 = 无门时圆球）。 */
    fun inspectMode(prefs: CapsuleCompanionPrefs): CompanionAppMode {
        val override = CompanionAppMode.fromStorage(prefs.appMode)
        if (override != CompanionAppMode.FOLLOW) return override
        return CompanionAppMode.ORB
    }

    val INSPECT_MODES = listOf(
        CompanionAppMode.ORB,
        CompanionAppMode.AUTO,
        CompanionAppMode.STANDARD,
        CompanionAppMode.LIGHT,
        CompanionAppMode.IMMERSIVE,
    )

    fun suggestedForm(
        kind: CompanionAppKind,
        hasIntentGate: Boolean,
        thisMediaPlaying: Boolean = false,
    ): CompanionBarForm = when (kind) {
        CompanionAppKind.GAME, CompanionAppKind.VIDEO -> CompanionBarForm.IMMERSIVE
        CompanionAppKind.MUSIC -> CompanionBarForm.LIGHT
        CompanionAppKind.MIXED -> when {
            thisMediaPlaying -> CompanionBarForm.IMMERSIVE
            hasIntentGate -> CompanionBarForm.STANDARD
            else -> CompanionBarForm.LIGHT
        }
        CompanionAppKind.GENERAL ->
            if (hasIntentGate) CompanionBarForm.STANDARD else CompanionBarForm.LIGHT
    }

    fun kindOf(packageName: String, applicationCategory: Int? = null): CompanionAppKind {
        val pkg = packageName.trim()
        if (pkg.isEmpty()) return CompanionAppKind.GENERAL
        if (pkg in MUSIC_PACKAGES) return CompanionAppKind.MUSIC
        if (pkg in VIDEO_PACKAGES) return CompanionAppKind.VIDEO
        if (pkg in MIXED_PACKAGES) return CompanionAppKind.MIXED
        if (pkg in GAME_PACKAGES) return CompanionAppKind.GAME
        return when (applicationCategory) {
            ApplicationInfo.CATEGORY_GAME -> CompanionAppKind.GAME
            ApplicationInfo.CATEGORY_VIDEO -> CompanionAppKind.VIDEO
            ApplicationInfo.CATEGORY_AUDIO -> CompanionAppKind.MUSIC
            else -> CompanionAppKind.GENERAL
        }
    }

    private val MUSIC_PACKAGES = setOf(
        "com.netease.cloudmusic",
        "com.netease.cloudmusic.lite",
        "com.tencent.qqmusic",
        "com.kugou.android",
        "com.kugou.android.lite",
        "cn.kuwo.player",
        "com.apple.android.music",
        "com.spotify.music",
        "com.ximalaya.ting.android",
        "com.luna.music",
        "cmccwm.mobilemusic",
        "com.kugou.fanxing",
        "com.tencent.ibg.joox",
        "com.netease.cloudmusic.iot",
    )

    private val VIDEO_PACKAGES = setOf(
        "tv.danmaku.bili",
        "com.bilibili.app.in",
        "com.ss.android.ugc.aweme",
        "com.ss.android.ugc.aweme.lite",
        "com.ss.android.ugc.live",
        "com.smile.gifmaker",
        "com.kuaishou.nebula",
        "com.qiyi.video",
        "com.tencent.qqlive",
        "com.youku.phone",
        "com.ss.android.article.video",
        "com.hunantv.imgo.activity",
        "com.duowan.kiwi",
        "air.tv.douyu.android",
        "com.le123.ysdq",
        "com.phoenix.read",
    )

    private val MIXED_PACKAGES = setOf(
        "com.xingin.xhs",
        "com.sina.weibo",
        "com.zhihu.android",
        "com.ss.android.article.news",
        "com.tencent.news",
        "com.baidu.searchbox",
        "com.instagram.android",
        "com.twitter.android",
        "com.zhiliaoapp.musically",
    )

    private val GAME_PACKAGES = setOf(
        "com.tencent.tmgp.sgame",
        "com.tencent.tmgp.pubgmhd",
        "com.tencent.tmgp.cf",
        "com.tencent.jkchess",
        "com.miHoYo.Yuanshen",
        "com.miHoYo.GenshinImpact",
        "com.miHoYo.Nap",
        "com.miHoYo.bh3rd.cn",
        "com.netease.dwrg",
        "com.netease.hyxd",
        "com.tencent.tmgp.speedmobile",
    )
}
