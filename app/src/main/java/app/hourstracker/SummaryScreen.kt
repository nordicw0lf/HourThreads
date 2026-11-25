package app.hourstracker

import android.text.format.DateFormat
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun SummaryScreen(
    shifts: List<Shift>,
    jobs: List<Job>,
    preferences: Preferences,
    saving: Boolean,
    onIncomeTaxChange: (Boolean) -> Unit,
    onHideIncomeTaxWarning: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val currencyCode = preferences.currencyCode
    val quarterPoints = preferences.creditQuarterPoints
    val includeIncomeTax = preferences.includeIncomeTax
    var selectedMonth by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    var yearly by rememberSaveable { mutableStateOf(false) }
    val locale = displayLocale()
    val period = YearMonth.parse(selectedMonth)
    val months = remember(shifts, period.year) {
        shifts.groupBy { YearMonth.from(it.localStart()) }.filterKeys { it.year == period.year }
    }
    val included = if (yearly) months.values.flatten() else months[period].orEmpty()
    val totals = remember(included) { included.shiftTotals() }
    val shiftsByJob = remember(included) { included.groupBy { it.jobId } }
    val hasIsraeliPayroll = included.any { it.currencyCode == ILS } ||
        (included.isEmpty() && jobs.any { it.currencyCode == ILS })
    val payroll = remember(period, yearly, included, jobs, hasIsraeliPayroll, quarterPoints, includeIncomeTax) {
        if (yearly || !hasIsraeliPayroll) null
        else estimateIsraeliPayroll(period, included, jobs, quarterPoints, includeIncomeTax)
    }
    val includesVacation = remember(included) { included.any { it.isVacation } }
    val periods = remember(period, yearly) {
        if (yearly) (1..12).map { YearMonth.of(period.year, it) } else listOf(period)
    }

    key(period, yearly) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "controls") {
                SummaryControls(
                    period = period,
                    yearly = yearly,
                    locale = locale,
                    onModeChange = { yearly = it },
                    onPeriodChange = { selectedMonth = it.toString() },
                )
            }
            item(key = "totals") {
                LedgerCard(Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.total_hours), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LedgerNumber(durationText(totals.durationMillis), 62.sp)
                    for (amount in totals.formattedPay(currencyCode, locale)) {
                        Text(
                            stringResource(R.string.summary_pay, amount),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                    Text(
                        pluralStringResource(
                            if (includesVacation) R.plurals.payroll_entry_count else R.plurals.shift_count,
                            included.size, included.size,
                        ),
                        modifier = Modifier.padding(top = 12.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
            if (payroll != null) {
                item(key = "payroll") {
                    PayrollEstimateCard(
                        estimate = payroll,
                        multipleJobs = included.filter { it.currencyCode == ILS }.distinctBy { it.jobId }.size > 1,
                        excludesOtherCurrencies = totals.payByCurrency.size > 1,
                        includeIncomeTax = includeIncomeTax,
                        hideIncomeTaxWarning = preferences.hideIncomeTaxWarning,
                        saving = saving,
                        onIncomeTaxChange = onIncomeTaxChange,
                        onHideIncomeTaxWarning = onHideIncomeTaxWarning,
                    )
                }
            } else if (!yearly && hasIsraeliPayroll && period.year != ISRAEL_PAYROLL_YEAR) {
                item(key = "payroll-year") {
                    Text(
                        stringResource(R.string.payroll_other_year, ISRAEL_PAYROLL_YEAR),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            if (totals.durationMillis > 0) {
                item(key = "chart") {
                    val values = if (yearly) {
                        periods.map { month -> months[month].orEmpty().sumOf { it.completedDurationMillis } }
                    } else {
                        val days = included.groupBy { it.localStart().dayOfMonth }
                        (1..period.lengthOfMonth()).map { day -> days[day].orEmpty().sumOf { it.completedDurationMillis } }
                    }
                    HoursChart(values, yearly, locale)
                }
            }
            if (yearly) {
                items(periods, key = { it.toString() }) { month ->
                    SummaryRow(
                        label = month.month.getDisplayName(TextStyle.FULL_STANDALONE, locale),
                        totals = months[month].orEmpty().shiftTotals(),
                        currencyCode = currencyCode,
                        modifier = Modifier.clickable {
                            selectedMonth = month.toString()
                            yearly = false
                        },
                    )
                }
            } else if (included.isEmpty()) {
                item(key = "empty") {
                    Text(stringResource(R.string.empty_log), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                item(key = "jobs") { SummaryHeading(stringResource(R.string.summary_by_job)) }
                items(jobs.filter { it.id in shiftsByJob }, key = { "job-${it.id}" }) { job ->
                    SummaryRow(
                        label = job.name,
                        totals = shiftsByJob.getValue(job.id).shiftTotals(),
                        currencyCode = job.currencyCode,
                        jobId = job.id,
                    )
                }
                item(key = "shifts") {
                    SummaryHeading(stringResource(if (includesVacation) R.string.payroll_entries else R.string.summary_shifts))
                }
                shiftWeekItems(included, jobs, locale, onEdit)
            }
        }
    }
}

@Composable
private fun SummaryControls(
    period: YearMonth,
    yearly: Boolean,
    locale: Locale,
    onModeChange: (Boolean) -> Unit,
    onPeriodChange: (YearMonth) -> Unit,
) {
    val pattern = DateFormat.getBestDateTimePattern(locale, if (yearly) "y" else "yMMMM")
    val title = period.atDay(1).format(DateTimeFormatter.ofPattern(pattern, locale))
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PageTitle(stringResource(R.string.summary))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            LedgerFilterChip(stringResource(R.string.summary_month), !yearly, { onModeChange(false) })
            LedgerFilterChip(stringResource(R.string.summary_year), yearly, { onModeChange(true) })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            ArrowButton(stringResource(if (yearly) R.string.previous_year else R.string.previous_month), forward = false) {
                onPeriodChange(if (yearly) period.minusYears(1) else period.minusMonths(1))
            }
            Text(
                title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            ArrowButton(stringResource(if (yearly) R.string.next_year else R.string.next_month), forward = true) {
                onPeriodChange(if (yearly) period.plusYears(1) else period.plusMonths(1))
            }
        }
    }
}

@Composable
private fun SummaryHeading(label: String) {
    Text(
        label, Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
    )
}

@Composable
private fun SummaryRow(
    label: String,
    totals: ShiftTotals,
    currencyCode: String,
    modifier: Modifier = Modifier,
    jobId: Long? = null,
) {
    val locale = displayLocale()
    LedgerCard(modifier.fillMaxWidth(), PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (jobId != null) JobMarker(jobId)
            Text(label, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    durationText(totals.durationMillis),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                for (amount in totals.formattedPay(currencyCode, locale)) {
                    Text(
                        stringResource(R.string.shift_estimate, amount),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun HoursChart(values: List<Long>, yearly: Boolean, locale: Locale) {
    val maxValue = values.maxOrNull()?.takeIf { it > 0 } ?: return
    val title = stringResource(if (yearly) R.string.hours_by_month else R.string.hours_by_day)
    val colors = MaterialTheme.colorScheme
    val accent = LocalChartAccent.current
    val outline = colors.outlineVariant
    val axisFormat = NumberFormat.getIntegerInstance(locale)
    LedgerCard(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        Canvas(Modifier.fillMaxWidth().height(128.dp)) {
            val stride = size.width / values.size
            val barWidth = minOf(stride * .72f, 18.dp.toPx())
            val chartHeight = size.height - 8.dp.toPx()
            for (line in 1..3) {
                val y = size.height - 1f - chartHeight * (line - 1) / 3
                drawLine(outline.copy(alpha = .66f), Offset.Zero.copy(y = y), Offset(size.width, y))
            }
            values.forEachIndexed { index, value ->
                if (value > 0) {
                    val barHeight = ((value.toDouble() / maxValue) * chartHeight).toFloat().coerceAtLeast(2.dp.toPx())
                    val column = if (layoutDirection == LayoutDirection.Rtl) values.lastIndex - index else index
                    drawRoundRect(
                        brush = Brush.verticalGradient(
                            listOf(accent, colors.secondary),
                            startY = size.height - barHeight,
                            endY = size.height,
                        ),
                        topLeft = Offset(column * stride + (stride - barWidth) / 2, size.height - barHeight),
                        size = Size(barWidth, barHeight),
                        cornerRadius = CornerRadius(barWidth / 2),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                if (yearly) Month.JANUARY.getDisplayName(TextStyle.SHORT_STANDALONE, locale) else axisFormat.format(1),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                if (yearly) Month.DECEMBER.getDisplayName(TextStyle.SHORT_STANDALONE, locale) else axisFormat.format(values.size),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}
