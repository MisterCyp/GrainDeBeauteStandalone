# Dashboard Dermatologue — Design Spec

**Date :** 2026-04-15  
**Projet :** GrainDeBeauté Android  
**Scope :** Refonte du dashboard + ajout du suivi dermatologue

---

## Contexte

L'application permet le suivi photographique de grains de beauté à l'aide d'un dermatoscope imprimé en 3D. Le dashboard actuel est une simple liste de grains (nom, photo, temps depuis dernière analyse). On ajoute un suivi des consultations dermatologiques : historique des visites, diagnostics par grain, prochain rendez-vous et rappel de notification.

---

## 1. Modèle de données

### Nouvelles tables Room (migration version actuelle → +1)

```kotlin
@Entity(tableName = "dermatologist_visits")
data class DermatologistVisitEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val date: Long,
    val globalNote: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "mole_diagnoses",
    foreignKeys = [
        ForeignKey(
            entity = DermatologistVisitEntity::class,
            parentColumns = ["id"],
            childColumns = ["visitId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = MoleEntity::class,
            parentColumns = ["id"],
            childColumns = ["moleId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("visitId"), Index("moleId")]
)
data class MoleDiagnosisEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val visitId: Int,
    val moleId: Int?,           // nullable : grain peut être supprimé après
    val moleName: String,       // snapshot du nom au moment de la visite
    val category: String,       // "benign" | "monitor" | "suspect" | "removed"
    val note: String? = null,
)

@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,  // singleton
    val nextAppointmentDate: Long? = null,
    val practitionerName: String? = null,
    val practitionerPhone: String? = null,
    val practitionerEmail: String? = null,
    val reminderDaysBefore: Int = 7,
)
```

### Nouveaux DAOs

- `DermatologistVisitDao` : insert, update, delete, getAll (ORDER BY date DESC), getById
- `MoleDiagnosisDao` : insertAll, deleteByVisit, getByVisit, getLatestByMole (dernière par moleId)
- `AppSettingsDao` : upsert, get

### Modèles locaux

```kotlin
enum class DiagnosisCategory { BENIGN, MONITOR, SUSPECT, REMOVED }

data class LocalMoleDiagnosis(
    val id: Int,
    val visitId: Int,
    val moleId: Int?,
    val moleName: String,
    val category: DiagnosisCategory,
    val note: String?,
)

data class LocalDermatologistVisit(
    val id: Int,
    val date: Long,
    val globalNote: String?,
    val diagnoses: List<LocalMoleDiagnosis>,
)

data class LocalAppSettings(
    val nextAppointmentDate: Long?,
    val practitionerName: String?,
    val practitionerPhone: String?,
    val practitionerEmail: String?,
    val reminderDaysBefore: Int,
)
```

**Impact sur `LocalMole` :** ajout du champ `latestDiagnosis: LocalMoleDiagnosis?` — peuplé via `MoleDiagnosisDao.getLatestByMole()` lors de `getMoles()`.

**Impact sur `LocalRepository` :** ajout des méthodes CRUD pour visites, diagnostics et settings.

---

## 2. Dashboard restructuré

Le `DashboardScreen` est réorganisé en une `LazyColumn` avec 3 sections distinctes.

### Wireframe global

```
┌─────────────────────────────────────────┐
│ ● Mes Grains de Beauté          [menu] │  ← TopAppBar existant
├─────────────────────────────────────────┤
│                                         │
│ ┌─────────────────────────────────────┐ │
│ │  RÉSUMÉ SANTÉ                       │ │
│ │  ○ 12 grains de beauté              │ │
│ │  📅 Dernière visite : 3 jan. 2025   │ │
│ │  ┌───────────────────────────────┐  │ │
│ │  │ 📆 Prochain RDV : 15 mai 2026 │  │ │  ← tappable → bottom sheet RDV
│ │  │    Dr. Martin · 06 12 34 56   │  │ │
│ │  └───────────────────────────────┘  │ │
│ └─────────────────────────────────────┘ │
│                                         │
│  MES GRAINS                             │  ← titre de section
│ ┌─────────────────────────────────────┐ │
│ │ (●) Dos         [● Bénin]  🗑       │ │
│ │     Haut du dos                     │ │
│ │     Bénin · vu il y a 3 mois        │ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ (●) Bras droit  [● À surveiller] 🗑 │ │
│ │     Avant-bras                      │ │
│ │     À surveiller · vu il y a 6 mois │ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ (●) Épaule      [  —  ]       🗑   │ │
│ │     Épaule gauche                   │ │
│ │     Pas encore examiné              │ │
│ └─────────────────────────────────────┘ │
│                                         │
│  VISITES DERMATOLOGIQUES                │  ← titre de section
│  [ + Nouvelle visite ]                  │  ← bouton outlined
│ ┌─────────────────────────────────────┐ │
│ │ 3 jan. 2025                         │ │  ← tappable → VisitDetailScreen
│ │ "Suivi annuel, RAS globalement"      │ │
│ │ 3 grains examinés                   │ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ 12 juin 2024                        │ │
│ │ "Première consultation"             │ │
│ │ 5 grains examinés                   │ │
│ └─────────────────────────────────────┘ │
│                                         │
└─────────────────────────────────────────┘
                              [FAB existant]
```

