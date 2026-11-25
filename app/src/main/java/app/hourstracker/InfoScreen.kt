package app.hourstracker

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val privacyPolicyUpdated = LocalDate.of(2026, 11, 24)

@Composable
internal fun InfoScreen() {
    var showingPrivacy by rememberSaveable { mutableStateOf(false) }
    BackHandler(enabled = showingPrivacy) { showingPrivacy = false }

    if (showingPrivacy) {
        PrivacyPolicyScreen(onBack = { showingPrivacy = false })
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PageTitle(stringResource(R.string.info))
            LedgerCard(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Surface(shape = RoundedCornerShape(16.dp), color = Color.Black) {
                            Image(
                                painterResource(R.drawable.hourthread_mark),
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                            )
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                stringResource(R.string.app_name),
                                Modifier.semantics { heading() },
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            Text(
                                stringResource(R.string.info_free_open_source),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(stringResource(R.string.info_description), style = MaterialTheme.typography.bodyLarge)
                    Text(
                        stringResource(R.string.info_dedication),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SecondaryAction(stringResource(R.string.privacy_policy)) { showingPrivacy = true }
        }
    }
}

@Composable
private fun PrivacyPolicyScreen(onBack: () -> Unit) {
    val date = privacyPolicyUpdated.format(
        DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(displayLocale()),
    )
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle(stringResource(R.string.privacy_policy), onBack)
        Text(
            stringResource(R.string.privacy_updated, date),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LedgerCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                Text(stringResource(R.string.privacy_intro), style = MaterialTheme.typography.bodyLarge)
                PrivacySection(R.string.privacy_local_title, R.string.privacy_local_body)
                PrivacySection(R.string.privacy_tracking_title, R.string.privacy_tracking_body)
                PrivacySection(R.string.privacy_backups_title, R.string.privacy_backups_body, R.string.privacy_auto_backup_body)
                PrivacySection(R.string.privacy_control_title, R.string.privacy_control_body)
            }
        }
    }
}

@Composable
private fun PrivacySection(@StringRes title: Int, @StringRes vararg paragraphs: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            stringResource(title),
            Modifier.semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
        )
        for (paragraph in paragraphs) {
            Text(
                stringResource(paragraph),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
