package com.padraik99.grindhaus.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.padraik99.grindhaus.R
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.data.Grinder
import com.padraik99.grindhaus.data.LabelPhotos
import com.padraik99.grindhaus.domain.BuyAgain
import com.padraik99.grindhaus.domain.Comparison
import com.padraik99.grindhaus.domain.Methods
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.ui.brew.EditBrewScreen
import com.padraik99.grindhaus.ui.brew.Stars
import com.padraik99.grindhaus.ui.brew.TasteTag
import com.padraik99.grindhaus.ui.brew.formatG
import com.padraik99.grindhaus.ui.brew.formatGrind
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.padraik99.grindhaus.ui.components.BrandHeader
import com.padraik99.grindhaus.ui.components.FreshnessBadge
import com.padraik99.grindhaus.ui.components.RoastBar
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.components.Tag
import com.padraik99.grindhaus.ui.components.asEpochDayText
import com.padraik99.grindhaus.ui.theme.GrindhausColors

private const val NONE = -1L
private const val NEW = 0L

/** The Vault tab: list, bean page and editor, with the system back gesture between them. */
@Composable
fun VaultRoute(viewModel: VaultViewModel) {
    var detailId by rememberSaveable { mutableLongStateOf(NONE) }
    var editId by rememberSaveable { mutableLongStateOf(NONE) }
    var editLogId by rememberSaveable { mutableLongStateOf(NONE) }

    when {
        editId != NONE -> {
            BackHandler { editId = NONE }
            val existing by remember(editId) { viewModel.bean(editId) }.collectAsState(initial = null)
            if (editId == NEW || existing != null) {
                val context = LocalContext.current
                val scanner = remember(context) { viewModel.scannerFor(context) }
                BeanEditor(
                    initial = if (editId == NEW) null else existing,
                    today = viewModel.today(),
                    scanner = if (editId == NEW) scanner else null,
                    lookup = viewModel::lookupBarcode,
                    onCancel = { editId = NONE },
                    onSave = { bean ->
                        viewModel.save(bean) { id ->
                            editId = NONE
                            detailId = id
                        }
                    },
                )
            }
        }
        detailId != NONE -> {
            BackHandler { detailId = NONE }
            val bean by remember(detailId) { viewModel.bean(detailId) }.collectAsState(initial = null)
            val logs by remember(detailId) { viewModel.logs(detailId) }.collectAsState(initial = emptyList())
            val gramsUsed by remember(detailId) { viewModel.gramsUsed(detailId) }.collectAsState(initial = 0.0)
            val grinders by viewModel.grinders.collectAsStateWithLifecycle()
            val favourite by remember(detailId) { viewModel.favouriteOtherThan(detailId) }.collectAsState(initial = null)
            val editingLog = logs.firstOrNull { it.id == editLogId }
            val b = bean
            if (editLogId != NONE && editingLog != null && b != null) {
                BackHandler { editLogId = NONE }
                val shelf by viewModel.shelf.collectAsStateWithLifecycle()
                val all by viewModel.allBeans.collectAsStateWithLifecycle()
                EditBrewScreen(
                    log = editingLog,
                    bean = b,
                    choices = shelf,
                    allBeans = all,
                    grinders = grinders,
                    zone = viewModel.zone(),
                    today = viewModel.today(),
                    caffeine = viewModel::caffeineMg,
                    onClose = { editLogId = NONE },
                    onSave = { log, bean -> viewModel.updateBrew(log, bean) { editLogId = NONE } },
                    onDelete = {
                        viewModel.deleteLog(editingLog)
                        editLogId = NONE
                    },
                )
                return
            }
            bean?.let {
                BeanDetail(
                    bean = it,
                    card = BeanCard(it, it.freshness(viewModel.today())),
                    logs = logs,
                    gramsUsed = gramsUsed,
                    grinders = grinders,
                    onBack = { detailId = NONE },
                    onEdit = { editId = it.id },
                    favourite = favourite,
                    onRestore = { viewModel.setArchived(it.id, false) },
                    onFinish = { buy, other, outcome -> viewModel.finishBag(it.id, buy, other, outcome) },
                    onUpdateVerdict = { buy, other, outcome -> viewModel.updateVerdict(it.id, buy, other, outcome) },
                    onDeleteLog = viewModel::deleteLog,
                    onEditLog = { log -> editLogId = log.id },
                )
            }
        }
        else -> {
            val cards by viewModel.cards.collectAsStateWithLifecycle()
            val query by viewModel.query.collectAsStateWithLifecycle()
            val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
            val active by viewModel.activeCount.collectAsStateWithLifecycle()
            val archived by viewModel.archivedCount.collectAsStateWithLifecycle()
            val sort by viewModel.sort.collectAsStateWithLifecycle()
            VaultList(
                cards = cards,
                sort = sort,
                onSort = { viewModel.sort.value = it },
                query = query,
                showArchived = showArchived,
                activeCount = active,
                archivedCount = archived,
                onQuery = { viewModel.query.value = it },
                onShowArchived = { viewModel.showArchived.value = it },
                onOpen = { detailId = it },
                onNew = { editId = NEW },
            )
        }
    }
}

