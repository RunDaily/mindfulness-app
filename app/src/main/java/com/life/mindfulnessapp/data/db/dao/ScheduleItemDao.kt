package com.life.mindfulnessapp.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.life.mindfulnessapp.data.db.entity.ScheduleItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ScheduleItemDao {

    @Query(
        """
        SELECT * FROM schedule_items
        WHERE dayKey = :dayKey
        ORDER BY
            CASE WHEN startMinute IS NULL THEN 1 ELSE 0 END ASC,
            startMinute ASC,
            sortOrder ASC,
            createdAt ASC
        """
    )
    fun observeByDay(dayKey: String): Flow<List<ScheduleItemEntity>>

    @Query(
        """
        SELECT * FROM schedule_items
        WHERE dayKey = :dayKey
        ORDER BY
            CASE WHEN startMinute IS NULL THEN 1 ELSE 0 END ASC,
            startMinute ASC,
            sortOrder ASC,
            createdAt ASC
        """
    )
    suspend fun getByDay(dayKey: String): List<ScheduleItemEntity>

    @Query("SELECT * FROM schedule_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): ScheduleItemEntity?

    @Query("SELECT IFNULL(MAX(sortOrder), -1) FROM schedule_items WHERE dayKey = :dayKey")
    suspend fun getMaxSortOrder(dayKey: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: ScheduleItemEntity)

    @Query("DELETE FROM schedule_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE schedule_items SET done = :done WHERE id = :id")
    suspend fun setDone(id: String, done: Boolean)
}
