package app.hourstracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Locale

internal fun String.localizedPayrollValue(locale: Locale): String =
    replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)

internal fun String.parsePayrollValue(locale: Locale, percent: Boolean = false): String? {
    if (isBlank()) return "0"
    val value = parseRate(this, locale) ?: (if ('.' in this) parseRate(this, Locale.ROOT) else null) ?: return null
    if (percent && value > BigDecimal(100)) return null
    return value.toPlainString()
}

internal fun ShiftAdjustments.localized(locale: Locale) = copy(
    deductions = deductions.localizedPayrollValue(locale),
    bonus = bonus.localizedPayrollValue(locale),
    travel = travel.localizedPayrollValue(locale),
)

internal fun ShiftAdjustments.parseInput(locale: Locale): ShiftAdjustments? {
    val deductions = deductions.parsePayrollValue(locale) ?: return null
    val bonus = bonus.parsePayrollValue(locale) ?: return null
    val travel = travel.parsePayrollValue(locale) ?: return null
    return copy(deductions = deductions, bonus = bonus, travel = travel)
}

@Composable
internal fun PayrollField(
    label: String,
    unit: String,
    value: String,
    onChange: (String) -> Unit,
    percent: Boolean = false,
) {
    LedgerTextField(
        value = value,
        onValueChange = onChange,
        label = stringResource(R.string.payroll_field_unit, label, unit),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        isError = value.parsePayrollValue(displayLocale(), percent) == null,
    )
}