@Composable
fun VaultList(
    cards: List<BeanCard>,
    sort: VaultSort,
    onSort: (VaultSort) -> Unit,
    query: String,
    showArchived: Boolean,
    activeCount: Int,
    archivedCount: Int,
    onQuery: (String) -> Unit,
    onShowArchived: (Boolean) -> Unit,
    onOpen: (Long) -> Unit,
    onNew: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { BrandHeader("Vault") }
            item {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQuery,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Search beans, roasters, origins") },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    VaultChip("Active · $activeCount", selected = !showArchived) { onShowArchived(false) }
                    VaultChip("Archived · $archivedCount", selected = showArchived) { onShowArchived(true) }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Sort", color = GrindhausColors.Muted, fontSize = 13.sp)
                    VaultSort.entries.forEach { s -> VaultChip(s.label, selected = sort == s) { onSort(s) } }
                }
            }
            if (cards.isEmpty()) {
                item {
                    Text(
                        when {
                            query.isNotBlank() -> "Nothing matches \"$query\"."
                            showArchived -> "Finished bags land here when you archive them."
                            else -> "No beans yet. Tap New coffee to add your first bag."
                        },
                        color = GrindhausColors.Muted,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            items(cards, key = { it.bean.id }) { card -> BeanRow(card) { onOpen(card.bean.id) } }
        }
        // A plain button with a visible label, so screen readers announce "New coffee".
        FloatingActionButton(
            onClick = onNew,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = GrindhausColors.Crema,
            contentColor = GrindhausColors.Roast,
        ) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(painterResource(R.drawable.ic_add), contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New coffee", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun VaultChip(text: String, selected: Boolean, onClick: () -> Unit) {
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BeanRow(card: BeanCard, onClick: () -> Unit) {
    val bean = card.bean
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(GrindhausColors.Surface)
            .clickable(onClickLabel = "Open ${bean.title}", onClick = onClick)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        LabelThumb(bean.labelPhotoPath, Modifier.size(width = 76.dp, height = 96.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(bean.title, color = GrindhausColors.Foam, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            val sub = listOfNotNull(bean.roaster.ifBlank { null }, bean.origin?.ifBlank { null }).joinToString(" · ")
            if (sub.isNotEmpty()) Text(sub, color = GrindhausColors.Muted, fontSize = 13.sp)
            FreshnessBadge(card.freshness)
            RoastBar(bean.roastLevel)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (card.isFavourite) Tag("★ Favourite")
                bean.buyAgain?.let { Tag(it.label) }
                Tag(bean.species.label())
                if (bean.preGround) Tag("Pre-ground")
            }
        }
    }
}

@Composable
fun LabelThumb(photo: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier.clip(RoundedCornerShape(12.dp)).background(GrindhausColors.Line),
        contentAlignment = Alignment.Center,
    ) {
        if (photo != null) {
            AsyncImage(
                model = LabelPhotos.file(context, photo),
                contentDescription = "Label photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text("No photo", color = GrindhausColors.Muted, fontSize = 10.sp)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeanDetail(
    bean: Bean,
    card: BeanCard,
    logs: List<BrewLog>,
    gramsUsed: Double,
    grinders: List<Grinder>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    favourite: Bean?,
    onRestore: () -> Unit,
    onFinish: (BuyAgain?, Long?, Comparison?) -> Unit,
    onUpdateVerdict: (BuyAgain?, Long?, Comparison?) -> Unit,
    onDeleteLog: (BrewLog) -> Unit,
    onEditLog: (BrewLog) -> Unit = {},
) {
    var verdictDialog by rememberSaveable { mutableStateOf(false) }
    if (verdictDialog) {
        VerdictDialog(
            bean = bean,
            favourite = favourite,
            finishing = !bean.archived,
            onDismiss = { verdictDialog = false },
            onConfirm = { buy, other, outcome ->
                if (bean.archived) onUpdateVerdict(buy, other, outcome) else onFinish(buy, other, outcome)
                verdictDialog = false
            },
        )
    }
    var confirmDelete by remember { mutableStateOf<BrewLog?>(null) }
    confirmDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this brew?") },
            text = { Text("It comes off the dial history and out of today's caffeine.") },
            confirmButton = {
                TextButton(onClick = {
                    onDeleteLog(log)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Keep") } },
        )
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(210.dp)) {
            LabelThumb(bean.labelPhotoPath, Modifier.fillMaxSize())
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(12.dp).background(GrindhausColors.Roast, RoundedCornerShape(50)),
            ) { Icon(painterResource(R.drawable.ic_back), contentDescription = "Back to Vault", tint = GrindhausColors.Foam) }
            IconButton(
                onClick = onEdit,
                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)
                    .background(GrindhausColors.Roast, RoundedCornerShape(50)),
            ) { Icon(painterResource(R.drawable.ic_edit), contentDescription = "Edit bean", tint = GrindhausColors.Foam) }
        }
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(bean.title, color = GrindhausColors.Foam, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                val sub = listOfNotNull(bean.roaster.ifBlank { null }, bean.origin?.ifBlank { null }).joinToString(" · ")
                if (sub.isNotEmpty()) Text(sub, color = GrindhausColors.Muted, fontSize = 14.sp)
                FreshnessBadge(card.freshness)
                freshnessBasis(bean)?.let { Text(it, color = GrindhausColors.Muted, fontSize = 12.sp) }
                RoastBar(bean.roastLevel)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("Roasted", bean.roastDate?.asEpochDayText() ?: "Not on bag", Modifier.weight(1f))
                Fact("Best by", bean.bestByDate?.asEpochDayText() ?: "—", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Fact("Opened", bean.openedDate?.asEpochDayText() ?: "Not yet", Modifier.weight(1f))
                Fact("Bag", bagText(bean.bagGrams, gramsUsed, logs), Modifier.weight(1f))
            }
            Fact("Species", speciesDetail(bean), Modifier.fillMaxWidth())
            if (bean.tags.isNotEmpty()) {
                SectionLabel("Roaster's tasting notes")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bean.tags.forEach { Tag(it) }
                }
            }
            if (bean.notes.isNotBlank()) {
                SectionLabel("My notes")
                Text(bean.notes, color = GrindhausColors.FoamDim, fontSize = 14.sp)
            }
            DialHistory(logs, grinders, onClick = onEditLog, onLongPress = { confirmDelete = it })
            Spacer(Modifier.height(8.dp))
            if (bean.archived) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        bean.buyAgain?.label ?: "No verdict yet",
                        color = GrindhausColors.FoamDim,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { verdictDialog = true }) { Text("Change", color = GrindhausColors.Crema) }
                }
                OutlinedButton(onClick = onRestore, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Restore to active", color = GrindhausColors.FoamDim)
                }
            } else {
                OutlinedButton(onClick = { verdictDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Text("Finished this bag", color = GrindhausColors.FoamDim)
                }
            }
        }
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier.background(GrindhausColors.Surface, RoundedCornerShape(14.dp)).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SectionLabel(label)
        // Numbers read like gauges in monospace; words read better in the normal face.
        val numeric = value.firstOrNull()?.isDigit() == true
        Text(
            value,
            color = GrindhausColors.Foam,
            fontSize = 15.sp,
            fontFamily = if (numeric) FontFamily.Monospace else FontFamily.Default,
        )
    }
}

