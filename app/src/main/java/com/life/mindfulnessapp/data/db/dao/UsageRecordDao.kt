package com.life.mindfulnessapp.data.db.dao

import androidx.room.*
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UsageRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: UsageRecordEntity): Long

    @Update
    suspend fun update(record: UsageRecordEntity)

    @Query("SELECT * FROM usage_records WHERE packageName = :packageName ORDER BY startTime DESC")
    fun getRecordsByApp(packageName: String): Flow<List<UsageRecordEntity>>

    @Query("SELECT * FROM usage_records ORDER BY startTime DESC LIMIT :limit")
    fun getRecentRecords(limit: Int = 50): Flow<List<UsageRecordEntity>>

    /** 查询指定 App 在某一天的总使用时长（秒）*/
    @Query("""
        SELECT COALESCE(SUM(durationSeconds), 0) 
        FROM usage_records 
        WHERE packageName = :packageName 
        AND startTime >= :dayStartMs 
        AND startTime < :dayEndMs
        AND endTime > 0
    """)
    suspend fun getDailyUsageSeconds(
        packageName: String,
        dayStartMs: Long,
        dayEndMs: Long
    ): Long

    /** 查询指定 App 在某一周的总使用时长（秒）*/
    @Query("""
        SELECT COALESCE(SUM(durationSeconds), 0) 
        FROM usage_records 
        WHERE packageName = :packageName 
        AND startTime >= :weekStartMs 
        AND startTime < :weekEndMs
        AND endTime > 0
    """)
    suspend fun getWeeklyUsageSeconds(
        packageName: String,
        weekStartMs: Long,
        weekEndMs: Long
    ): Long

    /** 查询某天内所有 App 的使用记录（用于统计图表）*/
    @Query("""
        SELECT * FROM usage_records 
        WHERE startTime >= :dayStartMs 
        AND startTime < :dayEndMs
        AND endTime > 0
        ORDER BY startTime DESC
    """)
    fun getDayRecords(dayStartMs: Long, dayEndMs: Long): Flow<List<UsageRecordEntity>>

    /** 查询某周内所有 App 的使用记录 */
    @Query("""
        SELECT * FROM usage_records 
        WHERE startTime >= :weekStartMs 
        AND startTime < :weekEndMs
        AND endTime > 0
        ORDER BY startTime DESC
    """)
    fun getWeekRecords(weekStartMs: Long, weekEndMs: Long): Flow<List<UsageRecordEntity>>

    @Query("DELETE FROM usage_records WHERE startTime < :beforeMs")
    suspend fun deleteOldRecords(beforeMs: Long)

    /** 清除全部使用记录（用于「清除本地数据」功能）*/
    @Query("DELETE FROM usage_records")
    suspend fun deleteAllRecords()

    @Query("SELECT * FROM usage_records WHERE id = :id LIMIT 1")
    suspend fun getRecordById(id: Long): UsageRecordEntity?

    /** 仅更新单条记录的备注字段，避免覆盖其他字段 */
    @Query("UPDATE usage_records SET note = :note WHERE id = :id")
    suspend fun updateNote(id: Long, note: String?)

    /** 仅更新单条记录的效果评分字段 */
    @Query("UPDATE usage_records SET effectScore = :score WHERE id = :id")
    suspend fun updateEffectScore(id: Long, score: Int?)

    /** 同时更新备注和效果评分（结束弹框提交时调用）*/
    @Query("UPDATE usage_records SET note = :note, effectScore = :score WHERE id = :id")
    suspend fun updateNoteAndScore(id: Long, note: String?, score: Int?)

    /**
     * 查询某一天内有 purpose 记录的条目，用于「今日有意识使用回顾」
     */
    @Query("""
        SELECT * FROM usage_records 
        WHERE startTime >= :dayStartMs 
        AND startTime < :dayEndMs
        AND endTime > 0
        AND purpose IS NOT NULL
        ORDER BY startTime DESC
    """)
    fun getDayMindfulRecords(dayStartMs: Long, dayEndMs: Long): Flow<List<UsageRecordEntity>>

    // ── 新增：深度洞察所需查询 ──────────────────────────────────────────────────

    /**
     * 查询某时间段内指定 App 的打开次数（即记录条数）
     */
    @Query("""
        SELECT COUNT(*) FROM usage_records
        WHERE packageName = :packageName
        AND startTime >= :startMs
        AND startTime < :endMs
        AND endTime > 0
    """)
    suspend fun getOpenCount(packageName: String, startMs: Long, endMs: Long): Int

    /**
     * 查询某时间段内指定 App 最长单次使用时长（秒）
     */
    @Query("""
        SELECT COALESCE(MAX(durationSeconds), 0) FROM usage_records
        WHERE packageName = :packageName
        AND startTime >= :startMs
        AND startTime < :endMs
        AND endTime > 0
    """)
    suspend fun getLongestSession(packageName: String, startMs: Long, endMs: Long): Long

    /**
     * 查询某时间段内指定 App 按小时的使用时长分布
     * 返回每行：hour (0-23), totalSeconds
     */
    @Query("""
        SELECT 
            CAST((startTime / 3600000) % 24 AS INTEGER) AS hour,
            SUM(durationSeconds) AS totalSeconds
        FROM usage_records
        WHERE packageName = :packageName
        AND startTime >= :startMs
        AND startTime < :endMs
        AND endTime > 0
        GROUP BY hour
        ORDER BY hour
    """)
    suspend fun getHourlyDistribution(
        packageName: String,
        startMs: Long,
        endMs: Long
    ): List<HourlyUsage>

    /**
     * 查询某时间段内所有受监控 App 总时长，按 packageName 分组
     * 用于饼图/占比
     */
    @Query("""
        SELECT packageName, SUM(durationSeconds) AS totalSeconds
        FROM usage_records
        WHERE startTime >= :startMs
        AND startTime < :endMs
        AND endTime > 0
        GROUP BY packageName
        ORDER BY totalSeconds DESC
    """)
    suspend fun getAppTotalByPeriod(startMs: Long, endMs: Long): List<AppTotalUsage>

    /**
     * 查询某时间段内指定 App 有 purpose 的打开次数（即用户写了使用目的的次数）
     */
    @Query("""
        SELECT COUNT(*) FROM usage_records
        WHERE packageName = :packageName
        AND startTime >= :startMs
        AND startTime < :endMs
        AND endTime > 0
        AND purpose IS NOT NULL
    """)
    suspend fun getMindfulOpenCount(packageName: String, startMs: Long, endMs: Long): Int

    /**
     * 查询某时间段内指定 App 的全部记录（用于详情页）
     */
    @Query("""
        SELECT * FROM usage_records
        WHERE packageName = :packageName
        AND startTime >= :startMs
        AND startTime < :endMs
        AND endTime > 0
        ORDER BY startTime DESC
    """)
    fun getAppRecordsByPeriod(
        packageName: String,
        startMs: Long,
        endMs: Long
    ): Flow<List<UsageRecordEntity>>

    /**
     * 查询某时间段内所有 App 按小时的使用时长分布（全局热力图）
     */
    @Query("""
        SELECT 
            CAST((startTime / 3600000) % 24 AS INTEGER) AS hour,
            SUM(durationSeconds) AS totalSeconds
        FROM usage_records
        WHERE startTime >= :startMs
        AND startTime < :endMs
        AND endTime > 0
        GROUP BY hour
        ORDER BY hour
    """)
    suspend fun getGlobalHourlyDistribution(startMs: Long, endMs: Long): List<HourlyUsage>

    /**
     * 查询某一天内指定 App 的所有已完成记录（含 purpose），按开始时间倒序。
     * 用于拦截页展示今日使用记录列表。
     */
    @Query("""
        SELECT * FROM usage_records
        WHERE packageName = :packageName
        AND startTime >= :dayStartMs
        AND startTime < :dayEndMs
        AND endTime > 0
        ORDER BY startTime DESC
    """)
    suspend fun getDayRecordsForApp(
        packageName: String,
        dayStartMs: Long,
        dayEndMs: Long
    ): List<UsageRecordEntity>

    /**
     * 查询某天内「克制退出」的次数：
     * purpose 为空 + durationSeconds <= 20 秒，视为用户在拦截页选择了离开。
     */
    @Query("""
        SELECT COUNT(*) FROM usage_records
        WHERE startTime >= :dayStartMs
        AND startTime < :dayEndMs
        AND endTime > 0
        AND purpose IS NULL
        AND durationSeconds <= 20
    """)
    suspend fun getDayDismissCount(dayStartMs: Long, dayEndMs: Long): Int

    /**
     * 查询指定时间点之后的所有已完成记录（用于云端同步）。
     */
    @Query("""
        SELECT * FROM usage_records
        WHERE startTime >= :sinceMs
        AND endTime > 0
        ORDER BY startTime DESC
    """)
    suspend fun getAllCompletedRecordsSince(sinceMs: Long): List<UsageRecordEntity>

    /**
     * 查询某一天内指定 App 的所有已完成记录，按开始时间正序。
     * 用于详情页日历联动列表，显示某天的使用明细。
     */
    @Query("""
        SELECT * FROM usage_records
        WHERE packageName = :packageName
        AND startTime >= :dayStartMs
        AND startTime < :dayEndMs
        AND endTime > 0
        ORDER BY startTime ASC
    """)
    suspend fun getDayRecordsForAppAsc(
        packageName: String,
        dayStartMs: Long,
        dayEndMs: Long
    ): List<UsageRecordEntity>

    /**
     * 查询指定 App 在某时间段内每天的本地记录总时长（秒）。
     * 用于日历组件展示"规则内统计时长"（即本地拦截规则记录的时长，区别于系统 UsageStats）。
     * 返回结构：dateKey（yyyy-MM-dd）→ totalSeconds
     */
    @Query("""
        SELECT 
            date(startTime / 1000, 'unixepoch', 'localtime') AS dateKey,
            SUM(durationSeconds) AS totalSeconds
        FROM usage_records
        WHERE packageName = :packageName
        AND startTime >= :startMs
        AND startTime < :endMs
        AND endTime > 0
        GROUP BY dateKey
    """)
    suspend fun getDailyUsageByRange(
        packageName: String,
        startMs: Long,
        endMs: Long
    ): List<DailyUsageSummary>
}

/** 每日使用量摘要（Room 查询结果映射）*/
data class DailyUsageSummary(
    val dateKey: String,      // "yyyy-MM-dd"
    val totalSeconds: Long
)

/** 小时级使用量（Room 查询结果映射）*/
data class HourlyUsage(
    val hour: Int,
    val totalSeconds: Long
)

/** 单个 App 在某周期内的总时长（Room 查询结果映射）*/
data class AppTotalUsage(
    val packageName: String,
    val totalSeconds: Long
)
