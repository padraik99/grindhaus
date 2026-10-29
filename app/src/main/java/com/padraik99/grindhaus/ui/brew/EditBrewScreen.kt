package com.padraik99.grindhaus.ui.brew

import com.padraik99.grindhaus.ui.components.grindhausTimeColors
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.data.Grinder
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.Methods
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.domain.Taste
import com.padraik99.grindhaus.ui.components.Choice
import com.padraik99.grindhaus.ui.components.NumberField
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.components.panel
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a cleared yield falls back to: the plan the brew was logged against, or the method's ratio. */
fun BrewLog.plannedOutG(): Double = if (weighed) doseG * Methods.spec(method).ratio else outG

/**
 * Corrects a logged brew: its coffee, method, day and time as well as the numbers. Caffeine
 * is recalculated and moved to the corrected time, so there's never a reason to delete and
 * log it again.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditBrewScreen(
    log: BrewLog,
    bean: Bean,
    choices: List<Bean>,
    allBeans: List<Bean>,
    grinders: List<Grinder>,
    zone: ZoneId,
    today: LocalDate,
    caffeine: (Bean, BrewMethod, Double, Double) -> Double,
    onClose: () -> Unit,
    onSave: (BrewLog, Bean) -> Unit,
    onDelete: () -> Unit,
) {
    val brewed = Instant.ofEpochMilli(log.brewedAt).atZone(zone)
    var beanId by rememberSaveable { mutableLongStateOf(bean.id) }
    var method by rememberSaveable { mutableStateOf(log.method) }
    var day by rememberSaveable { mutableLongStateOf(brewed.toLocalDate().toEpochDay()) }
    var grind by rememberSaveable { mutableStateOf(log.grindSetting) }
    var dose by rememberSaveable { mutableStateOf(formatG(log.doseG)) }
    var out by rememberSaveable { mutableStateOf(if (log.weighed) formatG(log.outG) else "") }
    var time by rememberSaveable { mutableStateOf(log.shotTimeS?.toString().orEmpty()) }
    var rating by rememberSaveable { mutableStateOf(log.rating) }
    var taste by rememberSaveable { mutableStateOf(log.taste) }
    var notes by rememberSaveable { mutableStateOf(log.notes) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val timeState = rememberTimePickerState(initialHour = brewed.hour, initialMinute = brewed.minute, is24Hour = false)

    // The bag this brew belongs to stays a choice even if it's been finished since.
    val beanChoices = (choices + listOfNotNull(allBeans.firstOrNull { it.id == bean.id } ?: bean)).distinctBy { it.id }
    val newBean = beanChoices.firstOrNull { it.id == beanId } ?: bean
    val methodChoices = (Methods.OWNED + log.method).distinct()
    val spec = Methods.spec(method)
    val grinder = grinders.firstOrNull { it.id == log.grinderId } ?: Grinder.MAESTRI_HOUSE
    val planned = log.plannedOutG()

    val doseValue = dose.toDoubleOrNull()?.takeIf { it > 0 }
    val outValue = out.toDoubleOrNull()?.takeIf { it > 0 }
    val at = LocalDate.ofEpochDay(day).atTime(LocalTime.of(timeState.hour, timeState.minute)).atZone(zone).toInstant()
    val newMg = doseValue?.let { caffeine(newBean, method, it, outValue ?: planned) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this brew?") },
            text = { Text("It comes off the history and out of the caffeine count.") },
            confirmButton = { TextButton(onClick = onDelete) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }

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
                Text("Edit brew", color = GrindhausColors.Foam, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            }

            Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("Coffee")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    beanChoices.forEach { b ->
                        Choice(if (b.species == Species.DECAF) "${b.title} (decaf)" else b.title, b.id == beanId) { beanId = b.id }
                    }
                }
                SectionLabel("Method")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    methodChoices.forEach { m -> Choice(Methods.spec(m).label, m == method) { method = m } }
                }
                if ((beanId != bean.id || method != log.method) && newMg != null) {
                    Text(
                        String.format(
                            Locale.US,
                            "Changed from %s · %s. Caffeine goes from ~%.0f mg to ~%.0f mg.",
                            bean.title, Methods.spec(log.method).label, log.caffeineMg, newMg,
                        ),
                        color = GrindhausColors.FoamDim,
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().background(GrindhausColors.Inset, RoundedCornerShape(12.dp)).padding(12.dp),
                    )
                }
            }

            Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SectionLabel("When")
                val loggedDay = brewed.toLocalDate()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Choice("Today", day == today.toEpochDay()) { day = today.toEpochDay() }
                    Choice("Yesterday", day == today.minusDays(1).toEpochDay()) { day = today.minusDays(1).toEpochDay() }
                    if (loggedDay < today.minusDays(1)) {
                        Choice(loggedDay.format(DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)), day == loggedDay.toEpochDay()) {
                            day = loggedDay.toEpochDay()
                        }
                    }
                }
                TimeInput(state = timeState, colors = grindhausTimeColors())
                Text(
                    "Logged at ${clockTime(Instant.ofEpochMilli(log.brewedAt), zone)}. Set when you actually drank it; caffeine counts from then.",
                    color = GrindhausColors.Muted,
                    fontSize = 12.sp,
                )
            }

            Column(Modifier.fillMaxWidth().panel(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SectionLabel("The brew")
                val g = grind
                if (g != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        StepButton("−", "Finer") { grind = (g - grinder.step).coerceAtLeast(grinder.minSetting) }
                        Text(formatGrind(g), color = GrindhausColors.Foam, fontSize = 34.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.semantics { contentDescription = "Grind ${formatGrind(g)}" })
                        StepButton("+", "Coarser") { grind = (g + grinder.step).coerceAtMost(grinder.maxSetting) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    NumberField(dose, { dose = it }, "Dose", Modifier.weight(1f), suffix = "g")
                    NumberField(out, { out = it }, if (spec.weighsYield) "Yield" else "Water", Modifier.weight(1f), placeholder = formatG(planned), suffix = "g")
                    if (spec.timesShot) NumberField(time, { time = it }, "Time", Modifier.weight(1f), decimals = false, suffix = "s")
                }
                if (outValue == null) {
                    Text("Left blank: counted at ${formatG(planned)} g and marked not weighed.", color = GrindhausColors.Muted, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    (1..5).forEach { n ->
                        TextButton(
                            onClick = { rating = if (rating == n) null else n },
                            modifier = Modifier.size(46.dp).semantics { contentDescription = "Rate $n of 5" },
                            contentPadding = PaddingValues(0.dp),
                        ) { Text(if ((rating ?: 0) >= n) "★" else "☆", color = GrindhausColors.Crema, fontSize = 26.sp) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Taste.entries.forEach { t -> Choice(t.label, taste == t) { taste = if (taste == t) null else t } }
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

            TextButton(onClick = { confirmDelete = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Delete this brew", color = GrindhausColors.Bitter)
            }
            Spacer(Modifier.height(4.dp))
        }

        Box(Modifier.fillMaxWidth().height(1.dp).background(GrindhausColors.Line))
        Box(Modifier.fillMaxWidth().background(GrindhausColors.Roast).padding(horizontal = 20.dp, vertical = 12.dp)) {
            Button(
                onClick = {
                    val d = doseValue ?: return@Button
                    // A brew moved to pre-ground coffee has no grind setting.
                    val keepGrind = if (newBean.preGround) null else grind ?: Methods.spec(method).startingGrind
                    onSave(
                        log.copy(
                            method = method,
                            grindSetting = keepGrind,
                            doseG = d,
                            outG = outValue ?: planned,
                            weighed = outValue != null,
                            shotTimeS = if (spec.timesShot) time.toIntOrNull() else null,
                            rating = rating,
                            taste = taste,
                            notes = notes.trim(),
                            brewedAt = at.toEpochMilli(),
                        ),
                        newBean,
                    )
                },
                enabled = doseValue != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GrindhausColors.Crema, contentColor = GrindhausColors.Roast),
            ) { Text("Save changes", fontSize = 17.sp, fontWeight = FontWeight.Bold) }
        }
    }
}
