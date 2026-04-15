package com.grainbeaute.androidweb.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.grainbeaute.androidweb.R
import com.grainbeaute.androidweb.model.BodyPosition
import com.grainbeaute.androidweb.model.BodyZones

/**
 * Retourne le drawable VectorDrawable correspondant à la combinaison genre × face.
 * gender = "female" | "male", face = "front" | "back".
 */
fun bodyDrawableRes(gender: String, face: String): Int = when {
    gender == "male" && face == "back"  -> R.drawable.ic_body_man_back
    gender == "male"                    -> R.drawable.ic_body_man_front
    face   == "back"                    -> R.drawable.ic_body_woman_back
    else                                -> R.drawable.ic_body_woman_front
}

/**
 * Sélecteur interactif de position sur un corps humain.
 *
 * - Toggle Face / Dos : changer de face efface le marqueur courant.
 * - Pinch-to-zoom : scale 1×→4×, translation clampée aux bords.
 * - Tap : place un marqueur rouge, déduit la zone anatomique.
 * - Bouton "Créer" toujours actif (position optionnelle).
 *
 * @param initialPosition position pré-remplie (mode édition)
 * @param confirmLabel label du bouton de confirmation
 * @param onConfirm   appelé avec la BodyPosition finale, ou null si aucune
 * @param onDismiss   appelé sur "Annuler"
 */
@Composable
fun BodyMapPicker(
    initialPosition: BodyPosition? = null,
    confirmLabel: String = "Créer",
    gender: String = "female",
    onConfirm: (BodyPosition?) -> Unit,
    onDismiss: () -> Unit,
) {
    var face     by remember { mutableStateOf(initialPosition?.face ?: "front") }
    var marker   by remember { mutableStateOf(initialPosition?.let { Offset(it.x, it.y) }) }
    var zoneName by remember { mutableStateOf(initialPosition?.zoneName) }
    var scale    by remember { mutableFloatStateOf(1f) }
    var offset   by remember { mutableStateOf(Offset.Zero) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
    ) {
        // ── En-tête ────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) { Text("Annuler") }
            Text("Position du grain", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.width(64.dp))
        }

        // ── Toggle Face / Dos ──────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = {
                    if (face != "front") {
                        face = "front"; marker = null; zoneName = null
                        scale = 1f; offset = Offset.Zero
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (face == "front") Color(0xFF007AFF) else Color(0xFFE0E0E0),
                    contentColor   = if (face == "front") Color.White else Color.Black,
                ),
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Face") }
            Button(
                onClick = {
                    if (face != "back") {
                        face = "back"; marker = null; zoneName = null
                        scale = 1f; offset = Offset.Zero
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (face == "back") Color(0xFF007AFF) else Color(0xFFE0E0E0),
                    contentColor   = if (face == "back") Color.White else Color.Black,
                ),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Dos") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ── Corps interactif ───────────────────────────────
        // ContentScale.FillBounds : remplit tout l'espace, pas de letterbox.
        // Les coordonnées normalisées (0..1) mappent directement aux dimensions du Box.
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                // Pinch-to-zoom + panoramique
                .pointerInput(face) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        val newScale   = (scale * zoom).coerceIn(1f, 4f)
                        val maxOffsetX = size.width  * (newScale - 1) / 2f
                        val maxOffsetY = size.height * (newScale - 1) / 2f
                        offset = Offset(
                            (offset.x + pan.x).coerceIn(-maxOffsetX, maxOffsetX),
                            (offset.y + pan.y).coerceIn(-maxOffsetY, maxOffsetY),
                        )
                        scale = newScale
                    }
                }
                // Tap : placement du marqueur
                .pointerInput(face, scale, offset) {
                    detectTapGestures { tapOffset ->
                        val w = size.width.toFloat()
                        val h = size.height.toFloat()
                        // Inversion de la transformation graphicsLayer (pivot = centre du Box)
                        val contentX = (tapOffset.x - w / 2f - offset.x) / scale + w / 2f
                        val contentY = (tapOffset.y - h / 2f - offset.y) / scale + h / 2f
                        val nx = (contentX / w).coerceIn(0f, 1f)
                        val ny = (contentY / h).coerceIn(0f, 1f)
                        val zone = BodyZones.findZone(nx, ny, face)
                        marker   = Offset(nx, ny)
                        zoneName = zone
                    }
                },
        ) {
            // Contenu transformé visuellement (zoom/pan)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX      = scale
                        scaleY      = scale
                        translationX = offset.x
                        translationY = offset.y
                    },
            ) {
                Image(
                    painter = painterResource(bodyDrawableRes(gender, face)),
                    contentDescription = if (face == "front") "Corps face avant" else "Corps face arrière",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                )

                marker?.let { m ->
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val mx = m.x * size.width
                        val my = m.y * size.height
                        drawCircle(color = Color.White,         radius = 14f, center = Offset(mx, my))
                        drawCircle(color = Color(0xFFFF3B30),   radius = 10f, center = Offset(mx, my))
                    }
                }
            }
        }

        // ── Nom de zone ────────────────────────────────────
        Text(
            text = zoneName ?: "Touchez le corps pour positionner le grain",
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            style     = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color     = if (zoneName != null) Color(0xFF007AFF) else Color.Gray,
        )

        // ── Bouton Créer / Enregistrer ─────────────────────
        Button(
            onClick = {
                val position = marker?.let { m ->
                    BodyPosition(
                        x        = m.x,
                        y        = m.y,
                        face     = face,
                        zoneName = zoneName ?: "position personnalisée",
                    )
                }
                onConfirm(position)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)),
            shape  = RoundedCornerShape(8.dp),
        ) {
            Text(if (marker != null) confirmLabel else "$confirmLabel sans position")
        }
    }
}

/**
 * Miniature non interactive affichant la position d'un grain sur le corps.
 * Affiche la silhouette face avant en grisé si position est null.
 */
@Composable
fun BodyMapThumbnail(
    position: BodyPosition?,
    gender: String = "female",
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Image(
            painter = painterResource(bodyDrawableRes(gender, position?.face ?: "front")),
            contentDescription = "Position du grain",
            modifier     = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            alpha        = if (position == null) 0.35f else 1f,
        )

        if (position != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val mx = position.x * size.width
                val my = position.y * size.height
                drawCircle(color = Color.White,       radius = 6f, center = Offset(mx, my))
                drawCircle(color = Color(0xFFFF3B30), radius = 4f, center = Offset(mx, my))
            }
        }
    }
}
