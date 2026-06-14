package com.gibilator.gmg.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Visual category for procedural meat art. */
enum class MeatCategory { BRISKET, PORK_BUTT, ROAST, RIBS, POULTRY_WHOLE, POULTRY_DRUM, POULTRY_BREAST, CHOP, FISH, SAUSAGE }

fun categoryFor(meatKey: String): MeatCategory = when (meatKey) {
    "beef_brisket_packer", "beef_brisket_flat" -> MeatCategory.BRISKET
    "pork_butt_pulled", "pork_butt_sliced", "lamb_shoulder" -> MeatCategory.PORK_BUTT
    "beef_chuck_roast", "beef_tri_tip", "beef_prime_rib", "pork_loin", "lamb_leg" -> MeatCategory.ROAST
    "beef_ribs_dino", "baby_back_ribs", "spare_ribs_stlouis" -> MeatCategory.RIBS
    "whole_turkey", "whole_chicken" -> MeatCategory.POULTRY_WHOLE
    "turkey_breast", "chicken_breast" -> MeatCategory.POULTRY_BREAST
    "chicken_thighs_legs" -> MeatCategory.POULTRY_DRUM
    "pork_chops" -> MeatCategory.CHOP
    "salmon_fillet" -> MeatCategory.FISH
    "sausage_brats" -> MeatCategory.SAUSAGE
    else -> MeatCategory.ROAST
}

private val bark = Color(0xFF3E2317)
private val meatBrown = Color(0xFF6B3A24)
private val pink = Color(0xFFC76B6B)
private val fat = Color(0xFFE7D7B8)
private val golden = Color(0xFFC8862E)
private val goldenLight = Color(0xFFE3A94B)
private val salmon = Color(0xFFE8896B)
private val salmonStripe = Color(0xFFF3C3AE)
private val sausage = Color(0xFF8A4B2E)

/** A flat, recognizable illustration for a meat, drawn procedurally. */
@androidx.compose.runtime.Composable
fun MeatArt(meatKey: String, modifier: Modifier = Modifier) {
    Box(modifier.clip(RoundedCornerShape(16.dp))) {
        Canvas(Modifier.size(64.dp)) {
            when (categoryFor(meatKey)) {
                MeatCategory.BRISKET -> drawBrisket()
                MeatCategory.PORK_BUTT -> drawPorkButt()
                MeatCategory.ROAST -> drawRoast()
                MeatCategory.RIBS -> drawRibs()
                MeatCategory.POULTRY_WHOLE -> drawPoultryWhole()
                MeatCategory.POULTRY_DRUM -> drawDrumstick()
                MeatCategory.POULTRY_BREAST -> drawBreast()
                MeatCategory.CHOP -> drawChop()
                MeatCategory.FISH -> drawFish()
                MeatCategory.SAUSAGE -> drawSausage()
            }
        }
    }
}

private fun DrawScope.drawBrisket() {
    val w = size.width; val h = size.height
    val r = androidx.compose.ui.geometry.CornerRadius(h * 0.28f, h * 0.28f)
    drawRoundRect(bark, Offset(w * 0.10f, h * 0.30f), Size(w * 0.80f, h * 0.42f), r)
    drawRoundRect(fat, Offset(w * 0.10f, h * 0.30f), Size(w * 0.80f, h * 0.12f), r)
    // smoke ring along bottom
    drawRoundRect(pink, Offset(w * 0.12f, h * 0.62f), Size(w * 0.76f, h * 0.08f), r)
    drawCircle(Color(0x33000000), h * 0.02f, Offset(w * 0.3f, h * 0.5f))
    drawCircle(Color(0x33000000), h * 0.02f, Offset(w * 0.55f, h * 0.55f))
}

private fun DrawScope.drawPorkButt() {
    val w = size.width; val h = size.height
    drawCircle(meatBrown, w * 0.30f, Offset(w * 0.5f, h * 0.52f))
    drawCircle(bark.copy(alpha = 0.5f), w * 0.30f, Offset(w * 0.5f, h * 0.52f), style = Stroke(width = w * 0.05f))
    // shred lines
    for (i in 0..3) {
        drawLine(bark, Offset(w * (0.35f + i * 0.09f), h * 0.4f), Offset(w * (0.35f + i * 0.09f), h * 0.64f), strokeWidth = w * 0.015f)
    }
}

