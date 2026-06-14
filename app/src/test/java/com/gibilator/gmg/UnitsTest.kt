package com.gibilator.gmg

import com.gibilator.gmg.units.Units
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Parity port of `tests/test_units.py`. */
class UnitsTest {

    private fun approx(a: Double, b: Double, eps: Double = 1e-6) =
        assertTrue(abs(a - b) <= eps + 1e-4 * abs(b), "$a !~ $b")

    @Test
    fun temperatureRoundTrip() {
        for (f in doubleArrayOf(32.0, 212.0, 225.0, 375.0)) {
            approx(Units.cToF(Units.fToC(f)), f)
        }
    }

    @Test
    fun knownTemperaturePoints() {
        approx(Units.fToC(32.0), 0.0)
        approx(Units.fToC(212.0), 100.0)
        approx(Units.cToF(100.0), 212.0)
    }

    @Test
    fun weightRoundTrip() {
        for (kg in doubleArrayOf(0.2, 1.0, 5.0, 12.0)) {
            approx(Units.lbToKg(Units.kgToLb(kg)), kg)
        }
    }

    @Test
    fun knownWeightPoint() {
        approx(Units.kgToLb(1.0), 2.20462, 1e-3)
    }

    @Test
    fun resolveTempUnitExplicitWins() {
        assertEquals(Units.TEMP_C, Units.resolveTempUnit(Units.TEMP_UNIT_CELSIUS, metric = false))
        assertEquals(Units.TEMP_F, Units.resolveTempUnit(Units.TEMP_UNIT_FAHRENHEIT, metric = true))
    }

    @Test
    fun resolveTempUnitAutoFollowsSystem() {
        assertEquals(Units.TEMP_C, Units.resolveTempUnit(Units.TEMP_UNIT_AUTO, metric = true))
        assertEquals(Units.TEMP_F, Units.resolveTempUnit(Units.TEMP_UNIT_AUTO, metric = false))
    }

    @Test
    fun resolveWeightUnit() {
        assertEquals(Units.WEIGHT_KG, Units.resolveWeightUnit(Units.WEIGHT_UNIT_KILOGRAMS, metric = false))
        assertEquals(Units.WEIGHT_LB, Units.resolveWeightUnit(Units.WEIGHT_UNIT_POUNDS, metric = true))
        assertEquals(Units.WEIGHT_KG, Units.resolveWeightUnit(Units.WEIGHT_UNIT_AUTO, metric = true))
        assertEquals(Units.WEIGHT_LB, Units.resolveWeightUnit(Units.WEIGHT_UNIT_AUTO, metric = false))
    }

    @Test
    fun fmtTemp() {
        assertEquals("212°F", Units.fmtTemp(212.0, Units.TEMP_F))
        assertEquals("100°C", Units.fmtTemp(212.0, Units.TEMP_C))
        assertEquals("—", Units.fmtTemp(null, Units.TEMP_C))
    }

    @Test
    fun fmtWeight() {
        assertEquals("1.0 kg", Units.fmtWeight(1.0, Units.WEIGHT_KG))
        assertEquals("2.2 lb", Units.fmtWeight(1.0, Units.WEIGHT_LB))
        assertEquals("—", Units.fmtWeight(null, Units.WEIGHT_LB))
    }
}
