package com.padraik99.grindhaus.ui.brew

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.data.Grinder
import com.padraik99.grindhaus.domain.Taste
import com.padraik99.grindhaus.domain.TeaType
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.components.RoastBar
import com.padraik99.grindhaus.ui.components.color
import com.padraik99.grindhaus.ui.components.rememberVersionName
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import com.padraik99.grindhaus.ui.vault.LabelThumb
import com.padraik99.grindhaus.ui.vault.freshness
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.LaunchedEffect
import java.time.LocalDate
import java.util.Locale

@Composable
fun BrewRoute(viewModel: BrewViewModel, today: LocalDate, onAddBean: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var logging by rememberSaveable { mutableStateOf(false) }
    var editingLast by rememberSaveable { mutableStateOf(false) }
    val timer = rememberSaveable(saver = ShotTimerState.Saver) { ShotTimerState() }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val spec = state.spec
    val last = spec?.lastLog
    Box(Modifier.fillMaxSize()) {
        when {
            logging && spec != null -> {
                BackHandler { logging = false }
                BrewLogEditor(
                    title = "Log brew",
                    subtitle = "${spec.method.label} · ${spec.filter.label} · ${spec.bean.title}",
                    method = spec.method,
                    grinder = spec.grinder,
                    initial = BrewDraft(
                        grind = spec.grind,
                        doseG = spec.doseG,
                        // Espresso yield starts blank: weigh it, or leave it as "not weighed".
                        outG = if (spec.method.weighsYield) null else spec.outG,
                        shotTimeS = timer.seconds,
                        rating = null,
                        taste = null,
                        notes = "",
                    ),
                    plannedOutG = spec.outG,
                    onCancel = { logging = false },
                    onSave = { draft ->
                        viewModel.saveBrew(draft) {
                            timer.reset()
                            logging = false
                        }
                    },
                )
            }
            editingLast && spec != null && last != null -> {
                BackHandler { editingLast = false }
                BrewLogEditor(
                    title = "Edit brew",
                    subtitle = "${spec.method.label} · ${spec.filter.label} · ${spec.bean.title}",
                    method = spec.method,
                    grinder = spec.grinder,
                    initial = last.toDraft(),
                    plannedOutG = last.plannedOutG(),
                    onCancel = { editingLast = false },
                    onSave = { draft -> viewModel.updateBrew(last, draft) { editingLast = false } },
                    onDelete = { viewModel.deleteBrew(last) { editingLast = false } },
                )
            }
            else -> BrewBar(
                state = state,
                today = today,
                timer = timer,
                onSelectBean = viewModel::selectBean,
                onSelectMethod = viewModel::selectMethod,
                onSelectFilter = viewModel::selectFilter,
                onAcceptSuggestion = viewModel::acceptSuggestion,
                onAutoApply = viewModel::setAutoApply,
                onTea = { type ->
                    viewModel.logTea(type) { id ->
                        scope.launch {
                            snackbar.currentSnackbarData?.dismiss()
                            val result = snackbar.showSnackbar(
                                message = teaConfirmation(type),
                                actionLabel = "Undo",
                                duration = SnackbarDuration.Long,
                            )
                            if (result == SnackbarResult.ActionPerformed) viewModel.undoTea(id)
                        }
                    }
                },
                onLog = {
                    timer.stop()
                    logging = true
                },
                onEditLast = { editingLast = true },
                onAddBean = onAddBean,
                onStepGrind = viewModel::stepGrind,
                onSetDose = viewModel::setDose,
                onSetOut = viewModel::setOut,
            )
        }
        SnackbarHost(
            snackbar,
            Modifier.align(Alignment.BottomCenter).padding(start = 12.dp, end = 12.dp, bottom = 84.dp),
        ) { data ->
            Snackbar(
                modifier = Modifier.border(1.dp, GrindhausColors.Crema.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                containerColor = GrindhausColors.SurfaceHigh,
                contentColor = GrindhausColors.Foam,
                action = data.visuals.actionLabel?.let { label ->
                    { TextButton(onClick = data::performAction) { Text(label, color = GrindhausColors.Crema) } }
                },
            ) { Text(data.visuals.message) }
        }
    }
}

/** "Black tea logged · ~30 mg". It counts toward tonight right away, then shows in "in you" as it absorbs. */
private fun teaConfirmation(type: TeaType): String = when (type) {
    TeaType.BLACK -> "Black tea logged · ~30 mg"
    TeaType.GREEN -> "Green tea logged · ~25 mg"
    TeaType.HERBAL -> "Herbal tea logged · no caffeine"
}

@Composable
fun BrewBar(
    state: BrewUiState,
    today: LocalDate,
    timer: ShotTimerState,
    onSelectBean: (Long) -> Unit,
    onSelectMethod: (com.padraik99.grindhaus.domain.BrewMethod) -> Unit,
    onSelectFilter: (com.padraik99.grindhaus.domain.FilterOption) -> Unit,
    onAcceptSuggestion: () -> Unit,
    onAutoApply: (Boolean) -> Unit,
    onTea: (TeaType) -> Unit,
    onLog: () -> Unit,
    onEditLast: () -> Unit,
    onAddBean: () -> Unit,
    onStepGrind: (Int) -> Unit = {},
    onSetDose: (Double) -> Unit = {},
    onSetOut: (Double) -> Unit = {},
) {
    val spec = state.spec
    // Which number on the card is being typed into: "dose", "out" or none.
    var editingNumber by rememberSaveable { mutableStateOf<String?>(null) }
    // imePadding: the keyboard pushes the card and the pinned button up instead of covering them.
    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Header(state.inYouNowMg, onTea)
            Text(
                "Brew",
                color = GrindhausColors.Foam,
                fontSize = 30.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            if (state.loaded && state.beans.isEmpty()) {
                Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Add a bag to the Vault and it shows up here, ready to dial in.", color = GrindhausColors.Muted, fontSize = 15.sp)
                    OutlinedButton(onClick = onAddBean) { Text("Go to the Vault", color = GrindhausColors.FoamDim) }
                }
                return@Column
            }
            if (spec == null) return@Column

            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.beans, key = { it.id }) { bean ->
                    BeanChip(bean, today, selected = bean.id == spec.bean.id) { onSelectBean(bean.id) }
                }
            }

            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().background(GrindhausColors.Surface, RoundedCornerShape(50)).padding(4.dp),
                ) {
                    state.methods.forEach { m ->
                        val selected = m.method == state.selectedMethod
                        Box(
                            Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(50))
                                .background(if (selected) GrindhausColors.Foam else GrindhausColors.Surface)
                                .semantics { role = Role.Tab; this.selected = selected }
                                .clickable { onSelectMethod(m.method) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                m.label,
                                color = if (selected) GrindhausColors.Roast else GrindhausColors.FoamDim,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 15.sp,
                            )
                        }
                    }
                }
                if (spec.method.filters.size > 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(if (spec.method.timesShot) "Basket" else "Filter", color = GrindhausColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                        spec.method.filters.forEach { f ->
                            Spacer(Modifier.width(6.dp))
                            FilterChip(
                                selected = f == spec.filter,
                                onClick = { onSelectFilter(f) },
                                label = { Text(f.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = GrindhausColors.Foam,
                                    selectedLabelColor = GrindhausColors.Roast,
                                    labelColor = GrindhausColors.FoamDim,
                                ),
                            )
                        }
                    }
                }
            }

            NextShotCard(
                spec = spec,
                timer = timer,
                autoApply = state.autoApplyGrind,
                onAccept = onAcceptSuggestion,
                onAutoApply = onAutoApply,
                onStepGrind = onStepGrind,
                editing = editingNumber,
                onEdit = { editingNumber = it },
                onSaveNumber = { which, grams ->
                    if (which == "dose") onSetDose(grams) else onSetOut(grams)
                    editingNumber = null
                },
            )

            spec.warning?.let { w ->
                Text(
                    String.format(
                        Locale.US,
                        "This shot would leave about %.0f mg in you after %s. Your limit is %.0f mg.",
                        w.peakMg, w.bedtime, w.limitMg,
                    ),
                    color = GrindhausColors.Foam,
                    fontSize = 13.sp,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .fillMaxWidth()
                        .border(1.dp, GrindhausColors.Crema, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                )
            }

            spec.lastLog?.let { LastBrewCard(it, spec.method.timesShot, onEditLast) }
        }

        // Pinned below the scrolling part, so it's always in reach, never covers anything,
        // and never reads as part of the last brew.
        if (spec != null) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(GrindhausColors.Line))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(GrindhausColors.Roast)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
            ) {
                Button(
                    onClick = onLog,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GrindhausColors.Crema, contentColor = GrindhausColors.Roast),
                ) { Text("Log this shot", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun Header(inYouNowMg: Double, onTea: (TeaType) -> Unit) {
    var teaMenu by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column {
            Text("GRINDHAUS", color = GrindhausColors.Crema, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.6.sp)
            val version = rememberVersionName()
            if (version.isNotEmpty()) Text("v$version", color = GrindhausColors.Muted, fontSize = 10.sp, letterSpacing = 0.5.sp)
        }
        Spacer(Modifier.weight(1f))
        Text(
            String.format(Locale.US, "%.0f mg in you", inYouNowMg),
            color = GrindhausColors.FoamDim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.background(GrindhausColors.Surface, RoundedCornerShape(50)).padding(horizontal = 12.dp, vertical = 7.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box {
            OutlinedButton(onClick = { teaMenu = true }, contentPadding = PaddingValues(horizontal = 12.dp)) {
                Text("+ Tea", color = GrindhausColors.FoamDim, fontSize = 13.sp)
            }
            DropdownMenu(expanded = teaMenu, onDismissRequest = { teaMenu = false }) {
                TeaType.entries.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(teaLabel(type)) },
                        onClick = {
                            teaMenu = false
                            onTea(type)
                        },
                    )
                }
            }
        }
    }
}

