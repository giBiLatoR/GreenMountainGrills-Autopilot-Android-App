package com.gibilator.gmg.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import com.gibilator.gmg.ui.theme.Bone
import com.gibilator.gmg.ui.theme.Ember
import com.gibilator.gmg.ui.theme.Muted
import com.gibilator.gmg.ui.theme.ProbeBlue

/**
 * Big food-progress ring: sweep from start temp toward the pull target.
 * Center shows the headline ([centerTop]) and a sub-line ([centerBottom]).
 */
@Composable
fun ProgressRing(
    fraction: Float,
    centerTop: String,
    centerBottom: String,
    modifier: Modifier = Modifier,
) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(800),
        label = "ring",
    )
    Box(modifier.size(208.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(208.dp)) {
            val stroke = 20.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = Muted.copy(alpha = 0.20f),
                startAngle = 130f,
                sweepAngle = 280f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            drawArc(
                brush = Brush.sweepGradient(listOf(ProbeBlue, Ember, Ember)),
                startAngle = 130f,
                sweepAngle = 280f * animated,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(centerTop, color = Bone, fontWeight = FontWeight.Black, fontSize = 30.sp)
            Text(centerBottom, color = Muted, fontSize = 13.sp)
        }
    }
}
