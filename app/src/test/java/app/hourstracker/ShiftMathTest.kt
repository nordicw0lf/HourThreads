package app.hourstracker

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShiftMathTest {
    @Test
    fun overnightShiftKeepsStartDateAcrossMonthBoundaryAndDeductsBreak() {
        val date = LocalDate.of(2026, 11, 30)
        val start = LocalTime.of(18, 0)
        val end = LocalTime.of(2, 30)
        val endDate = defaultEndDate(date, start, end)
        val zone = ZoneId.of("Asia/Jerusalem")
        val inTime = resolveTime(date, start, zone, null).toInstant().toEpochMilli()
        val outTime = resolveTime(endDate, end, zone, null).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 12, 1), endDate)
        assertEquals(date, resolveTime(date, start, zone, null).toLocalDate())
        assertTrue(isValidShift(inTime, outTime, 30))
        assertEquals(8 * 3_600_000L, paidMillis(inTime, outTime, 30))
    }

    @Test
    fun paidTimeUsesElapsedTimeAcrossDaylightSavingChanges() {
        val zone = ZoneId.of("America/New_York")
        val date = LocalDate.of(2026, 3, 8)
        val start = resolveTime(date, LocalTime.of(1, 0), zone, null).toInstant().toEpochMilli()
        val end = resolveTime(date, LocalTime.of(4, 0), zone, null).toInstant().toEpochMilli()
        assertEquals(2 * 3_600_000L, paidMillis(start, end, 0))
        assertFalse(isValidShift(start, end, 121))
    }

    @Test
    fun selectedCurrencyControlsPayPrecisionRegardlessOfUiLocale() {
        val duration = 5_400_000L
        assertEquals(BigDecimal("27.75"), estimatedPay(duration, "18.50", "ILS"))
        assertEquals(BigDecimal("28"), estimatedPay(duration, "18.50", "JPY"))
        val yenSymbol = Currency.getInstance("JPY").getSymbol(Locale.JAPAN)
        assertEquals("${yenSymbol}28", formatMoney(BigDecimal("28"), "JPY", Locale.JAPAN))
        val shekelSymbol = Currency.getInstance("ILS").getSymbol(Locale.US)
        assertTrue(formatMoney(BigDecimal("27.75"), "ILS", Locale.US).contains(shekelSymbol))
        assertTrue(availableCurrencies(Locale.US).map { it.currencyCode }.containsAll(listOf("ILS", "USD", "JPY", "CNY")))
    }

    @Test
    fun rateInputUsesLocaleDecimalSeparatorAndRequiresTheEntireInput() {
        assertEquals(BigDecimal("18.50"), parseRate("18,50", Locale.GERMANY))
        assertNull(parseRate("18.50", Locale.GERMANY))
        assertNull(parseRate("18.50abc", Locale.US))
    }
}
