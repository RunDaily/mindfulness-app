package com.life.mindfulnessapp.data.deeplink

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.Log

data class InstalledSearchDeepLink(
    val entry: SearchDeepLinkEntry,
    val installedLabel: String,
    val icon: Drawable?,
)

object SearchDeepLinkLauncher {
    private const val TAG = "SearchDeepLink"

    fun listInstalled(context: Context): List<InstalledSearchDeepLink> {
        val pm = context.packageManager
        return SearchDeepLinkCatalog.all.mapNotNull { entry ->
            if (!isInstalled(pm, entry.packageName)) return@mapNotNull null
            val label = runCatching {
                val info = pm.getApplicationInfo(entry.packageName, 0)
                pm.getApplicationLabel(info).toString()
            }.getOrDefault(entry.displayName)
            val icon = runCatching { pm.getApplicationIcon(entry.packageName) }.getOrNull()
            InstalledSearchDeepLink(entry = entry, installedLabel = label, icon = icon)
        }
    }

    fun isInstalled(pm: PackageManager, packageName: String): Boolean =
        runCatching {
            pm.getPackageInfo(packageName, 0)
            true
        }.getOrDefault(false)

    fun canResolve(context: Context, entry: SearchDeepLinkEntry, query: String): Boolean {
        val intent = buildIntent(entry, query) ?: return false
        return intent.resolveActivity(context.packageManager) != null
    }

    fun buildUriString(entry: SearchDeepLinkEntry, query: String): String =
        entry.buildUri(query.ifBlank { "测试" })

    fun buildIntent(entry: SearchDeepLinkEntry, query: String): Intent? {
        val uriString = buildUriString(entry, query)
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return null
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(entry.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    /**
     * @return null on success, error message on failure
     */
    fun launch(context: Context, entry: SearchDeepLinkEntry, query: String): String? {
        val intent = buildIntent(entry, query) ?: return "无法解析 Uri"
        return launchUri(context, buildUriString(entry, query), entry.packageName, intent)
    }

    fun buildIntent(entry: AppDeepLinkEntry, scheme: String = entry.scheme): Intent? {
        val uri = runCatching { Uri.parse(scheme) }.getOrNull() ?: return null
        return Intent(Intent.ACTION_VIEW, uri).apply {
            setPackage(entry.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canResolve(context: Context, entry: AppDeepLinkEntry): Boolean =
        entry.schemes.any { scheme ->
            val intent = buildIntent(entry, scheme) ?: return@any false
            intent.resolveActivity(context.packageManager) != null
        }

    /** @return null on success, error message on failure */
    fun launch(context: Context, entry: AppDeepLinkEntry): String? {
        var lastErr: String? = "无法解析 Uri"
        for (scheme in entry.schemes) {
            val intent = buildIntent(entry, scheme) ?: continue
            val err = launchUri(context, scheme, entry.packageName, intent)
            if (err == null) return null
            lastErr = err
        }
        return lastErr
    }

    private fun launchUri(
        context: Context,
        uriString: String,
        packageName: String,
        boundIntent: Intent
    ): String? {
        return try {
            if (boundIntent.resolveActivity(context.packageManager) == null) {
                // 部分 OEM 对 setPackage 后 resolve 过严，再试不绑包名
                val loose = Intent(Intent.ACTION_VIEW, Uri.parse(uriString)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                if (loose.resolveActivity(context.packageManager) == null) {
                    return "系统无法解析该深链（可能已失效）"
                }
                context.startActivity(loose)
                Log.d(TAG, "launched (loose): $uriString")
                null
            } else {
                context.startActivity(boundIntent)
                Log.d(TAG, "launched: $uriString → $packageName")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "launch failed: $uriString", e)
            e.message ?: "启动失败"
        }
    }
}
