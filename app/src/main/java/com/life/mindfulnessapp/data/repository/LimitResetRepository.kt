package com.life.mindfulnessapp.data.repository

import com.life.mindfulnessapp.data.db.dao.LimitResetDao
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import com.life.mindfulnessapp.data.repository.UsageRecordRepository.Companion.getDayRange
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LimitResetRepository @Inject constructor(
    private val dao: LimitResetDao
) {
    suspend fun insert(entity: LimitResetEntity): Long = dao.insert(entity)

    /** 今日所有重设记录（Flow，实时更新）*/
    fun getTodayResets(): Flow<List<LimitResetEntity>> {
        val (start, end) = getDayRange(System.currentTimeMillis())
        return dao.getResetsByPeriod(start, end)
    }

    /** 今日所有重设记录（一次性查询）*/
    suspend fun getTodayResetsOnce(): List<LimitResetEntity> {
        val (start, end) = getDayRange(System.currentTimeMillis())
        return dao.getResetsByPeriodOnce(start, end)
    }

    /** 指定时段内的重设记录 */
    fun getResetsByPeriod(startMs: Long, endMs: Long): Flow<List<LimitResetEntity>> =
        dao.getResetsByPeriod(startMs, endMs)

    /** 清理 30 天前的记录 */
    suspend fun cleanOldRecords() {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        dao.deleteOldRecords(thirtyDaysAgo)
    }
}
