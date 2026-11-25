package app.hourstracker

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoBackupScheduleTest {
    private val zone = ZoneId.of("Asia/Jerusalem")

    @Test
    fun dailyKeepsTheChosenLocalTimeAcrossMidnight() {
        val plan = AutoBackupPlan(minuteOfDay = 17 * 60)
        assertEquals(at(2026, 11, 24, 17), plan.nextAfter(at(2026, 11, 24, 16)))
        assertEquals(at(2026, 11, 25, 17), plan.nextAfter(at(2026, 11, 24, 17)))
    }

    @Test
    fun weeklyMovesToTheNextWeekAfterTheSelectedTime() {
        val plan = AutoBackupPlan(frequency = BackupFrequency.Weekly, weekday = DayOfWeek.TUESDAY.value)
        assertEquals(at(2026, 11, 24, 3), plan.nextAfter(at(2026, 11, 23, 20)))
        assertEquals(at(2026, 12, 1, 3), plan.nextAfter(at(2026, 11, 24, 4)))
    }

    @Test
    fun monthlyUsesTheLastDayAndReturnsToTheChosenDayWhenPossible() {
        val plan = AutoBackupPlan(frequency = BackupFrequency.Monthly, monthDay = 31)
        assertEquals(at(2026, 2, 28, 3), plan.nextAfter(at(2026, 2, 1, 0)))
        assertEquals(at(2026, 3, 31, 3), plan.nextAfter(at(2026, 2, 28, 3)))
        assertEquals(at(2028, 2, 29, 3), plan.nextAfter(at(2028, 2, 1, 0)))
    }

    @Test
    fun nonexistentLocalTimeRunsAfterTheDstGap() {
        val plan = AutoBackupPlan(minuteOfDay = 2 * 60 + 30)
        val after = at(2026, 3, 27, 0)
        val occurrence = plan.nextAfter(after)
        assertEquals(at(2026, 3, 27, 3, 30), occurrence)
        assertEquals(at(2026, 3, 28, 2, 30), plan.nextAfter(occurrence))
    }

    private fun at(year: Int, month: Int, day: Int, hour: Int, minute: Int = 0) =
        ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)
}
