package com.grainbeaute.androidweb.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "moles")
data class MoleEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val bodyPart: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "captures",
    foreignKeys = [
        ForeignKey(
            entity = MoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["moleId"],
            onDelete = ForeignKey.SET_NULL,
        )
    ],
    indices = [Index("moleId")]
)
data class CaptureEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val moleId: Int? = null,
    val imagePath: String,
    val analyzedImagePath: String? = null,
    val croppedImagePath: String? = null,
    val status: String = "pending",
    val errorMessage: String? = null,
    val areaMm2: Float? = null,
    val maxDimensionMm: Float? = null,
    val circularity: Float? = null,
    val asymmetry: Float? = null,
    val colorVariation: Float? = null,
    val methodUsed: String? = null,
    val featureVector: String? = null,
    val suggestions: String? = null,
    val isConfident: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
