package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.db.dao.AppTotalUsage
import com.life.mindfulnessapp.data.db.dao.HourlyUsage
import com.life.mindfulnessapp.data.db.dao.UsageRecordDao
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity
import kotlinx.coroutines.flow.Flow
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRecordRepository @Inject constructor(
    private val dao: UsageRecordDao
) {
    suspend fun insertRecord(record: UsageRecordEntity): Long = dao.insert(record)

    suspend fun updateRecord(record: UsageRecordEntity) = dao.update(record)

    suspend fun getRecordById(id: Long): UsageRecordEntity? = dao.getRecordById(id)

    /** 仅更新指定记录的备注，传入 null 表示清空备注 */
    suspend fun updateNote(id: Long, note: String?) = dao.updateNote(id, note)

    fun getRecentRecords(limit: Int = 50): Flow<List<UsageRecordEntity>> =
        dao.getRecentRecords(limit)

    fun getDayRecords(dayStartMs: Long, dayEndMs: Long): Flow<List<UsageRecordEntity>> =
        dao.getDayRecords(dayStartMs, dayEndMs)

    /** 今日有 purpose 记录的 Flow，用于首页展示有意识使用次数 */
    fun getDayMindfulRecords(dayStartMs: Long, dayEndMs: Long): Flow<List<UsageRecordEntity>> =
        dao.getDayMindfulRecords(dayStartMs, dayEndMs)

    fun getWeekRecords(weekStartMs: Long, weekEndMs: Long): Flow<List<UsageRecordEntity>> =
        dao.getWeekRecords(weekStartMs, weekEndMs)

    suspend fun getDailyUsageSeconds(packageName: String, dateMs: Long = System.currentTimeMillis()): Long {
        val (start, end) = getDayRange(dateMs)
        return dao.getDailyUsageSeconds(packageName, start, end)
    }

    suspend fun getWeeklyUsageSeconds(packageName: String, dateMs: Long = System.currentTimeMillis()): Long {
        val (start, end) = getWeekRange(dateMs)
        return dao.getWeeklyUsageSeconds(packageName, start, end)
    }

    /** 删除 30 天前的记录 */
    suspend fun cleanOldRecords() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        dao.deleteOldRecords(thirtyDaysAgo)
    }

    /** 获取 sinceMs 之后的所有已完成记录（用于云端同步）*/
    suspend fun getAllCompletedRecordsSince(sinceMs: Long): List<UsageRecordEntity> =
        dao.getAllCompletedRecordsSince(sinceMs)

    // ── 深度洞察查询 ──────────────────────────────────────────────────────────

    /** 某 App 在指定时段内的打开次数 */
    suspend fun getOpenCount(packageName: String, startMs: Long, endMs: Long): Int =
        dao.getOpenCount(packageName, startMs, endMs)

    /** 某 App 在指定时段内的最长单次使用（秒）*/
    suspend fun getLongestSession(packageName: String, startMs: Long, endMs: Long): Long =
        dao.getLongestSession(packageName, startMs, endMs)

    /** 某 App 在指定时段内按小时的使用分布 */
    suspend fun getHourlyDistribution(packageName: String, startMs: Long, endMs: Long): List<HourlyUsage> =
        dao.getHourlyDistribution(packageName, startMs, endMs)

    /** 所有 App 在指定时段内按包名汇总时长，用于占比分析 */
    suspend fun getAppTotalByPeriod(startMs: Long, endMs: Long): List<AppTotalUsage> =
        dao.getAppTotalByPeriod(startMs, endMs)

    /** 某 App 在指定时段内有 purpose 的打开次数（即填写了使用目的的次数）*/
    suspend fun getMindfulOpenCount(packageName: String, startMs: Long, endMs: Long): Int =
        dao.getMindfulOpenCount(packageName, startMs, endMs)

    /** 某 App 在指定时段内的全部记录 Flow */
    fun getAppRecordsByPeriod(packageName: String, startMs: Long, endMs: Long): Flow<List<UsageRecordEntity>> =
        dao.getAppRecordsByPeriod(packageName, startMs, endMs)

    /** 全局（所有 App）在指定时段内的按小时使用分布 */
    suspend fun getGlobalHourlyDistribution(startMs: Long, endMs: Long): List<HourlyUsage> =
        dao.getGlobalHourlyDistribution(startMs, endMs)

    /**
     * 获取某一天内指定 App 的所有已完成记录（含 purpose），按开始时间倒序。
     * 用于拦截页展示今日历次使用记录。
     */
    suspend fun getDayRecordsForApp(
        packageName: String,
        dateMs: Long = System.currentTimeMillis()
    ): List<UsageRecordEntity> {
        val (start, end) = getDayRange(dateMs)
        return dao.getDayRecordsForApp(packageName, start, end)
    }

    companion object {
        /** 获取某天的开始和结束时间戳（毫秒） */
        fun getDayRange(dateMs: Long): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = dateMs
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
            return start to cal.timeInMillis
        }

        /** 获取本周（周一到周日）的开始和结束时间戳 */
        fun getWeekRange(dateMs: Long): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = dateMs
                firstDayOfWeek = Calendar.MONDAY
                set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.WEEK_OF_YEAR, 1)
            return start to cal.timeInMillis
        }

        /** 获取上周的开始和结束时间戳 */
        fun getLastWeekRange(dateMs: Long): Pair<Long, Long> {
            val (thisWeekStart, _) = getWeekRange(dateMs)
            val lastWeekEnd = thisWeekStart
            val lastWeekStart = thisWeekStart - 7L * 24 * 60 * 60 * 1000
            return lastWeekStart to lastWeekEnd
        }

        /** 获取昨天的开始和结束时间戳 */
        fun getYesterdayRange(dateMs: Long): Pair<Long, Long> {
            val (todayStart, _) = getDayRange(dateMs)
            val yesterdayStart = todayStart - 24 * 60 * 60 * 1000L
            return yesterdayStart to todayStart
        }
    }
}
