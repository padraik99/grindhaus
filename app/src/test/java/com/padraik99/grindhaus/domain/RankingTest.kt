package com.padraik99.grindhaus.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RankingTest {

    @Test
    fun `two level bags have an even chance`() {
        assertEquals(0.5, Ranking.expected(1500.0, 1500.0), 1e-9)
    }

    @Test
    fun `between level bags, better moves each by half a step`() {
        val (a, b) = Ranking.update(1500.0, 1500.0, Comparison.BETTER)
        assertEquals(1512.0, a, 1e-9)
        assertEquals(1488.0, b, 1e-9)
    }

    @Test
    fun `about the same between level bags changes nothing`() {
        assertEquals(1500.0 to 1500.0, Ranking.update(1500.0, 1500.0, Comparison.SAME))
    }

    @Test
    fun `points are only moved, never created`() {
        val (a, b) = Ranking.update(1460.0, 1580.0, Comparison.BETTER)
        assertEquals(1460.0 + 1580.0, a + b, 1e-9)
    }

    @Test
    fun `one off-day answer barely dents an established favourite`() {
        // The favourite has won ten comparisons in a row against a level challenger.
        var fav = 1500.0
        var other = 1500.0
        repeat(10) {
            val (o, f) = Ranking.update(other, fav, Comparison.WORSE)
            other = o
            fav = f
        }
        val before = fav
        // Then, on an off day, the challenger is called better once.
        val (_, after) = Ranking.update(other, fav, Comparison.BETTER)
        assertTrue("dropped ${before - after}", before - after < Ranking.K)
        assertTrue("favourite still well ahead", after > other + 60)
    }

    @Test
    fun `the same answer given repeatedly does climb`() {
        var challenger = 1500.0
        var fav = 1600.0
        repeat(8) {
            val (c, f) = Ranking.update(challenger, fav, Comparison.BETTER)
            challenger = c
            fav = f
        }
        assertTrue(challenger > fav)
    }
}
