package com.padraik99.grindhaus.ui.vault

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.data.BagDetails
import com.padraik99.grindhaus.data.BarcodeScanner
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.LookupOutcome
import com.padraik99.grindhaus.data.ScanResult
import com.padraik99.grindhaus.data.LabelPhotos
import com.padraik99.grindhaus.data.RoastLevel
import com.padraik99.grindhaus.domain.BuyAgain
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.components.asEpochDayText
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import kotlin.math.roundToInt

private const val MILLIS_PER_DAY = 86_400_000L

/** Common bag sizes, so the label's net weight is one tap. */
private val BAG_SIZES = listOf("12 oz" to 340.0, "1 lb" to 454.0, "250 g" to 250.0, "1 kg" to 1000.0)

/**
 * Add a new bag ([initial] null) or edit an existing one. Only the name is required;
 * everything else can be filled in later.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeanEditor(
    initial: Bean?,
    today: LocalDate,
    onCancel: () -> Unit,
    onSave: (Bean) -> Unit,
    /** Offered on new bags only. */
    scanner: BarcodeScanner? = null,
    lookup: suspend (String) -> LookupOutcome = { LookupOutcome.NotFound(it) },
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val start = initial ?: Bean(title = "", roastLevel = RoastLevel.MEDIUM, purchaseDate = today.toEpochDay())

    var title by rememberSaveable { mutableStateOf(start.title) }
    var roaster by rememberSaveable { mutableStateOf(start.roaster) }
    var origin by rememberSaveable { mutableStateOf(start.origin.orEmpty()) }
    var species by rememberSaveable { mutableStateOf(start.species) }
    var robustaShare by rememberSaveable { mutableDoubleStateOf(start.robustaShare ?: 0.5) }
    var roastLevel by rememberSaveable { mutableStateOf(start.roastLevel) }
    var roastDate by rememberSaveable { mutableStateOf(start.roastDate) }
    var bestByDate by rememberSaveable { mutableStateOf(start.bestByDate) }
    var purchaseDate by rememberSaveable { mutableStateOf(start.purchaseDate) }
    var openedDate by rememberSaveable { mutableStateOf(start.openedDate) }
    var bagGrams by rememberSaveable { mutableStateOf(start.bagGrams?.roundToInt()?.toString().orEmpty()) }
    var preGround by rememberSaveable { mutableStateOf(start.preGround) }
    var tags by rememberSaveable { mutableStateOf(start.tastingTags) }
    var notes by rememberSaveable { mutableStateOf(start.notes) }
    var photo by rememberSaveable { mutableStateOf(start.labelPhotoPath) }
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var barcode by rememberSaveable { mutableStateOf(start.barcode) }
    var scanStatus by rememberSaveable { mutableStateOf<String?>(null) }
    var scanning by rememberSaveable { mutableStateOf(false) }

    /** Text fields are filled only if still empty; choices take whatever the lookup knows. */
    fun fill(d: BagDetails) {
        if (title.isBlank()) d.title?.let { title = it }
        if (roaster.isBlank()) d.roaster?.let { roaster = it }
        if (origin.isBlank()) d.origin?.let { origin = it }
        d.species?.let { species = it }
        d.roastLevel?.let { roastLevel = it }
        if (bagGrams.isBlank()) d.bagGrams?.let { bagGrams = it.roundToInt().toString() }
        d.preGround?.let { preGround = it }
    }

    fun scan(s: BarcodeScanner) {
        scope.launch {
            scanning = true
            when (val result = s.scan()) {
                ScanResult.Cancelled -> Unit
                is ScanResult.Failed -> scanStatus =
                    "Couldn't start the scanner. The first time, Google Play services may still be downloading it: " +
                        "try again in a minute. (${result.reason ?: "no reason given"})"
                is ScanResult.Code -> {
                    barcode = result.value
                    scanStatus = "Looking up ${result.value}…"
                    val outcome = lookup(result.value)
                    scanStatus = when (outcome) {
                        is LookupOutcome.FromVault -> {
                            val b = outcome.bean
                            fill(BagDetails(b.title, b.roaster, b.origin, b.species, b.roastLevel, b.bagGrams, b.preGround))
                            if (b.species == Species.BLEND) b.robustaShare?.let { robustaShare = it }
                            if (tags.isBlank()) tags = b.tastingTags
                            val verdict = when (b.buyAgain) {
                                BuyAgain.YES -> " Last time you said you'd buy it again."
                                BuyAgain.MAYBE -> " Last time you said maybe."
                                BuyAgain.NO -> " Last time you said you wouldn't buy it again."
                                null -> ""
                            }
                            "You've had this one before. Filled in from that bag.$verdict Add this bag's dates."
                        }
                        is LookupOutcome.Found -> {
                            fill(outcome.details)
                            "Found on Open Food Facts. Check what filled in; the roast date and tasting notes are on the bag."
                        }
                        is LookupOutcome.NotFound ->
                            "Not on Open Food Facts yet. Fill it in below; the barcode is saved, so this bag fills itself in next time."
                        is LookupOutcome.Unavailable ->
                            "Couldn't reach Open Food Facts. Check the connection, or fill it in below; the barcode is still saved."
                    }
                }
            }
            scanning = false
        }
    }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val temp = pendingCapture?.let(::File)
        pendingCapture = null
        if (ok && temp != null) {
            scope.launch {
                val kept = LabelPhotos.keep(context, temp) ?: return@launch
                // A photo taken during this edit and then replaced is discarded straight away.
                if (photo != start.labelPhotoPath) LabelPhotos.delete(context, photo)
                photo = kept
            }
        } else {
            temp?.delete()
        }
    }

    fun cancel() {
        if (photo != start.labelPhotoPath) LabelPhotos.delete(context, photo)
        onCancel()
    }

    fun save() {
        if (photo != start.labelPhotoPath) LabelPhotos.delete(context, start.labelPhotoPath)
        onSave(
            start.copy(
                title = title.trim(),
                roaster = roaster.trim(),
                origin = origin.trim().ifEmpty { null },
                species = species,
                robustaShare = if (species == Species.BLEND) robustaShare else null,
                roastLevel = roastLevel,
                roastDate = roastDate,
                bestByDate = bestByDate,
                purchaseDate = purchaseDate,
                openedDate = openedDate,
                bagGrams = bagGrams.toDoubleOrNull(),
                preGround = preGround,
                labelPhotoPath = photo,
                tastingTags = tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "),
                notes = notes.trim(),
                barcode = barcode,
            ),
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            if (initial == null) "New coffee" else "Edit coffee",
            color = GrindhausColors.Foam,
            fontSize = 26.sp,
            fontWeight = FontWeight.SemiBold,
        )

        if (scanner != null) {
            OutlinedButton(
                onClick = { scan(scanner) },
                enabled = !scanning,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Icon(painterResource(R.drawable.ic_barcode), contentDescription = null, tint = GrindhausColors.Crema)
                Text(if (barcode == null) "  Scan barcode" else "  Scan again", color = GrindhausColors.Crema)
            }
            scanStatus?.let { Text(it, color = GrindhausColors.FoamDim, fontSize = 13.sp) }
                ?: Hint("Fills in what it can from your past bags or Open Food Facts. Only this button uses the internet.")
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LabelThumb(photo, Modifier.size(width = 76.dp, height = 96.dp))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = {
                    val (temp, uri) = LabelPhotos.newCapture(context)
                    pendingCapture = temp.path
                    takePicture.launch(uri)
                }) {
                    Icon(painterResource(R.drawable.ic_camera), contentDescription = null, tint = GrindhausColors.FoamDim)
                    Text(if (photo == null) "  Photograph label" else "  Retake", color = GrindhausColors.FoamDim)
                }
                if (photo != null) {
                    TextButton(onClick = {
                        if (photo != start.labelPhotoPath) LabelPhotos.delete(context, photo)
                        photo = null
                    }) { Text("Remove photo", color = GrindhausColors.Muted) }
                }
            }
        }

        Field("Name", title, { title = it }, required = true)
        Field("Roaster", roaster, { roaster = it })
        Field("Origin", origin, { origin = it })

        SectionLabel("Roast level")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoastLevel.entries.forEach { level ->
                Choice(level.label, roastLevel == level) { roastLevel = level }
            }
        }

        SectionLabel("Species")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Species.NOT_SURE, Species.ARABICA, Species.ROBUSTA, Species.BLEND, Species.DECAF).forEach { s ->
                Choice(if (s == Species.NOT_SURE) "Not sure" else s.label(), species == s) { species = s }
            }
        }
        if (species == Species.BLEND) {
            Text("Robusta share: ${(robustaShare * 100).roundToInt()}%", color = GrindhausColors.FoamDim, fontSize = 14.sp)
            Slider(value = robustaShare.toFloat(), onValueChange = { robustaShare = (it * 10).roundToInt() / 10.0 }, steps = 9)
        }
        if (species == Species.NOT_SURE) {
            Hint("A label saying 100% arabica settles it. Until then, caffeine is estimated as arabica with room for robusta.")
        }

        SectionLabel("Dates")
        Hint("Most bags show only one of roast date or best-by. Either is fine.")
        DateField("Roast date", roastDate) { roastDate = it }
        DateField("Best by", bestByDate) { bestByDate = it }
        DateField("Bought", purchaseDate) { purchaseDate = it }
        DateField("Opened", openedDate) { openedDate = it }
        if (initial == null && openedDate == null) Hint("Opened fills in by itself with your first brew from this bag.")

        SectionLabel("Bag")
        OutlinedTextField(
            value = bagGrams,
            onValueChange = { v -> bagGrams = v.filter { it.isDigit() }.take(5) },
            label = { Text("Net weight (g)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BAG_SIZES.forEach { (label, grams) ->
                Choice(label, bagGrams == grams.roundToInt().toString()) { bagGrams = grams.roundToInt().toString() }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Pre-ground", color = GrindhausColors.Foam, fontSize = 15.sp)
                Text("Skips the grind setting; uses the double-wall basket", color = GrindhausColors.Muted, fontSize = 12.sp)
            }
            Switch(checked = preGround, onCheckedChange = { preGround = it })
        }

        SectionLabel("Tasting")
        Field("Roaster's tasting notes, separated by commas", tags, { tags = it })
        Field("My notes", notes, { notes = it }, singleLine = false)
        Hint("Notes on each brew go in the brew log, so they build up over the life of the bag.")

        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = ::cancel, modifier = Modifier.weight(1f).height(52.dp)) {
                Text("Cancel", color = GrindhausColors.FoamDim)
            }
            Button(
                onClick = ::save,
                enabled = title.isNotBlank(),
                modifier = Modifier.weight(1f).height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GrindhausColors.Crema, contentColor = GrindhausColors.Roast),
            ) { Text("Save", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    required: Boolean = false,
    singleLine: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(if (required) "$label (required)" else label) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 3,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        modifier = Modifier.fillMaxWidth(),
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

@Composable
private fun Hint(text: String) {
    Text(text, color = GrindhausColors.Muted, fontSize = 12.sp)
}

/** A date row that opens the system date picker; the date can be cleared. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, epochDay: Long?, onChange: (Long?) -> Unit) {
    var open by rememberSaveable { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = GrindhausColors.FoamDim, fontSize = 15.sp, modifier = Modifier.weight(1f))
        TextButton(onClick = { open = true }) {
            Text(epochDay?.asEpochDayText() ?: "Set", color = if (epochDay == null) GrindhausColors.Muted else GrindhausColors.Foam)
        }
        if (epochDay != null) {
            TextButton(onClick = { onChange(null) }) { Text("Clear", color = GrindhausColors.Muted) }
        }
    }
    if (open) {
        val state = rememberDatePickerState(initialSelectedDateMillis = epochDay?.times(MILLIS_PER_DAY))
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onChange(it / MILLIS_PER_DAY) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state = state) }
    }
}
