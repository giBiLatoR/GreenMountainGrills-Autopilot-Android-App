package com.gibilator.gmg.cook

import com.gibilator.gmg.cook.CookPhysics.CP_MEATS
import com.gibilator.gmg.cook.CookPhysics.CookProjection
import com.gibilator.gmg.cook.CookPhysics.computeAt
import com.gibilator.gmg.cook.CookPhysics.expectedProbeAt
import com.gibilator.gmg.cook.CookPhysics.findExactTemp
import com.gibilator.gmg.cook.CookPhysics.phaseAt
import com.gibilator.gmg.protocol.GmgSnapshot
import com.gibilator.gmg.protocol.PowerState
import com.gibilator.gmg.units.Units
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** State-machine states. Port of `CookState`. */
enum class CookState(val value: String) {
    IDLE("idle"),
    PLANNED("planned"),
    PREHEATING("preheating"),
    WAITING_MEAT("waiting_meat"),
    COOKING("cooking"),
    APPROACHING("approaching"),
    PULL_REACHED("pull_reached"),
    COMPLETE("complete"),
    ABORTED("aborted"),
}

/** User-selectable behavior modes. Port of `CookMode`. */
enum class CookMode(val value: String) {
    SET_AND_FORGET("set_and_forget"),
    AUTONOMOUS("autonomous"),
    COACH("coach"),
    ;

    companion object {
        fun from(value: String): CookMode = entries.firstOrNull { it.value == value } ?: AUTONOMOUS
    }
}

internal data class ProbeSample(val ts: Double, val probeF: Double)

/** In-memory state of one cook session. Mutable; mirrors the Python dataclass. */
class CookSession(
    var state: CookState,
    val meatKey: String,
    val weightKg: Double,
    val probeIndex: Int,
    val mode: CookMode,
    var pitTargetF: Int,
    var projection: CookProjection,
    val createdAt: Double,
    var preheatStartedAt: Double? = null,
    var preheatReadySince: Double? = null,
    var cookStartedAt: Double? = null,
    var lastAdjAt: Double = 0.0,
    var lastPitSetF: Int = 0,
    var pullReachedAt: Double? = null,
    var lastPullNotifyAt: Double = 0.0,
) {
    internal val probeHistory: MutableList<ProbeSample> = mutableListOf()
    val notes: MutableList<String> = mutableListOf()
}

/** Outcome of pre-flight validation. */
data class PreFlightResult(
    val ok: Boolean,
    val pitTargetF: Int,
    val projection: CookProjection,
    val warnings: List<String>,
)

class CookManagerError(message: String) : Exception(message)

/** Grill actuation the cook loop is allowed to perform (never power-off). */
interface GrillController {
    suspend fun powerOn()
    suspend fun setGrillTemp(f: Int)
}

/** Milestone delivery (in-app + push). Fire-and-forget. */
interface CookNotifier {
    fun notify(title: String, message: String, critical: Boolean = false)
}

/** Persistence of sessions + dev-mode sample log. */
interface CookStore {
    suspend fun insertSession(session: CookSession, serial: String): Long
    suspend fun completeSession(session: CookSession, finalState: String, serial: String)
    suspend fun logSample(session: CookSession, snapshot: GmgSnapshot, probeF: Double?, serial: String)
}

/**
 * Auto-cook orchestration: state machine, persistence, control loop.
 *
 * Port of `cook_manager.py`. Hard rules (never violated): never auto power-off;
 * pit clamped to [150, 375]; auto power-on only on PLANNED -> PREHEATING.
 */
