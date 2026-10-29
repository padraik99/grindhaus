package com.padraik99.grindhaus.ui.brew

import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.CaffeineEstimates
import com.padraik99.grindhaus.domain.FilterOption
import com.padraik99.grindhaus.domain.Methods
import com.padraik99.grindhaus.domain.Species
import com.padraik99.grindhaus.domain.Taste
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class BrewHelpersTest {

    private val la: ZoneId = ZoneId.of("America/Los_Angeles")
    private fun at(s: String) = LocalDateTime.parse(s).atZone(la).toInstant()

    private val weighed = BrewLog(
        id = 7, beanId = 1, method = BrewMethod.ESPRESSO, grinderId = 1, filter = FilterOption.SINGLE_WALL,
        grindSetting = 4.0, doseG = 18.0, outG = 44.0, shotTimeS = 31, rating = 2, taste = Taste.BITTER,
        notes = "Gushed", caffeineMg = 170.0, brewedAt = at("2026-10-28T09:40").toEpochMilli(),
    )

    @Test
    fun clearingAWeighedYieldFallsBackToTheMethodRatio() {
        // The weighed 44 g was the number in doubt, so the fallback is 18 g at espresso's 1:2.
        assertEquals(36.0, weighed.plannedOutG(), 0.0)
        assertEquals(40.0, weighed.copy(outG = 40.0, weighed = false).plannedOutG(), 0.0)
    }

    @Test
    fun aPickedTimeLaterThanNowMeansLastNight() {
        val now = at("2026-10-28T00:30")
        assertEquals(at("2026-10-27T23:50"), pickedTime(23, 50, now, la))
        assertEquals(at("2026-10-28T00:10"), pickedTime(0, 10, now, la))
    }

    @Test
    fun recentBrewsSayTodayYesterdayOrTheDate() {
        val today = LocalDate.of(2026, 10, 28)
        assertEquals("Today 9:40 am", whenLabel(weighed.brewedAt, today, la))
        assertEquals("Yesterday 9:40 am", whenLabel(weighed.brewedAt, today.plusDays(1), la))
        assertEquals("Wed Oct 28 9:40 am", whenLabel(weighed.brewedAt, today.plusDays(5), la))
    }

    @Test
    fun aBrewSummaryMarksAGuessedYield() {
        assertEquals("g4 · 18→44 g · 31 s", brewSummary(weighed))
        assertEquals("g4 · 18→~36 g · 31 s", brewSummary(weighed.copy(outG = 36.0, weighed = false)))
        assertEquals("15→225 g", brewSummary(weighed.copy(method = BrewMethod.MOKA, grindSetting = null, doseG = 15.0, outG = 225.0, shotTimeS = null)))
    }

    @Test
    fun aFreddoIsAnAeroPressBrewForCaffeineAndDialIn() {
        assertEquals(
            CaffeineEstimates.coffee(15.0, BrewMethod.AEROPRESS, Species.ARABICA).mg,
            CaffeineEstimates.coffee(15.0, BrewMethod.FREDDO, Species.ARABICA).mg,
            1e-9,
        )
        assertEquals(BrewMethod.AEROPRESS, BrewMethod.FREDDO.dialKey)
        assertEquals(listOf(BrewMethod.AEROPRESS, BrewMethod.FREDDO), BrewMethod.AEROPRESS.dialFamily)
        assertEquals(listOf(BrewMethod.ESPRESSO), BrewMethod.ESPRESSO.dialFamily)
        assertEquals(Methods.OWNED, listOf(BrewMethod.ESPRESSO, BrewMethod.AEROPRESS, BrewMethod.MOKA, BrewMethod.FREDDO))
    }

    @Test
    fun theTimerRoundsToTheNearestSecond() {
        assertNull(ShotTimerState().seconds)
        assertEquals(27, ShotTimerState(27_499).seconds)
        assertEquals(28, ShotTimerState(27_500).seconds)
        val t = ShotTimerState(12_000).apply { start(); reset() }
        assertEquals(false, t.running)
        assertNull(t.seconds)
    }
}
