package app.hourstracker

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualShiftRangeTest {
    private val zone = ZoneId.of("Asia/Jerusalem")
    private val firstDay = LocalDate.of(2026, 11, 1)
    private val range = ManualShiftRange(
        1, firstDay, firstDay.plusDays(4), ManualShiftTimes(LocalTime.of(18, 0), LocalTime.of(2, 30)),
        zone, 30, "Stock count", ShiftPayroll(ShiftAdjustments(travel = "15")),
    )

    @Test
    fun generatesAnOvernightShiftForEveryDateIncludingBothEndpoints() {
        val plan = range.plan(emptyList())
        assertEquals(5, plan.entries.size)
        for ((index, entry) in plan.entries.withIndex()) {
            val expected = firstDay.plusDays(index.toLong())
            assertEquals(expected.atTime(18, 0).atZone(zone).toInstant(), Instant.ofEpochMilli(entry.start))
            assertEquals(expected.plusDays(1).atTime(2, 30).atZone(zone).toInstant(), Instant.ofEpochMilli(entry.end))
            assertEquals(8 * 3_600_000L, paidMillis(entry.start, entry.end, entry.breakMinutes))
            assertEquals("Stock count", entry.note)
        }
    }

    @Test
    fun skipsExactMatchesOnlyForTheSelectedJobAndKeepsDailyTravelFromDoubling() {
        val planned = range.plan(emptyList()).entries
        val exact = planned[1].existingShift()
        val earlier = planned[2].existingShift().let { it.copy(startMillis = it.startMillis - 60_000L) }
        val otherJob = planned[3].existingShift().copy(jobId = 2)
        val result = range.plan(listOf(exact, earlier, otherJob))
        assertEquals(1, result.duplicates)
        assertEquals(4, result.entries.size)
        assertEquals(listOf("15", "0", "15", "15"), result.entries.map { it.payroll.adjustments.travel })
    }

    @Test
    fun resolvesEachDaySeparatelyAndRejectsARangeWithAnInvalidShift() {
        val start = LocalDate.of(2026, 3, 26)
        val result = range.copy(
            firstDay = start, lastDay = start.plusDays(2),
            times = ManualShiftTimes(LocalTime.of(2, 30), LocalTime.of(3, 15)),
            breakMinutes = 0,
        ).plan(emptyList())
        assertEquals(start.plusDays(1), result.invalidDay)
        assertTrue(result.entries.isEmpty())
    }

    private fun ManualShiftEntry.existingShift() =
        Shift(jobId, start, zone.id, "100", ILS, breakMinutes, end, payroll = payroll)
}
