package com.gibilator.gmg.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gibilator.gmg.cook.CookCopy
import com.gibilator.gmg.cook.CookState
import com.gibilator.gmg.data.ConnState
import com.gibilator.gmg.data.GmgPrefs
import com.gibilator.gmg.data.GrillUiState
import com.gibilator.gmg.protocol.PowerState
import com.gibilator.gmg.ui.components.AnimatedTempReadout
import com.gibilator.gmg.ui.components.Confetti
import com.gibilator.gmg.ui.components.CookChart
import com.gibilator.gmg.ui.components.ProgressRing
import com.gibilator.gmg.ui.components.SectionCard
import com.gibilator.gmg.ui.components.SmokerHero
import com.gibilator.gmg.ui.components.Stepper
import com.gibilator.gmg.ui.components.WifiSetupCard
import com.gibilator.gmg.ui.components.tempUnit
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.GoodGreen
import com.gibilator.gmg.ui.theme.Muted
import com.gibilator.gmg.ui.theme.ProbeBlue
import com.gibilator.gmg.ui.theme.WarnRed
import kotlin.math.roundToInt

private fun stateOf(raw: String): CookState =
    CookState.entries.firstOrNull { it.value == raw } ?: CookState.IDLE

@Composable
fun HomeScreen(
    state: GrillUiState,
    prefs: GmgPrefs?,
    onPowerOn: () -> Unit,
    onPowerOff: () -> Unit,
    onColdSmoke: () -> Unit,
    onSetGrillTemp: (Int) -> Unit,
    onSetProbeTarget: (Int, Int) -> Unit,
    onMeatOn: () -> Unit,
    onAbort: () -> Unit,
    onNewCook: () -> Unit,
    onGoToGrills: () -> Unit,
) {
    val unit = prefs.tempUnit()
    val snapshot = state.snapshot
    var confirmShutdown by remember { mutableStateOf(false) }

    if (snapshot == null) {
        DisconnectedView(state.conn, onGoToGrills)
        return
    }

    val cook = state.cook
    val cookState = cook?.let { stateOf(it.rawState) } ?: CookState.IDLE
    val headline = if (cook != null && cook.active && cook.phaseKey != null) {
        CookCopy.phaseLabel(cook.phaseKey)
    } else {
        CookCopy.stateHeadline(cookState)
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.conn == ConnState.ServerMode) {
                SectionCard(null) {
                    Text("Server Mode is on", color = WarnRed, fontWeight = FontWeight.Bold)
                    Text(
                        "Your grill is routing through GMG's cloud, which blocks local control. " +
                            "Open the GMG app, go to WiFi Settings, and turn Server Mode OFF — then it'll work here.",
                        color = Muted,
                    )
                }
                WifiSetupCard(title = "How to turn Server Mode off")
            }

            SmokerHero(
                snapshot = snapshot,
                modelId = state.info?.modelId,
                tempUnit = unit,
                phaseHeadline = headline,
                onPowerTap = { if (snapshot.powerState.raw != 0) confirmShutdown = true else onPowerOn() },
            )

            if (cook != null && cook.active) {
                LiveCookSection(cook, unit, onMeatOn, onAbort)
                if (state.samples.size >= 2) {
                    SectionCard("COOK PROGRESS VS PLAN") { CookChart(state.samples) }
                }
            } else {
                Button(
                    onClick = onNewCook,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Ember),
                ) {
                    Icon(Icons.Rounded.RestaurantMenu, null, modifier = Modifier.padding(end = 8.dp))
                    Text("Start a guided cook", fontWeight = FontWeight.Bold)
                }
            }

            ManualControls(
                on = snapshot.powerState != PowerState.OFF,
                grillSet = snapshot.grillSetTemp,
                probe1Target = snapshot.probe1Target,
                probe2Target = snapshot.probe2Target,
                maxPit = prefs?.maxPitF ?: 375,
                unit = unit,
                onSetGrillTemp = onSetGrillTemp,
                onSetProbeTarget = onSetProbeTarget,
                onColdSmoke = onColdSmoke,
            )
            Spacer(Modifier.height(8.dp))
        }

        // Celebration overlay when it's done.
        if (cookState == CookState.PULL_REACHED) {
            Confetti(Modifier.fillMaxSize())
        }
    }

    if (confirmShutdown) {
        AlertDialog(
            onDismissRequest = { confirmShutdown = false },
            title = { Text("Shut the smoker down?") },
            text = { Text("This powers off the grill and begins its cooldown.") },
            confirmButton = {
                TextButton(onClick = { confirmShutdown = false; onPowerOff() }) {
                    Text("Shut down", color = WarnRed)
                }
            },
            dismissButton = { TextButton(onClick = { confirmShutdown = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LiveCookSection(
    cook: com.gibilator.gmg.data.CookView,
    unit: String,
    onMeatOn: () -> Unit,
    onAbort: () -> Unit,
) {
    val pull = cook.pullF
    val probe = cook.probeF
    val frac = if (pull != null && probe != null) ((probe - 38f) / (pull - 38f)) else 0f
    val remaining = cook.remainingMin?.let {
        val h = (it / 60).toInt(); val m = (it % 60).roundToInt(); if (h > 0) "~${h}h ${m}m left" else "~${m}m left"
    } ?: "tracking…"

    SectionCard(cook.meatLabel?.uppercase() ?: "COOK IN PROGRESS") {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            ProgressRing(
                fraction = frac,
                centerTop = probe?.let { com.gibilator.gmg.ui.components.fmtTempF(it, unit) } ?: "—",
                centerBottom = pull?.let { "of ${com.gibilator.gmg.ui.components.fmtTempF(it, unit)} · $remaining" } ?: remaining,
            )
            Spacer(Modifier.height(8.dp))
            cook.onSchedule?.let {
                Text(
                    if (it) "On track ✓" else "Running behind — that's okay",
                    color = if (it) GoodGreen else Ember,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            cook.phaseKey?.let { key ->
                Text(
                    CookCopy.whatsHappening(key, cook.deltaF, pull ?: 0, unit),
                    color = Muted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = onMeatOn, modifier = Modifier.weight(1f)) { Text("Meat is on") }
                OutlinedButton(onClick = onAbort, modifier = Modifier.weight(1f)) { Text("Stop", color = WarnRed) }
            }
        }
    }
}

@Composable
private fun ManualControls(
    on: Boolean,
    grillSet: Int,
    probe1Target: Int,
    probe2Target: Int,
    maxPit: Int,
    unit: String,
    onSetGrillTemp: (Int) -> Unit,
    onSetProbeTarget: (Int, Int) -> Unit,
    onColdSmoke: () -> Unit,
) {
    SectionCard("MANUAL CONTROL") {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            if (!on) {
                Text(
                    "Turn the grill on (START) to adjust temperatures.",
                    color = Muted,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            ControlRow("Grill heat", Ember, on) {
                Stepper(grillSet, { onSetGrillTemp(it) }, step = 5, min = 150, max = maxPit, unit = "°", enabled = on)
            }
            ControlRow("Food target (probe 1)", ProbeBlue, on) {
                Stepper(probe1Target, { onSetProbeTarget(1, it) }, step = 5, min = 32, max = 257, unit = "°", enabled = on)
            }
            ControlRow("Food target (probe 2)", ProbeBlue, on) {
                Stepper(probe2Target, { onSetProbeTarget(2, it) }, step = 5, min = 32, max = 257, unit = "°", enabled = on)
            }
            OutlinedButton(onClick = onColdSmoke, modifier = Modifier.fillMaxWidth()) {
                Text("❄  Cold smoke (smoke without heat)")
            }
        }
    }
}

@Composable
private fun ControlRow(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    enabled: Boolean,
    control: @Composable () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            color = if (enabled) color else Muted.copy(alpha = 0.5f),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        control()
    }
}

@Composable
private fun DisconnectedView(conn: ConnState, onGoToGrills: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🔥", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            if (conn == ConnState.Connecting) "Finding your grill…" else "No grill connected",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            "Connect to your Green Mountain Grill on the same Wi-Fi to start monitoring.",
            color = Muted,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        Button(onClick = onGoToGrills, colors = ButtonDefaults.buttonColors(containerColor = Ember)) {
            Text("Find my grill")
        }
    }
}
