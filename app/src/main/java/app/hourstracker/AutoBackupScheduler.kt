package app.hourstracker

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobScheduler
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.PersistableBundle
import java.time.ZonedDateTime
import java.time.Instant
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
internal enum class AutoBackupResult { Saved, ManualReminder, WriteFailed }

@Serializable
internal data class AutoBackupState(
    val plan: AutoBackupPlan = AutoBackupPlan(),
    val revision: String = "",
    val nextMillis: Long = 0,
    val lastRunMillis: Long = 0,
    val lastResult: AutoBackupResult? = null,
    val pendingDocument: String? = null,
)

internal data class AutoBackupRun(val revision: String, val scheduledMillis: Long)

internal class AutoBackupScheduler(context: Context) {
    private val app = context.applicationContext
    private val preferences = app.getSharedPreferences("hourthread_auto_backup", Context.MODE_PRIVATE)
    private val alarms = app.getSystemService(AlarmManager::class.java)
    private val jobs = app.getSystemService(JobScheduler::class.java)

    fun read(): AutoBackupState = preferences.getString(STATE, null)?.let { Json.decodeFromString(it) } ?: AutoBackupState()

    fun observe(onChange: (AutoBackupState) -> Unit): () -> Unit {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == STATE) onChange(read())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        onChange(read())
        return { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    fun save(plan: AutoBackupPlan, grantedFlags: Int) = synchronized(lock) {
        val previous = read()
        val uri = plan.folderUri?.let(Uri::parse)
        val hadWriteAccess = uri != null && hasWriteAccess(uri)
        if (uri != null && !hadWriteAccess && (previous.plan.folderUri != plan.folderUri || grantedFlags != 0)) {
            val allowed = grantedFlags and FOLDER_GRANT_FLAGS
            if (allowed and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0) throw SecurityException()
            app.contentResolver.takePersistableUriPermission(uri, allowed)
            if (!hasWriteAccess(uri)) throw SecurityException()
        }
        if (plan == previous.plan) {
            arm(previous)
            return@synchronized
        }

        val next = previous.copy(
            plan = plan,
            revision = UUID.randomUUID().toString(),
            nextMillis = if (plan.enabled) plan.nextAfter(ZonedDateTime.now()).toInstant().toEpochMilli() else 0,
            pendingDocument = null,
        )
        try {
            persist(next)
        } catch (failure: Exception) {
            if (uri != null && !hadWriteAccess && previous.plan.folderUri != plan.folderUri) releaseFolder(uri)
            throw failure
        }
        jobs.cancel(AUTO_BACKUP_JOB_ID)
        dismissAutoBackupNotification(app)
        arm(next)
        if (previous.plan.folderUri != plan.folderUri) previous.plan.folderUri?.let { releaseFolder(Uri.parse(it)) }
    }

    fun rearm(clockChanged: Boolean = false) = synchronized(lock) {
        val current = read()
        if (!current.plan.enabled) return@synchronized
        val now = ZonedDateTime.now()
        val pending = jobs.getPendingJob(AUTO_BACKUP_JOB_ID)?.extras?.run()
        val running = pending == AutoBackupRun(current.revision, current.nextMillis)
        val state = if (clockChanged && !running && current.pendingDocument == null &&
            current.nextMillis > now.toInstant().toEpochMilli()
        ) {
            current.copy(nextMillis = current.plan.nextAfter(now).toInstant().toEpochMilli()).also(::persist)
        } else {
            current
        }
        arm(state)
    }

    fun currentRun(run: AutoBackupRun): AutoBackupState? = synchronized(lock) {
        read().takeIf { it.matches(run) }
    }

    fun registerDocument(run: AutoBackupRun, uri: Uri): Boolean = synchronized(lock) {
        val current = read()
        if (!current.matches(run)) return@synchronized false
        persist(current.copy(pendingDocument = uri.toString()))
        true
    }

    fun complete(run: AutoBackupRun, outcome: AutoBackupResult) = synchronized(lock) {
        val current = read()
        if (!current.matches(run)) return@synchronized
        val now = ZonedDateTime.now()
        val scheduled = Instant.ofEpochMilli(run.scheduledMillis).atZone(now.zone)
        val next = current.copy(
            nextMillis = current.plan.nextAfter(maxOf(now, scheduled)).toInstant().toEpochMilli(),
            lastRunMillis = now.toInstant().toEpochMilli(),
            lastResult = outcome,
            pendingDocument = null,
        )
        persist(next)
        showAutoBackupNotification(app, outcome)
        arm(next)
    }

    private fun arm(state: AutoBackupState) {
        val alarm = PendingIntent.getBroadcast(
            app, 0, Intent(app, AutoBackupAlarmReceiver::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        if (!state.plan.enabled) {
            alarms.cancel(alarm)
        } else if (state.nextMillis > System.currentTimeMillis()) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, state.nextMillis, alarm)
        } else {
            val run = AutoBackupRun(state.revision, state.nextMillis)
            if (state.plan.folderUri == null) {
                complete(run, AutoBackupResult.ManualReminder)
                return
            }
            val existing = jobs.getPendingJob(AUTO_BACKUP_JOB_ID)
            if (existing?.extras?.run() == run) return
            val extras = PersistableBundle().apply {
                putString(REVISION, state.revision)
                putLong(SCHEDULED, state.nextMillis)
            }
            val queued = try {
                jobs.schedule(
                    JobInfo.Builder(AUTO_BACKUP_JOB_ID, ComponentName(app, AutoBackupJobService::class.java))
                        .setExtras(extras)
                        .setPersisted(true)
                        .setOverrideDeadline(0)
                        .build(),
                )
            } catch (_: IllegalStateException) {
                JobScheduler.RESULT_FAILURE
            }
            if (queued != JobScheduler.RESULT_SUCCESS) {
                complete(run, AutoBackupResult.WriteFailed)
            }
        }
    }

    private fun persist(state: AutoBackupState) {
        check(preferences.edit().putString(STATE, Json.encodeToString(state)).commit())
    }

    private fun hasWriteAccess(uri: Uri): Boolean =
        app.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }

    private fun releaseFolder(uri: Uri) {
        val grant = app.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (grant.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (grant.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        try {
            app.contentResolver.releasePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            // Android or the provider already revoked the folder grant.
        }
    }

    private fun AutoBackupState.matches(run: AutoBackupRun) =
        plan.enabled && run.revision == revision && run.scheduledMillis == nextMillis

    companion object {
        const val AUTO_BACKUP_JOB_ID = 0x4854
        const val FOLDER_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        private const val STATE = "state"
        private const val REVISION = "revision"
        private const val SCHEDULED = "scheduled"
        private val lock = Any()

        fun PersistableBundle.run(): AutoBackupRun? =
            getString(REVISION)?.let { AutoBackupRun(it, getLong(SCHEDULED)) }
    }
}

class AutoBackupAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        AutoBackupScheduler(context).rearm()
    }
}

class AutoBackupClockReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val clockChanged = intent.action == Intent.ACTION_TIME_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED
        AutoBackupScheduler(context).rearm(clockChanged)
    }
}
