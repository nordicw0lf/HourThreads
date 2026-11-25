package app.hourstracker

import java.time.LocalDate
import java.time.ZoneId

internal data class ManualShiftRange(
    val jobId: Long,
    val firstDay: LocalDate,
    val lastDay: LocalDate,
    val times: ManualShiftTimes,
    val zone: ZoneId,
    val breakMinutes: Int,
    val note: String,
    val payroll: ShiftPayroll,
) {
    fun plan(existing: List<Shift>): ManualShiftRangePlan {
        require(lastDay >= firstDay && payroll.vacationPay == null)
        val shifts = existing.filter { it.jobId == jobId && it.endMillis != null }
        val recordedTimes = shifts.mapTo(HashSet()) { it.startMillis to it.endMillis }
        val adjustments = payroll.adjustments
        val daysWithTravel = if (!adjustments.fuelPaidMonthly && adjustments.travel.toBigDecimal().signum() > 0) {
            shifts.filter {
                val recorded = it.payrollDetails.adjustments
                !recorded.fuelPaidMonthly && recorded.travel.toBigDecimal().signum() > 0
            }.mapTo(HashSet()) { it.localStart().toLocalDate() }
        } else {
            emptySet()
        }
        val startTime = times.startTime()
        val endTime = times.endTime()
        val entries = mutableListOf<ManualShiftEntry>()
        var duplicates = 0
        var date = firstDay
        while (true) {
            val endDate = defaultEndDate(date, startTime, endTime)
            val localStart = resolveTime(date, startTime, zone, null)
            if (localStart.toLocalDate() != date) return ManualShiftRangePlan(emptyList(), invalidDay = date)
            val start = localStart.toInstant().toEpochMilli()
            val end = resolveTime(endDate, endTime, zone, null).toInstant().toEpochMilli()
            if ((start to end) in recordedTimes) {
                duplicates++
            } else {
                if (!isValidShift(start, end, breakMinutes)) return ManualShiftRangePlan(emptyList(), invalidDay = date)
                val pay = if (date in daysWithTravel) payroll.copy(adjustments = adjustments.copy(travel = "0")) else payroll
                entries.add(ManualShiftEntry(jobId, start, end, zone, breakMinutes, note, times, pay))
            }
            if (date == lastDay) break
            date = date.plusDays(1)
        }
        return ManualShiftRangePlan(entries, duplicates)
    }
}

internal data class ManualShiftRangePlan(
    val entries: List<ManualShiftEntry>,
    val duplicates: Int = 0,
    val invalidDay: LocalDate? = null,
)
