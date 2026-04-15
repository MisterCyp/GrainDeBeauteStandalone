# Silhouettes genrées — BodyMapPicker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remplacer les 2 silhouettes génériques par 4 silhouettes genrées (femme/homme × face/dos), avec un réglage de genre dans les Paramètres.

**Architecture:** Un script Python découpe le SVG source en 4 fichiers; après import manuel dans Android Studio, les 4 VectorDrawables alimentent `BodyMapPicker` et `BodyMapThumbnail` via un nouveau paramètre `gender`. Le genre est persisté dans `AppSettingsEntity` (DB v6→v7) et propagé aux callers via `repository.getAppSettings()`.

**Tech Stack:** Kotlin + Jetpack Compose, Room (SQLite), Python 3 (script one-shot), Android Studio Vector Asset import.

---

## Fichiers touchés

| Fichier | Action |
|---------|--------|
| `docs/split_silhouettes.py` | Créer |
| `docs/silhouettes/*.svg` (4 fichiers) | Généré par le script |
| `app/src/main/res/drawable/ic_body_woman_front.xml` | Créer (import Android Studio) |
| `app/src/main/res/drawable/ic_body_woman_back.xml` | Créer (import Android Studio) |
| `app/src/main/res/drawable/ic_body_man_front.xml` | Créer (import Android Studio) |
| `app/src/main/res/drawable/ic_body_man_back.xml` | Créer (import Android Studio) |
| `app/src/main/java/.../data/db/Entities.kt` | Modifier — ajout `bodyGender` |
| `app/src/main/java/.../data/db/Migrations.kt` | Modifier — ajout `MIGRATION_6_7` |
| `app/src/main/java/.../data/db/AppDatabase.kt` | Modifier — version 6→7 |
| `app/src/main/java/.../model/LocalModels.kt` | Modifier — ajout `bodyGender` à `LocalAppSettings` |
| `app/src/main/java/.../data/LocalRepository.kt` | Modifier — 3 endroits |
| `app/src/main/java/.../ui/screens/BodyMapPicker.kt` | Modifier — param `gender` |
| `app/src/main/java/.../ui/screens/SettingsScreen.kt` | Modifier — toggle genre |
| `app/src/main/java/.../ui/screens/MoleDetailScreen.kt` | Modifier — charger + passer genre |
| `app/src/main/java/.../ui/screens/MolesScreen.kt` | Modifier — charger + passer genre |

---

## Task 1 : Script Python — découpe du SVG source

**Files:**
- Create: `docs/split_silhouettes.py`
- Output: `docs/silhouettes/woman_front.svg`, `docs/silhouettes/woman_back.svg`, `docs/silhouettes/man_front.svg`, `docs/silhouettes/man_back.svg`

- [ ] **Étape 1 : Écrire `docs/split_silhouettes.py`**

