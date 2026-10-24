package com.padraik99.grindhaus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class CaffeineModelTest {

    private val t0 = Instant.parse("2026-10-23T14:00:00Z")
    private fun hours(h: Double): Instant = t0.plusMillis((h * 3_600_000).toLong())

    @Test
    fun `derived threshold matches the spec at 4, 5 and 7 hour half-lives`() {
        assertEquals(24.1, CaffeineModel.derivedThresholdMg(4.0), 0.1)
        assertEquals(32.5, CaffeineModel.derivedThresholdMg(5.0), 0.1)
        assertEquals(45.7, CaffeineModel.derivedThresholdMg(7.0), 0.1)
    }

    @Test
    fun `nothing is in the body before or at the moment of a dose`() {
        assertEquals(0.0, CaffeineModel.amountFromDose(100.0, -1.0, 5.0), 0.0)
        assertEquals(0.0, CaffeineModel.amountFromDose(100.0, 0.0, 5.0), 0.0)
    }

    @Test
    fun `peak arrives about 45 minutes after drinking`() {
        val peakHour = (1..300).map { it / 100.0 }
            .maxBy { CaffeineModel.amountFromDose(100.0, it, 5.0) }
        assertEquals(0.74, peakHour, 0.02)
    }

    @Test
    fun `long after absorption the curve halves once per half-life`() {
        val at10 = CaffeineModel.amountFromDose(100.0, 10.0, 5.0)
        val at15 = CaffeineModel.amountFromDose(100.0, 15.0, 5.0)
        assertEquals(0.5, at15 / at10, 0.001)
    }

    @Test
    fun `doses add together`() {
        val doses = listOf(Dose(194.0, t0), Dose(40.0, hours(3.0)))
        val at = hours(6.5)
        val expected = CaffeineModel.amountFromDose(194.0, 6.5, 5.0) +
            CaffeineModel.amountFromDose(40.0, 3.5, 5.0)
        assertEquals(expected, CaffeineModel.totalAt(doses, at, 5.0), 1e-9)
    }

    @Test
    fun `sample day from the mockup leaves about 28 mg at 23-30`() {
        val morning = Instant.parse("2026-10-23T07:30:00Z")
        val doses = listOf(Dose(194.0, morning), Dose(40.0, morning.plus(Duration.ofHours(3))))
        val bedtime = morning.plus(Duration.ofHours(16))
        assertEquals(28.5, CaffeineModel.totalAt(doses, bedtime, 5.0), 0.2)
    }

    @Test
    fun `a cup just before bed is caught by the sleep window, not the bedtime instant`() {
        val bedtime = hours(9.0)
        val late = listOf(Dose(100.0, bedtime.minus(Duration.ofMinutes(3))))
        val limit = CaffeineModel.derivedThresholdMg(5.0)
        val atBedtime = CaffeineModel.totalAt(late, bedtime, 5.0)
        val peak = CaffeineModel.peakAfterBedtime(late, bedtime, 5.0)
        assertTrue("at bedtime $atBedtime mg looks under the $limit mg limit", atBedtime < limit)
        assertTrue("peak $peak mg should be well over the limit", peak > 80.0)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `half-life outside the sane range is rejected`() {
        CaffeineModel.amountFromDose(100.0, 1.0, 0.1)
    }
}
