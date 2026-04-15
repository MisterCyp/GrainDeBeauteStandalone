# Body Map Picker — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the free-text "Partie du corps" field with an interactive human body silhouette where the user taps to place a marker, generating the anatomical zone name and normalized coordinates automatically.

**Architecture:** `BodyZones.kt` holds pure Kotlin rectangle zone detection (unit-testable, no Android deps). `BodyMapPicker.kt` is a full-screen Compose composable with `detectTransformGestures` (pinch-to-zoom) + `detectTapGestures` (marker placement) and a read-only `BodyMapThumbnail`. DB migration 5→6 adds three nullable columns to `MoleEntity`. MolesScreen wraps `BodyMapPicker` in a full-screen `Dialog`; MoleDetailScreen shows `BodyMapThumbnail` + edit button.

**Tech Stack:** Jetpack Compose (gestures, graphicsLayer), Room (migration), VectorDrawable, JUnit 4

---

## File Map

**Create:**
- `app/src/main/java/com/grainbeaute/androidweb/model/BodyZones.kt`
- `app/src/main/java/com/grainbeaute/androidweb/ui/screens/BodyMapPicker.kt`
- `app/src/main/res/drawable/ic_body_front.xml`
- `app/src/main/res/drawable/ic_body_back.xml`
- `app/src/test/java/com/grainbeaute/androidweb/model/BodyZonesTest.kt`

**Modify:**
- `app/src/main/java/com/grainbeaute/androidweb/model/LocalModels.kt` — add `BodyPosition`, update `LocalMole`
- `app/src/main/java/com/grainbeaute/androidweb/data/db/Entities.kt` — 3 new nullable columns in `MoleEntity`
- `app/src/main/java/com/grainbeaute/androidweb/data/db/Migrations.kt` — add `MIGRATION_5_6`
- `app/src/main/java/com/grainbeaute/androidweb/data/db/AppDatabase.kt` — version 5 → 6
- `app/src/main/java/com/grainbeaute/androidweb/data/LocalRepository.kt` — update `toLocalMole`, add `createMoleWithPosition`, add `updateMolePosition`
- `app/src/main/java/com/grainbeaute/androidweb/ui/screens/MolesScreen.kt` — `Dialog` wrapping `BodyMapPicker` instead of `AddMoleDialog`
- `app/src/main/java/com/grainbeaute/androidweb/ui/screens/CommonComponents.kt` — delete `AddMoleDialog`
- `app/src/main/java/com/grainbeaute/androidweb/ui/screens/MoleDetailScreen.kt` — `BodyMapThumbnail` + edit button

---

### Task 1 : BodyPosition model + LocalMole

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/model/LocalModels.kt`

- [ ] **Step 1 : Read the file**

  Open `LocalModels.kt` and confirm the current `LocalMole` signature before editing.

- [ ] **Step 2 : Add `BodyPosition` and update `LocalMole`**

  Add `BodyPosition` just before `LocalMole`:
  ```kotlin
  data class BodyPosition(
      val x: Float,           // 0.0..1.0
      val y: Float,           // 0.0..1.0
      val face: String,       // "front" | "back"
      val zoneName: String,
  )
  ```

  Update `LocalMole` to include three nullable position fields:
  ```kotlin
  data class LocalMole(
      val id: Int,
      val name: String,
      val bodyPart: String? = null,
      val bodyPositionX: Float? = null,
      val bodyPositionY: Float? = null,
      val bodyFace: String? = null,
      val createdAt: Long,
      val lastCapture: LocalCapture? = null,
      val captures: List<LocalCapture> = emptyList(),
      val latestDiagnosis: LocalMoleDiagnosis? = null,
  )
  ```

- [ ] **Step 3 : Commit**
  ```
  git add app/src/main/java/com/grainbeaute/androidweb/model/LocalModels.kt
  git commit -m "feat: add BodyPosition model and position fields to LocalMole"
  ```

---

### Task 2 : DB migration 5 → 6

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/data/db/Entities.kt`
- Modify: `app/src/main/java/com/grainbeaute/androidweb/data/db/Migrations.kt`
- Modify: `app/src/main/java/com/grainbeaute/androidweb/data/db/AppDatabase.kt`
- Modify: `app/src/main/java/com/grainbeaute/androidweb/data/LocalRepository.kt`

