package com.grainbeaute.androidweb.ui.screens

import android.app.DatePickerDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.work.*
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.grainbeaute.androidweb.data.LocalRepository
import com.grainbeaute.androidweb.model.*
import com.grainbeaute.androidweb.ui.theme.CardBorder
import com.grainbeaute.androidweb.workers.AppointmentReminderWorker
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun MoleCard(mole: LocalMole, onClick: () -> Unit) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(CardBorder))
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(80.dp).clip(CircleShape).border(1.dp, Color.LightGray, CircleShape).background(Color.Gray.copy(alpha = 0.1f))) {
                mole.lastCapture?.let { AsyncImage(model = ImageRequest.Builder(context).data(it.croppedImagePath?.let { path -> File(path) }).crossfade(true).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.align(Alignment.Center), tint = Color.Gray)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(mole.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    mole.latestDiagnosis?.let { DiagnosisBadge(it.category) } ?: DiagnosisToDiagnoseBadge()
                }
                mole.bodyPart?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Color.Gray) }
                Spacer(modifier = Modifier.height(4.dp))
                val diagInfo = if (mole.latestDiagnosis != null) {
                    val categoryName = when(mole.latestDiagnosis.category) { DiagnosisCategory.BENIGN -> "Bénin"; DiagnosisCategory.MONITOR -> "À surveiller"; DiagnosisCategory.SUSPECT -> "Suspect"; DiagnosisCategory.REMOVED -> "Retiré" }
                    "$categoryName · examiné récemment"
                } else { "Pas encore examiné" }
                Text(text = diagInfo, style = MaterialTheme.typography.labelSmall, color = Color.Gray)
            }
        }
    }
}

