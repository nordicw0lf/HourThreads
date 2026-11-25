package app.hourstracker

import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JobPayChangesTest {
    private val start = Instant.parse("2026-11-08T08:00:00Z").toEpochMilli()
    private val original = Job(
        "Warehouse", "100", 30, 1,
        JobPayroll(
            "280", ShiftAdjustments("5", "10", "0"), EmployeeContributions("6", "2.5"),
        ),
        ILS,
    )

    @Test
    fun appliesPayChangesToCompletedEntriesAndStoresMonthlyFuel() {
        val updated = original.copy(
            hourlyRate = "125",
            breakMinutes = 60,
            payroll = original.payrollDefaults.copy(
                vacationPay = "300",
                adjustments = ShiftAdjustments("8", "15", "30", fuelReimbursement = true, fuelPaidMonthly = true),
                contributions = EmployeeContributions("7", "2.5"),
            ),
        )
        val morning = shift(1)
        val afternoon = shift(2, start + 6 * 3_600_000L).copy(
            payroll = ShiftPayroll(ShiftAdjustments("12", "50"), EmployeeContributions("5", "3")),
        )
        val vacation = shift(3).copy(
            endMillis = start, breakMinutes = 0, payroll = ShiftPayroll(vacationPay = "280.00"),
        )
        val active = shift(4).copy(endMillis = null, activeSlot = 1)
        val otherJob = shift(5).copy(jobId = 2)
        val edited = JobPayChanges(original, updated)
            .updatedShifts(listOf(afternoon, morning, vacation, active, otherJob), recalculatePast = true)
            .associateBy { it.id }

        assertEquals(setOf(1L, 2L, 3L), edited.keys)
        val changedMorning = edited.getValue(1)
        assertEquals("125", changedMorning.hourlyRate)
        assertEquals(30, changedMorning.breakMinutes)
        assertEquals(morning.completedDurationMillis, changedMorning.completedDurationMillis)
        assertEquals(
            ShiftAdjustments("8", "15", "30", fuelReimbursement = true, fuelPaidMonthly = true),
            changedMorning.payrollDetails.adjustments,
        )
        assertEquals(
            ShiftAdjustments("12", "50", "30", fuelReimbursement = true, fuelPaidMonthly = true),
            edited.getValue(2).payrollDetails.adjustments,
        )
        assertEquals(EmployeeContributions("7", "3"), edited.getValue(2).payrollDetails.contributions)
        assertEquals("300", edited.getValue(3).payrollDetails.vacationPay)
        assertEquals("100", edited.getValue(3).hourlyRate)
    }

    @Test
    fun futureOnlyFreezesOldContributionDefaultsOnUninitializedEntries() {
        val updated = original.copy(
            hourlyRate = "125",
            payroll = original.payrollDefaults.copy(contributions = EmployeeContributions("7")),
        )
        val legacy = shift(1).copy(payroll = null)
        val saved = shift(2)
        val other = legacy.copy(id = 3, jobId = 2)
        val edited = JobPayChanges(original, updated).updatedShifts(listOf(legacy, saved, other), recalculatePast = false)
        assertEquals(listOf(legacy.copy(payroll = ShiftPayroll(contributions = original.payrollDefaults.contributions))), edited)
    }

    @Test
    fun retainsCustomTravelAndReclassifiesAMatchingDefault() {
        val previous = original.copy(payroll = original.payrollDefaults.copy(adjustments = ShiftAdjustments(travel = "20")))
        val updated = previous.copy(
            payroll = previous.payrollDefaults.copy(adjustments = ShiftAdjustments(travel = "20", fuelReimbursement = true)),
        )
        val custom = shift(1).copy(payroll = ShiftPayroll(ShiftAdjustments(travel = "50")))
        val matching = shift(2, start + 86_400_000L).copy(payroll = ShiftPayroll(ShiftAdjustments(travel = "20.00")))
        val otherOnSameDay = shift(3, start + 2 * 3_600_000L)
        val changed = JobPayChanges(previous, updated)
            .updatedShifts(listOf(otherOnSameDay, custom, matching), recalculatePast = true)
        assertEquals(listOf(matching.copy(payroll = ShiftPayroll(ShiftAdjustments(travel = "20", fuelReimbursement = true)))), changed)
    }

    @Test
    fun convertingDailyFuelRequiresChoosingPastShiftsAndReplacesMatchingDailyPayments() {
        val dailyFuel = ShiftAdjustments(travel = "25", fuelReimbursement = true)
        val monthlyFuel = dailyFuel.withMonthlyFuel().copy(travel = "300")
        val previous = original.copy(payroll = JobPayroll(adjustments = dailyFuel))
        val updated = previous.copy(payroll = JobPayroll(adjustments = monthlyFuel, legacyDailyFuelDefault = "25"))
        val changes = JobPayChanges(previous, updated)
        val first = shift(1).copy(payroll = ShiftPayroll(dailyFuel))
        val second = shift(2, start + 86_400_000L).copy(payroll = ShiftPayroll(dailyFuel))
        val entries = listOf(first, second)

        assertTrue(changes.affectsPastPay)
        assertTrue(changes.updatedShifts(entries, recalculatePast = false).isEmpty())
        val recalculated = changes.updatedShifts(entries, recalculatePast = true)
        assertEquals(listOf(monthlyFuel, monthlyFuel), recalculated.map { it.payrollDetails.adjustments })
        assertEquals("300.00", recalculated.monthlyFuelTopUps().values.single().amount.toPlainString())

        val laterMonthlyFuel = monthlyFuel.copy(travel = "350")
        val later = updated.copy(payroll = updated.payrollDefaults.copy(adjustments = laterMonthlyFuel))
        val laterRecalculated = JobPayChanges(updated, later).updatedShifts(entries, recalculatePast = true)
        assertEquals("350.00", laterRecalculated.monthlyFuelTopUps().values.single().amount.toPlainString())
    }

    @Test
    fun cosmeticAndBreakChangesDoNotOfferRecalculation() {
        assertFalse(JobPayChanges(original, original.copy(hourlyRate = "100.00", name = "New name", breakMinutes = 10)).affectsPastPay)
        val payroll = original.payrollDefaults.copy(adjustments = ShiftAdjustments(travel = "25"))
        assertTrue(JobPayChanges(original, original.copy(payroll = payroll)).affectsPastPay)
    }

    @Test
    fun currencyChangeRelabelsCompletedShiftsOnlyAfterRecalculation() {
        val updated = original.copy(currencyCode = "USD")
        val completed = shift(1)
        val active = shift(2).copy(endMillis = null, activeSlot = 1)
        val changes = JobPayChanges(original, updated)

        assertTrue(changes.affectsPastPay)
        assertTrue(changes.updatedShifts(listOf(completed, active), recalculatePast = false).isEmpty())
        assertEquals(
            listOf(completed.copy(currencyCode = "USD")),
            changes.updatedShifts(listOf(completed, active), recalculatePast = true),
        )
    }

    @Test
    fun recalculatingAnUnchangedCurrencyDoesNotOverwriteHistoricalRatesInOtherCurrencies() {
        val usd = shift(1).copy(currencyCode = "USD", hourlyRate = "50")
        val ils = shift(2)
        val changes = JobPayChanges(original, original.copy(hourlyRate = "125"))
        assertEquals(listOf(ils.copy(hourlyRate = "125")), changes.updatedShifts(listOf(usd, ils), true))
    }

    private fun shift(id: Long, time: Long = start) = Shift(
        jobId = 1, startMillis = time, zoneId = "Asia/Jerusalem", hourlyRate = "100",
        currencyCode = ILS, breakMinutes = 30, endMillis = time + 4 * 3_600_000L, id = id,
        payroll = original.payrollDefaults.forShift(),
    )
}