private fun teaLabel(type: TeaType): String = when (type) {
    TeaType.BLACK -> "Black tea · ~30 mg"
    TeaType.GREEN -> "Green tea · ~25 mg"
    TeaType.HERBAL -> "Herbal tea · 0 mg"
}

@Composable
private fun BeanChip(bean: Bean, today: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val freshness = bean.freshness(today)
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GrindhausColors.Surface)
            .border(2.dp, if (selected) GrindhausColors.Crema else GrindhausColors.Surface, RoundedCornerShape(16.dp))
            .semantics { this.selected = selected }
            .clickable(onClickLabel = "Brew ${bean.title}", onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LabelThumb(bean.labelPhotoPath, Modifier.fillMaxWidth().height(70.dp))
        Text(bean.title, color = GrindhausColors.Foam, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        RoastBar(bean.roastLevel)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(freshness.tone.color(), CircleShape))
            Text(freshness.label, color = freshness.tone.color(), fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** The recipe for the shot you're about to pull. Everything on it can be changed here. */
@Composable
private fun NextShotCard(
    spec: BrewSpec,
    timer: ShotTimerState,
    autoApply: Boolean,
    onAccept: () -> Unit,
    onAutoApply: (Boolean) -> Unit,
    onStepGrind: (Int) -> Unit,
    editing: String?,
    onEdit: (String?) -> Unit,
    onSaveNumber: (String, Double) -> Unit,
) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .background(GrindhausColors.Surface, RoundedCornerShape(22.dp))
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel(if (spec.method.timesShot) "Next shot" else "Next brew")
            Spacer(Modifier.weight(1f))
            Text(spec.grinder.name, color = GrindhausColors.Muted, fontSize = 12.sp)
        }
        if (spec.grind != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                StepButton("−", "Finer") { onStepGrind(-1) }
                Text(
                    formatGrind(spec.grind),
                    color = GrindhausColors.Foam,
                    fontSize = 40.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(horizontal = 14.dp).semantics { contentDescription = "Grind ${formatGrind(spec.grind)}" },
                )
                StepButton("+", "Coarser") { onStepGrind(1) }
                Spacer(Modifier.weight(1f))
                spec.grinder.rangeName(spec.grind)?.let {
                    Text("$it range", color = GrindhausColors.Crema, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            DialBar(spec.grinder, spec.grind)
        } else {
            Text("Pre-ground: no grind setting", color = GrindhausColors.Muted, fontSize = 14.sp)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditableStat("Dose", "${formatG(spec.doseG)} g", Modifier.weight(1f), editing == "dose") { onEdit("dose") }
            EditableStat(
                if (spec.method.weighsYield) "Yield" else "Water", "${formatG(spec.outG)} g", Modifier.weight(1f), editing == "out",
            ) { onEdit("out") }
            Stat("Ratio", String.format(Locale.US, "1:%s", formatRatio(spec.ratio)), Modifier.weight(1f))
        }
        when (editing) {
            "dose" -> InlineNumberEditor("Dose (g)", spec.doseG, onCancel = { onEdit(null) }) { onSaveNumber("dose", it) }
            "out" -> InlineNumberEditor(
                if (spec.method.weighsYield) "Yield (g)" else "Water (g)", spec.outG, onCancel = { onEdit(null) },
            ) { onSaveNumber("out", it) }
        }
        Row {
            spec.method.targetTimeS?.let {
                Text("Target ${it.first}–${it.last} s", color = GrindhausColors.FoamDim, fontSize = 13.sp, modifier = Modifier.weight(1f))
            } ?: Spacer(Modifier.weight(1f))
            Text(
                String.format(Locale.US, "Caffeine ~%.0f mg", spec.caffeine.mg),
                color = GrindhausColors.FoamDim,
                fontSize = 13.sp,
            )
        }
        if (spec.method.timesShot) ShotTimer(timer, spec.method.targetTimeS)

        val last = spec.lastLog
        var dismissedFor by rememberSaveable { mutableLongStateOf(-1L) }
        if (last != null) {
            spec.suggestion?.takeIf { dismissedFor != last.id }?.let { s ->
                Column(
                    Modifier.fillMaxWidth().background(GrindhausColors.SurfaceHigh, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(s.reason, color = GrindhausColors.FoamDim, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onAccept) { Text("Use ${formatGrind(s.grind)}", color = GrindhausColors.Crema) }
                        OutlinedButton(onClick = { dismissedFor = last.id }) {
                            Text("Keep ${formatGrind(last.grindSetting ?: 0.0)}", color = GrindhausColors.FoamDim)
                        }
                    }
                    AutoApplyRow(autoApply, onAutoApply)
                }
            }
            spec.autoMovedFrom?.let { from ->
                Text(
                    "Last shot was at ${formatGrind(from)} and ran ${last.taste?.label?.lowercase()}.",
                    color = GrindhausColors.Muted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

/** What happened last time. Plainly history: tap it to correct or delete it. */
@Composable
private fun LastBrewCard(last: BrewLog, timesShot: Boolean, onEdit: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, GrindhausColors.Line, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Edit last brew", onClick = onEdit)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionLabel("Last brew")
            Spacer(Modifier.weight(1f))
            Text("Edit", color = GrindhausColors.Crema, fontSize = 13.sp)
        }
        LastBrewLine(last, timesShot)
        if (last.notes.isNotBlank()) Text(last.notes, color = GrindhausColors.FoamDim, fontSize = 13.sp, maxLines = 2)
    }
}

@Composable
private fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(44.dp).semantics { contentDescription = description },
    ) { Text(symbol, fontSize = 20.sp, color = GrindhausColors.Foam) }
}

/** A number on the card that opens a small editor when tapped; the pill says "you can change this". */
@Composable
private fun EditableStat(label: String, value: String, modifier: Modifier, selected: Boolean, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Change ${label.lowercase()}", onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SectionLabel(label)
        Text(
            value,
            color = GrindhausColors.Foam,
            fontSize = 22.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(GrindhausColors.SurfaceHigh, RoundedCornerShape(10.dp))
                .border(1.dp, if (selected) GrindhausColors.Crema else GrindhausColors.SurfaceHigh, RoundedCornerShape(10.dp))
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/** Types a new dose or yield right on the card. The keyboard opens straight away; Done saves. */
@Composable
private fun InlineNumberEditor(label: String, current: Double, onCancel: () -> Unit, onSave: (Double) -> Unit) {
    var text by rememberSaveable(label) { mutableStateOf(formatG(current)) }
    val value = text.toDoubleOrNull()?.takeIf { it > 0 && it < 2_000 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(label) { focus.requestFocus() }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { v ->
                val cleaned = v.filter { it.isDigit() || it == '.' }
                if (cleaned.count { it == '.' } <= 1 && cleaned.length <= 6) text = cleaned
            },
            label = { Text(label) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { value?.let(onSave) }),
            modifier = Modifier.weight(1f).focusRequester(focus),
        )
        TextButton(onClick = onCancel) { Text("Cancel", color = GrindhausColors.Muted) }
        TextButton(onClick = { value?.let(onSave) }, enabled = value != null) { Text("Set", color = GrindhausColors.Crema) }
    }
}

@Composable
private fun AutoApplyRow(on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onChange(!on) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = on, onCheckedChange = null, colors = CheckboxDefaults.colors(checkedColor = GrindhausColors.Crema))
        Spacer(Modifier.width(8.dp))
        Text("Apply grind suggestions automatically", color = GrindhausColors.Muted, fontSize = 12.sp)
    }
}

@Composable
fun LastBrewLine(log: BrewLog, timesShot: Boolean) {
    // Wraps onto a second line when a long shot line, stars and taste don't fit side by side.
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        val shot = buildString {
            append(if (log.weighed) "${formatG(log.doseG)} → ${formatG(log.outG)} g" else "${formatG(log.doseG)} → ~${formatG(log.outG)} g")
            if (timesShot && log.shotTimeS != null) append(" · ${log.shotTimeS} s")
        }
        log.grindSetting?.let { Text("Grind ${formatGrind(it)}", color = GrindhausColors.Muted, fontSize = 13.sp) }
        Text(shot, color = GrindhausColors.Foam, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
        if (!log.weighed) Text("not weighed", color = GrindhausColors.Muted, fontSize = 12.sp)
        log.rating?.let { Stars(it) }
        log.taste?.let { TasteTag(it) }
    }
}

@Composable
fun Stars(rating: Int) {
    Text(
        "★".repeat(rating) + "☆".repeat(5 - rating),
        color = GrindhausColors.Crema,
        fontSize = 13.sp,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "Rated $rating of 5" },
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

@Composable
private fun Stat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        SectionLabel(label)
        Text(value, color = GrindhausColors.Foam, fontSize = 22.sp, fontFamily = FontFamily.Monospace)
    }
}

