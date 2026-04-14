package com.grainbeaute.androidweb.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grainbeaute.androidweb.R
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.LocalMole
import com.grainbeaute.androidweb.ui.theme.CardBorder
import com.grainbeaute.androidweb.ui.theme.MedicalWarning
import kotlinx.coroutines.launch
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(navController: NavController, repository: LocalRepository) {
    var moles by remember { mutableStateOf<List<LocalMole>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showAddDialog by remember { mutableStateOf(false) }
    var moleToDelete by remember { mutableStateOf<LocalMole?>(null) }
    var speedDialOpen by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadMoles() {
        scope.launch {
            isLoading = true
            try {
                moles = repository.getMoles()
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Erreur : ${e.message}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadMoles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(32.dp),
                            color = Color(0xFF007AFF), // Bleu médical
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
                        Text("Mes Grains de Beauté", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        floatingActionButton = {
            SpeedDialFab(
                isOpen = speedDialOpen,
                onToggle = { speedDialOpen = !speedDialOpen },
                onOpenCamera = {
                    speedDialOpen = false
                    navController.navigate("camera/-1")
                },
                onCreateMole = {
                    speedDialOpen = false
                    showAddDialog = true
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (moles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text("Aucun grain de beauté. Ajoutez-en un !", color = Color.Gray)
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(moles) { mole ->
                        MoleCard(
                            mole = mole,
                            onClick = { navController.navigate("mole_detail/${mole.id}") },
                            onDelete = { moleToDelete = mole }
                        )
                    }
                }
            }

            if (speedDialOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f))
                        .clickable { speedDialOpen = false }
                )
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var bodyPart by remember { mutableStateOf("") }
        
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(24.dp),
            content = {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFF5F7FA) // Fond gris médical
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Nouveau Grain", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        
                        Spacer(modifier = Modifier.height(20.dp))

                        // Carte blanche pour le formulaire
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E6ED))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    label = { Text("Nom (ex: Dos)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = bodyPart,
                                    onValueChange = { bodyPart = it },
                                    label = { Text("Partie du corps") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { showAddDialog = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                border = BorderStroke(1.dp, Color(0xFFE0E6ED)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Annuler")
                            }
                            
                            Button(
                                onClick = {
                                    scope.launch {
                                        try {
                                            repository.createMole(name, bodyPart.takeIf { it.isNotBlank() })
                                            loadMoles()
                                            showAddDialog = false
                                        } catch (e: Exception) {
                                            showAddDialog = false
                                            snackbarHostState.showSnackbar("Erreur lors de la création : ${e.localizedMessage ?: "erreur inconnue"}")
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = name.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Ajouter")
                            }
                        }
                    }
                }
            }
        )
    }

    moleToDelete?.let { mole ->
        AlertDialog(
            onDismissRequest = { moleToDelete = null },
            title = { Text("Supprimer ?") },
            text = { Text("Voulez-vous vraiment supprimer '${mole.name}' et toutes ses captures ?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                repository.deleteMole(mole.id)
                                loadMoles()
                                moleToDelete = null
                            } catch (e: Exception) {
                                moleToDelete = null
                                snackbarHostState.showSnackbar("Erreur lors de la suppression : ${e.localizedMessage ?: "erreur inconnue"}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { moleToDelete = null }) { Text("Annuler") }
            }
        )
    }
}

@Composable
fun MoleCard(mole: LocalMole, onClick: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorder))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .border(1.dp, Color.Black, CircleShape)
                    .background(Color.LightGray)
            ) {
                mole.lastCapture?.let {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(mole.lastCapture.croppedImagePath?.let { path -> File(path) })
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } ?: Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.align(Alignment.Center), tint = Color.Gray)
            }
            
            Spacer(modifier = Modifier.width(16.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(mole.name, style = MaterialTheme.typography.titleMedium)
                mole.bodyPart?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                }
                Spacer(modifier = Modifier.height(4.dp))

                val timeInfo = getTimeSinceLastAnalysis(mole.lastCapture?.createdAt)
                Text(
                    text = timeInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (timeInfo.contains("jour")) MedicalWarning else Color.Gray
                )
            }
            
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color.LightGray)
            }
        }
    }
}

fun getTimeSinceLastAnalysis(createdAt: Long?): String {
    if (createdAt == null) return "Aucune analyse"

    return try {
        val now = Date()
        val diffInMillis = now.time - createdAt
        val hours = TimeUnit.MILLISECONDS.toHours(diffInMillis)
        val days = TimeUnit.MILLISECONDS.toDays(diffInMillis)

        when {
            hours < 1 -> "À l'instant"
            days < 1 -> "Il y a $hours h"
            days == 1L -> "Il y a 1 jour"
            else -> "Il y a $days jours"
        }
    } catch (e: Exception) {
        "Date inconnue"
    }
}

@Composable
fun SpeedDialFab(
    isOpen: Boolean,
    onToggle: () -> Unit,
    onOpenCamera: () -> Unit,
    onCreateMole: () -> Unit
) {
    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(
            visible = isOpen,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Prendre des photos",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SmallFloatingActionButton(onClick = onOpenCamera) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Mode rafale")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Nouveau grain",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SmallFloatingActionButton(onClick = onCreateMole) {
                        Icon(Icons.Default.Add, contentDescription = "Nouveau grain")
                    }
                }
            }
        }
        FloatingActionButton(onClick = onToggle) {
            Icon(
                imageVector = if (isOpen) Icons.Default.Close else Icons.Default.Add,
                contentDescription = if (isOpen) "Fermer" else "Menu"
            )
        }
    }
}
