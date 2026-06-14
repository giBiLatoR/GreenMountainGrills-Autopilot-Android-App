package com.gibilator.gmg.units

import kotlin.math.roundToInt

/**
 * Unit conversion + display formatting. Pure functions, port of `units.py`.
 *
 * The protocol is natively Fahrenheit and the cook physics works in pounds; these
 * helpers convert canonical values (Fahrenheit, kilograms) to the user's chosen
 * display unit for notifications and the dashboard.
 */
object Units {
    // Options-flow preference tokens.
    const val TEMP_UNIT_AUTO = "auto"
    const val TEMP_UNIT_CELSIUS = "celsius"
    const val TEMP_UNIT_FAHRENHEIT = "fahrenheit"

    const val WEIGHT_UNIT_AUTO = "auto"
    const val WEIGHT_UNIT_KILOGRAMS = "kilograms"
    const val WEIGHT_UNIT_POUNDS = "pounds"

    // Resolved, concrete unit tokens used internally.
    const val TEMP_C = "C"
    const val TEMP_F = "F"
    const val WEIGHT_KG = "kg"
    const val WEIGHT_LB = "lb"

    const val LB_PER_KG = 1.0 / 0.453592

    fun fToC(valueF: Double): Double = (valueF - 32.0) * 5.0 / 9.0

    fun cToF(valueC: Double): Double = valueC * 9.0 / 5.0 + 32.0

    fun kgToLb(valueKg: Double): Double = valueKg * LB_PER_KG

    fun lbToKg(valueLb: Double): Double = valueLb / LB_PER_KG

    /** Resolve a temperature preference to concrete "C"/"F". `auto` follows [metric]. */
    fun resolveTempUnit(pref: String, metric: Boolean): String = when (pref) {
        TEMP_UNIT_CELSIUS -> TEMP_C
        TEMP_UNIT_FAHRENHEIT -> TEMP_F
        else -> if (metric) TEMP_C else TEMP_F
    }

    /** Resolve a weight preference to concrete "kg"/"lb". `auto` follows [metric]. */
    fun resolveWeightUnit(pref: String, metric: Boolean): String = when (pref) {
        WEIGHT_UNIT_KILOGRAMS -> WEIGHT_KG
        WEIGHT_UNIT_POUNDS -> WEIGHT_LB
        else -> if (metric) WEIGHT_KG else WEIGHT_LB
    }

    /** Format a canonical Fahrenheit value as a display string in [unit]. */
    fun fmtTemp(valueF: Double?, unit: String): String {
        if (valueF == null) return "—"
        return if (unit == TEMP_C) "${fToC(valueF).roundToInt()}°C" else "${valueF.roundToInt()}°F"
    }

    /** Format a canonical kilogram value as a display string in [unit]. */
    fun fmtWeight(valueKg: Double?, unit: String): String {
        if (valueKg == null) return "—"
        return if (unit == WEIGHT_LB) {
            "%.1f lb".format(kgToLb(valueKg))
        } else {
            "%.1f kg".format(valueKg)
        }
    }
}
