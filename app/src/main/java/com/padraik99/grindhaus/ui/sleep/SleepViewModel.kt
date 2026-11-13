package com.padraik99.grindhaus.ui.sleep

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.padraik99.grindhaus.AppGraph
import com.padraik99.grindhaus.data.GrindhausSettings
import com.padraik99.grindhaus.data.IntakeEvent
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.CaffeineEstimates
import com.padraik99.grindhaus.domain.CaffeineModel
import com.padraik99.grindhaus.domain.Dose
import com.padraik99.grindhaus.domain.SleepForecast
import com.padraik99.grindhaus.domain.SleepForecaster
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.domain.TeaType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

data class SleepUiState(
    val loaded: Boolean = false,
    val settings: GrindhausSettings = GrindhausSettings(),
    val doses: List<Dose> = emptyList(),
    /** What's been had since the chart's start, oldest first. */
    val today: List<IntakeEvent> = emptyList(),
    val forecast: SleepForecast? = null,
    val teaType: TeaType = TeaType.BLACK,
    val zone: ZoneId = ZoneId.systemDefault(),
)

class SleepViewModel(private val graph: AppGraph) : ViewModel() {

    private val clock = graph.clock
    private val repo = graph.brewRepository

    /** Re-runs the numbers once a minute so "now" and the cutoffs keep moving. */
    private val minuteTick = flow {
        while (true) {
            emit(Unit)
            delay(60_000)
        }
    }

    /** 36 hours covers anything still in the body. */
    private val intake = graph.db.brewDao().observeIntakeSince(clock.millis() - 36 * 3_600_000L)

    val state: StateFlow<SleepUiState> = combine(
        intake,
        graph.settings.settings,
        graph.db.brewDao().observeRecentLogs(limit = 20),
        minuteTick,
    ) { events, settings, logs, _ ->
        val doses = events.map { Dose(it.mg, Instant.ofEpochMilli(it.takenAt)) }
        val usual = SleepForecaster.usualBrewMg(logs.map { it.caffeineMg }) ?: FALLBACK_BREW_MG
        // Herbal has no caffeine, so the cutoff line uses black tea instead.
        val teaType = settings.defaultTea.takeIf { CaffeineEstimates.teaDefaultMg(it) > 0 } ?: TeaType.BLACK
        val forecast = SleepForecaster.forecast(
            doses = doses,
            now = ZonedDateTime.now(clock),
            bedtime = settings.bedtime,
            halfLifeHours = settings.halfLifeHours,
            limitOverrideMg = settings.limitOverrideMg,
            usualBrewMg = usual,
            teaMg = CaffeineEstimates.tea(teaType).mg,
        )
        SleepUiState(
            loaded = true,
            settings = settings,
            doses = doses,
            today = events.filter { it.takenAt >= forecast.chartStart.toEpochMilli() },
            forecast = forecast,
            teaType = teaType,
            zone = clock.zone,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SleepUiState())

    fun setBedtime(time: LocalTime) {
        viewModelScope.launch { graph.settings.setBedtime(time) }
    }

    fun setHalfLife(hours: Double) {
        val r = CaffeineModel.HALF_LIFE_RANGE_HOURS
        viewModelScope.launch { graph.settings.setHalfLife(hours.coerceIn(r)) }
    }

    fun setLimit(mg: Double?) {
        viewModelScope.launch { graph.settings.setLimitOverride(mg?.takeIf { it > 0 }) }
    }

    fun logOther(mg: Double) {
        if (mg <= 0) return
        viewModelScope.launch { repo.logOther(mg) }
    }

    fun remove(event: IntakeEvent) {
        if (event.brewLogId != null) return
        viewModelScope.launch { repo.removeStandaloneIntake(event.id) }
    }

    companion object {
        /** Before any brew is logged: an 18 g arabica espresso at 1:2. */
        val FALLBACK_BREW_MG = CaffeineEstimates.coffee(18.0, BrewMethod.ESPRESSO, Species.ARABICA).mg

        fun factory(graph: AppGraph): ViewModelProvider.Factory = viewModelFactory {
            initializer { SleepViewModel(graph) }
        }
    }
}
