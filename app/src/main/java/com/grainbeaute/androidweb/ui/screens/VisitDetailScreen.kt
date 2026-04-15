package com.grainbeaute.androidweb.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.LocalDermatologistVisit
import com.grainbeaute.androidweb.model.LocalMole
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VisitDetailScreen(navController: NavController, repository: LocalRepository, visitId: Int) {
    var visit by remember { mutableStateOf<LocalDermatologistVisit?>(null) }
    var moles by remember { mutableStateOf<List<LocalMole>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val dateFormatter = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)

    fun loadData() {
        scope.launch {
            isLoading = true
            visit = repository.getVisit(visitId)
            moles = repository.getMoles()
            isLoading = false
        }
    }

    LaunchedEffect(visitId) {
        loadData()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Détail de la visite") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showEditSheet = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifier")
                    }
                    IconButton(onClick = { showDeleteConfirmation = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color.Red)
                    }
                }
            )
        }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (visit == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Visite non trouvée")
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                item {
                    Column {
                        Text(dateFormatter.format(Date(visit!!.date)), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        if (!visit!!.practitionerName.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF007AFF))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Dr. ${visit!!.practitionerName}", style = MaterialTheme.typography.titleMedium, color = Color(0xFF007AFF))
                            }
                        }
                        if (!visit!!.practitionerAddress.isNullOrBlank()) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Default.LocationOn, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color.Gray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(visit!!.practitionerAddress!!, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                            }
                        }
                    }
                }

                if (!visit!!.globalNote.isNullOrBlank()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
                            border = BorderStroke(1.dp, Color(0xFFE0E6ED))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("NOTE GLOBALE", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(visit!!.globalNote!!, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }

                item {
                    Text("GRAINS EXAMINÉS (${visit!!.diagnoses.size})", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Gray)
                }

                items(visit!!.diagnoses) { diag ->
                    val mole = moles.find { it.id == diag.moleId }
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE0E6ED))
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(50.dp).clip(CircleShape).background(Color.LightGray)) {
                                mole?.lastCapture?.let {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(it.croppedImagePath?.let { path -> File(path) }).build(),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(diag.moleName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                DiagnosisBadge(diag.category)
                                if (!diag.note.isNullOrBlank()) {
                                    Text(diag.note!!, style = MaterialTheme.typography.bodySmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }
                    }
                }
                
                item {
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showEditSheet && visit != null) {
        NewVisitBottomSheet(
            moles = moles,
            initialVisit = visit,
            onDismiss = { showEditSheet = false },
            onSave = { date, practitionerName, practitionerAddress, note, diags ->
                scope.launch {
                    val finalDiags = diags.map { it.second }
                    repository.updateVisit(visitId, date, practitionerName, practitionerAddress, note, finalDiags)
                    loadData()
                    showEditSheet = false
                }
            }
        )
    }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Supprimer la visite") },
            text = { Text("Êtes-vous sûr de vouloir supprimer cette visite ? Cette action est irréversible.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            repository.deleteVisit(visitId)
                            showDeleteConfirmation = false
                            navController.popBackStack()
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.Red)
                ) {
                    Text("Supprimer")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}
