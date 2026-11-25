package app.hourstracker

import java.math.BigDecimal
import java.util.Locale

internal val Shift.completedDurationMillis: Long
    get() = paidMillis(startMillis, checkNotNull(endMillis), breakMinutes)

internal data class ShiftTotals(val durationMillis: Long, val payByCurrency: Map<String, BigDecimal>) {
    fun formattedPay(defaultCurrency: String, locale: Locale): List<String> =
        payByCurrency.ifEmpty { mapOf(defaultCurrency to BigDecimal.ZERO) }
            .map { (currency, pay) -> formatMoney(pay, currency, locale) }
}

internal fun List<Shift>.shiftTotals(includeMonthlyFuel: Boolean = true): ShiftTotals {
    val fuelByCurrency = if (includeMonthlyFuel) monthlyFuelTopUps().values.groupBy { it.currencyCode } else emptyMap()
    val payByCurrency = groupBy { it.currencyCode }.toSortedMap().mapValues { (currency, shifts) ->
        val gross = shifts.sumOf { it.earnings().gross }
        gross + fuelByCurrency[currency].orEmpty().sumOf { it.amount }
    }
    return ShiftTotals(sumOf { it.completedDurationMillis }, payByCurrency)
}
