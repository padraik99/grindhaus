package com.padraik99.grindhaus.domain

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime

/** How late a cup can still be had tonight. */
sealed interface Cutoff {
    /** Fits if drunk before [at]. */
    data class Until(val at: Instant) : Cutoff

    /** Small enough to fit right up to bedtime. */
    data object AnyTime : Cutoff

    /** Already too late: even now it would go over the limit. */
    data object Passed : Cutoff
}

/** Something you might have: a usual brew, a black tea. */
data class Drink(val name: String, val mg: Double)

/** A drink and the latest it still fits tonight. */
data class LastCall(val drink: Drink, val cutoff: Cutoff)

/** Everything the Sleep tab says, worked out in one place so it can be tested without a screen. */
data class SleepForecast(
    val now: Instant,
    val bedtime: ZonedDateTime,
    val nowMg: Double,
    /** Highest amount in the hours after bedtime (see [CaffeineModel.peakAfterBedtime]). */
    val peakAfterBedMg: Double,
    val limitMg: Double,
    val limitIsDerived: Boolean,
    val halfLifeHours: Double,
    val lastCalls: List<LastCall>,
    /** The span the chart draws: from the morning (or the first cup) to the end of the sleep check. */
    val chartStart: Instant,
    val chartEnd: Instant,
) {
    val overLimit: Boolean get() = peakAfterBedMg > limitMg
}

object SleepForecaster {

    /** The chart starts no later than this on the evening's date, so a morning cup is always on it. */
    private val MORNING: LocalTime = LocalTime.of(6, 0)

    fun forecast(
        doses: List<Dose>,
        now: ZonedDateTime,
        bedtime: LocalTime,
        halfLifeHours: Double,
        limitOverrideMg: Double?,
        drinks: List<Drink>,
    ): SleepForecast {
        val bed = Bedtime.next(now, bedtime)
        val limit = SleepPlanner.limitMg(halfLifeHours, limitOverrideMg)
        val at = now.toInstant()

        fun cutoff(mg: Double): Cutoff {
            if (mg <= 0.0) return Cutoff.AnyTime
            val latest = SleepPlanner.latestTime(mg, doses, at, bed.toInstant(), halfLifeHours, limit)
            return when {
                latest == null -> Cutoff.Passed
                !latest.isBefore(bed.toInstant()) -> Cutoff.AnyTime
                else -> Cutoff.Until(latest)
            }
        }

        // A bedtime after midnight still belongs to the evening before.
        val evening = if (bed.toLocalTime() < LocalTime.NOON) bed.toLocalDate().minusDays(1) else bed.toLocalDate()
        val dayStart = evening.atStartOfDay(now.zone).toInstant()
        var start = minOf(evening.atTime(MORNING).atZone(now.zone).toInstant(), at.minus(Duration.ofHours(1)))
        doses.filter { it.takenAt >= dayStart && it.takenAt < start }.minOfOrNull { it.takenAt }?.let {
            start = it.minus(Duration.ofMinutes(30))
        }
        val end = bed.toInstant().plus(Duration.ofMinutes((CaffeineModel.SLEEP_WINDOW_HOURS * 60).toLong()))

        return SleepForecast(
            now = at,
            bedtime = bed,
            nowMg = CaffeineModel.totalAt(doses, at, halfLifeHours),
            peakAfterBedMg = CaffeineModel.peakAfterBedtime(doses, bed.toInstant(), halfLifeHours),
            limitMg = limit,
            limitIsDerived = limitOverrideMg == null,
            halfLifeHours = halfLifeHours,
            lastCalls = drinks.map { LastCall(it, cutoff(it.mg)) },
            chartStart = start,
            chartEnd = end,
        )
    }

    /**
     * A typical brew: the median of the last ten that had real caffeine in them, so one
     * decaf or one double doesn't swing it. Null when there are none yet.
     */
    fun usualBrewMg(recentBrewMg: List<Double>): Double? {
        val real = recentBrewMg.filter { it >= 20.0 }.take(10).sorted()
        if (real.isEmpty()) return null
        val mid = real.size / 2
        return if (real.size % 2 == 1) real[mid] else (real[mid - 1] + real[mid]) / 2
    }

    /** What if: the highest point after bedtime had [extra] been drunk too. */
    fun peakWith(doses: List<Dose>, extra: Dose, bedtime: Instant, halfLifeHours: Double): Double =
        CaffeineModel.peakAfterBedtime(doses + extra, bedtime, halfLifeHours)

    /** The half-life band the chart shades: an hour quicker to two hours slower than the setting. */
    fun band(halfLifeHours: Double): ClosedFloatingPointRange<Double> {
        val r = CaffeineModel.HALF_LIFE_RANGE_HOURS
        return (halfLifeHours - 1.0).coerceIn(r) ..(halfLifeHours + 2.0).coerceIn(r)
    }
}
