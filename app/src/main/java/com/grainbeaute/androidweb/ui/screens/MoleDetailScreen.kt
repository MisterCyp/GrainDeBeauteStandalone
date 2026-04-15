package com.grainbeaute.androidweb.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.LocalCapture
import com.grainbeaute.androidweb.model.LocalEvolutionPoint
import com.grainbeaute.androidweb.model.LocalMole
import com.grainbeaute.androidweb.model.LocalMoleDiagnosis
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
    var moleDiagnoses by remember { mutableStateOf<List<LocalMoleDiagnosis>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var captureToDelete by remember { mutableStateOf<LocalCapture?>(null) }
    var showDeleteMoleDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadData() {
        scope.launch {
            try {
                mole = repository.getMole(moleId)
                evolution = repository.getEvolution(moleId)
                moleDiagnoses = repository.getDiagnosesForMole(moleId)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Erreur de chargement : ${e.localizedMessage ?: "erreur inconnue"}")
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(moleId) {
        loadData()
        while (true) {
            val hasPending = mole?.captures?.any { it.status == "pending" } ?: false
            delay(if (hasPending) 3000L else 10000L)
            loadData()
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
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
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
                    item { Spacer(modifier = Modifier.height(8.dp)) }

                    // 1. Photo hero
                    item {
                        MoleHeroPhoto(
                            capture = m.lastCapture,
                            onClick = {
                                m.lastCapture?.let { navController.navigate("capture_detail/${it.id}") }
                            }
                        )
                    }

                    // 2. Card état actuel
                    item {
                        MoleStatusCard(mole = m)
                    }

                    // 3. Graphe taille (mm)
                    if (evolution.filter { it.maxDimensionMm != null }.size >= 2) {
                        item {
                            EvolutionChartCard("Évolution de la taille (mm)", evolution) { it.maxDimensionMm }
                        }
                    }

                    // 4. Graphe surface (mm²)
                    if (evolution.filter { it.areaMm2 != null }.size >= 2) {
                        item {
                            EvolutionChartCard("Évolution de la surface (mm²)", evolution) { it.areaMm2 }
                        }
                    }

                    // 5. Grille de captures
                    item {
                        Text("Captures", style = MaterialTheme.typography.titleMedium)
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

                    // 6. Visites dermatologiques pour ce grain
                    if (moleDiagnoses.isNotEmpty()) {
                        item {
                            Text("Visites dermatologiques", style = MaterialTheme.typography.titleMedium)
                        }
                        items(moleDiagnoses) { diag ->
                            MoleDiagnosisVisitCard(
                                diagnosis = diag,
                                onClick = { navController.navigate("visit_detail/${diag.visitId}") }
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(80.dp)) }
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
                                snackbarHostState.showSnackbar("Erreur lors de la suppression : ${e.localizedMessage ?: "erreur inconnue"}")
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
                                snackbarHostState.showSnackbar("Erreur lors de la suppression : ${e.localizedMessage ?: "erreur inconnue"}")
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

// ─────────────────────────────────────────────────────────────
// Composables privés
// ─────────────────────────────────────────────────────────────

@Composable
private fun MoleHeroPhoto(capture: LocalCapture?, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(CircleShape)
            .border(1.dp, Color.Black, CircleShape)
            .background(Color(0xFFEEEEEE))
            .clickable(enabled = capture != null && capture.status == "done") { onClick() },
        contentAlignment = Alignment.Center
    ) {
        when {
            capture == null || (capture.croppedImagePath == null && capture.status != "pending") -> {
                Icon(
                    Icons.Default.CameraAlt,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color.Gray
                )
            }
            capture.status == "pending" -> {
                CircularProgressIndicator()
            }
            else -> {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(File(capture.croppedImagePath ?: ""))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }
}

@Composable
private fun MoleStatusCard(mole: LocalMole) {
    val dateFormatter = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E6ED)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Statut
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Statut",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.width(110.dp)
                )
                mole.latestDiagnosis?.let { DiagnosisBadge(it.category) }
                    ?: DiagnosisToDiagnoseBadge()
            }
            // Dernière photo
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Dernière photo",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.width(110.dp)
                )
                Text(
                    text = mole.lastCapture?.let { dateFormatter.format(Date(it.createdAt)) }
                        ?: "Aucune photo",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            // Dernière visite
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    "Dernière visite",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.width(110.dp)
                )
                val diag = mole.latestDiagnosis
                if (diag != null && diag.visitDate > 0L) {
                    Column {
                        val visitLine = buildString {
                            append(dateFormatter.format(Date(diag.visitDate)))
                            diag.visitPractitionerName?.let { append(" · $it") }
                        }
                        Text(visitLine, style = MaterialTheme.typography.bodyMedium)
                        diag.note?.let {
                            Text(
                                it,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.Gray,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else {
                    Text(
                        "Aucune visite",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            }
            // Taille actuelle
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Taille actuelle",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.Gray,
                    modifier = Modifier.width(110.dp)
                )
                val taille = mole.lastCapture?.analysisResult?.maxDimensionMm
                Text(
                    text = if (taille != null) String.format(Locale.US, "%.1f mm", taille) else "—",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun MoleDiagnosisVisitCard(diagnosis: LocalMoleDiagnosis, onClick: () -> Unit) {
    val dateFormatter = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E6ED)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val dateLine = buildString {
                    append(dateFormatter.format(Date(diagnosis.visitDate)))
                    diagnosis.visitPractitionerName?.let { append(" · $it") }
                }
                Text(
                    dateLine,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                diagnosis.note?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            DiagnosisBadge(diagnosis.category)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Graphes d'évolution (Améliorés)
// ─────────────────────────────────────────────────────────────

@Composable
private fun EvolutionChartCard(
    title: String, 
    points: List<LocalEvolutionPoint>, 
    valueSelector: (LocalEvolutionPoint) -> Float?
) {
    val validPoints = points.filter { valueSelector(it) != null }.sortedBy { it.date }
    if (validPoints.size < 2) return

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE0E6ED)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(16.dp))
            EvolutionChart(validPoints, valueSelector)
        }
    }
}

@Composable
private fun EvolutionChart(points: List<LocalEvolutionPoint>, valueSelector: (LocalEvolutionPoint) -> Float?) {
    val values = points.map { valueSelector(it)!! }
    val dates = points.map { it.date }
    
    val minDate = dates.minOrNull() ?: 0L
    val maxDate = dates.maxOrNull() ?: 1L
    val dateRange = (maxDate - minDate).coerceAtLeast(1L)
    
    val minValue = values.minOrNull() ?: 0f
    val maxValue = values.maxOrNull() ?: 1f
    val valueRange = (maxValue - minValue).coerceAtLeast(0.1f)
    
    val yMin = (minValue - valueRange * 0.15f).coerceAtLeast(0f)
    val yMax = maxValue + valueRange * 0.15f
    val yRange = yMax - yMin

    Canvas(modifier = Modifier.height(160.dp).fillMaxWidth()) {
        val width = size.width
        val height = size.height
        val marginB = 30.dp.toPx()
        val marginT = 20.dp.toPx()
        val marginL = 35.dp.toPx()
        val marginR = 15.dp.toPx()
        val chartH = height - marginB - marginT
        val chartW = width - marginL - marginR

        fun getX(date: Long) = marginL + ((date - minDate).toFloat() / dateRange.toFloat()) * chartW
        fun getY(value: Float) = marginT + chartH - ((value - yMin) / yRange) * chartH

        val gridColor = Color.LightGray.copy(alpha = 0.2f)
        val paintY = android.graphics.Paint().apply {
            color = android.graphics.Color.LTGRAY
            textSize = 9.dp.toPx()
            textAlign = android.graphics.Paint.Align.RIGHT
        }
        listOf(yMin, (yMin + yMax) / 2, yMax).forEach { v ->
            val y = getY(v)
            drawLine(gridColor, Offset(marginL, y), Offset(marginL + chartW, y), 1.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", v), marginL - 6.dp.toPx(), y + 3.dp.toPx(), paintY)
        }

        val path = Path()
        val fillPath = Path()
        
        points.forEachIndexed { i, pt ->
            val x = getX(pt.date)
            val y = getY(valueSelector(pt)!!)
            if (i == 0) {
                path.moveTo(x, y)
                fillPath.moveTo(x, getY(yMin))
                fillPath.lineTo(x, y)
            } else {
                val prevPt = points[i-1]
                val prevX = getX(prevPt.date)
                val prevY = getY(valueSelector(prevPt)!!)
                val cx1 = prevX + (x - prevX) / 2
                val cy1 = prevY
                val cx2 = prevX + (x - prevX) / 2
                val cy2 = y
                path.cubicTo(cx1, cy1, cx2, cy2, x, y)
                fillPath.cubicTo(cx1, cy1, cx2, cy2, x, y)
            }
            if (i == points.size - 1) {
                fillPath.lineTo(x, getY(yMin))
                fillPath.close()
            }
        }

        drawPath(path = fillPath, brush = Brush.verticalGradient(colors = listOf(MedicalBlue.copy(alpha = 0.3f), Color.Transparent), startY = getY(yMax), endY = getY(yMin)))
        drawPath(path = path, color = MedicalBlue, style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))

        val textPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.DKGRAY
            textSize = 10.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
        }
        val datePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.GRAY
            textSize = 8.dp.toPx()
            textAlign = android.graphics.Paint.Align.CENTER
        }

        points.forEach { pt ->
            val x = getX(pt.date)
            val v = valueSelector(pt)!!
            val y = getY(v)
            drawCircle(Color.White, radius = 5.dp.toPx(), center = Offset(x, y))
            drawCircle(MedicalBlue, radius = 5.dp.toPx(), center = Offset(x, y), style = Stroke(2.dp.toPx()))
            drawContext.canvas.nativeCanvas.drawText(String.format(Locale.US, "%.1f", v), x, y - 10.dp.toPx(), textPaint)
            val dateStr = SimpleDateFormat("dd/MM", Locale.FRANCE).format(Date(pt.date))
            drawContext.canvas.nativeCanvas.drawText(dateStr, x, height - 5.dp.toPx(), datePaint)
        }
    }
}

// ─────────────────────────────────────────────────────────────
// Grille de captures (inchangée)
// ─────────────────────────────────────────────────────────────

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
            when (capture.status) {
                "pending" -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
                "error" -> {
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
                }
                else -> {
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
        }

        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = formatSmartDate(capture.createdAt),
            color = Color.DarkGray,
            style = MaterialTheme.typography.labelSmall
        )
    }
}
