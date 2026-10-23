package dev.edgecode.core.design

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import dev.edgecode.R

/**
 * The app's single typeface.
 *
 * One variable font covers every weight the interface uses, so the APK carries
 * one ~286 KB asset instead of four static files, and the platform resolves a
 * single font resource as weights are requested. Declared at the top level: a
 * [FontFamily] is resolved and cached once, never per composition.
 */
val FiraCode: FontFamily = FontFamily(
    firaCode(FontWeight.Light, 300),
    firaCode(FontWeight.Normal, 400),
    firaCode(FontWeight.Medium, 500),
    firaCode(FontWeight.SemiBold, 600),
    firaCode(FontWeight.Bold, 700),
)

private fun firaCode(weight: FontWeight, axis: Int): Font = Font(
    resId = R.font.fira_code_vf,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(axis)),
)

/**
 * Fira Code draws its programming ligatures with the `calt` contextual feature;
 * `liga` covers the standard set. Nothing else is enabled — stylistic sets and
 * discretionary ligatures only add shaping work.
 */
const val LIGATURE_FEATURES = "\"calt\" 1, \"liga\" 1"
