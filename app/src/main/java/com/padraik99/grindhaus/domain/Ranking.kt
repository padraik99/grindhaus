package com.padraik99.grindhaus.domain

import kotlin.math.pow

/** "Would you buy it again?" asked once, when a bag is finished. */
enum class BuyAgain(val label: String) { YES("Would buy again"), MAYBE("Maybe again"), NO("Wouldn't buy again") }

/** "Better than your current favourite?" */
enum class Comparison(val label: String, val score: Double) {
    BETTER("Better", 1.0),
    SAME("About the same", 0.5),
    WORSE("Worse", 0.0),
}

/**
 * Ranks bags from one-at-a-time comparisons, the way chess ratings rank players (Elo).
 *
 * Each answer nudges both bags' scores by at most [K] points, scaled by how surprising
 * it was. A single off-day answer barely moves an established favourite; the same
 * answer given repeatedly does. That suits a palate that varies with mood and sleep.
 */
object Ranking {

    const val START = 1500.0

    /** Small steps on purpose: consistency wins, one-off answers don't. */
    const val K = 24.0

    /** Chance that a bag scored [a] beats one scored [b]. */
    fun expected(a: Double, b: Double): Double = 1.0 / (1.0 + 10.0.pow((b - a) / 400.0))

    /** New scores for the bag just finished ([a]) and the favourite it was compared with ([b]). */
    fun update(a: Double, b: Double, result: Comparison): Pair<Double, Double> {
        val change = K * (result.score - expected(a, b))
        return (a + change) to (b - change)
    }
}
