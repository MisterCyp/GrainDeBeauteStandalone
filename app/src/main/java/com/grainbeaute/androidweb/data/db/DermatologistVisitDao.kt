package com.grainbeaute.androidweb.data.db

import androidx.room.*

@Dao
interface DermatologistVisitDao {
    @Query("SELECT * FROM dermatologist_visits ORDER BY date DESC")
    suspend fun getAll(): List<DermatologistVisitEntity>

    @Query("SELECT * FROM dermatologist_visits WHERE id = :id")
    suspend fun getById(id: Int): DermatologistVisitEntity?

    @Insert
    suspend fun insert(visit: DermatologistVisitEntity): Long

    @Update
    suspend fun update(visit: DermatologistVisitEntity)

    @Delete
    suspend fun delete(visit: DermatologistVisitEntity)

    @Query("SELECT * FROM dermatologist_visits ORDER BY date DESC LIMIT 1")
    suspend fun getLatest(): DermatologistVisitEntity?
}
