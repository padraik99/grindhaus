package com.padraik99.grindhaus.domain

import java.time.Duration
import java.time.Instant

/** Answers "how late can I still have this?" for a given bedtime. */
object SleepPlanner {

    /** The bedtime limit in use: the user's override, or the one derived from their half-life. */
    fun limitMg(halfLifeHours: Double, overrideMg: Double? = null): Double =
        overrideMg ?: CaffeineModel.derivedThresholdMg(halfLifeHours)

    /** True when [doses] keep the level after [bedtime] at or under [limitMg]. */
    fun fits(doses: List<Dose>, bedtime: Instant, halfLifeHours: Double, limitMg: Double): Boolean =
        CaffeineModel.peakAfterBedtime(doses, bedtime, halfLifeHours) <= limitMg

    /**
     * Latest minute, between [from] and [bedtime], at which [newDoseMg] can be taken on
     * top of [existing] and still stay within [limitMg]. Null when it never fits.
     *
     * Later is always worse here (less time to clear), so a binary search over minutes
     * finds the boundary.
     */
    fun latestTime(
        newDoseMg: Double,
        existing: List<Dose>,
        from: Instant,
        bedtime: Instant,
        halfLifeHours: Double,
        limitMg: Double,
    ): Instant? {
        fun fitsAt(minute: Long): Boolean =
            fits(existing + Dose(newDoseMg, from.plus(Duration.ofMinutes(minute))), bedtime, halfLifeHours, limitMg)

        val span = Duration.between(from, bedtime).toMinutes()
        if (span < 0 || !fitsAt(0)) return null
        if (fitsAt(span)) return bedtime
        var ok = 0L
        var bad = span
        while (bad - ok > 1) {
            val mid = (ok + bad) / 2
            if (fitsAt(mid)) ok = mid else bad = mid
        }
        return from.plus(Duration.ofMinutes(ok))
    }
}
