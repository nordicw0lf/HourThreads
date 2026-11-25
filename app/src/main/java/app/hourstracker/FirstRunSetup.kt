package app.hourstracker

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.Currency

@Composable
internal fun FirstRunSetup(
    saving: Boolean,
    onChooseLanguage: () -> Unit,
    onImport: () -> Unit,
    onSubmit: (String, Job) -> Unit,
) {
    val locale = displayLocale()
    var currencyCode by rememberSaveable { mutableStateOf(ILS) }
    var addingFirstJob by rememberSaveable { mutableStateOf(false) }
    var choosingCurrency by rememberSaveable { mutableStateOf(false) }
    val draft = rememberSaveableStateHolder()

    BackHandler(enabled = addingFirstJob && !saving) { addingFirstJob = false }
    if (addingFirstJob) {
        draft.SaveableStateProvider("firstJob") {
            JobForm(currencyCode, saving, { addingFirstJob = false }, firstJob = true) { job, _ ->
                onSubmit(currencyCode, job)
            }
        }
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Surface(color = Color.Black, shape = RoundedCornerShape(20.dp)) {
                Image(
                    painterResource(R.drawable.hourthread_mark),
                    contentDescription = null,
                    modifier = Modifier.size(68.dp),
                )
            }
            Text(
                stringResource(R.string.welcome_start),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
        }
        ChoiceField(
            stringResource(R.string.language),
            stringResource(AppLanguage.current().label),
            onClick = onChooseLanguage,
        )
        Text(
            stringResource(R.string.setup_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ChoiceField(
            stringResource(R.string.currency),
            stringResource(R.string.currency_option, currencyCode, Currency.getInstance(currencyCode).getDisplayName(locale)),
            onClick = { choosingCurrency = true },
        )
        PrimaryAction(stringResource(R.string.start_setup), enabled = !saving) { addingFirstJob = true }
        SecondaryAction(stringResource(R.string.backup_import), !saving, onImport)
    }

    if (choosingCurrency) {
        CurrencyPicker(
            onSelect = {
                currencyCode = it
                choosingCurrency = false
            },
            onDismiss = { choosingCurrency = false },
        )
    }
}
