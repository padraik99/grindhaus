package com.padraik99.grindhaus.ui.brew

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.data.SavedTea
import com.padraik99.grindhaus.domain.CaffeineEstimates
import com.padraik99.grindhaus.domain.TeaType
import com.padraik99.grindhaus.ui.components.Choice
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import java.util.Locale

private const val NONE = -1L

fun TeaType.label(): String = when (this) {
    TeaType.BLACK -> "Black"
    TeaType.GREEN -> "Green"
    TeaType.HERBAL -> "Herbal"
}

private fun TeaType.mgText(): String {
    val mg = CaffeineEstimates.tea(this).mg
    return if (mg == 0.0) "0 mg" else String.format(Locale.US, "~%.0f mg", mg)
}

/**
 * Logging a tea: your saved teas first (most used at the top), then any black, green or
 * herbal. Long-press a saved tea to rename or remove it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TeaScreen(
    teas: List<SavedTea>,
    onClose: () -> Unit,
    onLog: (TeaType, SavedTea?) -> Unit,
    onSaveTea: (String, TeaType) -> Unit,
    onUpdateTea: (SavedTea) -> Unit,
    onRemoveTea: (SavedTea) -> Unit,
) {
    var adding by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableLongStateOf(NONE) }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.size(44.dp).border(1.dp, GrindhausColors.Outline, CircleShape),
            ) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close", tint = GrindhausColors.FoamDim) }
            Column(Modifier.weight(1f)) {
                Text("Log a tea", color = GrindhausColors.Foam, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Text("Counts from now", color = GrindhausColors.Muted, fontSize = 12.sp)
            }
        }

        SectionLabel("Your teas")
        if (teas.isEmpty()) {
            Text("Save the teas you drink often and they show up here, one tap to log.", color = GrindhausColors.Muted, fontSize = 13.sp)
        } else {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, GrindhausColors.Edge, RoundedCornerShape(16.dp)),
            ) {
                teas.forEachIndexed { i, tea ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GrindhausColors.Edge))
                    if (editingId == tea.id) {
                        TeaForm(
                            initialName = tea.name,
                            initialType = tea.type,
                            saveLabel = "Save",
                            onCancel = { editingId = NONE },
                            onSave = { name, type ->
                                onUpdateTea(tea.copy(name = name, type = type))
                                editingId = NONE
                            },
                            onRemove = {
                                onRemoveTea(tea)
                                editingId = NONE
                            },
                        )
                    } else {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .background(GrindhausColors.Surface)
                                .combinedClickable(
                                    onClickLabel = "Log ${tea.name}",
                                    onLongClickLabel = "Rename or remove ${tea.name}",
                                    onLongClick = { editingId = tea.id },
                                    onClick = { onLog(tea.type, tea) },
                                )
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(tea.name, color = GrindhausColors.Foam, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                Text(tea.type.label(), color = GrindhausColors.Muted, fontSize = 12.sp)
                            }
                            Text(tea.type.mgText(), color = GrindhausColors.FoamDim, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
            Text("Tap to log. Long-press to rename or remove.", color = GrindhausColors.Muted, fontSize = 12.sp)
        }

        SectionLabel("Any other tea")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeaType.entries.forEach { type ->
                OutlinedButton(onClick = { onLog(type, null) }, modifier = Modifier.weight(1f).height(52.dp)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(type.label(), color = GrindhausColors.FoamDim, fontSize = 14.sp)
                        Text(type.mgText(), color = GrindhausColors.Muted, fontSize = 11.sp)
                    }
                }
            }
        }

        if (adding) {
            Box(Modifier.clip(RoundedCornerShape(16.dp)).border(1.dp, GrindhausColors.Edge, RoundedCornerShape(16.dp))) {
                TeaForm(
                    initialName = "",
                    initialType = TeaType.BLACK,
                    saveLabel = "Save tea",
                    onCancel = { adding = false },
                    onSave = { name, type ->
                        onSaveTea(name, type)
                        adding = false
                    },
                    onRemove = null,
                )
            }
        } else {
            OutlinedButton(onClick = { adding = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                Text("+ Save a tea you drink often", color = GrindhausColors.Crema, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun TeaForm(
    initialName: String,
    initialType: TeaType,
    saveLabel: String,
    onCancel: () -> Unit,
    onSave: (String, TeaType) -> Unit,
    onRemove: (() -> Unit)?,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var type by rememberSaveable { mutableStateOf(initialType) }
    Column(
        Modifier.fillMaxWidth().background(GrindhausColors.Surface).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text("Tea name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TeaType.entries.forEach { t -> Choice("${t.label()} · ${t.mgText()}", t == type) { type = t } }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (onRemove != null) TextButton(onClick = onRemove) { Text("Remove", color = GrindhausColors.Bitter) }
            Box(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Cancel", color = GrindhausColors.Muted) }
            TextButton(onClick = { onSave(name, type) }, enabled = name.isNotBlank()) { Text(saveLabel, color = GrindhausColors.Crema) }
        }
    }
}
