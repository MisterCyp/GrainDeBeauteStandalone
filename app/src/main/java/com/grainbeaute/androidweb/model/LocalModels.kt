package com.grainbeaute.androidweb.model

data class LocalMole(
    val id: Int,
    val name: String,
    val bodyPart: String? = null,
    val createdAt: Long,
    val lastCapture: LocalCapture? = null,
    val captures: List<LocalCapture> = emptyList(),
    val latestDiagnosis: LocalMoleDiagnosis? = null,
)

enum class DiagnosisCategory { BENIGN, MONITOR, SUSPECT, REMOVED }

data class LocalMoleDiagnosis(
    val id: Int,
    val visitId: Int,
    val moleId: Int?,
    val moleName: String,
    val category: DiagnosisCategory,
    val note: String?,
    val visitDate: Long = 0L,
    val visitPractitionerName: String? = null,
)

data class LocalDermatologistVisit(
    val id: Int,
    val date: Long,
    val practitionerName: String?,
    val practitionerAddress: String?,
    val globalNote: String?,
    val diagnoses: List<LocalMoleDiagnosis>,
)

data class LocalAppSettings(
    val nextAppointmentDate: Long?,
    val practitionerName: String?,
    val practitionerAddress: String?,
    val reminderDaysBefore: Int,
)

data class LocalCapture(
    val id: Int,
    val moleId: Int? = null,
    val imagePath: String,
    val analyzedImagePath: String? = null,
    val croppedImagePath: String? = null,
    val status: String,
    val errorMessage: String? = null,
    val isConfident: Boolean = false,
    val suggestions: List<LocalMoleCandidate>? = null,
    val analysisResult: LocalAnalysisResult? = null,
    val createdAt: Long,
)

data class LocalMoleCandidate(
    val moleId: Int,
    val moleName: String,
    val bodyPart: String? = null,
    val score: Double,
    val lastCaptureId: Int,
    val croppedImagePath: String? = null,
)

data class LocalAnalysisResult(
    val id: Int,
    val captureId: Int,
    val areaMm2: Float?,
    val maxDimensionMm: Float?,
    val circularity: Float?,
    val asymmetry: Float?,
    val colorVariation: Float?,
    val methodUsed: String?,
)

data class LocalEvolutionPoint(
    val captureId: Int,
    val date: Long,
    val areaMm2: Float?,
    val maxDimensionMm: Float?,
    val circularity: Float?,
    val asymmetry: Float?,
    val colorVariation: Float?,
)
