package com.padraik99.grindhaus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GrindAdvisorTest {

    /** The Maestri House: 0-30, whole steps, lower is finer. */
    private val maestri = GrindScale(min = 0.0, max = 30.0, step = 1.0, finerIsLower = true)

    @Test
    fun `sour goes one step finer, which is lower on the Maestri`() {
        val s = GrindAdvisor.suggest(Taste.SOUR, 4.0, maestri)!!
        assertEquals(3.0, s.grind, 0.0)
        assertEquals("Ran sour. Try grind 3, one step finer?", s.reason)
    }

    @Test
    fun `bitter goes one step coarser`() {
        assertEquals(5.0, GrindAdvisor.suggest(Taste.BITTER, 4.0, maestri)!!.grind, 0.0)
    }

    @Test
    fun `balanced, unrated or pre-ground brews get no suggestion`() {
        assertNull(GrindAdvisor.suggest(Taste.BALANCED, 4.0, maestri))
        assertNull(GrindAdvisor.suggest(null, 4.0, maestri))
        assertNull(GrindAdvisor.suggest(Taste.SOUR, null, maestri))
    }

    @Test
    fun `never suggests past the end of the dial`() {
        assertNull(GrindAdvisor.suggest(Taste.SOUR, 0.0, maestri))
        assertNull(GrindAdvisor.suggest(Taste.BITTER, 30.0, maestri))
    }

    @Test
    fun `a grinder where higher is finer is handled the other way round`() {
        val reversed = maestri.copy(finerIsLower = false)
        assertEquals(5.0, GrindAdvisor.suggest(Taste.SOUR, 4.0, reversed)!!.grind, 0.0)
    }

    @Test
    fun `half-step grinders suggest half steps`() {
        val fine = maestri.copy(step = 0.5)
        val s = GrindAdvisor.suggest(Taste.SOUR, 3.0, fine)!!
        assertEquals(2.5, s.grind, 0.0)
        assertEquals("Ran sour. Try grind 2.5, one step finer?", s.reason)
    }
}
