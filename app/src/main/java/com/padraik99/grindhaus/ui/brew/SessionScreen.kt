package com.padraik99.grindhaus.ui.brew

import com.padraik99.grindhaus.ui.components.TimeOfDayField
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.FilterOption
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.domain.Taste
import com.padraik99.grindhaus.ui.components.Choice
import com.padraik99.grindhaus.ui.components.NumberField
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.components.panel
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A new brew, top to bottom in the order you make it: what you're brewing, the set-up, the
 * shot itself, how it tasted, and when you drank it. Nothing is saved until Save brew.
 */
@Composable
fun SessionScreen(
    state: BrewUiState,
    spec: BrewSpec,
    now: () -> Instant,
    outlook: (Double, Instant) -> BedtimeOutlook,
    caffeine: (Bean, BrewMethod, Double, Double) -> Double,
    onSelectBean: (Long) -> Unit,
    onSelectMethod: (BrewMethod) -> Unit,
    onSelectFilter: (FilterOption) -> Unit,
    onAutoApply: (Boolean) -> Unit,
    onClose: () -> Unit,
    onSave: (BrewDraft, Instant) -> Unit,
) {
    val method = spec.method
    // The set-up follows the chosen coffee, method and basket; switching starts it afresh.
    val combo = "${spec.bean.id}/${method.method}/${spec.filter}"
    var grind by rememberSaveable(combo) { mutableStateOf(spec.grind) }
    var dose by rememberSaveable(combo) { mutableStateOf(formatG(spec.doseG)) }
    var aim by rememberSaveable(combo) { mutableStateOf(formatG(spec.outG)) }
    var yieldText by rememberSaveable(combo) { mutableStateOf("") }
    var time by rememberSaveable(combo) { mutableStateOf("") }
    var rating by rememberSaveable { mutableStateOf<Int?>(null) }
    var taste by rememberSaveable { mutableStateOf<Taste?>(null) }
    var notes by rememberSaveable { mutableStateOf("") }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var whenMode by rememberSaveable { mutableStateOf("now") }
    var dismissedSuggestion by rememberSaveable(combo) { mutableStateOf(false) }
    val timer = rememberSaveable(saver = ShotTimerState.Saver) { ShotTimerState() }
    val zone = state.zone
    val nowAt = now()
    val nowLocal = nowAt.atZone(zone)
    var pickTime by rememberSaveable { mutableStateOf(nowLocal.toLocalTime().withSecond(0).withNano(0)) }

    // The timer's reading drops into Time when it stops.
    LaunchedEffect(timer.running) {
        if (!timer.running) timer.seconds?.let { time = it.toString() }
    }

    val doseValue = dose.toDoubleOrNull()?.takeIf { it > 0 }
    val aimValue = aim.toDoubleOrNull()?.takeIf { it > 0 }
    val yieldValue = yieldText.toDoubleOrNull()?.takeIf { it > 0 }
    // For espresso the cup is weighed after the shot; for the rest, the water poured is the measure.
    val actualOut = if (method.weighsYield) yieldValue else aimValue
    val outForCaffeine = actualOut ?: aimValue ?: spec.outG
    val drankAt: Instant = when (whenMode) {
        "15" -> nowAt.minus(Duration.ofMinutes(15))
        "pick" -> pickedTime(pickTime.hour, pickTime.minute, nowAt, zone)
        else -> nowAt
    }
    val mg = doseValue?.let { caffeine(spec.bean, method.method, it, outForCaffeine) }

    Column(Modifier.fillMaxSize().imePadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(44.dp).border(1.dp, GrindhausColors.Outline, CircleShape),
                ) { Icon(painterResource(R.drawable.ic_close), contentDescription = "Close without saving", tint = GrindhausColors.FoamDim) }
                Text("New brew", color = GrindhausColors.Foam, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }

            // What's being brewed: both can be changed right here.
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PickerButton("Coffee", spec.bean.title, Modifier.weight(1.4f)) { picking = if (picking == "coffee") null else "coffee" }
                PickerButton("Method", method.label, Modifier.weight(1f)) { picking = if (picking == "method") null else "method" }
            }
            when (picking) {
                "coffee" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.shelf.forEach { b ->
                        Choice(beanChoiceLabel(b), b.id == spec.bean.id) {
                            onSelectBean(b.id)
                            picking = null
                        }
                    }
                }
                "method" -> FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    state.methods.forEach { m ->
                        Choice(m.label, m.method == method.method) {
                            onSelectMethod(m.method)
                            picking = null
                        }
                    }
                }
            }
            if (method.filters.size > 1) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(if (method.timesShot) "Basket" else "Filter", color = GrindhausColors.Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
                    method.filters.forEach { f -> Choice(f.label, f == spec.filter) { onSelectFilter(f) } }
                }
            }

            // 1. Set up
            Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("1 · Set up", Modifier.weight(1f))
                    Text(spec.grinder.name, color = GrindhausColors.Muted, fontSize = 12.sp)
                }
                val g = grind
                if (g != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StepButton("−", "Finer") { grind = (g - spec.grinder.step).coerceAtLeast(spec.grinder.minSetting) }
                        Text(
                            formatGrind(g),
                            color = GrindhausColors.Foam,
                            fontSize = 40.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.semantics { contentDescription = "Grind ${formatGrind(g)}" },
                        )
                        StepButton("+", "Coarser") { grind = (g + spec.grinder.step).coerceAtMost(spec.grinder.maxSetting) }
                        Spacer(Modifier.weight(1f))
                        spec.grinder.rangeName(g)?.let {
                            Text("$it range", color = GrindhausColors.Crema, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                } else {
                    Text("Pre-ground: no grind setting", color = GrindhausColors.Muted, fontSize = 14.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    NumberField(dose, { dose = it }, "Dose", Modifier.weight(1f), suffix = "g")
                    NumberField(aim, { aim = it }, if (method.weighsYield) "Aim for" else "Water", Modifier.weight(1f), suffix = "g")
                    Column(Modifier.weight(0.8f)) {
                        SectionLabel("Ratio")
                        val ratio = if (doseValue != null && aimValue != null) "1:${formatRatio(aimValue / doseValue)}" else "–"
                        Text(ratio, color = GrindhausColors.Foam, fontSize = 20.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Text(
                    caffeineLine(mg, spec.bean),
                    color = GrindhausColors.FoamDim,
                    fontSize = 13.sp,
                )
                spec.lastLog?.let { last ->
                    Column(
                        Modifier.fillMaxWidth().background(GrindhausColors.Inset, RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Last time with this coffee", color = GrindhausColors.Muted, fontSize = 12.sp)
                        Text(
                            buildString {
                                append(brewSummary(last))
                                last.taste?.let { append(" · ").append(it.label.lowercase(Locale.US)) }
                            },
                            color = GrindhausColors.FoamDim,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    val s = spec.suggestion
                    if (s != null && !dismissedSuggestion && grind == spec.grind) {
                        Column(
                            Modifier.fillMaxWidth().background(GrindhausColors.SurfaceHigh, RoundedCornerShape(14.dp)).padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(s.reason, color = GrindhausColors.FoamDim, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = { grind = s.grind }) { Text("Use ${formatGrind(s.grind)}", color = GrindhausColors.Crema) }
                                OutlinedButton(onClick = { dismissedSuggestion = true }) {
                                    Text("Keep ${formatGrind(last.grindSetting ?: 0.0)}", color = GrindhausColors.FoamDim)
                                }
                            }
                            Row(
                                Modifier.fillMaxWidth().clickable(role = Role.Checkbox) { onAutoApply(!state.autoApplyGrind) },
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = state.autoApplyGrind,
                                    onCheckedChange = null,
                                    colors = CheckboxDefaults.colors(checkedColor = GrindhausColors.Crema),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Apply grind suggestions automatically", color = GrindhausColors.Muted, fontSize = 12.sp)
                            }
                        }
                    }
                    spec.autoMovedFrom?.let { from ->
                        Text(
                            "Last time was ${formatGrind(from)} and ran ${last.taste?.label?.lowercase(Locale.US)}, so this starts one step over.",
                            color = GrindhausColors.Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }

            // 2. The shot itself (espresso only: the others are measured by the water poured)
            if (method.timesShot) {
                Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionLabel("2 · Pull the shot", Modifier.weight(1f))
                        method.targetTimeS?.let { Text("Target ${it.first}–${it.last} s", color = GrindhausColors.Muted, fontSize = 12.sp) }
                    }
                    ShotTimer(timer, method.targetTimeS)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        NumberField(yieldText, { yieldText = it }, "Yield", Modifier.weight(1f), placeholder = aimValue?.let(::formatG), suffix = "g")
                        NumberField(time, { time = it }, "Time", Modifier.weight(1f), decimals = false, suffix = "s")
                    }
                    Text(
                        if (yieldValue == null) {
                            "Leave yield blank if the scale let you down: it's counted at ${formatG(aimValue ?: spec.outG)} g and marked not weighed."
                        } else {
                            "Time comes from the timer; type over it if you timed it another way."
                        },
                        color = GrindhausColors.Muted,
                        fontSize = 12.sp,
                    )
                }
            }

            // 3. Taste
            Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel(if (method.timesShot) "3 · How was it?" else "2 · How was it?")
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { n ->
                        TextButton(
                            onClick = { rating = if (rating == n) null else n },
                            modifier = Modifier.size(48.dp).semantics { contentDescription = "Rate $n of 5" },
                            contentPadding = PaddingValues(0.dp),
                        ) { Text(if ((rating ?: 0) >= n) "★" else "☆", color = GrindhausColors.Crema, fontSize = 28.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Taste.entries.forEach { t ->
                        Choice(t.label, taste == t) { taste = if (taste == t) null else t }
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
            }

            // When: caffeine counts from when it was drunk, not when it was logged.
            Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("When did you drink it?")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Now", whenMode == "now") { whenMode = "now" }
                    Choice("15 min ago", whenMode == "15") { whenMode = "15" }
                    Choice("Pick a time", whenMode == "pick") { whenMode = "pick" }
                }
                if (whenMode == "pick") TimeOfDayField(pickTime, { pickTime = it })
                Text(
                    "Caffeine counts from ${clockTime(drankAt, zone)}, so a brew logged late, or a freddo from the freezer, still adds up right.",
                    color = GrindhausColors.Muted,
                    fontSize = 12.sp,
                )
            }

            mg?.let { BedtimeLine(outlook(it, drankAt)) }
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(GrindhausColors.Line))
        Box(Modifier.fillMaxWidth().background(GrindhausColors.Roast).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick = {
                    val d = doseValue ?: return@Button
                    onSave(
                        BrewDraft(
                            grind = grind,
                            doseG = d,
                            outG = actualOut,
                            plannedOutG = aimValue,
                            shotTimeS = time.toIntOrNull(),
                            rating = rating,
                            taste = taste,
                            notes = notes,
                        ),
                        drankAt,
                    )
                },
                enabled = doseValue != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GrindhausColors.Crema, contentColor = GrindhausColors.Roast),
            ) { Text("Save brew", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

/** A hour and minute picked for today; if that's still ahead, it means last night. */
fun pickedTime(hour: Int, minute: Int, now: Instant, zone: ZoneId): Instant {
    val today = now.atZone(zone).toLocalDate().atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant()
    return if (today.isAfter(now)) today.minus(Duration.ofDays(1)) else today
}

private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

fun clockTime(at: Instant, zone: ZoneId): String = at.atZone(zone).format(CLOCK).lowercase(Locale.US)

private fun beanChoiceLabel(b: Bean) = if (b.species == Species.DECAF) "${b.title} (decaf)" else b.title

private fun caffeineLine(mg: Double?, bean: Bean): String = when {
    mg == null -> "Enter a dose to see the caffeine."
    bean.species == Species.DECAF -> String.format(Locale.US, "Caffeine ~%.0f mg (decaf)", mg)
    else -> String.format(Locale.US, "Caffeine ~%.0f mg", mg)
}

@Composable
private fun BedtimeLine(o: BedtimeOutlook) {
    val bed = o.bedtime.format(CLOCK).lowercase(Locale.US)
    Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(Modifier.size(8.dp).background(if (o.over) GrindhausColors.Sour else GrindhausColors.Peak, CircleShape))
        Text(
            if (o.over) {
                String.format(Locale.US, "This would leave about %.0f mg in you after %s. Your limit is %.0f mg.", o.peakMg, bed, o.limitMg)
            } else {
                String.format(Locale.US, "Fine for tonight: about %.0f mg left after %s.", o.peakMg, bed)
            },
            color = GrindhausColors.FoamDim,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun PickerButton(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Row(
        modifier
            .height(58.dp)
            .background(GrindhausColors.Surface, RoundedCornerShape(14.dp))
            .border(1.dp, GrindhausColors.Edge, RoundedCornerShape(14.dp))
            .clickable(onClickLabel = "Change ${label.lowercase(Locale.US)}", onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            SectionLabel(label)
            Text(value, color = GrindhausColors.Foam, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        Text("Change", color = GrindhausColors.Crema, fontSize = 12.sp)
    }
}

@Composable
fun StepButton(symbol: String, description: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        contentPadding = PaddingValues(0.dp),
        modifier = Modifier.size(46.dp).semantics { contentDescription = description },
    ) { Text(symbol, fontSize = 20.sp, color = GrindhausColors.Foam) }
}
