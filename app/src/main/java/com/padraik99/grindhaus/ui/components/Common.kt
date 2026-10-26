package com.padraik99.grindhaus.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.data.RoastLevel
import com.padraik99.grindhaus.domain.Freshness
import com.padraik99.grindhaus.domain.FreshnessTone
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BrandHeader(title: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "GRINDHAUS",
            color = GrindhausColors.Crema,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.6.sp,
        )
        Text(title, color = GrindhausColors.Foam, fontSize = 30.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), modifier = modifier, color = GrindhausColors.Muted, fontSize = 11.sp, letterSpacing = 1.5.sp)
}

fun FreshnessTone.color(): Color = when (this) {
    FreshnessTone.RESTING -> GrindhausColors.Sour
    FreshnessTone.GOOD -> GrindhausColors.Peak
    FreshnessTone.FADING -> GrindhausColors.Muted
    FreshnessTone.STALE -> GrindhausColors.Bitter
    FreshnessTone.UNOPENED -> GrindhausColors.Muted
}

/** A coloured dot plus words, so the badge never relies on colour alone. */
@Composable
fun FreshnessBadge(freshness: Freshness, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).background(freshness.tone.color(), CircleShape))
        Text(freshness.label, color = freshness.tone.color(), fontSize = 12.sp)
    }
}

/** Four segments, filled up to the roast level, with the level named beside it. */
@Composable
fun RoastBar(level: RoastLevel, modifier: Modifier = Modifier) {
    Row(
        modifier.clearAndSetSemantics { contentDescription = "${level.label} roast" },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            for (i in 1..4) {
                Box(
                    Modifier
                        .size(width = 10.dp, height = 4.dp)
                        .background(
                            if (i <= level.step) GrindhausColors.FoamDim else GrindhausColors.Outline,
                            RoundedCornerShape(2.dp),
                        ),
                )
            }
        }
        Text(level.label, color = GrindhausColors.FoamDim, fontSize = 12.sp)
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier
            .background(GrindhausColors.SurfaceHigh, RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 2.dp),
        color = GrindhausColors.FoamDim,
        fontSize = 11.sp,
    )
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.US)

fun Long.asEpochDayText(): String = LocalDate.ofEpochDay(this).format(DATE_FORMAT)