### Section 1 — Résumé santé (carte en haut)

Affiche :
- Nombre total de grains de beauté
- Dernière visite : date formatée (ex: "3 janvier 2025") ou "Aucune visite" si vide
- Prochain RDV : date + nom du praticien (ex: "15 mai 2026 · Dr. Martin") ou "Non planifié"
- Tap sur le prochain RDV → ouvre le bottom sheet d'édition des settings

### Section 2 — Mes grains

Liste des `MoleCard` avec ajouts visuels :
- **Badge coloré** à droite du nom :
  - 🟢 Bénin (`Color(0xFF4CAF50)`)
  - 🟠 À surveiller (`Color(0xFFFF9800)`)
  - 🔴 Suspect (`Color(0xFFF44336)`)
  - ⚫ Retiré (`Color(0xFF9E9E9E)`)
  - Gris clair = pas de diagnostic
- **Ligne de diagnostic** sous la partie du corps : `"Bénin · vu il y a 3 mois"` (catégorie + date de la visite où il a été examiné en dernier). Si aucun diagnostic : `"Pas encore examiné"`

### Wireframe d'une MoleCard

```
┌──────────────────────────────────────────────┐
│  ┌──────┐   Dos              [● Bénin]   🗑  │
│  │ 📷   │   Haut du dos                      │
│  │(photo│   Bénin · vu il y a 3 mois         │
│  └──────┘                                    │
└──────────────────────────────────────────────┘

Légende badge :
  [● Bénin]        fond vert    #4CAF50
  [● À surveiller] fond orange  #FF9800
  [● Suspect]      fond rouge   #F44336
  [● Retiré]       fond gris    #9E9E9E
  [  —  ]          pas de diagnostic (gris clair, texte gris)
```

### Section 3 — Visites dermatologiques

- Bouton "Nouvelle visite" en tête de section
- Liste des visites passées (ORDER BY date DESC), chaque item affiche :
  - Date formatée
  - Note globale tronquée (1 ligne, ellipsis)
  - Nombre de grains examinés lors de cette visite (ex: "3 grains examinés")
- Tap sur une visite → `VisitDetailScreen`

---

## 3. Saisie d'une nouvelle visite

**Point d'entrée :** bouton "Nouvelle visite" dans la section 3 du dashboard.

**UI :** `ModalBottomSheet` en 2 étapes (même bottom sheet, contenu qui change).

### Flow de navigation

```
Dashboard
    │
    │ tap "Nouvelle visite"
    ▼
┌─────────────────────────────┐
│  ModalBottomSheet           │
│  ÉTAPE 1 / 2 — Infos        │
│                             │
│  Date : [15 avr. 2026]  📅  │  ← DatePickerDialog au tap
│  Note : [               ]  │
│         [               ]  │
│                             │
│              [ Suivant → ]  │
└─────────────────────────────┘
    │
    │ tap "Suivant"
    ▼
┌─────────────────────────────┐
│  ModalBottomSheet           │
│  ÉTAPE 2 / 2 — Diagnostics  │
│                             │
│ ┌─────────────────────────┐ │
│ │(●)Dos      ☑ examiné    │ │
│ │   [Bénin ▼] [note...]   │ │
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │(●)Bras dr. ☑ examiné    │ │
│ │   [Suspect▼] [note...]  │ │
│ └─────────────────────────┘ │
│ ┌─────────────────────────┐ │
│ │(●)Épaule   ☐ examiné    │ │
│ └─────────────────────────┘ │
│                             │
│  [ ← Retour ]  [Enreg. ✓]  │
└─────────────────────────────┘
    │
    │ tap "Enregistrer"
    ▼
Dashboard (rechargé)
  → nouvelle visite apparaît dans l'historique
  → badges des grains mis à jour
```

### Étape 1 — Infos générales
- Sélecteur de date (DatePickerDialog Android natif), pré-rempli à aujourd'hui
- Champ texte libre "Note globale" (optionnel, multiline)
- Bouton "Suivant →"

### Étape 2 — Diagnostics par grain
- Liste de tous les grains existants, chacun présenté avec :
  - Photo vignette circulaire (dernière capture du grain, comme dans `MoleCard`)
  - Nom du grain + partie du corps
  - Checkbox "Examiné lors de cette visite"
  - Si coché : dropdown catégorie (Bénin / À surveiller / Suspect / Retiré) + champ note optionnel
- Boutons "← Retour" et "Enregistrer ✓"
- "Enregistrer" : crée `DermatologistVisitEntity` + `MoleDiagnosisEntity` pour chaque grain coché → ferme le bottom sheet → recharge le dashboard