- [ ] **Step 1 : Update `MoleEntity` in Entities.kt**

  Read `Entities.kt`. Replace `MoleEntity` with:
  ```kotlin
  @Entity(tableName = "moles")
  data class MoleEntity(
      @PrimaryKey(autoGenerate = true) val id: Int = 0,
      val name: String,
      val bodyPart: String? = null,
      val bodyPositionX: Float? = null,
      val bodyPositionY: Float? = null,
      val bodyFace: String? = null,
      val createdAt: Long = System.currentTimeMillis(),
  )
  ```

- [ ] **Step 2 : Add `MIGRATION_5_6` to Migrations.kt**

  Read `Migrations.kt`. Add after `MIGRATION_4_5`:
  ```kotlin
  /**
   * Migration de la version 5 à 6 :
   * - Ajout de bodyPositionX, bodyPositionY, bodyFace à la table `moles`
   */
  val MIGRATION_5_6 = object : Migration(5, 6) {
      override fun migrate(db: SupportSQLiteDatabase) {
          db.execSQL("ALTER TABLE `moles` ADD COLUMN `bodyPositionX` REAL")
          db.execSQL("ALTER TABLE `moles` ADD COLUMN `bodyPositionY` REAL")
          db.execSQL("ALTER TABLE `moles` ADD COLUMN `bodyFace` TEXT")
      }
  }
  ```

  Update the `ALL` array:
  ```kotlin
  val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
  ```

- [ ] **Step 3 : Bump version in AppDatabase.kt**

  Read `AppDatabase.kt`. Change `version = 5` to `version = 6`. Verify that `addMigrations(*Migrations.ALL)` is already wired in the builder — if so no other change needed.

- [ ] **Step 4 : Update `toLocalMole` in LocalRepository.kt**

  Find `private fun MoleEntity.toLocalMole(` (around line 354). Replace the body:
  ```kotlin
  private fun MoleEntity.toLocalMole(
      captures: List<LocalCapture> = emptyList(),
      lastCapture: LocalCapture? = null,
      latestDiagnosis: LocalMoleDiagnosis? = null,
  ) = LocalMole(
      id = id, name = name, bodyPart = bodyPart,
      bodyPositionX = bodyPositionX,
      bodyPositionY = bodyPositionY,
      bodyFace = bodyFace,
      createdAt = createdAt, lastCapture = lastCapture, captures = captures,
      latestDiagnosis = latestDiagnosis,
  )
  ```

- [ ] **Step 5 : Commit**
  ```
  git add app/src/main/java/com/grainbeaute/androidweb/data/db/Entities.kt \
          app/src/main/java/com/grainbeaute/androidweb/data/db/Migrations.kt \
          app/src/main/java/com/grainbeaute/androidweb/data/db/AppDatabase.kt \
          app/src/main/java/com/grainbeaute/androidweb/data/LocalRepository.kt
  git commit -m "feat: DB migration 5→6 — add body position columns to moles"
  ```

---

### Task 3 : BodyZones logic + unit tests

**Files:**
- Create: `app/src/main/java/com/grainbeaute/androidweb/model/BodyZones.kt`
- Create: `app/src/test/java/com/grainbeaute/androidweb/model/BodyZonesTest.kt`

- [ ] **Step 1 : Write the failing tests**

  Create `app/src/test/java/com/grainbeaute/androidweb/model/BodyZonesTest.kt`:
  ```kotlin
  package com.grainbeaute.androidweb.model

  import org.junit.Assert.assertEquals
  import org.junit.Assert.assertNotEquals
  import org.junit.Test

  class BodyZonesTest {

      @Test
      fun `head zone detected at top center front`() {
          assertEquals("tête", BodyZones.findZone(0.50f, 0.07f, "front"))
      }

      @Test
      fun `torso front detected at vertical center`() {
          assertEquals("torse avant", BodyZones.findZone(0.50f, 0.33f, "front"))
      }

      @Test
      fun `left thigh detected front`() {
          assertEquals("cuisse gauche", BodyZones.findZone(0.42f, 0.65f, "front"))
      }

      @Test
      fun `back and front zones are different at same coordinate`() {
          val front = BodyZones.findZone(0.50f, 0.33f, "front")
          val back  = BodyZones.findZone(0.50f, 0.33f, "back")
          assertNotEquals(front, back)
      }

      @Test
      fun `outside body returns position personnalisee`() {
          assertEquals("position personnalisée", BodyZones.findZone(0.01f, 0.01f, "front"))
      }

      @Test
      fun `back torso detected`() {
          assertEquals("dos haut", BodyZones.findZone(0.50f, 0.30f, "back"))
      }
  }
  ```

