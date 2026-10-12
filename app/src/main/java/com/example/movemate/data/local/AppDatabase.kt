package com.example.movemate.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.movemate.data.local.dao.FoodDao
import com.example.movemate.data.local.dao.SleepDao
import com.example.movemate.data.local.dao.StepDao
import com.example.movemate.data.local.dao.UserProfileDao
import com.example.movemate.data.local.dao.WeightDao
import com.example.movemate.data.local.entity.FoodRecord
import com.example.movemate.data.local.entity.SleepRecord
import com.example.movemate.data.local.entity.StepRecord
import com.example.movemate.data.local.entity.UserProfile
import com.example.movemate.data.local.entity.WeightRecord

@Database(
    entities = [
        StepRecord::class,
        SleepRecord::class,
        FoodRecord::class,
        WeightRecord::class,
        UserProfile::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {

    abstract fun stepDao(): StepDao
    abstract fun sleepDao(): SleepDao
    abstract fun foodDao(): FoodDao
    abstract fun weightDao(): WeightDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        /**
         * Explicit migration from schema version 1 → 2.
         * Replace this body with the SQL ALTER / CREATE statements that reflect
         * whatever structural change was made when the version was bumped to 2.
         * Adding an explicit migration here protects existing user data instead of
         * wiping it via fallbackToDestructiveMigration.
         *
         * How to add future migrations:
         *   1. Bump [version] above.
         *   2. Create MIGRATION_2_3 = object : Migration(2, 3) { ... } with the SQL changes.
         *   3. Add it to addMigrations() in getInstance() below.
         */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // If the v1 → v2 change was purely data/behaviour-level with no schema
                // change, this can remain empty.  Add SQL here for any column or table
                // changes that occurred between versions.
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val db = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "movemate_db"
                )
                    .addMigrations(MIGRATION_1_2)
                    // fallbackToDestructiveMigration() removed — schema changes must now
                    // be handled by explicit Migration objects to protect user data.
                    .build()
                INSTANCE = db
                db
            }
        }
    }
}
