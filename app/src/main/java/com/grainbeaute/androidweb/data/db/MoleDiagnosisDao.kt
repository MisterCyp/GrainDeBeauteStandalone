package com.grainbeaute.androidweb.data.db

import androidx.room.*

@Dao
interface MoleDiagnosisDao {
    @Query("SELECT * FROM mole_diagnoses WHERE visitId = :visitId")
    suspend fun getByVisit(visitId: Int): List<MoleDiagnosisEntity>

    @Query("SELECT * FROM mole_diagnoses WHERE moleId = :moleId ORDER BY id DESC LIMIT 1")
    suspend fun getLatestByMole(moleId: Int): MoleDiagnosisEntity?

    @Insert
    suspend fun insertAll(diagnoses: List<MoleDiagnosisEntity>)

    @Query("DELETE FROM mole_diagnoses WHERE visitId = :visitId")
    suspend fun deleteByVisit(visitId: Int)

    @Transaction
    suspend fun replaceDiagnosesForVisit(visitId: Int, diagnoses: List<MoleDiagnosisEntity>) {
        deleteByVisit(visitId)
        insertAll(diagnoses)
    }
}
