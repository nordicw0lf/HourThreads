package app.hourstracker

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.YearMonth
import java.util.Currency
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val payrollDecimal = Regex("[0-9]+(?:\\.[0-9]+)?")

internal fun String.isPayrollAmount(): Boolean = payrollDecimal.matches(this)

internal fun String.isPayrollPercent(): Boolean = isPayrollAmount() && toBigDecimal() <= BigDecimal(100)

@Serializable
internal data class ShiftAdjustments(
    val deductions: String = "0",
    val bonus: String = "0",
    val travel: String = "0",
    val fuelReimbursement: Boolean = false,
    val fuelPaidMonthly: Boolean = false,
) : java.io.Serializable {
    fun isValid(): Boolean =
        (!fuelPaidMonthly || fuelReimbursement) && listOf(deductions, bonus, travel).all { it.isPayrollAmount() }

    fun withMonthlyFuel() = copy(fuelPaidMonthly = fuelReimbursement)
}

@Serializable
internal data class EmployeeContributions(
    val pensionPercent: String = "0",
    val trainingPercent: String = "0",
) {
    fun isValid(): Boolean = pensionPercent.isPayrollPercent() && trainingPercent.isPayrollPercent()
}

@Serializable
internal data class JobPayroll(
    val vacationPay: String = "0",
    val adjustments: ShiftAdjustments = ShiftAdjustments(),
    val contributions: EmployeeContributions = EmployeeContributions(),
    val legacyDailyFuelDefault: String? = null,
) {
    fun isValid(): Boolean = vacationPay.isPayrollAmount() && adjustments.isValid() && contributions.isValid() &&
        legacyDailyFuelDefault?.isPayrollAmount() != false

    fun forShift() = ShiftPayroll(adjustments.withMonthlyFuel(), contributions)
}

@Serializable
internal data class ShiftPayroll(
    val adjustments: ShiftAdjustments = ShiftAdjustments(),
    val contributions: EmployeeContributions = EmployeeContributions(),
    val vacationPay: String? = null,
) {
    fun isValid(): Boolean =
        adjustments.isValid() && contributions.isValid() &&
            (vacationPay == null || (vacationPay.isPayrollAmount() && adjustments == ShiftAdjustments()))
}

internal class PayrollConverters {
    @TypeConverter fun encodeJob(value: JobPayroll?): String? = value?.let { Json.encodeToString(it) }
    @TypeConverter fun decodeJob(value: String?): JobPayroll? = value?.let { Json.decodeFromString(it) }
    @TypeConverter fun encodeShift(value: ShiftPayroll?): String? = value?.let { Json.encodeToString(it) }
    @TypeConverter fun decodeShift(value: String?): ShiftPayroll? = value?.let { Json.decodeFromString(it) }
}

internal val Job.payrollDefaults: JobPayroll get() = payroll ?: JobPayroll()
internal val Shift.payrollDetails: ShiftPayroll get() = payroll ?: ShiftPayroll()
internal val Shift.isVacation: Boolean get() = payroll?.vacationPay != null

internal fun List<Shift>.hasTravelPayment(jobId: Long, date: LocalDate): Boolean =
    any { shift ->
        val adjustments = shift.payrollDetails.adjustments
        shift.jobId == jobId && shift.endMillis != null && !adjustments.fuelPaidMonthly &&
            adjustments.travel.toBigDecimal().signum() > 0 && shift.localStart().toLocalDate() == date
    }

internal data class MonthlyFuelTopUp(val currencyCode: String, val amount: BigDecimal)

internal fun List<Shift>.monthlyFuelTopUps(): Map<Pair<Long, YearMonth>, MonthlyFuelTopUp> {
    val fuelEntries = filter { shift ->
        val adjustments = shift.payrollDetails.adjustments
        shift.endMillis != null && !shift.isVacation && adjustments.fuelReimbursement &&
            adjustments.travel.toBigDecimal().signum() > 0
    }
    return buildMap {
        for ((jobMonth, entries) in fuelEntries.groupBy { it.jobId to YearMonth.from(it.localStart()) }) {
            val firstMonthly = entries.filter { it.payrollDetails.adjustments.fuelPaidMonthly }
                .minByOrNull { it.id } ?: continue
            val currency = firstMonthly.currencyCode
            val scale = Currency.getInstance(currency).defaultFractionDigits
            val allowance = firstMonthly.payrollDetails.adjustments.travel.toBigDecimal()
                .setScale(scale, RoundingMode.HALF_UP)
            val dailyFuel = entries.filter {
                it.currencyCode == currency && !it.payrollDetails.adjustments.fuelPaidMonthly
            }.sumOf {
                it.payrollDetails.adjustments.travel.toBigDecimal().setScale(scale, RoundingMode.HALF_UP)
            }
            put(jobMonth, MonthlyFuelTopUp(currency, (allowance - dailyFuel).max(BigDecimal.ZERO)))
        }
    }
}

