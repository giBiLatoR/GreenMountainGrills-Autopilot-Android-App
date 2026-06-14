package com.gibilator.gmg.cook

import kotlin.math.atan
import kotlin.math.ln
import kotlin.math.pow

/**
 * Heat-diffusion physics model for the auto-cook feature.
 *
 * Pure Kotlin port of `cook_physics.py` (itself a port of the JS cook planner).
 * No Android imports — JVM unit-testable in isolation.
 */
object CookPhysics {
    // Physics constants -----------------------------------------------------
    const val CP_STALL_START_F = 150.0 // Lower bound of projection-curve stall region.
    const val CP_STALL_END_F = 165.0 // Upper bound of projection-curve stall region.
    const val CP_RH_PELLET = 12.0 // Approx RH (%) inside a pellet-smoker firebox.
    const val CP_TI_F = 38.0 // Initial meat temp assumed at cook start.
    const val LBS_PER_KG = 1.0 / 0.453592

    // Runtime stall-detection range (distinct from projection range).
    const val STALL_DETECT_LOW_F = 158.0
    const val STALL_DETECT_HIGH_F = 170.0

    /** One reference meat entry. `lfn` is the half-thickness L(w_lbs) in inches. */
    data class Meat(
        val key: String,
        val label: String,
        val km: Double,
        val lfn: (Double) -> Double,
        val pullF: Int,
        val stall: Boolean,
        val restMin: Int,
        val foil: Boolean,
        val maxHours: Double,
        // Thin "by-the-piece" items: their size doesn't change the time (constant
        // half-thickness), so don't ask for weight.
        val byThePiece: Boolean = false,
        // Cook at a fixed pit temp instead of solving one from a finish time —
        // sensible for quick items where a hot finish-time solution is wrong.
        val fixedPitF: Int? = null,
    )

    private fun lLinear(intercept: Double, slope: Double = 0.0): (Double) -> Double =
        { w -> intercept + slope * w }

    /** 21 canonical meats, identical to the Python `CP_MEATS` table. */
    val CP_MEATS: Map<String, Meat> = linkedMapOf(
        "beef_brisket_packer" to Meat("beef_brisket_packer", "Beef Brisket — Whole Packer", 1.85, lLinear(1.2, 0.05), 203, true, 90, true, 20.0),
        "beef_brisket_flat" to Meat("beef_brisket_flat", "Beef Brisket — Flat Only", 1.90, lLinear(1.2, 0.05), 198, true, 30, true, 20.0),
        "pork_butt_pulled" to Meat("pork_butt_pulled", "Pork Butt — Pulled Pork", 1.80, lLinear(1.9), 203, true, 60, true, 16.0),
        "pork_butt_sliced" to Meat("pork_butt_sliced", "Pork Butt — Sliced", 1.80, lLinear(1.9), 190, true, 45, true, 16.0),
        "beef_chuck_roast" to Meat("beef_chuck_roast", "Beef Chuck Roast", 1.85, lLinear(1.2, 0.05), 195, true, 45, true, 8.0),
        "lamb_shoulder" to Meat("lamb_shoulder", "Lamb Shoulder", 1.85, lLinear(1.0, 0.07), 190, true, 45, true, 10.0),
        "beef_ribs_dino" to Meat("beef_ribs_dino", "Beef Ribs — Short Plate", 1.75, lLinear(0.75), 203, true, 45, false, 10.0),
        "whole_turkey" to Meat("whole_turkey", "Whole Turkey", 1.55, lLinear(1.5, 0.05), 165, false, 35, false, 8.0),
        "turkey_breast" to Meat("turkey_breast", "Turkey Breast", 1.60, lLinear(1.0, 0.08), 160, false, 20, false, 8.0),
        "whole_chicken" to Meat("whole_chicken", "Whole Chicken", 1.55, lLinear(1.0, 0.10), 165, false, 15, false, 4.0),
        "chicken_thighs_legs" to Meat("chicken_thighs_legs", "Chicken Thighs / Legs", 1.55, lLinear(1.2), 175, false, 10, false, 4.0),
        "chicken_breast" to Meat("chicken_breast", "Chicken Breasts", 1.55, lLinear(1.2), 162, false, 5, false, 4.0, byThePiece = true, fixedPitF = 275),
        "pork_loin" to Meat("pork_loin", "Pork Loin Roast", 1.60, lLinear(1.0, 0.08), 145, false, 15, false, 4.0),
        "lamb_leg" to Meat("lamb_leg", "Lamb Leg (Bone-In)", 1.60, lLinear(1.0, 0.08), 135, false, 15, false, 10.0),
        "beef_tri_tip" to Meat("beef_tri_tip", "Beef Tri-Tip Roast", 1.60, lLinear(1.2), 135, false, 10, false, 8.0),
        "beef_prime_rib" to Meat("beef_prime_rib", "Beef Prime Rib Roast", 1.60, lLinear(1.2, 0.05), 130, false, 25, false, 8.0),
        "baby_back_ribs" to Meat("baby_back_ribs", "Baby Back Ribs", 1.70, lLinear(0.6), 190, false, 15, false, 10.0),
        "spare_ribs_stlouis" to Meat("spare_ribs_stlouis", "Spare Ribs — St. Louis Style", 1.72, lLinear(0.65), 195, false, 15, false, 10.0),
        "pork_chops" to Meat("pork_chops", "Pork Chops", 1.60, lLinear(0.8), 145, false, 5, false, 4.0),
        "salmon_fillet" to Meat("salmon_fillet", "Salmon Fillet", 1.50, lLinear(0.3, 0.06), 145, false, 5, true, 2.0),
        "sausage_brats" to Meat("sausage_brats", "Sausage / Bratwurst", 1.55, lLinear(0.7), 160, false, 5, false, 3.0, byThePiece = true, fixedPitF = 250),
    )

