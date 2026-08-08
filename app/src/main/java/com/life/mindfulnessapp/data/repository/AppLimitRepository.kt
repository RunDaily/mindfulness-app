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
}
