package com.life.mindfulnessapp.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings

/**
 * Android 13+ 侧载安装的「受限设置」：开无障碍等敏感权限前，
 * 须先在应用详情里允许受限制设置，否则系统弹「已拒绝授予访问权限」。
 */
object RestrictedSettingsGuide {

    fun applies(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun appDetailsIntent(context: Context): Intent =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

    /** 保活清单 / 权限门用的单行路径 */
    val howOneLine: String =
        "应用详情右上角 ⋮ → 允许受限制设置（侧载安装开无障碍前必做）"

    /** 弹窗被拒时的说明 */
    val deniedDialogHint: String =
        "若出现「系统已拒绝向此应用授予访问权限」，请先完成此项，再回无障碍列表打开心锚。"

    /** 无障碍保活行的补充说明 */
    val accessibilityHow: String =
        "先进应用详情允许受限制设置，再开此项；窗口切换即时响应，拦截不必等触摸"
}
