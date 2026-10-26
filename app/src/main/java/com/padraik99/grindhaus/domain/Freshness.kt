package com.padraik99.grindhaus.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * What the Vault badge says about a bag.
 *
 * With a roast date, freshness counts days since roast against a peak window.
 * Most supermarket bags print only a best-by date, which cannot be turned back into
 * a roast date (brands set it anywhere from months to years out), so for those
 * the badge counts days since the bag was opened instead.
 *
 * The windows are coffee-community rules of thumb, not study results; Settings will
 * make them editable.
 */
enum class FreshnessTone { RESTING, GOOD, FADING, STALE, UNOPENED }

data class Freshness(val label: String, val tone: FreshnessTone)

object FreshnessRules {

    /** Days after roasting: resting before [PEAK_START], peak through [PEAK_END]. */
    const val PEAK_START = 7
    const val PEAK_END = 21

    /** Days after opening: whole beans keep longer than ground coffee. */
    const val OPENED_GOOD_WHOLE = 14
    const val OPENED_FADING_WHOLE = 30
    const val OPENED_GOOD_GROUND = 7
    const val OPENED_FADING_GROUND = 14

    fun of(
        today: LocalDate,
        roastDate: LocalDate?,
        bestByDate: LocalDate?,
        openedDate: LocalDate?,
        preGround: Boolean,
    ): Freshness {
        if (bestByDate != null && today.isAfter(bestByDate)) {
            return Freshness("Past best-by", FreshnessTone.STALE)
        }
        if (roastDate != null) {
            val day = ChronoUnit.DAYS.between(roastDate, today).coerceAtLeast(0)
            return when {
                day < PEAK_START -> Freshness("Resting · day $day", FreshnessTone.RESTING)
                day <= PEAK_END -> Freshness("Peak · day $day", FreshnessTone.GOOD)
                else -> Freshness("Day $day", FreshnessTone.FADING)
            }
        }
        if (openedDate != null) {
            val days = ChronoUnit.DAYS.between(openedDate, today).coerceAtLeast(0)
            val good = if (preGround) OPENED_GOOD_GROUND else OPENED_GOOD_WHOLE
            val fading = if (preGround) OPENED_FADING_GROUND else OPENED_FADING_WHOLE
            val label = when (days) {
                0L -> "Opened today"
                1L -> "Opened yesterday"
                else -> "Opened $days days ago"
            }
            val tone = when {
                days <= good -> FreshnessTone.GOOD
                days <= fading -> FreshnessTone.FADING
                else -> FreshnessTone.STALE
            }
            return Freshness(label, tone)
        }
        return Freshness("Unopened", FreshnessTone.UNOPENED)
    }
}
