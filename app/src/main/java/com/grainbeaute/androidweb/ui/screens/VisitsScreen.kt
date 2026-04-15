package com.grainbeaute.androidweb.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.LocalAppSettings
import com.grainbeaute.androidweb.model.LocalDermatologistVisit
import com.grainbeaute.androidweb.model.LocalMole
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitsScreen(navController: NavController, repository: LocalRepository) {
    var visits by remember { mutableStateOf<List<LocalDermatologistVisit>>(emptyList()) }
    var moles by remember { mutableStateOf<List<LocalMole>>(emptyList()) }
    var appSettings by remember { mutableStateOf<LocalAppSettings?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var showNewVisitSheet by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadData() {
        scope.launch {
            isLoading = true
            try {
                visits = repository.getVisits()
                moles = repository.getMoles()
                appSettings = repository.getAppSettings()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Erreur : ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes Visites", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showNewVisitSheet = true },
                containerColor = Color(0xFF007AFF),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Ajouter une visite")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (visits.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Aucune visite enregistrée", style = MaterialTheme.typography.titleMedium, color = Color.Gray)
                    Text("Utilisez le bouton + pour en ajouter une", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
            ) {
                items(visits) { visit ->
                    VisitCard(
                        visit = visit,
                        onClick = { navController.navigate("visit_detail/${visit.id}") }
                    )
                }
            }
        }
    }

    if (showNewVisitSheet) {
        NewVisitBottomSheet(
            moles = moles,
            currentPractitionerName = appSettings?.practitionerName,
            currentPractitionerAddress = appSettings?.practitionerAddress,
            onDismiss = { showNewVisitSheet = false },
            onSave = { date, practitionerName, practitionerAddress, note, diags ->
                scope.launch {
                    repository.createVisit(date, practitionerName, practitionerAddress, note, diags)
                    loadData()
                    showNewVisitSheet = false
                }
            }
        )
    }
}
