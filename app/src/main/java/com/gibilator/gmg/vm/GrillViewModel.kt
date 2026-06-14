package com.gibilator.gmg.vm

import android.app.Application
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.gibilator.gmg.GmgApp
import com.gibilator.gmg.cook.CookMode
import com.gibilator.gmg.cook.CookPhysics.Phase
import com.gibilator.gmg.data.GmgPrefs
import com.gibilator.gmg.data.GrillUiState
import com.gibilator.gmg.data.LoggedSample
import com.gibilator.gmg.data.StoredGrill
import com.gibilator.gmg.data.StoredSession
import com.gibilator.gmg.protocol.DiscoveredGrill
import com.gibilator.gmg.service.PollService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Result of a pre-flight preview for the wizard. */
data class PreviewUi(
    val pitTargetF: Int = 0,
    val totalHours: Double = 0.0,
    val phases: List<Phase> = emptyList(),
    val warnings: List<String> = emptyList(),
    val error: String? = null,
)

/** State for the discovery / grills screen. */
data class DiscoveryUi(
    val loading: Boolean = false,
    val found: List<DiscoveredGrill> = emptyList(),
    val known: List<StoredGrill> = emptyList(),
)

class GrillViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = GmgApp.repo

    val uiState: StateFlow<GrillUiState> = repo.state

    val prefs: StateFlow<GmgPrefs?> =
        repo.prefs.flow.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _preview = MutableStateFlow<PreviewUi?>(null)
    val preview: StateFlow<PreviewUi?> = _preview.asStateFlow()

    private val _discovery = MutableStateFlow(DiscoveryUi())
    val discovery: StateFlow<DiscoveryUi> = _discovery.asStateFlow()

    private val _history = MutableStateFlow<List<StoredSession>>(emptyList())
    val history: StateFlow<List<StoredSession>> = _history.asStateFlow()

    private val _sessionLog = MutableStateFlow<List<LoggedSample>>(emptyList())
    val sessionLog: StateFlow<List<LoggedSample>> = _sessionLog.asStateFlow()

    init {
        viewModelScope.launch {
            repo.prefs.flow.collect { repo.applyPrefs(it) }
        }
        viewModelScope.launch { autoConnect() }
    }

    private suspend fun autoConnect() {
        val p = repo.prefs.flow.first()
        val serial = p.selectedSerial ?: return
        val host = repo.knownGrills().firstOrNull { it.serial == serial }?.host ?: return
        val result = repo.connect(host)
        if (result.isSuccess) PollService.start(getApplication())
    }

    fun connectTo(host: String) = viewModelScope.launch {
        val result = repo.connect(host)
        result.getOrNull()?.let {
            repo.prefs.setSelectedSerial(it.serial)
            PollService.start(getApplication())
        }
    }

    fun selectKnown(grill: StoredGrill) = connectTo(grill.host)

    // --- manual controls ---------------------------------------------------

    fun powerOn() = viewModelScope.launch { repo.powerOn() }
    fun powerOff() = viewModelScope.launch { repo.powerOff() }
    fun coldSmoke() = viewModelScope.launch { repo.coldSmoke() }
    fun setGrillTemp(f: Int) = viewModelScope.launch { repo.setGrillTemp(f) }
    fun setProbeTarget(probe: Int, f: Int) = viewModelScope.launch { repo.setProbeTarget(probe, f) }

    // --- auto-cook ---------------------------------------------------------

    fun preview(meatKey: String, weightKg: Double, finishInHours: Double) {
        _preview.value = try {
            val pf = repo.preFlight(meatKey, weightKg, finishInHours)
            PreviewUi(pf.pitTargetF, pf.projection.totalHours, pf.projection.phases, pf.warnings)
        } catch (e: Exception) {
            PreviewUi(error = e.message ?: "Couldn't plan that cook")
        }
    }

    fun clearPreview() { _preview.value = null }

    fun startCook(meatKey: String, weightKg: Double, probeIndex: Int, mode: CookMode, finishInHours: Double) =
        viewModelScope.launch {
            repo.prefs.setPlanner(meatKey, mode.value, probeIndex, weightKg, finishInHours)
            runCatching { repo.startCook(meatKey, weightKg, probeIndex, mode, finishInHours) }
            PollService.start(getApplication())
        }

    fun abortCook() = viewModelScope.launch { repo.abortCook() }
    fun markMeatOn() = repo.markMeatOn()

    // --- discovery ---------------------------------------------------------

    fun runDiscovery() = viewModelScope.launch {
        _discovery.value = _discovery.value.copy(loading = true, known = repo.knownGrills())
        val found = runCatching { repo.discover() }.getOrDefault(emptyList())
        _discovery.value = _discovery.value.copy(loading = false, found = found, known = repo.knownGrills())
    }

    // --- settings ----------------------------------------------------------

    fun setScanInterval(s: Int) = viewModelScope.launch { repo.prefs.setScanInterval(s) }
    fun setMaxPit(f: Int) = viewModelScope.launch { repo.prefs.setMaxPitF(f) }
    fun setAutoCook(b: Boolean) = viewModelScope.launch { repo.prefs.setAutoCook(b) }
    fun setPush(b: Boolean) = viewModelScope.launch { repo.prefs.setPush(b) }
    fun setNotifyLevel(level: String) = viewModelScope.launch { repo.prefs.setNotifyLevel(level) }
    fun setDevMode(b: Boolean) = viewModelScope.launch { repo.prefs.setDevMode(b) }
    fun setTempUnit(pref: String) = viewModelScope.launch { repo.prefs.setTempUnit(pref) }
    fun setWeightUnit(pref: String) = viewModelScope.launch { repo.prefs.setWeightUnit(pref) }
    fun completeOnboarding() = viewModelScope.launch { repo.prefs.setOnboardingDone(true) }

    // --- history -----------------------------------------------------------

    fun loadHistory() = viewModelScope.launch { _history.value = repo.listSessions() }
    fun openSession(id: Long) = viewModelScope.launch { _sessionLog.value = repo.sessionLog(id) }
    fun closeSession() { _sessionLog.value = emptyList() }

    // --- monitoring lifecycle ---------------------------------------------

    /** Quit: stop the foreground poll service and clear all notifications. */
    fun stopMonitoring() {
        PollService.stop(getApplication())
        runCatching { NotificationManagerCompat.from(getApplication<Application>()).cancelAll() }
    }
}