/** One line saying what the badge is counting, so a best-by-only bag isn't a mystery. */
private fun freshnessBasis(bean: Bean): String? = when {
    bean.roastDate != null -> "Counted from the roast date."
    bean.openedDate != null -> "No roast date on the bag, so this counts days since opening."
    else -> "Freshness starts counting from your first brew of this bag."
}

fun Species.label(): String = when (this) {
    Species.ARABICA -> "Arabica"
    Species.ROBUSTA -> "Robusta"
    Species.BLEND -> "Blend"
    Species.DECAF -> "Decaf"
    Species.NOT_SURE -> "Species not sure"
}

private fun speciesDetail(bean: Bean): String = when (bean.species) {
    Species.BLEND -> "Blend · ${((bean.robustaShare ?: 0.0) * 100).toInt()}% robusta"
    Species.NOT_SURE -> "Not sure · estimated as arabica"
    Species.DECAF -> "Decaf · caffeine counted at about 3%"
    else -> bean.species.label()
}

/** Net weight, and roughly how much is left once brews have been logged. */
private fun bagText(bagGrams: Double?, gramsUsed: Double, logs: List<BrewLog>): String {
    if (bagGrams == null) return "—"
    if (logs.isEmpty()) return "${bagGrams.toInt()} g"
    val left = (bagGrams - gramsUsed).coerceAtLeast(0.0)
    val typical = logs.take(5).map { it.doseG }.average()
    val brews = if (typical > 0) (left / typical).toInt() else 0
    return "~${left.toInt()} g · ~$brews brews"
}

