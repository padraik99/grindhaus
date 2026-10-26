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
import androidx.compose.material3.AlertDialog
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
import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.data.Grinder
import com.padraik99.grindhaus.domain.MethodSpec
import com.padraik99.grindhaus.domain.Methods
import com.padraik99.grindhaus.domain.Taste
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.theme.GrindhausColors

/** What the log screen collects. A blank yield or water ([outG] null) means "not weighed". */
data class BrewDraft(
    val grind: Double?,
    val doseG: Double,
    val outG: Double?,
    val shotTimeS: Int?,
    val rating: Int?,
    val taste: Taste?,
    val notes: String,
)

fun BrewLog.toDraft() = BrewDraft(
    grind = grindSetting,
    doseG = doseG,
    outG = outG.takeIf { weighed },
    shotTimeS = shotTimeS,
    rating = rating,
    taste = taste,
    notes = notes,
)

/**
 * What a cleared yield falls back to when editing: the plan this brew was logged against
 * (kept in outG when it wasn't weighed), or the method's standard ratio if it was.
 */
fun BrewLog.plannedOutG(): Double = if (weighed) doseG * Methods.spec(method).ratio else outG

/** Applies an edited draft to a stored brew. Caffeine is recalculated by the repository. */
fun BrewLog.withDraft(draft: BrewDraft, plannedOutG: Double) = copy(
    grindSetting = draft.grind,
    doseG = draft.doseG,
    outG = draft.outG ?: plannedOutG,
    weighed = draft.outG != null,
    shotTimeS = draft.shotTimeS,
    rating = draft.rating,
    taste = draft.taste,
    notes = draft.notes.trim(),
)

/**
 * Records a brew, or corrects one already logged ([onDelete] set). For a new brew,
 * a shot that went to plan is just stars + taste + Save.
 */
@Composable
fun BrewLogEditor(
    title: String,
    subtitle: String,
    method: MethodSpec,
    grinder: Grinder,
    initial: BrewDraft,
    plannedOutG: Double,
    onCancel: () -> Unit,
    onSave: (BrewDraft) -> Unit,
    onDelete: (() -> Unit)? = null,
) {
    val step = grinder.step
    var grind by rememberSaveable { mutableStateOf(initial.grind) }
    var dose by rememberSaveable { mutableStateOf(formatG(initial.doseG)) }
    var out by rememberSaveable { mutableStateOf(initial.outG?.let(::formatG).orEmpty()) }
    var time by rememberSaveable { mutableStateOf(initial.shotTimeS?.toString().orEmpty()) }
    var rating by rememberSaveable { mutableStateOf(initial.rating) }
    var taste by rememberSaveable { mutableStateOf(initial.taste) }
    var notes by rememberSaveable { mutableStateOf(initial.notes) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val doseValue = dose.toDoubleOrNull()?.takeIf { it > 0 }
    val outValue = out.toDoubleOrNull()?.takeIf { it > 0 }
    val outLabel = if (method.weighsYield) "Yield (g)" else "Water (g)"

    if (confirmDelete && onDelete != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this brew?") },
            text = { Text("It comes off the dial history and out of the caffeine count.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).imePadding().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(title, color = GrindhausColors.Foam, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = GrindhausColors.Muted, fontSize = 14.sp)

        grind?.let { g ->
            SectionLabel("Grind")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedButton(
                    onClick = { grind = (g - step).coerceAtLeast(grinder.minSetting) },
                    modifier = Modifier.size(52.dp).semantics { contentDescription = "Finer" },
                ) { Text("−", fontSize = 20.sp, color = GrindhausColors.Foam) }
                Text(formatGrind(g), color = GrindhausColors.Foam, fontSize = 36.sp, fontFamily = FontFamily.Monospace)
                OutlinedButton(
                    onClick = { grind = (g + step).coerceAtMost(grinder.maxSetting) },
                    modifier = Modifier.size(52.dp).semantics { contentDescription = "Coarser" },
                ) { Text("+", fontSize = 20.sp, color = GrindhausColors.Foam) }
                grinder.rangeName(g)?.let { Text("$it range", color = GrindhausColors.Muted, fontSize = 13.sp) }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberField("Dose (g)", dose, { dose = it }, Modifier.weight(1f))
            NumberField(outLabel, out, { out = it }, Modifier.weight(1f), placeholder = formatG(plannedOutG))
            if (method.timesShot) NumberField("Time (s)", time, { time = it }, Modifier.weight(1f), decimals = false)
        }
        Text(
            if (outValue == null) {
                "Left blank, this brew is marked not weighed and caffeine is estimated from ${formatG(plannedOutG)} g. " +
                    "Grind and taste still count."
            } else if (method.weighsYield) {
                "Weigh the cup. The Bambino stops by volume, so the scale is the truth."
            } else {
                "The water you actually used."
            },
            color = GrindhausColors.Muted,
            fontSize = 12.sp,
        )

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
                    onSave(BrewDraft(grind, doseValue!!, outValue, time.toIntOrNull(), rating, taste, notes))
                },
                enabled = doseValue != null && (out.isBlank() || outValue != null),
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GrindhausColors.Crema, contentColor = GrindhausColors.Roast),
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        }
        if (onDelete != null) {
            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete this brew", color = GrindhausColors.Bitter)
            }
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
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v ->
            val cleaned = v.filter { it.isDigit() || (decimals && it == '.') }
            if (cleaned.count { it == '.' } <= 1 && cleaned.length <= 6) onChange(cleaned)
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = GrindhausColors.Muted) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number),
        modifier = modifier,
    )
}