internal data class ShiftEarnings(
    val wages: BigDecimal,
    val vacation: BigDecimal,
    val bonus: BigDecimal,
    val travel: BigDecimal,
    val fuel: BigDecimal,
    val deductions: BigDecimal,
) {
    val contributionBase: BigDecimal get() = wages + vacation
    val gross: BigDecimal get() = contributionBase + bonus + travel + fuel
}

internal fun shiftEarnings(
    durationMillis: Long, rate: String, currency: String, payroll: ShiftPayroll,
): ShiftEarnings {
    val vacation = payroll.vacationPay
    val adjustments = payroll.adjustments
    val fractionDigits = Currency.getInstance(currency).defaultFractionDigits
    fun amount(value: String): BigDecimal = value.toBigDecimal().setScale(fractionDigits, RoundingMode.HALF_UP)
    val commute = amount(adjustments.travel)
    return ShiftEarnings(
        wages = if (vacation == null) estimatedPay(durationMillis, rate, currency) else BigDecimal.ZERO,
        vacation = vacation?.let(::amount) ?: BigDecimal.ZERO,
        bonus = amount(adjustments.bonus),
        travel = if (adjustments.fuelReimbursement) BigDecimal.ZERO else commute,
        fuel = if (adjustments.fuelReimbursement && !adjustments.fuelPaidMonthly) commute else BigDecimal.ZERO,
        deductions = amount(adjustments.deductions),
    )
}

internal fun Shift.earnings(): ShiftEarnings =
    shiftEarnings(completedDurationMillis, hourlyRate, currencyCode, payrollDetails)

internal const val ISRAEL_PAYROLL_YEAR = 2026
internal const val ILS = "ILS"

internal data class IsraeliPayrollEstimate(
    val wages: BigDecimal,
    val vacation: BigDecimal,
    val bonus: BigDecimal,
    val travel: BigDecimal,
    val fuel: BigDecimal,
    val deductions: BigDecimal,
    val pension: BigDecimal,
    val training: BigDecimal,
    val taxBeforeCredits: BigDecimal,
    val pointsCredit: BigDecimal,
    val pensionCredit: BigDecimal,
    val incomeTax: BigDecimal,
    val nationalInsurance: BigDecimal,
    val healthInsurance: BigDecimal,
) {
    val gross: BigDecimal get() = wages + vacation + bonus + travel + fuel
    val net: BigDecimal get() = gross - deductions - pension - training - incomeTax - nationalInsurance - healthInsurance
}

// Israel Tax Authority, January 2026 onward after bracket widening, including the employment-income surtax.
// https://www.gov.il/he/pages/pa110123-1
private val incomeTaxBands = listOf(
    7_010 to 10, 10_060 to 14, 19_000 to 20, 25_100 to 31, 46_690 to 35, 60_130 to 47,
)
private val creditPointValue = BigDecimal(242)
private val qualifyingPensionIncome = BigDecimal(9_700)

// Standard resident employee rates, age 18 to retirement, January 2026 onward.
// https://www.btl.gov.il/ENGLISH%20HOMEPAGE/INSURANCE/RATESANDAMOUNT/Pages/forSalaried.aspx
private val reducedInsuranceLimit = BigDecimal(7_703)
private val insuranceCeiling = BigDecimal(51_910)

