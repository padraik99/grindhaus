package com.padraik99.grindhaus.domain

import java.time.LocalTime
import java.time.ZonedDateTime

/**
 * Finds the next bedtime as a real moment in time.
 *
 * A bedtime before noon (00:30, say) belongs to the night that started the evening
 * before. Times are built in the phone's zone, so daylight-saving shifts are handled
 * and durations measure real elapsed time.
 */
object Bedtime {

    private val NOON: LocalTime = LocalTime.NOON

    fun next(now: ZonedDateTime, bedtime: LocalTime): ZonedDateTime {
        // Before noon we are still in the previous evening's night.
        val eveningDate = if (now.toLocalTime() < NOON) now.toLocalDate().minusDays(1) else now.toLocalDate()
        var candidate = bedtimeForEvening(eveningDate, bedtime, now)
        if (!candidate.isAfter(now)) {
            candidate = bedtimeForEvening(eveningDate.plusDays(1), bedtime, now)
        }
        return candidate
    }

    private fun bedtimeForEvening(
        eveningDate: java.time.LocalDate,
        bedtime: LocalTime,
        now: ZonedDateTime,
    ): ZonedDateTime {
        val date = if (bedtime < NOON) eveningDate.plusDays(1) else eveningDate
        return ZonedDateTime.of(date, bedtime, now.zone)
    }
}
