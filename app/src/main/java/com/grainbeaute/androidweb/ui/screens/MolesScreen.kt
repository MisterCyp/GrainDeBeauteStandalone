package com.grainbeaute.androidweb.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.grainbeaute.androidweb.model.LocalMole
import com.grainbeaute.androidweb.ui.theme.CardBorder
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MolesScreen(navController: NavController, repository: LocalRepository) {
    var moles by remember { mutableStateOf<List<LocalMole>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var showBodyMapPicker by remember { mutableStateOf(false) }
    var speedDialOpen by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadMoles() {
        scope.launch {
            isLoading = true
            moles = repository.getMoles()
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        loadMoles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mes Grains", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold) },
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
                    showBodyMapPicker = true
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (moles.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Spa, contentDescription = null, modifier = Modifier.size(64.dp), tint = Color.LightGray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Aucun grain enregistré", color = Color.Gray)
                    TextButton(onClick = { showBodyMapPicker = true }) {
                        Text("Ajouter mon premier grain")
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(top = 16.dp, bottom = 80.dp)
                ) {
                    items(moles) { mole ->
                        MoleCard(
                            mole = mole,
                            onClick = { navController.navigate("mole_detail/${mole.id}") }
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

    if (showBodyMapPicker) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showBodyMapPicker = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            BodyMapPicker(
                onConfirm = { position ->
                    scope.launch {
                        repository.createMoleWithPosition(position)
                        loadMoles()
                        showBodyMapPicker = false
                    }
                },
                onDismiss = { showBodyMapPicker = false },
            )
        }
    }
}
