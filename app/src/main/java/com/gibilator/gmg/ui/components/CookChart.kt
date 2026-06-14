package com.gibilator.gmg.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gibilator.gmg.data.Sample
import com.gibilator.gmg.ui.theme.Bone
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.Muted
import com.gibilator.gmg.ui.theme.ProbeBlue
import com.gibilator.gmg.ui.theme.ProbeBlueDim
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/**
 * Native cook chart — food actual/expected + grill over a time window. Tap any
 * point to pin a marker and read that node's time + temperatures below.
 */
@Composable
fun CookChart(samples: List<Sample>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        if (samples.size < 2) {
            Text("Waiting for cook data…", color = Muted, style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        val tMin = samples.first().tMs.toFloat()
        val tMax = samples.last().tMs.toFloat()
        val span = (tMax - tMin).coerceAtLeast(1f)

        var selected by remember { mutableStateOf<Int?>(null) }
        val selIdx = selected?.coerceIn(0, samples.lastIndex)

        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(samples.size) {
                    detectTapGestures { offset ->
                        val frac = (offset.x / size.width).coerceIn(0f, 1f)
                        val t = tMin + frac * span
                        selected = samples.indices.minByOrNull { abs(samples[it].tMs - t) }
                    }
                },
        ) {
            var yMin = Float.MAX_VALUE
            var yMax = Float.MIN_VALUE
            for (s in samples) {
                listOfNotNull(s.grillF.toFloat(), s.probeF?.toFloat(), s.expectedF?.toFloat()).forEach {
                    if (it < yMin) yMin = it
                    if (it > yMax) yMax = it
                }
            }
            if (yMin == Float.MAX_VALUE) return@Canvas
            if (yMin == yMax) { yMin -= 1; yMax += 1 }
            val padY = (yMax - yMin) * 0.10f
            yMin -= padY; yMax += padY

            fun x(t: Float) = (t - tMin) / span * size.width
            fun y(v: Float) = size.height - (v - yMin) / (yMax - yMin) * size.height

            for (i in 0..3) {
                val gy = size.height * i / 3f
                drawLine(Muted.copy(alpha = 0.15f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }

            drawSeries(samples, ::x, ::y, { it.grillF.toFloat() }, Ember.copy(alpha = 0.7f), 3f, false)
            drawSeries(samples, ::x, ::y, { it.expectedF?.toFloat() }, ProbeBlueDim, 3f, dashed = true)
            drawSeries(samples, ::x, ::y, { it.probeF?.toFloat() }, ProbeBlue, 5f, false)

            // Selected node marker.
            selIdx?.let { idx ->
                val s = samples[idx]
                val px = x(s.tMs.toFloat())
                drawLine(Bone.copy(alpha = 0.5f), Offset(px, 0f), Offset(px, size.height), strokeWidth = 2f)
                drawCircle(Ember, 6f, Offset(px, y(s.grillF.toFloat())))
                s.probeF?.let { drawCircle(ProbeBlue, 6f, Offset(px, y(it.toFloat()))) }
                s.expectedF?.let { drawCircle(ProbeBlueDim, 5f, Offset(px, y(it.toFloat()))) }
            }
        }

        // Detail readout for the tapped node, or a hint.
        val detail = selIdx?.let { idx ->
            val s = samples[idx]
            val time = SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(s.tMs))
            buildString {
                append("$time  ·  Grill ${s.grillF}°")
                s.probeF?.let { append("  ·  Food ${it}°") }
                s.expectedF?.let { append("  ·  Plan ${it.toInt()}°") }
            }
        } ?: "Tap the chart to read any point."
        Text(
            detail,
            color = if (selIdx != null) Bone else Muted,
            fontWeight = if (selIdx != null) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 8.dp),
        )

        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            LegendDot(ProbeBlue, "Food")
            LegendDot(ProbeBlueDim, "Plan")
            LegendDot(Ember, "Grill")
        }
    }
}

private inline fun DrawScope.drawSeries(
    samples: List<Sample>,
    x: (Float) -> Float,
    y: (Float) -> Float,
    value: (Sample) -> Float?,
    color: Color,
    width: Float,
    dashed: Boolean,
) {
    val path = Path()
    var started = false
    for (s in samples) {
        val v = value(s) ?: continue
        val px = x(s.tMs.toFloat())
        val py = y(v)
        if (!started) { path.moveTo(px, py); started = true } else path.lineTo(px, py)
    }
    if (!started) return
    drawPath(
        path,
        color = color,
        style = Stroke(
            width = width,
            cap = StrokeCap.Round,
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 10f)) else null,
        ),
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(10.dp)) { drawCircle(color) }
        Text("  $label", color = Muted, fontSize = 12.sp)
    }
}
