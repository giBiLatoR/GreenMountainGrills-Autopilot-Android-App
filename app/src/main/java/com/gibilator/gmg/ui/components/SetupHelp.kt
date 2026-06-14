package com.gibilator.gmg.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.gibilator.gmg.ui.theme.Amber
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.Muted

/**
 * One-time setup help. The local protocol can't join the grill to WiFi or toggle
 * Server Mode — that's done once in the official GMG app. After that this app
 * runs fully local (no cloud, no GMG app).
 */
@Composable
fun WifiSetupCard(modifier: Modifier = Modifier, title: String = "First time? Quick one-time setup") {
    SectionCard(null, modifier) {
        Column {
            Text(title, fontWeight = FontWeight.Bold, color = Amber)
            Text(
                "Your smoker joins WiFi through the official Green Mountain Grills app — just once.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )
            Step(1, "Open the official GMG app and connect your smoker to your home WiFi (the same network as this phone).")
            Step(2, "In the GMG app, find WiFi Settings and turn Server Mode OFF. (Server Mode forces everything through GMG's cloud and blocks local control.)")
            Step(3, "Come back here and tap Search. That's it — you won't need the GMG app again.")
            Spacer(Modifier.height(8.dp))
            Text(
                "After this, GMG Control talks to your grill directly on your WiFi — no internet, no cloud, no GMG app.",
                color = Muted,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.Start) {
        Text(
            "$n",
            color = Ember,
            fontWeight = FontWeight.Black,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}
