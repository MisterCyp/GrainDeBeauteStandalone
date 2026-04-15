# Body Map Picker — Design Spec
**Date:** 2026-04-15  
**Feature:** Sélection interactive de la position d'un grain de beauté sur un dessin du corps humain

---

## Résumé

Remplace le champ texte libre "Partie du corps" par un body map interactif. L'utilisateur place un marqueur sur une silhouette SVG du corps humain (face avant / face arrière) avec son doigt. La zone anatomique est déduite automatiquement. La création d'un grain est simplifiée à ce seul geste.

---

## Données

### Migration DB : version 5 → 6

`MoleEntity` reçoit 3 nouvelles colonnes optionnelles :

```sql
bodyPositionX   REAL    -- coordonnée X normalisée 0.0..1.0
bodyPositionY   REAL    -- coordonnée Y normalisée 0.0..1.0
bodyFace        TEXT    -- "front" | "back"
```

`bodyPart` (TEXT existant) reste — contient le nom de zone auto-généré (ex. `"avant-bras gauche"`). Il n'est plus saisi manuellement.

### Modèle de position

```kotlin
data class BodyPosition(
    val x: Float,        // 0.0..1.0
    val y: Float,        // 0.0..1.0
    val face: String,    // "front" | "back"
    val zoneName: String // déduit par BodyZones.findZone()
)
```

`LocalMole` est mis à jour avec `bodyPositionX: Float?`, `bodyPositionY: Float?`, `bodyFace: String?`.

---

## Mapping des zones anatomiques — `BodyZones`

Objet Kotlin singleton définissant ~20 zones par face comme des polygones normalisés (coordonnées 0..1 relatives à la hauteur/largeur du SVG).

### Zones face avant (~16)
tête, cou, épaule gauche, épaule droite, torse avant, abdomen, avant-bras gauche, avant-bras droit, main gauche, main droite, cuisse gauche, cuisse droite, mollet gauche, mollet droit, pied gauche, pied droit

### Zones face arrière (~11)
nuque, dos haut, dos bas, fesses, épaule gauche (dos), épaule droite (dos), arrière cuisse gauche, arrière cuisse droite, mollet gauche (dos), mollet droit (dos), talon gauche, talon droit

### Algorithme

```kotlin
fun findZone(x: Float, y: Float, face: String): String
```

Itère sur les polygones de la face correspondante, teste `Path.contains(x, y)` pour chaque zone. Retourne la première zone qui contient le point. Retourne `"position personnalisée"` si aucune zone ne correspond (tap en dehors de la silhouette).

Les polygones sont définis statiquement — aucun parsing SVG au runtime.

---

## Composants Compose

### `BodyMapPicker`

```kotlin
@Composable
fun BodyMapPicker(
    initialPosition: BodyPosition? = null,
    onPositionSelected: (BodyPosition?) -> Unit,
    onConfirm: () -> Unit
)
```

**Comportement :**
- Affiche le VectorDrawable de la silhouette du corps (front ou back selon l'état)
- Bouton toggle "Face / Dos" — changer de face efface le marqueur courant
- **Pinch-to-zoom** : `detectTransformGestures` gère scale (min 1x, max 4x) + translation clampée aux bords
- **Tap** : place un marqueur circulaire coloré, convertit en coordonnées normalisées, appelle `BodyZones.findZone()`, émet `BodyPosition` via `onPositionSelected`
- Un seul marqueur à la fois — retapper déplace le marqueur
- Nom de zone affiché en texte sous la silhouette (ex. `"avant-bras gauche"`)
- Bouton "Créer" / "Enregistrer" en bas — toujours actif (position optionnelle)

### `BodyMapThumbnail`

```kotlin
@Composable
fun BodyMapThumbnail(
    position: BodyPosition?,
    modifier: Modifier = Modifier
)
```

Version miniature non interactive. Affiche la silhouette à la bonne face avec le marqueur positionné. Si `position` est null, affiche un placeholder grisé (silhouette front sans marqueur). Utilisé dans `MoleDetailScreen`. Taille recommandée : 100×160dp.

---

## Intégration dans l'app

### Création d'un grain

- L'action "Nouveau grain" (SpeedDial ou bouton vide) ouvre `BodyMapPicker` en plein écran (remplace `AddMoleDialog`)
- Nom auto-généré en Kotlin : `"Grain #${moles.size + 1}"` — non affiché à l'utilisateur. Des gaps de numérotation après suppression sont acceptables (ex. "Grain #3" si seulement 2 grains restants).
- Bouton "Créer" toujours actif — position optionnelle
- À la confirmation : `repository.createMole(name, bodyPosition)` est appelé

### Détail d'un grain (`MoleDetailScreen`)

- Si position enregistrée : `BodyMapThumbnail` miniature + bouton "Modifier la position"
- Si pas de position : bouton "Ajouter une position"
- "Modifier" / "Ajouter" ouvre `BodyMapPicker` en plein écran

### Repository

```kotlin
// Création — nom auto-généré, position optionnelle
suspend fun createMole(name: String, bodyPosition: BodyPosition?): LocalMole

// Mise à jour de position uniquement
suspend fun updateMolePosition(moleId: Int, position: BodyPosition?)
```

`createMole` existant garde sa signature actuelle pour compatibilité interne ; une surcharge ou refactoring propre est fait au moment de l'implémentation.

---

## Assets

- **SVG du corps :** un SVG libre de droits d'une silhouette humaine neutre (face avant + face arrière). Converti en deux `VectorDrawable` Android via Android Studio (`ic_body_front.xml`, `ic_body_back.xml`).
- Les zones anatomiques ne sont **pas** encodées dans le VectorDrawable — elles sont définies séparément dans `BodyZones.kt`.

---

## Ce qui est hors scope

- Renommage manuel des grains (le nom reste auto-généré)
- Zoom > 4x
- Historique de déplacement de marqueur
- Support multi-marqueurs (un seul grain par position)
