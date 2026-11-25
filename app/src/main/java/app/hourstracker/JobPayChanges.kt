package app.hourstracker

internal class JobPayChanges(private val previous: Job, private val updated: Job) {
    private val oldDefaults = previous.payrollDefaults
    private val newDefaults = updated.payrollDefaults
    private val oldAmounts = oldDefaults.adjustments
    private val newAmounts = newDefaults.adjustments
    private val rateChanged = !previous.hourlyRate.sameAmount(updated.hourlyRate)
    private val currencyChanged = previous.currencyCode != updated.currencyCode
    private val vacationChanged = !oldDefaults.vacationPay.sameAmount(newDefaults.vacationPay)
    private val deductionsChanged = !oldAmounts.deductions.sameAmount(newAmounts.deductions)
    private val bonusChanged = !oldAmounts.bonus.sameAmount(newAmounts.bonus)
    private val commuteChanged = !oldAmounts.travel.sameAmount(newAmounts.travel) ||
        oldAmounts.fuelReimbursement != newAmounts.fuelReimbursement ||
        oldAmounts.fuelPaidMonthly != newAmounts.fuelPaidMonthly
    private val pensionChanged =
        !oldDefaults.contributions.pensionPercent.sameAmount(newDefaults.contributions.pensionPercent)
    private val trainingChanged =
        !oldDefaults.contributions.trainingPercent.sameAmount(newDefaults.contributions.trainingPercent)

    val contributionsChanged = pensionChanged || trainingChanged
    val preserveLegacyContributions = contributionsChanged || currencyChanged
    val affectsPastPay = currencyChanged || rateChanged || vacationChanged || deductionsChanged || bonusChanged ||
        commuteChanged || contributionsChanged

    fun updatedShifts(shifts: List<Shift>, recalculatePast: Boolean): List<Shift> {
        val jobShifts = shifts.filter { it.jobId == updated.id }
        val applyChanges = recalculatePast && affectsPastPay
        val eligible = jobShifts.filter { currencyChanged || it.currencyCode == updated.currencyCode }
        val commuteUpdates = if (applyChanges && commuteChanged) commuteUpdates(eligible) else emptyMap()

        return jobShifts.mapNotNull { shift ->
            val recalculated = if (applyChanges && shift.endMillis != null &&
                (currencyChanged || shift.currencyCode == updated.currencyCode)
            ) {
                recalculate(shift, commuteUpdates[shift.id])
            } else if (preserveLegacyContributions && shift.payroll == null) {
                // Older entries otherwise pick up the latest contribution percentages at summary time.
                shift.copy(payroll = ShiftPayroll(contributions = oldDefaults.contributions))
            } else {
                shift
            }
            recalculated.takeUnless { it == shift }
        }
    }

    private fun commuteUpdates(shifts: List<Shift>): Map<Long, ShiftAdjustments> {
        val worked = shifts.filter { it.endMillis != null && !it.isVacation }
        if (newAmounts.fuelPaidMonthly) {
            return worked.filter { shift ->
                val amounts = shift.payrollDetails.adjustments
                val oldFuelDefault = amounts.fuelReimbursement &&
                    oldDefaults.legacyDailyFuelDefault?.let { amounts.travel.sameAmount(it) } == true
                amounts.fuelPaidMonthly || amounts.travel.sameAmount("0") ||
                    amounts.matchesCommute(oldAmounts) || oldFuelDefault
            }.associate { it.id to newAmounts }
        }

        return buildMap {
            for (day in worked.groupBy { it.localStart().toLocalDate() }.values) {
                val monthly = day.filter { it.payrollDetails.adjustments.fuelPaidMonthly }
                val paid = day.filter { shift ->
                    val amounts = shift.payrollDetails.adjustments
                    !amounts.fuelPaidMonthly && !amounts.travel.sameAmount("0")
                }
                val match = paid.firstOrNull { it.payrollDetails.adjustments.matchesCommute(oldAmounts) }
                val shouldAddDaily = paid.isEmpty() &&
                    (monthly.isNotEmpty() || oldAmounts.travel.sameAmount("0")) && !newAmounts.travel.sameAmount("0")
                for (shift in monthly) put(shift.id, ShiftAdjustments())
                if (match != null) {
                    put(match.id, newAmounts)
                } else if (shouldAddDaily) {
                    val first = day.minWith(compareBy(Shift::startMillis, Shift::id))
                    put(first.id, newAmounts)
                }
            }
        }
    }

    private fun recalculate(shift: Shift, commute: ShiftAdjustments?): Shift {
        val saved = shift.payroll ?: ShiftPayroll(contributions = oldDefaults.contributions)
        val amounts = saved.adjustments
        val contributions = saved.contributions
        val updatedPayroll = saved.copy(
            adjustments = if (shift.isVacation) amounts else amounts.copy(
                deductions = if (deductionsChanged && amounts.deductions.sameAmount(oldAmounts.deductions)) {
                    newAmounts.deductions
                } else {
                    amounts.deductions
                },
                bonus = if (bonusChanged && amounts.bonus.sameAmount(oldAmounts.bonus)) newAmounts.bonus else amounts.bonus,
                travel = commute?.travel ?: amounts.travel,
                fuelReimbursement = commute?.fuelReimbursement ?: amounts.fuelReimbursement,
                fuelPaidMonthly = commute?.fuelPaidMonthly ?: amounts.fuelPaidMonthly,
            ),
            contributions = contributions.copy(
                pensionPercent = if (pensionChanged) newDefaults.contributions.pensionPercent else contributions.pensionPercent,
                trainingPercent = if (trainingChanged) newDefaults.contributions.trainingPercent else contributions.trainingPercent,
            ),
            vacationPay = saved.vacationPay?.let {
                if (vacationChanged && !newDefaults.vacationPay.sameAmount("0") && it.sameAmount(oldDefaults.vacationPay)) {
                    newDefaults.vacationPay
                } else {
                    it
                }
            },
        )
        return shift.copy(
            hourlyRate = if (rateChanged && !shift.isVacation) updated.hourlyRate else shift.hourlyRate,
            currencyCode = if (currencyChanged) updated.currencyCode else shift.currencyCode,
            payroll = if (updatedPayroll != saved || (preserveLegacyContributions && shift.payroll == null)) {
                updatedPayroll
            } else {
                shift.payroll
            },
        )
    }
}

private fun String.sameAmount(other: String): Boolean = toBigDecimal().compareTo(other.toBigDecimal()) == 0

private fun ShiftAdjustments.matchesCommute(other: ShiftAdjustments): Boolean =
    travel.sameAmount(other.travel) && fuelReimbursement == other.fuelReimbursement &&
        fuelPaidMonthly == other.fuelPaidMonthly
