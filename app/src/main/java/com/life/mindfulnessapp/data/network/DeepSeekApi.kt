package com.life.mindfulnessapp.data.network

import com.google.gson.annotations.SerializedName
import retrofit2.http.Body
import retrofit2.http.POST

/** DeepSeek OpenAI 兼容 Chat Completions */
interface DeepSeekApi {

    @POST("chat/completions")
    suspend fun chatCompletions(
        @Body body: DeepSeekChatRequest
    ): DeepSeekChatResponse
}

data class DeepSeekChatRequest(
    val model: String,
    val messages: List<DeepSeekMessage>,
    val temperature: Double = 0.1,
    @SerializedName("max_tokens") val maxTokens: Int = 32
)

data class DeepSeekMessage(
    val role: String,
    val content: String
)

data class DeepSeekChatResponse(
    val choices: List<DeepSeekChoice>? = null
)

data class DeepSeekChoice(
    val message: DeepSeekMessage? = null
)
