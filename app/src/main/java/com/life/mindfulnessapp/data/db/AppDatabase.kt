package com.life.mindfulnessapp.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.life.mindfulnessapp.data.db.dao.AppLimitDao
import com.life.mindfulnessapp.data.db.dao.FavoriteQuoteDao
import com.life.mindfulnessapp.data.db.dao.LimitResetDao
import com.life.mindfulnessapp.data.db.dao.UsageRecordDao
import com.life.mindfulnessapp.data.db.entity.AppLimitEntity
import com.life.mindfulnessapp.data.db.entity.FavoriteQuoteEntity
import com.life.mindfulnessapp.data.db.entity.LimitResetEntity
import com.life.mindfulnessapp.data.db.entity.UsageRecordEntity

@Database(
    entities = [AppLimitEntity::class, UsageRecordEntity::class, LimitResetEntity::class, FavoriteQuoteEntity::class],
    version = 23,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun appLimitDao(): AppLimitDao
    abstract fun usageRecordDao(): UsageRecordDao
    abstract fun limitResetDao(): LimitResetDao
    abstract fun favoriteQuoteDao(): FavoriteQuoteDao

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

        /**
         * 版本 8 → 9：usage_records 新增 effectScore 字段
         * 用于记录用户在结束弹框中对本次使用效果的自评分（0-10，null 表示未评分）
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_records ADD COLUMN effectScore INTEGER")
            }
        }

        /**
         * 版本 9 → 10：新增 favorite_quotes 表
         * 存储用户在拦截页收藏的名言
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS favorite_quotes (
                        content TEXT NOT NULL PRIMARY KEY,
                        author TEXT NOT NULL DEFAULT '',
                        savedAt INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
            }
        }

        /**
         * 版本 10 → 11：app_limits 新增 usageCovenant（对该 App 的用法约定）
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE app_limits ADD COLUMN usageCovenant TEXT NOT NULL DEFAULT ''")
            }
        }

        /**
         * 版本 11 → 12：app_limits 新增 remindCovenantOnOpen（打开时提醒约定）
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN remindCovenantOnOpen INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * 版本 12 → 13：app_limits 新增 requireIntentOnOpen（意图门，与时长锁独立）
         * 默认 1：保持旧版「监控即拦截写意图」行为。
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN requireIntentOnOpen INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * 版本 13 → 14：usage_records 新增 mindfulnessLevel（意图回顾正念程度三档）
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE usage_records ADD COLUMN mindfulnessLevel INTEGER")
            }
        }

        /**
         * 版本 14 → 15：单次意图时长 + 记录侧意图类型 / 会话上限 / 续时
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN sessionLimitEnabled INTEGER NOT NULL DEFAULT 1"
                )
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN defaultSessionLimitMinutes INTEGER NOT NULL DEFAULT 15"
                )
                db.execSQL("ALTER TABLE usage_records ADD COLUMN intentKind TEXT")
                db.execSQL(
                    "ALTER TABLE usage_records ADD COLUMN sessionLimitMinutes INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE usage_records ADD COLUMN sessionExtensionMinutes INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 版本 15 → 16：意图门二级开关「允许无明确目的进入」
         */
        val MIGRATION_15_16 = object : Migration(15, 16) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN allowPurposelessEntry INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        /**
         * 版本 16 → 17：每日打开次数上限（按放行次数）
         */
        val MIGRATION_16_17 = object : Migration(16, 17) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN dailyOpenLimitEnabled INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN dailyOpenLimit INTEGER NOT NULL DEFAULT 5"
                )
            }
        }

        /**
         * 版本 17 → 18：每 App 意图回顾开关（默认关）
         */
        val MIGRATION_17_18 = object : Migration(17, 18) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN intentReviewEnabled INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 版本 18 → 19：监控列表自定义排序（首页坑位与管理页共用）
         */
        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN sortOrder INTEGER NOT NULL DEFAULT 0"
                )
                val cursor = db.query(
                    "SELECT packageName FROM app_limits ORDER BY createdAt ASC, packageName ASC"
                )
                try {
                    var order = 0
                    while (cursor.moveToNext()) {
                        val pkg = cursor.getString(0)
                        db.execSQL(
                            "UPDATE app_limits SET sortOrder = ? WHERE packageName = ?",
                            arrayOf(order, pkg)
                        )
                        order++
                    }
                } finally {
                    cursor.close()
                }
            }
        }

        /**
         * 版本 19 → 20：时段锁（开关 + 窗口 JSON + 承诺文案）
         */
        val MIGRATION_19_20 = object : Migration(19, 20) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN periodLockEnabled INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN periodWindowsJson TEXT NOT NULL DEFAULT ''"
                )
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN periodLockCommitment TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        /**
         * 版本 20 → 21：意图门二级开关「意图质量检验」（默认关，需用户显式开启）
         */
        val MIGRATION_20_21 = object : Migration(20, 21) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN intentQualityCheckEnabled INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 版本 21 → 22：系锚前一周日均基线（冻结对照尺）
         */
        val MIGRATION_21_22 = object : Migration(21, 22) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN baselineDailyAvgSeconds INTEGER NOT NULL DEFAULT 0"
                )
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN baselineCapturedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }

        /**
         * 版本 22 → 23：意图检验改为用户自定义限制关键词
         */
        val MIGRATION_22_23 = object : Migration(22, 23) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE app_limits ADD COLUMN intentBlockKeywordsJson TEXT NOT NULL DEFAULT ''"
                )
            }
        }
    }
}
