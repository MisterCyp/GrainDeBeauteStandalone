package com.grainbeaute.androidweb.data.db

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    @TypeConverter
    fun floatListToJson(value: List<Float>?): String? =
        value?.let { gson.toJson(it) }

    @TypeConverter
    fun jsonToFloatList(value: String?): List<Float>? =
        value?.let { gson.fromJson(it, object : TypeToken<List<Float>>() {}.type) }
}
