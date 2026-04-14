package com.grainbeaute.androidweb

import android.app.Application
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class GrainBeauteApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // Initialiser Python en background pour ne pas bloquer l'UI au démarrage
        CoroutineScope(Dispatchers.IO).launch {
            if (!Python.isStarted()) {
                Python.start(AndroidPlatform(this@GrainBeauteApplication))
            }
        }
    }
}
