package app.hourstracker

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
internal fun ShiftEditor(
    original: Shift?,
    jobs: List<Job>,
    completedShifts: List<Shift>,
    manualShiftTimes: ManualShiftTimes?,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: (ManualShiftEntry) -> Unit,
    onSaveRange: (ManualShiftRange) -> Unit,
) {
    val context = LocalContext.current
    val locale = displayLocale()
    val initialStart = original?.localStart()
    val initialEnd = original?.localEnd()
    val zone = rememberSaveable { original?.zoneId ?: ZoneId.systemDefault().id }.let(ZoneId::of)
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    val timeFormat = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    var jobId by rememberSaveable { mutableStateOf(original?.jobId ?: jobs.firstOrNull()?.id) }
    var day by rememberSaveable { mutableStateOf((initialStart?.toLocalDate() ?: LocalDate.now(zone)).toString()) }
    var startTime by rememberSaveable {
        mutableStateOf((initialStart?.toLocalTime() ?: manualShiftTimes?.startTime() ?: LocalTime.of(9, 0)).toString())
    }
    var endTime by rememberSaveable {
        mutableStateOf((initialEnd?.toLocalTime() ?: manualShiftTimes?.endTime() ?: LocalTime.of(17, 0)).toString())
    }
    var explicitEndDate by rememberSaveable { mutableStateOf(initialEnd?.toLocalDate()?.toString()) }
    var breakInput by rememberSaveable {
        mutableStateOf((original?.breakMinutes ?: jobs.firstOrNull()?.breakMinutes ?: 0).toString())
    }
    var note by rememberSaveable { mutableStateOf(original?.note.orEmpty()) }
    var choosingJob by rememberSaveable { mutableStateOf(false) }
    var vacation by rememberSaveable { mutableStateOf(original?.isVacation == true) }
    var rangeMode by rememberSaveable { mutableStateOf(false) }
    var lastDay by rememberSaveable { mutableStateOf<String?>(null) }
    var showingAdjustments by rememberSaveable { mutableStateOf(false) }
    var changes by rememberSaveable(jobId) { mutableStateOf<ShiftAdjustments?>(null) }
    var travelChange by rememberSaveable(jobId) { mutableStateOf<String?>(null) }
    var vacationChange by rememberSaveable(jobId) { mutableStateOf<String?>(null) }

    val selectedJob = jobs.firstOrNull { it.id == jobId }
    val retained = original?.takeIf { it.jobId == selectedJob?.id }
    val currencyCode = retained?.currencyCode ?: selectedJob?.currencyCode.orEmpty()
    val startDate = LocalDate.parse(day)
    val addingRange = original == null && !vacation && rangeMode
    val lastShiftDate = maxOf(startDate, lastDay?.let(LocalDate::parse) ?: startDate)
    val start = LocalTime.parse(startTime)
    val end = LocalTime.parse(endTime)
    val endDate = if (addingRange) defaultEndDate(startDate, start, end)
        else explicitEndDate?.let(LocalDate::parse) ?: defaultEndDate(startDate, start, end)
    val startMillis = if (vacation) startDate.atStartOfDay(zone).toInstant().toEpochMilli()
    else resolveTime(startDate, start, zone, original?.startMillis).toInstant().toEpochMilli()
    val endMillis = if (vacation) startMillis
    else resolveTime(endDate, end, zone, original?.endMillis).toInstant().toEpochMilli()
    val breakMinutes = if (vacation) 0 else breakInput.toIntOrNull()
    val jobDefaults = selectedJob?.payrollDefaults ?: JobPayroll()
    val jobAdjustments = jobDefaults.forShift().adjustments
    val savedAdjustments = original?.takeIf { it.currencyCode == currencyCode }
        ?.payrollDetails?.adjustments ?: jobAdjustments
    val defaultAdjustments = if (original != null && original.jobId != selectedJob?.id &&
        (savedAdjustments.fuelPaidMonthly || jobAdjustments.fuelPaidMonthly)
    ) {
        savedAdjustments.copy(
            travel = jobAdjustments.travel,
            fuelReimbursement = jobAdjustments.fuelReimbursement,
            fuelPaidMonthly = jobAdjustments.fuelPaidMonthly,
        )
    } else {
        savedAdjustments
    }
    val travelAlreadyPaid = original == null && !addingRange && selectedJob != null && !defaultAdjustments.fuelPaidMonthly &&
        defaultAdjustments.travel.toBigDecimal().signum() > 0 && completedShifts.hasTravelPayment(selectedJob.id, startDate)
    val localizedAdjustments = defaultAdjustments.localized(locale)
    val adjustmentInput = (changes ?: localizedAdjustments).copy(
        travel = travelChange ?: if (travelAlreadyPaid) "0" else localizedAdjustments.travel,
    )
    val vacationInput = vacationChange ?: (original?.takeIf { it.currencyCode == currencyCode }
        ?.payroll?.vacationPay ?: jobDefaults.vacationPay)
        .localizedPayrollValue(locale)
    val payroll = if (vacation) {
        vacationInput.parsePayrollValue(locale)?.takeIf { it.toBigDecimal().signum() > 0 }?.let {
            ShiftPayroll(vacationPay = it)
        }
    } else {
        adjustmentInput.parseInput(locale)?.let { ShiftPayroll(adjustments = it) }
    }
    val range = if (addingRange && selectedJob != null && breakMinutes != null && payroll != null) {
        ManualShiftRange(selectedJob.id, startDate, lastShiftDate, ManualShiftTimes(start, end), zone, breakMinutes, note, payroll)
    } else {
        null
    }
    val rangePlan = remember(range, completedShifts) { range?.plan(completedShifts) }
    val rangeCount = rangePlan?.entries?.size ?: 0
    val validTime = if (addingRange) rangeCount > 0
        else vacation || (breakMinutes != null && isValidShift(startMillis, endMillis, breakMinutes))

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val title = when {
            original == null -> R.string.add_shift
            vacation -> R.string.payroll_edit_vacation
            else -> R.string.edit_shift
        }
        PageTitle(stringResource(title), onBack)
        if (original == null) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerFilterChip(stringResource(R.string.payroll_work_shift), !vacation, { vacation = false })
                LedgerFilterChip(stringResource(R.string.payroll_paid_vacation), vacation, { vacation = true })
            }
        }
        LedgerCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceField(
                    stringResource(R.string.job), selectedJob?.name ?: stringResource(R.string.choose_job),
                    embedded = true,
                    onClick = { choosingJob = true },
                )
                if (original == null && !vacation) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LedgerFilterChip(stringResource(R.string.shift_single_date), !rangeMode, { rangeMode = false })
                        LedgerFilterChip(stringResource(R.string.shift_date_range), rangeMode, { rangeMode = true })
                    }
                }
                ChoiceField(
                    stringResource(when {
                        vacation -> R.string.payroll_vacation_date
                        addingRange -> R.string.shift_first_date
                        else -> R.string.start_date
                    }),
                    startDate.format(dateFormat),
                    embedded = true,
                ) {
                    DatePickerDialog(context, { _, year, month, dayOfMonth ->
                        val newStartDate = LocalDate.of(year, month + 1, dayOfMonth)
                        explicitEndDate = explicitEndDate?.let {
                            newStartDate.plusDays(LocalDate.parse(it).toEpochDay() - startDate.toEpochDay()).toString()
                        }
                        if (addingRange && lastDay != null) lastDay = maxOf(lastShiftDate, newStartDate).toString()
                        day = newStartDate.toString()
                    }, startDate.year, startDate.monthValue - 1, startDate.dayOfMonth).show()
                }
                if (addingRange) {
                    ChoiceField(
                        stringResource(R.string.shift_last_date), lastShiftDate.format(dateFormat), embedded = true,
                    ) {
                        DatePickerDialog(context, { _, year, month, dayOfMonth ->
                            lastDay = LocalDate.of(year, month + 1, dayOfMonth).toString()
                        }, lastShiftDate.year, lastShiftDate.monthValue - 1, lastShiftDate.dayOfMonth).apply {
                            datePicker.minDate = startDate.atStartOfDay(zone).toInstant().toEpochMilli()
                        }.show()
                    }
                    Text(
                        stringResource(R.string.shift_range_every_day),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vacation) {
                    PayrollField(
                        stringResource(R.string.payroll_vacation), currencyCode, vacationInput, { vacationChange = it },
                    )
                } else {
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        maxItemsInEachRow = if (LocalDensity.current.fontScale > 1.3f) 1 else 2,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ChoiceField(
                            stringResource(R.string.start_time), start.format(timeFormat), Modifier.weight(1f), embedded = true,
                        ) {
                            TimePickerDialog(context, { _, hour, minute ->
                                if (hour != start.hour || minute != start.minute) startTime = LocalTime.of(hour, minute).toString()
                            }, start.hour, start.minute, DateFormat.is24HourFormat(context)).show()
                        }
                        ChoiceField(
                            stringResource(R.string.end_time), end.format(timeFormat), Modifier.weight(1f), embedded = true,
                        ) {
                            TimePickerDialog(context, { _, hour, minute ->
                                if (hour != end.hour || minute != end.minute) endTime = LocalTime.of(hour, minute).toString()
                            }, end.hour, end.minute, DateFormat.is24HourFormat(context)).show()
                        }
                    }
                    if (endDate == startDate.plusDays(1)) {
                        Text(stringResource(R.string.ends_next_day), color = MaterialTheme.colorScheme.primary)
                    }
                    if (!addingRange) {
                        ChoiceField(stringResource(R.string.end_date), endDate.format(dateFormat), embedded = true) {
                            DatePickerDialog(context, { _, year, month, dayOfMonth ->
                                explicitEndDate = LocalDate.of(year, month + 1, dayOfMonth).toString()
                            }, endDate.year, endDate.monthValue - 1, endDate.dayOfMonth).show()
                        }
                        if (explicitEndDate != null) {
                            TextButton(onClick = { explicitEndDate = null }) {
                                Text(stringResource(R.string.use_automatic_end_date))
                            }
                        }
                    }
                    BreakField(breakInput, { breakInput = it }, embedded = true)
                }
            }
        }
        if (!vacation) {
            TextButton(onClick = { showingAdjustments = !showingAdjustments }) {
                Text(stringResource(if (showingAdjustments) R.string.payroll_hide_options else R.string.payroll_adjustments))
            }
            if (showingAdjustments) {
                ShiftAdjustmentFields(adjustmentInput, currencyCode, onChange = { updated ->
                    if (updated.travel != adjustmentInput.travel) travelChange = updated.travel
                    changes = updated
                })
            }
        }
        val duplicates = rangePlan?.duplicates ?: 0
        if (duplicates > 0) {
            Text(
                pluralStringResource(R.plurals.shift_range_duplicates, duplicates, duplicates),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when {
            rangePlan?.invalidDay != null ->
                Text(
                    stringResource(R.string.shift_range_invalid_day, rangePlan.invalidDay.format(dateFormat)),
                    color = MaterialTheme.colorScheme.error,
                )
            !addingRange && !vacation && endMillis <= startMillis ->
                Text(stringResource(R.string.invalid_times), color = MaterialTheme.colorScheme.error)
            !addingRange && !validTime && breakMinutes != null ->
                Text(stringResource(R.string.invalid_break), color = MaterialTheme.colorScheme.error)
            validTime && selectedJob != null && payroll != null && breakMinutes != null -> {
                val duration = paidMillis(startMillis, endMillis, breakMinutes)
                val code = currencyCode
                val rate = retained?.hourlyRate ?: selectedJob.hourlyRate
                val entries = rangePlan?.entries
                val totalDuration = entries?.sumOf { paidMillis(it.start, it.end, it.breakMinutes) } ?: duration
                val earnings = shiftEarnings(duration, rate, code, payroll)
                val gross = entries?.sumOf {
                    shiftEarnings(paidMillis(it.start, it.end, it.breakMinutes), rate, code, it.payroll).gross
                } ?: earnings.gross
                LedgerCard(Modifier.fillMaxWidth()) {
                    if (!vacation) {
                        Text(
                            stringResource(if (addingRange) R.string.shift_range_total_duration else R.string.duration),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        LedgerNumber(durationText(totalDuration), 55.sp)
                    }
                    Text(
                        stringResource(R.string.summary_pay, formatMoney(gross, code, locale)),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    val deductions = if (addingRange) earnings.deductions.multiply(rangeCount.toBigDecimal())
                        else earnings.deductions
                    if (deductions.signum() > 0) {
                        Text(
                            stringResource(R.string.payroll_shift_deduction_value, formatMoney(deductions, code, locale)),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        LedgerTextField(note, { note = it }, stringResource(R.string.note), maxLines = 4)
        val saveLabel = when {
            addingRange -> pluralStringResource(R.plurals.shift_save_range, rangeCount, rangeCount)
            vacation -> stringResource(R.string.payroll_save_vacation)
            else -> stringResource(R.string.save_shift)
        }
        PrimaryAction(
            saveLabel,
            enabled = validTime && selectedJob != null && breakMinutes != null && payroll != null && !saving,
        ) {
            if (addingRange) {
                if (range != null && rangeCount > 0) onSaveRange(range)
            } else if (selectedJob != null && breakMinutes != null && payroll != null) {
                onSave(
                    ManualShiftEntry(
                        selectedJob.id, startMillis, endMillis, zone, breakMinutes, note,
                        if (vacation) null else ManualShiftTimes(start, end), payroll,
                    ),
                )
            }
        }
    }

    if (choosingJob) {
        JobPicker(jobs, {
            if (jobId != it.id && original == null) breakInput = it.breakMinutes.toString()
            jobId = it.id
            choosingJob = false
        }, { choosingJob = false })
    }
}