    /** One segment of the projection curve. */
    data class Phase(
        val name: String,
        val startInternalF: Double,
        val endInternalF: Double,
        val hours: Double,
    )

    /** Result of [computeAt] — total hours plus per-phase breakdown. */
    data class CookProjection(
        val totalHours: Double,
        val halfThicknessIn: Double,
        val wetBulbF: Double,
        val phases: List<Phase>,
    )

    /** Rogers & Howarth wet-bulb approximation in Fahrenheit. */
    fun wetBulbF(tdbF: Double, rhPct: Double): Double {
        val tC = (tdbF - 32) * 5 / 9
        val twC = (
            tC * atan(0.151977 * (rhPct + 8.313659).pow(0.5)) +
                atan(tC + rhPct) -
                atan(rhPct - 1.676331) +
                0.00391838 * rhPct.pow(1.5) * atan(0.023101 * rhPct) -
                4.686035
            )
        return twC * 9 / 5 + 32
    }

    /** Single diffusion phase duration in hours. Returns +inf for unreachable cases. */
    fun phaseHours(km: Double, lIn: Double, tDriveF: Double, tInitF: Double, tFinalF: Double): Double {
        if (tDriveF <= tFinalF || !tDriveF.isFinite()) return Double.POSITIVE_INFINITY
        val ratio = (tDriveF - tInitF) / (tDriveF - tFinalF)
        if (ratio <= 0 || !ratio.isFinite()) return Double.POSITIVE_INFINITY
        return km * lIn * lIn * ln(ratio)
    }

