package com.grainbeaute.androidweb.ui.screens

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
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.LocalCapture
import com.grainbeaute.androidweb.model.LocalMole
import com.grainbeaute.androidweb.model.LocalMoleCandidate
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    navController: NavController,
    repository: LocalRepository,
    onBadgeCountChange: (Int) -> Unit,
) {
    var captures by remember { mutableStateOf<List<LocalCapture>>(emptyList()) }
    var moles by remember { mutableStateOf<List<LocalMole>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var refreshKey by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        try {
            moles = repository.getMoles()
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Impossible de charger les grains : ${e.localizedMessage ?: "erreur"}")
        }
    }

    LaunchedEffect(refreshKey) {
        while (true) {
            try {
                val data = repository.getUnassignedCaptures()
                captures = data
                isLoading = false
                val badgeCount = data.count { it.status == "done" }
                onBadgeCountChange(badgeCount)
                if (!data.any { it.status == "pending" }) break
            } catch (e: Exception) {
                isLoading = false
                break
            }
            delay(3000)
        }
    }

    fun refresh() {
        refreshKey++
    }

    Scaffold(
        containerColor = Color(0xFFF5F7FA), // Fond gris très clair moderne
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Identification", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        if (captures.isNotEmpty()) {
                            Text("${captures.size} capture${if(captures.size > 1) "s" else ""} à identifier", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF007AFF))
            }
        } else if (captures.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(
                        modifier = Modifier.size(120.dp),
                        color = Color.White,
                        shape = CircleShape,
                        shadowElevation = 2.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text("✓", style = MaterialTheme.typography.displayLarge, color = Color(0xFF4CAF50))
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("Tout est à jour", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("Aucune capture en attente d'identification", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                items(captures, key = { it.id }) { capture ->
                    CaptureInboxCard(
                        capture = capture,
                        moles = moles,
                        onAssign = { captureId, moleId ->
                            scope.launch {
                                try {
                                    repository.assignCapture(captureId, moleId)
                                    navController.navigate("mole_detail/$moleId")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Erreur d'assignation : ${e.localizedMessage ?: "erreur locale"}")
                                }
                            }
                        },
                        onCreateAndAssign = { captureId, name, bodyPart ->
                            scope.launch {
                                try {
                                    val newMole = repository.createMole(name, bodyPart)
                                    repository.assignCapture(captureId, newMole.id)
                                    navController.navigate("mole_detail/${newMole.id}")
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Erreur : ${e.localizedMessage ?: "erreur locale"}")
                                }
                            }
                        },
                        onDelete = { captureId ->
                            scope.launch {
                                try {
                                    repository.deleteCapture(captureId)
                                    refresh()
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Erreur de suppression : ${e.localizedMessage ?: "erreur locale"}")
                                }
                            }
                        },
                        onReanalyze = { captureId ->
                            scope.launch {
                                try {
                                    repository.reanalyzeCapture(captureId)
                                    refresh()
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Erreur : ${e.localizedMessage ?: "erreur locale"}")
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureInboxCard(
    capture: LocalCapture,
    moles: List<LocalMole>,
    onAssign: (captureId: Int, moleId: Int) -> Unit,
    onCreateAndAssign: (captureId: Int, name: String, bodyPart: String?) -> Unit,
    onDelete: (captureId: Int) -> Unit,
    onReanalyze: (captureId: Int) -> Unit
) {
    var showNewMoleDialog by remember { mutableStateOf(false) }
    var showAllMolesDialog by remember { mutableStateOf(false) }
    var suggestionToConfirm by remember { mutableStateOf<LocalMoleCandidate?>(null) }

    val timeStr = remember(capture.createdAt) {
        try {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(capture.createdAt))
        } catch (_: Exception) { "?" }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFE0E6ED))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when (capture.status) {
                                "pending" -> Color(0xFFFFF9C4)
                                "error" -> Color(0xFFFFEBEE)
                                "done" -> Color(0xFFE3F2FD)
                                else -> Color(0xFFF5F7FA)
                            }
                        )
                        .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        when (capture.status) {
                            "pending" -> {
                                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = Color(0xFFFBC02D))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Analyse en cours...", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFF57F17))
                            }
                            "error" -> {
                                Text("✗", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Échec de l'analyse", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFFD32F2F))
                            }
                            "done" -> {
                                Text("✓", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Prêt pour identification", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1976D2))
                            }
                            else -> {
                                Text("Capture reçue", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                            }
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        Text(timeStr, style = MaterialTheme.typography.labelSmall, color = Color.Gray.copy(alpha = 0.7f))
                        IconButton(onClick = { onDelete(capture.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Supprimer", tint = Color.Black.copy(alpha = 0.2f), modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .align(Alignment.CenterHorizontally)
                    .clip(CircleShape)
                    .border(1.dp, Color.Black, CircleShape)
                    .background(Color(0xFFF9FAFB))
            ) {
                if (capture.status != "pending" && capture.croppedImagePath != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(File(capture.croppedImagePath))
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else if (capture.status == "pending") {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center).size(48.dp), color = Color(0xFF007AFF))
                }
            }

            if (capture.status == "error") {
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { onReanalyze(capture.id) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF))) { Text("Relancer l'analyse") }
            }

            if (capture.status == "done") {
                val suggestions = capture.suggestions ?: emptyList()

                if (suggestions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text("CORRESPONDANCES SUGGÉRÉES", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        items(suggestions.take(3)) { suggestion ->
                            SuggestionCircleItem(
                                name = suggestion.moleName,
                                score = suggestion.score.toFloat(),
                                croppedImagePath = suggestion.croppedImagePath,
                                onClick = { suggestionToConfirm = suggestion }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color(0xFFF0F4F8))
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { showAllMolesDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF007AFF)),
                        border = BorderStroke(1.dp, Color(0xFF007AFF))
                    ) {
                        Text("Choisir un grain...", style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = { showNewMoleDialog = true },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF0F4F8), contentColor = Color.Black)
                    ) {
                        Text("+ Nouveau grain", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }

    if (showAllMolesDialog) {
        MoleSelectionDialog(
            captureId = capture.id,
            capture = capture,
            moles = moles,
            onDismiss = { showAllMolesDialog = false },
            onMoleSelected = { mole ->
                showAllMolesDialog = false
                val lastCapture = mole.lastCapture ?: mole.captures.firstOrNull()
                suggestionToConfirm = LocalMoleCandidate(
                    moleId = mole.id,
                    moleName = mole.name,
                    score = 0.0,
                    croppedImagePath = lastCapture?.croppedImagePath,
                    bodyPart = mole.bodyPart,
                    lastCaptureId = lastCapture?.id ?: 0
                )
            }
        )
    }

    suggestionToConfirm?.let { suggestion ->
        AlertDialog(
            onDismissRequest = { suggestionToConfirm = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.padding(24.dp),
            content = {
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFFF5F7FA) // Fond gris médical
                ) {
                    Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Confirmer l'identité", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(20.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E6ED))
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, Color.Black, CircleShape)
                                    ) {
                                        if (capture.croppedImagePath != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(File(capture.croppedImagePath))
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        }
                                    }
                                    Text("Analyse", style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                }

                                Icon(
                                    imageVector = Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color(0xFF007AFF),
                                    modifier = Modifier.size(24.dp)
                                )

                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(CircleShape)
                                            .border(1.dp, Color(0xFF007AFF), CircleShape)
                                    ) {
                                        if (suggestion.croppedImagePath != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(File(suggestion.croppedImagePath))
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)), contentAlignment = Alignment.Center) {
                                                Text(suggestion.moleName.take(1).uppercase(), color = Color.LightGray, style = MaterialTheme.typography.titleLarge)
                                            }
                                        }
                                    }
                                    Text(suggestion.moleName, style = MaterialTheme.typography.labelSmall, color = Color.Gray, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Text(
                            "Voulez-vous assigner cette capture au grain '${suggestion.moleName}' ?",
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = Color.DarkGray
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Button(
                                onClick = { suggestionToConfirm = null },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                border = BorderStroke(1.dp, Color(0xFFE0E6ED)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Annuler")
                            }

                            Button(
                                onClick = {
                                    onAssign(capture.id, suggestion.moleId)
                                    suggestionToConfirm = null
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Confirmer")
                            }
                        }
                    }
                }
            }
        )
    }

    if (showNewMoleDialog) {
        var newMoleName by remember { mutableStateOf("") }
        var newMoleBodyPart by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showNewMoleDialog = false },
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

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(16.dp),
                            border = BorderStroke(1.dp, Color(0xFFE0E6ED))
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                OutlinedTextField(
                                    value = newMoleName,
                                    onValueChange = { newMoleName = it },
                                    label = { Text("Nom (ex: Dos)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    singleLine = true
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                OutlinedTextField(
                                    value = newMoleBodyPart,
                                    onValueChange = { newMoleBodyPart = it },
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
                                onClick = { showNewMoleDialog = false },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                                border = BorderStroke(1.dp, Color(0xFFE0E6ED)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Annuler")
                            }

                            Button(
                                onClick = {
                                    if (newMoleName.isNotBlank()) {
                                        showNewMoleDialog = false
                                        onCreateAndAssign(
                                            capture.id,
                                            newMoleName.trim(),
                                            newMoleBodyPart.trim().ifBlank { null }
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = newMoleName.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Créer & Assigner")
                            }
                        }
                    }
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoleSelectionDialog(
    captureId: Int,
    capture: LocalCapture,
    moles: List<LocalMole>,
    onDismiss: () -> Unit,
    onMoleSelected: (LocalMole) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        modifier = Modifier.fillMaxSize().padding(16.dp),
        content = {
            Surface(
                modifier = Modifier.fillMaxSize(),
                shape = RoundedCornerShape(24.dp),
                color = Color(0xFFF5F7FA) // Fond gris médical
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Tous mes grains", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Fermer", tint = Color.Black)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(16.dp))
                            .border(1.dp, Color(0xFFE0E6ED), RoundedCornerShape(16.dp))
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("GRAIN À IDENTIFIER", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .size(140.dp) // Retour à 140dp pour un meilleur confort de comparaison
                                .clip(CircleShape)
                                .border(1.dp, Color.Black, CircleShape) // Bordure noire pour l'analyse
                        ) {
                            if (capture.croppedImagePath != null) {
                                AsyncImage(
                                    model = ImageRequest.Builder(LocalContext.current)
                                        .data(File(capture.croppedImagePath))
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    Text("SÉLECTIONNER UN MATCH", style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))

                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        items(moles) { mole ->
                            val lastCapture = mole.lastCapture ?: mole.captures.firstOrNull()

                            Card(
                                onClick = { onMoleSelected(mole) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color.White), // Cartes blanches
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color(0xFFE0E6ED))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(100.dp) // Agrandissement à 100dp
                                            .clip(CircleShape)
                                            .border(1.dp, Color(0xFF007AFF), CircleShape) // Bordure bleue pour les grains à choisir
                                    ) {
                                        if (lastCapture?.croppedImagePath != null) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(File(lastCapture.croppedImagePath))
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = null,
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF9FAFB)), contentAlignment = Alignment.Center) {
                                                Text(mole.name.take(1).uppercase(), color = Color.LightGray, style = MaterialTheme.typography.titleLarge)
                                            }
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text(mole.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(mole.bodyPart ?: "Partie inconnue", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                    }
                                    Spacer(modifier = Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowForward, contentDescription = null, tint = Color.LightGray)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        border = BorderStroke(1.dp, Color(0xFFE0E6ED)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Annuler")
                    }
                }
            }
        }
    )
}

@Composable
fun SuggestionCircleItem(
    name: String,
    score: Float?,
    croppedImagePath: String?,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(110.dp)
            .clickable { onClick() }
    ) {
        if (score != null && score > 0) {
            Surface(
                color = Color(0xFF4CAF50),
                shape = RoundedCornerShape(4.dp),
                shadowElevation = 2.dp
            ) {
                Text(
                    "${(score * 100).toInt()}%",
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
        }

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .border(1.dp, if (score != null && score > 0) Color(0xFF007AFF) else Color(0xFFE0E6ED), CircleShape)
                .background(Color(0xFFF9FAFB))
        ) {
            if (!croppedImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(File(croppedImagePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(name.take(1).uppercase(), style = MaterialTheme.typography.titleLarge, color = Color.LightGray)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
        )
    }
}
