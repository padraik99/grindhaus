package com.padraik99.grindhaus.ui.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.domain.BuyAgain
import com.padraik99.grindhaus.domain.Comparison
import com.padraik99.grindhaus.ui.theme.GrindhausColors

/**
 * Two quick questions about a bag, asked when it's finished (or later, to change your mind).
 * Both are optional. The comparison only appears once there's a favourite to compare with.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerdictDialog(
    bean: Bean,
    favourite: Bean?,
    finishing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (BuyAgain?, comparedWith: Long?, Comparison?) -> Unit,
) {
    var buyAgain by rememberSaveable { mutableStateOf(bean.buyAgain) }
    var outcome by rememberSaveable { mutableStateOf<Comparison?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (finishing) "Finished ${bean.title}?" else "Your verdict on ${bean.title}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Text("Would you buy it again?", color = GrindhausColors.FoamDim, fontSize = 15.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(BuyAgain.YES to "Yes", BuyAgain.MAYBE to "Maybe", BuyAgain.NO to "No").forEach { (value, label) ->
                        Choice(label, buyAgain == value) { buyAgain = if (buyAgain == value) null else value }
                    }
                }
                if (favourite != null) {
                    Text("Better than ${favourite.title}?", color = GrindhausColors.FoamDim, fontSize = 15.sp)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Comparison.entries.forEach { c ->
                            Choice(c.label, outcome == c) { outcome = if (outcome == c) null else c }
                        }
                    }
                    Text(
                        "Your favourite so far. One answer only nudges the ranking, so an off day won't upend it.",
                        color = GrindhausColors.Muted,
                        fontSize = 12.sp,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(buyAgain, favourite?.id, outcome) }) {
                Text(if (finishing) "Archive" else "Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun Choice(text: String, selected: Boolean, onClick: () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(text) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = GrindhausColors.Foam,
            selectedLabelColor = GrindhausColors.Roast,
            labelColor = GrindhausColors.FoamDim,
        ),
    )
}
