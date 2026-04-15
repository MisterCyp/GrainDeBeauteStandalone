# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build

**Build must be done from Android Studio on Windows — not from WSL2.** The Gradle build does not work from WSL2.

- Open the project root in Android Studio
- `compileSdk = 34`, `minSdk = 24`, Java/Kotlin target 17
- `versionName = "2.0"` (local mode)

### Critical version constraints — do not change without verifying

| Component | Version | Reason |
|---|---|---|
| Gradle | **8.7** | 8.8+ breaks KSP1 (`friendPathsSet` state lock) |
| KSP | 2.0.21-1.0.28 | Must stay on **KSP1** (`ksp.useKSP2=false` in `gradle.properties`) |
| Room | 2.6.1 | Incompatible with KSP2 (`unexpected jvm signature V`) |
| Chaquopy | 16.0.0 | Embedded Python runtime |
| AGP | 8.5.2 | Compatible with Gradle 8.7 |

## Architecture

```
UI (Compose) ──→ LocalRepository ──→ Chaquopy (Python) ──→ mole_logic.py
                       │                                     analysis_runner.py
                       ↓                                     matching_service_local.py
                 Room DB (SQLite)
                 context.filesDir/images/
```

100% local — no server, no network. All analysis (OpenCV/ABCDE), matching, and storage run on-device.

**Key Kotlin files:**
- `data/LocalRepository.kt` — single entry point for all CRUD + Python analysis launch
- `data/db/AppDatabase.kt` — Room singleton, version 5, migrations in `Migrations.kt`
- `data/db/Entities.kt` — all Room entities
- `model/LocalModels.kt` — domain models (`LocalMole`, `LocalCapture`, `LocalAnalysisResult`, etc.)
- `GrainBeauteApplication.kt` — initialises Chaquopy (`Python.start()`) at startup
- `MainActivity.kt` — NavHost, app entry point

**Python files (`src/main/python/`):**
- `mole_logic.py` — OpenCV algorithms: spacer ring detection, segmentation, ABCDE metrics
- `analysis_runner.py` — full pipeline, returns `json.dumps({...})`
- `matching_service_local.py` — cosine similarity matching, `find_matching_moles_json()` returns `json.dumps({...})`

## Room DB schema

```
MoleEntity              (id, name, bodyPart, createdAt)
CaptureEntity           (id, moleId NULLABLE, imagePath, analyzedImagePath,
                         croppedImagePath, status, errorMessage,
                         areaMm2, maxDimensionMm, circularity, asymmetry, colorVariation,
                         methodUsed, featureVector TEXT/JSON, suggestions TEXT/JSON,
                         isConfident, createdAt)
DermatologistVisitEntity (id, date, practitionerName, practitionerAddress, globalNote, createdAt)
MoleDiagnosisEntity      (id, visitId, moleId NULLABLE, moleName, category, note)
AppSettingsEntity        (id=1 singleton, nextAppointmentDate, practitionerName,
                          practitionerAddress, reminderDaysBefore)
```

- `moleId = null` on a capture → unassigned (Inbox/burst mode)
- `featureVector`: JSON array of 1044 floats (~4 KB/capture)
- `status`: `"pending"` → `"done"` or `"error"`
- `MoleDiagnosis.category`: `"benign"` | `"monitor"` | `"suspect"` | `"removed"`

## Navigation

```
main                       → MainScreen (bottom nav: Dashboard / Inbox / Settings)
mole_detail/{moleId}       → MoleDetailScreen
capture_detail/{captureId} → CaptureDetailScreen
camera/{moleId}            → CameraScreen (moleId=-1 = burst mode)
visit_detail/{visitId}     → VisitDetailScreen
settings/about             → SettingsAboutScreen
calibration                → CalibrationScreen
```

Badge on Inbox tab = count of unassigned captures with `status == "done"`. Polled every 5 s.

## Python/Kotlin integration — critical pitfalls

- **Python functions return `json.dumps(...)` (a JSON string), not a Python dict.** Parse in Kotlin with `JsonParser.parseString(...).asJsonObject` (Gson).
- **Never access a `PyObject` dict with a String key** (`result["key"]`) — always returns null because the map is `Map<PyObject, PyObject>`.
- Python `print()` output goes to Logcat under tag `python.stdout`.

## Analysis pipeline

```
run_analysis(image_path, output_dir)
  1. cv2.imread(image_path)
  2. detect_spacer_ring(img)         → center, radius  [ValueError if not detected]
  3. mask_and_crop_spacer(img, ...)  → crop
  4. segment_mole_smart(crop)        → mask, contour, method ("HYST" or "FIXE")
  5. calculate_dimensions / circularity / asymmetry / color_variation
  6. compute_feature_vector(crop, mask, metrics)  → 1044 floats
  7. Save analyzed_*.png + cropped_*.png
  8. return json.dumps({...})
```

## WorkManager

`AppointmentReminderWorker` schedules a `OneTimeWorkRequest` tagged `"appointment_reminder"` whenever the next appointment date is saved. Previous work is cancelled before scheduling a new one. Requires `POST_NOTIFICATIONS` permission (Android 13+).

## Python packages (Chaquopy)

`opencv-python-headless`, `numpy`, `scikit-image` — declared in `app/build.gradle.kts` under `chaquopy { defaultConfig { pip { ... } } }`.

## Specs

Design specs live in `docs/superpowers/specs/`. The 2026-04-15 spec covers the dermatologist dashboard redesign (health summary card, visit history, diagnosis badges on mole cards, appointment reminder notifications).