@Composable
fun VisitCard(visit: LocalDermatologistVisit, onClick: () -> Unit) {
    val dateFormatter = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
    Card(modifier = Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFE0E6ED))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(dateFormatter.format(Date(visit.date)), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (!visit.practitionerName.isNullOrBlank()) {
                val locText = if (!visit.practitionerAddress.isNullOrBlank()) " · ${visit.practitionerAddress}" else ""
                Text("${visit.practitionerName}$locText", style = MaterialTheme.typography.bodySmall, color = Color(0xFF007AFF), fontWeight = FontWeight.Medium)
            }
            if (!visit.globalNote.isNullOrBlank()) { Text(visit.globalNote, style = MaterialTheme.typography.bodySmall, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            Spacer(modifier = Modifier.height(4.dp))
            Text("${visit.diagnoses.size} grains examinés", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewVisitBottomSheet(
    moles: List<LocalMole>, 
    initialVisit: LocalDermatologistVisit? = null,
    currentPractitionerName: String? = null,
    currentPractitionerAddress: String? = null,
    onDismiss: () -> Unit, 
    onSave: (date: Long, practitionerName: String?, practitionerAddress: String?, note: String?, diagnoses: List<Pair<Int, LocalMoleDiagnosis>>) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var step by remember { mutableStateOf(1) }
    var visitDate by remember { mutableStateOf(initialVisit?.date ?: System.currentTimeMillis()) }
    var globalNote by remember { mutableStateOf(initialVisit?.globalNote ?: "") }
    
    val examinedMoles = remember { 
        mutableStateMapOf<Int, Boolean>().apply {
            initialVisit?.diagnoses?.forEach { put(it.moleId ?: -1, true) }
        }
    }
    val diagnoses = remember { 
        mutableStateMapOf<Int, DiagnosisCategory>().apply {
            initialVisit?.diagnoses?.forEach { put(it.moleId ?: -1, it.category) }
        }
    }
    val notes = remember { 
        mutableStateMapOf<Int, String>().apply {
            initialVisit?.diagnoses?.forEach { put(it.moleId ?: -1, it.note ?: "") }
        }
    }

    val dateFormatter = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState, dragHandle = { BottomSheetDefaults.DragHandle() }) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp, start = 16.dp, end = 16.dp)) {
            Text(
                if (initialVisit == null) {
                    if (step == 1) "NOUVELLE VISITE — INFOS" else "NOUVELLE VISITE — DIAGNOSTICS"
                } else {
                    if (step == 1) "MODIFIER VISITE — INFOS" else "MODIFIER VISITE — DIAGNOSTICS"
                },
                style = MaterialTheme.typography.labelLarge, 
                color = Color.Gray, 
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(16.dp))
            if (step == 1) {
                OutlinedTextField(
                    value = dateFormatter.format(Date(visitDate)),
                    onValueChange = {},
                    label = { Text("Date de la visite") },
                    modifier = Modifier.fillMaxWidth().clickable { 
                        showDatePicker(context, visitDate) { visitDate = it }
                    },
                    enabled = false,
                    readOnly = true,
                    trailingIcon = { Icon(Icons.Default.Event, contentDescription = null) },
                    colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant)
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(value = globalNote, onValueChange = { globalNote = it }, label = { Text("Note globale (optionnel)") }, modifier = Modifier.fillMaxWidth().height(120.dp), maxLines = 5)
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = { step = 2 }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(8.dp)) { Text("Suivant →") }
            } else {
                LazyColumn(modifier = Modifier.weight(1f, fill = false), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(moles) { mole ->
                        DiagnosisEntryCard(mole = mole, isExamined = examinedMoles[mole.id] ?: false, onExaminedChange = { examinedMoles[mole.id] = it }, category = diagnoses[mole.id] ?: DiagnosisCategory.BENIGN, onCategoryChange = { diagnoses[mole.id] = it }, note = notes[mole.id] ?: "", onNoteChange = { notes[mole.id] = it })
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { step = 1 }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("← Retour") }
                    Button(onClick = {
                        val finalDiags = examinedMoles.filter { it.value }.keys.map { moleId ->
                            val mole = moles.find { it.id == moleId }!!
                            moleId to LocalMoleDiagnosis(id = 0, visitId = initialVisit?.id ?: 0, moleId = moleId, moleName = mole.name, category = diagnoses[moleId] ?: DiagnosisCategory.BENIGN, note = notes[moleId])
                        }
                        onSave(
                            visitDate, 
                            initialVisit?.practitionerName ?: currentPractitionerName,
                            initialVisit?.practitionerAddress ?: currentPractitionerAddress,
                            globalNote.takeIf { it.isNotBlank() }, 
                            finalDiags
                        )
                    }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("Enregistrer ✓") }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosisEntryCard(mole: LocalMole, isExamined: Boolean, onExaminedChange: (Boolean) -> Unit, category: DiagnosisCategory, onCategoryChange: (DiagnosisCategory) -> Unit, note: String, onNoteChange: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (isExamined) Color(0xFFF0F7FF) else Color.White), border = BorderStroke(1.dp, if (isExamined) Color(0xFFCCE5FF) else Color(0xFFE0E6ED))) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(80.dp).clip(CircleShape).background(Color.LightGray)) {
                    mole.lastCapture?.let { AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(it.croppedImagePath?.let { File(it) }).build(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(mole.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Checkbox(checked = isExamined, onCheckedChange = onExaminedChange)
                Text("examiné", style = MaterialTheme.typography.labelSmall)
            }
            if (isExamined) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        FilterChip(selected = true, onClick = { expanded = true }, label = { Text(when(category) { DiagnosisCategory.BENIGN -> "Bénin"; DiagnosisCategory.MONITOR -> "À surveiller"; DiagnosisCategory.SUSPECT -> "Suspect"; DiagnosisCategory.REMOVED -> "Retiré" }) }, trailingIcon = { Icon(Icons.Default.ArrowDropDown, null) })
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DiagnosisCategory.entries.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) }, onClick = { onCategoryChange(cat); expanded = false })
                            }
                        }
                    }
                    OutlinedTextField(value = note, onValueChange = onNoteChange, placeholder = { Text("Note...", fontSize = 12.sp) }, modifier = Modifier.weight(1f), textStyle = MaterialTheme.typography.bodySmall, singleLine = true)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PractitionerInfoBottomSheet(
    currentSettings: LocalAppSettings,
    onDismiss: () -> Unit,
    onSave: (LocalAppSettings) -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    var name by remember { mutableStateOf(currentSettings.practitionerName ?: "") }
    var address by remember { mutableStateOf(currentSettings.practitionerAddress ?: "") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("COORDONNÉES DU DERMATOLOGUE", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom du praticien") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = address, onValueChange = { address = it }, label = { Text("Adresse / Lieu") }, modifier = Modifier.fillMaxWidth())
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Annuler") }
                Button(onClick = { 
                    onSave(currentSettings.copy(
                        practitionerName = name.takeIf { it.isNotBlank() },
                        practitionerAddress = address.takeIf { it.isNotBlank() }
                    )) 
                }, modifier = Modifier.weight(1f)) { Text("Enregistrer") }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAppointmentBottomSheet(currentSettings: LocalAppSettings, onDismiss: () -> Unit, onSave: (LocalAppSettings) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    var date by remember { mutableStateOf(currentSettings.nextAppointmentDate) }
    var reminderDays by remember { mutableStateOf(currentSettings.reminderDaysBefore) }
    val dateFormatter = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE)
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp).padding(bottom = 32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("PROCHAIN RENDEZ-VOUS", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            
            OutlinedTextField(
                value = date?.let { dateFormatter.format(Date(it)) } ?: "",
                onValueChange = {},
                label = { Text("Date du RDV") },
                modifier = Modifier.fillMaxWidth().clickable { 
                    showDatePicker(context, date ?: System.currentTimeMillis()) { date = it }
                },
                readOnly = true,
                enabled = false,
                trailingIcon = { Icon(Icons.Default.Event, null) },
                colors = OutlinedTextFieldDefaults.colors(disabledTextColor = MaterialTheme.colorScheme.onSurface, disabledBorderColor = MaterialTheme.colorScheme.outline, disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant, disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant)
            )
            Column {
                Text("Rappel avant le RDV", style = MaterialTheme.typography.labelLarge)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    listOf(1, 3, 7, 14).forEach { d -> FilterChip(selected = reminderDays == d, onClick = { reminderDays = d }, label = { Text("$d j") }) }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("Annuler") }
                Button(onClick = { onSave(currentSettings.copy(nextAppointmentDate = date, reminderDaysBefore = reminderDays)) }, modifier = Modifier.weight(1f)) { Text("Enregistrer") }
            }
        }
    }
}

