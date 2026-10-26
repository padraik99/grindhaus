package com.padraik99.grindhaus.ui.brew

import com.padraik99.grindhaus.data.BrewLog
import com.padraik99.grindhaus.domain.BrewMethod
import com.padraik99.grindhaus.domain.FilterOption
import com.padraik99.grindhaus.domain.Taste
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrewDraftTest {

    private val weighed = BrewLog(
        id = 7, beanId = 1, method = BrewMethod.ESPRESSO, grinderId = 1, filter = FilterOption.SINGLE_WALL,
        grindSetting = 4.0, doseG = 18.0, outG = 44.0, shotTimeS = 31, rating = 2, taste = Taste.BITTER,
        notes = "Gushed", caffeineMg = 170.0, brewedAt = 1_000,
    )

    @Test
    fun anUnweighedBrewOpensWithABlankYield() {
        assertNull(weighed.copy(weighed = false).toDraft().outG)
        assertEquals(44.0, weighed.toDraft().outG!!, 0.0)
    }

    @Test
    fun clearingAWeighedYieldFallsBackToTheMethodRatio() {
        // The weighed 44 g was the number in doubt, so the fallback is 18 g at espresso's 1:2.
        assertEquals(36.0, weighed.plannedOutG(), 0.0)
        val edited = weighed.withDraft(weighed.toDraft().copy(outG = null), weighed.plannedOutG())
        assertEquals(false, edited.weighed)
        assertEquals(36.0, edited.outG, 0.0)
    }

    @Test
    fun anUnweighedBrewKeepsItsPlannedYield() {
        val planned = weighed.copy(outG = 40.0, weighed = false)
        assertEquals(40.0, planned.plannedOutG(), 0.0)
    }

    @Test
    fun editingKeepsTheBrewsIdentityAndTime() {
        val edited = weighed.withDraft(
            BrewDraft(grind = 3.0, doseG = 18.5, outG = 37.0, shotTimeS = 28, rating = 4, taste = Taste.BALANCED, notes = " Better "),
            plannedOutG = 36.0,
        )
        assertEquals(7L, edited.id)
        assertEquals(1_000L, edited.brewedAt)
        assertEquals(true, edited.weighed)
        assertEquals("Better", edited.notes)
        assertEquals(3.0, edited.grindSetting!!, 0.0)
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
