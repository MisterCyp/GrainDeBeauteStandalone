# Android App — Grain de Beauté (Mode Local)

**Stack :** Kotlin, Jetpack Compose (Material 3), Chaquopy 16.0.0, Room 2.6.1, CameraX, Coil
**Répertoire :** `graindebeaute_android_web/`

> **Mode 100% local** — aucun serveur, aucune connexion réseau. Tout l'analyse (OpenCV/ABCDE), le matching et le stockage s'exécutent directement sur le téléphone.

---

## Build

Ouvrir `graindebeaute_android_web/` dans **Android Studio** (Windows).

- `compileSdk = 34`, `minSdk = 24`, Java target 17
- Le build Gradle **ne fonctionne pas depuis WSL2** — utiliser Android Studio sous Windows
- `versionName = "2.0"` (mode local)

### Versions critiques (ne pas changer sans vérification)

| Composant | Version | Raison |
|---|---|---|
| Gradle | **8.7** | 8.8+ casse KSP1 (`friendPathsSet` state lock) |
| KSP | 2.0.21-1.0.28 | Doit rester en **KSP1** (`ksp.useKSP2=false`) |
| Room | 2.6.1 | Incompatible KSP2 (`unexpected jvm signature V`) |
| Chaquopy | 16.0.0 | Runtime Python embarqué |
| AGP | 8.5.2 | Compatible Gradle 8.7 |

---

## Architecture

```
UI (Compose) ──→ LocalRepository ──→ Chaquopy (Python) ──→ mole_logic.py
                       │                                     analysis_runner.py
                       ↓                                     matching_service_local.py
                 Room DB (SQLite)
                 context.filesDir/images/
```

### Fichiers clés

| Fichier | Rôle |
|---|---|
| `data/LocalRepository.kt` | Point d'entrée unique — CRUD Room + lancement analyse Python |
| `data/db/AppDatabase.kt` | Room DB singleton |
| `data/db/Entities.kt` | `MoleEntity`, `CaptureEntity` |
| `data/db/MoleDao.kt` | Requêtes Room grains |
| `data/db/CaptureDao.kt` | Requêtes Room captures |
| `model/LocalModels.kt` | `LocalMole`, `LocalCapture`, `LocalAnalysisResult`, `LocalMoleCandidate`, `LocalEvolutionPoint` |
| `GrainBeauteApplication.kt` | Init Chaquopy au démarrage (`Python.start()`) |
| `MainActivity.kt` | NavHost, point d'entrée `main` |

### Fichiers Python (`src/main/python/`)

| Fichier | Rôle |
|---|---|
| `mole_logic.py` | Algorithmes OpenCV — détection anneau, segmentation, métriques ABCDE |
| `analysis_runner.py` | Pipeline complet → retourne `json.dumps({...})` |
| `matching_service_local.py` | Similarité cosinus — `find_matching_moles_json()` retourne `json.dumps({...})` |

---

## Schéma Room DB

```
MoleEntity (id, name, bodyPart, createdAt)
 └── CaptureEntity (id, moleId NULLABLE, imagePath, analyzedImagePath,
                    croppedImagePath, status, errorMessage,
                    areaMm2, maxDimensionMm, circularity, asymmetry, colorVariation,
                    methodUsed, featureVector TEXT/JSON, suggestions TEXT/JSON,
                    isConfident, createdAt)
```

- `moleId = null` → capture non-assignée (mode Inbox/Rafale)
- `featureVector` : JSON array de 1044 floats (~4 Ko/capture)
- `suggestions` : JSON array de `LocalMoleCandidate`
- `status` : `"pending"` → `"done"` ou `"error"`

---

## Navigation

Point d'entrée : `main` (pas de login).

```
main                    → MainScreen (Bottom Navigation 3 onglets)
  ├── dashboard         → DashboardScreen (onglet 0)
  ├── identification    → InboxScreen (onglet 1, avec badge)
  └── paramètres        → SettingsScreen (onglet 2)

mole_detail/{moleId}    → MoleDetailScreen
capture_detail/{captureId} → CaptureDetailScreen
camera/{moleId}         → CameraScreen (moleId=-1 = mode rafale)
settings/about          → SettingsAboutScreen
calibration             → CalibrationScreen
```

---

## Écrans

### `MainScreen.kt`