fun showDatePicker(context: Context, initialDate: Long, onDateSelected: (Long) -> Unit) {
    val calendar = Calendar.getInstance().apply { timeInMillis = initialDate }
    DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val result = Calendar.getInstance().apply {
                set(Calendar.YEAR, year)
                set(Calendar.MONTH, month)
                set(Calendar.DAY_OF_MONTH, dayOfMonth)
            }
            onDateSelected(result.timeInMillis)
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    ).show()
}

@Composable
fun DiagnosisBadge(category: DiagnosisCategory) {
    val (color, text) = when (category) { DiagnosisCategory.BENIGN -> Color(0xFF4CAF50) to "Bénin"; DiagnosisCategory.MONITOR -> Color(0xFFFF9800) to "À surveiller"; DiagnosisCategory.SUSPECT -> Color(0xFFF44336) to "Suspect"; DiagnosisCategory.REMOVED -> Color(0xFF9E9E9E) to "Retiré" }
    Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, color.copy(alpha = 0.5f))) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(text, style = MaterialTheme.typography.labelSmall, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DiagnosisToDiagnoseBadge() {
    Surface(color = Color(0xFF007AFF).copy(alpha = 0.1f), shape = RoundedCornerShape(4.dp), border = BorderStroke(1.dp, Color(0xFF007AFF).copy(alpha = 0.5f))) {
        Row(modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Color(0xFF007AFF)))
            Text("À diagnostiquer", style = MaterialTheme.typography.labelSmall, color = Color(0xFF007AFF), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun DiagnosisBadgePlaceholder() {
    Surface(color = Color.LightGray.copy(alpha = 0.2f), shape = RoundedCornerShape(4.dp)) {
        Text("—", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, color = Color.Gray)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddMoleDialog(onDismiss: () -> Unit, onAdd: (String, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var bodyPart by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false), modifier = Modifier.padding(24.dp), content = { Surface(shape = RoundedCornerShape(24.dp), color = Color(0xFFF5F7FA)) { Column(modifier = Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("Nouveau Grain", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(20.dp)); Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFE0E6ED))) { Column(modifier = Modifier.padding(16.dp)) { OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nom (ex: Dos)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true); Spacer(modifier = Modifier.height(12.dp)); OutlinedTextField(value = bodyPart, onValueChange = { bodyPart = it }, label = { Text("Partie du corps") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), singleLine = true) } }; Spacer(modifier = Modifier.height(24.dp)); Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) { Button(onClick = onDismiss, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black), border = BorderStroke(1.dp, Color(0xFFE0E6ED)), shape = RoundedCornerShape(8.dp)) { Text("Annuler") }; Button(onClick = { onAdd(name, bodyPart.takeIf { it.isNotBlank() }) }, modifier = Modifier.weight(1f), enabled = name.isNotBlank(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF007AFF)), shape = RoundedCornerShape(8.dp)) { Text("Ajouter") } } } } })
}

