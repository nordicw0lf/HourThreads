package app.hourstracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

@Composable
internal fun SettingsScreen(
    theme: ThemeMode,
    lightAccent: AccentColor,
    darkAccent: AccentColor,
    jobs: List<Job>,
    hasIsraeliPayroll: Boolean,
    quarterPoints: Int,
    saving: Boolean,
    onBack: () -> Unit,
    onChooseLanguage: () -> Unit,
    onChooseTheme: (ThemeMode) -> Unit,
    onChooseAccent: (Boolean, AccentColor) -> Unit,
    onAddJob: () -> Unit,
    onEditJob: (Job) -> Unit,
    onSaveCredits: (Int) -> Unit,
) {
    var pickingDarkAccent by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var choosingJob by rememberSaveable { mutableStateOf(false) }
    var editingCredits by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle(stringResource(R.string.settings), onBack)
        LedgerCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.jobs), style = MaterialTheme.typography.titleLarge)
                SecondaryAction(stringResource(R.string.edit_job), !saving && jobs.isNotEmpty()) { choosingJob = true }
                SecondaryAction(stringResource(R.string.add_job), !saving, onAddJob)
            }
        }
        if (hasIsraeliPayroll) {
            ChoiceField(
                stringResource(R.string.payroll_credit_points),
                creditPointsText(quarterPoints, displayLocale()),
                enabled = !saving,
                onClick = { editingCredits = true },
            )
        }
        LedgerCard(Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.theme),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            FlowRow(
                Modifier.selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                for (option in ThemeMode.entries) {
                    LedgerFilterChip(
                        text = stringResource(option.label),
                        selected = option == theme,
                        onClick = { onChooseTheme(option) },
                        enabled = !saving,
                    )
                }
            }
        }
        LedgerCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.accent_color), style = MaterialTheme.typography.titleLarge)
                ChoiceField(
                    stringResource(R.string.accent_light),
                    stringResource(lightAccent.label),
                    enabled = !saving,
                    leadingContent = { AccentSwatch(lightAccent.swatch(dark = false)) },
                    onClick = { pickingDarkAccent = false },
                )
                ChoiceField(
                    stringResource(R.string.accent_dark),
                    stringResource(darkAccent.label),
                    enabled = !saving,
                    leadingContent = { AccentSwatch(darkAccent.swatch(dark = true)) },
                    onClick = { pickingDarkAccent = true },
                )
            }
        }
        ChoiceField(
            stringResource(R.string.language),
            stringResource(AppLanguage.current().label),
            onClick = onChooseLanguage,
        )
    }

    if (editingCredits) {
        TaxCreditDialog(
            quarterPoints,
            onSave = { points ->
                editingCredits = false
                if (points != quarterPoints) onSaveCredits(points)
            },
            onDismiss = { editingCredits = false },
        )
    }

    if (choosingJob) {
        JobPicker(
            jobs,
            onPick = { job ->
                choosingJob = false
                onEditJob(job)
            },
            onDismiss = { choosingJob = false },
        )
    }

    pickingDarkAccent?.let { dark ->
        val selected = if (dark) darkAccent else lightAccent
        AccentPicker(
            dark = dark,
            selected = selected,
            onChoose = { accent ->
                pickingDarkAccent = null
                if (accent != selected) onChooseAccent(dark, accent)
            },
            onDismiss = { pickingDarkAccent = null },
        )
    }
}
