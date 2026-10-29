package com.padraik99.grindhaus.ui.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.BeanLastBrewed
import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.data.DialPreset
import com.padraik99.grindhaus.data.GrindhausSettings
import com.padraik99.grindhaus.data.Grinder
import com.padraik99.grindhaus.data.IntakeEvent
import com.padraik99.grindhaus.data.SavedTea
import com.padraik99.grindhaus.domain.Bedtime
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.CaffeineEstimate
import com.padraik99.grindhaus.domain.CaffeineEstimates
import com.padraik99.grindhaus.domain.CaffeineModel
import com.padraik99.grindhaus.domain.Dose
import com.padraik99.grindhaus.domain.FilterOption
import com.padraik99.grindhaus.domain.GrindAdvisor
import com.padraik99.grindhaus.domain.GrindSuggestion
import com.padraik99.grindhaus.domain.MethodSpec
import com.padraik99.grindhaus.domain.Methods
import com.padraik99.grindhaus.domain.SleepPlanner
import com.padraik99.grindhaus.domain.Taste
import com.padraik99.grindhaus.domain.TeaType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Where tonight stands if a brew of [addedMg] is drunk at the given time. */
data class BedtimeOutlook(val peakMg: Double, val limitMg: Double, val bedtime: LocalTime) {
    val over: Boolean get() = peakMg > limitMg
}

/** The remembered recipe for the selected coffee + method + basket: where a new brew starts. */
data class BrewSpec(
    val bean: Bean,
    val method: MethodSpec,
    val filter: FilterOption,
    val grinder: Grinder,
    /** Null for pre-ground coffee. */
    val grind: Double?,
    val doseG: Double,
    val outG: Double,
    val caffeine: CaffeineEstimate,
    /** The last brew of this coffee with this method (or one sharing its dial-in). */
    val lastLog: BrewLog?,
    val suggestion: GrindSuggestion?,
    /** Set when auto-apply already moved the grind after the last brew. */
    val autoMovedFrom: Double?,
) {
    val ratio: Double get() = outG / doseG
}

/** A recent brew with its coffee, for Home's carousel. */
data class RecentBrew(val log: BrewLog, val bean: Bean)

data class BrewUiState(
    /** Open bags, the most recently brewed first. */
    val shelf: List<Bean> = emptyList(),
    /** Every bag, finished ones too, for editing an old brew. */
    val allBeans: List<Bean> = emptyList(),
    val methods: List<MethodSpec> = Methods.OWNED.map(Methods::spec),
    val selectedMethod: BrewMethod = BrewMethod.ESPRESSO,
    val spec: BrewSpec? = null,
    val recent: List<RecentBrew> = emptyList(),
    val inYouNowMg: Double = 0.0,
    val doses: List<Dose> = emptyList(),
    val settings: GrindhausSettings = GrindhausSettings(),
    val teas: List<SavedTea> = emptyList(),
    val grinders: List<Grinder> = emptyList(),
    /** The phone's time zone (the test clock's in tests), for showing times of day. */
    val zone: ZoneId = ZoneId.systemDefault(),
    val loaded: Boolean = false,
) {
    val autoApplyGrind: Boolean get() = settings.autoApplyGrind
}

private data class Selection(val beanId: Long?, val method: BrewMethod, val filter: FilterOption?)

@OptIn(ExperimentalCoroutinesApi::class)
class BrewViewModel(private val graph: AppGraph) : ViewModel() {

    private val beanDao = graph.db.beanDao()
    private val brewDao = graph.db.brewDao()
    private val repo = graph.brewRepository
    private val clock = graph.clock

    private val selection = MutableStateFlow(Selection(beanId = null, method = BrewMethod.ESPRESSO, filter = null))
    private val grinder = MutableStateFlow<Grinder?>(null)

    init {
        viewModelScope.launch { grinder.value = repo.defaultGrinder() }
    }

    private val active: Flow<List<Bean>> = beanDao.observeList(archived = false, query = "")
    private val archived: Flow<List<Bean>> = beanDao.observeList(archived = true, query = "")
    private val lastBrewed: Flow<List<BeanLastBrewed>> = brewDao.observeLastBrewed()

    /** Open bags, the most recently brewed first; never-brewed bags keep their own order after. */
    private val shelf: Flow<List<Bean>> = combine(active, lastBrewed) { beans, last ->
        val at = last.associate { it.beanId to it.lastBrewed }
        beans.sortedByDescending { at[it.id] ?: Long.MIN_VALUE }
    }

