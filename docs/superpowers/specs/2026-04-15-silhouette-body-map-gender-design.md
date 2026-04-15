# Silhouettes genrées pour le BodyMapPicker

**Date :** 2026-04-15  
**Statut :** Approuvé

---

## Objectif

Remplacer les deux silhouettes génériques actuelles (`ic_body_front`, `ic_body_back`) par quatre silhouettes séparées — femme face, femme dos, homme face, homme dos — extraites du fichier source `docs/SilhouetteHommeFemmeFaceDos.svg`. L'utilisateur choisit son genre dans les Réglages ; le `BodyMapPicker` et le `BodyMapThumbnail` affichent la silhouette correspondante.

---

## Section 1 — Découpe du SVG

### Source

`docs/SilhouetteHommeFemmeFaceDos.svg` (Inkscape, 446×917 px, licence libre)  
Contient un seul layer (`layer1`, `transform="translate(-64.7, -62.6)"`) avec quatre groupes :

| Groupe | Contenu estimé | Position dans le canvas |
|--------|---------------|------------------------|
| `g5268` | femme face | moitié gauche, moitié haute |
| `g5423` | homme face | moitié droite, moitié haute |
| `g5553` | femme dos | moitié gauche, moitié basse |
| `g4158` | homme dos | moitié droite, moitié basse |

> La correspondance gauche=femme / droite=homme est à confirmer visuellement après la découpe.

### Script Python

Un script `docs/split_silhouettes.py` :
1. Parse le SVG source avec `xml.etree.ElementTree`
2. Pour chaque groupe, calcule le `viewBox` exact (min/max des coordonnées de tous les paths, en tenant compte du transform du layer)
3. Produit 4 fichiers SVG autonomes dans `docs/silhouettes/` :
   - `woman_front.svg`
   - `woman_back.svg`
   - `man_front.svg`
   - `man_back.svg`

### Import Android Studio (étape manuelle)

Pour chaque SVG : **File > New > Vector Asset > Local file** → nom de ressource :
- `ic_body_woman_front`
- `ic_body_woman_back`
- `ic_body_man_front`
- `ic_body_man_back`

Destination : `app/src/main/res/drawable/`.

---

## Section 2 — Modèle de données

### `AppSettingsEntity` (Room)

Ajout d'un champ :
```kotlin
val bodyGender: String = "female"   // "female" | "male"
```

**Migration Room** : version DB 5 → 6, `Migrations.kt` :
```sql
ALTER TABLE app_settings ADD COLUMN bodyGender TEXT NOT NULL DEFAULT 'female'
```

### `BodyPosition`

Aucun changement. Le genre est une préférence globale, pas liée à chaque position individuelle. Les positions existantes s'affichent sur la silhouette femme (défaut).

### `BodyZones.kt`

Aucun changement. Les zones anatomiques (rectangles normalisés 0–1) sont identiques pour les deux genres à ce niveau de granularité. Les deux listes existantes (`frontZones`, `backZones`) s'appliquent aux deux silhouettes.

---

## Section 3 — UI

### Sélection du drawable

Logique partagée (peut être une fonction utilitaire ou inline dans les composables) :

```kotlin
fun bodyDrawableRes(gender: String, face: String): Int = when {
    gender == "female" && face == "front" -> R.drawable.ic_body_woman_front
    gender == "female" && face == "back"  -> R.drawable.ic_body_woman_back
    gender == "male"   && face == "front" -> R.drawable.ic_body_man_front
    else                                  -> R.drawable.ic_body_man_back
}
```

### `BodyMapPicker`

- Nouveau paramètre : `gender: String = "female"`
- Remplace la sélection actuelle `if (face == "front") R.drawable.ic_body_front else R.drawable.ic_body_back` par `bodyDrawableRes(gender, face)`
- Les callers passent le genre lu depuis le ViewModel des settings

### `BodyMapThumbnail`

- Nouveau paramètre : `gender: String = "female"`
- Même logique de sélection du drawable

### Settings (genre)

Dans l'écran de réglages existant, ajout d'un toggle **Femme / Homme** (même style que le toggle Face/Dos du BodyMapPicker) :
- Lecture/écriture via `AppSettingsViewModel` (ou le ViewModel de settings existant)
- Valeur par défaut : `"female"`

### Propagation du genre aux callers

Les écrans qui instancient `BodyMapPicker` ou `BodyMapThumbnail` (`MoleDetailScreen`, etc.) récupèrent `bodyGender` depuis le ViewModel des settings et le passent en paramètre. Pas de nouveau ViewModel nécessaire.

---

## Hors scope

- Zones anatomiques différenciées homme/femme (pas de valeur ajoutée à ce stade)
- Migration automatique des positions existantes (on assume `"female"` par défaut)
- Zones SVG polygonales (rectangles suffisants)