- [ ] **Step 2 : Run tests — confirm failure**

  In Android Studio: right-click `BodyZonesTest` → Run. Expected: compilation error `Unresolved reference: BodyZones`.

- [ ] **Step 3 : Implement BodyZones.kt**

  Create `app/src/main/java/com/grainbeaute/androidweb/model/BodyZones.kt`:
  ```kotlin
  package com.grainbeaute.androidweb.model

  /**
   * Détection de zone anatomique par coordonnées normalisées (0..1).
   * Les zones sont des rectangles définis relativement au viewport SVG (100 × 200).
   * x=0 = gauche de l'image, y=0 = haut.
   */
  object BodyZones {

      data class Zone(
          val name: String,
          val xMin: Float, val xMax: Float,
          val yMin: Float, val yMax: Float,
      ) {
          fun contains(x: Float, y: Float) = x in xMin..xMax && y in yMin..yMax
      }

      private val frontZones = listOf(
          Zone("tête",                0.36f, 0.64f, 0.00f, 0.15f),
          Zone("cou",                 0.42f, 0.58f, 0.15f, 0.20f),
          Zone("épaule gauche",       0.15f, 0.37f, 0.15f, 0.28f),
          Zone("épaule droite",       0.63f, 0.85f, 0.15f, 0.28f),
          Zone("bras gauche",         0.12f, 0.35f, 0.18f, 0.40f),
          Zone("bras droit",          0.65f, 0.88f, 0.18f, 0.40f),
          Zone("avant-bras gauche",   0.09f, 0.32f, 0.40f, 0.57f),
          Zone("avant-bras droit",    0.68f, 0.91f, 0.40f, 0.57f),
          Zone("main gauche",         0.08f, 0.28f, 0.57f, 0.68f),
          Zone("main droite",         0.72f, 0.92f, 0.57f, 0.68f),
          Zone("torse avant",         0.34f, 0.66f, 0.20f, 0.48f),
          Zone("abdomen",             0.34f, 0.66f, 0.48f, 0.57f),
          Zone("cuisse gauche",       0.34f, 0.52f, 0.57f, 0.77f),
          Zone("cuisse droite",       0.48f, 0.66f, 0.57f, 0.77f),
          Zone("mollet gauche",       0.33f, 0.51f, 0.77f, 0.93f),
          Zone("mollet droit",        0.49f, 0.67f, 0.77f, 0.93f),
          Zone("pied gauche",         0.27f, 0.50f, 0.93f, 1.00f),
          Zone("pied droit",          0.50f, 0.73f, 0.93f, 1.00f),
      )

      private val backZones = listOf(
          Zone("nuque",                       0.40f, 0.60f, 0.13f, 0.22f),
          Zone("épaule gauche (dos)",         0.15f, 0.37f, 0.15f, 0.28f),
          Zone("épaule droite (dos)",         0.63f, 0.85f, 0.15f, 0.28f),
          Zone("bras gauche (dos)",           0.12f, 0.35f, 0.18f, 0.40f),
          Zone("bras droit (dos)",            0.65f, 0.88f, 0.18f, 0.40f),
          Zone("avant-bras gauche (dos)",     0.09f, 0.32f, 0.40f, 0.57f),
          Zone("avant-bras droit (dos)",      0.68f, 0.91f, 0.40f, 0.57f),
          Zone("dos haut",                    0.34f, 0.66f, 0.20f, 0.42f),
          Zone("dos bas",                     0.34f, 0.66f, 0.42f, 0.57f),
          Zone("fesses",                      0.34f, 0.66f, 0.57f, 0.68f),
          Zone("arrière cuisse gauche",       0.34f, 0.52f, 0.68f, 0.79f),
          Zone("arrière cuisse droite",       0.48f, 0.66f, 0.68f, 0.79f),
          Zone("mollet gauche (dos)",         0.33f, 0.51f, 0.79f, 0.93f),
          Zone("mollet droit (dos)",          0.49f, 0.67f, 0.79f, 0.93f),
          Zone("talon gauche",                0.27f, 0.50f, 0.93f, 1.00f),
          Zone("talon droit",                 0.50f, 0.73f, 0.93f, 1.00f),
      )

      /**
       * Retourne le nom de zone anatomique pour des coordonnées normalisées.
       * Retourne "position personnalisée" si aucune zone ne correspond.
       */
      fun findZone(x: Float, y: Float, face: String): String {
          val zones = if (face == "front") frontZones else backZones
          return zones.firstOrNull { it.contains(x, y) }?.name ?: "position personnalisée"
      }
  }
  ```

