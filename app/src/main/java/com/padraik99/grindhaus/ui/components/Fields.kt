package com.padraik99.grindhaus.ui.components

import androidx.compose.foundation.background
import java.util.Locale
import java.time.LocalTime
import androidx.compose.ui.Alignment
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.ui.theme.GrindhausColors

/** A card: the lighter panel colour with a slightly darker edge, so sections stand apart. */
fun Modifier.panel(radius: Dp = 20.dp, padding: Dp = 18.dp): Modifier = this
    .background(GrindhausColors.Surface, RoundedCornerShape(radius))
    .border(1.dp, GrindhausColors.Edge, RoundedCornerShape(radius))
    .padding(padding)

/**
 * A number field that selects its whole contents when tapped, so typing replaces the old
 * number instead of adding to it. Only digits (and one decimal point when [decimals]).
 */
@Composable
fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    decimals: Boolean = true,
    placeholder: String? = null,
    suffix: String? = null,
) {
    var field by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    // Follow changes made from outside (a timer filling Time, a coffee switch).
    LaunchedEffect(value) {
        if (field.text != value) field = TextFieldValue(value, TextRange(value.length))
    }
    // The tap that focuses the field also moves the cursor afterwards; keep the selection.
    var selectOnNextMove by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = field,
        onValueChange = { next ->
            if (selectOnNextMove && next.text == field.text) {
                field = next.copy(selection = TextRange(0, next.text.length))
                selectOnNextMove = false
                return@OutlinedTextField
            }
            selectOnNextMove = false
            val cleaned = next.text.filter { it.isDigit() || (decimals && it == '.') }
            if (cleaned.count { it == '.' } <= 1 && cleaned.length <= 6) {
                field = if (cleaned == next.text) next else TextFieldValue(cleaned, TextRange(cleaned.length))
                if (cleaned != value) onValueChange(cleaned)
            }
        },
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = GrindhausColors.Muted) } },
        suffix = suffix?.let { { Text(it, color = GrindhausColors.Muted) } },
        singleLine = true,
        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (decimals) KeyboardType.Decimal else KeyboardType.Number,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier.onFocusChanged { state ->
            if (state.isFocused) {
                field = field.copy(selection = TextRange(0, field.text.length))
                selectOnNextMove = true
            } else {
                selectOnNextMove = false
            }
        },
    )
}

/** One choice in a row of choices: light when picked, outlined when not. */
@Composable
fun Choice(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text, fontSize = 14.sp) },
        modifier = modifier,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = GrindhausColors.Foam,
            selectedLabelColor = GrindhausColors.Roast,
            labelColor = GrindhausColors.FoamDim,
        ),
    )
}

/**
 * A time of day, compact: hour and minute fields (tap selects, type replaces) and am / pm.
 * [onChange] fires only with a valid time; a half-typed hour is left alone until it is one.
 */
@Composable
fun TimeOfDayField(time: LocalTime, onChange: (LocalTime) -> Unit, modifier: Modifier = Modifier) {
    val pm = time.hour >= 12
    var hourText by rememberSaveable { mutableStateOf(time.toHour12().toString()) }
    var minuteText by rememberSaveable { mutableStateOf(String.format(Locale.US, "%02d", time.minute)) }
    fun emit(hour12: Int?, minute: Int?, isPm: Boolean) {
        if (hour12 == null || hour12 !in 1..12 || minute == null || minute !in 0..59) return
        onChange(LocalTime.of((hour12 % 12) + if (isPm) 12 else 0, minute))
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            hourText,
            { hourText = it; emit(it.toIntOrNull(), minuteText.toIntOrNull(), pm) },
            "Hour",
            Modifier.width(88.dp),
            decimals = false,
        )
        Text(":", color = GrindhausColors.FoamDim, fontSize = 22.sp)
        NumberField(
            minuteText,
            { minuteText = it; emit(hourText.toIntOrNull(), it.toIntOrNull(), pm) },
            "Min",
            Modifier.width(88.dp),
            decimals = false,
        )
        Choice("am", !pm) { emit(hourText.toIntOrNull(), minuteText.toIntOrNull(), false) }
        Choice("pm", pm) { emit(hourText.toIntOrNull(), minuteText.toIntOrNull(), true) }
    }
}

private fun LocalTime.toHour12(): Int = (hour % 12).let { if (it == 0) 12 else it }