@Composable
internal fun ShiftAdjustmentFields(
    values: ShiftAdjustments,
    currency: String,
    onChange: (ShiftAdjustments) -> Unit,
    jobDefaults: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PayrollField(stringResource(R.string.payroll_shift_deductions), currency, values.deductions, {
            onChange(values.copy(deductions = it))
        })
        PayrollField(stringResource(R.string.payroll_tips), currency, values.bonus, {
            onChange(values.copy(bonus = it))
        })
        if (jobDefaults) {
            Row(
                Modifier.fillMaxWidth().toggleable(
                    value = values.fuelReimbursement,
                    role = Role.Switch,
                    onValueChange = { onChange(values.copy(fuelReimbursement = it)) },
                ).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.payroll_fuel), Modifier.weight(1f))
                Switch(checked = values.fuelReimbursement, onCheckedChange = null)
            }
        }
        if (jobDefaults || !values.fuelPaidMonthly) {
            val travelLabel = when {
                values.fuelReimbursement && jobDefaults -> R.string.payroll_fuel_monthly
                values.fuelReimbursement -> R.string.payroll_fuel
                jobDefaults -> R.string.payroll_travel_daily
                else -> R.string.payroll_travel
            }
            PayrollField(stringResource(travelLabel), currency, values.travel, { onChange(values.copy(travel = it)) })
        } else if (values.travel.toBigDecimalOrNull()?.signum() == 1) {
            Text(
                stringResource(R.string.payroll_monthly_fuel_in_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun creditPointsText(quarterPoints: Int, locale: Locale): String =
    NumberFormat.getNumberInstance(locale).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 2
    }.format(BigDecimal(quarterPoints).divide(BigDecimal(4)))

@Composable
internal fun TaxCreditDialog(initial: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    var draft by rememberSaveable { mutableStateOf(initial) }
    val decrease = stringResource(R.string.payroll_points_decrease)
    val increase = stringResource(R.string.payroll_points_increase)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.payroll_credit_points)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.payroll_credits_hint))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = { draft-- },
                        enabled = draft > 0,
                        modifier = Modifier.semantics { contentDescription = decrease },
                    ) { Text("−", fontSize = 24.sp) }
                    Text(
                        creditPointsText(draft, displayLocale()),
                        modifier = Modifier.padding(horizontal = 12.dp),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    TextButton(
                        onClick = { draft++ },
                        enabled = draft < Int.MAX_VALUE,
                        modifier = Modifier.semantics { contentDescription = increase },
                    ) { Text("+", fontSize = 24.sp) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(draft) }) { Text(stringResource(R.string.payroll_save_credits)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
internal fun PayrollEstimateCard(
    estimate: IsraeliPayrollEstimate,
    multipleJobs: Boolean,
    excludesOtherCurrencies: Boolean,
    includeIncomeTax: Boolean,
    hideIncomeTaxWarning: Boolean,
    saving: Boolean,
    onIncomeTaxChange: (Boolean) -> Unit,
    onHideIncomeTaxWarning: () -> Unit,
) {
    var showBreakdown by rememberSaveable { mutableStateOf(false) }
    var includeIncomeTaxDraft by rememberSaveable(includeIncomeTax) { mutableStateOf(includeIncomeTax) }
    var showIncomeTaxWarning by rememberSaveable { mutableStateOf(false) }
    val locale = displayLocale()
    LedgerCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.payroll_net), style = MaterialTheme.typography.titleLarge)
            LedgerNumber(formatMoney(estimate.net, ILS, locale), 48.sp)
            Text(
                stringResource(R.string.payroll_estimate_basis, ISRAEL_PAYROLL_YEAR),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (excludesOtherCurrencies) {
                Text(
                    stringResource(R.string.payroll_ils_only),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!includeIncomeTax) {
                Text(
                    stringResource(R.string.payroll_income_tax_excluded),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (multipleJobs) {
                Text(stringResource(R.string.payroll_multi_job), style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = { showBreakdown = !showBreakdown }) {
                Text(stringResource(if (showBreakdown) R.string.payroll_hide_breakdown else R.string.payroll_breakdown))
            }
            if (showBreakdown) {
                val positive = listOf(
                    R.string.payroll_wages to estimate.wages,
                    R.string.payroll_vacation to estimate.vacation,
                    R.string.payroll_tips to estimate.bonus,
                    R.string.payroll_travel to estimate.travel,
                )
                for ((label, amount) in positive) {
                    PayrollLine(stringResource(label), formatMoney(amount, ILS, locale))
                }
                if (estimate.fuel.signum() > 0) {
                    PayrollLine(stringResource(R.string.payroll_fuel), formatMoney(estimate.fuel, ILS, locale))
                }
                HorizontalDivider()
                PayrollLine(stringResource(R.string.payroll_gross), formatMoney(estimate.gross, ILS, locale))
                Row(
                    Modifier.fillMaxWidth().toggleable(
                        value = includeIncomeTaxDraft,
                        enabled = !saving,
                        role = Role.Switch,
                        onValueChange = { include ->
                            if (!include && includeIncomeTax && !hideIncomeTaxWarning) {
                                showIncomeTaxWarning = true
                            } else {
                                includeIncomeTaxDraft = include
                            }
                        },
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.payroll_include_income_tax), style = MaterialTheme.typography.bodyMedium)
                        Text(formatMoney(estimate.incomeTax.negate(), ILS, locale), style = MaterialTheme.typography.titleSmall)
                    }
                    Switch(checked = includeIncomeTaxDraft, onCheckedChange = null, enabled = !saving)
                }
                if (includeIncomeTaxDraft != includeIncomeTax) {
                    Text(
                        stringResource(R.string.payroll_income_tax_recalculate_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onIncomeTaxChange(includeIncomeTaxDraft) }, enabled = !saving) {
                        Text(stringResource(R.string.payroll_recalculate))
                    }
                }
                val negative = listOf(
                    R.string.payroll_national_insurance to estimate.nationalInsurance,
                    R.string.payroll_health_insurance to estimate.healthInsurance,
                    R.string.payroll_pension to estimate.pension,
                    R.string.payroll_training to estimate.training,
                    R.string.payroll_shift_deductions to estimate.deductions,
                )
                for ((label, amount) in negative) {
                    PayrollLine(stringResource(label), formatMoney(amount.negate(), ILS, locale))
                }
                if (includeIncomeTax && (estimate.pointsCredit.signum() > 0 || estimate.pensionCredit.signum() > 0)) {
                    val availableCredit = estimate.pointsCredit + estimate.pensionCredit
                    Text(
                        stringResource(R.string.payroll_credits_applied, formatMoney(availableCredit.min(estimate.taxBeforeCredits), ILS, locale)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
    if (showIncomeTaxWarning) {
        AlertDialog(
            onDismissRequest = { showIncomeTaxWarning = false },
            title = { Text(stringResource(R.string.payroll_income_tax_warning_title)) },
            text = { Text(stringResource(R.string.payroll_income_tax_warning_body)) },
            confirmButton = {
                TextButton(onClick = {
                    includeIncomeTaxDraft = false
                    showIncomeTaxWarning = false
                }) { Text(stringResource(R.string.dismiss)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    includeIncomeTaxDraft = false
                    showIncomeTaxWarning = false
                    onHideIncomeTaxWarning()
                }) { Text(stringResource(R.string.payroll_income_tax_hide_warning)) }
            },
        )
    }
}

@Composable
private fun PayrollLine(label: String, amount: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(amount, style = MaterialTheme.typography.titleSmall)
    }
}