- [ ] **Step 4 : Run tests — confirm all pass**

  In Android Studio: right-click `BodyZonesTest` → Run. Expected: 6 tests PASS.

- [ ] **Step 5 : Commit**
  ```
  git add app/src/main/java/com/grainbeaute/androidweb/model/BodyZones.kt \
          app/src/test/java/com/grainbeaute/androidweb/model/BodyZonesTest.kt
  git commit -m "feat: BodyZones zone detection with 18 front zones, 16 back zones, unit tests"
  ```

---

### Task 4 : VectorDrawable assets

**Files:**
- Create: `app/src/main/res/drawable/ic_body_front.xml`
- Create: `app/src/main/res/drawable/ic_body_back.xml`

The path draws a simplified human silhouette in a 100 × 200 viewport. The path goes clockwise from the left side of the neck. Note: `ContentScale.FillBounds` will be used so the image fills its container without letterboxing — the slight distortion is acceptable at the scale this is used.

- [ ] **Step 1 : Create ic_body_front.xml**

  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="100dp"
      android:height="200dp"
      android:viewportWidth="100"
      android:viewportHeight="200">
      <!-- Tête -->
      <path
          android:fillColor="#CCCCCC"
          android:strokeColor="#888888"
          android:strokeWidth="1.5"
          android:pathData="M50,2 A12,12 0 0 1 62,14 A12,12 0 0 1 50,26 A12,12 0 0 1 38,14 A12,12 0 0 1 50,2 Z" />
      <!-- Corps, bras et jambes -->
      <path
          android:fillColor="#CCCCCC"
          android:strokeColor="#888888"
          android:strokeWidth="1.5"
          android:pathData="
  M44,27 L36,31 L22,35 L18,55 L15,72 L13,86
  L16,93 L20,98 L23,93
  L24,78 L26,56 L30,42
  L33,45 L33,95
  L36,100 L34,133 L33,168 L30,194
  L40,198 L41,168 L43,133 L44,106
  L56,106 L57,133 L59,168 L60,198
  L70,194 L67,168 L66,133 L64,100
  L67,95 L67,45
  L70,42 L74,56 L76,78
  L77,93 L80,98 L84,93
  L87,86 L85,72 L82,55 L78,35 L64,31 L56,27 Z" />
  </vector>
  ```

- [ ] **Step 2 : Create ic_body_back.xml**

  Same silhouette, slightly darker fill and a vertical spine line for visual differentiation:
  ```xml
  <?xml version="1.0" encoding="utf-8"?>
  <vector xmlns:android="http://schemas.android.com/apk/res/android"
      android:width="100dp"
      android:height="200dp"
      android:viewportWidth="100"
      android:viewportHeight="200">
      <!-- Tête (dos) -->
      <path
          android:fillColor="#BBBBBB"
          android:strokeColor="#888888"
          android:strokeWidth="1.5"
          android:pathData="M50,2 A12,12 0 0 1 62,14 A12,12 0 0 1 50,26 A12,12 0 0 1 38,14 A12,12 0 0 1 50,2 Z" />
      <!-- Corps, bras et jambes (dos) -->
      <path
          android:fillColor="#BBBBBB"
          android:strokeColor="#888888"
          android:strokeWidth="1.5"
          android:pathData="
  M44,27 L36,31 L22,35 L18,55 L15,72 L13,86
  L16,93 L20,98 L23,93
  L24,78 L26,56 L30,42
  L33,45 L33,95
  L36,100 L34,133 L33,168 L30,194
  L40,198 L41,168 L43,133 L44,106
  L56,106 L57,133 L59,168 L60,198
  L70,194 L67,168 L66,133 L64,100
  L67,95 L67,45
  L70,42 L74,56 L76,78
  L77,93 L80,98 L84,93
  L87,86 L85,72 L82,55 L78,35 L64,31 L56,27 Z" />
      <!-- Ligne de colonne vertébrale -->
      <path
          android:strokeColor="#777777"
          android:strokeWidth="1"
          android:fillColor="@android:color/transparent"
          android:pathData="M50,27 L50,95" />
  </vector>
  ```

- [ ] **Step 3 : Verify in Android Studio preview**

  Open each file. The Android Studio preview pane should show a rough human silhouette. If the shape looks significantly broken, adjust the path points in steps of ±5 units.

- [ ] **Step 4 : Commit**
  ```
  git add app/src/main/res/drawable/ic_body_front.xml \
          app/src/main/res/drawable/ic_body_back.xml
  git commit -m "feat: add human body silhouette VectorDrawable assets"
  ```

---

### Task 5 : Repository — createMoleWithPosition + updateMolePosition

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/data/LocalRepository.kt`