```python
#!/usr/bin/env python3
"""
Découpe SilhouetteHommeFemmeFaceDos.svg en 4 silhouettes autonomes.
Résultat dans docs/silhouettes/ : woman_front.svg, man_front.svg,
woman_back.svg, man_back.svg.

Les groupes sont dans layer1 (translate(-64.704637,-62.604335)) :
  g5268 → femme face   (haut gauche)
  g5423 → homme face   (haut droite)
  g5553 → femme dos    (bas gauche)
  g4158 → homme dos    (bas droite)
"""
import re
import os
from copy import deepcopy
import xml.etree.ElementTree as ET

SVG_NS = 'http://www.w3.org/2000/svg'

# Préserver les namespaces dans les fichiers de sortie
for prefix, uri in [
    ('', SVG_NS),
    ('dc',       'http://purl.org/dc/elements/1.1/'),
    ('cc',       'http://creativecommons.org/ns#'),
    ('rdf',      'http://www.w3.org/1999/02/22-rdf-syntax-ns#'),
    ('xlink',    'http://www.w3.org/1999/xlink'),
    ('inkscape', 'http://www.inkscape.org/namespaces/inkscape'),
    ('sodipodi', 'http://sodipodi.sourceforge.net/DTD/sodipodi-0.0.dtd'),
]:
    ET.register_namespace(prefix, uri)


def parse_translate(transform_str: str):
    """Extrait (tx, ty) d'un attribut 'translate(tx, ty)'."""
    if not transform_str:
        return 0.0, 0.0
    m = re.search(r'translate\(\s*([-\d.]+)\s*,\s*([-\d.]+)\s*\)', transform_str)
    return (float(m.group(1)), float(m.group(2))) if m else (0.0, 0.0)


def bbox_of_group(group, tx: float, ty: float, padding: float = 8.0):
    """
    Calcule la bounding box viewport d'un groupe (coordonnées path + translate).
    Retourne (vx, vy, vw, vh) pour l'attribut viewBox.
    Approche : extraire tous les nombres du path data et les traiter par paires (x,y).
    Suffit pour des paths majoritairement absolus (Inkscape).
    """
    all_x, all_y = [], []
    for elem in group.iter(f'{{{SVG_NS}}}path'):
        d = elem.get('d', '')
        nums = [float(n) for n in re.findall(
            r'[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?', d
        )]
        for i in range(0, len(nums) - 1, 2):
            all_x.append(nums[i] + tx)
            all_y.append(nums[i + 1] + ty)

    if not all_x:
        return 0.0, 0.0, 100.0, 200.0

    x0 = min(all_x) - padding
    y0 = min(all_y) - padding
    x1 = max(all_x) + padding
    y1 = max(all_y) + padding
    return x0, y0, x1 - x0, y1 - y0


def write_silhouette(group, tx: float, ty: float, name: str, out_dir: str):
    """Écrit un SVG autonome pour un groupe, avec la bonne viewBox."""
    vx, vy, vw, vh = bbox_of_group(group, tx, ty)
    aspect = vh / vw if vw else 2.0

    root = ET.Element('svg')
    root.set('xmlns', SVG_NS)
    root.set('version', '1.1')
    root.set('viewBox', f'{vx:.3f} {vy:.3f} {vw:.3f} {vh:.3f}')
    # Dimensions intrinsèques neutres ; Android Studio les ignore au profit du viewBox
    root.set('width', '100')
    root.set('height', f'{100 * aspect:.1f}')

    g = ET.SubElement(root, 'g')
    g.set('transform', f'translate({tx:.6f},{ty:.6f})')
    for child in group:
        g.append(deepcopy(child))

    os.makedirs(out_dir, exist_ok=True)
    out_path = os.path.join(out_dir, f'{name}.svg')
    ET.ElementTree(root).write(out_path, xml_declaration=True, encoding='UTF-8')
    print(f'  ✓  {out_path}  (viewBox: {vx:.1f} {vy:.1f} {vw:.1f} {vh:.1f})')


def main():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    src = os.path.join(script_dir, 'SilhouetteHommeFemmeFaceDos.svg')
    out_dir = os.path.join(script_dir, 'silhouettes')

    print(f'Source : {src}')
    tree = ET.parse(src)
    root = tree.getroot()

    layer1 = root.find(f'.//{{{SVG_NS}}}g[@id="layer1"]')
    if layer1 is None:
        raise RuntimeError('layer1 introuvable dans le SVG')

    tx, ty = parse_translate(layer1.get('transform', ''))
    print(f'Layer translate : ({tx}, {ty})')

    children = list(layer1)
    if len(children) < 4:
        raise RuntimeError(f'Attendu 4 groupes dans layer1, trouvé {len(children)}')

    # Ordre dans le SVG source : femme face, homme face, femme dos, homme dos
    configs = [
        ('woman_front', children[0]),
        ('man_front',   children[1]),
        ('woman_back',  children[2]),
        ('man_back',    children[3]),
    ]

    print(f'\nGénération des silhouettes dans {out_dir}/')
    for name, group in configs:
        write_silhouette(group, tx, ty, name, out_dir)

    print('\nTerminé. Vérifie les 4 SVG dans un navigateur avant l\'import Android Studio.')
    print('Si une silhouette est mal orientée (femme↔homme), échange les noms dans `configs`.')


if __name__ == '__main__':
    main()
```

- [ ] **Étape 2 : Exécuter le script**

```bash
cd /mnt/c/Users/miste/Documents/graindebeaute_android_standalone/docs
python3 split_silhouettes.py
```

Résultat attendu :
```
Source : .../docs/SilhouetteHommeFemmeFaceDos.svg
Layer translate : (-64.704637, -62.604335)

Génération des silhouettes dans .../docs/silhouettes/
  ✓  silhouettes/woman_front.svg  (viewBox: ...)
  ✓  silhouettes/man_front.svg    (viewBox: ...)
  ✓  silhouettes/woman_back.svg   (viewBox: ...)
  ✓  silhouettes/man_back.svg     (viewBox: ...)

Terminé.
```

- [ ] **Étape 3 : Vérifier visuellement les 4 SVG**