/** Every brew of this bag, newest first, with the best one pinned on top. Tap edits, long-press deletes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DialHistory(
    logs: List<BrewLog>,
    grinders: List<Grinder>,
    onClick: (BrewLog) -> Unit,
    onLongPress: (BrewLog) -> Unit,
) {
    SectionLabel("Dial history")
    if (logs.isEmpty()) {
        Text("Brews you log on the Brew tab appear here with their ratings.", color = GrindhausColors.Muted, fontSize = 13.sp)
        return
    }
    logs.filter { it.rating != null }.maxWithOrNull(compareBy<BrewLog> { it.rating }.thenBy { it.brewedAt })?.let { best ->
        Column(
            Modifier.fillMaxWidth().border(1.dp, GrindhausColors.Crema, RoundedCornerShape(16.dp)).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("BEST SO FAR", color = GrindhausColors.Crema, fontSize = 11.sp, letterSpacing = 1.5.sp)
            Text(describe(best, grinders), color = GrindhausColors.Foam, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            Stars(best.rating ?: 0)
        }
    }
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(GrindhausColors.Surface),
    ) {
        logs.forEachIndexed { i, log ->
            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(GrindhausColors.SurfaceHigh))
            Column(
                Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClickLabel = "Edit this brew",
                        onLongClickLabel = "Delete this brew",
                        onLongClick = { onLongPress(log) },
                        onClick = { onClick(log) },
                    )
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(HISTORY_DATE.format(Instant.ofEpochMilli(log.brewedAt).atZone(ZoneId.systemDefault())), color = GrindhausColors.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
                    log.taste?.let { TasteTag(it) }
                }
                Text(describe(log, grinders), color = GrindhausColors.Foam, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                log.rating?.let { Stars(it) }
                if (log.notes.isNotBlank()) Text(log.notes, color = GrindhausColors.FoamDim, fontSize = 13.sp)
            }
        }
    }
    Text("Tap a brew to edit it. Long-press to delete.", color = GrindhausColors.Muted, fontSize = 12.sp)
}

private val HISTORY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d · HH:mm", Locale.US)

private fun describe(log: BrewLog, grinders: List<Grinder>): String = buildString {
    append(Methods.spec(log.method).label)
    log.grindSetting?.let { g ->
        append(" · grind ").append(formatGrind(g))
        if (grinders.size > 1) grinders.firstOrNull { it.id == log.grinderId }?.let { append(" (").append(it.name).append(")") }
    }
    append(" · ").append(formatG(log.doseG)).append("→")
    if (!log.weighed) append("~")
    append(formatG(log.outG)).append(" g")
    log.shotTimeS?.let { append(" · ").append(it).append(" s") }
    if (!log.weighed) append(" · not weighed")
}