class CookManager(
    private val controller: GrillController,
    private val notifier: CookNotifier,
    private val store: CookStore,
    private var serial: String = "",
    private val clock: () -> Double = { System.currentTimeMillis() / 1000.0 },
) {
    var session: CookSession? = null
        private set

    private var autoCookEnabled = false
    private var devMode = false
    private var pushEnabled = false
    private var maxPitF = PIT_CLAMP_MAX_F
    private var tempUnit = Units.TEMP_F
    private var weightUnit = Units.WEIGHT_KG

    fun setSerial(value: String) { serial = value }

    fun configure(
        autoCook: Boolean,
        devMode: Boolean,
        push: Boolean,
        maxPitF: Int = PIT_CLAMP_MAX_F,
        tempUnit: String = Units.TEMP_F,
        weightUnit: String = Units.WEIGHT_KG,
    ) {
        this.autoCookEnabled = autoCook
        this.devMode = devMode
        this.pushEnabled = push
        this.maxPitF = max(PIT_CLAMP_MIN_F, min(PIT_CLAMP_MAX_F, maxPitF))
        this.tempUnit = tempUnit
        this.weightUnit = weightUnit
    }

    private fun ftemp(v: Number?): String = Units.fmtTemp(v?.toDouble(), tempUnit)
    private fun fweight(v: Number?): String = Units.fmtWeight(v?.toDouble(), weightUnit)

    // --- planning ----------------------------------------------------------

    fun preFlight(meatKey: String, weightKg: Double, finishInHours: Double): PreFlightResult {
        val meat = CP_MEATS[meatKey] ?: throw CookManagerError("unknown meat key: $meatKey")
        if (weightKg <= 0) throw CookManagerError("weight must be > 0 kg")
        if (finishInHours <= 0.5) throw CookManagerError("finish time too soon (>0.5h required)")
        val weightLbs = weightKg * 2.20462
        val cookHrs = finishInHours - (meat.restMin / 60.0) - 0.5
        if (cookHrs <= 0.25) throw CookManagerError("not enough cook time after rest + preheat budget")
        var pitTarget = findExactTemp(meatKey, weightLbs, cookHrs)
        pitTarget = max(PIT_CLAMP_MIN_F.toDouble(), min(maxPitF.toDouble(), pitTarget.roundToInt().toDouble()))
        val projection = computeAt(meatKey, weightLbs, pitTarget)
            ?: throw CookManagerError("physics model failed to converge")

        val warnings = mutableListOf<String>()
        if (projection.totalHours > meat.maxHours) {
            warnings.add(
                "projected %.1fh exceeds %s max %.1fh".format(projection.totalHours, meat.label, meat.maxHours),
            )
        }
        val slow = computeAt(meatKey, weightLbs, PIT_CLAMP_MIN_F.toDouble())
        if (slow != null && slow.totalHours < projection.totalHours) {
            warnings.add(
                "computed pit target slower than the ${ftemp(PIT_CLAMP_MIN_F)} floor — review inputs",
            )
        }
        return PreFlightResult(true, pitTarget.toInt(), projection, warnings)
    }

    suspend fun startCook(
        meatKey: String,
        weightKg: Double,
        probeIndex: Int,
        mode: CookMode,
        finishInHours: Double,
        snapshot: GmgSnapshot,
    ): CookSession {
        val current = session
        if (current != null && current.state !in setOf(CookState.COMPLETE, CookState.ABORTED, CookState.IDLE)) {
            throw CookManagerError("a cook is already in progress")
        }
        if (probeIndex != 1 && probeIndex != 2) throw CookManagerError("probe_index must be 1 or 2")

        val pf = preFlight(meatKey, weightKg, finishInHours)
        val now = clock()
        val newSession = CookSession(
            state = CookState.PLANNED,
            meatKey = meatKey,
            weightKg = weightKg,
            probeIndex = probeIndex,
            mode = mode,
            pitTargetF = pf.pitTargetF,
            projection = pf.projection,
            createdAt = now,
            lastPitSetF = snapshot.grillSetTemp,
        )
        session = newSession
        store.insertSession(newSession, serial)

        val label = CP_MEATS.getValue(meatKey).label
        val target = pf.pitTargetF
        val projH = pf.projection.totalHours
        if (mode == CookMode.COACH) {
            newSession.state = CookState.PREHEATING
            newSession.preheatStartedAt = now
            notifier.notify(
                "Auto-Cook (coach) started",
                "$label (${fweight(weightKg)}) — power on the grill and set the pit to " +
                    "${ftemp(target)}. Projected %.1fh. I'll track progress and advise, but I won't change the grill.".format(projH),
            )
        } else {
            if (snapshot.powerState == PowerState.OFF) {
                runCatching { controller.powerOn() }
            }
            setPitTarget(target, "preheat")
            newSession.state = CookState.PREHEATING
            newSession.preheatStartedAt = now
            notifier.notify(
                "Auto-Cook started",
                "$label (${fweight(weightKg)}) — preheating to ${ftemp(target)}. Projected %.1fh.".format(projH),
            )
        }
        if (pf.warnings.isNotEmpty()) {
            notifier.notify("Auto-Cook pre-flight warnings", pf.warnings.joinToString("; "))
        }
        return newSession
    }

    suspend fun abortCook() {
        val s = session ?: return
        s.state = CookState.ABORTED
        store.completeSession(s, "aborted", serial)
        notifier.notify("Auto-Cook aborted", "Session cancelled.")
        session = null
    }

    // --- per-poll loop -----------------------------------------------------

    suspend fun update(snapshot: GmgSnapshot) {
        if (!autoCookEnabled) return
        val s = session ?: return
        val now = clock()
        val probeF: Double? =
            (if (s.probeIndex == 1) snapshot.probe1Temp else snapshot.probe2Temp)?.toDouble()
        if (probeF != null) {
            s.probeHistory.add(ProbeSample(now, probeF))
            if (s.probeHistory.size > 240) {
                val tail = s.probeHistory.takeLast(240)
                s.probeHistory.clear()
                s.probeHistory.addAll(tail)
            }
        }

        if (devMode) store.logSample(s, snapshot, probeF, serial)

        // Grill failure trip — pit dropped from hot to below the safe floor.
        if (s.state in setOf(CookState.PREHEATING, CookState.WAITING_MEAT, CookState.COOKING, CookState.APPROACHING) &&
            snapshot.grillTemp < PIT_CLAMP_MIN_F &&
            wasAbove(s, PIT_ERROR_TRIP_F)
        ) {
            notifier.notify(
                "Auto-Cook: grill failure suspected",
                "Pit dropped to ${ftemp(snapshot.grillTemp)}. Check grill.",
                critical = true,
            )
        }

        when (s.state) {
            CookState.PREHEATING -> {
                if (detectCookStart(s)) {
                    s.state = CookState.COOKING
                    s.cookStartedAt = now
                    notifier.notify("Cook started", "Meat detected during preheat — tracking now.")
                    return
                }
                if (abs(snapshot.grillTemp - s.pitTargetF) <= PREHEAT_BAND_F) {
                    if (s.preheatReadySince == null) {
                        s.preheatReadySince = now
                    } else if (now - s.preheatReadySince!! >= PREHEAT_HOLD_S) {
                        s.state = CookState.WAITING_MEAT
                        notifier.notify("Grill ready", "At ${ftemp(snapshot.grillTemp)} — insert probe into meat.")
                    }
                } else {
                    s.preheatReadySince = null
                }
            }

            CookState.WAITING_MEAT -> {
                if (detectCookStart(s)) {
                    s.state = CookState.COOKING
                    s.cookStartedAt = now
                    notifier.notify("Cook started", "Probe drop detected. Tracking projection.")
                }
            }

            CookState.COOKING, CookState.APPROACHING -> {
                val pullF = CP_MEATS.getValue(s.meatKey).pullF
                if (probeF != null) {
                    if (probeF >= pullF) {
                        s.state = CookState.PULL_REACHED
                        s.pullReachedAt = now
                        notifier.notify(
                            "Pull temp reached",
                            "Probe at ${ftemp(probeF)} (target ${ftemp(pullF)}).",
                            critical = true,
                        )
                        return
                    }
                    if (probeF >= pullF - APPROACHING_BAND_F && s.state == CookState.COOKING) {
                        s.state = CookState.APPROACHING
                        notifier.notify(
                            "Approaching pull",
                            "Approaching the ${ftemp(pullF)} pull target. No further pit adjustments.",
                        )
                    }
                }
                if (s.state == CookState.COOKING && probeF != null) {
                    when (s.mode) {
                        CookMode.AUTONOMOUS -> maybeAdjustPit(s, snapshot, probeF, now)
                        CookMode.COACH -> maybeAdvisePit(s, probeF, now)
                        CookMode.SET_AND_FORGET -> {}
                    }
                }
            }

            CookState.PULL_REACHED -> {
                val elapsedSincePull = now - (s.pullReachedAt ?: now)
                if (elapsedSincePull <= 1800 && now - s.lastPullNotifyAt >= 300) {
                    s.lastPullNotifyAt = now
                    notifier.notify("Pull temp reached", "Probe ${ftemp(probeF)} — pull the meat.")
                }
                if (snapshot.powerState == PowerState.OFF || probeF == null || probeF <= PROBE_UNPLUGGED_SENTINEL_F) {
                    s.state = CookState.COMPLETE
                    store.completeSession(s, "complete", serial)
                    notifier.notify("Cook complete", "Session closed.")
                    session = null
                }
            }

            else -> {}
        }
    }

    // --- control helpers ---------------------------------------------------

    private fun detectCookStart(s: CookSession): Boolean {
        val hist = s.probeHistory
        if (hist.size < 2) return false
        val cutoff = hist.last().ts - COOK_START_WINDOW_S
        val recent = hist.filter { it.ts >= cutoff }
        if (recent.size < 2) return false
        return (recent.first().probeF - recent.last().probeF) >= COOK_START_DROP_F
    }

    private fun wasAbove(s: CookSession, thresholdF: Int): Boolean = s.lastPitSetF >= thresholdF

    private suspend fun maybeAdjustPit(s: CookSession, snapshot: GmgSnapshot, probeF: Double, now: Double) {
        val cookStarted = s.cookStartedAt ?: return
        val elapsedH = (now - cookStarted) / 3600
        val expected = expectedProbeAt(s.projection, elapsedH)
        val delta = expected - probeF // > 0 = behind schedule
        val phase = phaseAt(s.projection, probeF, CP_MEATS.getValue(s.meatKey).pullF)
        var tol = 7.0
        if (phase == "stall") {
            tol = 3.0
            if (abs(delta) < 5) return
        } else if (phase == "post_stall" || phase == "single_phase") {
            tol = 3.0
        }
        if (abs(delta) < tol) return

        val target = s.pitTargetF
        val maxDelta = max(1.0, MAX_DELTA_PCT * target)
        val newSet: Int
        if (delta > 0) {
            val adjust = min(maxDelta, delta * 0.5)
            newSet = min(maxPitF, snapshot.grillSetTemp + adjust.roundToInt())
        } else {
            if (snapshot.grillSetTemp <= target) return
            val adjust = min(maxDelta, (snapshot.grillSetTemp - target).toDouble())
            newSet = max(target, snapshot.grillSetTemp - adjust.roundToInt())
        }

        val adjPct = abs(newSet - snapshot.grillSetTemp) / max(target, 1).toDouble()
        var minInterval = MIN_ADJ_INTERVAL_BASE_S + ((adjPct - 0.005) / 0.015) * ADJ_INTERVAL_SPAN_S
        minInterval = max(
            MIN_ADJ_INTERVAL_BASE_S.toDouble(),
            min(minInterval, (MIN_ADJ_INTERVAL_BASE_S + ADJ_INTERVAL_SPAN_S).toDouble()),
        )
        if (now - s.lastAdjAt < minInterval) return
        if (newSet == snapshot.grillSetTemp) return
        setPitTarget(newSet, "adjust ($phase)")
        s.lastAdjAt = now
    }

    private fun maybeAdvisePit(s: CookSession, probeF: Double, now: Double) {
        val cookStarted = s.cookStartedAt ?: return
        val elapsedH = (now - cookStarted) / 3600
        val expected = expectedProbeAt(s.projection, elapsedH)
        val delta = expected - probeF
        if (abs(delta) < COACH_ADVISE_BAND_F) return
        if (now - s.lastAdjAt < COACH_ADVISE_INTERVAL_S) return
        s.lastAdjAt = now
        if (delta > 0) {
            notifier.notify(
                "Coach: running behind",
                "Probe ${ftemp(probeF)} vs expected ${ftemp(expected)}. Consider raising the pit setpoint.",
            )
        } else {
            notifier.notify(
                "Coach: ahead of schedule",
                "Probe ${ftemp(probeF)} vs expected ${ftemp(expected)}. " +
                    "Consider lowering the pit toward ${ftemp(s.pitTargetF)}.",
            )
        }
    }

    /** User override: probe already buried in cold meat — begin tracking now. */
    fun markMeatOn() {
        val s = session
        if (s == null) {
            notifier.notify("Meat-on ignored", "No active cook session to apply 'meat is on' to.")
            return
        }
        if (s.state !in setOf(CookState.PLANNED, CookState.PREHEATING, CookState.WAITING_MEAT)) return
        s.state = CookState.COOKING
        s.cookStartedAt = clock()
        s.preheatReadySince = null
        notifier.notify("Cook started", "Meat-on override — tracking the cook now.")
    }

    private suspend fun setPitTarget(pitF: Int, reason: String) {
        val clamped = max(PIT_CLAMP_MIN_F, min(maxPitF, pitF))
        runCatching { controller.setGrillTemp(clamped) }.onFailure { return }
        session?.let {
            it.lastPitSetF = clamped
            it.notes.add("$reason: $clamped°F")
        }
    }

    companion object {
        const val PIT_CLAMP_MIN_F = 150
        const val PIT_CLAMP_MAX_F = 375
        const val COOK_START_DROP_F = 30.0
        const val COOK_START_WINDOW_S = 60.0
        const val PREHEAT_BAND_F = 10
        const val PREHEAT_HOLD_S = 180.0
        const val APPROACHING_BAND_F = 10
        const val MAX_DELTA_PCT = 0.02
        const val MIN_ADJ_INTERVAL_BASE_S = 60
        const val ADJ_INTERVAL_SPAN_S = 120
        const val PIT_ERROR_TRIP_F = 200
        const val PROBE_UNPLUGGED_SENTINEL_F = 89
        const val COACH_ADVISE_BAND_F = 8
        const val COACH_ADVISE_INTERVAL_S = 900
    }
}
