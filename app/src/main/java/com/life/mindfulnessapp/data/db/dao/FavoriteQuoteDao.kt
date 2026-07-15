package com.life.mindfulnessapp.data.db.dao

import androidx.room.*
import com.life.mindfulnessapp.data.db.entity.FavoriteQuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteQuoteDao {

    /** 收藏一条名言（若已存在则忽略） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(quote: FavoriteQuoteEntity)

    /** 取消收藏 */
    @Delete
    suspend fun delete(quote: FavoriteQuoteEntity)

    /** 根据内容取消收藏 */
    @Query("DELETE FROM favorite_quotes WHERE content = :content")
    suspend fun deleteByContent(content: String)

    /** 查询全部收藏（按收藏时间倒序） */
    @Query("SELECT * FROM favorite_quotes ORDER BY savedAt DESC")
    fun getAllFavorites(): Flow<List<FavoriteQuoteEntity>>

    /** 检查某条名言是否已被收藏（用于切换心形按钮状态） */
    @Query("SELECT EXISTS(SELECT 1 FROM favorite_quotes WHERE content = :content LIMIT 1)")
    fun isFavorite(content: String): Flow<Boolean>

    /** 获取收藏总数 */
    @Query("SELECT COUNT(*) FROM favorite_quotes")
    fun getFavoriteCount(): Flow<Int>
}
