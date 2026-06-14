package com.gibilator.gmg.ui.wizard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gibilator.gmg.cook.CookMode
import com.gibilator.gmg.cook.CookPhysics
import com.gibilator.gmg.cook.CookPhysics.CP_MEATS
import com.gibilator.gmg.data.GmgPrefs
import com.gibilator.gmg.ui.components.MeatArt
import com.gibilator.gmg.ui.components.fmtTempF
import com.gibilator.gmg.ui.components.tempUnit
import com.gibilator.gmg.ui.components.weightUnit
import com.gibilator.gmg.ui.theme.Amber
import com.gibilator.gmg.ui.theme.CharcoalCard
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.Muted
import com.gibilator.gmg.ui.theme.ProbeBlue
import com.gibilator.gmg.ui.theme.WarnRed
import com.gibilator.gmg.units.Units
import com.gibilator.gmg.vm.PreviewUi

@Composable
fun NewCookScreen(
    prefs: GmgPrefs?,
    preview: PreviewUi?,
    onPreview: (String, Double, Double) -> Unit,
    onClearPreview: () -> Unit,
    onStart: (String, Double, Int, CookMode, Double) -> Unit,
    onDone: () -> Unit,
) {
    var meatKey by remember { mutableStateOf(prefs?.plannerMeat ?: "beef_brisket_packer") }
    var weightKg by remember { mutableFloatStateOf((prefs?.plannerWeightKg ?: 4.0).toFloat()) }
    var finishH by remember { mutableFloatStateOf((prefs?.plannerFinishH ?: 12.0).toFloat()) }
    var mode by remember { mutableStateOf(CookMode.from(prefs?.plannerMode ?: "autonomous")) }
    val probe = prefs?.plannerProbe ?: 1
    val unit = prefs.tempUnit()
    val wUnit = prefs.weightUnit()

    // The steps depend on the meat: by-the-piece items skip weight; fixed-temp
    // items skip the finish-time question (their time is set by the temp).
    val meat = CP_MEATS[meatKey]
    val steps = remember(meatKey) {
        buildList {
            add(WizStep.MEAT)
            if (meat?.byThePiece != true) add(WizStep.WEIGHT)
            if (meat?.fixedPitF == null) add(WizStep.FINISH)
            add(WizStep.MODE)
            add(WizStep.PLAN)
        }
    }
    var stepIdx by remember { mutableIntStateOf(0) }
    val idx = stepIdx.coerceIn(0, steps.lastIndex)
    val current = steps[idx]

    LaunchedEffect(current, meatKey, weightKg, finishH) {
        if (current == WizStep.PLAN) onPreview(meatKey, weightKg.toDouble(), finishH.toDouble())
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        StepDots(idx, steps.size)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (current) {
                WizStep.MEAT -> MeatStep(meatKey) { meatKey = it }
                WizStep.WEIGHT -> WeightStep(weightKg, wUnit) { weightKg = it }
                WizStep.FINISH -> FinishStep(finishH) { finishH = it }
                WizStep.MODE -> ModeStep(mode) { mode = it }
                WizStep.PLAN -> PlanStep(preview, unit)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (idx > 0) {
                TextButton(
                    onClick = { if (current == WizStep.PLAN) onClearPreview(); stepIdx = idx - 1 },
                    modifier = Modifier.weight(1f),
                ) { Text("Back") }
            }
            Button(
                onClick = {
                    if (current != WizStep.PLAN) {
                        stepIdx = idx + 1
                    } else {
                        onStart(meatKey, weightKg.toDouble(), probe, mode, finishH.toDouble())
                        onClearPreview()
                        onDone()
                    }
                },
                modifier = Modifier.weight(2f),
                enabled = current != WizStep.PLAN || (preview != null && preview.error == null),
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
            ) {
                Text(if (current != WizStep.PLAN) "Next" else "🔥  Start cooking", fontWeight = FontWeight.Bold)
            }
        }
    }
}

private enum class WizStep { MEAT, WEIGHT, FINISH, MODE, PLAN }

@Composable
private fun StepDots(step: Int, total: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i ->
            Box(
                Modifier
                    .height(6.dp)
                    .weight(1f)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (i <= step) Ember else CharcoalCard),
            )
        }
    }
}

