package com.grainbeaute.androidweb.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import android.media.MediaActionSound
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.FilterCenterFocus
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.grainbeaute.androidweb.data.CalibrationManager
import com.grainbeaute.androidweb.data.LocalRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CameraScreen(navController: NavController, moleId: Int, repository: LocalRepository) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor: ExecutorService = remember { Executors.newSingleThreadExecutor() }
    val calibrationManager = remember { CalibrationManager(context) }

    // State for flash/torch (default to ON)
    var isFlashOn by remember { mutableStateOf(true) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var cameraInfo by remember { mutableStateOf<CameraInfo?>(null) }
    var previewViewRef by remember { mutableStateOf<PreviewView?>(null) }
    var showFocusCircle by remember { mutableStateOf(false) }

    // Exposure control
    var exposureIndex by remember { mutableStateOf(calibrationManager.exposureIndex) }
    var showExposureSlider by remember { mutableStateOf(false) }

    // Shutter effect states
    var showVisualFlash by remember { mutableStateOf(false) }
    val shutterSound = remember { MediaActionSound().apply { load(MediaActionSound.SHUTTER_CLICK) } }
    val flashOpacity by animateFloatAsState(
        targetValue = if (showVisualFlash) 1f else 0f,
        animationSpec = tween(durationMillis = 100),
        finishedListener = { if (it == 1f) showVisualFlash = false }
    )

    // Calibration lue depuis les préférences
    val calX = calibrationManager.centerX
    val calY = calibrationManager.centerY

    val imageCapture: ImageCapture = remember {
        ImageCapture.Builder()
            .setFlashMode(ImageCapture.FLASH_MODE_OFF)
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setJpegQuality(100)
            .setTargetResolution(android.util.Size(4000, 3000))
            .build()
    }

    LaunchedEffect(isFlashOn) {
        // Pour la dermoscopie, la lumière continue (Torch) est préférable au flash (burst)
        // car elle évite les écarts d'exposition entre la prévisualisation et la capture.
        imageCapture.flashMode = ImageCapture.FLASH_MODE_OFF
        cameraControl?.enableTorch(isFlashOn)
    }

    LaunchedEffect(exposureIndex, cameraControl) {
        cameraControl?.setExposureCompensationIndex(exposureIndex.toInt())
    }

    val scope = rememberCoroutineScope()
    var capturedImageFile by remember { mutableStateOf<File?>(null) }
    var photoCount by remember { mutableStateOf(0) }
    var uploadingCount by remember { mutableStateOf(0) }
    var showSuccessBanner by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    fun startBackgroundUpload(file: File) {
        if (isUploading) return
        isUploading = true
        uploadingCount++

        if (moleId == -1) {
            // Mode rafale : retour immédiat à la caméra
            capturedImageFile = null
            scope.launch {
                try {
                    repository.uploadCapture(moleId = null, sourceFile = file)
                    photoCount++
                    capturedImageFile = null
                    showSuccessBanner = true
                    delay(1500)
                    showSuccessBanner = false
                } catch (e: Exception) {
                    // silencieux — l'analyse se fait en background
                } finally {
                    isUploading = false
                    uploadingCount--
                }
            }
        } else {
            scope.launch {
                try {
                    val capture = repository.uploadCapture(moleId = moleId, sourceFile = file)
                    navController.navigate("capture_detail/${capture.id}")
                } catch (e: Exception) {
                    // gérer l'erreur si nécessaire
                } finally {
                    isUploading = false
                    uploadingCount--
                }
            }
        }
    }

    // Fonction pour forcer le focus sur la zone calibrée
    fun triggerCalibratedFocus() {
        val pView = previewViewRef ?: return
        val control = cameraControl ?: return
        val factory = pView.meteringPointFactory
        val point = factory.createPoint(pView.width * calX, pView.height * calY)
        
        // On effectue un focus manuel, une mesure d'exposition et de balance des blancs sur le point calibré.
        // L'autofocus continu reprendra après 5 secondes d'inactivité.
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE or FocusMeteringAction.FLAG_AWB)
            .setAutoCancelDuration(5, TimeUnit.SECONDS)
            .build()
        
        scope.launch {
            showFocusCircle = true
            control.startFocusAndMetering(action)
            delay(1000)
            showFocusCircle = false
        }
    }

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted -> hasCameraPermission = granted }
    )

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            launcher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("L'accès à la caméra est requis")
        }
        return
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { _ ->
    Box(modifier = Modifier.fillMaxSize()) {
        // Preview de la caméra
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture
                        )
                        cameraControl = camera.cameraControl
                        cameraInfo = camera.cameraInfo
                        cameraControl?.enableTorch(isFlashOn)
                        previewViewRef = previewView
                    } catch (exc: Exception) {
                        scope.launch {
                            snackbarHostState.showSnackbar("Erreur caméra : ${exc.localizedMessage}")
                        }
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Focus Indicator (placé selon la calibration)
        if (showFocusCircle) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .align(Alignment { size, space, _ ->
                        androidx.compose.ui.unit.IntOffset(
                            (space.width * calX - size.width / 2).toInt(),
                            (space.height * calY - size.height / 2).toInt()
                        )
                    })
                    .border(2.dp, Color.White.copy(alpha = 0.5f), CircleShape)
            )
        }

        // Overlay de confirmation
        capturedImageFile?.let { imgFile ->
            val bitmap = remember(imgFile) {
                val bmp = BitmapFactory.decodeFile(imgFile.absolutePath) ?: return@remember null
                val exif = ExifInterface(imgFile.absolutePath)
                val degrees = when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
                if (degrees != 0f) {
                    val matrix = Matrix().apply { postRotate(degrees) }
                    Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true).asImageBitmap()
                } else {
                    bmp.asImageBitmap()
                }
            }

            val zoomInitial = 2.0f
            var scale by remember { mutableStateOf(zoomInitial) }
            var offset by remember { mutableStateOf(Offset.Zero) }
            val state = rememberTransformableState { zoomChange, offsetChange, _ ->
                scale = (scale * zoomChange).coerceIn(1f, 5f)
                offset += offsetChange
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                val screenWidth = constraints.maxWidth.toFloat()
                val screenHeight = constraints.maxHeight.toFloat()

                // Centrage initial sur la zone de calibration
                LaunchedEffect(imgFile) {
                    scale = zoomInitial
                    offset = Offset(
                        (0.5f - calX) * screenWidth * zoomInitial,
                        (0.5f - calY) * screenHeight * zoomInitial
                    )
                }

                bitmap?.let {
                    Image(
                        bitmap = it,
                        contentDescription = null,
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
                
                // Instructions de zoom
                Text(
                    "Pincez pour zoomer / Glissez pour déplacer",
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 60.dp),
                    color = Color.White.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )

                Row(
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(onClick = { 
                        if (imgFile.exists()) imgFile.delete()
                        capturedImageFile = null 
                    }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))) {
                        Icon(Icons.Default.Refresh, null, tint = Color.White)
                        Text("Reprendre", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    }
                    Button(onClick = {
                        startBackgroundUpload(imgFile)
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.Check, null)
                        Text("Envoyer", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            return@Scaffold
        }

        // Interface de capture
        Box(modifier = Modifier.fillMaxSize()) {
            // Zone de détection de clic pour fermer le curseur d'exposition
            if (showExposureSlider) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { showExposureSlider = false })
                        }
                )
            }

            // BOUTON CALIBRATION (Haut Droite)
            IconButton(
                onClick = { navController.navigate("calibration") },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Settings, 
                    contentDescription = "Calibration", 
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }

            // BOUTONS ACTIONS (Bas Droite)
            val actionsModifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 120.dp)
            Column(
                modifier = actionsModifier,
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Focus Button (déclenche manuellement le focus calibré)
                IconButton(
                    onClick = { triggerCalibratedFocus() },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.FilterCenterFocus, 
                        contentDescription = "Focus", 
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Flash Button
                IconButton(
                    onClick = { isFlashOn = !isFlashOn },
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff, 
                        contentDescription = "Flash",
                        tint = if (isFlashOn) Color.Yellow else Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                // Exposure Button
                if (cameraInfo?.exposureState?.isExposureCompensationSupported == true) {
                    IconButton(
                        onClick = { showExposureSlider = !showExposureSlider },
                        modifier = Modifier
                            .size(56.dp)
                            .background(if (showExposureSlider) Color.Yellow.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.6f), CircleShape)
                    ) {
                        Icon(
                            Icons.Default.Brightness6, 
                            contentDescription = "Exposition", 
                            tint = if (showExposureSlider) Color.Black else Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Bandeau Bas (Shutter + Compteur)
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            ) {
                // Exposure Slider
                if (showExposureSlider && cameraInfo != null) {
                    val range = cameraInfo!!.exposureState.exposureCompensationRange
                    if (range.lower != range.upper) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 32.dp, vertical = 8.dp)
                                .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                .padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "Exposition : ${if(exposureIndex > 0) "+" else ""}${exposureIndex.toInt()}", 
                                color = Color.White, 
                                style = MaterialTheme.typography.labelMedium
                            )
                            Slider(
                                value = exposureIndex,
                                onValueChange = { 
                                    exposureIndex = it
                                    calibrationManager.exposureIndex = it
                                },
                                valueRange = range.lower.toFloat()..range.upper.toFloat(),
                                steps = if (range.upper - range.lower > 1) range.upper - range.lower - 1 else 0,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                IconButton(
                    onClick = {
                        if (showExposureSlider) {
                            showExposureSlider = false
                        } else {
                            // Effets de capture (son et flash visuel)
                            shutterSound.play(MediaActionSound.SHUTTER_CLICK)
                            showVisualFlash = true

                            scope.launch {
                                // Petit délai pour laisser le temps au système AE de se stabiliser
                                // si des changements ont eu lieu juste avant
                                delay(400)
                                val file = File(context.cacheDir, "capture_${System.currentTimeMillis()}.jpg")
                                imageCapture.takePicture(
                                    ImageCapture.OutputFileOptions.Builder(file).build(),
                                    cameraExecutor,
                                    object : ImageCapture.OnImageSavedCallback {
                                        override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                            scope.launch {
                                                capturedImageFile = file
                                            }
                                        }
                                        override fun onError(exc: ImageCaptureException) {}
                                    }
                                )
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 16.dp).size(80.dp)
                ) {
                    Icon(Icons.Default.CameraAlt, null, modifier = Modifier.size(64.dp), tint = Color.White)
                }

                Row(
                    modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.7f)).padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (photoCount == 0 && uploadingCount == 0) {
                        IconButton(onClick = { navController.popBackStack() }) { 
                            Icon(Icons.Default.Close, null, tint = Color.White) 
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                    
                    val statusText = when {
                        uploadingCount > 0 -> "Envoi de $uploadingCount photo${if(uploadingCount>1) "s" else ""}..."
                        photoCount > 0 -> "$photoCount photo${if(photoCount>1) "s" else ""} envoyée${if(photoCount>1) "s" else ""}"
                        moleId == -1 -> "Mode rafale"
                        else -> "Ajout d'analyses"
                    }
                    
                    Text(statusText, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, color = Color.White)
                    
                    if (photoCount > 0 || uploadingCount > 0) {
                        IconButton(
                            onClick = { navController.popBackStack() },
                            enabled = uploadingCount == 0
                        ) { 
                            if (uploadingCount > 0) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp, color = Color.White)
                            } else {
                                Icon(Icons.Default.Check, null, tint = Color.White)
                            }
                        }
                    } else {
                        Spacer(modifier = Modifier.size(48.dp))
                    }
                }
            }
        }

        // Flash visuel blanc
        if (flashOpacity > 0f) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRect(color = Color.White, alpha = flashOpacity)
            }
        }
    }
}
}
