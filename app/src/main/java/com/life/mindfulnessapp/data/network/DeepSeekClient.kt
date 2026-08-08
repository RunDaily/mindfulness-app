package com.life.mindfulnessapp.data.network

import com.life.mindfulnessapp.BuildConfig
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object DeepSeekClient {

    val isConfigured: Boolean
        get() = BuildConfig.DEEPSEEK_API_KEY.isNotBlank()

    private val authInterceptor = Interceptor { chain ->
        val key = BuildConfig.DEEPSEEK_API_KEY
        val req = if (key.isBlank()) {
            chain.request()
        } else {
            chain.request().newBuilder()
                .header("Authorization", "Bearer $key")
                .header("Content-Type", "application/json")
                .build()
        }
        chain.proceed(req)
    }

    private val logging = HttpLoggingInterceptor().apply {
        // 不打印 BODY，避免意图文案与鉴权细节进日志
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
        else HttpLoggingInterceptor.Level.NONE
    }

    private val okHttp = OkHttpClient.Builder()
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .build()

    val api: DeepSeekApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.DEEPSEEK_BASE_URL)
            .client(okHttp)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(DeepSeekApi::class.java)
    }
}
