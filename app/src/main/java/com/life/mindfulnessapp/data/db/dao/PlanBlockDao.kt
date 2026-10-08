package com.life.mindfulnessapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.life.mindfulnessapp.data.db.entity.PlanBlockEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlanBlockDao {

    /** 按时段先后（开始分钟 → 结束分钟）；跨午夜段自然靠后。 */
    @Query("SELECT * FROM plan_blocks ORDER BY startMinute ASC, endMinute ASC, createdAt ASC")
    fun observeAll(): Flow<List<PlanBlockEntity>>

    @Query("SELECT * FROM plan_blocks ORDER BY startMinute ASC, endMinute ASC, createdAt ASC")
    suspend fun getAllOnce(): List<PlanBlockEntity>

    @Query("SELECT * FROM plan_blocks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PlanBlockEntity?

    @Query("SELECT IFNULL(MAX(sortOrder), -1) FROM plan_blocks")
    suspend fun getMaxSortOrder(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlanBlockEntity)

    @Query("DELETE FROM plan_blocks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE plan_blocks SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}