Ouvrir chaque fichier dans un navigateur (drag & drop) et confirmer :
- `woman_front.svg` → silhouette féminine de face (seins, hanches marquées)
- `man_front.svg` → silhouette masculine de face
- `woman_back.svg` → silhouette féminine de dos
- `man_back.svg` → silhouette masculine de dos

Si femme et homme sont inversés : dans `split_silhouettes.py`, échanger `children[0]` ↔ `children[1]` et `children[2]` ↔ `children[3]`, puis relancer.

---

## Task 2 : Import des SVG dans Android Studio (étape manuelle)

**Files:**
- Create (manual): `app/src/main/res/drawable/ic_body_woman_front.xml`
- Create (manual): `app/src/main/res/drawable/ic_body_woman_back.xml`
- Create (manual): `app/src/main/res/drawable/ic_body_man_front.xml`
- Create (manual): `app/src/main/res/drawable/ic_body_man_back.xml`

- [ ] **Étape 1 : Importer chaque SVG**

Dans Android Studio, répéter 4 fois (une fois par silhouette) :

1. **File > New > Vector Asset**
2. Asset type : **Local file (SVG, PSD)**
3. Sélectionner le fichier dans `docs/silhouettes/`
4. Nommer la ressource exactement :
   - `woman_front.svg` → `ic_body_woman_front`
   - `woman_back.svg` → `ic_body_woman_back`
   - `man_front.svg` → `ic_body_man_front`
   - `man_back.svg` → `ic_body_man_back`
5. Cliquer **Next > Finish**

- [ ] **Étape 2 : Vérifier les fichiers générés**

Confirmer que ces 4 fichiers existent :
```
app/src/main/res/drawable/ic_body_woman_front.xml
app/src/main/res/drawable/ic_body_woman_back.xml
app/src/main/res/drawable/ic_body_man_front.xml
app/src/main/res/drawable/ic_body_man_back.xml
```

Dans Android Studio, ouvrir chaque XML et cliquer l'onglet **Preview** pour vérifier que la silhouette s'affiche correctement.

---

## Task 3 : Migration Room — ajout de `bodyGender` (DB v6 → v7)

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/data/db/Entities.kt`
- Modify: `app/src/main/java/com/grainbeaute/androidweb/data/db/Migrations.kt`
- Modify: `app/src/main/java/com/grainbeaute/androidweb/data/db/AppDatabase.kt`

- [ ] **Étape 1 : Ajouter `bodyGender` à `AppSettingsEntity`**

Dans `Entities.kt`, remplacer la `data class AppSettingsEntity` :

```kotlin
// AVANT
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val nextAppointmentDate: Long? = null,
    val practitionerName: String? = null,
    val practitionerAddress: String? = null,
    val reminderDaysBefore: Int = 7,
)

