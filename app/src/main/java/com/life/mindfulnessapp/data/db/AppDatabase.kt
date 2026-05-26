package com.life.mindfulnessapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.life.mindfulnessapp.data.db.dao.AppLimitDao
import com.life.mindfulnessapp.data.db.dao.LimitResetDao
import com.life.mindfulnessapp.data.db.dao.UsageRecordDao
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

@Database(
    entities = [AppLimitEntity::class, UsageRecordEntity::class, LimitResetEntity::class],
    version = 8,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appLimitDao(): AppLimitDao
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun limitResetDao(): LimitResetDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_records ADD COLUMN purpose TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_limits ADD COLUMN dailyModifyCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN lastModifiedDate TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 版本 3 → 4：app_limits 新增 mindfulModeEnabled 字段（历史遗留，已弃用）
         * 该字段在后续版本中不再使用，但迁移脚本保留以维持数据库兼容性
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_limits ADD COLUMN mindfulModeEnabled INTEGER NOT NULL DEFAULT 0")
            }
        }

        /**
         * 版本 4 → 5：新增 limit_resets 表
         * 记录用户在超时后主动重新设定限额的行为事件，供首页时间轴特殊标注
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS limit_resets (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        packageName TEXT NOT NULL,
                        appName TEXT NOT NULL,
                        resetTime INTEGER NOT NULL,
                        oldDailyLimitMinutes INTEGER NOT NULL,
                        newDailyLimitMinutes INTEGER NOT NULL,
                        oldWeeklyLimitMinutes INTEGER NOT NULL DEFAULT 0,
                        newWeeklyLimitMinutes INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * 版本 5 → 6：usage_records 新增 note 字段
         * 允许用户在记录列表里事后为每次使用添加效果备注
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_records ADD COLUMN note TEXT")
            }
        }

        /**
         * 版本 6 → 7：app_limits 新增时长监控开关、超时提醒文案字段
         * （mindfulStartMinute/mindfulEndMinute 为历史遗留字段，已弃用）
         * 注意：此迁移曾在版本7实体中遗漏了 mindfulStartMinute/mindfulEndMinute，
         * 导致 Room identity hash 不一致。已由 MIGRATION_7_8 修正。
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_limits ADD COLUMN mindfulStartMinute INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN mindfulEndMinute INTEGER NOT NULL DEFAULT 1439")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN timeLimitEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN overTimeMessage TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 版本 7 → 8：移除 app_limits 表中已弃用的 mindfulStartMinute / mindfulEndMinute 字段
         * 同时修正版本7时 Room identity hash 不一致的问题
         * SQLite 不支持 DROP COLUMN，通过"创建新表 → 复制数据 → 删旧表 → 重命名"方式实现
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS app_limits_new (
                        packageName TEXT NOT NULL PRIMARY KEY,
                        appName TEXT NOT NULL,
                        dailyLimitMinutes INTEGER NOT NULL DEFAULT 60,
                        weeklyLimitMinutes INTEGER NOT NULL DEFAULT 0,
                        isEnabled INTEGER NOT NULL DEFAULT 1,
                        createdAt INTEGER NOT NULL DEFAULT 0,
                        dailyModifyCount INTEGER NOT NULL DEFAULT 0,
                        lastModifiedDate TEXT NOT NULL DEFAULT '',
                        timeLimitEnabled INTEGER NOT NULL DEFAULT 1,
                        overTimeMessage TEXT NOT NULL DEFAULT ''
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT INTO app_limits_new (
                        packageName, appName, dailyLimitMinutes, weeklyLimitMinutes,
                        isEnabled, createdAt, dailyModifyCount, lastModifiedDate,
                        timeLimitEnabled, overTimeMessage
                    )
                    SELECT
                        packageName, appName, dailyLimitMinutes, weeklyLimitMinutes,
                        isEnabled, createdAt, dailyModifyCount, lastModifiedDate,
                        timeLimitEnabled, overTimeMessage
                    FROM app_limits
                """.trimIndent())
                db.execSQL("DROP TABLE app_limits")
                db.execSQL("ALTER TABLE app_limits_new RENAME TO app_limits")
            }
        }

        /**
         * 版本 6 → 8：直接跳过版本7，供从未安装过版本7的设备使用
         * 仅添加 timeLimitEnabled 和 overTimeMessage（跳过已弃用的 mindfulStartMinute/mindfulEndMinute）
         */
        val MIGRATION_6_8 = object : Migration(6, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_limits ADD COLUMN timeLimitEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE app_limits ADD COLUMN overTimeMessage TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}
