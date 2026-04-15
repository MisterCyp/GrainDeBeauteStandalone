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