    private val recentLogs: Flow<List<BrewLog>> = brewDao.observeRecentLogs(limit = 6)

    /** Caffeine logged over the last 36 hours: enough to cover anything still in the body. */
    private val recentIntake: Flow<List<IntakeEvent>> =
        brewDao.observeIntakeSince(clock.millis() - 36 * 3_600_000L)

    private data class Resolved(val bean: Bean, val method: BrewMethod, val filter: FilterOption, val grinder: Grinder)

    private val resolved: Flow<Resolved?> = combine(shelf, recentLogs, selection, grinder) { list, recent, sel, g ->
        if (g == null) return@combine null
        // Nothing picked yet: start from the coffee and method brewed most recently.
        val lastUsed = recent.firstOrNull()
        val bean = list.firstOrNull { it.id == sel.beanId }
            ?: list.firstOrNull { it.id == lastUsed?.beanId }
            ?: list.firstOrNull()
            ?: return@combine null
        val method = sel.method
        val filter = sel.filter?.takeIf { it in Methods.spec(method).filters }
            ?: Methods.defaultFilter(method, bean.preGround)
        Resolved(bean, method, filter, g)
    }

    private val presetAndLast: Flow<Pair<DialPreset?, BrewLog?>> = resolved.flatMapLatest { r ->
        if (r == null) {
            flowOf(null to null)
        } else {
            combine(
                brewDao.observePreset(r.bean.id, r.method.dialKey, r.grinder.id, r.filter),
                brewDao.observeLastLogIn(r.bean.id, r.method.dialFamily, r.grinder.id, r.filter),
            ) { p, l -> p to l }
        }
    }

    /** Re-runs the numbers once a minute so "in you now" and the bedtime check keep moving. */
    private val minuteTick: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    private val allBeans: Flow<List<Bean>> = combine(active, archived) { a, b -> a + b }

    private val extras = combine(
        recentIntake, graph.settings.settings, graph.db.teaDao().observeAll(), graph.db.grinderDao().observeActive(), minuteTick,
    ) { intake, settings, teas, grinders, _ -> Extras(intake, settings, teas, grinders) }

    private data class Extras(
        val intake: List<IntakeEvent>,
        val settings: GrindhausSettings,
        val teas: List<SavedTea>,
        val grinders: List<Grinder>,
    )

