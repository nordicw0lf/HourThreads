package app.hourstracker

import android.app.job.JobParameters
import android.app.job.JobService
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.app.LocaleManagerCompat
import app.hourstracker.AutoBackupScheduler.Companion.run
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AutoBackupJobService : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var task: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val run = params.extras.run() ?: return false
        val scheduler = AutoBackupScheduler(this)
        val state = scheduler.currentRun(run) ?: return false
        task = scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val result = export(scheduler, run, state)
                    ensureActive()
                    if (result != null) scheduler.complete(run, result)
                }
                jobFinished(params, false)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                jobFinished(params, true)
            }
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        task?.cancel()
        val interrupted = params.extras.run()?.let { AutoBackupScheduler(this).currentRun(it) != null } == true
        if (interrupted) showAutoBackupNotification(this, AutoBackupResult.WriteFailed)
        return interrupted
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun export(
        scheduler: AutoBackupScheduler, run: AutoBackupRun, state: AutoBackupState,
    ): AutoBackupResult? {
        val folder = state.plan.folderUri?.let(Uri::parse) ?: return AutoBackupResult.ManualReminder
        var document = state.pendingDocument?.let(Uri::parse)
        return try {
            val database = HoursDatabase.open(this)
            val backup = try {
                val language = LocaleManagerCompat.getApplicationLocales(this).toLanguageTags()
                HoursRepository(database).makeBackup(language)
            } finally {
                database.close()
            }
            if (scheduler.currentRun(run) == null) return null
            if (document == null) {
                val parent = DocumentsContract.buildDocumentUriUsingTree(folder, DocumentsContract.getTreeDocumentId(folder))
                val date = Instant.ofEpochMilli(run.scheduledMillis).atZone(ZoneId.systemDefault()).format(filenameDate)
                val created = checkNotNull(
                    DocumentsContract.createDocument(contentResolver, parent, "application/json", "HourThreads-auto-backup-$date.json"),
                )
                document = created
                if (!scheduler.registerDocument(run, created)) {
                    deletePartialDocument(created)
                    return null
                }
            }
            BackupFiles(contentResolver).write(document, backup)
            AutoBackupResult.Saved
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            document?.let(::deletePartialDocument)
            AutoBackupResult.WriteFailed
        }
    }

    private fun deletePartialDocument(uri: Uri) {
        try {
            DocumentsContract.deleteDocument(contentResolver, uri)
        } catch (_: Exception) {
            // Some providers do not allow deletion once the write has failed.
        }
    }

    private companion object {
        val filenameDate: DateTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd_HH-mm", Locale.ROOT)
    }
}
