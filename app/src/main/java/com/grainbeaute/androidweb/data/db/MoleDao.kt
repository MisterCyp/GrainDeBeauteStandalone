package com.grainbeaute.androidweb.data.db

import androidx.room.*

@Dao
interface MoleDao {
    @Query("SELECT * FROM moles ORDER BY createdAt DESC")
    suspend fun getAll(): List<MoleEntity>

    @Query("SELECT * FROM moles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): MoleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(mole: MoleEntity): Long

    @Delete
    suspend fun delete(mole: MoleEntity)

    @Update
    suspend fun update(mole: MoleEntity)
}
