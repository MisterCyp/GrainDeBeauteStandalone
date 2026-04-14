package com.grainbeaute.androidweb.data

import android.content.Context
import android.content.SharedPreferences

class CalibrationManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("calibration_prefs", Context.MODE_PRIVATE)

    var centerX: Float
        get() = prefs.getFloat("center_x", 0.5f)
        set(value) = prefs.edit().putFloat("center_x", value).apply()

    var centerY: Float
        get() = prefs.getFloat("center_y", 0.5f)
        set(value) = prefs.edit().putFloat("center_y", value).apply()

    var exposureIndex: Float
        get() = prefs.getFloat("exposure_index", -7f)
        set(value) = prefs.edit().putFloat("exposure_index", value).apply()

    fun reset() {
        prefs.edit().clear().apply()
    }
}
