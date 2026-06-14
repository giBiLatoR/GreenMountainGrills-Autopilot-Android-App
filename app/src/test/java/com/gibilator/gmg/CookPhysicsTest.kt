package com.gibilator.gmg

import com.gibilator.gmg.cook.CookPhysics
import com.gibilator.gmg.cook.CookPhysics.CP_MEATS
import com.gibilator.gmg.cook.CookPhysics.computeAt
import com.gibilator.gmg.cook.CookPhysics.elapsedAtProbe
import com.gibilator.gmg.cook.CookPhysics.expectedProbeAt
import com.gibilator.gmg.cook.CookPhysics.findExactTemp
import com.gibilator.gmg.cook.CookPhysics.phaseAt
import com.gibilator.gmg.cook.CookPhysics.phaseHours
import com.gibilator.gmg.cook.CookPhysics.wetBulbF
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Parity port of `tests/test_cook_physics.py`. */
class CookPhysicsTest {

    @Test
    fun meatsTableSize() {
        assertEquals(21, CP_MEATS.size)
        for ((key, meat) in CP_MEATS) {
            assertEquals(key, meat.key)
            assertTrue(meat.label.isNotEmpty())
            assertTrue(meat.pullF > 0)
            assertTrue(meat.maxHours > 0)
        }
    }

    @Test
    fun wetBulbBelowDryBulb() {
        for (tdb in intArrayOf(150, 200, 225, 250, 300)) {
            val twb = wetBulbF(tdb.toDouble(), 12.0)
            assertTrue(twb < tdb, "wet-bulb $twb !< $tdb")
            assertTrue(twb > 0)
        }
    }

    @Test
    fun phaseReturnsInfWhenUnreachable() {
        assertTrue(phaseHours(1.85, 1.5, 100.0, 38.0, 200.0).isInfinite())
    }

    @Test
    fun computeAtBrisketLowAndSlow() {
        val res = computeAt("beef_brisket_packer", 10.0, 225.0)
        assertNotNull(res)
        assertTrue(res.totalHours in 10.0..30.0, "brisket hours=${res.totalHours}")
        val names = res.phases.map { it.name }
        assertTrue("Smoke & Bark" in names)
        assertTrue("Foil Wrap — Render" in names)
    }

    @Test
    fun computeAtChickenNoStall() {
        val res = computeAt("whole_chicken", 2.0, 350.0)
        assertNotNull(res)
        assertEquals(1, res.phases.size)
        assertEquals("Smoke", res.phases[0].name)
        assertTrue(res.totalHours < 3.0)
    }

    @Test
    fun computeAtUnwrappedStallHasThreePhases() {
        val res = computeAt("beef_ribs_dino", 4.0, 250.0)
        assertNotNull(res)
        val names = res.phases.map { it.name }
        assertTrue("Smoke & Bark" in names)
        assertTrue("Stall Plateau" in names)
        assertTrue("Collagen Render" in names)
    }

    @Test
    fun computeAtReturnsNullWhenPitTooCool() {
        assertNull(computeAt("beef_brisket_packer", 10.0, 100.0))
    }

    @Test
    fun findExactTempInverselyMonotonic() {
        val fast = findExactTemp("pork_butt_pulled", 8.0, 8.0)
        val slow = findExactTemp("pork_butt_pulled", 8.0, 16.0)
        assertTrue(fast > slow, "fast=$fast slow=$slow")
        assertTrue(slow in 150.0..450.0)
        assertTrue(fast in 150.0..450.0)
    }

    @Test
    fun expectedProbeMonotonicInElapsed() {
        val res = computeAt("pork_butt_pulled", 8.0, 250.0)
        assertNotNull(res)
        val samples = doubleArrayOf(0.5, 2.0, 5.0, 9.0, 15.0).map { expectedProbeAt(res, it) }
        for (i in 1 until samples.size) {
            assertTrue(samples[i] >= samples[i - 1], "not monotonic: $samples")
        }
    }

    @Test
    fun phaseAtClassifiesProbeTemps() {
        val res = computeAt("beef_brisket_packer", 10.0, 225.0)
        assertNotNull(res)
        val pull = CP_MEATS.getValue("beef_brisket_packer").pullF
        assertEquals("pre_stall", phaseAt(res, 100.0, pull))
        assertEquals("stall", phaseAt(res, 162.0, pull))
        assertEquals("post_stall", phaseAt(res, 180.0, pull))
        assertEquals("approaching", phaseAt(res, (pull - 5).toDouble(), pull))
        assertEquals("pull_reached", phaseAt(res, pull.toDouble(), pull))
    }

    @Test
    fun elapsedAtProbeInvertsExpected() {
        val res = computeAt("whole_chicken", 2.0, 350.0) // single phase, strictly increasing
        assertNotNull(res)
        // Start temp maps to t=0; pull temp maps to the full projected time.
        assertEquals(0.0, elapsedAtProbe(res, res.phases.first().startInternalF), 1e-9)
        assertTrue(abs(elapsedAtProbe(res, res.phases.last().endInternalF) - res.totalHours) < 1e-6)
        // Round-trips with expectedProbeAt mid-cook.
        val h = res.totalHours * 0.5
        assertTrue(abs(elapsedAtProbe(res, expectedProbeAt(res, h)) - h) < 1e-3, "round-trip off")
    }

    @Test
    fun chickenPhaseAtIsSinglePhase() {
        val res = computeAt("whole_chicken", 2.0, 325.0)
        assertNotNull(res)
        val pull = CP_MEATS.getValue("whole_chicken").pullF
        assertEquals("single_phase", phaseAt(res, 120.0, pull))
    }

    @Test
    fun allMeatsComputeAtDefaultPit() {
        for ((key, meat) in CP_MEATS) {
            val weight = if (meat.stall) 8.0 else 4.0
            val res = computeAt(key, weight, 275.0)
            assertNotNull(res, "null projection for $key")
            assertTrue(res.totalHours.isFinite(), "non-finite for $key")
            assertTrue(res.totalHours > 0, "non-positive for $key")
        }
    }
}
