package app.hourstracker

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.NumberFormat
import java.text.ParsePosition
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Currency
import java.util.Locale
import kotlinx.serialization.Serializable

private const val MILLIS_PER_MINUTE = 60_000L
private val millisPerHour = BigDecimal.valueOf(3_600_000L)
private val commonCurrencies = listOf("ILS", "USD", "EUR", "GBP", "JPY", "CNY", "CAD", "AUD", "INR")

@Serializable
internal data class ManualShiftTimes(val startMinuteOfDay: Int, val endMinuteOfDay: Int) {
    init {
        require(startMinuteOfDay in 0 until 24 * 60 && endMinuteOfDay in 0 until 24 * 60)
    }

    constructor(start: LocalTime, end: LocalTime) : this(start.toSecondOfDay() / 60, end.toSecondOfDay() / 60)

    fun startTime(): LocalTime = LocalTime.MIDNIGHT.plusMinutes(startMinuteOfDay.toLong())

    fun endTime(): LocalTime = LocalTime.MIDNIGHT.plusMinutes(endMinuteOfDay.toLong())
}

internal fun defaultCurrency(locale: Locale): Currency =
    runCatching { Currency.getInstance(locale) }.getOrNull()
        ?.takeIf { it.defaultFractionDigits >= 0 } ?: Currency.getInstance("USD")

internal fun availableCurrencies(locale: Locale): List<Currency> {
    val available = Currency.getAvailableCurrencies()
        .filter { it.defaultFractionDigits >= 0 }
        .associateBy { it.currencyCode }
    val featured = listOf(defaultCurrency(locale).currencyCode) + commonCurrencies
    val featuredCurrencies = featured.distinct().mapNotNull(available::get)
    return featuredCurrencies + available.values
        .filterNot { it.currencyCode in featured }
        .sortedBy { it.currencyCode }
}

internal fun parseRate(input: String, locale: Locale): BigDecimal? {
    val trimmed = input.trim()
    val format = NumberFormat.getNumberInstance(locale) as DecimalFormat
    format.isParseBigDecimal = true
    format.isGroupingUsed = false
    val position = ParsePosition(0)
    val value = format.parse(trimmed, position) as? BigDecimal ?: return null
    return value.takeIf { position.index == trimmed.length && it.signum() >= 0 }
}

internal fun formatMoney(amount: BigDecimal, currencyCode: String, locale: Locale): String {
    val unit = Currency.getInstance(currencyCode)
    return NumberFormat.getCurrencyInstance(locale).apply {
        currency = unit
        minimumFractionDigits = unit.defaultFractionDigits
        maximumFractionDigits = unit.defaultFractionDigits
    }.format(amount)
}

internal fun defaultEndDate(date: LocalDate, start: LocalTime, end: LocalTime): LocalDate =
    if (end < start) date.plusDays(1) else date

internal fun resolveTime(
    date: LocalDate,
    time: LocalTime,
    zone: ZoneId,
    originalMillis: Long?,
): ZonedDateTime {
    val originalOffset = originalMillis?.let { Instant.ofEpochMilli(it).atZone(zone).offset }
    return ZonedDateTime.ofLocal(date.atTime(time), zone, originalOffset)
}

internal fun paidMillis(start: Long, end: Long, breakMinutes: Int): Long =
    (end - start - breakMinutes * MILLIS_PER_MINUTE).coerceAtLeast(0)

internal fun isValidShift(start: Long, end: Long, breakMinutes: Int): Boolean =
    end > start && breakMinutes >= 0 && breakMinutes * MILLIS_PER_MINUTE <= end - start

internal fun estimatedPay(durationMillis: Long, hourlyRate: String, currencyCode: String): BigDecimal =
    hourlyRate.toBigDecimal().multiply(BigDecimal.valueOf(durationMillis))
        .divide(millisPerHour, Currency.getInstance(currencyCode).defaultFractionDigits, RoundingMode.HALF_UP)
