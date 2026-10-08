package com.life.mindfulnessapp.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.life.mindfulnessapp.BuildConfig
import com.life.mindfulnessapp.data.AppPreferences
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.AppReleaseResponse
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

data class UpdateCheckResult(
    val hasUpdate: Boolean,
    val forceUpdate: Boolean,
    val release: AppReleaseResponse?,
    val error: String? = null
)

data class WhatsNewContent(
    val versionCode: Int,
    val versionName: String,
    val changelog: String
)

/**
 * 官网 APK 版本检查、下载与安装。
 * Play 渠道不会走应用内下载，仅可能打开商店页（由调用方分流）。
 */
@Singleton
class AppUpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val apiService: ApiService,
    private val appPreferences: AppPreferences
) {

    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.MINUTES)
            .writeTimeout(5, TimeUnit.MINUTES)
            .build()
    }

    suspend fun check(): UpdateCheckResult {
        return try {
            val release = apiService.getLatestRelease()
            if (!release.success) {
                return UpdateCheckResult(
                    hasUpdate = false,
                    forceUpdate = false,
                    release = null,
                    error = release.error?.takeIf { it.isNotBlank() } ?: "检查更新失败"
                )
            }
            val current = BuildConfig.VERSION_CODE
            val hasUpdate = release.version_code > current
            val belowMin = current < release.min_version_code
            val force = belowMin || (hasUpdate && release.force_update)
            UpdateCheckResult(
                hasUpdate = hasUpdate || belowMin,
                forceUpdate = force,
                release = release
            )
        } catch (e: Exception) {
            UpdateCheckResult(
                hasUpdate = false,
                forceUpdate = false,
                release = null,
                error = e.message?.takeIf { it.isNotBlank() } ?: "网络异常，请稍后重试"
            )
        }
    }

    fun isWebsiteChannel(): Boolean =
        BuildConfig.DISTRIBUTION_CHANNEL.equals("website", ignoreCase = true)

    fun shouldOfferAfterSkip(release: AppReleaseResponse, forceUpdate: Boolean): Boolean {
        if (forceUpdate) return true
        return release.version_code > appPreferences.getSkippedUpdateVersionCode()
    }

    fun getLastPromptedUpdateVersionCode(): Int =
        appPreferences.getLastPromptedUpdateVersionCode()

    fun setLastPromptedUpdateVersionCode(versionCode: Int) {
        appPreferences.setLastPromptedUpdateVersionCode(versionCode)
    }

    fun shouldPromptOptionalUpdate(release: AppReleaseResponse): Boolean {
        return release.version_code > getLastPromptedUpdateVersionCode()
    }

    fun skipVersion(versionCode: Int) {
        appPreferences.setSkippedUpdateVersionCode(versionCode)
    }

    /** 升级弹窗出现时缓存说明，安装重启后可离线展示 */
    fun cacheWhatsNewFromRelease(release: AppReleaseResponse) {
        appPreferences.cacheWhatsNewNotes(
            versionCode = release.version_code,
            versionName = release.version_name,
            changelog = release.changelog
        )
    }

    /**
     * 解析当前版本是否应展示「更新说明」。
     * - 新装 / 功能首次上线：静默种子，不弹
     * - 有升级且未看过：优先本地缓存，否则用 [remote]（通常为 latest）
     */
    fun resolveWhatsNew(
        remote: AppReleaseResponse? = null
    ): WhatsNewContent? {
        val current = BuildConfig.VERSION_CODE
        val lastSeen = appPreferences.getLastSeenWhatsNewVersionCode()
        if (lastSeen < 0) {
            appPreferences.markWhatsNewSeen(current)
            return null
        }
        if (current <= lastSeen) return null

        appPreferences.getCachedWhatsNewNotes(current)?.let { (code, name, notes) ->
            return WhatsNewContent(versionCode = code, versionName = name, changelog = notes)
        }

        if (remote != null &&
            remote.success &&
            remote.version_code == current
        ) {
            val notes = remote.changelog.trim()
            if (notes.isNotEmpty()) {
                return WhatsNewContent(
                    versionCode = current,
                    versionName = remote.version_name.ifBlank { BuildConfig.VERSION_NAME },
                    changelog = notes
                )
            }
            // 服务端无说明：视为无需展示，避免每次启动重试
            appPreferences.markWhatsNewSeen(current)
            return null
        }
        return null
    }

    fun markWhatsNewSeen(versionCode: Int) {
        appPreferences.markWhatsNewSeen(versionCode)
    }

    fun openUrlPreferApk(release: AppReleaseResponse): String {
        return when {
            release.apk_url.isNotBlank() -> release.apk_url
            release.download_page_url.isNotBlank() -> release.download_page_url
            else -> AppPreferences.HEART_ANCHOR_WEB_URL
        }
    }

    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    fun installPermissionSettingsIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            )
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }

    /**
     * 下载 APK 到 cache/updates/，[onProgress] 取值 0f..1f。
     */
    suspend fun downloadApk(
        release: AppReleaseResponse,
        onProgress: (Float) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        val url = release.apk_url.trim()
        if (url.isBlank()) {
            return@withContext Result.failure(IOException("暂无安装包地址，请前往官网下载"))
        }
        val dir = File(context.cacheDir, UPDATES_DIR).apply {
            if (!exists()) mkdirs()
        }
        // 清理旧包，避免占满缓存
        dir.listFiles()?.forEach { old ->
            if (old.isFile && old.name.endsWith(".apk")) old.delete()
        }
        val target = File(dir, "heart-anchor-${release.version_code}.apk")
        val tmp = File(dir, "heart-anchor-${release.version_code}.apk.part")
        if (tmp.exists()) tmp.delete()

        try {
            val request = Request.Builder().url(url).get().build()
            downloadClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(
                        IOException("下载失败（${response.code}）")
                    )
                }
                val body = response.body ?: return@withContext Result.failure(
                    IOException("下载失败：空响应")
                )
                val total = body.contentLength()
                body.byteStream().use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                        var readTotal = 0L
                        var lastEmit = -1
                        while (true) {
                            coroutineContext.ensureActive()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            readTotal += read
                            if (total > 0L) {
                                val pct = ((readTotal * 100) / total).toInt().coerceIn(0, 100)
                                if (pct != lastEmit) {
                                    lastEmit = pct
                                    onProgress(pct / 100f)
                                }
                            } else {
                                onProgress(-1f) // 未知长度
                            }
                        }
                        output.flush()
                    }
                }
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) {
                tmp.copyTo(target, overwrite = true)
                tmp.delete()
            }
            onProgress(1f)
            Result.success(target)
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (tmp.exists()) tmp.delete()
            throw e
        } catch (e: Exception) {
            if (tmp.exists()) tmp.delete()
            Result.failure(
                if (e is IOException) e
                else IOException(e.message?.takeIf { it.isNotBlank() } ?: "下载失败", e)
            )
        }
    }

    fun installApk(file: File): Result<Unit> {
        return try {
            if (!file.exists() || file.length() <= 0L) {
                return Result.failure(IOException("安装包不存在或已损坏"))
            }
            if (!canInstallPackages()) {
                return Result.failure(SecurityException(NEED_INSTALL_PERMISSION))
            }
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(intent)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        private const val UPDATES_DIR = "updates"
        const val NEED_INSTALL_PERMISSION = "NEED_INSTALL_PERMISSION"
    }
}
