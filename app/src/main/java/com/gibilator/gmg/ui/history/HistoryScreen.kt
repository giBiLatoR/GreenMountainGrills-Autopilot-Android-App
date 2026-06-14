package com.gibilator.gmg.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gibilator.gmg.cook.CookPhysics.CP_MEATS
import com.gibilator.gmg.data.LoggedSample
import com.gibilator.gmg.data.Sample
import com.gibilator.gmg.data.StoredSession
import com.gibilator.gmg.ui.components.CookChart
import com.gibilator.gmg.ui.components.MeatArt
import com.gibilator.gmg.ui.components.SectionCard
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.GoodGreen
import com.gibilator.gmg.ui.theme.Muted
import com.gibilator.gmg.ui.theme.WarnRed
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    history: List<StoredSession>,
    sessionLog: List<LoggedSample>,
    onLoad: () -> Unit,
    onOpen: (Long) -> Unit,
    onClose: () -> Unit,
) {
    LaunchedEffect(Unit) { onLoad() }
    var selected by remember { mutableStateOf<StoredSession?>(null) }

    if (selected != null) {
        CookDetail(selected!!, sessionLog) { selected = null; onClose() }
        return
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Cook history", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))
        if (history.isEmpty()) {
            Text("No cooks yet. Start one and it'll show up here with a graph.", color = Muted)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(history) { s ->
                    HistoryRow(s) { selected = s; onOpen(s.id) }
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(s: StoredSession, onClick: () -> Unit) {
    SectionCard(null, Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MeatArt(s.meatKey)
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(CP_MEATS[s.meatKey]?.label ?: s.meatKey, fontWeight = FontWeight.SemiBold)
                Text(formatWhen(s.createdAt), color = Muted, style = MaterialTheme.typography.bodyMedium)
            }
            OutcomeChip(s.state)
        }
    }
}

@Composable
private fun OutcomeChip(state: String) {
    val (label, color) = when (state) {
        "complete" -> "Done" to GoodGreen
        "aborted" -> "Stopped" to WarnRed
        else -> "In progress" to Ember
    }
    Text(label, color = color, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun CookDetail(s: StoredSession, log: List<LoggedSample>, onBack: () -> Unit) {
    val samples = remember(log) {
        log.map { Sample(tMs = (it.ts * 1000).toLong(), grillF = it.pitF ?: 0, probeF = it.probeF, expectedF = null) }
    }
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        TextButton(onClick = onBack) { Text("← Back to history") }
        Text(CP_MEATS[s.meatKey]?.label ?: s.meatKey, style = MaterialTheme.typography.headlineMedium)
        Text(formatWhen(s.createdAt), color = Muted)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Stat("Target heat", "${s.pitTargetF}°F")
            Stat("Mode", s.mode.replace('_', ' '))
            Stat("Result", if (s.state == "complete") "Done" else s.state)
        }
        Spacer(Modifier.height(16.dp))
        if (samples.size >= 2) {
            SectionCard("TEMPERATURE OVER TIME") { CookChart(samples) }
        } else {
            Text("Not enough logged data to chart this cook.", color = Muted)
        }
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column {
        Text(label.uppercase(), style = MaterialTheme.typography.labelSmall, color = Muted)
        Text(value, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatWhen(epochSeconds: Double): String =
    SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault()).format(Date((epochSeconds * 1000).toLong()))