@Composable
fun SpeedDialFab(isOpen: Boolean, onToggle: () -> Unit, onOpenCamera: () -> Unit, onCreateMole: () -> Unit) {
    Column(horizontalAlignment = Alignment.End) {
        AnimatedVisibility(visible = isOpen, enter = slideInVertically(initialOffsetY = { it }) + fadeIn(), exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Prendre des photos", style = MaterialTheme.typography.labelMedium, modifier = Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp)); Spacer(modifier = Modifier.width(8.dp)); SmallFloatingActionButton(onClick = onOpenCamera) { Icon(Icons.Default.CameraAlt, contentDescription = "Mode rafale") } }
                Row(verticalAlignment = Alignment.CenterVertically) { Text("Nouveau grain", style = MaterialTheme.typography.labelMedium, modifier = Modifier.background(MaterialTheme.colorScheme.surface, MaterialTheme.shapes.small).padding(horizontal = 8.dp, vertical = 4.dp)); Spacer(modifier = Modifier.width(8.dp)); SmallFloatingActionButton(onClick = onCreateMole) { Icon(Icons.Default.Add, contentDescription = "Nouveau grain") } }
            }
        }
        FloatingActionButton(onClick = onToggle) { Icon(imageVector = if (isOpen) Icons.Default.Close else Icons.Default.Add, contentDescription = if (isOpen) "Fermer" else "Menu") }
    }
}

@Composable
fun HealthSummaryCard(
    moleCount: Int, 
    lastVisitDate: Long?,
    nextAppointment: LocalAppSettings? = null,
    onClickNextAppointment: () -> Unit = {}
) {
    val dateFormatter = SimpleDateFormat("d MMM yyyy", Locale.FRANCE)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F7FF)), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Color(0xFFCCE5FF))) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("RÉSUMÉ SANTÉ", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = Color(0xFF0056B3))
            Spacer(modifier = Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Info, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); Text("$moleCount grains de beauté", style = MaterialTheme.typography.bodyMedium) }
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.History, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp)); Spacer(modifier = Modifier.width(8.dp)); val lastVisitStr = lastVisitDate?.let { dateFormatter.format(Date(it)) } ?: "Aucune visite"; Text("Dernière visite : $lastVisitStr", style = MaterialTheme.typography.bodyMedium) }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { onClickNextAppointment() }
            ) {
                Icon(Icons.Default.Event, contentDescription = null, tint = Color(0xFF007AFF), modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                val nextDateStr = nextAppointment?.nextAppointmentDate?.let { dateFormatter.format(Date(it)) } ?: "Non planifié"
                Text(
                    text = if (nextAppointment?.nextAppointmentDate != null) "Prochain RDV : $nextDateStr" else "Planifier un rendez-vous",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (nextAppointment?.nextAppointmentDate != null) Color.Black else Color(0xFF007AFF)
                )
            }
        }
    }
}

fun scheduleReminder(context: Context, settings: LocalAppSettings) {
    val workManager = WorkManager.getInstance(context)
    workManager.cancelAllWorkByTag("appointment_reminder")
    val appointmentTime = settings.nextAppointmentDate ?: return
    val reminderTime = appointmentTime - TimeUnit.DAYS.toMillis(settings.reminderDaysBefore.toLong())
    val delay = reminderTime - System.currentTimeMillis()
    if (delay > 0) {
        val data = workDataOf("practitioner_name" to settings.practitionerName, "days_before" to settings.reminderDaysBefore, "appointment_date" to SimpleDateFormat("d MMM", Locale.FRANCE).format(Date(appointmentTime)))
        val workRequest = OneTimeWorkRequestBuilder<AppointmentReminderWorker>().setInitialDelay(delay, TimeUnit.MILLISECONDS).setInputData(data).addTag("appointment_reminder").build()
        workManager.enqueue(workRequest)
    }
}