    val state: StateFlow<BrewUiState> = combine(
        combine(shelf, allBeans, recentLogs) { s, all, r -> Triple(s, all, r) },
        selection, resolved, presetAndLast, extras,
    ) { (shelfList, all, recent), sel, r, (preset, last), x ->
        val doses = x.intake.map { Dose(it.mg, Instant.ofEpochMilli(it.takenAt)) }
        val byId = all.associateBy { it.id }
        BrewUiState(
            shelf = shelfList,
            allBeans = all,
            selectedMethod = r?.method ?: sel.method,
            spec = r?.let { buildSpec(it, preset, last) },
            recent = recent.mapNotNull { log -> byId[log.beanId]?.let { RecentBrew(log, it) } },
            inYouNowMg = CaffeineModel.totalAt(doses, clock.instant(), x.settings.halfLifeHours),
            doses = doses,
            settings = x.settings,
            teas = x.teas,
            grinders = x.grinders,
            zone = clock.zone,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrewUiState())

    private fun buildSpec(r: Resolved, preset: DialPreset?, last: BrewLog?): BrewSpec {
        val method = Methods.spec(r.method)
        val dose = preset?.doseG ?: method.defaultDoseG
        val out = preset?.outG ?: (dose * method.ratio)
        val grind = if (r.bean.preGround) null else preset?.grindSetting ?: method.startingGrind
        val ratio = if (method.weighsYield) out / dose else CaffeineEstimates.DEFAULT_ESPRESSO_RATIO
        val estimate = CaffeineEstimates.coffee(dose, r.method, r.bean.species, ratio, r.bean.robustaShare)

        // A suggestion only makes sense while the remembered grind is still the one that tasted off.
        val pending = last?.takeIf { grind != null && it.grindSetting == grind }
            ?.let { GrindAdvisor.suggest(it.taste, it.grindSetting, r.grinder.scale) }
        val autoMoved = last?.takeIf {
            grind != null && it.grindSetting != null && it.grindSetting != grind && it.taste != Taste.BALANCED && it.taste != null
        }?.grindSetting

        return BrewSpec(
            bean = r.bean, method = method, filter = r.filter, grinder = r.grinder,
            grind = grind, doseG = dose, outG = out, caffeine = estimate,
            lastLog = last, suggestion = pending, autoMovedFrom = autoMoved,
        )
    }

    /** Peak caffeine in the hours after bedtime if [addedMg] is drunk at [at], against the limit. */
    fun outlook(state: BrewUiState, addedMg: Double, at: Instant): BedtimeOutlook {
        val s = state.settings
        val bedtime = Bedtime.next(ZonedDateTime.now(clock), s.bedtime).toInstant()
        val peak = CaffeineModel.peakAfterBedtime(state.doses + Dose(addedMg, at), bedtime, s.halfLifeHours)
        return BedtimeOutlook(peak, SleepPlanner.limitMg(s.halfLifeHours, s.limitOverrideMg), s.bedtime)
    }

    /** Caffeine for a brew of this coffee by this method. */
    fun caffeineMg(bean: Bean, method: BrewMethod, doseG: Double, outG: Double) = repo.caffeineMg(bean, method, doseG, outG)

    fun now(): Instant = clock.instant()

    fun selectBean(id: Long) = selection.update { it.copy(beanId = id, filter = null) }
    fun selectMethod(method: BrewMethod) = selection.update { it.copy(method = method, filter = null) }
    fun selectFilter(filter: FilterOption) = selection.update { it.copy(filter = filter) }

    /** "Again" on a recent brew: the same coffee, method and basket. */
    fun startFrom(log: BrewLog) {
        selection.value = Selection(beanId = log.beanId, method = log.method, filter = log.filter)
    }

    private inline fun MutableStateFlow<Selection>.update(f: (Selection) -> Selection) {
        value = f(value)
    }

    fun setAutoApply(on: Boolean) {
        viewModelScope.launch { graph.settings.setAutoApplyGrind(on) }
    }

    /** Logs a new brew. A blank yield is "not weighed": the planned yield stands in for caffeine. */
    fun saveBrew(draft: BrewDraft, drankAt: Instant, onSaved: () -> Unit) {
        val spec = state.value.spec ?: return
        viewModelScope.launch {
            val outG = draft.outG ?: draft.plannedOutG ?: spec.outG
            val log = BrewLog(
                beanId = spec.bean.id,
                method = spec.method.method,
                grinderId = spec.grinder.id,
                filter = spec.filter,
                grindSetting = draft.grind,
                doseG = draft.doseG,
                outG = outG,
                weighed = draft.outG != null,
                shotTimeS = draft.shotTimeS,
                rating = draft.rating,
                taste = draft.taste,
                notes = draft.notes.trim(),
                caffeineMg = repo.caffeineMg(spec.bean, spec.method.method, draft.doseG, outG),
                brewedAt = drankAt.toEpochMilli(),
            )
            val autoApply = graph.settings.settings.first().autoApplyGrind
            repo.logBrew(log, spec.bean, spec.grinder, autoApply)
            onSaved()
        }
    }

    /** Saves an edited brew: its coffee, method, time and numbers. */
    fun updateBrew(log: BrewLog, bean: Bean, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.updateBrew(log, bean)
            onDone()
        }
    }

    fun deleteBrew(log: BrewLog, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.deleteBrew(log)
            onDone()
        }
    }

    /** [onLogged] gets the new record's id, so the confirmation can offer Undo. */
    fun logTea(type: TeaType, saved: SavedTea? = null, onLogged: (Long) -> Unit = {}) {
        viewModelScope.launch { onLogged(repo.logTea(type, saved)) }
    }

    fun undoTea(intakeId: Long) {
        viewModelScope.launch { repo.undoTea(intakeId) }
    }

    fun saveTea(name: String, type: TeaType) {
        viewModelScope.launch { repo.saveTea(name, type) }
    }

    fun updateTea(tea: SavedTea) {
        viewModelScope.launch { repo.updateTea(tea) }
    }

    fun removeTea(tea: SavedTea) {
        viewModelScope.launch { repo.removeTea(tea) }
    }

    companion object {
        fun factory(graph: AppGraph): ViewModelProvider.Factory = viewModelFactory {
            initializer { BrewViewModel(graph) }
        }
    }
}
