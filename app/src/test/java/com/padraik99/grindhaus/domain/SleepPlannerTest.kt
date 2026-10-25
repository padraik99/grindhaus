package com.padraik99.grindhaus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Patrick's day: nothing before 9:00, bed at 23:30, default 5 h half-life. */
class SleepPlannerTest {

    private val la = ZoneId.of("America/Los_Angeles")
    private fun at(time: String): Instant =
        ZonedDateTime.of(LocalDateTime.parse("2026-10-24T$time"), la).toInstant()

    private val bedtime = at("23:30")
    private val halfLife = 5.0
    private val limit = SleepPlanner.limitMg(halfLife)
    private val double = CaffeineEstimates.coffee(18.0, BrewMethod.ESPRESSO, Species.ARABICA).mg
    private val morning = listOf(Dose(double, at("09:00")))

    @Test
    fun `the limit is derived from the half-life unless overridden`() {
        assertEquals(32.5, limit, 0.1)
        assertEquals(40.0, SleepPlanner.limitMg(halfLife, overrideMg = 40.0), 0.0)
    }

    @Test
    fun `a 9 o'clock arabica double fits, leaving about 23 mg at bedtime`() {
        assertEquals(22.6, CaffeineModel.peakAfterBedtime(morning, bedtime, halfLife), 0.2)
        assertTrue(SleepPlanner.fits(morning, bedtime, halfLife, limit))
    }

    @Test
    fun `the same double from robusta does not fit`() {
        val robusta = listOf(Dose(double * 2, at("09:00")))
        assertFalse(SleepPlanner.fits(robusta, bedtime, halfLife, limit))
    }

    @Test
    fun `after the morning double, a black tea fits until about 3-15 pm`() {
        val tea = CaffeineEstimates.tea(TeaType.BLACK).mg
        val latest = SleepPlanner.latestTime(tea, morning, at("09:00"), bedtime, halfLife, limit)!!
        val local = latest.atZone(la).toLocalTime()
        assertEquals(15, local.hour)
        assertTrue("latest was $local", local.minute in 10..20)
    }

    @Test
    fun `after the morning double, no second espresso fits at all`() {
        val ristretto = CaffeineEstimates.coffee(18.0, BrewMethod.ESPRESSO, Species.ARABICA, espressoRatio = 1.0).mg
        assertNull(SleepPlanner.latestTime(ristretto, morning, at("09:00"), bedtime, halfLife, limit))
    }

    @Test
    fun `a lone double on an empty day can be had until about 11-37`() {
        val latest = SleepPlanner.latestTime(double, emptyList(), at("09:00"), bedtime, halfLife, limit)!!
        val local = latest.atZone(la).toLocalTime()
        assertEquals(11, local.hour)
        assertTrue("latest was $local", local.minute in 32..42)
    }

    @Test
    fun `herbal tea fits right up to bedtime`() {
        assertEquals(bedtime, SleepPlanner.latestTime(0.0, morning, at("09:00"), bedtime, halfLife, limit))
    }
}