internal fun estimateIsraeliPayroll(
    month: YearMonth, shifts: List<Shift>, jobs: List<Job>, quarterPoints: Int,
    includeIncomeTax: Boolean = true,
): IsraeliPayrollEstimate? {
    if (month.year != ISRAEL_PAYROLL_YEAR) return null
    val monthly = shifts.filter { it.endMillis != null && YearMonth.from(it.localStart()) == month }
    val included = monthly.filter { it.currencyCode == ILS }
    if (monthly.isNotEmpty() && included.isEmpty()) return null

    val entries = included.map { it to it.earnings() }
    val monthlyFuel = monthly.monthlyFuelTopUps().filterValues { it.currencyCode == ILS }
    val jobsById = jobs.associateBy { it.id }
    fun contributions(shift: Shift): EmployeeContributions =
        shift.payroll?.contributions ?: jobsById[shift.jobId]?.takeIf { it.currencyCode == ILS }
            ?.payrollDefaults?.contributions ?: EmployeeContributions()
    val wages = entries.sumOf { (_, earnings) -> earnings.wages }
    val vacation = entries.sumOf { (_, earnings) -> earnings.vacation }
    val bonus = entries.sumOf { (_, earnings) -> earnings.bonus }
    val travel = entries.sumOf { (_, earnings) -> earnings.travel }
    val fuel = entries.sumOf { (_, earnings) -> earnings.fuel } + monthlyFuel.values.sumOf { it.amount }
    val deductions = entries.sumOf { (_, earnings) -> earnings.deductions }
    val gross = wages + vacation + bonus + travel + fuel
    val pension = entries.sumOf { (shift, earnings) ->
        earnings.contributionBase * contributions(shift).pensionPercent.toBigDecimal().movePointLeft(2)
    }.toAgorot()
    val training = entries.sumOf { (shift, earnings) ->
        earnings.contributionBase * contributions(shift).trainingPercent.toBigDecimal().movePointLeft(2)
    }.toAgorot()

    val creditFromPoints = creditPointValue * BigDecimal(quarterPoints).divide(BigDecimal(4))
    val pensionBase = entries.sumOf { (shift, earnings) ->
        if (contributions(shift).pensionPercent.toBigDecimal().signum() > 0) {
            earnings.contributionBase
        } else {
            BigDecimal.ZERO
        }
    }
    // The standard employee pension credit uses the qualifying-income cap and the section 45A amount floor.
    val pensionCreditLimit = pensionBase.min(qualifyingPensionIncome).byPercent(7).max(BigDecimal(189))
    val creditFromPension = pension.min(pensionCreditLimit).byPercent(35)
    val beforeCredits = progressiveIncomeTax(gross.setScale(0, RoundingMode.HALF_UP))
    val incomeTax = if (includeIncomeTax) {
        (beforeCredits - creditFromPoints - creditFromPension)
            .max(BigDecimal.ZERO).setScale(0, RoundingMode.HALF_UP)
    } else {
        BigDecimal.ZERO
    }

    // Hourly workers use the minimum contribution base for the hours worked, when higher than the gross.
    val minimumHourlyBase = if (month.monthValue < 4) "33.58" else "34.64"
    val insured = entries.groupBy { it.first.jobId }.entries.sumOf { (jobId, jobEntries) ->
        val jobGross = jobEntries.sumOf { it.second.gross } + (monthlyFuel[jobId to month]?.amount ?: BigDecimal.ZERO)
        val jobMillis = jobEntries.sumOf { it.first.completedDurationMillis }
        jobGross.max(estimatedPay(jobMillis, minimumHourlyBase, ILS))
    }.min(insuranceCeiling)
    val lower = insured.min(reducedInsuranceLimit)
    val upper = insured - lower
    return IsraeliPayrollEstimate(
        wages, vacation, bonus, travel, fuel, deductions, pension, training,
        beforeCredits.toAgorot(), creditFromPoints.toAgorot(), creditFromPension.toAgorot(), incomeTax,
        nationalInsurance = (lower * BigDecimal("0.0104") + upper.byPercent(7)).toAgorot(),
        healthInsurance = (lower * BigDecimal("0.0323") + upper * BigDecimal("0.0517")).toAgorot(),
    )
}

private fun progressiveIncomeTax(gross: BigDecimal): BigDecimal {
    var previous = BigDecimal.ZERO
    var tax = BigDecimal.ZERO
    for ((limit, rate) in incomeTaxBands) {
        val ceiling = BigDecimal(limit)
        tax += (gross.min(ceiling) - previous).max(BigDecimal.ZERO).byPercent(rate)
        previous = ceiling
    }
    return tax + (gross - previous).max(BigDecimal.ZERO).byPercent(50)
}

private fun BigDecimal.byPercent(percent: Int) = (this * BigDecimal(percent)).movePointLeft(2)

private fun BigDecimal.toAgorot() = setScale(2, RoundingMode.HALF_UP)
