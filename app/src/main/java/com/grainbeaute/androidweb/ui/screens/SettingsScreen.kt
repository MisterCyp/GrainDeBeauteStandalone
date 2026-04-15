package com.grainbeaute.androidweb.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController

import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.LocalAppSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(navController: NavController, repository: LocalRepository) {
    var appSettings by remember { mutableStateOf<LocalAppSettings?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        appSettings = repository.getAppSettings()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Paramètres") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "GÉNÉRAL",
                style = MaterialTheme.typography.labelLarge,
                color = Color.Gray,
                modifier = Modifier.padding(16.dp)
            )

            SettingsMenuItem(
                icon = Icons.Default.Camera,
                title = "Matériel",
                description = "Calibration du centre de l'image",
                onClick = { navController.navigate("calibration") }
            )
            
            Divider(modifier = Modifier.padding(horizontal = 16.dp))

            // Section Rappels
            appSettings?.let { settings ->
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Notifications, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Rappels de rendez-vous", style = MaterialTheme.typography.titleMedium)
                            Text("Nombre de jours avant le RDV", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        listOf(1, 3, 7, 14).forEach { days ->
                            FilterChip(
                                selected = settings.reminderDaysBefore == days,
                                onClick = {
                                    scope.launch {
                                        val newSettings = settings.copy(reminderDaysBefore = days)
                                        repository.updateAppSettings(newSettings)
                                        appSettings = newSettings
                                        // TODO: Recalculer le reminder si un RDV existe
                                    }
                                },
                                label = { Text("$days j") }
                            )
                        }
                    }
                }
            }

            appSettings?.let { settings ->
                Divider(modifier = Modifier.padding(horizontal = 16.dp))

                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                            }
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Silhouette", style = MaterialTheme.typography.titleMedium)
                            Text("Corps affiché sur la carte corporelle", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Button(
                            onClick = {
                                if (settings.bodyGender != "female") {
                                    val newSettings = settings.copy(bodyGender = "female")
                                    scope.launch {
                                        repository.updateAppSettings(newSettings)
                                        appSettings = newSettings
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.bodyGender == "female") Color(0xFF007AFF) else Color(0xFFE0E0E0),
                                contentColor   = if (settings.bodyGender == "female") Color.White else Color.Black,
                            ),
                            shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text("Femme") }

                        Button(
                            onClick = {
                                if (settings.bodyGender != "male") {
                                    val newSettings = settings.copy(bodyGender = "male")
                                    scope.launch {
                                        repository.updateAppSettings(newSettings)
                                        appSettings = newSettings
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (settings.bodyGender == "male") Color(0xFF007AFF) else Color(0xFFE0E0E0),
                                contentColor   = if (settings.bodyGender == "male") Color.White else Color.Black,
                            ),
                            shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                            modifier = Modifier.weight(1f),
                        ) { Text("Homme") }
                    }
                }
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            SettingsMenuItem(
                icon = Icons.Default.Info,
                title = "À propos",
                description = "Version du logiciel et informations",
                onClick = { navController.navigate("settings/about") }
            )
        }
    }
}

@Composable
fun SettingsMenuItem(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(40.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = CircleShape
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(description, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        }
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.LightGray
        )
    }
}
