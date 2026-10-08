package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.db.dao.AppLimitDao
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppLimitRepository @Inject constructor(
    private val dao: AppLimitDao
) {
    private val dateFormat = SimpleDateFormat("yyyyMMdd", Locale.getDefault())

    fun getAllAppLimits(): Flow<List<AppLimitEntity>> = dao.getAllAppLimits()

    fun getEnabledAppLimits(): Flow<List<AppLimitEntity>> = dao.getEnabledAppLimits()

    suspend fun getAppLimit(packageName: String): AppLimitEntity? = dao.getAppLimit(packageName)

    /** 一次性获取所有 limit（suspend 版，用于非 Flow 场景）*/
    suspend fun getAllLimitsOnce(): List<AppLimitEntity> = dao.getAllLimitsOnce()

    /** 别名，与 ViewModel 使用一致 */
    suspend fun getLimit(packageName: String): AppLimitEntity? = dao.getAppLimit(packageName)

    suspend fun getEnabledPackageNames(): List<String> = dao.getEnabledPackageNames()

    suspend fun saveAppLimit(appLimit: AppLimitEntity) =
        dao.insertOrUpdate(appLimit.copy(weeklyLimitMinutes = 0))

    /** 新加入监控时分配的下一个 sortOrder */
    suspend fun nextSortOrder(): Int = dao.getMaxSortOrder() + 1

    /**
     * 按给定包名顺序重写 [AppLimitEntity.sortOrder]（0..n-1）。
     * 首页坑位与管理列表共用此顺序。
     */
    suspend fun updateSortOrders(orderedPackageNames: List<String>) {
        orderedPackageNames.forEachIndexed { index, packageName ->
            dao.updateSortOrder(packageName, index)
        }
    }

    suspend fun deleteAppLimit(packageName: String) = dao.deleteByPackageName(packageName)

    suspend fun setEnabled(packageName: String, enabled: Boolean) =
        dao.setEnabled(packageName, enabled)

    /** 一次性清掉历史每周上限（功能已下线） */
    suspend fun clearAllWeeklyLimits() = dao.clearAllWeeklyLimits()

    /**
     * 获取今日剩余可修改次数（跨天自动重置）
     * @return 剩余次数，0 表示今日已用完
     */
    suspend fun getRemainingModifyCount(packageName: String): Int {
        val entity = dao.getAppLimit(packageName) ?: return 0
        val todayStr = dateFormat.format(Date())
        val usedCount = if (entity.lastModifiedDate == todayStr) entity.dailyModifyCount else 0
        return (AppLimitEntity.MAX_DAILY_MODIFY_COUNT - usedCount).coerceAtLeast(0)
    }

    /**
     * 重新设定 App 限制时长（消耗一次今日修改机会）
     * @param packageName 应用包名
     * @param newDailyLimitMinutes 新的每日限制（分钟）
     * @param newWeeklyLimitMinutes 已废弃，始终写入 0
     * @return true 表示修改成功，false 表示今日次数已用完
     */
    suspend fun resetAppLimit(
        packageName: String,
        newDailyLimitMinutes: Int,
        newWeeklyLimitMinutes: Int = 0
    ): Boolean {
        val entity = dao.getAppLimit(packageName) ?: return false
        val todayStr = dateFormat.format(Date())
        val usedCount = if (entity.lastModifiedDate == todayStr) entity.dailyModifyCount else 0

        if (usedCount >= AppLimitEntity.MAX_DAILY_MODIFY_COUNT) return false

        dao.updateLimitWithModifyCount(
            packageName = packageName,
            dailyLimitMinutes = newDailyLimitMinutes,
            weeklyLimitMinutes = 0,
            dailyModifyCount = usedCount + 1,
            lastModifiedDate = todayStr
        )
        return true
    }

    /**
     * 写下意图 · 快捷标签（存于 quickIntentsJson）。
     * 空库时用 [IntentGateProfiles] 预设冷启动；再空才退到深链目录默认。
     */
    suspend fun getCommonIntents(packageName: String): List<com.life.mindfulnessapp.domain.model.CommonIntentItem> {
        val entity = dao.getAppLimit(packageName) ?: return emptyList()
        val decoded = com.life.mindfulnessapp.domain.model.CommonIntentsCodec
            .decode(entity.quickIntentsJson)
            .filterNot {
                com.life.mindfulnessapp.domain.model.BrowseCasualIntent.isBrowseLike(it.label) ||
                    com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
                        .isRetiredForPackage(packageName, it.label)
            }
        if (decoded.isNotEmpty()) {
            val (enriched, deepLinkChanged) = com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
                .enrichCommonIntents(packageName, decoded)
            // 清理过期文案 / 补绑定 deepLinkId 后写回
            val raw = com.life.mindfulnessapp.domain.model.CommonIntentsCodec.decode(entity.quickIntentsJson)
            if (raw.size != decoded.size || deepLinkChanged) {
                dao.insertOrUpdate(
                    entity.copy(
                        quickIntentsJson =
                            com.life.mindfulnessapp.domain.model.CommonIntentsCodec.encode(enriched)
                    )
                )
            }
            return enriched
        }
        val seeded = com.life.mindfulnessapp.domain.model.IntentGateProfiles
            .presetCommonIntents(packageName)
            .ifEmpty {
                com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
                    .defaultCommonIntents(packageName)
            }
        if (seeded.isNotEmpty()) {
            dao.insertOrUpdate(
                entity.copy(
                    quickIntentsJson =
                        com.life.mindfulnessapp.domain.model.CommonIntentsCodec.encode(seeded)
                )
            )
        }
        return seeded
    }

    suspend fun setCommonIntents(
        packageName: String,
        intents: List<com.life.mindfulnessapp.domain.model.CommonIntentItem>
    ): Boolean {
        val entity = dao.getAppLimit(packageName) ?: return false
        val cleaned = intents.filterNot {
            com.life.mindfulnessapp.domain.model.BrowseCasualIntent.isBrowseLike(it.label) ||
                com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
                    .isRetiredForPackage(packageName, it.label)
        }
        val encoded = com.life.mindfulnessapp.domain.model.CommonIntentsCodec.encode(cleaned)
        dao.insertOrUpdate(entity.copy(quickIntentsJson = encoded))
        return true
    }

    /** 恢复为该 App 的快捷标签预设（覆盖用户改动）。 */
    suspend fun resetCommonIntentsToPresets(packageName: String): Boolean {
        val presets = com.life.mindfulnessapp.domain.model.IntentGateProfiles
            .presetCommonIntents(packageName)
            .ifEmpty {
                com.life.mindfulnessapp.data.deeplink.AppDeepLinkCatalog
                    .defaultCommonIntents(packageName)
            }
        return setCommonIntents(packageName, presets)
    }

    suspend fun getBrowseCasualPolicy(packageName: String): com.life.mindfulnessapp.domain.model.BrowseCasualPolicy {
        val entity = dao.getAppLimit(packageName)
            ?: return com.life.mindfulnessapp.domain.model.BrowseCasualPolicy.default()
        val policy = com.life.mindfulnessapp.domain.model.BrowseCasualPolicyCodec.decode(entity.browseCasualJson)
        return clampBrowseToDaily(policy, entity)
    }

    suspend fun setBrowseCasualPolicy(
        packageName: String,
        policy: com.life.mindfulnessapp.domain.model.BrowseCasualPolicy
    ): Boolean {
        val entity = dao.getAppLimit(packageName) ?: return false
        val capped = clampBrowseToDaily(policy, entity)
        dao.insertOrUpdate(
            entity.copy(
                browseCasualJson = com.life.mindfulnessapp.domain.model.BrowseCasualPolicyCodec.encode(capped)
            )
        )
        return true
    }

    /** 随意浏览限额不得高于日总限额（0=不限则不受此约束）。 */
    private fun clampBrowseToDaily(
        policy: com.life.mindfulnessapp.domain.model.BrowseCasualPolicy,
        entity: com.life.mindfulnessapp.data.db.entity.AppLimitEntity
    ): com.life.mindfulnessapp.domain.model.BrowseCasualPolicy {
        val b = policy.dailyLimitMinutes
        if (b <= 0) return policy
        if (!entity.timeLimitEnabled) return policy
        val total = entity.dailyLimitMinutes
        if (total <= 0 || b <= total) return policy
        return policy.copy(dailyLimitMinutes = total)
    }
}
