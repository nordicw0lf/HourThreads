package app.hourstracker

import android.text.format.DateFormat
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

@Composable
internal fun ShiftLog(
    shifts: List<Shift>,
    jobs: List<Job>,
    currencyCode: String,
    onBackup: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var selectedYear by rememberSaveable { mutableStateOf<Int?>(null) }
    var selectedMonth by rememberSaveable { mutableStateOf<Month?>(null) }
    var selectedJobId by rememberSaveable { mutableStateOf<Long?>(null) }
    var expandedShiftId by rememberSaveable { mutableStateOf<Long?>(null) }
    val locale = displayLocale()
    val today = LocalDate.now()
    val months = remember(shifts) {
        shifts.groupBy { YearMonth.from(it.localStart()) }.toSortedMap(reverseOrder())
    }
    val recordedYears = remember(months) { months.keys.map { it.year }.distinct() }
    val year = selectedYear ?: today.year.takeIf { it in recordedYears } ?: recordedYears.firstOrNull() ?: today.year
    val years = remember(recordedYears, today.year, year) {
        (recordedYears + today.year + year).distinct().sortedDescending()
    }
    val jobsById = remember(jobs) { jobs.associateBy { it.id } }
    val visibleMonths = remember(months, year, selectedMonth, selectedJobId) {
        months.filterKeys { it.year == year && (selectedMonth == null || it.month == selectedMonth) }
            .mapValues { (_, entries) -> entries.filter { selectedJobId == null || it.jobId == selectedJobId } }
            .filterValues { it.isNotEmpty() }
    }
    val visibleShifts = remember(visibleMonths) { visibleMonths.values.flatten() }
    val monthFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "yMMMM"), locale)
    }
    val dateFormat = remember(locale) { shiftDateFormat(locale) }
    val separateJobs = selectedJobId == null && jobs.size > 1

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LogFilters(
                year, years, selectedMonth, jobsById[selectedJobId], jobs, locale, onBackup,
                onYearChange = { selectedYear = it },
                onMonthChange = { selectedMonth = it },
                onJobChange = { selectedJobId = it?.id },
            )
        },
        bottomBar = { LogTotals(visibleShifts, jobsById[selectedJobId]?.currencyCode ?: currencyCode, locale) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                AddIcon(stringResource(R.string.add_shift))
            }
        },
    ) { padding ->
        key(year, selectedMonth, selectedJobId) {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 24.dp, top = 10.dp, end = 24.dp, bottom = 88.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (visibleShifts.isEmpty()) {
                    item {
                        LedgerCard(Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.empty_log), style = MaterialTheme.typography.titleLarge)
                            Text(
                                stringResource(if (shifts.isEmpty()) R.string.empty_log_detail else R.string.empty_log_filters),
                                Modifier.padding(top = 8.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                for ((month, entries) in visibleMonths) {
                    if (selectedMonth == null) {
                        item(key = "month-$month") {
                            Text(
                                month.atDay(1).format(monthFormat),
                                modifier = Modifier.padding(top = 6.dp).semantics { heading() },
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                    for ((jobId, jobShifts) in entries.groupBy { it.jobId }) {
                        if (separateJobs) {
                            item(key = "job-$month-$jobId") {
                                Row(
                                    modifier = Modifier.padding(start = 2.dp, top = 6.dp).semantics { heading() },
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    JobMarker(jobId, badged = false)
                                    Text(
                                        jobsById[jobId]?.name ?: stringResource(R.string.unknown_job),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                }
                            }
                        }
                        items(jobShifts, key = { it.id }) { shift ->
                            val expanded = expandedShiftId == shift.id
                            LogShiftRow(
                                shift = shift,
                                jobName = jobsById[jobId]?.name ?: stringResource(R.string.unknown_job),
                                dateFormat = dateFormat,
                                locale = locale,
                                expanded = expanded,
                                onToggle = { expandedShiftId = if (expanded) null else shift.id },
                                onEdit = { onEdit(shift.id) },
                                onDelete = { onDelete(shift.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogFilters(
    year: Int,
    years: List<Int>,
    month: Month?,
    job: Job?,
    jobs: List<Job>,
    locale: Locale,
    onBackup: () -> Unit,
    onYearChange: (Int) -> Unit,
    onMonthChange: (Month?) -> Unit,
    onJobChange: (Job?) -> Unit,
) {
    val yearFormat = remember(locale) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "y"), locale)
    }
    val allJobs = stringResource(R.string.all_jobs)
    val allMonths = stringResource(R.string.all_months)
    Column(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp, bottom = 4.dp)) {
            PageTitle(stringResource(R.string.log)) {
                IconButton(onClick = onBackup) {
                    Icon(painterResource(R.drawable.ic_backup), contentDescription = stringResource(R.string.backup_title))
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LogFilterMenu(
                    value = year, options = years,
                    label = { LocalDate.of(it, 1, 1).format(yearFormat) },
                    onSelect = onYearChange,
                )
                LogFilterMenu(
                    value = job, options = listOf(null) + jobs,
                    label = { it?.name ?: allJobs },
                    onSelect = onJobChange,
                    modifier = Modifier.weight(1f),
                )
            }
            LogFilterMenu(
                value = month, options = listOf(null) + Month.entries,
                label = { it?.getDisplayName(TextStyle.FULL_STANDALONE, locale) ?: allMonths },
                onSelect = onMonthChange,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun <T> LogFilterMenu(
    value: T,
    options: List<T>,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(
            onClick = { open = true },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            contentPadding = PaddingValues(start = 14.dp, end = 10.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Text(label(value), Modifier.weight(1f, fill = false), maxLines = 1, overflow = TextOverflow.Ellipsis)
            LogChevron(open, Modifier.padding(start = 6.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Text(
                            label(option),
                            fontWeight = if (option == value) FontWeight.Bold else FontWeight.Normal,
                            color = if (option == value) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        )
                    },
                    onClick = {
                        open = false
                        onSelect(option)
                    },
                )
            }
        }
    }
}

@Composable
private fun LogTotals(shifts: List<Shift>, currencyCode: String, locale: Locale) {
    val totals = remember(shifts) { shifts.shiftTotals() }
    val count = remember(shifts.size, locale) { NumberFormat.getIntegerInstance(locale).format(shifts.size) }
    val amount = if (totals.payByCurrency.size > 1) stringResource(R.string.multiple_currencies)
        else totals.formattedPay(currencyCode, locale).single()
    val countLabel = if (shifts.any { it.isVacation }) R.string.payroll_entries else R.string.summary_shifts
    val colors = MaterialTheme.colorScheme
    Surface(color = colors.surface) {
        Column {
            HorizontalDivider(color = colors.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                LogTotal(stringResource(countLabel), count, Modifier.weight(.85f))
                LogTotal(stringResource(R.string.total_hours), durationText(totals.durationMillis), Modifier.weight(1f))
                LogTotal(stringResource(R.string.estimated_pay), amount, Modifier.weight(1.15f), accent = true)
            }
        }
    }
}

@Composable
private fun LogTotal(label: String, value: String, modifier: Modifier, accent: Boolean = false) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (accent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun LogShiftRow(
    shift: Shift,
    jobName: String,
    dateFormat: DateTimeFormatter,
    locale: Locale,
    expanded: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val editLabel = stringResource(if (shift.isVacation) R.string.payroll_edit_vacation else R.string.edit_shift)
    val deleteLabel = stringResource(if (shift.isVacation) R.string.payroll_delete_vacation else R.string.delete_shift)
    LedgerCard(Modifier.fillMaxWidth(), PaddingValues(0.dp)) {
        Box {
            Row(
                modifier = Modifier.fillMaxWidth().combinedClickable(
                    role = Role.Button,
                    onClickLabel = stringResource(if (expanded) R.string.hide_shift_details else R.string.show_shift_details),
                    onClick = onToggle,
                    onLongClickLabel = stringResource(R.string.shift_actions),
                    onLongClick = { menuOpen = true },
                ).heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShiftDateAndTime(shift, dateFormat, locale, Modifier.weight(1f), prominentDate = true)
                ShiftAmounts(shift, locale)
                LogChevron(expanded)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(editLabel) },
                    onClick = {
                        menuOpen = false
                        onEdit()
                    },
                )
                DropdownMenuItem(
                    text = { Text(deleteLabel, color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    },
                )
            }
        }
        if (expanded) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Column(
                Modifier.fillMaxWidth().padding(start = 14.dp, top = 12.dp, end = 14.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    JobMarker(shift.jobId, badged = false)
                    Text(jobName, style = MaterialTheme.typography.titleMedium)
                }
                if (!shift.isVacation) {
                    Text(
                        stringResource(R.string.shift_break_detail, shift.breakMinutes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    val earnings = remember(shift) { shift.earnings() }
                    val adjustments = listOf(
                        R.string.payroll_shift_tip_value to earnings.bonus,
                        R.string.payroll_shift_travel_value to earnings.travel,
                        R.string.payroll_shift_fuel_value to earnings.fuel,
                        R.string.payroll_shift_deduction_value to earnings.deductions,
                    )
                    for ((label, value) in adjustments) {
                        if (value.signum() > 0) {
                            Text(
                                stringResource(label, formatMoney(value, shift.currencyCode, locale)),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (shift.note.isNotBlank()) {
                    Text(shift.note, style = MaterialTheme.typography.bodyMedium)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                        Text(editLabel)
                    }
                    TextButton(onClick = onDelete, modifier = Modifier.weight(1f)) {
                        Text(deleteLabel, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun LogChevron(up: Boolean, modifier: Modifier = Modifier) {
    Icon(
        painterResource(R.drawable.ic_expand_more),
        contentDescription = null,
        modifier = modifier.size(18.dp).rotate(if (up) 180f else 0f),
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

internal fun LazyListScope.shiftWeekItems(
    shifts: List<Shift>,
    jobs: List<Job>,
    locale: Locale,
    onEdit: (Long) -> Unit,
) {
    val weekStart = WeekFields.of(locale).firstDayOfWeek
    val weekFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val dateFormat = shiftDateFormat(locale)
    val jobsById = jobs.associateBy { it.id }
    val weeks = shifts.groupBy { it.localStart().toLocalDate().with(TemporalAdjusters.previousOrSame(weekStart)) }
        .toSortedMap(reverseOrder())

    for ((week, entries) in weeks) {
        item(key = "week-$week") {
            Text(
                stringResource(R.string.week_of, week.format(weekFormat)),
                modifier = Modifier.padding(top = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        items(entries, key = { it.id }) { shift ->
            LedgerCard(Modifier.fillMaxWidth().clickable { onEdit(shift.id) }, PaddingValues(14.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    JobMarker(shift.jobId)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            jobsById[shift.jobId]?.name ?: stringResource(R.string.unknown_job),
                            style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                        ShiftDateAndTime(shift, dateFormat, locale)
                    }
                    ShiftAmounts(shift, locale)
                }
            }
        }
    }
}

private fun shiftDateFormat(locale: Locale) =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale)

@Composable
private fun ShiftDateAndTime(
    shift: Shift,
    dateFormat: DateTimeFormatter,
    locale: Locale,
    modifier: Modifier = Modifier,
    prominentDate: Boolean = false,
) {
    val start = shift.localStart()
    val end = checkNotNull(shift.localEnd())
    val timeFormat = remember(locale) { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale) }
    val dayDifference = ChronoUnit.DAYS.between(start.toLocalDate(), end.toLocalDate())
    val startTime = start.format(timeFormat)
    val endTime = end.format(timeFormat)
    val timeRange = if (shift.isVacation) {
        stringResource(R.string.payroll_paid_vacation)
    } else if (dayDifference > 0) {
        stringResource(R.string.time_range_overnight, startTime, endTime, dayDifference)
    } else {
        stringResource(R.string.time_range, startTime, endTime)
    }
    val colors = MaterialTheme.colorScheme
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            start.format(dateFormat),
            style = if (prominentDate) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
            color = if (prominentDate) colors.onSurface else colors.onSurfaceVariant,
        )
        Text(timeRange, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
    }
}

@Composable
private fun ShiftAmounts(shift: Shift, locale: Locale) {
    val duration = shift.completedDurationMillis
    val amount = remember(shift, locale) {
        formatMoney(shift.earnings().gross, shift.currencyCode, locale)
    }
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!shift.isVacation) {
            Text(durationText(duration), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.shift_estimate, amount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}
