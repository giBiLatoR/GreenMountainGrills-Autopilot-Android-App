package com.gibilator.gmg.data

import android.content.Context
import android.net.wifi.WifiManager
import com.gibilator.gmg.cook.CookManager
import com.gibilator.gmg.cook.CookMode
import com.gibilator.gmg.cook.CookNotifier
import com.gibilator.gmg.cook.CookPhysics.CP_MEATS
import com.gibilator.gmg.cook.CookPhysics.expectedProbeAt
import com.gibilator.gmg.cook.CookPhysics.phaseAt
import com.gibilator.gmg.cook.CookState
import com.gibilator.gmg.cook.GrillController
import com.gibilator.gmg.cook.PreFlightResult
import com.gibilator.gmg.net.Discovery
import com.gibilator.gmg.net.GmgClient
import com.gibilator.gmg.protocol.DiscoveredGrill
import com.gibilator.gmg.protocol.GmgGrillInfo
import com.gibilator.gmg.protocol.GmgServerModeException
import com.gibilator.gmg.protocol.GmgSnapshot
import com.gibilator.gmg.units.Units
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * App-wide singleton that owns the active grill client + cook manager and holds
 * the [GrillUiState] the UI observes. Replaces the HA DataUpdateCoordinator.
 */
class GrillRepository(
    private val context: Context,
    notifier: CookNotifier,
) {
    val prefs = GmgPreferences(context)
    val store = GmgStore(context)

    private val _state = MutableStateFlow(GrillUiState())
    val state: StateFlow<GrillUiState> = _state.asStateFlow()

    private var client: GmgClient? = null
    private var info: GmgGrillInfo? = null
    private val samples = ArrayDeque<Sample>()

    private var currentPrefs: GmgPrefs? = null

    private val controller = object : GrillController {
        override suspend fun powerOn() { client?.powerOn() }
        override suspend fun setGrillTemp(f: Int) { client?.setGrillTemp(f) }
    }
    private val cookManager = CookManager(controller, notifier, store)

    val maxPitF: Int get() = currentPrefs?.maxPitF ?: 375

    /** Push current prefs into the cook manager. Call when prefs change. */
    fun applyPrefs(p: GmgPrefs) {
        currentPrefs = p
        val metric = isMetricLocale()
        cookManager.configure(
            autoCook = p.autoCookEnabled,
            devMode = p.devMode,
            push = p.push,
            maxPitF = p.maxPitF,
            tempUnit = Units.resolveTempUnit(p.tempUnitPref, metric),
            weightUnit = Units.resolveWeightUnit(p.weightUnitPref, metric),
            notifyLevel = p.notifyLevel,
        )
    }

    /** Probe + attach to a grill at [host]. */
    suspend fun connect(host: String): Result<GmgGrillInfo> {
        _state.value = _state.value.copy(conn = ConnState.Connecting, error = null)
        val c = GmgClient(host)
        return try {
            val grillInfo = c.probe()
            client = c
            info = grillInfo
            cookManager.setSerial(grillInfo.serial)
            currentPrefs?.let { applyPrefs(it) }
            store.upsertGrill(
                StoredGrill(
                    serial = grillInfo.serial,
                    host = host,
                    modelId = grillInfo.modelId,
                    model = grillInfo.model,
                    firmware = grillInfo.firmware,
                    label = grillInfo.model + " (" + grillInfo.serial + ")",
                ),
            )
            // Resume an in-flight cook if one was persisted (app/service was killed
            // or the phone left WiFi range and came back).
            store.loadActiveSession(grillInfo.serial)?.let { active ->
                cookManager.restoreActiveSession(
                    meatKey = active.meatKey,
                    weightKg = active.weightKg,
                    probeIndex = active.probeIndex,
                    mode = CookMode.from(active.mode),
                    pitTargetF = active.pitTargetF,
                    state = CookState.from(active.state),
                    createdAt = active.createdAt,
                    cookStartedAt = active.cookStartedAt,
                    pullReachedAt = active.pullReachedAt,
                )
            }
            samples.clear()
            _state.value = GrillUiState(
                conn = ConnState.Connected,
                info = grillInfo,
                snapshot = grillInfo.snapshot,
                cook = buildCookView(grillInfo.snapshot),
            )
            Result.success(grillInfo)
        } catch (e: GmgServerModeException) {
            _state.value = _state.value.copy(conn = ConnState.ServerMode, error = e.message)
            Result.failure(e)
        } catch (e: Exception) {
            _state.value = _state.value.copy(conn = ConnState.Error, error = e.message)
            Result.failure(e)
        }
    }

    /** One poll cycle: snapshot, run cook loop, push state. */
    suspend fun pollOnce() {
        val c = client ?: return
        try {
            val snapshot = c.poll()
            runCatching { cookManager.update(snapshot) }
            appendSample(snapshot)
            _state.value = _state.value.copy(
                conn = ConnState.Connected,
                snapshot = snapshot,
                cook = buildCookView(snapshot),
                samples = samples.toList(),
                error = null,
            )
        } catch (e: GmgServerModeException) {
            _state.value = _state.value.copy(conn = ConnState.ServerMode, error = e.message)
        } catch (e: Exception) {
            _state.value = _state.value.copy(error = e.message)
        }
    }

    // --- commands (clamp + refresh, like the coordinator) ------------------

    // Every command is wrapped so a failed/unacknowledged UDP write (common when
    // the grill is off or briefly unreachable) surfaces as an error in the UI
    // state instead of an uncaught coroutine exception that crashes the app.
    private suspend fun command(block: suspend () -> Unit) {
        runCatching { block() }.onFailure { e ->
            _state.value = _state.value.copy(error = e.message ?: "That didn't go through — try again.")
        }
        pollOnce()
    }

    suspend fun setGrillTemp(f: Int) {
        // The grill controller mishandles a setpoint sent during its cold-start
        // sequence, so block heat changes until it's up to its minimum operating
        // temp (150°F). Covers the manual path; Auto-Cook gates its own preheat.
        val snap = _state.value.snapshot
        if (snap == null || snap.grillTemp < CookManager.LAUNCH_READY_TEMP_F) {
            _state.value = _state.value.copy(
                error = "Hold on — let the grill reach 150°F before changing the heat. " +
                    "The controller can't take a new setpoint while it's still starting up.",
            )
            return
        }
        command { client?.setGrillTemp(max(150, min(maxPitF, f))) }
    }
    suspend fun setProbeTarget(probe: Int, f: Int) = command { client?.setProbeTarget(probe, f) }
    suspend fun powerOn() = command { client?.powerOn() }
    suspend fun powerOff() = command { client?.powerOff() }
    suspend fun coldSmoke() = command { client?.coldSmoke() }

    fun preFlight(meatKey: String, weightKg: Double, finishInHours: Double): PreFlightResult =
        cookManager.preFlight(meatKey, weightKg, finishInHours)

    suspend fun startCook(meatKey: String, weightKg: Double, probeIndex: Int, mode: CookMode, finishInHours: Double) {
        val snapshot = _state.value.snapshot ?: throw IllegalStateException("no snapshot available yet")
        cookManager.startCook(meatKey, weightKg, probeIndex, mode, finishInHours, snapshot)
        pollOnce()
    }

    suspend fun abortCook() { cookManager.abortCook(); pollOnce() }
    fun markMeatOn() { cookManager.markMeatOn() }

    /** Broadcast discovery, holding a Wi-Fi multicast lock for reply reception. */
    suspend fun discover(): List<DiscoveredGrill> {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = wifi?.createMulticastLock("gmg-discovery")?.apply { setReferenceCounted(false); acquire() }
        return try {
            Discovery.discover()
        } finally {
            runCatching { lock?.release() }
        }
    }

    suspend fun knownGrills() = store.listGrills()

    suspend fun listSessions(): List<StoredSession> =
        info?.serial?.let { store.listSessions(it) } ?: emptyList()

    suspend fun sessionLog(id: Long): List<LoggedSample> = store.loadLog(id)

    // --- helpers -----------------------------------------------------------

    private fun appendSample(snapshot: GmgSnapshot) {
        val session = cookManager.session
        val probeIndex = session?.probeIndex ?: 1
        val probeF = if (probeIndex == 1) snapshot.probe1Temp else snapshot.probe2Temp
        val expected = session?.cookStartedAt?.let {
            expectedProbeAt(session.projection, (nowS() - it) / 3600.0)
        }
        samples.addLast(Sample(System.currentTimeMillis(), snapshot.grillTemp, probeF, expected))
        while (samples.size > MAX_SAMPLES) samples.removeFirst()
    }

    private fun buildCookView(snapshot: GmgSnapshot): CookView {
        val s = cookManager.session
            ?: return CookView(false, "idle", null, null, null, null, null, null, null, null, null, null, null, null, 1)
        val meat = CP_MEATS[s.meatKey]
        val probeF = if (s.probeIndex == 1) snapshot.probe1Temp else snapshot.probe2Temp
        val pullF = meat?.pullF
        val now = nowS()
        val started = s.cookStartedAt
        val elapsedMin = started?.let { (now - it) / 60.0 }
        val totalMin = s.projection.totalHours * 60
        val remainingMin = started?.let { max(0.0, totalMin - (now - it) / 60.0) }
        val expected = started?.let { expectedProbeAt(s.projection, (now - it) / 3600.0) }
        val phaseKey = if (probeF != null && pullF != null) phaseAt(s.projection, probeF.toDouble(), pullF) else null
        val delta = if (expected != null && probeF != null) expected - probeF else null
        val onSchedule = if (expected != null && probeF != null) abs(expected - probeF) <= 10.0 else null
        return CookView(
            active = s.state != CookState.IDLE,
            rawState = s.state.value,
            meatKey = s.meatKey,
            meatLabel = meat?.label,
            pitTargetF = s.pitTargetF,
            pullF = pullF,
            expectedProbeF = expected,
            probeF = probeF,
            elapsedMin = elapsedMin,
            remainingMin = remainingMin,
            onSchedule = onSchedule,
            phaseKey = phaseKey,
            deltaF = delta,
            projection = s.projection,
            probeIndex = s.probeIndex,
        )
    }

    private fun nowS(): Double = System.currentTimeMillis() / 1000.0

    private fun isMetricLocale(): Boolean =
        Locale.getDefault().country !in setOf("US", "LR", "MM")

    companion object {
        const val MAX_SAMPLES = 1000
    }
}
