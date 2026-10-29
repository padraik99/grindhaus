package com.padraik99.grindhaus.ui.brew

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.Taste
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToLong

/** What a brew form collects. A blank yield ([outG] null) means "not weighed". */
data class BrewDraft(
    val grind: Double?,
    val doseG: Double,
    val outG: Double?,
    /** The yield or water aimed for; stands in for caffeine when [outG] is blank. */
    val plannedOutG: Double?,
    val shotTimeS: Int?,
    val rating: Int?,
    val taste: Taste?,
    val notes: String,
)

/** Handwritten labels under the method stickers (Caveat, SIL Open Font License). */
val Handwriting = FontFamily(Font(R.font.caveat_semibold, FontWeight.SemiBold))

/** The sticker drawn for each method; methods without one show their name only. */
@DrawableRes
fun BrewMethod.sticker(): Int? = when (this) {
    BrewMethod.ESPRESSO -> R.drawable.sticker_espresso
    BrewMethod.AEROPRESS -> R.drawable.sticker_aeropress
    BrewMethod.MOKA -> R.drawable.sticker_moka
    BrewMethod.FREDDO -> R.drawable.sticker_freddo
    BrewMethod.POUR_OVER, BrewMethod.FRENCH_PRESS -> null
}

/** Short name for the sticker tiles. */
fun BrewMethod.tileLabel(): String = when (this) {
    BrewMethod.FREDDO -> "Freddo"
    else -> com.padraik99.grindhaus.domain.Methods.spec(this).label
}

@Composable
fun Stars(rating: Int, modifier: Modifier = Modifier) {
    Text(
        "★".repeat(rating) + "☆".repeat(5 - rating),
        color = GrindhausColors.Crema,
        fontSize = 13.sp,
        modifier = modifier.clearAndSetSemantics { contentDescription = "Rated $rating of 5" },
    )
}

@Composable
fun TasteTag(taste: Taste) {
    Text(
        taste.label,
        color = GrindhausColors.Roast,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.background(taste.color(), RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 2.dp),
    )
}

fun Taste.color() = when (this) {
    Taste.SOUR -> GrindhausColors.Sour
    Taste.BALANCED -> GrindhausColors.Balanced
    Taste.BITTER -> GrindhausColors.Bitter
}

fun formatGrind(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
fun formatG(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
fun formatRatio(v: Double): String =
    if (abs(v - v.roundToLong()) < 0.05) v.roundToLong().toString() else String.format(Locale.US, "%.1f", v)
