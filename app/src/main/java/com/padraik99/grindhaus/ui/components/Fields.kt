package com.padraik99.grindhaus.ui.components

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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

/** The time picker in the app's colours rather than Android's default purple. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun grindhausTimeColors() = TimePickerDefaults.colors(
    timeSelectorSelectedContainerColor = GrindhausColors.SurfaceHigh,
    timeSelectorUnselectedContainerColor = GrindhausColors.Inset,
    timeSelectorSelectedContentColor = GrindhausColors.Crema,
    timeSelectorUnselectedContentColor = GrindhausColors.Foam,
    periodSelectorBorderColor = GrindhausColors.Outline,
    periodSelectorSelectedContainerColor = GrindhausColors.SurfaceHigh,
    periodSelectorUnselectedContainerColor = Color.Transparent,
    periodSelectorSelectedContentColor = GrindhausColors.Crema,
    periodSelectorUnselectedContentColor = GrindhausColors.Muted,
)
