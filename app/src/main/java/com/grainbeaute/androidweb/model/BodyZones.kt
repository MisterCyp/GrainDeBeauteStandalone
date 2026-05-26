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
        Zone("épaule droite",       0.15f, 0.37f, 0.15f, 0.28f),
        Zone("épaule gauche",       0.63f, 0.85f, 0.15f, 0.28f),
        Zone("bras droit",          0.12f, 0.35f, 0.18f, 0.40f),
        Zone("bras gauche",         0.65f, 0.88f, 0.18f, 0.40f),
        Zone("avant-bras droit",    0.09f, 0.32f, 0.40f, 0.57f),
        Zone("avant-bras gauche",   0.68f, 0.91f, 0.40f, 0.57f),
        Zone("main droite",         0.08f, 0.28f, 0.57f, 0.68f),
        Zone("main gauche",         0.72f, 0.92f, 0.57f, 0.68f),
        Zone("torse avant",         0.34f, 0.66f, 0.20f, 0.48f),
        Zone("abdomen",             0.34f, 0.66f, 0.48f, 0.57f),
        Zone("cuisse droite",       0.34f, 0.52f, 0.57f, 0.77f),
        Zone("cuisse gauche",       0.48f, 0.66f, 0.57f, 0.77f),
        Zone("mollet droit",        0.33f, 0.51f, 0.77f, 0.93f),
        Zone("mollet gauche",       0.49f, 0.67f, 0.77f, 0.93f),
        Zone("pied droit",          0.27f, 0.50f, 0.93f, 1.00f),
        Zone("pied gauche",         0.50f, 0.73f, 0.93f, 1.00f),
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