- [ ] **Step 1 : Add `createMoleWithPosition`**

  Read `LocalRepository.kt`. After the existing `createMole(name, bodyPart)` function (line ~67), insert:
  ```kotlin
  /**
   * Crée un grain avec un nom auto-généré ("Grain #N") et une position optionnelle.
   * Utilisé par le flow BodyMapPicker.
   */
  suspend fun createMoleWithPosition(bodyPosition: BodyPosition?): LocalMole = withContext(Dispatchers.IO) {
      val count = moleDao.getAll().size
      val name = "Grain #${count + 1}"
      val entity = MoleEntity(
          name = name,
          bodyPart = bodyPosition?.zoneName,
          bodyPositionX = bodyPosition?.x,
          bodyPositionY = bodyPosition?.y,
          bodyFace = bodyPosition?.face,
      )
      val id = moleDao.insert(entity).toInt()
      entity.copy(id = id).toLocalMole()
  }
  ```

- [ ] **Step 2 : Add `updateMolePosition`**

  After `updateMole(id, name, bodyPart)`, insert:
  ```kotlin
  /**
   * Met à jour uniquement la position d'un grain (utilisé depuis MoleDetailScreen).
   * Si position est null, efface la position existante.
   */
  suspend fun updateMolePosition(moleId: Int, position: BodyPosition?) = withContext(Dispatchers.IO) {
      val entity = moleDao.getById(moleId) ?: return@withContext
      moleDao.update(entity.copy(
          bodyPart = position?.zoneName ?: entity.bodyPart,
          bodyPositionX = position?.x,
          bodyPositionY = position?.y,
          bodyFace = position?.face,
      ))
  }
  ```

- [ ] **Step 3 : Commit**
  ```
  git add app/src/main/java/com/grainbeaute/androidweb/data/LocalRepository.kt
  git commit -m "feat: add createMoleWithPosition and updateMolePosition to repository"
  ```

---

### Task 6 : BodyMapPicker + BodyMapThumbnail composables

**Files:**
- Create: `app/src/main/java/com/grainbeaute/androidweb/ui/screens/BodyMapPicker.kt`

- [ ] **Step 1 : Create BodyMapPicker.kt**

  ```kotlin
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
   * Sélecteur interactif de position sur un corps humain.
   *
   * - Toggle Face / Dos : changer de face efface le marqueur courant.
   * - Pinch-to-zoom : scale 1×→4×, translation clampée aux bords.
   * - Tap : place un marqueur rouge, déduit la zone anatomique.
   * - Bouton "Créer" toujours actif (position optionnelle).
   *
   * @param initialPosition position pré-remplie (mode édition)
   * @param onConfirm   appelé avec la BodyPosition finale, ou null si aucune
   * @param onDismiss   appelé sur "Annuler"
   */
  @Composable
  fun BodyMapPicker(
      initialPosition: BodyPosition? = null,
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
                      painter = painterResource(
                          if (face == "front") R.drawable.ic_body_front else R.drawable.ic_body_back,
                      ),
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
              Text(if (marker != null) "Créer" else "Créer sans position")
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
      modifier: Modifier = Modifier,
  ) {
      Box(modifier = modifier) {
          Image(
              painter = painterResource(
                  if (position?.face == "back") R.drawable.ic_body_back else R.drawable.ic_body_front,
              ),
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
  ```

