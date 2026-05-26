package com.life.mindfulnessapp.di

import com.life.mindfulnessapp.data.network.ApiService
import com.life.mindfulnessapp.data.network.RetrofitClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideApiService(): ApiService = RetrofitClient.apiService
}
