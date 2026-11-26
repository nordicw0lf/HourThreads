package app.hourstracker

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

internal enum class HoursTab(val label: Int) {
    Home(R.string.home),
    Log(R.string.log),
    Summary(R.string.summary),
    Info(R.string.info),
}

class MainActivity : AppCompatActivity() {
    private var backupShortcutRequested by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleBackupShortcut(intent)
        lifecycleScope.launch(Dispatchers.IO) { AutoBackupScheduler(applicationContext).rearm() }
        val systemBars = WindowCompat.getInsetsController(window, window.decorView)
        val factory = viewModelFactory {
            initializer {
                HoursViewModel(
                    HoursRepository(HoursDatabase.open(applicationContext)),
                    BackupFiles(applicationContext.contentResolver),
                )
            }
        }
        val model = ViewModelProvider(this, factory)[HoursViewModel::class.java]
        setContent {
            val state by model.state.collectAsStateWithLifecycle()
            val preferences = state?.preferences
            val theme = ThemeMode.fromKey(preferences?.themeMode) ?: ThemeMode.System
            val dark = when (theme) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            val accent = AccentColor.fromKey(if (dark) preferences?.darkAccent else preferences?.lightAccent)
                ?: AccentColor.Default
            SideEffect {
                if (preferences != null && delegate.localNightMode != theme.nightMode) {
                    delegate.localNightMode = theme.nightMode
                }
                systemBars.isAppearanceLightStatusBars = !dark
                systemBars.isAppearanceLightNavigationBars = !dark
            }
            CompositionLocalProvider(LocalChartAccent provides accent.chartColor(dark)) {
                MaterialTheme(colorScheme = hourThreadColorScheme(dark, accent), typography = ledgerTypography) {
                    HoursApp(model, state, theme, backupShortcutRequested) { backupShortcutRequested = false }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleBackupShortcut(intent)
    }

    private fun handleBackupShortcut(intent: Intent?) {
        if (intent?.action == OPEN_BACKUP_OPTIONS) {
            backupShortcutRequested = true
            intent.action = null
        }
    }
}

private val ledgerTypography = Typography().run {
    copy(
        headlineLarge = headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.7).sp),
        headlineMedium = headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.35).sp),
        titleMedium = titleMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.sp),
        labelLarge = labelLarge.copy(letterSpacing = 0.sp),
    )
}

