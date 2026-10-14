package com.kirankotla.connectfour.data

import android.content.Context
import androidx.room.Database
import androidx.room.migration.Migration
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        SavedGameEntity::class,
        MatchSummaryEntity::class,
        MatchReplayEntity::class,
        PlayerProfileEntity::class,
        PlayerStatsEntity::class,
        AchievementEntity::class
    ],
    version = 5,
    exportSchema = false
)
abstract class ConnectFourDatabase : RoomDatabase() {

    abstract fun connectFourDao(): ConnectFourDao

    companion object {
        @Volatile
        private var INSTANCE: ConnectFourDatabase? = null

        fun getDatabase(context: Context): ConnectFourDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ConnectFourDatabase::class.java,
                    "connectfour_database"
                ).addMigrations(MIGRATION_3_4, MIGRATION_4_5).build()
                INSTANCE = instance
                instance
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE player_stats ADD COLUMN lastDailyClaimDay INTEGER NOT NULL DEFAULT -1")
                database.execSQL("ALTER TABLE player_stats ADD COLUMN dailyStreak INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE player_stats ADD COLUMN bestDailyStreak INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE player_stats ADD COLUMN lifetimeXp INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE player_stats ADD COLUMN totalPlayTimeSeconds INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE player_stats ADD COLUMN powerUpsUsed INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE player_stats ADD COLUMN highestCombo INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
