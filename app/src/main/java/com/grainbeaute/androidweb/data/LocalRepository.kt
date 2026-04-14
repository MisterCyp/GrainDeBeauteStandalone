package com.grainbeaute.androidweb.data

import android.content.Context
import android.util.Log
import com.chaquo.python.Python
import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.grainbeaute.androidweb.data.db.*
import com.grainbeaute.androidweb.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class LocalRepository(private val context: Context) {

    private val db = AppDatabase.getInstance(context)
    private val moleDao = db.moleDao()
    private val captureDao = db.captureDao()
    private val gson = Gson()

    val imagesDir: File
        get() = File(context.filesDir, "images").also { it.mkdirs() }

    // ──────────────────────────────────────────────────────────
    // MOLES
    // ──────────────────────────────────────────────────────────

    suspend fun getMoles(): List<LocalMole> = withContext(Dispatchers.IO) {
        moleDao.getAll().map { mole ->
            val captures = captureDao.getByMole(mole.id).map { it.toLocalCapture() }
            mole.toLocalMole(captures = captures, lastCapture = captures.firstOrNull())
        }
    }

    suspend fun getMole(id: Int): LocalMole? = withContext(Dispatchers.IO) {
        val mole = moleDao.getById(id) ?: return@withContext null
        val captures = captureDao.getByMole(id).map { it.toLocalCapture() }
        mole.toLocalMole(captures = captures, lastCapture = captures.firstOrNull())
    }

    suspend fun createMole(name: String, bodyPart: String?): LocalMole = withContext(Dispatchers.IO) {
        val entity = MoleEntity(name = name, bodyPart = bodyPart)
        val id = moleDao.insert(entity).toInt()
        entity.copy(id = id).toLocalMole()
    }

    suspend fun deleteMole(id: Int) = withContext(Dispatchers.IO) {
        val entity = moleDao.getById(id) ?: return@withContext
        moleDao.delete(entity)
    }

    suspend fun updateMole(id: Int, name: String, bodyPart: String?) = withContext(Dispatchers.IO) {
        val entity = moleDao.getById(id) ?: return@withContext
        moleDao.update(entity.copy(name = name, bodyPart = bodyPart))
    }

    // ──────────────────────────────────────────────────────────
    // CAPTURES — lecture
    // ──────────────────────────────────────────────────────────

    suspend fun getCaptures(moleId: Int): List<LocalCapture> = withContext(Dispatchers.IO) {
        captureDao.getByMole(moleId).map { it.toLocalCapture() }
    }

    suspend fun getCapture(id: Int): LocalCapture? = withContext(Dispatchers.IO) {
        captureDao.getById(id)?.toLocalCapture()
    }

    suspend fun getUnassignedCaptures(): List<LocalCapture> = withContext(Dispatchers.IO) {
        captureDao.getUnassigned().map { it.toLocalCapture() }
    }

    suspend fun getEvolution(moleId: Int): List<LocalEvolutionPoint> = withContext(Dispatchers.IO) {
        captureDao.getByMoleChronological(moleId)
            .filter { it.status == "done" }
            .map { cap ->
                LocalEvolutionPoint(
                    captureId = cap.id,
                    date = cap.createdAt,
                    areaMm2 = cap.areaMm2,
                    maxDimensionMm = cap.maxDimensionMm,
                    circularity = cap.circularity,
                    asymmetry = cap.asymmetry,
                    colorVariation = cap.colorVariation,
                )
            }
    }

    // ──────────────────────────────────────────────────────────
    // CAPTURES — écriture
    // ──────────────────────────────────────────────────────────

    suspend fun deleteCapture(id: Int) = withContext(Dispatchers.IO) {
        val entity = captureDao.getById(id) ?: return@withContext
        listOfNotNull(entity.imagePath, entity.analyzedImagePath, entity.croppedImagePath)
            .forEach { File(it).delete() }
        captureDao.delete(entity)
    }

    suspend fun assignCapture(captureId: Int, moleId: Int) = withContext(Dispatchers.IO) {
        captureDao.assign(captureId, moleId)
    }

    // ──────────────────────────────────────────────────────────
    // UPLOAD + ANALYSE
    // ──────────────────────────────────────────────────────────

    suspend fun uploadCapture(moleId: Int?, sourceFile: File): LocalCapture = withContext(Dispatchers.IO) {
        val destFile = File(imagesDir, "original_${System.currentTimeMillis()}.jpg")
        sourceFile.copyTo(destFile, overwrite = true)

        val entity = CaptureEntity(
            moleId = moleId,
            imagePath = destFile.absolutePath,
            status = "pending",
        )
        val captureId = captureDao.insert(entity).toInt()
        val inserted = entity.copy(id = captureId)

        CoroutineScope(Dispatchers.IO).launch {
            runAnalysis(captureId, destFile.absolutePath)
        }

        inserted.toLocalCapture()
    }

    suspend fun reanalyzeCapture(captureId: Int) = withContext(Dispatchers.IO) {
        val entity = captureDao.getById(captureId) ?: return@withContext
        captureDao.update(entity.copy(status = "pending", errorMessage = null))
        CoroutineScope(Dispatchers.IO).launch {
            runAnalysis(captureId, entity.imagePath)
        }
    }

    private suspend fun runAnalysis(captureId: Int, imagePath: String) {
        Log.d("GrainAnalysis", "START captureId=$captureId imagePath=$imagePath")
        try {
            val py = Python.getInstance()
            val runner = py.getModule("analysis_runner")
            Log.d("GrainAnalysis", "Module analysis_runner chargé, appel run_analysis...")
            // Python retourne un JSON string (le dict PyObject ne supporte pas l'accès par String key)
            val jsonStr = runner.callAttr("run_analysis", imagePath, imagesDir.absolutePath).toString()
            val result = JsonParser.parseString(jsonStr).asJsonObject
            Log.d("GrainAnalysis", "run_analysis OK method=${result.get("method_used")?.asString}")

            val fvType = object : TypeToken<List<Float>>() {}.type
            val featureVector: List<Float>? = result.get("feature_vector")
                ?.takeIf { !it.isJsonNull }
                ?.let { gson.fromJson(it, fvType) }
            Log.d("GrainAnalysis", "feature_vector: ${if (featureVector != null) "${featureVector.size} dims" else "null"}")
            val featureVectorJson = featureVector?.let { gson.toJson(it) }

            val matchResult = featureVector?.let { runMatching(captureId, it) }
            val suggestionsJson = matchResult?.first
            val isConfident = matchResult?.second ?: false
            Log.d("GrainAnalysis", "matching: isConfident=$isConfident suggestions=${suggestionsJson?.take(80)}")

            val entity = captureDao.getById(captureId) ?: return
            captureDao.update(
                entity.copy(
                    status = "done",
                    analyzedImagePath = result.get("analyzed_image_path")?.asString,
                    croppedImagePath = result.get("cropped_image_path")?.asString,
                    areaMm2 = result.get("area_mm2")?.asFloat,
                    maxDimensionMm = result.get("max_dimension_mm")?.asFloat,
                    circularity = result.get("circularity")?.asFloat,
                    asymmetry = result.get("asymmetry")?.asFloat,
                    colorVariation = result.get("color_variation")?.asFloat,
                    methodUsed = result.get("method_used")?.asString,
                    featureVector = featureVectorJson,
                    suggestions = suggestionsJson,
                    isConfident = isConfident,
                )
            )
            Log.d("GrainAnalysis", "DONE captureId=$captureId status=done area=${result.get("area_mm2")?.asFloat}")
        } catch (e: Exception) {
            val fullTrace = e.stackTraceToString()
            Log.e("GrainAnalysis", "FAILED captureId=$captureId\n$fullTrace")
            val entity = captureDao.getById(captureId) ?: return
            captureDao.update(entity.copy(status = "error", errorMessage = e.message ?: "Erreur inconnue"))
        }
    }

    private suspend fun runMatching(
        excludeCaptureId: Int,
        featureVector: List<Float>,
    ): Pair<String, Boolean>? {
        return try {
            val references = captureDao.getAllWithFeatureVector()
                .filter { it.id != excludeCaptureId }
            if (references.isEmpty()) return null

            val moleMap = moleDao.getAll().associateBy { it.id }

            data class Candidate(
                val mole_id: Int,
                val mole_name: String,
                val body_part: String?,
                val last_capture_id: Int,
                val cropped_image_path: String?,
                val feature_vector: List<Float>,
            )

            val candidates = references.mapNotNull { cap ->
                val m = cap.moleId?.let { moleMap[it] } ?: return@mapNotNull null
                val fv: List<Float> = cap.featureVector?.let {
                    gson.fromJson(it, object : TypeToken<List<Float>>() {}.type)
                } ?: return@mapNotNull null
                Candidate(m.id, m.name, m.bodyPart, cap.id, cap.croppedImagePath, fv)
            }

            if (candidates.isEmpty()) return null

            val candidatesJson = gson.toJson(candidates)
            val featureVectorJson = gson.toJson(featureVector)

            val py = Python.getInstance()
            val matchingModule = py.getModule("matching_service_local")
            val matchJsonStr = matchingModule.callAttr(
                "find_matching_moles_json",
                featureVectorJson,
                candidatesJson,
            ).toString()
            val matchResult = JsonParser.parseString(matchJsonStr).asJsonObject

            val matchesArray = matchResult.getAsJsonArray("matches") ?: return null
            val matchesList = matchesArray.map { elem ->
                val m = elem.asJsonObject
                LocalMoleCandidate(
                    moleId = m.get("mole_id").asInt,
                    moleName = m.get("mole_name").asString,
                    bodyPart = m.get("body_part")?.takeIf { !it.isJsonNull }?.asString,
                    score = m.get("score").asDouble,
                    lastCaptureId = m.get("last_capture_id").asInt,
                    croppedImagePath = m.get("cropped_image_path")?.takeIf { !it.isJsonNull }?.asString,
                )
            }
            val isConfident = matchResult.get("is_confident")?.asBoolean ?: false

            Pair(gson.toJson(matchesList), isConfident)
        } catch (_: Exception) {
            null
        }
    }

    // ──────────────────────────────────────────────────────────
    // CONVERSIONS Entity → LocalModel
    // ──────────────────────────────────────────────────────────

    private fun MoleEntity.toLocalMole(
        captures: List<LocalCapture> = emptyList(),
        lastCapture: LocalCapture? = null,
    ) = LocalMole(
        id = id, name = name, bodyPart = bodyPart,
        createdAt = createdAt, lastCapture = lastCapture, captures = captures,
    )

    fun CaptureEntity.toLocalCapture(): LocalCapture {
        val suggestionsList: List<LocalMoleCandidate>? = suggestions?.let {
            try {
                gson.fromJson(it, object : TypeToken<List<LocalMoleCandidate>>() {}.type)
            } catch (_: Exception) { null }
        }
        val ar = if (areaMm2 != null) LocalAnalysisResult(
            id = 0, captureId = id,
            areaMm2 = areaMm2, maxDimensionMm = maxDimensionMm,
            circularity = circularity, asymmetry = asymmetry,
            colorVariation = colorVariation, methodUsed = methodUsed,
        ) else null
        return LocalCapture(
            id = id, moleId = moleId,
            imagePath = imagePath,
            analyzedImagePath = analyzedImagePath,
            croppedImagePath = croppedImagePath,
            status = status, errorMessage = errorMessage,
            isConfident = isConfident,
            suggestions = suggestionsList,
            analysisResult = ar,
            createdAt = createdAt,
        )
    }
}
