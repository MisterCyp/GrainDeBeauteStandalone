package com.grainbeaute.androidweb.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.LocalCapture
import com.grainbeaute.androidweb.model.LocalEvolutionPoint
import com.grainbeaute.androidweb.model.LocalMole
import com.grainbeaute.androidweb.ui.theme.MedicalBlue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun formatSmartDate(timestamp: Long): String {
    return try {
        SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date(timestamp))
    } catch (e: Exception) {
        timestamp.toString()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoleDetailScreen(navController: NavController, moleId: Int, repository: LocalRepository) {
    var mole by remember { mutableStateOf<LocalMole?>(null) }
    var evolution by remember { mutableStateOf<List<LocalEvolutionPoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var captureToDelete by remember { mutableStateOf<LocalCapture?>(null) }
    var showDeleteMoleDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var errorShown by remember { mutableStateOf(false) }

    fun loadData() {
        scope.launch {
            try {
                mole = repository.getMole(moleId)
                evolution = repository.getEvolution(moleId)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Erreur de chargement : ${e.localizedMessage ?: "erreur réseau"}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(moleId) {
        loadData()
        while (true) {
            val hasPending = mole?.captures?.any { it.status == "pending" } ?: false
            if (hasPending) {
                delay(3000)
                loadData()
            } else {
                delay(10000)
            }
            val hasCurrentError = mole?.captures?.any { it.status == "error" } ?: false
            if (!hasCurrentError) errorShown = false
            if (hasCurrentError && !errorShown) {
                errorShown = true
                scope.launch {
                    snackbarHostState.showSnackbar("Une analyse a échoué. Vérifiez que la pièce 3D est bien en place.")
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(mole?.name ?: "Détail") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteMoleDialog = true }) {
                        Icon(Icons.Default.Delete, contentDescription = "Supprimer le grain")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { navController.navigate("camera/$moleId") }) {
                Icon(Icons.Default.CameraAlt, contentDescription = "Prendre photo")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        if (isLoading && mole == null) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            mole?.let { m ->
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Analyses", style = MaterialTheme.typography.titleMedium)
                    }

                    item {
                        val captures = m.captures.sortedByDescending { it.createdAt }
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            captures.chunked(3).forEach { rowCaptures ->
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    rowCaptures.forEach { capture ->
                                        CaptureGridItem(
                                            capture = capture,
                                            modifier = Modifier.weight(1f),
                                            onClick = { navController.navigate("capture_detail/${capture.id}") },
                                            onDelete = { captureToDelete = capture }
                                        )
                                    }
                                    repeat(3 - rowCaptures.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }

                    if (evolution.filter { it.areaMm2 != null }.size >= 2) {
                        item {
                            Divider(modifier = Modifier.padding(vertical = 8.dp))
                            Text("Evolution temporelle", style = MaterialTheme.typography.titleLarge)
                        }

                        item {
                            EvolutionChartCard("Surface (mm²)", evolution.mapNotNull { it.areaMm2?.toDouble() })
                        }
                        item {
                            EvolutionChartCard("Dimension Max (mm)", evolution.mapNotNull { it.maxDimensionMm?.toDouble() })
                        }
                        item {
                            EvolutionChartCard("Asymétrie", evolution.mapNotNull { it.asymmetry?.toDouble() })
                        }
                        item {
                            EvolutionChartCard("Circularité", evolution.mapNotNull { it.circularity?.toDouble() })
                        }
                        item {
                            EvolutionChartCard("Variation Couleur", evolution.mapNotNull { it.colorVariation?.toDouble() })
                        }
                    }

                    item { Spacer(modifier = Modifier.height(32.dp)) }
                }
            }
        }
    }

    if (showDeleteMoleDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteMoleDialog = false },
            title = { Text("Supprimer le grain ?") },
            text = { Text("Ceci supprimera définitivement '${mole?.name}' et tout son historique.") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                repository.deleteMole(moleId)
                                navController.popBackStack()
                            } catch (e: Exception) {
                                showDeleteMoleDialog = false
                                snackbarHostState.showSnackbar("Erreur lors de la suppression : ${e.localizedMessage ?: "erreur réseau"}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteMoleDialog = false }) { Text("Annuler") }
            }
        )
    }

    captureToDelete?.let { capture ->
        AlertDialog(
            onDismissRequest = { captureToDelete = null },
            title = { Text("Supprimer la capture ?") },
            text = { Text("Voulez-vous supprimer cette capture du ${formatSmartDate(capture.createdAt)} ?") },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            try {
                                repository.deleteCapture(capture.id)
                                loadData()
                                captureToDelete = null
                            } catch (e: Exception) {
                                captureToDelete = null
                                snackbarHostState.showSnackbar("Erreur lors de la suppression : ${e.localizedMessage ?: "erreur réseau"}")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Supprimer") }
            },
            dismissButton = {
                TextButton(onClick = { captureToDelete = null }) { Text("Annuler") }
            }
        )
    }
}

@Composable
fun EvolutionChartCard(title: String, values: List<Double>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFFE0E0E0)))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.Gray)
            Spacer(modifier = Modifier.height(8.dp))
            EvolutionChart(values)
        }
    }
}

@Composable
fun EvolutionChart(values: List<Double>) {
    if (values.size < 2) return

    Canvas(modifier = Modifier.height(140.dp).fillMaxWidth()) {
        val width = size.width
        val height = size.height
        val minV = values.minOrNull() ?: 0.0
        val maxV = values.maxOrNull() ?: 1.0
        val range = (maxV - minV).coerceAtLeast(0.001)

        val stepX = width / (values.size - 1)

        drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, 20.dp.toPx()), Offset(width, 20.dp.toPx()), 1f)
        drawLine(Color.LightGray.copy(alpha = 0.5f), Offset(0f, height - 20.dp.toPx()), Offset(width, height - 20.dp.toPx()), 1f)

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 10.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }

        for (i in 0 until values.size) {
            val x = i * stepX
            val normalizedValue = ((values[i] - minV).toFloat() / range.toFloat())
            val y = height - (normalizedValue * (height - 40.dp.toPx()) + 20.dp.toPx())
            val point = Offset(x, y)

            if (i < values.size - 1) {
                val nextNormalized = ((values[i+1] - minV).toFloat() / range.toFloat())
                val nextY = height - (nextNormalized * (height - 40.dp.toPx()) + 20.dp.toPx())
                drawLine(color = MedicalBlue, start = point, end = Offset((i + 1) * stepX, nextY), strokeWidth = 3.dp.toPx())
            }

            drawCircle(color = MedicalBlue, radius = 5.dp.toPx(), center = point)

            // Draw value text (rounded to 1 decimal)
            drawContext.canvas.nativeCanvas.drawText(
                String.format(Locale.US, "%.1f", values[i]),
                x,
                y - 10.dp.toPx(),
                textPaint
            )
        }
    }
}

@Composable
fun CaptureGridItem(
    capture: LocalCapture,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = modifier.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .border(1.dp, Color.Black, CircleShape)
                .background(Color(0xFFEEEEEE))
        ) {
            if (capture.status == "pending") {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            } else if (capture.status == "error") {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(capture.croppedImagePath ?: capture.imagePath))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Red.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("✗", color = Color.White, style = MaterialTheme.typography.labelMedium)
                }
            } else {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(capture.croppedImagePath?.let { File(it) })
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = formatSmartDate(capture.createdAt),
            color = Color.DarkGray,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