@Composable
private fun HoursApp(
    model: HoursViewModel,
    state: HoursState?,
    theme: ThemeMode,
    backupShortcutRequested: Boolean,
    onBackupShortcutHandled: () -> Unit,
) {
    val context = LocalContext.current.applicationContext
    val saving by model.saving.collectAsStateWithLifecycle()
    val saveFailed by model.saveFailed.collectAsStateWithLifecycle()
    val backupStatus by model.backupStatus.collectAsStateWithLifecycle()
    val deletedShift by model.deletedShift.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableStateOf(HoursTab.Home) }
    var editingShiftId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmingShiftId by rememberSaveable { mutableStateOf<Long?>(null) }
    var jobFormId by rememberSaveable { mutableStateOf<Long?>(null) }
    var showingSettings by rememberSaveable { mutableStateOf(false) }
    var choosingLanguage by rememberSaveable { mutableStateOf(false) }
    var showingBackupOptions by rememberSaveable { mutableStateOf(false) }
    var showingAutoBackup by rememberSaveable { mutableStateOf(false) }
    val closeAutoBackup: () -> Unit = {
        showingAutoBackup = false
        showingBackupOptions = true
    }
    val tabState = rememberSaveableStateHolder()
    val snackbarHost = remember { SnackbarHostState() }
    val deletedMessage = stringResource(R.string.shift_deleted)
    val undoLabel = stringResource(R.string.undo)
    LaunchedEffect(deletedShift, deletedMessage, undoLabel) {
        val shift = deletedShift ?: return@LaunchedEffect
        val result = snackbarHost.showSnackbar(deletedMessage, undoLabel, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) {
            model.saving.first { !it }
            model.undoShiftDeletion(shift)
        } else {
            model.dismissShiftUndo(shift)
        }
    }
    val chooseBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(model::previewBackup)
    }
    val createBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) model.exportBackup(uri, appLanguageTags())
    }

    val current = state
    val preferences = current?.preferences
    val jobToEdit = current?.jobs?.firstOrNull { it.id == jobFormId }
    val showingJobForm = jobFormId == 0L || jobToEdit != null
    BackHandler(
        enabled = showingAutoBackup || editingShiftId != null || showingJobForm || showingSettings ||
            selectedTab != HoursTab.Home,
    ) {
        when {
            showingAutoBackup -> closeAutoBackup()
            editingShiftId != null -> editingShiftId = null
            showingJobForm -> jobFormId = null
            showingSettings -> showingSettings = false
            else -> selectedTab = HoursTab.Home
        }
    }

    LaunchedEffect(backupShortcutRequested, preferences != null) {
        if (backupShortcutRequested && preferences != null) {
            editingShiftId = null
            confirmingShiftId = null
            jobFormId = null
            showingSettings = false
            showingAutoBackup = false
            selectedTab = HoursTab.Log
            showingBackupOptions = true
            onBackupShortcutHandled()
        }
    }

    val pendingLanguage = preferences?.pendingLanguageTags
    LaunchedEffect(pendingLanguage) {
        if (pendingLanguage != null) {
            restoreAppLanguage(pendingLanguage)
            model.acknowledgeRestoredLanguage(pendingLanguage)
        }
    }
    LaunchedEffect(backupStatus) {
        if (backupStatus == BackupStatus.Exported) dismissAutoBackupNotification(context)
        if (backupStatus == BackupStatus.Restored) {
            selectedTab = HoursTab.Home
            tabState.removeState(HoursTab.Log.name)
            tabState.removeState(HoursTab.Summary.name)
        }
    }

    val showTabControls = preferences != null && editingShiftId == null && !showingJobForm && !showingSettings &&
        !showingAutoBackup
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            if (showTabControls) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                    for (tab in HoursTab.entries) {
                        NavigationBarItem(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab },
                            icon = { NavIcon(tab) },
                            label = {
                                Text(
                                    stringResource(tab.label),
                                    fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Medium,
                                )
                            },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                current == null ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                preferences == null -> FirstRunSetup(
                    saving = saving,
                    onChooseLanguage = { choosingLanguage = true },
                    onImport = { chooseBackup.launch(arrayOf("*/*")) },
                ) { currency, job ->
                    model.save { setup(currency, job) }
                }
                showingAutoBackup -> AutoBackupScreen(onBack = closeAutoBackup)
                showingJobForm -> JobForm(
                    preferences.currencyCode, saving, onBack = { jobFormId = null }, original = jobToEdit,
                    hasCompletedEntries = jobToEdit != null && current.finishedShifts.any { it.jobId == jobToEdit.id },
                ) { job, recalculatePast ->
                    model.save(onSaved = { jobFormId = null }) {
                        val savedJob = saveJob(job, recalculatePast)
                        model.state.first { it?.jobs?.contains(savedJob) == true }
                    }
                }
                showingSettings -> SettingsScreen(
                    theme = theme,
                    lightAccent = AccentColor.fromKey(preferences.lightAccent) ?: AccentColor.Default,
                    darkAccent = AccentColor.fromKey(preferences.darkAccent) ?: AccentColor.Default,
                    jobs = current.jobs,
                    hasIsraeliPayroll = current.jobs.any { it.currencyCode == ILS } ||
                        current.finishedShifts.any { it.currencyCode == ILS },
                    quarterPoints = preferences.creditQuarterPoints,
                    saving = saving,
                    onBack = { showingSettings = false },
                    onChooseLanguage = { choosingLanguage = true },
                    onChooseTheme = { mode -> if (mode != theme) model.save { setTheme(mode) } },
                    onChooseAccent = { dark, accent -> model.save { setAccent(dark, accent) } },
                    onAddJob = { jobFormId = 0L },
                    onEditJob = { jobFormId = it.id },
                    onSaveCredits = { points -> model.save { setCreditQuarterPoints(points) } },
                )
                editingShiftId != null -> {
                    val shift = current.finishedShifts.firstOrNull { it.id == editingShiftId }
                    ShiftEditor(
                        shift, current.jobs, current.finishedShifts, preferences.manualShiftTimes,
                        saving, onBack = { editingShiftId = null },
                        onSave = { entry ->
                            model.save(onSaved = {
                                editingShiftId = null
                                if (shift == null) selectedTab = HoursTab.Log
                            }) {
                                saveShift(shift?.id, entry)
                                if (shift == null && entry.times != null) {
                                    model.state.first { it?.preferences?.manualShiftTimes == entry.times }
                                }
                            }
                        },
                        onSaveRange = { range ->
                            model.save(onSaved = {
                                editingShiftId = null
                                selectedTab = HoursTab.Log
                            }) {
                                if (saveShiftRange(range) > 0) {
                                    model.state.first { it?.preferences?.manualShiftTimes == range.times }
                                }
                            }
                        },
                    )
                }
                selectedTab == HoursTab.Log -> tabState.SaveableStateProvider(HoursTab.Log.name) {
                    ShiftLog(
                        current.finishedShifts, current.jobs, preferences.currencyCode,
                        onBackup = { showingBackupOptions = true },
                        onAdd = { editingShiftId = 0 },
                        onEdit = { editingShiftId = it },
                        onDelete = { confirmingShiftId = it },
                    )
                }
                selectedTab == HoursTab.Summary -> tabState.SaveableStateProvider(HoursTab.Summary.name) {
                    SummaryScreen(
                        current.finishedShifts, current.jobs, preferences, saving,
                        onIncomeTaxChange = { include -> model.save { setIncludeIncomeTax(include) } },
                        onHideIncomeTaxWarning = { model.save { hideIncomeTaxWarning() } },
                        onEdit = { editingShiftId = it },
                    )
                }
                selectedTab == HoursTab.Info -> tabState.SaveableStateProvider(HoursTab.Info.name) {
                    InfoScreen()
                }
                else -> HomeScreen(
                    current, preferences.currencyCode, saving,
                    onSettings = { showingSettings = true },
                    onAddShift = { editingShiftId = 0 },
                    onStart = { jobId ->
                        model.save { clockIn(jobId, System.currentTimeMillis(), ZoneId.systemDefault()) }
                    },
                    onStop = { shiftId, end, breaks, adjustments, onSaved ->
                        model.save(onSaved = onSaved) { clockOut(shiftId, end, breaks, adjustments) }
                    },
                )
            }
        }
    }

    confirmingShiftId?.let { id ->
        val vacation = current?.finishedShifts?.firstOrNull { it.id == id }?.isVacation == true
        AlertDialog(
            onDismissRequest = { confirmingShiftId = null },
            title = { Text(stringResource(if (vacation) R.string.payroll_delete_vacation else R.string.delete_shift)) },
            text = {
                Text(
                    stringResource(
                        if (vacation) R.string.payroll_delete_vacation_confirm else R.string.delete_shift_confirmation,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !saving,
                    onClick = {
                        confirmingShiftId = null
                        model.deleteShift(id)
                    },
                ) {
                    Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingShiftId = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (showingBackupOptions) {
        BackupOptions(
            onExport = {
                showingBackupOptions = false
                createBackup.launch("HourThreads-backup-${LocalDate.now()}.json")
            },
            onImport = {
                showingBackupOptions = false
                chooseBackup.launch(arrayOf("*/*"))
            },
            onAutoBackup = {
                showingBackupOptions = false
                showingAutoBackup = true
            },
            onDismiss = { showingBackupOptions = false },
        )
    }

    backupStatus?.let { status ->
        BackupResultDialog(
            status,
            hasExistingData = preferences != null,
            onConfirm = model::restoreBackup,
            onDismiss = model::dismissBackup,
        )
    }

    if (choosingLanguage) {
        LanguagePicker(
            onSelect = {
                choosingLanguage = false
                it.select()
            },
            onDismiss = { choosingLanguage = false },
        )
    }

    if (saveFailed) {
        AlertDialog(
            onDismissRequest = model::dismissError,
            text = { Text(stringResource(R.string.save_failed)) },
            confirmButton = {
                TextButton(onClick = model::dismissError) { Text(stringResource(R.string.dismiss)) }
            },
        )
    }
}

@Composable
internal fun PageTitle(title: String, onBack: (() -> Unit)? = null, action: @Composable () -> Unit = {}) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (onBack != null) BackButton(onBack)
        Column(Modifier.weight(1f)) {
            Text(title, Modifier.semantics { heading() }, style = MaterialTheme.typography.headlineLarge)
        }
        action()
    }
}
