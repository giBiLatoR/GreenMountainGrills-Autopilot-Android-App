package com.gibilator.gmg.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.gibilator.gmg.ui.theme.Amber
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.GoodGreen
import com.gibilator.gmg.ui.theme.ProbeBlue
import kotlin.math.sin
import kotlin.random.Random

/** Looping celebratory confetti — used while the cook is in "it's ready". */
@Composable
fun Confetti(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "confetti")
    val t by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Restart), label = "fall",
    )
    val colors = listOf(Ember, Amber, ProbeBlue, GoodGreen, Color(0xFFFF8A65))
    val pieces = remember {
        List(40) {
            Piece(
                x = Random.nextFloat(),
                delay = Random.nextFloat(),
                size = Random.nextFloat() * 8f + 4f,
                sway = Random.nextFloat() * 0.06f + 0.02f,
                color = it % 5,
            )
        }
    }
    Canvas(modifier) {
        for (p in pieces) {
            val phase = (t + p.delay) % 1f
            val y = phase * size.height
            val x = p.x * size.width + sin(phase * 12f) * size.width * p.sway
            drawRect(
                color = colors[p.color].copy(alpha = (1f - phase).coerceIn(0f, 1f)),
                topLeft = Offset(x, y),
                size = Size(p.size, p.size * 1.6f),
            )
        }
    }
}

private data class Piece(val x: Float, val delay: Float, val size: Float, val sway: Float, val color: Int)

/** Expanding radar rings — used while scanning for grills. */
@Composable
fun RadarScan(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "radar")
    val t by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800), RepeatMode.Restart), label = "ping",
    )
    Canvas(modifier) {
        val center = Offset(size.width / 2, size.height / 2)
        val maxR = size.minDimension / 2
        for (i in 0..2) {
            val phase = (t + i / 3f) % 1f
            drawCircle(
                color = Ember.copy(alpha = (1f - phase) * 0.5f),
                radius = maxR * phase,
                center = center,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f),
            )
        }
        drawCircle(Ember, radius = maxR * 0.10f, center = center)
    }
}
