package com.grainbeaute.androidweb

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.ui.screens.*
import com.grainbeaute.androidweb.ui.theme.GrainBeauteTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GrainBeauteTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation(LocalRepository(applicationContext))
                }
            }
        }
    }
}

@Composable
fun AppNavigation(repository: LocalRepository) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {
        composable("main") { MainScreen(navController, repository) }
        composable("mole_detail/{moleId}") { backStackEntry ->
            val moleId = backStackEntry.arguments?.getString("moleId")?.toIntOrNull() ?: 0
            MoleDetailScreen(navController, moleId, repository)
        }
        composable("capture_detail/{captureId}") { backStackEntry ->
            val captureId = backStackEntry.arguments?.getString("captureId")?.toIntOrNull() ?: 0
            CaptureDetailScreen(navController, captureId, repository)
        }
        composable("camera/{moleId}") { backStackEntry ->
            val moleId = backStackEntry.arguments?.getString("moleId")?.toIntOrNull() ?: 0
            CameraScreen(navController, moleId, repository)
        }
        composable("settings") { SettingsScreen(navController) }
        composable("settings/about") { SettingsAboutScreen(navController) }
        composable("calibration") { CalibrationScreen(navController) }
    }
}
