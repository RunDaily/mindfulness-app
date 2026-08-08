package com.life.mindfulnessapp.di

import android.content.Context
import androidx.room.Room
import com.life.mindfulnessapp.data.db.AppDatabase
import com.life.mindfulnessapp.data.db.dao.AppLimitDao
import com.life.mindfulnessapp.data.db.dao.FavoriteQuoteDao
import com.life.mindfulnessapp.data.db.dao.LimitResetDao
import com.life.mindfulnessapp.data.db.dao.UsageRecordDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "mindfulness_db"
        )
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6,
                AppDatabase.MIGRATION_6_7,
                AppDatabase.MIGRATION_7_8,
                AppDatabase.MIGRATION_6_8,
                AppDatabase.MIGRATION_8_9,
                AppDatabase.MIGRATION_9_10,
                AppDatabase.MIGRATION_10_11,
                AppDatabase.MIGRATION_11_12,
                AppDatabase.MIGRATION_12_13,
                AppDatabase.MIGRATION_13_14,
                AppDatabase.MIGRATION_14_15,
                AppDatabase.MIGRATION_15_16,
                AppDatabase.MIGRATION_16_17,
                AppDatabase.MIGRATION_17_18,
                AppDatabase.MIGRATION_18_19,
                AppDatabase.MIGRATION_19_20,
                AppDatabase.MIGRATION_20_21,
                AppDatabase.MIGRATION_21_22,
                AppDatabase.MIGRATION_22_23
            )
            .build()
    }

    @Provides
    fun provideAppLimitDao(db: AppDatabase): AppLimitDao = db.appLimitDao()

    @Provides
    fun provideUsageRecordDao(db: AppDatabase): UsageRecordDao = db.usageRecordDao()

    @Provides
    fun provideLimitResetDao(db: AppDatabase): LimitResetDao = db.limitResetDao()

    @Provides
    fun provideFavoriteQuoteDao(db: AppDatabase): FavoriteQuoteDao = db.favoriteQuoteDao()
}