Wrapper `Scaffold` + `NavigationBar` 3 onglets : **Dashboard** / **Identification** (badge) / **Paramètres**.
- Badge = nombre de captures non-assignées avec `status == "done"`
- Polling badge toutes les 5s + refresh à chaque retour de navigation

### `DashboardScreen.kt`

Liste des grains de beauté. Speed Dial FAB :
- 📷 "Mode rafale" → `camera/-1`
- ➕ "Nouveau grain" → dialog création

### `InboxScreen.kt`

Captures non-assignées (mode rafale). Propose automatiquement les grains similaires (matching cosinus).
- Polling tant que captures `pending` (délai 3s)
- Assignation via dropdown ou création d'un nouveau grain

### `CameraScreen.kt`

Prise de photo avec CameraX.
- **Mode rafale** (`moleId == -1`) : confirmation avant envoi, compteur de photos
- **Mode assigné** (`moleId >= 0`) : upload direct → navigation `capture_detail/{id}`
- Correction EXIF orientation via `ExifInterface` + `Matrix.postRotate()`

### `CaptureDetailScreen.kt`

Métriques ABCDE, images (vue normale / vue annotée avec contours), zoom plein écran.
- Polling `status == "pending"` toutes les 2s (max 90 tentatives = 3 min)
- Bouton "Relancer l'analyse" si `status == "error"`
- Composable `LocalCaptureImage` : charge les fichiers locaux via `File(path)` + Coil

### `MoleDetailScreen.kt`

Historique d'un grain : galerie de captures + graphe d'évolution (Canvas).
- Graphe `EvolutionChart` : aire, dimension max, circularité, asymétrie, couleur

### `CalibrationScreen.kt`

Calibration du centre optique de l'image (compensation de l'offset du spacer ring).

### `SettingsScreen.kt`

Deux entrées : **Matériel** (calibration) et **À propos**.

### `SettingsAboutScreen.kt`

Informations version + description de l'app.

---

## Pipeline d'analyse Python

```
run_analysis(image_path, output_dir)
  1. cv2.imread(image_path)
  2. detect_spacer_ring(img)         → center, radius  [ValueError si non détecté]
  3. mask_and_crop_spacer(img, ...)  → crop
  4. segment_mole_smart(crop)        → mask, contour, method ("HYST" ou "FIXE")
  5. calculate_dimensions/circularity/asymmetry/color_variation
  6. compute_feature_vector(crop, mask, metrics)  → 1044 floats
  7. Sauvegarde analyzed_*.png + cropped_*.png
  8. return json.dumps({...})        ← IMPORTANT : JSON string, pas dict Python
```

**Attention** : les fonctions Python retournent `json.dumps(...)`. Kotlin parse avec `JsonParser.parseString(...).asJsonObject`. Ne jamais accéder à un PyObject dict avec `result["key"]` (String Java) — ça retourne toujours null.

---

## Dépendances clés (`app/build.gradle.kts`)

| Lib | Version | Usage |
|---|---|---|
| Compose BOM | 2024.06.00 | UI Material 3 |
| Navigation Compose | 2.7.6 | Routing |
| CameraX | 1.3.1 | Preview + capture photo |
| Coil | 2.5.0 | Chargement images locales (`File(path)`) |
| Room + KSP | 2.6.1 + 2.0.21-1.0.28 | DB locale SQLite |
| Gson | 2.10.1 | JSON parsing (résultats Python) |
| Chaquopy | 16.0.0 | Runtime Python embarqué |
| DataStore | 1.0.0 | Calibration offset |

**Packages Python (Chaquopy) :** `opencv-python-headless`, `numpy`, `scikit-image`

---

## Pièges connus

| Problème | Cause | Solution |
|---|---|---|
| `result["key"]` → null | PyObject est `Map<PyObject,PyObject>` | Retourner JSON depuis Python, parser avec Gson |
| Build KSP échoue (friendPathsSet) | Gradle 8.8+ + KSP1 | Gradle 8.7 dans `gradle-wrapper.properties` |
| Build Room échoue (jvm signature V) | Room 2.6.1 + KSP2 | `ksp.useKSP2=false` dans `gradle.properties` |
| Zoom écran noir | `ZoomableAsyncImage` recevait un `ImageRequest` au lieu d'un `File` | Passer `File` directement |
| Logs Python | `print()` va dans Logcat tag `python.stdout` | Filtrer `python.stdout` dans Logcat |
