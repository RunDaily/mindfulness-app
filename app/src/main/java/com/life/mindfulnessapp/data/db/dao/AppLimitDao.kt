package com.life.mindfulnessapp.data.db.dao

import androidx.room.*
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface AppLimitDao {

    @Query("SELECT * FROM app_limits ORDER BY sortOrder ASC, createdAt ASC, packageName ASC")
    fun getAllAppLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits ORDER BY sortOrder ASC, createdAt ASC, packageName ASC")
    suspend fun getAllLimitsOnce(): List<AppLimitEntity>

    @Query("SELECT * FROM app_limits WHERE isEnabled = 1 ORDER BY sortOrder ASC, createdAt ASC, packageName ASC")
    fun getEnabledAppLimits(): Flow<List<AppLimitEntity>>

    @Query("SELECT * FROM app_limits WHERE packageName = :packageName LIMIT 1")
    suspend fun getAppLimit(packageName: String): AppLimitEntity?

    @Query("SELECT packageName FROM app_limits WHERE isEnabled = 1 ORDER BY sortOrder ASC, createdAt ASC, packageName ASC")
    suspend fun getEnabledPackageNames(): List<String>

    @Query("SELECT IFNULL(MAX(sortOrder), -1) FROM app_limits")
    suspend fun getMaxSortOrder(): Int

    @Query("UPDATE app_limits SET sortOrder = :sortOrder WHERE packageName = :packageName")
    suspend fun updateSortOrder(packageName: String, sortOrder: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(appLimit: AppLimitEntity)

    @Delete
    suspend fun delete(appLimit: AppLimitEntity)

    @Query("DELETE FROM app_limits WHERE packageName = :packageName")
    suspend fun deleteByPackageName(packageName: String)

    @Query("UPDATE app_limits SET isEnabled = :enabled WHERE packageName = :packageName")
    suspend fun setEnabled(packageName: String, enabled: Boolean)

    /** 产品已下线每周上限：清空历史配置，避免继续拦截 */
    @Query("UPDATE app_limits SET weeklyLimitMinutes = 0 WHERE weeklyLimitMinutes != 0")
    suspend fun clearAllWeeklyLimits()

    /**
     * 更新限制时长，同时记录修改次数和日期（用于防止无限改限制）
     */
    @Query("""
        UPDATE app_limits 
        SET dailyLimitMinutes = :dailyLimitMinutes,
            weeklyLimitMinutes = :weeklyLimitMinutes,
            dailyModifyCount = :dailyModifyCount,
            lastModifiedDate = :lastModifiedDate
        WHERE packageName = :packageName
    """)
    suspend fun updateLimitWithModifyCount(
        packageName: String,
        dailyLimitMinutes: Int,
        weeklyLimitMinutes: Int,
        dailyModifyCount: Int,
        lastModifiedDate: String
    )

}
