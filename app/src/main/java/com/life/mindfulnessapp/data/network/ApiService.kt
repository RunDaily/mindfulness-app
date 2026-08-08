package com.life.mindfulnessapp.data.network

import retrofit2.http.GET
import retrofit2.http.Query

/**
 * 心锚后端 API。
 * 仅保留无需登录的公开接口（拦截名言）；使用数据全部留在本机。
 */
interface ApiService {

    /** 随机获取 count 条拦截名言 */
    @GET("api/heartanchor/quotes/random")
    suspend fun getRandomQuotes(
        @Query("count") count: Int = 5
    ): QuoteRandomResponse

    /** 批量拉取名言用于本地缓存 */
    @GET("api/heartanchor/quotes")
    suspend fun getQuotes(
        @Query("limit") limit: Int = 50,
        @Query("offset") offset: Int = 0
    ): QuoteListResponse
}
