package com.life.mindfulnessapp.data.db.dao

import androidx.room.*
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LimitResetDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: LimitResetEntity): Long

    /** 查询指定时间段内的所有重设记录（按时间正序）*/
    @Query("""
        SELECT * FROM limit_resets
        WHERE resetTime >= :startMs AND resetTime < :endMs
        ORDER BY resetTime ASC
    """)
    fun getResetsByPeriod(startMs: Long, endMs: Long): Flow<List<LimitResetEntity>>

    /** 一次性查询指定时间段内的所有重设记录 */
    @Query("""
        SELECT * FROM limit_resets
        WHERE resetTime >= :startMs AND resetTime < :endMs
        ORDER BY resetTime ASC
    """)
    suspend fun getResetsByPeriodOnce(startMs: Long, endMs: Long): List<LimitResetEntity>

    @Query("DELETE FROM limit_resets WHERE resetTime < :beforeMs")
    suspend fun deleteOldRecords(beforeMs: Long)
}
