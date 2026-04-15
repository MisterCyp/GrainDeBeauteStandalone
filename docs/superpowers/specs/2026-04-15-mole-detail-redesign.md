# MoleDetailScreen — Redesign

**Date :** 2026-04-15
**Projet :** GrainDeBeauté Android
**Scope :** Refonte de `MoleDetailScreen` + désactivation des métriques ABCDE non utilisées

---

## Contexte

L'écran actuel affiche d'abord une grille de toutes les captures, puis cinq graphes d'évolution (surface, dimension max, asymétrie, circularité, variation couleur). Le redesign met en avant l'état actuel du grain (photo + résumé clinique) avant de montrer l'historique.

---

## 1. Layout — LazyColumn

```
┌─────────────────────────────────────────┐
│ ← Dos                             [🗑]  │  ← TopAppBar existant
├─────────────────────────────────────────┤
│                                         │
│  ┌─────────────────────────────────┐    │
│  │                                 │    │
│  │       (photo hero — crop)       │    │  ← pleine largeur, ratio carré
│  │                                 │    │
│  └─────────────────────────────────┘    │
│                                         │
│  ┌── État actuel ──────────────────┐    │
│  │  [● Bénin]                      │    │  ← badge coloré (ou "Pas encore examiné")
│  │  Dernière photo : 12/04/2026    │    │
│  │  Dernière visite : 3 jan. 2025  │    │
│  │    Dr. Martin                   │    │
│  │    "grain stable"               │    │  ← latestDiagnosis.note (si présente)
│  │  Taille actuelle : 4.2 mm       │    │
│  └─────────────────────────────────┘    │
│                                         │
│  Taille (mm)               [graphe]     │
│                                         │
│  Surface (mm²)             [graphe]     │
│                                         │
│  ANALYSES                               │
│  [grille captures existante]            │
│                                         │
│  VISITES DERMATOLOGIQUES                │
│  ┌─────────────────────────────────┐    │
│  │ 3 jan. 2025 · Dr. Martin        │    │  ← tappable → visit_detail/{id}
│  │ [● Bénin]  "grain stable"       │    │
│  └─────────────────────────────────┘    │
│  ┌─────────────────────────────────┐    │
│  │ 12 juin 2024 · Dr. Martin       │    │
│  │ [● À surveiller]  —             │    │
│  └─────────────────────────────────┘    │
│                                         │
└─────────────────────────────────────────┘
                              [FAB 📷]
```

---

## 2. Sections en détail

### 2.1 Photo hero

- Source : `lastCapture?.croppedImagePath` via Coil (`File(path)`)
- Dimensions : pleine largeur, ratio 1:1, coins arrondis (`RoundedCornerShape(12.dp)`)
- Si `lastCapture == null` ou `croppedImagePath == null` : placeholder gris avec icône `CameraAlt` centré
- Si `lastCapture.status == "pending"` : overlay avec `CircularProgressIndicator`

### 2.2 Card "État actuel"

Champs affichés :

| Ligne | Source | Fallback |
|---|---|---|
| Badge statut | `latestDiagnosis.category` (couleurs dashboard) | Chip gris "Pas encore examiné" |
| Dernière photo | `lastCapture.createdAt` formaté `dd MMM yyyy` | "Aucune photo" |
| Dernière visite | `latestDiagnosis` → `visitId` → date de la visite + `practitionerName` | "Aucune visite" |
| Note visite | `latestDiagnosis.note` (1 ligne, ellipsis) | masqué si null |
| Taille actuelle | `lastCapture.analysisResult?.maxDimensionMm` → `"X.X mm"` | `"—"` |

Pour récupérer la date et le praticien de `latestDiagnosis`, `LocalRepository` doit exposer la visite associée. Option : ajouter `visitDate: Long` et `visitPractitionerName: String?` directement dans `LocalMoleDiagnosis`, peuplés lors du mapping en repository.

### 2.3 Graphes d'évolution

Deux graphes, dans l'ordre :
1. **"Taille (mm)"** — série `maxDimensionMm` (non-null uniquement)
2. **"Surface (mm²)"** — série `areaMm2` (non-null uniquement)

Composable existant `EvolutionChartCard` réutilisé. Les graphes n'apparaissent que si la série contient ≥ 2 points (comportement inchangé).

Graphes supprimés : asymétrie, circularité, variation couleur.

### 2.4 Section "Analyses"

Titre `"Analyses"` + grille de captures existante (`CaptureGridItem`), comportement inchangé.

### 2.5 Section "Visites dermatologiques"

- Titre `"Visites dermatologiques"` affiché seulement si la liste est non vide
- Chaque item : `Card` cliquable → `navController.navigate("visit_detail/${visit.id}")`
- Contenu d'un item :
  - Ligne 1 : date formatée `dd MMM yyyy` + `· PratiqueNom` (si présent)
  - Ligne 2 : badge catégorie (même composable que dashboard) + note tronquée (1 ligne, ellipsis), ou `"—"` si note absente
- Données : `repository.getDiagnosesForMole(moleId)` → liste de `LocalMoleDiagnosis` enrichie de `visitDate` et `visitPractitionerName`, triée par date DESC

---

## 3. Données nécessaires

### Modèle `LocalMoleDiagnosis` — ajout de champs de dénormalisation

```kotlin
data class LocalMoleDiagnosis(
    val id: Int,
    val visitId: Int,
    val moleId: Int?,
    val moleName: String,
    val category: DiagnosisCategory,
    val note: String?,
    val visitDate: Long,                    // nouveau — peuplé par le repository
    val visitPractitionerName: String?,     // nouveau — peuplé par le repository
)
```

Ces champs sont peuplés en repository via une jointure ou deux requêtes DAOs (pas de changement de schéma Room).

### Repository — nouvelle méthode

```kotlin
suspend fun getDiagnosesForMole(moleId: Int): List<LocalMoleDiagnosis>
```

Retourne toutes les `MoleDiagnosisEntity` pour ce grain, enrichies des infos de visite, triées par `visitDate` DESC.

---

## 4. Désactivation des métriques Python

Dans `mole_logic.py` et `analysis_runner.py`, les calculs suivants sont commentés et remplacés par `None` dans le JSON retourné :

- `circularity`
- `asymmetry`
- `color_variation`

Les champs `featureVector` (1044 floats) restent calculés car utilisés pour le matching cosinus dans `matching_service_local.py`.

Les colonnes Room (`circularity`, `asymmetry`, `colorVariation`) restent en base — valeur `null` pour toutes les nouvelles captures. Pas de migration nécessaire.

---

## 5. Fichiers à modifier

| Fichier | Changement |
|---|---|
| `ui/screens/MoleDetailScreen.kt` | Refonte complète du layout ; suppression des 3 graphes ; ajout section visites |
| `model/LocalModels.kt` | Ajout `visitDate` + `visitPractitionerName` dans `LocalMoleDiagnosis` |
| `data/LocalRepository.kt` | Ajout `getDiagnosesForMole(moleId)` |
| `app/src/main/python/mole_logic.py` | Commenter calculs circularity / asymmetry / color_variation |
| `app/src/main/python/analysis_runner.py` | Retourner `None` pour ces 3 métriques dans le JSON |

---

## Hors scope

- Modification de `VisitDetailScreen` (écran existant, inchangé)
- Suppression des colonnes Room (conservées pour compatibilité)
- Recalcul rétroactif des captures existantes
