package app.hourstracker

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import kotlinx.coroutines.delay

@Composable
internal fun HomeScreen(
    state: HoursState,
    currencyCode: String,
    saving: Boolean,
    onSettings: () -> Unit,
    onAddShift: () -> Unit,
    onStart: (Long) -> Unit,
    onStop: (Long, Long, Int, ShiftAdjustments, () -> Unit) -> Unit,
) {
    val active = state.activeShift
    val locale = displayLocale()
    val today = LocalDate.now()
    val dateLabel = today.format(DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "EEEMMMd"), locale))
    val weekStart = today.with(TemporalAdjusters.previousOrSame(WeekFields.of(locale).firstDayOfWeek))
    val week = state.finishedShifts.filter { shift ->
        val date = shift.localStart().toLocalDate()
        !date.isBefore(weekStart) && !date.isAfter(today)
    }
    val totals = week.shiftTotals(includeMonthlyFuel = false)
    val hours = listOf(durationText(totals.durationMillis))
    val amounts = totals.formattedPay(currencyCode, locale)
    var choosingJob by rememberSaveable { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle(stringResource(R.string.home)) {
            IconButton(onClick = onSettings) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = stringResource(R.string.settings),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (active != null) {
            val jobName = state.jobs.firstOrNull { it.id == active.jobId }?.name
                ?: stringResource(R.string.unknown_job)
            RunningTimer(active, jobName, dateLabel)
        } else {
            TimerCard(dateLabel, String.format(locale, "%02d:%02d:%02d", 0, 0, 0), stringResource(R.string.ready_title))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            if (maxWidth >= 270.dp && LocalDensity.current.fontScale <= 1.3f) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    StatCard(stringResource(R.string.week_hours), hours, Modifier.weight(1f))
                    StatCard(
                        stringResource(R.string.estimated_pay), amounts,
                        Modifier.weight(1f),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard(stringResource(R.string.week_hours), hours, Modifier.fillMaxWidth())
                    StatCard(
                        stringResource(R.string.estimated_pay), amounts,
                        Modifier.fillMaxWidth(),
                    )
                }
            }
        }
        if (active != null) {
            ClockOutAction(active, state.finishedShifts, saving, onStop)
        } else {
            PrimaryAction(stringResource(R.string.clock_in), !saving && state.jobs.isNotEmpty()) {
                val onlyJob = state.jobs.singleOrNull()
                if (onlyJob != null) onStart(onlyJob.id) else choosingJob = true
            }
        }
        TextButton(onClick = onAddShift, modifier = Modifier.align(Alignment.CenterHorizontally)) {
            Text(stringResource(R.string.add_shift))
        }
    }
    if (choosingJob) {
        JobPicker(
            state.jobs,
            onPick = {
                choosingJob = false
                onStart(it.id)
            },
            onDismiss = { choosingJob = false },
        )
    }
}

@Composable
private fun RunningTimer(shift: Shift, jobName: String, dateLabel: String) {
    val locale = displayLocale()
    val elapsed by produceState((System.currentTimeMillis() - shift.startMillis).coerceAtLeast(0), shift.startMillis) {
        while (true) {
            val now = (System.currentTimeMillis() - shift.startMillis).coerceAtLeast(0)
            value = now
            delay(1_000 - now % 1_000)
        }
    }
    val seconds = elapsed / 1_000
    val start = shift.localStart().format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale))
    TimerCard(
        dateLabel,
        String.format(locale, "%02d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60),
        stringResource(R.string.started_at, jobName, start),
        jobId = shift.jobId,
    )
}

@Composable
private fun TimerCard(date: String, time: String, detail: String, jobId: Long? = null) {
    val activeState = stringResource(R.string.shift_in_progress)
    LedgerCard(
        Modifier.fillMaxWidth().semantics { if (jobId != null) stateDescription = activeState },
        PaddingValues(horizontal = 20.dp, vertical = 26.dp),
    ) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            LedgerNumber(time, 76.sp, Modifier.padding(vertical = 9.dp), TextAlign.Center)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (jobId != null) JobMarker(jobId, badged = false)
                Text(detail, color = MaterialTheme.colorScheme.primary, textAlign = TextAlign.Center)
            }
        }
    }
}

@Composable
private fun StatCard(label: String, values: List<String>, modifier: Modifier) {
    LedgerCard(modifier, PaddingValues(horizontal = 16.dp, vertical = 18.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (value in values) LedgerNumber(value, 30.sp, Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ClockOutAction(
    shift: Shift,
    completedShifts: List<Shift>,
    saving: Boolean,
    onStop: (Long, Long, Int, ShiftAdjustments, () -> Unit) -> Unit,
) {
    val locale = displayLocale()
    var endMillis by rememberSaveable(shift.id) { mutableStateOf<Long?>(null) }
    var breakInput by rememberSaveable(shift.id) { mutableStateOf("") }
    var adjustmentInput by rememberSaveable(shift.id) { mutableStateOf(ShiftAdjustments()) }
    var showingAdjustments by rememberSaveable(shift.id) { mutableStateOf(false) }

    PrimaryAction(stringResource(R.string.clock_out), !saving) {
        val now = System.currentTimeMillis()
        val elapsedMinutes = ((now - shift.startMillis) / 60_000).coerceAtLeast(0)
        breakInput = shift.breakMinutes.coerceAtMost(elapsedMinutes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()).toString()
        val defaults = shift.payrollDetails.adjustments.withMonthlyFuel()
        val hasDailyTravel = !defaults.fuelPaidMonthly && defaults.travel.toBigDecimal().signum() > 0 &&
            completedShifts.hasTravelPayment(shift.jobId, shift.localStart().toLocalDate())
        adjustmentInput = (if (hasDailyTravel) defaults.copy(travel = "0") else defaults).localized(locale)
        showingAdjustments = false
        endMillis = now
    }
    endMillis?.let { end ->
        val breakMinutes = breakInput.toIntOrNull()
        val validTime = breakMinutes != null && isValidShift(shift.startMillis, end, breakMinutes)
        val adjustments = adjustmentInput.parseInput(locale)
        AlertDialog(
            onDismissRequest = { if (!saving) endMillis = null },
            title = { Text(stringResource(R.string.clock_out)) },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BreakField(breakInput, { breakInput = it }, isError = !validTime)
                    if (!validTime) Text(stringResource(R.string.invalid_break), color = MaterialTheme.colorScheme.error)
                    if (validTime) {
                        Text(durationText(paidMillis(shift.startMillis, end, breakMinutes)))
                    }
                    TextButton(onClick = { showingAdjustments = !showingAdjustments }) {
                        Text(stringResource(if (showingAdjustments) R.string.payroll_hide_options else R.string.payroll_adjustments))
                    }
                    if (showingAdjustments) {
                        ShiftAdjustmentFields(adjustmentInput, shift.currencyCode, onChange = { adjustmentInput = it })
                    }
                }
            },
            confirmButton = {
                TextButton(enabled = validTime && adjustments != null && !saving, onClick = {
                    if (breakMinutes != null && adjustments != null) {
                        onStop(shift.id, end, breakMinutes, adjustments) { endMillis = null }
                    }
                }) { Text(stringResource(R.string.save_shift)) }
            },
            dismissButton = {
                TextButton(enabled = !saving, onClick = { endMillis = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

internal fun Shift.localStart() = Instant.ofEpochMilli(startMillis).atZone(ZoneId.of(zoneId))
internal fun Shift.localEnd() = endMillis?.let { Instant.ofEpochMilli(it).atZone(ZoneId.of(zoneId)) }
