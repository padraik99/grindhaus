package com.padraik99.grindhaus.ui.vault

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.padraik99.grindhaus.data.LabelPhotos
import com.padraik99.grindhaus.domain.Species
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

    when {
        editId != NONE -> {
            BackHandler { editId = NONE }
            val existing by remember(editId) { viewModel.bean(editId) }.collectAsState(initial = null)
            if (editId == NEW || existing != null) {
                BeanEditor(
                    initial = if (editId == NEW) null else existing,
                    today = viewModel.today(),
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
            bean?.let {
                BeanDetail(
                    bean = it,
                    card = BeanCard(it, it.freshness(viewModel.today())),
                    onBack = { detailId = NONE },
                    onEdit = { editId = it.id },
                    onArchive = { archived -> viewModel.setArchived(it.id, archived) },
                )
            }
        }
        else -> {
            val cards by viewModel.cards.collectAsStateWithLifecycle()
            val query by viewModel.query.collectAsStateWithLifecycle()
            val showArchived by viewModel.showArchived.collectAsStateWithLifecycle()
            val active by viewModel.activeCount.collectAsStateWithLifecycle()
            val archived by viewModel.archivedCount.collectAsStateWithLifecycle()
            VaultList(
                cards = cards,
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
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onArchive: (Boolean) -> Unit,
) {
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
                Fact("Bag", bean.bagGrams?.let { "${it.toInt()} g" } ?: "—", Modifier.weight(1f))
            }
            Fact("Species", speciesDetail(bean), Modifier.fillMaxWidth())
            if (bean.tags.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    bean.tags.forEach { Tag(it) }
                }
            }
            if (bean.notes.isNotBlank()) Text(bean.notes, color = GrindhausColors.FoamDim, fontSize = 14.sp)
            SectionLabel("Dial history")
            Text("Shots and ratings appear here once Brew Bar arrives (build step 6).", color = GrindhausColors.Muted, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { onArchive(!bean.archived) }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text(if (bean.archived) "Restore to active" else "Archive bean", color = GrindhausColors.FoamDim)
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
    else -> bean.species.label()
}