- [ ] **Step 2 : Update button label for edit mode**

  In `BodyMapPicker`, the confirm button currently says "Créer" or "Créer sans position". When used from `MoleDetailScreen` (edit mode), it should say "Enregistrer". Add a `confirmLabel: String = "Créer"` parameter and update the Button text:

  Change signature:
  ```kotlin
  fun BodyMapPicker(
      initialPosition: BodyPosition? = null,
      confirmLabel: String = "Créer",
      onConfirm: (BodyPosition?) -> Unit,
      onDismiss: () -> Unit,
  )
  ```

  Change button text:
  ```kotlin
  Text(if (marker != null) confirmLabel else "$confirmLabel sans position")
  ```

- [ ] **Step 3 : Commit**
  ```
  git add app/src/main/java/com/grainbeaute/androidweb/ui/screens/BodyMapPicker.kt
  git commit -m "feat: add BodyMapPicker (interactive) and BodyMapThumbnail (read-only) composables"
  ```

---

### Task 7 : MolesScreen — remplacer AddMoleDialog par BodyMapPicker

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/ui/screens/MolesScreen.kt`
- Modify: `app/src/main/java/com/grainbeaute/androidweb/ui/screens/CommonComponents.kt`

- [ ] **Step 1 : Vérifier que AddMoleDialog n'est utilisé que dans MolesScreen**

  Search: `grep -r "AddMoleDialog" app/src/main/java/`. Expected: only `MolesScreen.kt` and `CommonComponents.kt`. If it also appears in `InboxScreen.kt`, do not delete it yet — adapt the plan accordingly.

- [ ] **Step 2 : Mettre à jour MolesScreen.kt**

  Read `MolesScreen.kt`. Make these changes:

  1. Replace `var showAddMoleDialog by remember { mutableStateOf(false) }` with:
     ```kotlin
     var showBodyMapPicker by remember { mutableStateOf(false) }
     ```

  2. Replace all 3 occurrences of `showAddMoleDialog` with `showBodyMapPicker`.

  3. Replace the bottom dialog block:
     ```kotlin
     // AVANT
     if (showAddMoleDialog) {
         AddMoleDialog(
             onDismiss = { showAddMoleDialog = false },
             onAdd = { name, part ->
                 scope.launch {
                     repository.createMole(name, part)
                     loadMoles()
                     showAddMoleDialog = false
                 }
             }
         )
     }
     ```
     ```kotlin
     // APRÈS
     if (showBodyMapPicker) {
         Dialog(
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
     ```

  4. Add import:
     ```kotlin
     import androidx.compose.ui.window.Dialog
     ```

- [ ] **Step 3 : Supprimer AddMoleDialog de CommonComponents.kt**

  Read `CommonComponents.kt`. Delete the entire `AddMoleDialog` function (lines 385–391 approx., from `@OptIn` annotation to closing brace). The function takes up approximately 7 lines. Remove only that block; leave everything else untouched.

- [ ] **Step 4 : Commit**
  ```
  git add app/src/main/java/com/grainbeaute/androidweb/ui/screens/MolesScreen.kt \
          app/src/main/java/com/grainbeaute/androidweb/ui/screens/CommonComponents.kt
  git commit -m "feat: replace AddMoleDialog with full-screen BodyMapPicker in creation flow"
  ```

---

### Task 8 : MoleDetailScreen — BodyMapThumbnail + bouton édition

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/ui/screens/MoleDetailScreen.kt`

- [ ] **Step 1 : Ajouter l'état et la Dialog d'édition**

  Read `MoleDetailScreen.kt`. At the top of `MoleDetailScreen` (after the existing `var` declarations), add:
  ```kotlin
  var showBodyMapEditor by remember { mutableStateOf(false) }
  ```

  At the very end of the `Scaffold` lambda (after the `}` closing the `if (isLoading && mole == null) { ... } else { ... }` block, but still inside `Scaffold`), add:
  ```kotlin
  if (showBodyMapEditor) {
      Dialog(
          onDismissRequest = { showBodyMapEditor = false },
          properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
      ) {
          val currentPosition = mole?.run {
              if (bodyPositionX != null && bodyPositionY != null && bodyFace != null)
                  BodyPosition(x = bodyPositionX, y = bodyPositionY, face = bodyFace, zoneName = bodyPart ?: "")
              else null
          }
          BodyMapPicker(
              initialPosition = currentPosition,
              confirmLabel    = "Enregistrer",
              onConfirm = { position ->
                  scope.launch {
                      repository.updateMolePosition(moleId, position)
                      loadData()
                      showBodyMapEditor = false
                  }
              },
              onDismiss = { showBodyMapEditor = false },
          )
      }
  }
  ```

- [ ] **Step 2 : Ajouter BodyMapSection dans le LazyColumn**

  In the LazyColumn, after the `MoleStatusCard` item (search for `MoleStatusCard(mole = m)`), insert:
  ```kotlin
  // Position sur le corps
  item {
      BodyMapSection(
          mole = m,
          onEditPosition = { showBodyMapEditor = true },
      )
  }
  ```

- [ ] **Step 3 : Ajouter le composable privé BodyMapSection**

  At the bottom of `MoleDetailScreen.kt` (after all existing private composables), add:
  ```kotlin
  @Composable
  private fun BodyMapSection(mole: LocalMole, onEditPosition: () -> Unit) {
      val hasPosition = mole.bodyPositionX != null
      Card(
          modifier = Modifier.fillMaxWidth(),
          colors   = CardDefaults.cardColors(containerColor = Color.White),
          shape    = RoundedCornerShape(16.dp),
          border   = BorderStroke(1.dp, Color(0xFFE0E6ED)),
      ) {
          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .padding(16.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
              BodyMapThumbnail(
                  position = if (hasPosition) BodyPosition(
                      x        = mole.bodyPositionX!!,
                      y        = mole.bodyPositionY!!,
                      face     = mole.bodyFace!!,
                      zoneName = mole.bodyPart ?: "",
                  ) else null,
                  modifier = Modifier
                      .width(60.dp)
                      .height(120.dp),
              )
              Spacer(modifier = Modifier.width(16.dp))
              Column(modifier = Modifier.weight(1f)) {
                  Text(
                      text  = if (hasPosition) (mole.bodyPart ?: "Position enregistrée")
                              else "Position non définie",
                      style = MaterialTheme.typography.bodyMedium,
                  )
                  Spacer(modifier = Modifier.height(8.dp))
                  TextButton(
                      onClick = onEditPosition,
                      contentPadding = PaddingValues(0.dp),
                  ) {
                      Text(
                          text  = if (hasPosition) "Modifier la position" else "Ajouter une position",
                          color = Color(0xFF007AFF),
                      )
                  }
              }
          }
      }
  }
  ```

- [ ] **Step 4 : Ajouter les imports manquants**

  At the top of `MoleDetailScreen.kt`, add:
  ```kotlin
  import androidx.compose.ui.window.Dialog
  import com.grainbeaute.androidweb.model.BodyPosition
  ```

- [ ] **Step 5 : Commit**
  ```
  git add app/src/main/java/com/grainbeaute/androidweb/ui/screens/MoleDetailScreen.kt
  git commit -m "feat: add BodyMapSection with thumbnail and position editor to MoleDetailScreen"
  ```

---

## Notes d'implémentation

- **Gestion des conflits de gestes** : `detectTransformGestures` et `detectTapGestures` sont sur deux `pointerInput` séparés. Si des taps sont absorbés par le détecteur de transformation (notamment lors d'un zoom rapide), ajouter un délai minimum avant de considérer un toucher comme un tap. Tester sur un vrai appareil — l'émulateur ne simule pas bien le multi-touch.

- **Silhouette VectorDrawable** : Le path fourni est fonctionnel mais approximatif. Pour une version finale, remplacer les VectorDrawable par une silhouette importée depuis un SVG libre de droits (ex. Wikimedia Commons "Body outline") via Android Studio `File → New → Vector Asset → Local file`.

- **InboxScreen** : `InboxScreen.kt` appelle `repository.createMole(name, bodyPart)` (ancienne signature). Celle-ci est conservée telle quelle — pas besoin de la modifier pour cette feature.