    /**
     * Compute the projected cook for a meat at a given pit temp.
     * Returns null when the pit is too cool (wet-bulb >= pit) or numeric failure.
     */
    fun computeAt(meatKey: String, weightLbs: Double, pitF: Double): CookProjection? {
        val meat = CP_MEATS[meatKey] ?: return null
        if (!pitF.isFinite()) return null
        val km = meat.km
        val lIn = meat.lfn(weightLbs)
        val twb = wetBulbF(pitF, CP_RH_PELLET)
        val tFinal = meat.pullF.toDouble()
        val tInit = CP_TI_F
        if (twb >= pitF) return null

        val phases = mutableListOf<Phase>()

        if (!meat.stall) {
            val t = phaseHours(km, lIn, pitF, tInit, tFinal)
            if (t <= 0 || !t.isFinite()) return null
            phases.add(Phase("Smoke", tInit, tFinal, t))
            return CookProjection(t, lIn, twb, phases)
        }

        if (meat.foil) {
            val t1 = phaseHours(km, lIn, pitF, tInit, CP_STALL_START_F)
            val t3 = phaseHours(km, lIn, pitF, CP_STALL_START_F, tFinal)
            if (!t1.isFinite() || !t3.isFinite() || (t1 + t3) <= 0) return null
            phases.add(Phase("Smoke & Bark", tInit, CP_STALL_START_F, t1))
            phases.add(Phase("Foil Wrap — Render", CP_STALL_START_F, tFinal, t3))
            return CookProjection(t1 + t3, lIn, twb, phases)
        }

        // Unwrapped stall — evaporative cooling reduces effective drive temp 40%.
        val tEff = pitF - (pitF - twb) * 0.40
        val t1 = phaseHours(km, lIn, pitF, tInit, CP_STALL_START_F)
        phases.add(Phase("Smoke & Bark", tInit, CP_STALL_START_F, t1))
        var t2 = 0.0
        if (tEff > CP_STALL_END_F) {
            val t2Calc = phaseHours(km, lIn, tEff, CP_STALL_START_F, CP_STALL_END_F)
            if (t2Calc.isFinite() && t2Calc >= 0) t2 = t2Calc
        }
        phases.add(Phase("Stall Plateau", CP_STALL_START_F, CP_STALL_END_F, t2))
        val t3 = phaseHours(km, lIn, pitF, CP_STALL_END_F, tFinal)
        if (!t3.isFinite()) return null
        phases.add(Phase("Collagen Render", CP_STALL_END_F, tFinal, t3))
        val total = t1 + t2 + t3
        if (total <= 0) return null
        return CookProjection(total, lIn, twb, phases)
    }

    /** Binary search the pit temp that yields ~[targetHours] total cook time. */
    fun findExactTemp(meatKey: String, weightLbs: Double, targetHours: Double): Double {
        var lo = 150.0
        var hi = 450.0
        repeat(80) {
            val mid = (lo + hi) / 2
            val r = computeAt(meatKey, weightLbs, mid)
            if (r == null) {
                lo = mid + 5
                return@repeat
            }
            when {
                r.totalHours > targetHours -> lo = mid
                r.totalHours < targetHours -> hi = mid
                else -> return (lo + hi) / 2
            }
        }
        return (lo + hi) / 2
    }

    /**
     * Interpolate the expected probe temp from the projection at a given elapsed time.
     * Linear within each phase; returns the final pull temp past the projected end.
     */
    fun expectedProbeAt(projection: CookProjection, elapsedHours: Double): Double {
        if (elapsedHours <= 0) return projection.phases.first().startInternalF
        var cum = 0.0
        for (ph in projection.phases) {
            if (elapsedHours <= cum + ph.hours) {
                if (ph.hours <= 0) return ph.endInternalF
                val frac = (elapsedHours - cum) / ph.hours
                return ph.startInternalF + frac * (ph.endInternalF - ph.startInternalF)
            }
            cum += ph.hours
        }
        return projection.phases.last().endInternalF
    }

    /**
     * Classify the current cook phase from probe temp. Returns one of:
     * `pre_stall`, `stall`, `post_stall`, `single_phase`, `approaching`, `pull_reached`.
     */
    fun phaseAt(projection: CookProjection, probeF: Double, pullF: Int): String {
        if (probeF >= pullF) return "pull_reached"
        if (probeF >= pullF - 10) return "approaching"
        val hasStall = projection.phases.any { it.name.contains("Stall") || it.name.contains("Render") }
        if (!hasStall) return "single_phase"
        if (probeF < STALL_DETECT_LOW_F) return "pre_stall"
        if (probeF <= STALL_DETECT_HIGH_F) return "stall"
        return "post_stall"
    }
}
