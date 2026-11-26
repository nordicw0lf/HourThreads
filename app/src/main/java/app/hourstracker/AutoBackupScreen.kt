package app.hourstracker

import android.Manifest
import android.app.Activity
import android.app.TimePickerDialog
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AutoBackupScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scheduler = remember(context) { AutoBackupScheduler(context) }
    val scope = rememberCoroutineScope()
    var state by remember(scheduler) { mutableStateOf(scheduler.read()) }
    DisposableEffect(scheduler) {
        val stopObserving = scheduler.observe { state = it }
        onDispose(stopObserving)
    }
    var enabled by rememberSaveable { mutableStateOf(state.plan.enabled) }
    var frequency by rememberSaveable { mutableStateOf(state.plan.frequency) }
    var minuteOfDay by rememberSaveable { mutableStateOf(state.plan.minuteOfDay) }
    var weekday by rememberSaveable { mutableStateOf(state.plan.weekday) }
    var monthDay by rememberSaveable { mutableStateOf(state.plan.monthDay) }
    var folderUri by rememberSaveable { mutableStateOf(state.plan.folderUri) }
    var folderName by rememberSaveable { mutableStateOf(state.plan.folderName) }
    var folderFlags by rememberSaveable { mutableStateOf(0) }
    var choosingDay by rememberSaveable { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<Int?>(null) }
    var error by rememberSaveable { mutableStateOf<Int?>(null) }
    var notificationsEnabled by remember { mutableStateOf(backupNotificationsEnabled(context)) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsEnabled = backupNotificationsEnabled(context)
    }
    val notificationSettings = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        notificationsEnabled = backupNotificationsEnabled(context)
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data
        val uri = data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            val flags = data.flags and AutoBackupScheduler.FOLDER_GRANT_FLAGS
            if (!DocumentsContract.isTreeUri(uri) || flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) {
                error = R.string.auto_backup_folder_unavailable
            } else {
                folderUri = uri.toString()
                folderFlags = flags
                message = null
                error = null
                busy = true
                scope.launch {
                    try {
                        folderName = withContext(Dispatchers.IO) { folderDisplayName(context.contentResolver, uri) }
                    } finally {
                        busy = false
                    }
                }
            }
        }
    }

    val plan = AutoBackupPlan(enabled, frequency, minuteOfDay, weekday, monthDay, folderUri, folderName)
    val locale = displayLocale()
    val is24Hour = DateFormat.is24HourFormat(context)
    val timeFormat = remember(locale, is24Hour) {
        DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, if (is24Hour) "Hm" else "hm"), locale)
    }
    val dateFormat = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    val numbers = remember(locale) { NumberFormat.getIntegerInstance(locale) }
    val week = remember(locale) {
        val firstDay = WeekFields.of(locale).firstDayOfWeek
        (0L..6L).map { firstDay.plus(it) }
    }
    val weekdays = week.map { it.getDisplayName(TextStyle.FULL, locale) }
    val daysOfMonth = (1..31).map(numbers::format)

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PageTitle(stringResource(R.string.auto_backup), onBack)
        LedgerCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(
                        value = enabled,
                        enabled = !busy,
                        role = Role.Switch,
                        onValueChange = { enabled = it },
                    ),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.auto_backup_enabled), Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Switch(checked = enabled, onCheckedChange = null, enabled = !busy)
                }
                Text(stringResource(R.string.auto_backup_repeat), style = MaterialTheme.typography.titleMedium)
                FlowRow(
                    Modifier.selectableGroup(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    for (option in BackupFrequency.entries) {
                        val label = when (option) {
                            BackupFrequency.Daily -> R.string.auto_backup_daily
                            BackupFrequency.Weekly -> R.string.auto_backup_weekly
                            BackupFrequency.Monthly -> R.string.auto_backup_monthly
                        }
                        LedgerFilterChip(stringResource(label), frequency == option, { frequency = option }, enabled = !busy)
                    }
                }
                when (frequency) {
                    BackupFrequency.Daily -> Unit
                    BackupFrequency.Weekly -> ChoiceField(
                        stringResource(R.string.auto_backup_weekday),
                        DayOfWeek.of(weekday).getDisplayName(TextStyle.FULL, locale),
                        embedded = true, enabled = !busy,
                        onClick = { choosingDay = true },
                    )
                    BackupFrequency.Monthly -> {
                        ChoiceField(
                            stringResource(R.string.auto_backup_month_day), numbers.format(monthDay),
                            embedded = true, enabled = !busy,
                            onClick = { choosingDay = true },
                        )
                        AutoBackupNote(stringResource(R.string.auto_backup_short_months))
                    }
                }
                ChoiceField(
                    stringResource(R.string.auto_backup_time), plan.time.format(timeFormat),
                    embedded = true, enabled = !busy,
                ) {
                    TimePickerDialog(context, { _, hour, minute -> minuteOfDay = hour * 60 + minute },
                        plan.time.hour, plan.time.minute, is24Hour).show()
                }
                AutoBackupNote(stringResource(R.string.auto_backup_timing))
            }
        }

        LedgerCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ChoiceField(
                    stringResource(R.string.auto_backup_folder),
                    folderName ?: stringResource(
                        if (folderUri == null) R.string.auto_backup_reminders_only else R.string.auto_backup_selected_folder,
                    ),
                    embedded = true, enabled = !busy,
                ) {
                    folderPicker.launch(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
                        AutoBackupScheduler.FOLDER_GRANT_FLAGS or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                    ))
                }
                if (folderUri != null) {
                    TextButton(onClick = {
                        folderUri = null
                        folderName = null
                        folderFlags = 0
                        error = null
                        message = null
                    }, enabled = !busy) {
                        Text(stringResource(R.string.auto_backup_use_reminders))
                    }
                }
                AutoBackupNote(stringResource(R.string.auto_backup_folder_hint))
            }
        }

        if (enabled && !notificationsEnabled) {
            AutoBackupNote(stringResource(R.string.auto_backup_notification_hint))
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    notificationSettings.launch(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
                }
            }) { Text(stringResource(R.string.auto_backup_allow_notifications)) }
        }

        fun timestamp(millis: Long): ZonedDateTime =
            Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())
        if (state.plan.enabled && state.nextMillis > 0) {
            val next = timestamp(state.nextMillis)
            AutoBackupNote(stringResource(R.string.auto_backup_next, next.format(dateFormat), next.format(timeFormat)))
        }
        state.lastResult?.let { result ->
            val last = timestamp(state.lastRunMillis)
            val label = when (result) {
                AutoBackupResult.Saved -> R.string.auto_backup_last_saved
                AutoBackupResult.ManualReminder -> R.string.auto_backup_last_reminder
                AutoBackupResult.WriteFailed -> R.string.auto_backup_last_failed
            }
            AutoBackupNote(stringResource(label, last.format(dateFormat), last.format(timeFormat)))
        }
        error?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error) }
        message?.let { AutoBackupNote(stringResource(it)) }
        PrimaryAction(stringResource(R.string.auto_backup_save), enabled = !busy) {
            val wasEnabled = state.plan.enabled
            busy = true
            error = null
            message = null
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { scheduler.save(plan, folderFlags) }
                    message = R.string.auto_backup_schedule_saved
                    if (enabled && !wasEnabled && Build.VERSION.SDK_INT >= 33 &&
                        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                    ) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: SecurityException) {
                    error = R.string.auto_backup_folder_unavailable
                } catch (_: Exception) {
                    error = R.string.auto_backup_schedule_error
                } finally {
                    busy = false
                }
            }
        }
    }
    if (choosingDay && frequency != BackupFrequency.Daily) {
        val weekly = frequency == BackupFrequency.Weekly
        val selected = if (weekly) week.indexOf(DayOfWeek.of(weekday)) else monthDay - 1
        BackupDayPicker(
            title = stringResource(if (weekly) R.string.auto_backup_weekday else R.string.auto_backup_month_day),
            options = if (weekly) weekdays else daysOfMonth,
            selected = selected,
            onChoose = {
                if (weekly) weekday = week[it].value else monthDay = it + 1
                choosingDay = false
            },
            onDismiss = { choosingDay = false },
        )
    }
}

@Composable
private fun AutoBackupNote(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun BackupDayPicker(
    title: String, options: List<String>, selected: Int, onChoose: (Int) -> Unit, onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                Modifier.selectableGroup().heightIn(max = 320.dp),
                state = rememberLazyListState(initialFirstVisibleItemIndex = (selected - 2).coerceAtLeast(0)),
            ) {
                itemsIndexed(options) { index, label ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(
                            selected = index == selected, role = Role.RadioButton, onClick = { onChoose(index) },
                        ),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = index == selected, onClick = null)
                        Text(label)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

private fun folderDisplayName(resolver: ContentResolver, uri: Uri): String? =
    try {
        val folder = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri))
        resolver.query(folder, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) {
        null
    }
