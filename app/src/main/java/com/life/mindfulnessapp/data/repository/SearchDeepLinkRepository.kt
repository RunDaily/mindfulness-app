package com.life.mindfulnessapp.data.repository

import android.content.Context
import android.util.Log
import com.life.mindfulnessapp.data.deeplink.SearchDeepLinkCatalog
import com.life.mindfulnessapp.data.deeplink.SearchDeepLinkEntry
import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.SearchDeepLinkDto
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 搜索直达目录：启动拉远端 → 写缓存 → 替换 [SearchDeepLinkCatalog] 生效表。
 * 失败则用上次缓存，再不行用 builtin。
 */
@Singleton
class SearchDeepLinkRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val apiService: ApiService,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    init {
        applyCachedOrBuiltin()
    }

    fun applyCachedOrBuiltin() {
        val cached = readCache()
        if (cached != null) {
            SearchDeepLinkCatalog.replaceEffective(cached)
        } else {
            SearchDeepLinkCatalog.resetToBuiltin()
        }
    }

    suspend fun sync(): Boolean {
        return try {
            val res = apiService.getSearchDeeplinks()
            if (!res.success || res.data.isEmpty()) {
                Log.w(TAG, "sync empty/fail success=${res.success} err=${res.error}")
                return false
            }
            val entries = res.data.mapNotNull { it.toEntry() }
            if (entries.isEmpty()) return false
            writeCache(entries, res.updated_at)
            SearchDeepLinkCatalog.replaceEffective(entries)
            Log.i(TAG, "synced ${entries.size} entries updated_at=${res.updated_at}")
            true
        } catch (e: Exception) {
            Log.w(TAG, "sync failed: ${e.message}")
            false
        }
    }

    /**
     * 门口是否出「搜索直达」：
     * 目录 prefer ∧ 用户未关 ∧（可选）本机可 resolve 由调用方再判。
     */
    fun catalogAllowsSearchFace(packageName: String): Boolean =
        SearchDeepLinkCatalog.preferSearchLanding(packageName)

    private fun readCache(): List<SearchDeepLinkEntry>? {
        val raw = prefs.getString(KEY_JSON, null) ?: return null
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    add(
                        SearchDeepLinkEntry(
                            id = o.optString("id"),
                            displayName = o.optString("displayName"),
                            packageName = o.optString("packageName"),
                            category = o.optString("category", "内容"),
                            schemeTemplate = o.optString("schemeTemplate"),
                            supportsKeyword = o.optBoolean("supportsKeyword", true),
                            preferSearchLanding = o.optBoolean("preferSearchLanding", true),
                            isPrimary = o.optBoolean("isPrimary", false),
                            uriKind = o.optString("uriKind", SearchDeepLinkEntry.URI_KIND_Q)
                                .ifBlank { SearchDeepLinkEntry.URI_KIND_Q },
                            note = o.optString("note").takeIf { it.isNotBlank() },
                            sortOrder = o.optInt("sortOrder", 0),
                        )
                    )
                }
            }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun writeCache(entries: List<SearchDeepLinkEntry>, updatedAt: String?) {
        val arr = JSONArray()
        entries.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id)
                    .put("displayName", e.displayName)
                    .put("packageName", e.packageName)
                    .put("category", e.category)
                    .put("schemeTemplate", e.schemeTemplate)
                    .put("supportsKeyword", e.supportsKeyword)
                    .put("preferSearchLanding", e.preferSearchLanding)
                    .put("isPrimary", e.isPrimary)
                    .put("uriKind", e.uriKind)
                    .put("note", e.note ?: "")
                    .put("sortOrder", e.sortOrder)
            )
        }
        prefs.edit()
            .putString(KEY_JSON, arr.toString())
            .putString(KEY_UPDATED, updatedAt.orEmpty())
            .apply()
    }

    private fun SearchDeepLinkDto.toEntry(): SearchDeepLinkEntry? {
        val id = id.trim()
        val pkg = package_name.trim()
        val scheme = scheme_template.trim()
        if (id.isEmpty() || pkg.isEmpty() || scheme.isEmpty()) return null
        val kind = when (uri_kind.trim().lowercase()) {
            SearchDeepLinkEntry.URI_KIND_JD -> SearchDeepLinkEntry.URI_KIND_JD
            SearchDeepLinkEntry.URI_KIND_SMZDM -> SearchDeepLinkEntry.URI_KIND_SMZDM
            else -> SearchDeepLinkEntry.URI_KIND_Q
        }
        return SearchDeepLinkEntry(
            id = id,
            displayName = display_name.ifBlank { id },
            packageName = pkg,
            category = category.ifBlank { "内容" },
            schemeTemplate = scheme,
            supportsKeyword = supports_keyword,
            preferSearchLanding = prefer_search_landing,
            isPrimary = is_primary,
            uriKind = kind,
            note = note.takeIf { it.isNotBlank() },
            sortOrder = sort_order,
        )
    }

    companion object {
        private const val TAG = "SearchDeepLinkRepo"
        private const val PREFS = "ha_search_deeplinks"
        private const val KEY_JSON = "catalog_json"
        private const val KEY_UPDATED = "updated_at"
    }
}
