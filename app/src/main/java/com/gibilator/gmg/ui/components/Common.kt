package com.gibilator.gmg.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.gibilator.gmg.data.GmgPrefs
import com.gibilator.gmg.ui.theme.Bone
import com.gibilator.gmg.units.Units
import java.util.Locale

/** Resolve the user's display temp unit ("C"/"F") for the UI. */
fun GmgPrefs?.tempUnit(): String {
    val metric = Locale.getDefault().country !in setOf("US", "LR", "MM")
    return Units.resolveTempUnit(this?.tempUnitPref ?: Units.TEMP_UNIT_AUTO, metric)
}

fun GmgPrefs?.weightUnit(): String {
    val metric = Locale.getDefault().country !in setOf("US", "LR", "MM")
    return Units.resolveWeightUnit(this?.weightUnitPref ?: Units.WEIGHT_UNIT_AUTO, metric)
}

/** Format a canonical °F value into the user's chosen unit string. */
fun fmtTempF(valueF: Int?, unit: String): String = Units.fmtTemp(valueF?.toDouble(), unit)

/** A rolling, animated temperature readout with a caption underneath. */
@Composable
fun AnimatedTempReadout(
    valueF: Int?,
    label: String,
    color: Color,
    unit: String,
    modifier: Modifier = Modifier,
    valueSize: Int = 22,
) {
    val animated by animateIntAsState(
        targetValue = valueF ?: 0,
        animationSpec = tween(600),
        label = "temp",
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = modifier) {
        Text(
            text = if (valueF == null) "—" else fmtTempF(animated, unit),
            color = color,
            fontWeight = FontWeight.Black,
            fontSize = valueSize.sp,
        )
        Text(
            text = label.uppercase(),
            color = color.copy(alpha = 0.92f),
            fontWeight = FontWeight.SemiBold,
            fontSize = (valueSize * 0.45f).sp,
            letterSpacing = 0.5.sp,
        )
    }
}

/** A titled card used to group content. */
@Composable
fun SectionCard(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp)) {
            if (title != null) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
            }
            content()
        }
    }
}

/** A −/value/+ stepper for setpoints. */
@Composable
fun Stepper(
    value: Int,
    onChange: (Int) -> Unit,
    step: Int,
    min: Int,
    max: Int,
    unit: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FilledTonalIconButton(
            onClick = { onChange((value - step).coerceIn(min, max)) },
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Lower") }
        Text(
            "$value$unit",
            style = MaterialTheme.typography.titleLarge,
            color = Bone,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        FilledTonalIconButton(
            onClick = { onChange((value + step).coerceIn(min, max)) },
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
        ) { Icon(Icons.Rounded.Add, contentDescription = "Raise") }
    }
}
