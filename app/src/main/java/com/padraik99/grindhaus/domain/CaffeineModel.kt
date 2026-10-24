package com.padraik99.grindhaus.domain

import java.time.Duration
import java.time.Instant
import kotlin.math.exp
import kotlin.math.ln

/** One logged intake of caffeine. */
data class Dose(val mg: Double, val takenAt: Instant)

/**
 * Caffeine in the body over time: a one-compartment model with first-order
 * absorption and elimination, summed across doses. Pure Kotlin, no Android types,
 * so every number here is unit-tested.
 *
 * Amounts are milligrams in the body, not blood concentration.
 */
object CaffeineModel {

    /** Absorption rate. ~5 per hour puts the peak about 45 minutes after drinking. */
    const val ABSORPTION_PER_HOUR = 5.0

    /** Reference coffee from Gardiner et al. 2023 (Sleep Med Rev 69:101764). */
    const val REFERENCE_DOSE_MG = 107.0

    /** Gardiner et al. 2023: finish the reference coffee at least this long before bed. */
    const val REFERENCE_HOURS_BEFORE_BED = 8.8

    /** How long after bedtime the warning checks for the highest level. */
    const val SLEEP_WINDOW_HOURS = 3.0

    val HALF_LIFE_RANGE_HOURS = 1.0..15.0

    private fun eliminationPerHour(halfLifeHours: Double): Double {
        require(halfLifeHours in HALF_LIFE_RANGE_HOURS) {
            "Half-life $halfLifeHours h is outside $HALF_LIFE_RANGE_HOURS"
        }
        return ln(2.0) / halfLifeHours
    }

    /** Milligrams still in the body [hoursSinceDose] after a single dose. */
    fun amountFromDose(doseMg: Double, hoursSinceDose: Double, halfLifeHours: Double): Double {
        if (hoursSinceDose <= 0.0 || doseMg <= 0.0) return 0.0
        val ke = eliminationPerHour(halfLifeHours)
        val ka = ABSORPTION_PER_HOUR
        return doseMg * ka / (ka - ke) * (exp(-ke * hoursSinceDose) - exp(-ka * hoursSinceDose))
    }

    /** Total milligrams in the body at [at] from every dose taken before it. */
    fun totalAt(doses: List<Dose>, at: Instant, halfLifeHours: Double): Double =
        doses.sumOf { amountFromDose(it.mg, hoursBetween(it.takenAt, at), halfLifeHours) }

    /**
     * Highest total in the [SLEEP_WINDOW_HOURS] after [bedtime], checked every [stepMinutes].
     * Checking only the bedtime instant would let a late cup slip through,
     * because it has not been absorbed yet.
     */
    fun peakAfterBedtime(
        doses: List<Dose>,
        bedtime: Instant,
        halfLifeHours: Double,
        stepMinutes: Long = 5,
    ): Double {
        val steps = (SLEEP_WINDOW_HOURS * 60 / stepMinutes).toInt()
        return (0..steps).maxOf { i ->
            totalAt(doses, bedtime.plus(Duration.ofMinutes(i * stepMinutes)), halfLifeHours)
        }
    }

    /**
     * Default bedtime limit, derived rather than typed in: what the reference coffee
     * leaves behind after the reference interval at the user's half-life.
     */
    fun derivedThresholdMg(halfLifeHours: Double): Double =
        amountFromDose(REFERENCE_DOSE_MG, REFERENCE_HOURS_BEFORE_BED, halfLifeHours)

    private fun hoursBetween(from: Instant, to: Instant): Double =
        Duration.between(from, to).toMillis() / 3_600_000.0
}
