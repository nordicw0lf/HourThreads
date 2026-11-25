package app.hourstracker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
internal fun BackupOptions(onExport: () -> Unit, onImport: () -> Unit, onAutoBackup: () -> Unit, onDismiss: () -> Unit) {
    BackupPanel(onDismiss) {
        PageTitle(stringResource(R.string.backup_title), onDismiss)
        Text(
            stringResource(R.string.backup_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge,
        )
        PrimaryAction(stringResource(R.string.backup_export), onClick = onExport)
        SecondaryAction(stringResource(R.string.auto_backup), onClick = onAutoBackup)
        SecondaryAction(stringResource(R.string.backup_import), onClick = onImport)
    }
}

@Composable
private fun BackupPanel(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        LedgerCard(
            Modifier.widthIn(max = 480.dp).fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp),
        ) {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                content()
            }
        }
    }
}

@Composable
internal fun BackupResultDialog(
    status: BackupStatus,
    hasExistingData: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    when (status) {
        BackupStatus.Working -> AlertDialog(
            onDismissRequest = {},
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text(stringResource(R.string.backup_working))
                }
            },
            confirmButton = {},
        )
        is BackupStatus.Preview -> BackupPanel(onDismiss) {
            PageTitle(stringResource(R.string.backup_import), onDismiss)
            Text(stringResource(R.string.backup_counts, status.backup.jobs.size, status.backup.shifts.size))
            if (hasExistingData) Text(stringResource(R.string.backup_replace))
            PrimaryAction(stringResource(R.string.backup_restore), onClick = onConfirm)
            SecondaryAction(stringResource(R.string.cancel), onClick = onDismiss)
        }
        else -> {
            val message = when (status) {
                BackupStatus.Exported -> R.string.backup_exported
                BackupStatus.Restored -> R.string.backup_restored
                is BackupStatus.Error -> status.message
                else -> error("Unexpected backup result")
            }
            AlertDialog(
                onDismissRequest = onDismiss,
                text = { Text(stringResource(message)) },
                confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.dismiss)) } },
            )
        }
    }
}