/** The whole dial with its printed ranges, and a marker at the current setting. */
@Composable
private fun DialBar(grinder: Grinder, setting: Double) {
    val span = (grinder.maxSetting - grinder.minSetting).takeIf { it > 0 } ?: 1.0
    fun frac(v: Double) = ((v - grinder.minSetting) / span).toFloat().coerceIn(0f, 1f)
    Column(
        Modifier.clearAndSetSemantics {
            contentDescription = "Grind ${formatGrind(setting)} on a ${formatGrind(grinder.minSetting)} to ${formatGrind(grinder.maxSetting)} dial"
        },
    ) {
        Box(Modifier.fillMaxWidth().height(16.dp)) {
            Box(Modifier.fillMaxWidth().height(6.dp).offset(y = 5.dp).background(GrindhausColors.Line, RoundedCornerShape(3.dp)))
            Box(
                Modifier.fillMaxWidth(frac(setting).coerceAtLeast(0.01f)).height(6.dp).offset(y = 5.dp)
                    .background(GrindhausColors.Crema.copy(alpha = 0.45f), RoundedCornerShape(3.dp)),
            )
            Row(Modifier.fillMaxWidth()) {
                val f = frac(setting)
                if (f > 0f) Spacer(Modifier.weight(f))
                Box(Modifier.width(4.dp).fillMaxHeight().background(GrindhausColors.Crema, RoundedCornerShape(2.dp)))
                if (f < 1f) Spacer(Modifier.weight(1f - f))
            }
        }
        // Each printed range name sits under the middle of its own stretch of the dial.
        // No fixed height: the row grows with the phone's text size instead of clipping.
        BoxWithConstraints(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            val labelWidth = 64.dp
            var from = grinder.minSetting
            grinder.labels.forEach { (end, name) ->
                val mid = (from + end) / 2
                from = end
                val x = (maxWidth * frac(mid) - labelWidth / 2).coerceIn(0.dp, maxWidth - labelWidth)
                Text(
                    name,
                    color = if (grinder.rangeName(setting) == name) GrindhausColors.Crema else GrindhausColors.Muted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier.width(labelWidth).offset(x = x),
                )
            }
        }
    }
}

fun formatGrind(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
fun formatG(v: Double): String = if (v % 1.0 == 0.0) v.toInt().toString() else String.format(Locale.US, "%.1f", v)
private fun formatRatio(v: Double): String = if (kotlin.math.abs(v - Math.round(v)) < 0.05) Math.round(v).toString() else String.format(Locale.US, "%.1f", v)
