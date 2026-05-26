package com.grainbeaute.androidweb.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.LocalCapture
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun LocalCaptureImage(
    path: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit
) {
    if (path == null) return
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(File(path))
            .crossfade(true)
            .build(),
        contentDescription = null,
        modifier = modifier,
        contentScale = contentScale,
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CaptureDetailScreen(navController: NavController, captureId: Int, repository: LocalRepository) {
    var captures by remember { mutableStateOf<List<LocalCapture>>(emptyList()) }
    var isLoadingMole by remember { mutableStateOf(true) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    fun loadCaptures() {
        scope.launch {
            isLoadingMole = true
            try {
                val initialCapture = repository.getCapture(captureId)
                if (initialCapture != null) {
                    val moleId = initialCapture.moleId
                    if (moleId != null) {
                        val mole = repository.getMole(moleId)
                        captures = mole?.captures?.sortedByDescending { it.createdAt } ?: listOf(initialCapture)
                    } else {
                        captures = listOf(initialCapture)
                    }
                } else {
                    captures = emptyList()
                }
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Erreur de chargement : ${e.localizedMessage ?: "erreur inconnue"}")
            } finally {
                isLoadingMole = false
            }
        }
    }

    LaunchedEffect(captureId) {
        loadCaptures()
    }

    if (isLoadingMole && captures.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else if (captures.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Aucune capture trouvée")
        }
    } else {
        val initialPage = remember(captures) {
            val index = captures.indexOfFirst { it.id == captureId }
            if (index != -1) index else 0
        }
        val pagerState = rememberPagerState(initialPage = initialPage, pageCount = { captures.size })
        var isZoomed by remember { mutableStateOf(false) }

        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                TopAppBar(
                    title = { Text("Analyse du grain") },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Retour")
                        }
                    },
                    actions = {
                        if (!isZoomed) {
                            IconButton(onClick = { showDeleteDialog = true }) {
                                Icon(Icons.Default.Delete, contentDescription = "Supprimer l'analyse")
                            }
                        }
                    }
                )
            },
            bottomBar = {
                if (captures.size > 1 && !isZoomed) {
                    CaptureNavigationBottomBar(
                        captures = captures,
                        currentPage = pagerState.currentPage,
                        onPageSelected = { page ->
                            scope.launch { pagerState.animateScrollToPage(page) }
                        }
                    )
                }
            }
        ) { padding ->
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = !isZoomed,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) { page ->
                CapturePageContent(
                    initialCapture = captures[page],
                    repository = repository,
                    snackbarHostState = snackbarHostState,
                    onZoomStateChanged = { isZoomed = it }
                )
            }
        }

        if (showDeleteDialog) {
            val currentCapture = captures[pagerState.currentPage]
            AlertDialog(
                onDismissRequest = { showDeleteDialog = false },
                title = { Text("Supprimer cette analyse ?") },
                text = {
                    Text(
                        "Voulez-vous vraiment supprimer l'analyse du ${
                            SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                .format(Date(currentCapture.createdAt))
                        } ?"
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                isDeleting = true
                                try {
                                    repository.deleteCapture(currentCapture.id)
                                    showDeleteDialog = false
                                    navController.popBackStack()
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar("Erreur lors de la suppression")
                                } finally {
                                    isDeleting = false
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        enabled = !isDeleting
                    ) {
                        if (isDeleting) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        } else {
                            Text("Supprimer")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = false }, enabled = !isDeleting) {
                        Text("Annuler")
                    }
                }
            )
        }
    }
}