private fun DrawScope.drawRoast() {
    val w = size.width; val h = size.height
    drawOval(meatBrown, Offset(w * 0.16f, h * 0.30f), Size(w * 0.68f, h * 0.42f))
    drawOval(pink, Offset(w * 0.30f, h * 0.40f), Size(w * 0.40f, h * 0.22f))
    drawArc(bark, 0f, 360f, false, Offset(w * 0.16f, h * 0.30f), Size(w * 0.68f, h * 0.42f), style = Stroke(width = w * 0.05f))
}

private fun DrawScope.drawRibs() {
    val w = size.width; val h = size.height
    drawRoundRect(meatBrown, Offset(w * 0.12f, h * 0.28f), Size(w * 0.76f, h * 0.44f), androidx.compose.ui.geometry.CornerRadius(h * 0.1f, h * 0.1f))
    for (i in 0..4) {
        val x = w * (0.2f + i * 0.15f)
        drawLine(fat, Offset(x, h * 0.30f), Offset(x, h * 0.70f), strokeWidth = w * 0.03f)
        drawCircle(fat, w * 0.03f, Offset(x, h * 0.70f))
    }
}

private fun DrawScope.drawPoultryWhole() {
    val w = size.width; val h = size.height
    drawOval(golden, Offset(w * 0.22f, h * 0.34f), Size(w * 0.56f, h * 0.40f))
    drawOval(goldenLight, Offset(w * 0.30f, h * 0.30f), Size(w * 0.40f, h * 0.22f))
    // drumstick nubs
    drawCircle(golden, w * 0.08f, Offset(w * 0.74f, h * 0.44f))
    drawCircle(golden, w * 0.08f, Offset(w * 0.26f, h * 0.44f))
}

private fun DrawScope.drawDrumstick() {
    val w = size.width; val h = size.height
    drawCircle(golden, w * 0.20f, Offset(w * 0.40f, h * 0.42f))
    val p = Path().apply {
        moveTo(w * 0.5f, h * 0.5f)
        lineTo(w * 0.78f, h * 0.74f)
        lineTo(w * 0.70f, h * 0.80f)
        lineTo(w * 0.42f, h * 0.56f)
        close()
    }
    drawPath(p, goldenLight)
    drawCircle(fat, w * 0.05f, Offset(w * 0.80f, h * 0.78f))
}

private fun DrawScope.drawBreast() {
    val w = size.width; val h = size.height
    drawOval(goldenLight, Offset(w * 0.24f, h * 0.32f), Size(w * 0.52f, h * 0.40f))
    drawArc(golden, 0f, 360f, false, Offset(w * 0.24f, h * 0.32f), Size(w * 0.52f, h * 0.40f), style = Stroke(width = w * 0.04f))
}

private fun DrawScope.drawChop() {
    val w = size.width; val h = size.height
    drawOval(pink, Offset(w * 0.24f, h * 0.30f), Size(w * 0.52f, h * 0.44f))
    drawArc(meatBrown, 0f, 360f, false, Offset(w * 0.24f, h * 0.30f), Size(w * 0.52f, h * 0.44f), style = Stroke(width = w * 0.07f))
    drawCircle(fat, w * 0.06f, Offset(w * 0.5f, h * 0.72f))
}

private fun DrawScope.drawFish() {
    val w = size.width; val h = size.height
    val p = Path().apply {
        moveTo(w * 0.14f, h * 0.40f)
        lineTo(w * 0.82f, h * 0.32f)
        lineTo(w * 0.86f, h * 0.62f)
        lineTo(w * 0.18f, h * 0.66f)
        close()
    }
    drawPath(p, salmon)
    for (i in 0..2) {
        drawLine(salmonStripe, Offset(w * 0.22f, h * (0.46f + i * 0.06f)), Offset(w * 0.80f, h * (0.40f + i * 0.06f)), strokeWidth = w * 0.02f)
    }
}

private fun DrawScope.drawSausage() {
    val w = size.width; val h = size.height
    for (i in 0..1) {
        val y = h * (0.40f + i * 0.18f)
        drawRoundRect(sausage, Offset(w * 0.18f, y), Size(w * 0.64f, h * 0.12f), androidx.compose.ui.geometry.CornerRadius(h * 0.06f, h * 0.06f))
        drawLine(bark, Offset(w * 0.32f, y), Offset(w * 0.32f, y + h * 0.12f), strokeWidth = w * 0.015f)
        drawLine(bark, Offset(w * 0.66f, y), Offset(w * 0.66f, y + h * 0.12f), strokeWidth = w * 0.015f)
    }
}
