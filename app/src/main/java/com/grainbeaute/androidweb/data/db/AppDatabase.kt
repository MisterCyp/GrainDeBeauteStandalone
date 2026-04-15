package com.grainbeaute.androidweb.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MoleEntity::class,
        CaptureEntity::class,
        DermatologistVisitEntity::class,
        MoleDiagnosisEntity::class,
        AppSettingsEntity::class
    ],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun moleDao(): MoleDao
    abstract fun captureDao(): CaptureDao
    abstract fun dermatologistVisitDao(): DermatologistVisitDao
    abstract fun moleDiagnosisDao(): MoleDiagnosisDao
    abstract fun appSettingsDao(): AppSettingsDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "grainbeaute.db"
                )
                    .addMigrations(*Migrations.ALL)
                    .build().also { INSTANCE = it }
            }
    }
}
