package com.gibilator.gmg.ui.grills

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.gibilator.gmg.ui.components.RadarScan
import com.gibilator.gmg.ui.components.SectionCard
import com.gibilator.gmg.ui.components.WifiSetupCard
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.Muted
import com.gibilator.gmg.vm.DiscoveryUi

@Composable
fun GrillsScreen(
    discovery: DiscoveryUi,
    onRunDiscovery: () -> Unit,
    onConnectHost: (String) -> Unit,
) {
    LaunchedEffect(Unit) { onRunDiscovery() }
    var manualHost by remember { mutableStateOf("") }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Your grills", style = MaterialTheme.typography.headlineMedium)

        if (discovery.loading) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    RadarScan(Modifier.size(120.dp))
                    Text("Looking for your grill…", color = Muted, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }

        if (discovery.found.isNotEmpty()) {
            SectionCard("FOUND ON YOUR NETWORK") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discovery.found.forEach { g ->
                        GrillRow(g.serial, g.host) { onConnectHost(g.host) }
                    }
                }
            }
        }

        if (discovery.known.isNotEmpty()) {
            SectionCard("SAVED GRILLS") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    discovery.known.forEach { g ->
                        GrillRow(g.label, g.host) { onConnectHost(g.host) }
                    }
                }
            }
        }

        if (!discovery.loading && discovery.found.isEmpty() && discovery.known.isEmpty()) {
            Text(
                "No grill found yet. Make sure the smoker is on and on the same Wi-Fi, then search again — or enter its IP below.",
                color = Muted,
            )
            WifiSetupCard()
        }

        OutlinedButton(onClick = onRunDiscovery, modifier = Modifier.fillMaxWidth()) {
            Text("🔍  Search again")
        }

        SectionCard("ENTER IP MANUALLY") {
            Column {
                OutlinedTextField(
                    value = manualHost,
                    onValueChange = { manualHost = it },
                    label = { Text("Grill IP (e.g. 192.168.1.148)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = { if (manualHost.isNotBlank()) onConnectHost(manualHost.trim()) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Ember),
                ) { Text("Connect") }
            }
        }
    }
}

@Composable
private fun GrillRow(title: String, host: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("🔥", modifier = Modifier.padding(end = 12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(host, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
        Text("Connect", color = Ember, fontWeight = FontWeight.SemiBold)
    }
}
