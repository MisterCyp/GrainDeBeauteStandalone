package com.grainbeaute.androidweb.data.db

import androidx.room.*

@Dao
interface AppSettingsDao {
    @Query("SELECT * FROM app_settings WHERE id = 1")
    suspend fun get(): AppSettingsEntity?

    @Upsert
    suspend fun upsert(settings: AppSettingsEntity)
}
