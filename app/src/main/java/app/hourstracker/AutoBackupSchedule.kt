package app.hourstracker

import java.time.DayOfWeek
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZonedDateTime
import java.time.temporal.TemporalAdjusters
import kotlinx.serialization.Serializable

@Serializable
internal enum class BackupFrequency { Daily, Weekly, Monthly }

@Serializable
internal data class AutoBackupPlan(
    val enabled: Boolean = false,
    val frequency: BackupFrequency = BackupFrequency.Daily,
    val minuteOfDay: Int = 3 * 60,
    val weekday: Int = DayOfWeek.MONDAY.value,
    val monthDay: Int = 1,
    val folderUri: String? = null,
    val folderName: String? = null,
) {
    val time: LocalTime get() = LocalTime.of(minuteOfDay / 60, minuteOfDay % 60)
}

internal fun AutoBackupPlan.nextAfter(after: ZonedDateTime): ZonedDateTime {
    val localDate = after.toLocalDate()
    return when (frequency) {
        BackupFrequency.Daily -> {
            val today = localDate.atTime(time).atZone(after.zone)
            if (today.isAfter(after)) today else localDate.plusDays(1).atTime(time).atZone(after.zone)
        }
        BackupFrequency.Weekly -> {
            val day = localDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday)))
            val candidate = day.atTime(time).atZone(after.zone)
            if (candidate.isAfter(after)) candidate else day.plusWeeks(1).atTime(time).atZone(after.zone)
        }
        BackupFrequency.Monthly -> {
            val month = YearMonth.from(localDate)
            fun scheduled(period: YearMonth) =
                period.atDay(monthDay.coerceAtMost(period.lengthOfMonth())).atTime(time).atZone(after.zone)
            val candidate = scheduled(month)
            if (candidate.isAfter(after)) candidate else scheduled(month.plusMonths(1))
        }
    }
}
