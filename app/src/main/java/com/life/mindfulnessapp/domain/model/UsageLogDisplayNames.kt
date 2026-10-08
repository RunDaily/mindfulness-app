package com.life.mindfulnessapp.domain.model

/**
 * 使用日志里系统表层的友好显示名 / 噪声过滤。
 * UsageStats 常把屏保、分享选择器等记成前台包，对「一天中的切换」无意义。
 */
object UsageLogDisplayNames {

    const val SCREEN_WAKE = "点亮屏幕"
    const val HOME = "桌面"

    fun displayName(packageName: String, rawLabel: String): String {
        if (isScreenWakeSurface(packageName, rawLabel)) return SCREEN_WAKE
        return rawLabel.ifBlank { packageName.substringAfterLast('.') }
    }

    /** 屏保 / 分享选择器 / 权限页等：不进打开、不进跳到 */
    fun isNoiseSystemSurface(packageName: String, rawLabel: String = ""): Boolean =
        isScreenWakeSurface(packageName, rawLabel) ||
            isTransientChooserSurface(packageName, rawLabel)

    fun isScreenWakeSurface(packageName: String, rawLabel: String = ""): Boolean {
        val pkg = packageName.lowercase()
        val label = rawLabel
        if (label.contains("屏保") ||
            label.contains("息屏显示") ||
            label.contains("息屏时钟") ||
            label.contains("Always on", ignoreCase = true) ||
            label.contains("Always-on", ignoreCase = true) ||
            label.equals("AOD", ignoreCase = true) ||
            label.contains("Dream", ignoreCase = true)
        ) {
            return true
        }
        if (pkg.contains(".dream") ||
            pkg.contains("dreams") ||
            pkg.contains("screensaver") ||
            pkg.contains(".aod") ||
            pkg.endsWith(".aod") ||
            pkg.contains("aodservice")
        ) {
            return true
        }
        return pkg in KNOWN_SCREEN_WAKE_PACKAGES
    }

    /**
     * 系统「打开方式 / 分享到…」选择器等瞬时层。
     * 例如 IntentResolver、ChooserActivity —— 不是用户跳去的 App。
     */
    fun isTransientChooserSurface(packageName: String, rawLabel: String = ""): Boolean {
        val pkg = packageName.lowercase()
        val label = rawLabel
        if (label.contains("IntentResolver", ignoreCase = true) ||
            label.contains("ResolverActivity", ignoreCase = true) ||
            label.contains("ChooserActivity", ignoreCase = true) ||
            label.contains("打开方式") ||
            label.contains("分享到")
        ) {
            return true
        }
        if (pkg.contains("intentresolver") ||
            pkg.contains("permissioncontroller") ||
            pkg.endsWith(".permissioncontroller") ||
            pkg in KNOWN_CHOOSER_PACKAGES
        ) {
            return true
        }
        if ((pkg == "android" || pkg.contains("systemui")) &&
            (label.contains("resolver", ignoreCase = true) ||
                label.contains("chooser", ignoreCase = true))
        ) {
            return true
        }
        return false
    }

    private val KNOWN_SCREEN_WAKE_PACKAGES = setOf(
        "com.miui.aod",
        "com.android.systemui.aod",
        "com.samsung.android.app.aodservice",
        "com.samsung.android.app.dressroom",
        "com.huawei.aod",
        "com.coloros.aod",
        "com.oplus.aod",
        "com.oneplus.aod",
        "com.google.android.apps.dream",
        "com.android.dreams.basic",
        "com.android.dreams.phototable"
    )

    private val KNOWN_CHOOSER_PACKAGES = setOf(
        "com.android.intentresolver",
        "com.google.android.intentresolver",
        "com.android.internal.app",
        "com.android.permissioncontroller",
        "com.google.android.permissioncontroller"
    )
}
