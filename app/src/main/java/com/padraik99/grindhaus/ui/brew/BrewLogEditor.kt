package com.padraik99.grindhaus.ui.brew

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.domain.Taste
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.theme.GrindhausColors

/**
 * Records a brew as it actually happened. Starts from the Brew Bar's settings so a
 * shot that went to plan is just rating + taste + Save.
 */
@Composable
fun BrewLogEditor(
    spec: BrewSpec,
    onCancel: () -> Unit,
    onSave: (grind: Double?, doseG: Double, outG: Double, shotTimeS: Int?, rating: Int?, taste: Taste?, notes: String) -> Unit,
) {
    val step = spec.grinder.step
    var grind by rememberSaveable { mutableStateOf(spec.grind) }
    var dose by rememberSaveable { mutableStateOf(formatG(spec.doseG)) }
    var out by rememberSaveable { mutableStateOf(formatG(spec.outG)) }
    var time by rememberSaveable { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf<Int?>(null) }
    var taste by rememberSaveable { mutableStateOf<Taste?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }

    val doseValue = dose.toDoubleOrNull()?.takeIf { it > 0 }
    val outValue = out.toDoubleOrNull()?.takeIf { it > 0 }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Log brew", color = GrindhausColors.Foam, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "${spec.method.label} · ${spec.filter.label} · ${spec.bean.title}",
            color = GrindhausColors.Muted,
            fontSize = 14.sp,
        )

        grind?.let { g ->
            SectionLabel("Grind")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { grind = (g - step).coerceAtLeast(spec.grinder.minSetting) },
                    modifier = Modifier.size(52.dp).semantics { contentDescription = "Finer" },
                ) { Text("−", fontSize = 20.sp, color = GrindhausColors.Foam) }
                Text(formatGrind(g), color = GrindhausColors.Foam, fontSize = 36.sp, fontFamily = FontFamily.Monospace)
                OutlinedButton(
                    onClick = { grind = (g + step).coerceAtMost(spec.grinder.maxSetting) },
                    modifier = Modifier.size(52.dp).semantics { contentDescription = "Coarser" },
                ) { Text("+", fontSize = 20.sp, color = GrindhausColors.Foam) }
                spec.grinder.rangeName(g)?.let { Text("$it range", color = GrindhausColors.Muted, fontSize = 13.sp) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Dose (g)", dose, { dose = it }, Modifier.weight(1f))
            NumberField(if (spec.method.weighsYield) "Yield (g)" else "Water (g)", out, { out = it }, Modifier.weight(1f))
            if (spec.method.timesShot) NumberField("Time (s)", time, { time = it }, Modifier.weight(1f), decimals = false)
        }
        if (spec.method.weighsYield) {
            Text(
                "Weigh the cup. The Bambino stops by volume, so the scale is the truth.",
                color = GrindhausColors.Muted,
                fontSize = 12.sp,
            )
        }

        SectionLabel("How was it?")
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..5).forEach { n ->
                val on = (rating ?: 0) >= n
                TextButton(
                    onClick = { rating = if (rating == n) null else n },
                    modifier = Modifier.size(48.dp).semantics { contentDescription = "Rate $n of 5" },
                ) { Text(if (on) "★" else "☆", color = GrindhausColors.Crema, fontSize = 26.sp) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Taste.entries.forEach { t ->
                FilterChip(
                    selected = taste == t,
                    onClick = { taste = if (taste == t) null else t },
                    label = { Text(t.label) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = t.color(),
                        selectedLabelColor = GrindhausColors.Roast,
                        labelColor = GrindhausColors.FoamDim,
                    ),
                )
            }
        }
        if (taste == Taste.SOUR || taste == Taste.BITTER) {
            Text(
                if (taste == Taste.SOUR) "Sour usually means under-extracted: next time, grind finer."
                else "Bitter usually means over-extracted: next time, grind coarser.",
                color = GrindhausColors.Muted,
                fontSize = 12.sp,
            )
        }

        OutlinedTextField(
            value = notes,
            onValueChange = { notes = it },
            label = { Text("Notes on this brew") },
            minLines = 2,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )

        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Cancel", color = GrindhausColors.FoamDim)
            }
            Button(
                onClick = {
                    onSave(grind, doseValue!!, outValue!!, time.toIntOrNull(), rating, taste, notes)
                },
                enabled = doseValue != null && outValue != null,
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GrindhausColors.Crema, contentColor = GrindhausColors.Roast),
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    decimals: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            val cleaned = v.filter { it.isDigit() || (decimals && it == '.') }
            if (cleaned.count { it == '.' } <= 1 && cleaned.length <= 6) onChange(cleaned)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
