package com.padraik99.grindhaus.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class FreshnessTest {

    private val today = LocalDate.of(2026, 10, 25)
    private fun of(
        roast: LocalDate? = null,
        bestBy: LocalDate? = null,
        opened: LocalDate? = null,
        preGround: Boolean = false,
    ) = FreshnessRules.of(today, roast, bestBy, opened, preGround)

    @Test
    fun `roast-dated bags rest, peak, then fade`() {
        assertEquals(Freshness("Resting · day 4", FreshnessTone.RESTING), of(roast = today.minusDays(4)))
        assertEquals(Freshness("Peak · day 7", FreshnessTone.GOOD), of(roast = today.minusDays(7)))
        assertEquals(Freshness("Peak · day 21", FreshnessTone.GOOD), of(roast = today.minusDays(21)))
        assertEquals(Freshness("Day 22", FreshnessTone.FADING), of(roast = today.minusDays(22)))
    }

    @Test
    fun `a roast date wins over an opened date`() {
        assertEquals(FreshnessTone.GOOD, of(roast = today.minusDays(10), opened = today.minusDays(40)).tone)
    }

    @Test
    fun `best-by-only bags count days since opening, whole beans`() {
        val bestBy = today.plusMonths(8)
        assertEquals(Freshness("Opened today", FreshnessTone.GOOD), of(bestBy = bestBy, opened = today))
        assertEquals(Freshness("Opened yesterday", FreshnessTone.GOOD), of(bestBy = bestBy, opened = today.minusDays(1)))
        assertEquals(Freshness("Opened 14 days ago", FreshnessTone.GOOD), of(bestBy = bestBy, opened = today.minusDays(14)))
        assertEquals(FreshnessTone.FADING, of(bestBy = bestBy, opened = today.minusDays(15)).tone)
        assertEquals(FreshnessTone.STALE, of(bestBy = bestBy, opened = today.minusDays(31)).tone)
    }

    @Test
    fun `ground coffee goes stale twice as fast once opened`() {
        assertEquals(FreshnessTone.GOOD, of(opened = today.minusDays(7), preGround = true).tone)
        assertEquals(FreshnessTone.FADING, of(opened = today.minusDays(8), preGround = true).tone)
        assertEquals(FreshnessTone.STALE, of(opened = today.minusDays(15), preGround = true).tone)
    }

    @Test
    fun `past best-by is flagged whatever else is known`() {
        assertEquals(Freshness("Past best-by", FreshnessTone.STALE), of(bestBy = today.minusDays(1), opened = today))
        assertEquals(FreshnessTone.GOOD, of(bestBy = today, opened = today).tone)
    }

    @Test
    fun `a bag with no dates yet reads unopened`() {
        assertEquals(Freshness("Unopened", FreshnessTone.UNOPENED), of(bestBy = today.plusMonths(6)))
        assertEquals(Freshness("Unopened", FreshnessTone.UNOPENED), of())
    }
}
