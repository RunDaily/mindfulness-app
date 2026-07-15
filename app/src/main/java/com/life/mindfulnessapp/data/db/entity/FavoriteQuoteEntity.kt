package com.life.mindfulnessapp.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户收藏的名言（本地持久化）。
 * 以 content 字段作为主键，相同内容不会重复收藏。
 */
@Entity(tableName = "favorite_quotes")
data class FavoriteQuoteEntity(
    /** 名言正文（去掉引号后的原始内容），同时作为主键去重 */
    @PrimaryKey
    val content: String,
    /** 作者（已含 "—— " 前缀格式，可为空字符串） */
    val author: String = "",
    /** 收藏时间（毫秒时间戳），用于列表倒序排列 */
    val savedAt: Long = System.currentTimeMillis()
)
