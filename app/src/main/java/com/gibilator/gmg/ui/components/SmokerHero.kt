package com.gibilator.gmg.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Power
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gibilator.gmg.R
import com.gibilator.gmg.protocol.GmgSnapshot
import com.gibilator.gmg.protocol.WarnCode
import com.gibilator.gmg.ui.theme.Amber
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.Muted
import com.gibilator.gmg.ui.theme.ProbeBlue
import com.gibilator.gmg.ui.theme.WarnRed
import kotlin.math.sin

private fun modelDrawable(modelId: Int?): Int = when (modelId) {
    0 -> R.drawable.model_0; 1 -> R.drawable.model_1; 2 -> R.drawable.model_2; 3 -> R.drawable.model_3
    4 -> R.drawable.model_4; 5 -> R.drawable.model_5; 6 -> R.drawable.model_6; 7 -> R.drawable.model_7
    8 -> R.drawable.model_8; 9 -> R.drawable.model_9; 10 -> R.drawable.model_10; 11 -> R.drawable.model_11
    12 -> R.drawable.model_12; 13 -> R.drawable.model_13; 14 -> R.drawable.model_14; 15 -> R.drawable.model_15
    else -> R.drawable.smoker_generic
}

private fun warnMessage(code: WarnCode): String = when (code) {
    WarnCode.LOW_PELLET -> "Running low on pellets"
    WarnCode.FAN_OVERLOAD -> "Fan overload"
    WarnCode.AUGER_OVERLOAD -> "Auger overload"
    WarnCode.IGNITOR_OVERLOAD -> "Ignitor overload"
    WarnCode.LOW_VOLTAGE -> "Low voltage"
    WarnCode.FAN_DISCONNECT -> "Fan disconnected"
    WarnCode.AUGER_DISCONNECT -> "Auger disconnected"
    WarnCode.IGNITOR_DISCONNECT -> "Ignitor disconnected"
    WarnCode.NONE -> ""
}

@Composable
private fun AtFraction(fx: Float, fy: Float, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = BiasAlignment(2 * fx - 1, 2 * fy - 1)) { content() }
}

/**
 * The smoker hero — model image + live overlays + procedural smoke/flame + glow.
 * Geometry mirrors the HA "GMG Smoker" card (gmg-smoker-strategy.js).
 */
@Composable
fun SmokerHero(
    snapshot: GmgSnapshot,
    modelId: Int?,
    tempUnit: String,
    phaseHeadline: String,
    onPowerTap: () -> Unit,
) {
    val heating = snapshot.flameOn
    val transition = rememberInfiniteTransition(label = "hero")
    val glow by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "glow",
    )
    val t by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600), RepeatMode.Restart),
        label = "smoke",
    )

    Box(
        Modifier
            .fillMaxWidth()
            .height(280.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Procedural smoke + flame behind/around the smoker when heating.
        if (heating) {
            Canvas(Modifier.fillMaxSize()) { drawSmokeAndFlame(t, glow) }
        }

        Image(
            painter = painterResource(modelDrawable(modelId)),
            contentDescription = "Smoker",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .alpha(if (heating) 1f else 0.92f),
        )

        // Phase headline.
        AtFraction(0.50f, 0.10f) {
            Text(
                phaseHeadline,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }

        // Grill + food readouts (two-line).
        AtFraction(0.36f, 0.66f) {
            AnimatedTempReadout(snapshot.grillTemp, "Grill", Ember, tempUnit, valueSize = 18)
        }
        val foodProbe = snapshot.probe1Temp
        AtFraction(0.68f, 0.66f) {
            AnimatedTempReadout(foodProbe, "Food", ProbeBlue, tempUnit, valueSize = 18)
        }

        // Power button.
        AtFraction(0.12f, 0.62f) {
            Surface(
                shape = CircleShape,
                color = if (snapshot.powerState.raw != 0) Ember else Color(0xFF555048),
                onClick = onPowerTap,
            ) {
                Icon(
                    Icons.Rounded.Power,
                    contentDescription = if (snapshot.powerState.raw != 0) "Shut down" else "Light the grill",
                    tint = Color.White,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }

        // Hopper pellets.
        AtFraction(0.14f, 0.92f) {
            Text("Pellets ${snapshot.hopperPct}%", color = Muted, fontSize = 12.sp)
        }

        // Warning chip.
        if (snapshot.warnCode != WarnCode.NONE) {
            AtFraction(0.55f, 0.92f) {
                Surface(shape = CircleShape, color = WarnRed.copy(alpha = 0.18f)) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Icon(Icons.Rounded.Warning, null, tint = WarnRed, modifier = Modifier.padding(end = 4.dp))
                        Text(warnMessage(snapshot.warnCode), color = WarnRed, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSmokeAndFlame(t: Float, glow: Float) {
    val w = size.width
    val h = size.height
    // Flame glow near the firebox (left chimney area).
    drawCircle(
        Ember.copy(alpha = 0.18f * glow + 0.05f),
        radius = w * 0.18f,
        center = Offset(w * 0.30f, h * 0.55f),
    )
    drawCircle(
        Amber.copy(alpha = 0.14f * glow),
        radius = w * 0.10f,
        center = Offset(w * 0.30f, h * 0.55f),
    )
    // Rising smoke puffs from the stack (upper-right).
    val baseX = w * 0.78f
    for (i in 0..4) {
        val phase = (t + i * 0.2f) % 1f
        val y = h * 0.40f - phase * h * 0.38f
        val drift = sin((phase + i) * 6.28f) * w * 0.03f
        val alpha = (1f - phase) * 0.22f
        drawCircle(
            Color(0xFFBFB4AC).copy(alpha = alpha),
            radius = w * (0.03f + phase * 0.05f),
            center = Offset(baseX + drift, y),
        )
    }
}
