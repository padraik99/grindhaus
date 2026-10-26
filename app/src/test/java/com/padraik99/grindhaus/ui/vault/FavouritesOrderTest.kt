package com.padraik99.grindhaus.ui.vault

import com.padraik99.grindhaus.data.Bean
import com.padraik99.grindhaus.data.BeanRating
import com.padraik99.grindhaus.data.RoastLevel
import com.padraik99.grindhaus.domain.BuyAgain
import com.padraik99.grindhaus.domain.Freshness
import com.padraik99.grindhaus.domain.FreshnessTone
import org.junit.Assert.assertEquals
import org.junit.Test

class FavouritesOrderTest {

    private fun card(id: Long, buy: BuyAgain?, rank: Double = 1500.0) =
        BeanCard(Bean(id = id, title = "Bag $id", roastLevel = RoastLevel.MEDIUM, buyAgain = buy, rankScore = rank), Freshness("", FreshnessTone.UNOPENED))

    @Test
    fun buyAgainComesFirstThenRankThenShotStars() {
        val cards = listOf(
            card(1, BuyAgain.NO, rank = 1700.0),
            card(2, null),
            card(3, BuyAgain.MAYBE),
            card(4, BuyAgain.YES, rank = 1490.0),
            card(5, BuyAgain.YES, rank = 1530.0),
            card(6, null),
        )
        val shots = mapOf(6L to 4.7, 2L to 3.0)
        assertEquals(listOf(5L, 4L, 3L, 6L, 2L, 1L), favouritesOrder(cards, shots).map { it.bean.id })
    }

    @Test
    fun shotStarsAverageEachBagsBestThree() {
        val ratings = listOf(5, 4, 4, 1, 1).map { BeanRating(7, it) } + BeanRating(8, 2)
        val avg = topThreeAverages(ratings)
        assertEquals(13.0 / 3, avg.getValue(7), 1e-9)
        assertEquals(2.0, avg.getValue(8), 1e-9)
    }
}