@Composable
private fun MeatStep(selected: String, onSelect: (String) -> Unit) {
    Column {
        Text("What are you cooking?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(columns = GridCells.Fixed(2), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(CP_MEATS.values.toList()) { meat ->
                val sel = meat.key == selected
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CharcoalCard)
                        .border(2.dp, if (sel) Ember else Color.Transparent, RoundedCornerShape(16.dp))
                        .clickable { onSelect(meat.key) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MeatArt(meat.key)
                    Text(
                        meat.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightStep(weightKg: Float, wUnit: String, onChange: (Float) -> Unit) {
    Column {
        Text("How big is it?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        Text(Units.fmtWeight(weightKg.toDouble(), wUnit), style = MaterialTheme.typography.headlineLarge, color = Ember)
        Text("Roughly feeds ${(weightKg * 2.2).toInt().coerceAtLeast(1)} people", color = Muted)
        Spacer(Modifier.height(16.dp))
        Slider(value = weightKg, onValueChange = onChange, valueRange = 0.2f..12f)
        Text("Slide to set the weight. A guess is fine — it just tunes the timing.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinishStep(finishH: Float, onChange: (Float) -> Unit) {
    var byClock by remember { mutableStateOf(false) }
    Column {
        Text("When do you want to eat?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = !byClock, onClick = { byClock = false }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("In hours")
            }
            SegmentedButton(selected = byClock, onClick = { byClock = true }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("At a time")
            }
        }
        Spacer(Modifier.height(16.dp))
        val h = finishH.toInt(); val m = ((finishH - h) * 60).toInt()
        if (!byClock) {
            Text("In ${h}h ${m}m", style = MaterialTheme.typography.headlineLarge, color = Ember)
            Spacer(Modifier.height(16.dp))
            Slider(value = finishH, onValueChange = onChange, valueRange = 1f..30f, steps = 57)
            Text("The app picks a grill temperature to hit this finish time.", color = Muted, style = MaterialTheme.typography.bodyMedium)
        } else {
            val picker = rememberTimePickerState(initialHour = 18, initialMinute = 0, is24Hour = false)
            // Convert the chosen clock time into hours-from-now whenever it changes.
            LaunchedEffect(picker.hour, picker.minute) {
                onChange(hoursUntil(picker.hour, picker.minute).toFloat().coerceIn(1f, 30f))
            }
            TimePicker(state = picker)
            Text(
                "Ready about ${readyTimeLabel(picker.hour, picker.minute)}  (in ${h}h ${m}m)",
                color = Ember, style = MaterialTheme.typography.titleMedium,
            )
            Text(
                "Includes the ~10 min startup. If the time's already passed today, it plans for tomorrow.",
                color = Muted, style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun hoursUntil(hour: Int, minute: Int): Double {
    val now = java.time.LocalDateTime.now()
    var target = now.withHour(hour).withMinute(minute).withSecond(0).withNano(0)
    if (!target.isAfter(now)) target = target.plusDays(1)
    return java.time.Duration.between(now, target).toMinutes() / 60.0
}

private fun readyTimeLabel(hour: Int, minute: Int): String =
    java.time.LocalTime.of(hour, minute).format(java.time.format.DateTimeFormatter.ofPattern("h:mm a"))

@Composable
private fun ModeStep(mode: CookMode, onSelect: (CookMode) -> Unit) {
    Column {
        Text("How hands-on do you want to be?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        ModeCard("Auto-pilot", "The app nudges the grill to hit your finish time. Most hands-off.", mode == CookMode.AUTONOMOUS) { onSelect(CookMode.AUTONOMOUS) }
        Spacer(Modifier.height(10.dp))
        ModeCard("Set & forget", "Set the heat once and let it ride. You watch the progress.", mode == CookMode.SET_AND_FORGET) { onSelect(CookMode.SET_AND_FORGET) }
        Spacer(Modifier.height(10.dp))
        ModeCard("Coach me", "The app tells you what to adjust; you turn the dial.", mode == CookMode.COACH) { onSelect(CookMode.COACH) }
    }
}

@Composable
private fun ModeCard(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(CharcoalCard)
            .border(2.dp, if (selected) Ember else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = if (selected) Ember else MaterialTheme.colorScheme.onSurface)
        Text(desc, color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PlanStep(preview: PreviewUi?, unit: String) {
    Column {
        Text("Here's the plan", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        when {
            preview == null -> Text("Working it out…", color = Muted)
            preview.error != null -> Text(preview.error, color = WarnRed)
            else -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Grill heat: ", color = Muted)
                    Text(fmtTempF(preview.pitTargetF, unit), color = Ember, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                }
                Text("Cook time about ${"%.1f".format(preview.totalHours)} hours", color = MaterialTheme.colorScheme.onSurface)
                if (preview.preheatMin > 0 || preview.restMin > 0) {
                    Text(
                        "Plus ~${preview.preheatMin} min startup + ${preview.restMin} min rest before serving.",
                        color = Muted, style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Text("WHAT TO EXPECT", style = MaterialTheme.typography.labelSmall, color = Muted)
                Spacer(Modifier.height(6.dp))
                PhaseTimeline(preview.phases)
                if (preview.warnings.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    preview.warnings.forEach { Text("⚠ $it", color = Amber, style = MaterialTheme.typography.bodyMedium) }
                }
            }
        }
    }
}

@Composable
private fun PhaseTimeline(phases: List<CookPhysics.Phase>) {
    val total = phases.sumOf { it.hours }.coerceAtLeast(0.001)
    val colors = listOf(ProbeBlue, Amber, Ember)
    Column {
        Row(Modifier.fillMaxWidth().height(28.dp).clip(RoundedCornerShape(8.dp))) {
            phases.forEachIndexed { i, p ->
                Box(
                    Modifier
                        .weight((p.hours / total).toFloat().coerceAtLeast(0.02f))
                        .fillMaxSize()
                        .background(colors[i % colors.size]),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        phases.forEachIndexed { i, p ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(RoundedCornerShape(3.dp)).background(colors[i % colors.size]))
                Text("  ${friendlyPhase(p.name)} — ${"%.1f".format(p.hours)}h", color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun friendlyPhase(name: String): String = when {
    name.contains("Smoke") -> "Smoke & bark"
    name.contains("Stall") -> "The stall (normal)"
    name.contains("Render") -> "Getting tender"
    else -> name
}
