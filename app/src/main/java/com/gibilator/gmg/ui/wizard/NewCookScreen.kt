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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    var step by remember { mutableIntStateOf(0) }
    var meatKey by remember { mutableStateOf(prefs?.plannerMeat ?: "beef_brisket_packer") }
    var weightKg by remember { mutableFloatStateOf((prefs?.plannerWeightKg ?: 4.0).toFloat()) }
    var finishH by remember { mutableFloatStateOf((prefs?.plannerFinishH ?: 12.0).toFloat()) }
    var mode by remember { mutableStateOf(CookMode.from(prefs?.plannerMode ?: "autonomous")) }
    val probe = prefs?.plannerProbe ?: 1
    val unit = prefs.tempUnit()
    val wUnit = prefs.weightUnit()

    LaunchedEffect(step) {
        if (step == 4) onPreview(meatKey, weightKg.toDouble(), finishH.toDouble())
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        StepDots(step, 5)
        Spacer(Modifier.height(12.dp))
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (step) {
                0 -> MeatStep(meatKey) { meatKey = it }
                1 -> WeightStep(weightKg, wUnit) { weightKg = it }
                2 -> FinishStep(finishH) { finishH = it }
                3 -> ModeStep(mode) { mode = it }
                else -> PlanStep(preview, unit)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (step > 0) {
                TextButton(onClick = { if (step == 4) onClearPreview(); step-- }, modifier = Modifier.weight(1f)) {
                    Text("Back")
                }
            }
            Button(
                onClick = {
                    if (step < 4) {
                        step++
                    } else {
                        onStart(meatKey, weightKg.toDouble(), probe, mode, finishH.toDouble())
                        onClearPreview()
                        onDone()
                    }
                },
                modifier = Modifier.weight(2f),
                enabled = step < 4 || (preview != null && preview.error == null),
                colors = ButtonDefaults.buttonColors(containerColor = Ember),
            ) {
                Text(if (step < 4) "Next" else "🔥  Start cooking", fontWeight = FontWeight.Bold)
            }
        }
    }
}

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

@Composable
private fun FinishStep(finishH: Float, onChange: (Float) -> Unit) {
    Column {
        Text("When do you want to eat?", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(8.dp))
        val h = finishH.toInt(); val m = ((finishH - h) * 60).toInt()
        Text("In ${h}h ${m}m", style = MaterialTheme.typography.headlineLarge, color = Ember)
        Spacer(Modifier.height(16.dp))
        Slider(value = finishH, onValueChange = onChange, valueRange = 1f..30f, steps = 57)
        Text("The app picks a grill temperature to hit this finish time.", color = Muted, style = MaterialTheme.typography.bodyMedium)
    }
}

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
                Text("Ready in about ${"%.1f".format(preview.totalHours)} hours", color = MaterialTheme.colorScheme.onSurface)
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
