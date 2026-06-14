package com.gibilator.gmg.ui.onboarding

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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gibilator.gmg.ui.components.WifiSetupCard
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.Muted

@Composable
fun OnboardingScreen(onGetStarted: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(28.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(24.dp))
        Text("🔥", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text("GMG Control", style = MaterialTheme.typography.headlineLarge, color = Ember, fontWeight = FontWeight.Black)
        Text("Smoke like a pro — even your first time.", color = Muted, modifier = Modifier.padding(top = 4.dp))
        Spacer(Modifier.height(28.dp))

        Bullet("📺", "Watch from the couch", "Live grill + food temps, right on your phone.")
        Bullet("🎯", "Hit your dinner time", "Tell it when you want to eat — it picks the heat and drives the cook.")
        Bullet("🔔", "Never miss the moment", "Get pinged when the grill's ready and when it's done.")

        Spacer(Modifier.height(20.dp))
        WifiSetupCard()

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGetStarted,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Ember),
        ) { Text("Find my grill", fontWeight = FontWeight.Bold) }
        Text("Your grill needs to be on the same Wi-Fi.", color = Muted, modifier = Modifier.padding(top = 10.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Bullet(emoji: String, title: String, desc: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp)) {
        Text(emoji, modifier = Modifier.padding(end = 14.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Text(desc, color = Muted, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
