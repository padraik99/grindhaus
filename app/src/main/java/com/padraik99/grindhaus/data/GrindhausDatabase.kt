package com.padraik99.grindhaus.data

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

/**
 * Version history (each version's layout is saved in app/schemas):
 * 1. beans
 * 2. adds grinders, dial_presets, brew_logs, intake_events, bean_comparisons, and
 *    two bean columns (buy_again, rank_score, with defaults). Only additions, so
 *    Room upgrades version 1 automatically and leaves every bean in place.
 */
@Database(
    entities = [Bean::class, Grinder::class, DialPreset::class, BrewLog::class, IntakeEvent::class, BeanComparison::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2)],
)
abstract class GrindhausDatabase : RoomDatabase() {

    abstract fun beanDao(): BeanDao
    abstract fun grinderDao(): GrinderDao
    abstract fun brewDao(): BrewDao

    companion object {
        const val NAME = "grindhaus.db"

        @Volatile
        private var instance: GrindhausDatabase? = null

        fun get(context: Context): GrindhausDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(context.applicationContext, GrindhausDatabase::class.java, NAME)
                    .build()
                    .also { instance = it }
            }
    }
}
