package com.padraik99.grindhaus.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Bean::class], version = 1, exportSchema = true)
abstract class GrindhausDatabase : RoomDatabase() {

    abstract fun beanDao(): BeanDao

    companion object {
        @Volatile
        private var instance: GrindhausDatabase? = null

        fun get(context: Context): GrindhausDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    GrindhausDatabase::class.java,
                    "grindhaus.db",
                ).build().also { instance = it }
            }
    }
}
