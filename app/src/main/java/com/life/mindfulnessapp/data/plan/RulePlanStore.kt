package com.life.mindfulnessapp.data.plan

import android.content.Context
import com.life.mindfulnessapp.domain.model.RuleGoal
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/** 目标、门口元数据、依据。限额本体仍写在 app_limits。 */
@Singleton
class RulePlanStore @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun goal(): RuleGoal {
        val raw = prefs.getString(KEY_GOAL, null) ?: return RuleGoal()
        return runCatching {
            val o = JSONObject(raw)
            val label = o.optString("label", "")
            val legacyObserve = label == "先观察"
            val updatedAt = o.optLong("updatedAt", 0L)
            val confirmed = when {
                o.has("confirmed") -> o.optBoolean("confirmed", false)
                else -> label.isNotBlank() && !legacyObserve && updatedAt > 0L
            }
            RuleGoal(
                label = if (legacyObserve) "" else label,
                intensity = o.optString("intensity", "mid").ifBlank { "mid" },
                scope = o.optString("scope", ""),
                confirmed = confirmed && label.isNotBlank() && !legacyObserve,
                updatedAt = updatedAt
            )
        }.getOrDefault(RuleGoal())
    }

    fun saveGoal(goal: RuleGoal) {
        val o = JSONObject()
            .put("label", goal.label)
            .put("intensity", goal.intensity)
            .put("scope", goal.scope)
            .put("confirmed", goal.confirmed)
            .put("updatedAt", goal.updatedAt)
        prefs.edit().putString(KEY_GOAL, o.toString()).apply()
    }

    fun rationale(packageName: String): String = meta(packageName).optString("why", "")

    /** 方案侧：是否允许搜索直达。缺省 true；只能关不能强开无链 App。 */
    fun searchDirectEnabled(packageName: String): Boolean {
        val m = meta(packageName)
        if (!m.has("searchDirect")) return true
        return m.optBoolean("searchDirect", true)
    }

    fun saveMeta(
        packageName: String,
        rationale: String,
        searchDirectEnabled: Boolean = true,
    ) {
        val all = allMeta()
        val o = JSONObject()
            .put("why", rationale.take(80))
            .put("searchDirect", searchDirectEnabled)
        all.put(packageName, o)
        prefs.edit().putString(KEY_META, all.toString()).apply()
    }

    fun requestEdit(packageName: String) {
        prefs.edit().putString(KEY_EDIT, packageName).apply()
    }

    fun consumeEdit(): String? {
        val pkg = prefs.getString(KEY_EDIT, null)?.takeIf { it.isNotBlank() } ?: return null
        prefs.edit().remove(KEY_EDIT).apply()
        return pkg
    }

    fun clearMeta(packageName: String) {
        val all = allMeta()
        all.remove(packageName)
        prefs.edit().putString(KEY_META, all.toString()).apply()
    }

    private fun meta(packageName: String): JSONObject =
        allMeta().optJSONObject(packageName) ?: JSONObject()

    private fun allMeta(): JSONObject {
        val raw = prefs.getString(KEY_META, null) ?: return JSONObject()
        return runCatching { JSONObject(raw) }.getOrDefault(JSONObject())
    }

    companion object {
        private const val PREFS = "rule_plan"
        private const val KEY_GOAL = "goal"
        private const val KEY_META = "meta"
        private const val KEY_EDIT = "pending_edit"
    }
}
