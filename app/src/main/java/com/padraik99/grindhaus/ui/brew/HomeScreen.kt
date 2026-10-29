package com.padraik99.grindhaus.ui.brew

import androidx.compose.ui.text.TextStyle
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.text.BasicText
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.SavedTea
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.CaffeineEstimates
import com.padraik99.grindhaus.domain.Methods
import com.padraik99.grindhaus.domain.TeaType
import com.padraik99.grindhaus.ui.components.RoastBar
import com.padraik99.grindhaus.ui.components.SectionLabel
import com.padraik99.grindhaus.ui.components.color
import com.padraik99.grindhaus.ui.components.panel
import com.padraik99.grindhaus.ui.components.rememberVersionName
import com.padraik99.grindhaus.ui.theme.GrindhausColors
import com.padraik99.grindhaus.ui.vault.LabelThumb
import com.padraik99.grindhaus.ui.vault.freshness
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private const val NONE = -1L

/**
 * The Home tab: what's on the shelf, how to brew it, and what was brewed lately. A new brew
 * opens as its own screen on top, so nothing here is ever mistaken for the brew in progress.
 */
@Composable
fun HomeRoute(viewModel: BrewViewModel, today: LocalDate, onAddBean: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var screen by rememberSaveable { mutableStateOf("home") }
    var editLogId by rememberSaveable { mutableLongStateOf(NONE) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun confirm(message: String, undo: (() -> Unit)? = null) {
        scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar(
                message = message,
                actionLabel = undo?.let { "Undo" },
                duration = if (undo != null) SnackbarDuration.Long else SnackbarDuration.Short,
            )
            if (result == SnackbarResult.ActionPerformed) undo?.invoke()
        }
    }

    Box(Modifier.fillMaxSize()) {
        val spec = state.spec
        val editing = state.recent.firstOrNull { it.log.id == editLogId }
        when {
            screen == "session" && spec != null -> {
                BackHandler { screen = "home" }
                SessionScreen(
                    state = state,
                    spec = spec,
                    now = viewModel::now,
                    outlook = { mg, at -> viewModel.outlook(state, mg, at) },
                    caffeine = viewModel::caffeineMg,
                    onSelectBean = viewModel::selectBean,
                    onSelectMethod = viewModel::selectMethod,
                    onSelectFilter = viewModel::selectFilter,
                    onAutoApply = viewModel::setAutoApply,
                    onClose = { screen = "home" },
                    onSave = { draft, at ->
                        val what = "${spec.bean.title} · ${spec.method.label.lowercase(Locale.US)}"
                        viewModel.saveBrew(draft, at) {
                            screen = "home"
                            confirm("Saved: $what")
                        }
                    },
                )
            }
            editLogId != NONE && editing != null -> {
                BackHandler { editLogId = NONE }
                EditBrewScreen(
                    log = editing.log,
                    bean = editing.bean,
                    choices = state.shelf,
                    allBeans = state.allBeans,
                    grinders = state.grinders,
                    zone = state.zone,
                    today = today,
                    caffeine = viewModel::caffeineMg,
                    onClose = { editLogId = NONE },
                    onSave = { log, bean -> viewModel.updateBrew(log, bean) { editLogId = NONE } },
                    onDelete = { viewModel.deleteBrew(editing.log) { editLogId = NONE } },
                )
            }
            screen == "tea" -> {
                BackHandler { screen = "home" }
                TeaScreen(
                    teas = state.teas,
                    onClose = { screen = "home" },
                    onLog = { type, saved ->
                        viewModel.logTea(type, saved) { id ->
                            screen = "home"
                            confirm(teaConfirmation(type, saved)) { viewModel.undoTea(id) }
                        }
                    },
                    onSaveTea = viewModel::saveTea,
                    onUpdateTea = viewModel::updateTea,
                    onRemoveTea = viewModel::removeTea,
                )
            }
            else -> HomeScreen(
                state = state,
                today = today,
                onTea = {
                    snackbar.currentSnackbarData?.dismiss()
                    screen = "tea"
                },
                onSelectBean = viewModel::selectBean,
                // Opening another screen clears a confirmation, so it never covers that screen's button.
                onBrew = { method ->
                    snackbar.currentSnackbarData?.dismiss()
                    viewModel.selectMethod(method)
                    screen = "session"
                },
                onAgain = { brew ->
                    snackbar.currentSnackbarData?.dismiss()
                    viewModel.startFrom(brew.log)
                    screen = "session"
                },
                onEdit = { brew ->
                    snackbar.currentSnackbarData?.dismiss()
                    editLogId = brew.log.id
                },
                onAddBean = onAddBean,
            )
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(12.dp)) { data ->
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

/** "Earl Grey logged · ~30 mg". It counts toward tonight right away, then shows in "in you" as it absorbs. */
private fun teaConfirmation(type: TeaType, saved: SavedTea?): String {
    val name = saved?.name ?: when (type) {
        TeaType.BLACK -> "Black tea"
        TeaType.GREEN -> "Green tea"
        TeaType.HERBAL -> "Herbal tea"
    }
    val mg = CaffeineEstimates.tea(type).mg
    return if (mg == 0.0) "$name logged · no caffeine" else String.format(Locale.US, "%s logged · ~%.0f mg", name, mg)
}

@Composable
fun HomeScreen(
    state: BrewUiState,
    today: LocalDate,
    onTea: () -> Unit,
    onSelectBean: (Long) -> Unit,
    onBrew: (BrewMethod) -> Unit,
    onAgain: (RecentBrew) -> Unit,
    onEdit: (RecentBrew) -> Unit,
    onAddBean: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        Header(state.inYouNowMg, onTea)
        val spec = state.spec
        if (state.loaded && state.shelf.isEmpty()) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Add a bag to the Vault and it shows up here, ready to dial in.", color = GrindhausColors.Muted, fontSize = 15.sp)
                OutlinedButton(onClick = onAddBean) { Text("Go to the Vault", color = GrindhausColors.FoamDim) }
            }
            return@Column
        }
        if (spec == null) return@Column

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("On the shelf", Modifier.weight(1f))
                Text("Recently brewed first", color = GrindhausColors.Muted, fontSize = 12.sp)
            }
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                items(state.shelf, key = { it.id }) { bean ->
                    BeanChip(bean, today, selected = bean.id == spec.bean.id) { onSelectBean(bean.id) }
                }
                item(key = "add") {
                    Box(
                        Modifier
                            .width(96.dp)
                            .height(170.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .border(2.dp, GrindhausColors.Outline, RoundedCornerShape(16.dp))
                            .clickable(onClickLabel = "Add a bag", onClick = onAddBean),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ Add\na bag", color = GrindhausColors.Muted, fontSize = 13.sp, textAlign = TextAlign.Center) }
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Brew ${spec.bean.title} as")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.methods.forEach { m ->
                    MethodTile(m.method, Modifier.weight(1f)) { onBrew(m.method) }
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SectionLabel("Recent brews", Modifier.padding(horizontal = 20.dp))
            if (state.recent.isEmpty()) {
                Text(
                    "Your brews show up here once you log one.",
                    color = GrindhausColors.Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            } else {
                LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(state.recent, key = { it.log.id }) { brew ->
                        RecentCard(brew, today, state.zone, onAgain = { onAgain(brew) }, onEdit = { onEdit(brew) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(inYouNowMg: Double, onTea: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("GRINDHAUS", color = GrindhausColors.Crema, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.6.sp)
            val version = rememberVersionName()
            if (version.isNotEmpty()) Text("v$version", color = GrindhausColors.Muted, fontSize = 10.sp, letterSpacing = 0.5.sp)
        }
        Text(
            String.format(Locale.US, "%.0f mg in you", inYouNowMg),
            color = GrindhausColors.FoamDim,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(GrindhausColors.Surface, RoundedCornerShape(50))
                .border(1.dp, GrindhausColors.Edge, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onTea, contentPadding = PaddingValues(horizontal = 12.dp)) {
            Text("+ Tea", color = GrindhausColors.FoamDim, fontSize = 13.sp)
        }
    }
}

@Composable
private fun BeanChip(bean: Bean, today: LocalDate, selected: Boolean, onClick: () -> Unit) {
    val freshness = bean.freshness(today)
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(GrindhausColors.Surface)
            .border(2.dp, if (selected) GrindhausColors.Crema else GrindhausColors.Edge, RoundedCornerShape(16.dp))
            .semantics { this.selected = selected }
            .clickable(onClickLabel = "Pick ${bean.title}", onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        LabelThumb(bean.labelPhotoPath, Modifier.fillMaxWidth().height(66.dp))
        Text(bean.title, color = GrindhausColors.Foam, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RoastBar(bean.roastLevel)
            if (bean.species == com.padraik99.grindhaus.domain.Species.DECAF) {
                Text("Decaf", color = GrindhausColors.FoamDim, fontSize = 12.sp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(7.dp).background(freshness.tone.color(), CircleShape))
            Text(freshness.label, color = freshness.tone.color(), fontSize = 12.sp, maxLines = 1)
        }
    }
}

/** A drawn sticker with its handwritten name. Tapping it starts a new brew. */
@Composable
private fun MethodTile(method: BrewMethod, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(GrindhausColors.Surface)
            .border(1.dp, GrindhausColors.Edge, RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Start a ${Methods.spec(method).label} brew", onClick = onClick)
            .padding(top = 10.dp, bottom = 6.dp, start = 4.dp, end = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        method.sticker()?.let {
            Image(
                painterResource(it),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(62.dp).fillMaxWidth(),
            )
        }
        // Shrinks to fit rather than cutting a long name short at large text sizes.
        BasicText(
            method.tileLabel(),
            style = TextStyle(fontFamily = Handwriting, fontWeight = FontWeight.SemiBold, color = GrindhausColors.Foam),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(minFontSize = 12.sp, maxFontSize = 19.sp),
        )
    }
}

private val RECENT_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a", Locale.US)
private val RECENT_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE MMM d", Locale.US)

fun whenLabel(epochMillis: Long, today: LocalDate, zone: ZoneId): String {
    val at = Instant.ofEpochMilli(epochMillis).atZone(zone)
    val day = when (at.toLocalDate()) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> at.format(RECENT_DAY)
    }
    return "$day ${at.format(RECENT_TIME).lowercase(Locale.US)}"
}

/** One recent brew: what it was and how it went, with Again and Edit. */
@Composable
private fun RecentCard(brew: RecentBrew, today: LocalDate, zone: ZoneId, onAgain: () -> Unit, onEdit: () -> Unit) {
    val log = brew.log
    val method = Methods.spec(log.method)
    Column(
        Modifier.width(212.dp).fillMaxHeight().panel(radius = 18.dp, padding = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(whenLabel(log.brewedAt, today, zone).uppercase(Locale.US), color = GrindhausColors.Muted, fontSize = 11.sp, letterSpacing = 1.2.sp)
        Text("${brew.bean.title} · ${method.label}", color = GrindhausColors.Foam, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
        Text(brewSummary(log), color = GrindhausColors.FoamDim, fontSize = 13.sp, fontFamily = FontFamily.Monospace)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            log.rating?.let { Stars(it) }
            log.taste?.let { TasteTag(it) }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onAgain,
                modifier = Modifier.weight(1f).height(42.dp).semantics { contentDescription = "Brew ${brew.bean.title} ${method.label} again" },
                contentPadding = PaddingValues(0.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GrindhausColors.Crema, contentColor = GrindhausColors.Roast),
            ) { Text("Again", fontWeight = FontWeight.Bold) }
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.weight(1f).height(42.dp).semantics { contentDescription = "Edit ${brew.bean.title} ${method.label}" },
                contentPadding = PaddingValues(0.dp),
            ) { Text("Edit", color = GrindhausColors.FoamDim) }
        }
    }
}

/** "g2 · 18→38 g · 27 s", with "~" and "not weighed" for a guessed yield. */
fun brewSummary(log: com.padraik99.grindhaus.data.BrewLog): String = buildString {
    log.grindSetting?.let { append("g").append(formatGrind(it)).append(" · ") }
    append(formatG(log.doseG)).append("→")
    if (!log.weighed) append("~")
    append(formatG(log.outG)).append(" g")
    log.shotTimeS?.let { append(" · ").append(it).append(" s") }
}