### Écran détail d'une visite (`VisitDetailScreen`)

```
┌─────────────────────────────────────────┐
│ ← 3 janvier 2025              [Modifier]│  ← TopAppBar
├─────────────────────────────────────────┤
│                                         │
│  "Suivi annuel, RAS globalement"        │  ← note globale
│                                         │
│  GRAINS EXAMINÉS                        │
│ ┌─────────────────────────────────────┐ │
│ │ (●) Dos           [● Bénin]         │ │
│ │     "grain stable, pas d'évolution" │ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ (●) Bras droit    [● À surveiller]  │ │
│ │     "revoir dans 6 mois"            │ │
│ └─────────────────────────────────────┘ │
│ ┌─────────────────────────────────────┐ │
│ │ (●) Épaule        [● Bénin]         │ │
│ │     —                               │ │
│ └─────────────────────────────────────┘ │
│                                         │
└─────────────────────────────────────────┘
```

- Navigation : `navController.navigate("visit_detail/{visitId}")`
- Bouton "Modifier" (top-right) → ouvre le même bottom sheet en mode édition (pré-rempli)

---

## 4. Settings — Prochain RDV

**Accès :** tap sur la zone "Prochain RDV" dans la section résumé santé du dashboard.

### Wireframe bottom sheet RDV

```
┌─────────────────────────────────────────┐
│  ▬▬▬  (drag handle)                    │
│                                         │
│  Prochain rendez-vous                   │
│                                         │
│  Date du RDV                            │
│  [ 15 mai 2026                     📅 ] │
│                                         │
│  Praticien                              │
│  [ Dr. Martin                          ]│
│                                         │
│  Téléphone                              │
│  [ 06 12 34 56 78                      ]│
│                                         │
│  Email                                  │
│  [ cabinet.martin@exemple.fr           ]│
│                                         │
│  Rappel avant le RDV                    │
│  [ 7 jours              ▼ ]            │
│    1 j / 3 j / 7 j / 14 j              │
│                                         │
│  [ Annuler ]          [ Enregistrer ]   │
└─────────────────────────────────────────┘
```

**UI :** `ModalBottomSheet` simple avec :
- Sélecteur de date pour le RDV
- Champ "Nom du praticien" (ex: "Dr. Martin")
- Champ "Téléphone"
- Champ "Email"
- Slider ou dropdown "Rappel X jours avant" (options : 1, 3, 7, 14 jours)
- Boutons "Annuler" et "Enregistrer"

**Paramètre reminderDaysBefore** également exposé dans `SettingsScreen` existant.

---

## 5. Notifications de rappel

**Technologie :** `WorkManager` avec `OneTimeWorkRequest`.

**Déclenchement :** à chaque sauvegarde du prochain RDV → annulation du work précédent (tag unique `"appointment_reminder"`) + création d'un nouveau work planifié à `(dateRDV - reminderDaysBefore jours)`.

**Contenu de la notification :**
```
Titre : "Rappel dermatologue"
Corps  : "Votre RDV chez [praticien] est dans [N] jours ([date]).
          Pensez à photographier vos grains avant la consultation."
```
Si pas de nom de praticien : "Votre rendez-vous dermatologique est dans [N] jours."

**Permission :** `POST_NOTIFICATIONS` demandée au moment de la première saisie d'un RDV (Android 13+). Si refusée, le RDV est quand même sauvegardé mais sans notification (message informatif affiché).

**Survie aux redémarrages :** `WorkManager` est persistant par nature — le rappel survit à un redémarrage du téléphone.

---

## 6. Fichiers à créer ou modifier

### Nouveaux fichiers
- `data/db/DermatologistVisitDao.kt`
- `data/db/MoleDiagnosisDao.kt`
- `data/db/AppSettingsDao.kt`
- `ui/screens/VisitDetailScreen.kt`
- `workers/AppointmentReminderWorker.kt`

### Fichiers modifiés
- `data/db/Entities.kt` — ajout des 3 nouvelles entités
- `data/db/AppDatabase.kt` — version bump + migration + nouveaux DAOs
- `model/LocalModels.kt` — nouveaux modèles locaux + `latestDiagnosis` sur `LocalMole`
- `data/LocalRepository.kt` — nouvelles méthodes CRUD
- `ui/screens/DashboardScreen.kt` — refonte complète
- `ui/screens/MainScreen.kt` — ajout route `visit_detail/{visitId}`
- `ui/screens/SettingsScreen.kt` — ajout paramètre `reminderDaysBefore`
- `AndroidManifest.xml` — permission `POST_NOTIFICATIONS`
- `build.gradle` — dépendance `WorkManager`

---

## Hors scope

- Synchronisation cloud des visites
- Partage du rapport de visite (PDF, email)
- Gestion de plusieurs dermatologues
- Photos prises lors de la visite (couvert par le flux caméra existant)
