package com.grainbeaute.androidweb.data.db

import androidx.room.*

@Dao
interface CaptureDao {
    @Query("SELECT * FROM captures WHERE moleId = :moleId ORDER BY createdAt DESC")
    suspend fun getByMole(moleId: Int): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE id = :id LIMIT 1")
    suspend fun getById(id: Int): CaptureEntity?

    @Query("SELECT * FROM captures WHERE moleId IS NULL ORDER BY createdAt DESC")
    suspend fun getUnassigned(): List<CaptureEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(capture: CaptureEntity): Long

    @Update
    suspend fun update(capture: CaptureEntity)

    @Delete
    suspend fun delete(capture: CaptureEntity)

    @Query("UPDATE captures SET moleId = :moleId WHERE id = :captureId")
    suspend fun assign(captureId: Int, moleId: Int)

    @Query("""
        SELECT * FROM captures
        WHERE moleId IS NOT NULL
          AND status = 'done'
          AND featureVector IS NOT NULL
        ORDER BY createdAt DESC
    """)
    suspend fun getAllWithFeatureVector(): List<CaptureEntity>

    @Query("SELECT * FROM captures WHERE moleId = :moleId ORDER BY createdAt ASC")
    suspend fun getByMoleChronological(moleId: Int): List<CaptureEntity>
}
