package app.hourstracker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.LocaleManagerCompat
import java.text.DecimalFormatSymbols
import java.util.Currency

@Composable
internal fun JobForm(
    defaultCurrencyCode: String,
    saving: Boolean,
    onBack: () -> Unit,
    firstJob: Boolean = false,
    original: Job? = null,
    hasCompletedEntries: Boolean = false,
    onSubmit: (Job, Boolean) -> Unit,
) {
    val locale = displayLocale()
    val deviceLocale = LocaleManagerCompat.getSystemLocales(LocalContext.current)[0] ?: locale
    var name by rememberSaveable { mutableStateOf(original?.name.orEmpty()) }
    var currencyCode by rememberSaveable { mutableStateOf(original?.currencyCode ?: defaultCurrencyCode) }
    var choosingCurrency by rememberSaveable { mutableStateOf(false) }
    var confirmingCurrencyChange by rememberSaveable { mutableStateOf(false) }
    var rate by rememberSaveable {
        val decimalSeparator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
        mutableStateOf(original?.hourlyRate.orEmpty().replace('.', decimalSeparator))
    }
    var breakInput by rememberSaveable { mutableStateOf((original?.breakMinutes ?: 0).toString()) }
    val defaults = original?.payrollDefaults ?: JobPayroll()
    var vacation by rememberSaveable { mutableStateOf(defaults.vacationPay.localizedPayrollValue(locale)) }
    var adjustments by rememberSaveable { mutableStateOf(defaults.adjustments.localized(locale)) }
    var pension by rememberSaveable { mutableStateOf(defaults.contributions.pensionPercent.localizedPayrollValue(locale)) }
    var training by rememberSaveable { mutableStateOf(defaults.contributions.trainingPercent.localizedPayrollValue(locale)) }
    var showingPayroll by rememberSaveable { mutableStateOf(false) }
    var confirmingRecalculation by rememberSaveable { mutableStateOf(false) }
    val hourlyRate = remember(rate, locale, deviceLocale) {
        parseRate(rate, locale) ?: parseRate(rate, deviceLocale)
    }
    val breakMinutes = breakInput.toIntOrNull()
    val vacationPay = vacation.parsePayrollValue(locale)
    val shiftAdjustments = adjustments.parseInput(locale)?.withMonthlyFuel()
    val pensionPercent = pension.parsePayrollValue(locale, percent = true)
    val trainingPercent = training.parsePayrollValue(locale, percent = true)
    val legacyDailyFuel = defaults.legacyDailyFuelDefault ?: defaults.adjustments
        .takeIf { it.fuelReimbursement && !it.fuelPaidMonthly }?.travel
    val payroll = if (vacationPay != null && shiftAdjustments != null && pensionPercent != null && trainingPercent != null) {
        JobPayroll(vacationPay, shiftAdjustments, EmployeeContributions(pensionPercent, trainingPercent), legacyDailyFuel)
    } else {
        null
    }
    val proposedJob = if (name.isNotBlank() && hourlyRate != null && breakMinutes != null && payroll != null) {
        Job(
            name.trim(), hourlyRate.toPlainString(), breakMinutes,
            id = original?.id ?: 0L, payroll = payroll, currencyCode = currencyCode,
        )
    } else {
        null
    }
    val changingCurrency = original != null && original.currencyCode != currencyCode
    val saveJob = {
        if (proposedJob != null) {
            if (original != null && hasCompletedEntries && JobPayChanges(original, proposedJob).affectsPastPay) {
                confirmingRecalculation = true
            } else {
                onSubmit(proposedJob, false)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (firstJob) {
            BackButton(onBack)
            Text(
                stringResource(R.string.welcome), Modifier.fillMaxWidth().padding(bottom = 8.dp),
                style = MaterialTheme.typography.headlineLarge, textAlign = TextAlign.Center,
            )
        } else {
            PageTitle(stringResource(if (original == null) R.string.add_job else R.string.edit_job), onBack)
        }

        LedgerTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.job_name),
        )
        ChoiceField(
            stringResource(R.string.currency),
            stringResource(R.string.currency_option, currencyCode, Currency.getInstance(currencyCode).getDisplayName(locale)),
            enabled = !saving,
        ) {
            choosingCurrency = true
        }
        if (currencyCode != ILS) {
            Text(
                stringResource(R.string.job_currency_gross_only),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LedgerTextField(
            value = rate,
            onValueChange = { rate = it },
            label = stringResource(R.string.hourly_rate, currencyCode),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = rate.isNotBlank() && hourlyRate == null,
        )
        if (original != null) {
            Text(
                stringResource(R.string.job_rate_applies_to_new_shifts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BreakField(breakInput, { breakInput = it }, label = stringResource(R.string.default_break))
        TextButton(onClick = { showingPayroll = !showingPayroll }) {
            Text(stringResource(if (showingPayroll) R.string.payroll_hide_options else R.string.payroll_options))
        }
        if (showingPayroll) {
            PayrollField(stringResource(R.string.payroll_vacation_daily), currencyCode, vacation, { vacation = it })
            ShiftAdjustmentFields(adjustments, currencyCode, { adjustments = it }, jobDefaults = true)
            if (adjustments.fuelReimbursement && defaults.adjustments.fuelReimbursement &&
                !defaults.adjustments.fuelPaidMonthly
            ) {
                Text(
                    stringResource(R.string.payroll_review_legacy_fuel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (currencyCode == ILS) {
                PayrollField(stringResource(R.string.payroll_pension), "%", pension, { pension = it }, percent = true)
                PayrollField(stringResource(R.string.payroll_training), "%", training, { training = it }, percent = true)
                Text(
                    stringResource(R.string.payroll_contribution_basis),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PrimaryAction(
            stringResource(if (firstJob) R.string.continue_setup else R.string.save_job),
            enabled = !saving && proposedJob != null,
        ) {
            if (changingCurrency) confirmingCurrencyChange = true else saveJob()
        }
    }
    if (choosingCurrency) {
        CurrencyPicker(onSelect = {
            currencyCode = it
            choosingCurrency = false
        }, onDismiss = { choosingCurrency = false })
    }
    if (confirmingCurrencyChange) {
        AlertDialog(
            onDismissRequest = { confirmingCurrencyChange = false },
            title = { Text(stringResource(R.string.job_currency_warning_title)) },
            text = { Text(stringResource(R.string.job_currency_warning, currencyCode)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmingCurrencyChange = false
                    saveJob()
                }) { Text(stringResource(R.string.continue_setup)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmingCurrencyChange = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
    if (confirmingRecalculation && proposedJob != null) {
        AlertDialog(
            onDismissRequest = { confirmingRecalculation = false },
            title = { Text(stringResource(R.string.recalculate_past_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.recalculate_past_message, proposedJob.name))
                    if (changingCurrency) Text(stringResource(R.string.recalculate_past_currency, currencyCode))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingRecalculation = false
                    onSubmit(proposedJob, true)
                }) { Text(stringResource(R.string.recalculate_past_action)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    confirmingRecalculation = false
                    onSubmit(proposedJob, false)
                }) { Text(stringResource(R.string.recalculate_future_only)) }
            },
        )
    }
}

@Composable
internal fun CurrencyPicker(onSelect: (String) -> Unit, onDismiss: () -> Unit) {
    val locale = displayLocale()
    val available = remember(locale) { availableCurrencies(locale) }
    var query by rememberSaveable { mutableStateOf("") }
    val matching = remember(available, query, locale) {
        val term = query.trim()
        available.filter {
            term.isEmpty() || it.currencyCode.contains(term, ignoreCase = true) ||
                it.getDisplayName(locale).contains(term, ignoreCase = true) || it.getSymbol(locale) == term
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_currency)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LedgerTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = stringResource(R.string.search_currency),
                    embedded = true,
                )
                LazyColumn(Modifier.heightIn(max = 280.dp)) {
                    if (matching.isEmpty()) {
                        item { Text(stringResource(R.string.no_currencies), Modifier.padding(vertical = 12.dp)) }
                    }
                    items(matching, key = { it.currencyCode }) { currency ->
                        Text(
                            text = stringResource(
                                R.string.currency_option, currency.currencyCode, currency.getDisplayName(locale),
                            ),
                            modifier = Modifier.fillMaxWidth().clickable { onSelect(currency.currencyCode) }
                                .padding(vertical = 14.dp),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun ChoiceField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(if (embedded) 16.dp else 22.dp),
        color = if (embedded) colors.surfaceContainer else colors.surface,
        shadowElevation = if (!embedded && colors.background.luminance() >= .5f) 2.dp else 0.dp,
    ) {
        Row(
            Modifier.heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leadingContent?.invoke()
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
                Text(value, style = MaterialTheme.typography.titleMedium)
            }
            if (!embedded) Canvas(Modifier.size(16.dp)) {
                val middle = Offset(size.width * .5f, size.height * .63f)
                drawLine(colors.primary, Offset(size.width * .2f, size.height * .38f), middle, 1.8.dp.toPx(), StrokeCap.Round)
                drawLine(colors.primary, middle, Offset(size.width * .8f, size.height * .38f), 1.8.dp.toPx(), StrokeCap.Round)
            }
        }
    }
}

@Composable
internal fun LedgerTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    maxLines: Int = 1,
    isError: Boolean = false,
    embedded: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (embedded) 16.dp else 22.dp)
    val fill = if (embedded) colors.surfaceContainer else colors.surface
    TextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth().heightIn(min = if (embedded) 56.dp else 62.dp)
            .shadow(if (!embedded && colors.background.luminance() >= .5f) 2.dp else 0.dp, shape),
        shape = shape,
        textStyle = if (embedded) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        keyboardOptions = keyboardOptions,
        singleLine = maxLines == 1,
        maxLines = maxLines,
        isError = isError,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = fill,
            unfocusedContainerColor = fill,
            errorContainerColor = fill,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

@Composable
internal fun BreakField(
    value: String,
    onChange: (String) -> Unit,
    label: String = stringResource(R.string.break_minutes),
    isError: Boolean = false,
    embedded: Boolean = false,
) {
    LedgerTextField(
        value = value,
        onValueChange = { next ->
            if (next.isEmpty() || next.toIntOrNull()?.let { it >= 0 } == true) onChange(next)
        },
        label = label,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        isError = isError,
        embedded = embedded,
    )
}

@Composable
internal fun JobPicker(jobs: List<Job>, onPick: (Job) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.choose_job)) },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                items(jobs, key = { it.id }) { job ->
                    Text(
                        job.name,
                        Modifier.fillMaxWidth().clickable { onPick(job) }.padding(vertical = 14.dp),
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
