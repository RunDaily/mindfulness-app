package com.life.mindfulnessapp.di

import android.content.Context
import androidx.room.Room
import com.life.mindfulnessapp.data.db.AppDatabase
import com.life.mindfulnessapp.data.db.dao.AppLimitDao
import com.life.mindfulnessapp.data.db.dao.LimitResetDao
import com.life.mindfulnessapp.data.db.dao.PlanBlockDao
import com.life.mindfulnessapp.data.db.dao.ScheduleItemDao
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
                AppDatabase.MIGRATION_22_23,
                AppDatabase.MIGRATION_23_24,
                AppDatabase.MIGRATION_24_25,
                AppDatabase.MIGRATION_25_26,
                AppDatabase.MIGRATION_26_27,
                AppDatabase.MIGRATION_27_28,
                AppDatabase.MIGRATION_28_29,
                AppDatabase.MIGRATION_29_30,
                AppDatabase.MIGRATION_30_31,
                AppDatabase.MIGRATION_31_32,
                AppDatabase.MIGRATION_32_33,
                AppDatabase.MIGRATION_33_34,
                AppDatabase.MIGRATION_34_35,
                AppDatabase.MIGRATION_35_36,
                AppDatabase.MIGRATION_36_37,
                AppDatabase.MIGRATION_37_38,
                AppDatabase.MIGRATION_38_39,
                AppDatabase.MIGRATION_39_40,
                AppDatabase.MIGRATION_40_41
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
    fun providePlanBlockDao(db: AppDatabase): PlanBlockDao = db.planBlockDao()

    @Provides
    fun provideScheduleItemDao(db: AppDatabase): ScheduleItemDao = db.scheduleItemDao()
}
