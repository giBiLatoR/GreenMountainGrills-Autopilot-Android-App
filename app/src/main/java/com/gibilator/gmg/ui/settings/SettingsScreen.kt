package com.gibilator.gmg.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gibilator.gmg.data.GmgPrefs
import com.gibilator.gmg.ui.components.SectionCard
import com.gibilator.gmg.ui.theme.Muted
import com.gibilator.gmg.ui.theme.WarnRed
import com.gibilator.gmg.units.Units

@Composable
fun SettingsScreen(
    prefs: GmgPrefs?,
    onScanInterval: (Int) -> Unit,
    onMaxPit: (Int) -> Unit,
    onAutoCook: (Boolean) -> Unit,
    onPush: (Boolean) -> Unit,
    onNotifyLevel: (String) -> Unit,
    onDevMode: (Boolean) -> Unit,
    onTempUnit: (String) -> Unit,
    onWeightUnit: (String) -> Unit,
    onStopMonitoring: () -> Unit,
) {
    val p = prefs ?: return
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionCard("AUTO-COOK") {
            ToggleRow("Enable Auto-Cook control", "Let the app run the planner + autonomous pit control.", p.autoCookEnabled, onAutoCook)
            ToggleRow("Developer logging", "Extra-detailed cook logging (slightly more storage).", p.devMode, onDevMode)
        }

        SectionCard("NOTIFICATIONS") {
            ToggleRow("Notifications", "Master switch for all cook alerts.", p.push, onPush)
            if (p.push) {
                Text("How much to notify", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                UnitChoice(
                    listOf("Everything" to "all", "Milestones" to "milestones", "Only when done" to "critical"),
                    p.notifyLevel, onNotifyLevel,
                )
                Text(
                    "Fewer notifications = less buzzing. \"Milestones\" covers grill-ready, almost-done and done. " +
                        "\"Only when done\" pings just for the finish and problems — easiest on the battery.",
                    color = Muted, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        SectionCard("SAFETY") {
            LabeledSlider(
                "Max grill heat: ${p.maxPitF}°F",
                "The app will never set the grill above this. Hard ceiling 375°F.",
                p.maxPitF.toFloat(), 150f..375f, ((375 - 150) / 5),
            ) { onMaxPit((it / 5).toInt() * 5) }
        }

        SectionCard("MONITORING") {
            LabeledSlider(
                "Check every ${p.scanIntervalS}s",
                "How often to poll the grill.",
                p.scanIntervalS.toFloat(), 5f..60f, 11,
            ) { onScanInterval(it.toInt()) }
        }

        SectionCard("UNITS") {
            Text("Temperature", color = Muted, style = MaterialTheme.typography.bodyMedium)
            UnitChoice(
                listOf("Auto" to Units.TEMP_UNIT_AUTO, "°F" to Units.TEMP_UNIT_FAHRENHEIT, "°C" to Units.TEMP_UNIT_CELSIUS),
                p.tempUnitPref, onTempUnit,
            )
            Text("Weight", color = Muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
            UnitChoice(
                listOf("Auto" to Units.WEIGHT_UNIT_AUTO, "kg" to Units.WEIGHT_UNIT_KILOGRAMS, "lb" to Units.WEIGHT_UNIT_POUNDS),
                p.weightUnitPref, onWeightUnit,
            )
        }

        SectionCard("QUIT") {
            Text(
                "Stops monitoring in the background and clears all notifications. " +
                    "Your current cook is saved — it resumes when you reconnect.",
                color = Muted, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(bottom = 10.dp),
            )
            OutlinedButton(onClick = onStopMonitoring, modifier = Modifier.fillMaxWidth()) {
                Text("⏻  Stop monitoring & quit", color = WarnRed)
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun ToggleRow(title: String, desc: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(desc, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun LabeledSlider(title: String, desc: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    Column {
        Text(title, fontWeight = FontWeight.SemiBold)
        Text(desc, color = Muted, style = MaterialTheme.typography.bodyMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range, steps = steps)
    }
}

@Composable
private fun UnitChoice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        options.forEachIndexed { i, (label, value) ->
            SegmentedButton(
                selected = selected == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(i, options.size),
            ) { Text(label) }
        }
    }
}
