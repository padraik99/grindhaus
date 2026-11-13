package com.padraik99.grindhaus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SleepForecastTest {

    private val la = ZoneId.of("America/Los_Angeles")
    private fun at(dateTime: String): ZonedDateTime = ZonedDateTime.of(LocalDateTime.parse(dateTime), la)
    private fun instant(dateTime: String): Instant = at(dateTime).toInstant()

    private val espresso = CaffeineEstimates.coffee(18.0, BrewMethod.ESPRESSO, Species.ARABICA).mg
    private val tea = CaffeineEstimates.tea(TeaType.BLACK).mg

    private fun forecast(
        doses: List<Dose>,
        now: String,
        bedtime: LocalTime = LocalTime.of(23, 30),
        override: Double? = null,
    ) = SleepForecaster.forecast(doses, at(now), bedtime, 5.0, override, espresso, tea)

    @Test
    fun aMorningEspressoLeavesTonightUnderTheLimit() {
        val f = forecast(listOf(Dose(espresso, instant("2026-10-26T07:30"))), now = "2026-10-26T09:00")
        assertTrue(f.nowMg > 100)
        assertTrue(f.peakAfterBedMg < f.limitMg)
        assertFalse(f.overLimit)
        assertEquals(instant("2026-10-26T23:30"), f.bedtime.toInstant())
        assertTrue(f.limitIsDerived)
    }

    @Test
    fun theUsualBrewCutoffIsTheLastMinuteThatStillFits() {
        val doses = emptyList<Dose>()
        val f = forecast(doses, now = "2026-10-26T09:00")
        val cutoff = f.usualBrewCutoff as Cutoff.Until
        val bed = f.bedtime.toInstant()
        assertTrue(SleepPlanner.fits(doses + Dose(espresso, cutoff.at), bed, 5.0, f.limitMg))
        assertFalse(SleepPlanner.fits(doses + Dose(espresso, cutoff.at.plus(Duration.ofMinutes(2))), bed, 5.0, f.limitMg))
        assertTrue(cutoff.at.isAfter(instant("2026-10-26T09:00")))
    }

    @Test
    fun aSecondDoubleAfterAMorningOneIsAlreadyTooMuchAtTheDerivedLimit() {
        // 164 mg needs about 11.7 h to fall to 32 mg at a 5 h half-life, so two by 9 am don't fit.
        val f = forecast(listOf(Dose(espresso, instant("2026-10-26T07:30"))), now = "2026-10-26T09:00")
        assertEquals(Cutoff.Passed, f.usualBrewCutoff)
        assertTrue(f.teaCutoff is Cutoff.Until || f.teaCutoff == Cutoff.AnyTime)
    }

    @Test
    fun anEveningDoubleIsOverAndPutsAnotherOutOfReach() {
        val f = forecast(listOf(Dose(espresso, instant("2026-10-26T20:30"))), now = "2026-10-26T21:00")
        assertTrue(f.overLimit)
        assertEquals(Cutoff.Passed, f.usualBrewCutoff)
        assertEquals(Cutoff.Passed, f.teaCutoff)
    }

    @Test
    fun aMugOfTeaOnAnEmptyDayFitsRightUpToBed() {
        val f = forecast(emptyList(), now = "2026-10-26T21:00")
        assertEquals(Cutoff.AnyTime, f.teaCutoff)
    }

    @Test
    fun aRaisedLimitIsUsedAndMarkedAsYourOwn() {
        val f = forecast(emptyList(), now = "2026-10-26T09:00", override = 50.0)
        assertEquals(50.0, f.limitMg, 0.0)
        assertFalse(f.limitIsDerived)
    }

    @Test
    fun theChartStartsInTheMorningOrAtAnEarlierCup() {
        val plain = forecast(emptyList(), now = "2026-10-26T09:00")
        assertEquals(instant("2026-10-26T06:00"), plain.chartStart)
        assertEquals(instant("2026-10-27T02:30"), plain.chartEnd)

        val early = forecast(listOf(Dose(espresso, instant("2026-10-26T05:00"))), now = "2026-10-26T09:00")
        assertEquals(instant("2026-10-26T04:30"), early.chartStart)
    }

    @Test
    fun aBedtimeAfterMidnightStillChartsFromThatMorning() {
        val f = forecast(emptyList(), now = "2026-10-26T21:00", bedtime = LocalTime.of(0, 30))
        assertEquals(instant("2026-10-27T00:30"), f.bedtime.toInstant())
        assertEquals(instant("2026-10-26T06:00"), f.chartStart)
    }

    @Test
    fun theUsualBrewIsTheMiddleOfRecentRealOnes() {
        assertNull(SleepForecaster.usualBrewMg(emptyList()))
        // The decaf (5 mg) is left out.
        assertEquals(164.0, SleepForecaster.usualBrewMg(listOf(164.0, 5.0, 180.0, 150.0))!!, 0.0)
        assertEquals(157.0, SleepForecaster.usualBrewMg(listOf(164.0, 150.0))!!, 0.0)
    }

    @Test
    fun theBandRunsAnHourQuickerToTwoHoursSlower() {
        assertEquals(4.0..7.0, SleepForecaster.band(5.0))
        assertEquals(1.0..3.5, SleepForecaster.band(1.5))
    }
}
