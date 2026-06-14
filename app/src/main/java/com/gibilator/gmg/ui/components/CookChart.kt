package com.gibilator.gmg.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gibilator.gmg.data.Sample
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.Muted
import com.gibilator.gmg.ui.theme.ProbeBlue
import com.gibilator.gmg.ui.theme.ProbeBlueDim

/** Native cook chart — food actual/expected + grill over a time window. */
@Composable
fun CookChart(samples: List<Sample>, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (samples.size < 2) return@Canvas
            val tMin = samples.first().tMs.toFloat()
            val tMax = samples.last().tMs.toFloat()
            val span = (tMax - tMin).coerceAtLeast(1f)

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

            // gridlines
            for (i in 0..3) {
                val gy = size.height * i / 3f
                drawLine(Muted.copy(alpha = 0.15f), Offset(0f, gy), Offset(size.width, gy), strokeWidth = 1f)
            }

            drawSeries(samples, ::x, ::y, { it.grillF.toFloat() }, Ember.copy(alpha = 0.7f), 3f, false)
            drawSeries(samples, ::x, ::y, { it.expectedF?.toFloat() }, ProbeBlueDim, 3f, dashed = true)
            drawSeries(samples, ::x, ::y, { it.probeF?.toFloat() }, ProbeBlue, 5f, false)
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
