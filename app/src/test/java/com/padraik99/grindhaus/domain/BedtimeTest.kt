package com.padraik99.grindhaus.domain

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class BedtimeTest {

    private val la = ZoneId.of("America/Los_Angeles")
    private fun at(text: String): ZonedDateTime = ZonedDateTime.of(LocalDateTime.parse(text), la)

    @Test
    fun `evening bedtime later the same day`() {
        assertEquals(at("2026-10-23T23:30"), Bedtime.next(at("2026-10-23T14:00"), LocalTime.of(23, 30)))
    }

    @Test
    fun `after-midnight bedtime belongs to the evening before`() {
        assertEquals(at("2026-10-24T00:30"), Bedtime.next(at("2026-10-23T22:00"), LocalTime.of(0, 30)))
    }

    @Test
    fun `just past midnight, an after-midnight bedtime is still tonight`() {
        assertEquals(at("2026-10-24T00:30"), Bedtime.next(at("2026-10-24T00:10"), LocalTime.of(0, 30)))
    }

    @Test
    fun `once bedtime has passed the next one is tomorrow night`() {
        assertEquals(at("2026-10-24T23:30"), Bedtime.next(at("2026-10-24T01:00"), LocalTime.of(23, 30)))
    }

    @Test
    fun `durations use real elapsed time across the spring daylight-saving jump`() {
        // Clocks jump from 02:00 to 03:00 on 2026-03-08 in Los Angeles.
        val now = at("2026-03-08T01:00")
        val bed = Bedtime.next(now, LocalTime.of(3, 30))
        assertEquals(Duration.ofMinutes(90), Duration.between(now, bed))
    }
}
