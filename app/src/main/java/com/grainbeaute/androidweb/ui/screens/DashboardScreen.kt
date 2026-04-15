package com.grainbeaute.androidweb.ui.screens

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.work.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grainbeaute.androidweb.R
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.*
import com.grainbeaute.androidweb.ui.theme.CardBorder
import com.grainbeaute.androidweb.ui.theme.MedicalWarning
import com.grainbeaute.androidweb.workers.AppointmentReminderWorker
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, repository: LocalRepository) {
    var moles by remember { mutableStateOf<List<LocalMole>>(emptyList()) }
    var visits by remember { mutableStateOf<List<LocalDermatologistVisit>>(emptyList()) }
    var appSettings by remember { mutableStateOf<LocalAppSettings?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showPractitionerSheet by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    fun loadData() {
        scope.launch {
            isLoading = true
            try {
                moles = repository.getMoles()
                visits = repository.getVisits()
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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF007AFF),
                            shape = CircleShape
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_launcher_foreground),
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Dashboard Santé", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Paramètres", tint = Color.Gray)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(bottom = 32.dp, top = 16.dp)
                ) {
                    // SECTION 1 — Mes Grains
                    item {
                        MolesSummaryCard(
                            moleCount = moles.size,
                            statsContent = {
                                DiagnosisStatsRow(moles = moles)
                            }
                        )
                    }

                    // SECTION 2 — Mon Dermato
                    item {
                        Text("MON DERMATOLOGUE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            PractitionerCard(
                                settings = appSettings ?: LocalAppSettings(null, null, null, 7),
                                onClick = { showPractitionerSheet = true }
                            )

                            NextAppointmentCard(
                                nextAppointment = appSettings,
                                onClick = { showSettingsSheet = true }
                            )
                        }
                    }

                    // SECTION 3 — Dernière visite
                    item {
                        Text("DERNIÈRE VISITE", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                    }

                    if (visits.isEmpty()) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                                border = BorderStroke(1.dp, Color(0xFFE0E6ED))
                            ) {
                                Text(
                                    "Aucun historique de visite.",
                                    modifier = Modifier.padding(16.dp),
                                    color = Color.Gray,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    } else {
                        val lastVisit = visits.first()
                        item {
                            VisitCard(
                                visit = lastVisit,
                                onClick = { navController.navigate("visit_detail/${lastVisit.id}") }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        SettingsAppointmentBottomSheet(
            currentSettings = appSettings ?: LocalAppSettings(null, null, null, 7),
            onDismiss = { showSettingsSheet = false },
            onSave = { newSettings ->
                scope.launch {
                    repository.updateAppSettings(newSettings)
                    if (newSettings.nextAppointmentDate != null) {
                        scheduleReminder(context, newSettings)
                    } else {
                        WorkManager.getInstance(context).cancelAllWorkByTag("appointment_reminder")
                    }
                    loadData()
                    showSettingsSheet = false
                }
            }
        )
    }

    if (showPractitionerSheet) {
        PractitionerInfoBottomSheet(
            currentSettings = appSettings ?: LocalAppSettings(null, null, null, 7),
            onDismiss = { showPractitionerSheet = false },
            onSave = { newSettings ->
                scope.launch {
                    repository.updateAppSettings(newSettings)
                    loadData()
                    showPractitionerSheet = false
                }
            }
        )
    }
}

@Composable
fun MolesSummaryCard(
    moleCount: Int,
    statsContent: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E6ED))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("MES GRAINS", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = Color(0xFFF0F7FF),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.List, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("$moleCount grains enregistrés", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Suivi en cours", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            statsContent()
        }
    }
}

@Composable
fun NextAppointmentCard(nextAppointment: LocalAppSettings?, onClick: () -> Unit) {
    val dateFormatter = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E6ED))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = Color(0xFFF0F7FF),
                shape = CircleShape
            ) {
                Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                val nextDateStr = nextAppointment?.nextAppointmentDate?.let { dateFormatter.format(Date(it)) } ?: "Non planifié"
                Text(
                    text = if (nextAppointment?.nextAppointmentDate != null) "Prochain RDV : $nextDateStr" else "Planifier un rendez-vous",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (nextAppointment?.nextAppointmentDate != null) Color.Black else Color(0xFF007AFF)
                )
                if (nextAppointment?.nextAppointmentDate != null) {
                    Text("Cliquez pour modifier le rappel", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun PractitionerCard(settings: LocalAppSettings, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E6ED))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = Color(0xFFF0F7FF),
                    shape = CircleShape
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.padding(8.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(settings.practitionerName ?: "Nom du praticien non renseigné", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    if (!settings.practitionerAddress.isNullOrBlank()) {
                        Text(settings.practitionerAddress!!, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosisStatsRow(moles: List<LocalMole>) {
    val stats = moles.groupBy { it.latestDiagnosis?.category }
    val benignCount = stats[DiagnosisCategory.BENIGN]?.size ?: 0
    val monitorCount = stats[DiagnosisCategory.MONITOR]?.size ?: 0
    val suspectCount = stats[DiagnosisCategory.SUSPECT]?.size ?: 0
    val removedCount = stats[DiagnosisCategory.REMOVED]?.size ?: 0
    val noneCount = stats[null]?.size ?: 0

    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StatChip(count = benignCount, label = "Bénins", color = Color(0xFF4CAF50))
        StatChip(count = monitorCount, label = "Surveil.", color = Color(0xFFFF9800))
        StatChip(count = suspectCount, label = "Suspects", color = Color(0xFFF44336))
        StatChip(count = removedCount, label = "Retirés", color = Color(0xFF9E9E9E))
        StatChip(count = noneCount, label = "À diag.", color = Color(0xFF007AFF))
    }
}

@Composable
fun StatChip(count: Int, label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(count.toString(), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = color)
            Text(label, style = MaterialTheme.typography.labelSmall, color = color)
        }
    }
}