// APRÈS
@Entity(tableName = "app_settings")
data class AppSettingsEntity(
    @PrimaryKey val id: Int = 1,
    val nextAppointmentDate: Long? = null,
    val practitionerName: String? = null,
    val practitionerAddress: String? = null,
    val reminderDaysBefore: Int = 7,
    val bodyGender: String = "female",
)
```

- [ ] **Étape 2 : Ajouter `MIGRATION_6_7` dans `Migrations.kt`**

Après le bloc `MIGRATION_5_6`, avant `val ALL`, insérer :

```kotlin
/**
 * Migration de la version 6 à 7 :
 * - Ajout de bodyGender à app_settings (défaut "female")
 */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `app_settings` ADD COLUMN `bodyGender` TEXT NOT NULL DEFAULT 'female'"
        )
    }
}
```

Puis mettre à jour `val ALL` pour inclure la nouvelle migration :

```kotlin
val ALL = arrayOf(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
```

- [ ] **Étape 3 : Passer `AppDatabase` à la version 7**

Dans `AppDatabase.kt`, changer `version = 6` en `version = 7` :

```kotlin
// Chercher la ligne :
version = 6,
// Remplacer par :
version = 7,
```

---

## Task 4 : Modèle domaine — `LocalAppSettings` et `LocalRepository`

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/model/LocalModels.kt`
- Modify: `app/src/main/java/com/grainbeaute/androidweb/data/LocalRepository.kt`

- [ ] **Étape 1 : Ajouter `bodyGender` à `LocalAppSettings`**

Dans `LocalModels.kt`, remplacer :

```kotlin
// AVANT
data class LocalAppSettings(
    val nextAppointmentDate: Long?,
    val practitionerName: String?,
    val practitionerAddress: String?,
    val reminderDaysBefore: Int,
)

// APRÈS
data class LocalAppSettings(
    val nextAppointmentDate: Long?,
    val practitionerName: String?,
    val practitionerAddress: String?,
    val reminderDaysBefore: Int,
    val bodyGender: String = "female",
)
```

- [ ] **Étape 2 : Mettre à jour `getAppSettings()` dans `LocalRepository`**

```kotlin
// AVANT
suspend fun getAppSettings(): LocalAppSettings = withContext(Dispatchers.IO) {
    settingsDao.get()?.toLocalSettings() ?: LocalAppSettings(
        nextAppointmentDate = null,
        practitionerName = null,
        practitionerAddress = null,
        reminderDaysBefore = 7
    )
}

// APRÈS
suspend fun getAppSettings(): LocalAppSettings = withContext(Dispatchers.IO) {
    settingsDao.get()?.toLocalSettings() ?: LocalAppSettings(
        nextAppointmentDate = null,
        practitionerName = null,
        practitionerAddress = null,
        reminderDaysBefore = 7,
        bodyGender = "female",
    )
}
```

- [ ] **Étape 3 : Mettre à jour `updateAppSettings()` dans `LocalRepository`**

```kotlin
// AVANT
suspend fun updateAppSettings(settings: LocalAppSettings) = withContext(Dispatchers.IO) {
    settingsDao.upsert(
        AppSettingsEntity(
            nextAppointmentDate = settings.nextAppointmentDate,
            practitionerName = settings.practitionerName,
            practitionerAddress = settings.practitionerAddress,
            reminderDaysBefore = settings.reminderDaysBefore
        )
    )
}

// APRÈS
suspend fun updateAppSettings(settings: LocalAppSettings) = withContext(Dispatchers.IO) {
    settingsDao.upsert(
        AppSettingsEntity(
            nextAppointmentDate = settings.nextAppointmentDate,
            practitionerName = settings.practitionerName,
            practitionerAddress = settings.practitionerAddress,
            reminderDaysBefore = settings.reminderDaysBefore,
            bodyGender = settings.bodyGender,
        )
    )
}
```

- [ ] **Étape 4 : Mettre à jour `toLocalSettings()` dans `LocalRepository`**

```kotlin
// AVANT
private fun AppSettingsEntity.toLocalSettings() = LocalAppSettings(
    nextAppointmentDate = nextAppointmentDate,
    practitionerName = practitionerName,
    practitionerAddress = practitionerAddress,
    reminderDaysBefore = reminderDaysBefore
)

// APRÈS
private fun AppSettingsEntity.toLocalSettings() = LocalAppSettings(
    nextAppointmentDate = nextAppointmentDate,
    practitionerName = practitionerName,
    practitionerAddress = practitionerAddress,
    reminderDaysBefore = reminderDaysBefore,
    bodyGender = bodyGender,
)
```

---

## Task 5 : `BodyMapPicker` — paramètre `gender`

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/ui/screens/BodyMapPicker.kt`

- [ ] **Étape 1 : Ajouter la fonction utilitaire `bodyDrawableRes`**

En haut du fichier `BodyMapPicker.kt`, après les imports, avant `BodyMapPicker`, insérer :

```kotlin
import com.grainbeaute.androidweb.R

/**
 * Retourne le drawable VectorDrawable correspondant à la combinaison genre × face.
 * Valeurs valides : gender = "female"|"male", face = "front"|"back".
 */
fun bodyDrawableRes(gender: String, face: String): Int = when {
    gender == "male" && face == "back"  -> R.drawable.ic_body_man_back
    gender == "male"                    -> R.drawable.ic_body_man_front
    face   == "back"                    -> R.drawable.ic_body_woman_back
    else                                -> R.drawable.ic_body_woman_front
}
```

> Note : l'import `com.grainbeaute.androidweb.R` est probablement déjà présent — vérifier avant d'ajouter.

- [ ] **Étape 2 : Ajouter `gender` à la signature de `BodyMapPicker`**

```kotlin
// AVANT
@Composable
fun BodyMapPicker(
    initialPosition: BodyPosition? = null,
    confirmLabel: String = "Créer",
    onConfirm: (BodyPosition?) -> Unit,
    onDismiss: () -> Unit,
)

// APRÈS
@Composable
fun BodyMapPicker(
    initialPosition: BodyPosition? = null,
    confirmLabel: String = "Créer",
    gender: String = "female",
    onConfirm: (BodyPosition?) -> Unit,
    onDismiss: () -> Unit,
)
```

- [ ] **Étape 3 : Remplacer la sélection du drawable dans `BodyMapPicker`**

Dans le corps de `BodyMapPicker`, remplacer :

```kotlin
// AVANT
Image(
    painter = painterResource(
        if (face == "front") R.drawable.ic_body_front else R.drawable.ic_body_back,
    ),
    contentDescription = if (face == "front") "Corps face avant" else "Corps face arrière",
    modifier = Modifier.fillMaxSize(),
    contentScale = ContentScale.FillBounds,
)

// APRÈS
Image(
    painter = painterResource(bodyDrawableRes(gender, face)),
    contentDescription = if (face == "front") "Corps face avant" else "Corps face arrière",
    modifier = Modifier.fillMaxSize(),
    contentScale = ContentScale.FillBounds,
)
```

- [ ] **Étape 4 : Ajouter `gender` à la signature de `BodyMapThumbnail`**

```kotlin
// AVANT
@Composable
fun BodyMapThumbnail(
    position: BodyPosition?,
    modifier: Modifier = Modifier,
)

// APRÈS
@Composable
fun BodyMapThumbnail(
    position: BodyPosition?,
    gender: String = "female",
    modifier: Modifier = Modifier,
)
```

- [ ] **Étape 5 : Remplacer la sélection du drawable dans `BodyMapThumbnail`**

```kotlin
// AVANT
Image(
    painter = painterResource(
        if (position?.face == "back") R.drawable.ic_body_back else R.drawable.ic_body_front,
    ),
    contentDescription = "Position du grain",
    modifier     = Modifier.fillMaxSize(),
    contentScale = ContentScale.FillBounds,
    alpha        = if (position == null) 0.35f else 1f,
)

// APRÈS
Image(
    painter = painterResource(bodyDrawableRes(gender, position?.face ?: "front")),
    contentDescription = "Position du grain",
    modifier     = Modifier.fillMaxSize(),
    contentScale = ContentScale.FillBounds,
    alpha        = if (position == null) 0.35f else 1f,
)
```

---

## Task 6 : Écran Paramètres — toggle genre

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/ui/screens/SettingsScreen.kt`

- [ ] **Étape 1 : Ajouter le bloc genre dans `SettingsScreen`**

`SettingsScreen` charge déjà `appSettings` via `LaunchedEffect`. Ajouter la section genre juste après la section "Rappels de rendez-vous" (après la fermeture de `appSettings?.let { ... }` qui gère les rappels), avant le `Divider` final :

```kotlin
// Insérer après le bloc appSettings?.let { settings -> ... } des rappels :

appSettings?.let { settings ->
    Divider(modifier = Modifier.padding(horizontal = 16.dp))

    Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(40.dp),
                color = MaterialTheme.colorScheme.tertiaryContainer,
                shape = CircleShape
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Silhouette", style = MaterialTheme.typography.titleMedium)
                Text("Corps affiché sur la carte corporelle", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Button(
                onClick = {
                    if (settings.bodyGender != "female") {
                        val newSettings = settings.copy(bodyGender = "female")
                        scope.launch { repository.updateAppSettings(newSettings) }
                        appSettings = newSettings
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (settings.bodyGender == "female") Color(0xFF007AFF) else Color(0xFFE0E0E0),
                    contentColor   = if (settings.bodyGender == "female") Color.White else Color.Black,
                ),
                shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Femme") }

            Button(
                onClick = {
                    if (settings.bodyGender != "male") {
                        val newSettings = settings.copy(bodyGender = "male")
                        scope.launch { repository.updateAppSettings(newSettings) }
                        appSettings = newSettings
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (settings.bodyGender == "male") Color(0xFF007AFF) else Color(0xFFE0E0E0),
                    contentColor   = if (settings.bodyGender == "male") Color.White else Color.Black,
                ),
                shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Homme") }
        }
    }
}
```

Vérifier que `ButtonDefaults`, `RoundedCornerShape`, `Icons.Default.Person` sont bien importés. Les imports manquants probables :
```kotlin
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Person
```

---

## Task 7 : Callers — propagation du genre

**Files:**
- Modify: `app/src/main/java/com/grainbeaute/androidweb/ui/screens/MoleDetailScreen.kt`
- Modify: `app/src/main/java/com/grainbeaute/androidweb/ui/screens/MolesScreen.kt`

### MoleDetailScreen

- [ ] **Étape 1 : Ajouter l'état `bodyGender` dans `MoleDetailScreen`**

Après `var showBodyMapEditor by remember { mutableStateOf(false) }` (ligne ~68), insérer :

```kotlin
var bodyGender by remember { mutableStateOf("female") }
```

- [ ] **Étape 2 : Charger `bodyGender` dans `loadData()`**

Dans la fonction `loadData()`, ajouter une ligne après les chargements existants :

```kotlin
fun loadData() {
    scope.launch {
        try {
            mole = repository.getMole(moleId)
            evolution = repository.getEvolution(moleId)
            moleDiagnoses = repository.getDiagnosesForMole(moleId)
            bodyGender = repository.getAppSettings().bodyGender   // ← ajouter
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Erreur de chargement : ${e.localizedMessage ?: "erreur inconnue"}")
        } finally {
            isLoading = false
        }
    }
}
```

- [ ] **Étape 3 : Passer `gender` à `BodyMapPicker` dans `MoleDetailScreen`**

```kotlin
// AVANT (ligne ~226)
BodyMapPicker(
    initialPosition = currentPosition,
    confirmLabel    = "Enregistrer",
    onConfirm = { position ->
        ...
    },
    onDismiss = { showBodyMapEditor = false },
)

// APRÈS
BodyMapPicker(
    initialPosition = currentPosition,
    confirmLabel    = "Enregistrer",
    gender          = bodyGender,
    onConfirm = { position ->
        ...
    },
    onDismiss = { showBodyMapEditor = false },
)
```

- [ ] **Étape 4 : Passer `gender` à `BodyMapThumbnail` dans `MoleDetailScreen`**

```kotlin
// AVANT (ligne ~684)
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

// APRÈS
BodyMapThumbnail(
    position = if (hasPosition) BodyPosition(
        x        = mole.bodyPositionX!!,
        y        = mole.bodyPositionY!!,
        face     = mole.bodyFace!!,
        zoneName = mole.bodyPart ?: "",
    ) else null,
    gender   = bodyGender,
    modifier = Modifier
        .width(60.dp)
        .height(120.dp),
)
```

### MolesScreen

- [ ] **Étape 5 : Ajouter `bodyGender` dans `MolesScreen`**

Après `var speedDialOpen by remember { mutableStateOf(false) }` (ligne ~38), insérer :

```kotlin
var bodyGender by remember { mutableStateOf("female") }
```

- [ ] **Étape 6 : Charger `bodyGender` dans `MolesScreen`**

Dans `fun loadMoles()`, après `moles = repository.getMoles()`, ajouter :

```kotlin
fun loadMoles() {
    scope.launch {
        isLoading = true
        moles = repository.getMoles()
        bodyGender = repository.getAppSettings().bodyGender   // ← ajouter
        isLoading = false
    }
}
```

- [ ] **Étape 7 : Passer `gender` à `BodyMapPicker` dans `MolesScreen`**

```kotlin
// AVANT (ligne ~127)
BodyMapPicker(
    ...
    onConfirm = { ... },
    onDismiss = { showBodyMapPicker = false },
)

// APRÈS
BodyMapPicker(
    ...
    gender    = bodyGender,
    onConfirm = { ... },
    onDismiss = { showBodyMapPicker = false },
)
```

---

## Task 8 : Build et vérification finale

- [ ] **Étape 1 : Ouvrir Android Studio et synchroniser Gradle**

File > Sync Project with Gradle Files

- [ ] **Étape 2 : Builder le projet**

Build > Make Project — vérifier zéro erreur de compilation.

- [ ] **Étape 3 : Lancer sur émulateur ou appareil**

Tester le chemin complet :
1. **Paramètres** → vérifier que le toggle Femme/Homme est visible et fonctionne (persisté entre sessions)
2. **Grain de beauté > ajouter** → `BodyMapPicker` affiche la silhouette du genre sélectionné
3. **Toggle Face/Dos** dans le picker → silhouette change correctement (4 combinaisons)
4. **Vignette** dans `MoleDetailScreen` → `BodyMapThumbnail` affiche la bonne silhouette
5. **Changer le genre dans les Paramètres** → relancer l'app, vérifier que le choix est persisté et que les silhouettes sont mises à jour

- [ ] **Étape 4 : Vérifier la migration DB**

Installer l'app par-dessus une version existante (pas de désinstallation). L'app doit démarrer sans crash — Room applique `MIGRATION_6_7` automatiquement. Les positions existantes s'affichent sur la silhouette femme (défaut).
