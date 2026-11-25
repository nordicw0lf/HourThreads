package app.hourstracker

import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class PayrollTest {
    private val month = YearMonth.of(2026, 11)
    private val start = Instant.parse("2026-11-01T08:00:00Z").toEpochMilli()
    private val contributions = EmployeeContributions("6", "2.5")
    private val job = Job("Warehouse", "120", 0, 1, JobPayroll(contributions = contributions), ILS)

    @Test
    fun combinesEarningsThenDeductsInsuranceTaxAndEmployeeContributions() {
        val work = shift(
            hours = 80,
            rate = "120",
            payroll = ShiftPayroll(ShiftAdjustments("50", "1900", "100"), contributions),
        )
        val vacation = shift(hours = 0, rate = "120", payroll = ShiftPayroll(
            contributions = contributions, vacationPay = "400",
        )).copy(jobId = 2)
        val result = checkNotNull(estimateIsraeliPayroll(month, listOf(work, vacation), listOf(job), 9))
        assertAmount("12000", result.gross)
        assertAmount("9600", result.wages)
        assertAmount("400", result.vacation)
        assertAmount("600", result.pension)
        assertAmount("250", result.training)
        assertAmount("1516", result.taxBeforeCredits)
        assertAmount("544.50", result.pointsCredit)
        assertAmount("210", result.pensionCredit)
        assertAmount("762", result.incomeTax)
        assertAmount("380.90", result.nationalInsurance)
        assertAmount("470.96", result.healthInsurance)
        assertAmount("9486.14", result.net)
        assertEquals(80 * 3_600_000L, listOf(work, vacation).shiftTotals().durationMillis)
    }

    @Test
    fun appliesQuarterPointAndFloorsTaxAtZero() {
        val income = listOf(shift(hours = 50, rate = "200"))
        val result = checkNotNull(estimateIsraeliPayroll(month, income, emptyList(), 1))
        assertAmount("60.50", result.pointsCredit)
        assertAmount("1059", result.incomeTax)
        val creditsAboveTax = checkNotNull(estimateIsraeliPayroll(month, income, emptyList(), 20))
        assertAmount("0", creditsAboveTax.incomeTax)
    }

    @Test
    fun excludingIncomeTaxIncreasesNetByOnlyThatDeduction() {
        val work = shift(
            hours = 80, rate = "120",
            payroll = ShiftPayroll(ShiftAdjustments(deductions = "50", bonus = "1900"), contributions),
        )
        val withTax = checkNotNull(estimateIsraeliPayroll(month, listOf(work), listOf(job), 9))
        val withoutTax = checkNotNull(
            estimateIsraeliPayroll(month, listOf(work), listOf(job), 9, includeIncomeTax = false),
        )
        assertEquals(withTax.copy(incomeTax = BigDecimal.ZERO), withoutTax)
        assertAmount(withTax.incomeTax.toPlainString(), withoutTax.net - withTax.net)
    }

    @Test
    fun categorizesFuelSeparatelyAndIncludesItInGrossOnce() {
        val fuel = ShiftPayroll(ShiftAdjustments(travel = "30", fuelReimbursement = true))
        val travel = ShiftPayroll(ShiftAdjustments(travel = "15"))
        val entries = listOf(shift(hours = 8, rate = "100", payroll = fuel), shift(hours = 8, rate = "100", payroll = travel))
        val result = checkNotNull(estimateIsraeliPayroll(month, entries, emptyList(), 0))
        assertAmount("30", result.fuel)
        assertAmount("15", result.travel)
        assertAmount("1645", result.gross)
        assertAmount("1645", entries.shiftTotals().payByCurrency.getValue(ILS))
    }

    @Test
    fun countsMonthlyFuelOncePerJobAndCalendarMonth() {
        val fuel = ShiftPayroll(ShiftAdjustments(travel = "300", fuelReimbursement = true, fuelPaidMonthly = true))
        val first = shift(hours = 8, rate = "100", payroll = fuel).copy(id = 1)
        val later = first.copy(id = 2, startMillis = start + 86_400_000L, endMillis = first.endMillis!! + 86_400_000L)
        val otherJob = first.copy(id = 3, jobId = 2)
        val december = first.copy(id = 4, startMillis = start + 30 * 86_400_000L, endMillis = first.endMillis + 30 * 86_400_000L)
        val entries = listOf(later, first, otherJob, december)
        val result = checkNotNull(estimateIsraeliPayroll(month, entries, emptyList(), 0))

        assertAmount("600", result.fuel)
        assertAmount("3000", result.gross)
        assertAmount("4100", entries.shiftTotals().payByCurrency.getValue(ILS))
        assertAmount("3200", entries.shiftTotals(includeMonthlyFuel = false).payByCurrency.getValue(ILS))
        assertAmount("1100", listOf(later).shiftTotals().payByCurrency.getValue(ILS))
    }

    @Test
    fun countsPreviouslyRecordedFuelTowardTheFirstMonthlyAmount() {
        val daily = ShiftPayroll(ShiftAdjustments(travel = "25", fuelReimbursement = true))
        val monthly = daily.copy(adjustments = daily.adjustments.withMonthlyFuel().copy(travel = "300"))
        val first = shift(hours = 8, rate = "100", payroll = monthly).copy(id = 1)
        val later = first.copy(id = 2, payroll = monthly.copy(adjustments = monthly.adjustments.copy(travel = "350")))
        val older = first.copy(id = 3, payroll = daily)
        val entries = listOf(later, older, first)
        val result = checkNotNull(estimateIsraeliPayroll(month, entries, emptyList(), 0))

        assertAmount("300", result.fuel)
        assertAmount("2700", entries.shiftTotals().payByCurrency.getValue(ILS))
        assertAmount("2425", entries.shiftTotals(includeMonthlyFuel = false).payByCurrency.getValue(ILS))
    }

    @Test
    fun usesWidenedBracketsSurtaxAndInsuranceCeiling() {
        val income = listOf(shift(hours = 100, rate = "700"))
        val result = checkNotNull(estimateIsraeliPayroll(month, income, emptyList(), 0))
        assertAmount("23615.30", result.taxBeforeCredits)
        assertAmount("3174.60", result.nationalInsurance)
        assertAmount("2534.31", result.healthInsurance)
    }

    @Test
    fun usesJobContributionSettingsForLegacyShiftsAndStoredSettingsForNewShifts() {
        val legacy = shift(hours = 10, rate = "100")
        val saved = legacy.copy(payroll = ShiftPayroll(contributions = EmployeeContributions("2", "1")))
        val result = checkNotNull(estimateIsraeliPayroll(month, listOf(legacy, saved), listOf(job), 0))
        assertAmount("80", result.pension)
        assertAmount("35", result.training)
    }

    @Test
    fun restrictsPayrollToTheSupportedYearAndCurrency() {
        val income = shift(hours = 6, rate = "50")
        assertNull(estimateIsraeliPayroll(month.minusYears(1), listOf(income), emptyList(), 0))
        assertNull(estimateIsraeliPayroll(month, listOf(income.copy(currencyCode = "USD")), emptyList(), 0))
        val running = income.copy(endMillis = null)
        val october = income.copy(startMillis = start - 10 * 86_400_000L, endMillis = start - 9 * 86_400_000L)
        val result = checkNotNull(estimateIsraeliPayroll(month, listOf(running, october), emptyList(), 0))
        assertAmount("0", result.gross)
        assertFalse(EmployeeContributions("101").isValid())
    }

    @Test
    fun estimatesIsraeliPayrollForOnlyTheIlsPortionOfAMixedMonth() {
        val ils = shift(hours = 8, rate = "100")
        val usd = ils.copy(jobId = 2, currencyCode = "USD", hourlyRate = "50")
        val mixed = listOf(ils, usd)

        assertEquals(
            estimateIsraeliPayroll(month, listOf(ils), listOf(job), 0),
            estimateIsraeliPayroll(month, mixed, listOf(job), 0),
        )
        assertEquals(16 * 3_600_000L, mixed.shiftTotals().durationMillis)
        assertAmount("800", mixed.shiftTotals().payByCurrency.getValue(ILS))
        assertAmount("400", mixed.shiftTotals().payByCurrency.getValue("USD"))
    }

    @Test
    fun doesNotDeductForeignCurrencyFuelFromAMonthlyIlsAllowance() {
        val monthly = ShiftPayroll(ShiftAdjustments(travel = "300", fuelReimbursement = true, fuelPaidMonthly = true))
        val daily = ShiftPayroll(ShiftAdjustments(travel = "25", fuelReimbursement = true))
        val ils = shift(hours = 8, rate = "100", payroll = monthly).copy(id = 2)
        val usd = shift(hours = 8, rate = "50", payroll = daily).copy(id = 1, currencyCode = "USD")
        val mixed = listOf(usd, ils)

        assertAmount("1100", mixed.shiftTotals().payByCurrency.getValue(ILS))
        assertAmount("425", mixed.shiftTotals().payByCurrency.getValue("USD"))
        assertAmount("300", checkNotNull(estimateIsraeliPayroll(month, mixed, listOf(job), 0)).fuel)
    }

    private fun shift(hours: Int, rate: String, payroll: ShiftPayroll? = null) =
        Shift(1, start, "Asia/Jerusalem", rate, ILS, 0, start + hours * 3_600_000L, payroll = payroll)

    private fun assertAmount(expected: String, actual: BigDecimal) =
        assertEquals("Expected $expected, got $actual", 0, BigDecimal(expected).compareTo(actual))
}
