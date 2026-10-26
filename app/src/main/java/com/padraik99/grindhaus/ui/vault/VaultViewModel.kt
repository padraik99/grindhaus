package com.padraik99.grindhaus.ui.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import android.content.Context
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.data.BarcodeScanner
import com.padraik99.grindhaus.data.LookupOutcome
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.BeanRating
import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.data.Grinder
import com.padraik99.grindhaus.domain.BuyAgain
import com.padraik99.grindhaus.domain.Comparison
import com.padraik99.grindhaus.domain.Freshness
import com.padraik99.grindhaus.domain.FreshnessRules
import com.padraik99.grindhaus.ui.brew.BrewDraft
import com.padraik99.grindhaus.ui.brew.plannedOutG
import com.padraik99.grindhaus.ui.brew.withDraft
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BeanCard(val bean: Bean, val freshness: Freshness, val isFavourite: Boolean = false)

enum class VaultSort(val label: String) { NEWEST("Newest"), FAVORITES("Favorites") }

/**
 * Favorites order: "buy again?" first, then the comparison ranking, then how good
 * the bag's best three shots were, then newest. Shot stars only break ties because
 * they mostly measure the dial-in, not the bean.
 */
fun favouritesOrder(cards: List<BeanCard>, shotScore: Map<Long, Double>): List<BeanCard> {
    fun verdictRank(b: Bean) = when (b.buyAgain) {
        BuyAgain.YES -> 0
        BuyAgain.MAYBE -> 1
        null -> 2
        BuyAgain.NO -> 3
    }
    return cards.sortedWith(
        compareBy<BeanCard> { verdictRank(it.bean) }
            .thenByDescending { it.bean.rankScore }
            .thenByDescending { shotScore[it.bean.id] ?: -1.0 }
            .thenByDescending { it.bean.id },
    )
}

/** Average of each bag's three best-rated brews. */
fun topThreeAverages(ratings: List<BeanRating>): Map<Long, Double> =
    ratings.groupBy { it.beanId }.mapValues { (_, r) -> r.map { it.rating }.sortedDescending().take(3).average() }

fun Bean.freshness(today: LocalDate): Freshness = FreshnessRules.of(
    today = today,
    roastDate = roastDate?.let(LocalDate::ofEpochDay),
    bestByDate = bestByDate?.let(LocalDate::ofEpochDay),
    openedDate = openedDate?.let(LocalDate::ofEpochDay),
    preGround = preGround,
)

@OptIn(ExperimentalCoroutinesApi::class)
class VaultViewModel(private val graph: AppGraph) : ViewModel() {

    private val dao = graph.db.beanDao()
    private val brews = graph.db.brewDao()

    fun today(): LocalDate = LocalDate.now(graph.clock)

    val query = MutableStateFlow("")
    val showArchived = MutableStateFlow(false)
    val sort = MutableStateFlow(VaultSort.NEWEST)

    val cards: StateFlow<List<BeanCard>> =
        combine(query, showArchived) { q, archived -> q.trim() to archived }
            .flatMapLatest { (q, archived) -> dao.observeList(archived, q) }
            .combine(combine(sort, brews.observeRatings(), dao.observeFavourite(excludeId = -1)) { s, r, f -> Triple(s, r, f) }) {
                    beans, (sortBy, ratings, favourite) ->
                val cards = beans.map { BeanCard(it, it.freshness(today()), isFavourite = it.id == favourite?.id) }
                if (sortBy == VaultSort.FAVORITES) favouritesOrder(cards, topThreeAverages(ratings)) else cards
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val activeCount: StateFlow<Int> =
        dao.observeCount(false).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val archivedCount: StateFlow<Int> =
        dao.observeCount(true).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val grinders: StateFlow<List<Grinder>> =
        graph.db.grinderDao().observeActive().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bean(id: Long): Flow<Bean?> = dao.observe(id)
    fun logs(beanId: Long): Flow<List<BrewLog>> = brews.observeLogsForBean(beanId)
    fun gramsUsed(beanId: Long): Flow<Double> = brews.observeGramsUsed(beanId)

    fun save(bean: Bean, onSaved: (Long) -> Unit) {
        viewModelScope.launch {
            val id = if (bean.id == 0L) dao.insert(bean) else bean.id.also { dao.update(bean) }
            onSaved(id)
        }
    }

    fun setArchived(id: Long, archived: Boolean) {
        viewModelScope.launch { dao.setArchived(id, archived) }
    }

    /** The bag a finished one gets compared with, if there is one yet. */
    fun favouriteOtherThan(beanId: Long): Flow<Bean?> = dao.observeFavourite(beanId)

    fun finishBag(beanId: Long, buyAgain: BuyAgain?, comparedWith: Long?, outcome: Comparison?) {
        viewModelScope.launch { graph.brewRepository.finishBag(beanId, buyAgain, comparedWith, outcome) }
    }

    fun updateVerdict(beanId: Long, buyAgain: BuyAgain?, comparedWith: Long?, outcome: Comparison?) {
        viewModelScope.launch { graph.brewRepository.updateVerdict(beanId, buyAgain, comparedWith, outcome) }
    }

    /** Corrects a logged brew; caffeine and the intake record are recalculated. */
    fun updateBrew(original: BrewLog, draft: BrewDraft, bean: Bean, onDone: () -> Unit) {
        viewModelScope.launch {
            graph.brewRepository.updateBrew(original.withDraft(draft, original.plannedOutG()), bean)
            onDone()
        }
    }

    fun scannerFor(context: Context): BarcodeScanner = graph.scannerFor(context)

    suspend fun lookupBarcode(barcode: String): LookupOutcome = graph.barcodeLookup.lookup(barcode)

    fun deleteLog(log: BrewLog) {
        viewModelScope.launch { graph.brewRepository.deleteBrew(log) }
    }

    companion object {
        fun factory(graph: AppGraph): ViewModelProvider.Factory = viewModelFactory {
            initializer { VaultViewModel(graph) }
        }
    }
}
