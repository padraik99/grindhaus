package com.padraik99.grindhaus.ui.brew

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.data.DialPreset
import com.padraik99.grindhaus.data.GrindhausSettings
import com.padraik99.grindhaus.data.Grinder
import com.padraik99.grindhaus.data.IntakeEvent
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
import java.time.ZonedDateTime

/** "This brew would leave about [peakMg] at [bedtime]; your limit is [limitMg]." */
data class BedtimeWarning(val peakMg: Double, val limitMg: Double, val bedtime: LocalTime)

/** What the Brew Bar shows for the current bean + method + filter. */
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
    val lastLog: BrewLog?,
    val suggestion: GrindSuggestion?,
    /** Set when auto-apply already moved the grind after the last brew. */
    val autoMovedFrom: Double?,
    val warning: BedtimeWarning?,
) {
    val ratio: Double get() = outG / doseG
}

data class BrewUiState(
    val beans: List<Bean> = emptyList(),
    val methods: List<MethodSpec> = Methods.OWNED.map(Methods::spec),
    val selectedMethod: BrewMethod = BrewMethod.ESPRESSO,
    val spec: BrewSpec? = null,
    val inYouNowMg: Double = 0.0,
    val autoApplyGrind: Boolean = false,
    val loaded: Boolean = false,
)

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

    private val beans: Flow<List<Bean>> = beanDao.observeList(archived = false, query = "")

    /** Caffeine logged over the last 36 hours: enough to cover anything still in the body. */
    private val recentIntake: Flow<List<IntakeEvent>> =
        brewDao.observeIntakeSince(clock.millis() - 36 * 3_600_000L)

    private data class Resolved(val bean: Bean, val method: BrewMethod, val filter: FilterOption, val grinder: Grinder)

    private val resolved: Flow<Resolved?> = combine(beans, selection, grinder) { list, sel, g ->
        if (g == null) return@combine null
        val bean = list.firstOrNull { it.id == sel.beanId } ?: list.firstOrNull() ?: return@combine null
        val filter = sel.filter?.takeIf { it in Methods.spec(sel.method).filters }
            ?: Methods.defaultFilter(sel.method, bean.preGround)
        Resolved(bean, sel.method, filter, g)
    }

    private val presetAndLast: Flow<Pair<DialPreset?, BrewLog?>> = resolved.flatMapLatest { r ->
        if (r == null) flowOf(null to null)
        else combine(
            brewDao.observePreset(r.bean.id, r.method, r.grinder.id, r.filter),
            brewDao.observeLastLog(r.bean.id, r.method, r.grinder.id, r.filter),
        ) { p, l -> p to l }
    }

    /** Re-runs the numbers once a minute so "in you now" and the bedtime check keep moving. */
    private val minuteTick: Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    val state: StateFlow<BrewUiState> = combine(
        beans, selection, resolved, presetAndLast,
        combine(recentIntake, graph.settings.settings, minuteTick) { i, s, _ -> i to s },
    ) { beanList, sel, r, (preset, last), (intake, settings) ->
        val doses = intake.map { Dose(it.mg, Instant.ofEpochMilli(it.takenAt)) }
        val now = clock.instant()
        BrewUiState(
            beans = beanList,
            selectedMethod = sel.method,
            spec = r?.let { buildSpec(it, preset, last, doses, settings) },
            inYouNowMg = CaffeineModel.totalAt(doses, now, settings.halfLifeHours),
            autoApplyGrind = settings.autoApplyGrind,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BrewUiState())

    private fun buildSpec(
        r: Resolved,
        preset: DialPreset?,
        last: BrewLog?,
        doses: List<Dose>,
        settings: GrindhausSettings,
    ): BrewSpec {
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
            warning = bedtimeWarning(doses + Dose(estimate.mg, clock.instant()), settings),
        )
    }

    private fun bedtimeWarning(doses: List<Dose>, settings: GrindhausSettings): BedtimeWarning? {
        val bedtime = Bedtime.next(ZonedDateTime.now(clock), settings.bedtime).toInstant()
        val limit = SleepPlanner.limitMg(settings.halfLifeHours, settings.limitOverrideMg)
        val peak = CaffeineModel.peakAfterBedtime(doses, bedtime, settings.halfLifeHours)
        return if (peak > limit) BedtimeWarning(peak, limit, settings.bedtime) else null
    }

    fun selectBean(id: Long) = selection.update { it.copy(beanId = id, filter = null) }
    fun selectMethod(method: BrewMethod) = selection.update { it.copy(method = method, filter = null) }
    fun selectFilter(filter: FilterOption) = selection.update { it.copy(filter = filter) }

    private inline fun MutableStateFlow<Selection>.update(f: (Selection) -> Selection) {
        value = f(value)
    }

    fun acceptSuggestion() {
        val spec = state.value.spec ?: return
        val grind = spec.suggestion?.grind ?: return
        viewModelScope.launch {
            val preset = brewDao.preset(spec.bean.id, spec.method.method, spec.grinder.id, spec.filter)
                ?: DialPreset(
                    beanId = spec.bean.id, method = spec.method.method, grinderId = spec.grinder.id, filter = spec.filter,
                    grindSetting = spec.grind, doseG = spec.doseG, outG = spec.outG, updatedAt = clock.millis(),
                )
            repo.setPresetGrind(preset, grind)
        }
    }

    /** Keeping the current grind is recorded by logging the next brew; nothing to store now. */
    fun setAutoApply(on: Boolean) {
        viewModelScope.launch { graph.settings.setAutoApplyGrind(on) }
    }

    fun logTea(type: TeaType) {
        viewModelScope.launch { repo.logTea(type) }
    }

    fun saveBrew(
        grind: Double?,
        doseG: Double,
        outG: Double,
        shotTimeS: Int?,
        rating: Int?,
        taste: Taste?,
        notes: String,
        onSaved: () -> Unit,
    ) {
        val spec = state.value.spec ?: return
        viewModelScope.launch {
            val log = BrewLog(
                beanId = spec.bean.id,
                method = spec.method.method,
                grinderId = spec.grinder.id,
                filter = spec.filter,
                grindSetting = grind,
                doseG = doseG,
                outG = outG,
                shotTimeS = shotTimeS,
                rating = rating,
                taste = taste,
                notes = notes.trim(),
                caffeineMg = repo.caffeineMg(spec.bean, spec.method.method, doseG, outG),
                brewedAt = clock.millis(),
            )
            val autoApply = graph.settings.settings.first().autoApplyGrind
            repo.logBrew(log, spec.bean, spec.grinder, autoApply)
            onSaved()
        }
    }

    companion object {
        fun factory(graph: AppGraph): ViewModelProvider.Factory = viewModelFactory {
            initializer { BrewViewModel(graph) }
        }
    }
}