@Composable
fun CapturePageContent(
    initialCapture: LocalCapture,
    repository: LocalRepository,
    snackbarHostState: SnackbarHostState,
    onZoomStateChanged: (Boolean) -> Unit
) {
    var capture by remember(initialCapture.id) { mutableStateOf(initialCapture) }
    var isLoading by remember(initialCapture.id) { mutableStateOf(initialCapture.status == "pending") }
    var retryKey by remember { mutableStateOf(0) }
    var isRetrying by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var expandedImagePath by remember { mutableStateOf<String?>(null) }
    var showAnalyzed by remember { mutableStateOf(false) }

    LaunchedEffect(expandedImagePath) {
        onZoomStateChanged(expandedImagePath != null)
    }

    LaunchedEffect(initialCapture.id, retryKey) {
        isLoading = true
        try {
            var pollAttempts = 0
            while (pollAttempts < 90) {
                val c = repository.getCapture(initialCapture.id)
                capture = c ?: capture
                if (c == null || c.status != "pending") break
                delay(2000)
                pollAttempts++
            }
            if (capture.status == "pending") {
                snackbarHostState.showSnackbar("Analyse toujours en cours — vérifiez Logcat (tag: GrainAnalysis)")
            }
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Erreur polling : ${e.localizedMessage}")
        } finally {
            isLoading = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isLoading && capture.status == "pending") {
                Text("Analyse en cours...", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(16.dp))
                CircularProgressIndicator()
            } else if (capture.status == "error") {
                Text("Échec de l'analyse", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                LocalCaptureImage(
                    path = capture.imagePath,
                    modifier = Modifier
                        .size(300.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.Black, CircleShape)
                        .clickable { expandedImagePath = capture.imagePath },
                    contentScale = ContentScale.Fit
                )
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Erreur (vérifiez Logcat → tag: GrainAnalysis)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            capture.errorMessage ?: "Erreur inconnue",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = {
                        scope.launch {
                            isRetrying = true
                            try {
                                repository.reanalyzeCapture(capture.id)
                                capture = capture.copy(status = "pending")
                                retryKey++
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(
                                    "Impossible de relancer : ${e.localizedMessage ?: "erreur inconnue"}"
                                )
                            } finally {
                                isRetrying = false
                            }
                        }
                    },
                    enabled = !isRetrying,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isRetrying) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(if (isRetrying) "Relance en cours…" else "Relancer l'analyse")
                }
            } else {
                Text(
                    if (showAnalyzed) "Vue Analysée (Contours)" else "Vue Réelle",
                    style = MaterialTheme.typography.titleSmall,
                    color = if (showAnalyzed) MaterialTheme.colorScheme.primary else Color.Gray
                )

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .size(320.dp)
                        .clip(CircleShape)
                        .border(1.dp, Color.Black, CircleShape)
                        .clickable {
                            expandedImagePath = if (showAnalyzed) capture.analyzedImagePath else capture.croppedImagePath
                        }
                ) {
                    LocalCaptureImage(
                        path = capture.croppedImagePath,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (showAnalyzed) 0f else 1f },
                        contentScale = ContentScale.Fit
                    )
                    LocalCaptureImage(
                        path = capture.analyzedImagePath,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = if (showAnalyzed) 1f else 0f },
                        contentScale = ContentScale.Fit
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAnalyzed = !showAnalyzed },
                    colors = CardDefaults.cardColors(
                        containerColor = if (showAnalyzed) Color(0xFFE3F2FD) else Color.White
                    ),
                    border = BorderStroke(1.dp, if (showAnalyzed) MaterialTheme.colorScheme.primary else Color(0xFFE0E6ED))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Métriques ABCDE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Surface(
                                color = if (showAnalyzed) MaterialTheme.colorScheme.primary else Color.LightGray,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    if (showAnalyzed) "ANALYSÉ" else "NORMAL",
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        capture.analysisResult?.let { res ->
                            MetricRow("Surface", "${String.format("%.2f", res.areaMm2)} mm²")
                            MetricRow("Dimension Max", "${String.format("%.2f", res.maxDimensionMm)} mm")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Méthode: ${res.methodUsed}", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                        } ?: Text("Pas de métriques disponibles")

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Cliquez ici pour basculer l'affichage",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }
            }
        }

        expandedImagePath?.let { path ->
            FullScreenZoomOverlay(
                imagePath = path,
                onDismiss = { expandedImagePath = null }
            )
        }
    }
}

@Composable
fun FullScreenZoomOverlay(
    imagePath: String,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color.Black.copy(alpha = 0.95f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { onDismiss() })
                }
        ) {
            ZoomableAsyncImage(
                file = File(imagePath),
                contentDescription = "Zoom",
                modifier = Modifier.fillMaxSize()
            )

            IconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Fermer",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                "Pincez pour zoomer / Glissez pour déplacer\nCliquez n'importe où pour fermer",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp)
            )
        }
    }
}

@Composable
fun ZoomableAsyncImage(
    file: File,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableStateOf(1f) }
    var offset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
    val state = rememberTransformableState { zoomChange, offsetChange, _ ->
        scale = (scale * zoomChange).coerceIn(1f, 5f)
        offset += offsetChange
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(file)
                .size(coil.size.Size.ORIGINAL)
                .crossfade(true)
                .build(),
            contentDescription = contentDescription,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                )
                .transformable(state = state),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
fun CaptureNavigationBottomBar(
    captures: List<LocalCapture>,
    currentPage: Int,
    onPageSelected: (Int) -> Unit
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onPageSelected(currentPage - 1) },
                enabled = currentPage > 0
            ) {
                Icon(
                    Icons.Default.KeyboardArrowLeft,
                    contentDescription = "Précédent",
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                    .format(Date(captures[currentPage].createdAt)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = { onPageSelected(currentPage + 1) },
                enabled = currentPage < captures.size - 1
            ) {
                Icon(
                    Icons.Default.KeyboardArrowRight,
                    contentDescription = "Suivant",
                    modifier = Modifier.size(32.dp)
                )
            }
        }
    }
}

@Composable
fun MetricRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.titleMedium)
    }
}
