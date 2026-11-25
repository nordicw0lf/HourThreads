package app.hourstracker

import android.content.Context
import androidx.room.Dao
import androidx.room.ColumnInfo
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Update
import androidx.room.migration.Migration
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

@Entity(tableName = "preferences")
internal data class Preferences(
    val currencyCode: String,
    @PrimaryKey val id: Int = 1,
    val pendingLanguageTags: String? = null,
    @ColumnInfo(defaultValue = "'system'") val themeMode: String = ThemeMode.System.key,
    @ColumnInfo(defaultValue = "'$DEFAULT_ACCENT_KEY'") val lightAccent: String = DEFAULT_ACCENT_KEY,
    @ColumnInfo(defaultValue = "'$DEFAULT_ACCENT_KEY'") val darkAccent: String = DEFAULT_ACCENT_KEY,
    @Embedded val manualShiftTimes: ManualShiftTimes? = null,
    @ColumnInfo(defaultValue = "0") val creditQuarterPoints: Int = 0,
    @ColumnInfo(defaultValue = "1") val includeIncomeTax: Boolean = true,
    @ColumnInfo(defaultValue = "0") val hideIncomeTaxWarning: Boolean = false,
)

@Entity(tableName = "jobs")
internal data class Job(
    val name: String,
    val hourlyRate: String,
    val breakMinutes: Int,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payroll: JobPayroll? = null,
    @ColumnInfo(defaultValue = "'$ILS'") val currencyCode: String,
)

@Entity(
    tableName = "shifts",
    foreignKeys = [ForeignKey(
        entity = Job::class,
        parentColumns = ["id"],
        childColumns = ["jobId"],
    )],
    indices = [Index("jobId"), Index(value = ["activeSlot"], unique = true)],
)
internal data class Shift(
    val jobId: Long,
    val startMillis: Long,
    val zoneId: String,
    val hourlyRate: String,
    val currencyCode: String,
    val breakMinutes: Int,
    val endMillis: Long? = null,
    val note: String = "",
    // SQLite allows multiple NULLs in this index, so finished shifts leave the slot free.
    val activeSlot: Int? = null,
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val payroll: ShiftPayroll? = null,
)

@Dao
internal interface HoursDao {
    @Query("SELECT * FROM preferences WHERE id = 1")
    fun observePreferences(): Flow<Preferences?>

    @Query("SELECT * FROM jobs ORDER BY id")
    fun observeJobs(): Flow<List<Job>>

    @Query("SELECT * FROM shifts ORDER BY startMillis DESC, id DESC")
    fun observeShifts(): Flow<List<Shift>>

    @Query("SELECT * FROM preferences WHERE id = 1")
    suspend fun preferences(): Preferences?

    @Query("SELECT * FROM jobs ORDER BY id")
    suspend fun allJobs(): List<Job>

    @Query("SELECT * FROM shifts ORDER BY startMillis DESC, id DESC")
    suspend fun allShifts(): List<Shift>

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun job(id: Long): Job?

    @Query("SELECT * FROM shifts WHERE id = :id")
    suspend fun shift(id: Long): Shift?

    @Query("SELECT * FROM shifts WHERE jobId = :jobId ORDER BY startMillis, id")
    suspend fun shiftsForJob(jobId: Long): List<Shift>

    @Query("SELECT * FROM shifts WHERE activeSlot = 1")
    suspend fun activeShift(): Shift?

    @Insert suspend fun insertPreferences(preferences: Preferences)
    @Insert suspend fun insertJob(job: Job): Long
    @Insert suspend fun insertShift(shift: Shift)
    @Insert suspend fun insertJobs(jobs: List<Job>)
    @Insert suspend fun insertShifts(shifts: List<Shift>)
    @Update suspend fun updateJob(job: Job): Int
    @Update suspend fun updateShift(shift: Shift)
    @Update suspend fun updateShifts(shifts: List<Shift>): Int

    @Query("DELETE FROM shifts")
    suspend fun clearShifts()

    @Query("DELETE FROM shifts WHERE id = :id AND endMillis IS NOT NULL")
    suspend fun deleteCompletedShift(id: Long): Int

    @Query("DELETE FROM jobs")
    suspend fun clearJobs()

    @Query("DELETE FROM preferences")
    suspend fun clearPreferences()

    @Query("UPDATE preferences SET pendingLanguageTags = NULL WHERE id = 1 AND pendingLanguageTags = :tags")
    suspend fun acknowledgeRestoredLanguage(tags: String)

    @Query("UPDATE preferences SET themeMode = :mode WHERE id = 1")
    suspend fun setTheme(mode: String)

    @Query("UPDATE preferences SET lightAccent = :accent WHERE id = 1")
    suspend fun setLightAccent(accent: String)

    @Query("UPDATE preferences SET darkAccent = :accent WHERE id = 1")
    suspend fun setDarkAccent(accent: String)

    @Query("UPDATE preferences SET startMinuteOfDay = :start, endMinuteOfDay = :end WHERE id = 1")
    suspend fun setManualShiftTimes(start: Int, end: Int)

    @Query("UPDATE preferences SET creditQuarterPoints = :points WHERE id = 1")
    suspend fun setCreditQuarterPoints(points: Int)

    @Query("UPDATE preferences SET includeIncomeTax = :include WHERE id = 1")
    suspend fun setIncludeIncomeTax(include: Boolean)

    @Query("UPDATE preferences SET hideIncomeTaxWarning = 1 WHERE id = 1")
    suspend fun hideIncomeTaxWarning()
}

@Database(entities = [Preferences::class, Job::class, Shift::class], version = 8)
@TypeConverters(PayrollConverters::class)
internal abstract class HoursDatabase : RoomDatabase() {
    abstract fun dao(): HoursDao

    companion object {
        private val addPendingLanguage = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferences ADD COLUMN pendingLanguageTags TEXT")
            }
        }

        private val addTheme = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferences ADD COLUMN themeMode TEXT NOT NULL DEFAULT 'system'")
            }
        }

        private val addAccentColors = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferences ADD COLUMN lightAccent TEXT NOT NULL DEFAULT '$DEFAULT_ACCENT_KEY'")
                db.execSQL("ALTER TABLE preferences ADD COLUMN darkAccent TEXT NOT NULL DEFAULT '$DEFAULT_ACCENT_KEY'")
            }
        }

        private val addManualShiftTimes = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferences ADD COLUMN startMinuteOfDay INTEGER")
                db.execSQL("ALTER TABLE preferences ADD COLUMN endMinuteOfDay INTEGER")
            }
        }

        private val addPayroll = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferences ADD COLUMN creditQuarterPoints INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE jobs ADD COLUMN payroll TEXT")
                db.execSQL("ALTER TABLE shifts ADD COLUMN payroll TEXT")
            }
        }

        private val addIncomeTaxPreferences = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE preferences ADD COLUMN includeIncomeTax INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE preferences ADD COLUMN hideIncomeTaxWarning INTEGER NOT NULL DEFAULT 0")
            }
        }

        private val addJobCurrency = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE jobs ADD COLUMN currencyCode TEXT NOT NULL DEFAULT '$ILS'")
                db.execSQL(
                    "UPDATE jobs SET currencyCode = COALESCE((SELECT currencyCode FROM preferences WHERE id = 1), '$ILS')",
                )
            }
        }

        fun open(context: Context): HoursDatabase =
            Room.databaseBuilder(context.applicationContext, HoursDatabase::class.java, "hours.db")
                .addMigrations(
                    addPendingLanguage, addTheme, addAccentColors, addManualShiftTimes,
                    addPayroll, addIncomeTaxPreferences, addJobCurrency,
                )
                .build()
    }
}

internal data class HoursState(
    val preferences: Preferences?,
    val jobs: List<Job>,
    val shifts: List<Shift>,
) {
    val activeShift = shifts.firstOrNull { it.activeSlot != null }
    val finishedShifts = shifts.filter { it.endMillis != null }
}

internal data class ManualShiftEntry(
    val jobId: Long,
    val start: Long,
    val end: Long,
    val zone: ZoneId,
    val breakMinutes: Int,
    val note: String,
    val times: ManualShiftTimes?,
    val payroll: ShiftPayroll,
)

internal class HoursRepository(private val database: HoursDatabase) {
    private val dao = database.dao()
    val state = combine(dao.observePreferences(), dao.observeJobs(), dao.observeShifts(), ::HoursState)

    suspend fun makeBackup(languageTags: String): HourThreadBackup = database.withTransaction {
        val preferences = checkNotNull(dao.preferences())
        HourThreadBackup(
            settings = BackupSettings(
                preferences.currencyCode,
                preferences.pendingLanguageTags ?: languageTags,
                preferences.themeMode,
                preferences.lightAccent,
                preferences.darkAccent,
                preferences.manualShiftTimes,
                preferences.creditQuarterPoints,
                preferences.includeIncomeTax,
                preferences.hideIncomeTaxWarning,
            ),
            jobs = dao.allJobs().map { it.toBackupJob() },
            shifts = dao.allShifts().map { it.toBackupShift() },
        )
    }

    suspend fun restoreBackup(backup: HourThreadBackup) = database.withTransaction {
        dao.clearShifts()
        dao.clearJobs()
        dao.clearPreferences()
        dao.insertJobs(backup.jobs.map { it.toJob(backup.settings.currencyCode) })
        dao.insertShifts(backup.shifts.map { it.toShift() })
        dao.insertPreferences(
            Preferences(
                currencyCode = backup.settings.currencyCode,
                pendingLanguageTags = backup.settings.languageTags,
                themeMode = backup.settings.themeMode,
                lightAccent = backup.settings.lightAccent,
                darkAccent = backup.settings.darkAccent,
                manualShiftTimes = backup.settings.manualShiftTimes,
                creditQuarterPoints = backup.settings.creditQuarterPoints,
                includeIncomeTax = backup.settings.includeIncomeTax,
                hideIncomeTaxWarning = backup.settings.hideIncomeTaxWarning,
            ),
        )
    }

    suspend fun acknowledgeRestoredLanguage(tags: String) = dao.acknowledgeRestoredLanguage(tags)

    suspend fun setCreditQuarterPoints(points: Int) {
        require(points >= 0)
        dao.setCreditQuarterPoints(points)
    }

    suspend fun setIncludeIncomeTax(include: Boolean) = dao.setIncludeIncomeTax(include)

    suspend fun hideIncomeTaxWarning() = dao.hideIncomeTaxWarning()

    suspend fun setTheme(mode: ThemeMode) = dao.setTheme(mode.key)

    suspend fun setAccent(dark: Boolean, accent: AccentColor) {
        if (dark) dao.setDarkAccent(accent.key) else dao.setLightAccent(accent.key)
    }

    suspend fun setup(currencyCode: String, job: Job) = database.withTransaction {
        if (dao.preferences() == null) {
            dao.insertJob(job)
            dao.insertPreferences(Preferences(currencyCode))
        }
    }

    suspend fun saveJob(job: Job, recalculatePast: Boolean): Job = database.withTransaction {
        require(job.payroll?.isValid() != false)
        if (job.id == 0L) return@withTransaction job.copy(id = dao.insertJob(job))
        val changes = JobPayChanges(checkNotNull(dao.job(job.id)), job)
        check(dao.updateJob(job) == 1)
        if ((recalculatePast && changes.affectsPastPay) || changes.preserveLegacyContributions) {
            val shifts = changes.updatedShifts(dao.shiftsForJob(job.id), recalculatePast)
            if (shifts.isNotEmpty()) check(dao.updateShifts(shifts) == shifts.size)
        }
        job
    }

    suspend fun deleteCompletedShift(id: Long): Shift? = database.withTransaction {
        val shift = dao.shift(id)?.takeIf { it.endMillis != null } ?: return@withTransaction null
        check(dao.deleteCompletedShift(id) == 1)
        shift
    }

    suspend fun restoreDeletedShift(shift: Shift) = dao.insertShift(shift)

    suspend fun clockIn(jobId: Long, now: Long, zone: ZoneId) = database.withTransaction {
        if (dao.activeShift() != null) return@withTransaction
        val job = checkNotNull(dao.job(jobId))
        dao.insertShift(
            Shift(
                jobId = job.id, startMillis = now, zoneId = zone.id,
                hourlyRate = job.hourlyRate, currencyCode = job.currencyCode,
                breakMinutes = job.breakMinutes, activeSlot = 1,
                payroll = job.payroll?.forShift(),
            ),
        )
    }

    suspend fun clockOut(
        shiftId: Long, now: Long, breakMinutes: Int, adjustments: ShiftAdjustments,
    ) = database.withTransaction {
        val shift = dao.activeShift() ?: return@withTransaction
        if (shift.id != shiftId) return@withTransaction
        require(isValidShift(shift.startMillis, now, breakMinutes))
        require(adjustments.isValid())
        val contributions = shift.payroll?.contributions ?: checkNotNull(dao.job(shift.jobId)).payrollDefaults.contributions
        dao.updateShift(
            shift.copy(
                endMillis = now, breakMinutes = breakMinutes, activeSlot = null,
                payroll = ShiftPayroll(adjustments, contributions),
            ),
        )
    }

    suspend fun saveShift(shiftId: Long?, entry: ManualShiftEntry) = database.withTransaction {
        val job = checkNotNull(dao.job(entry.jobId))
        val original = shiftId?.let { checkNotNull(dao.shift(it)) }
        require(original == null || original.endMillis != null)
        val shift = entry.toShift(job, original)
        if (original == null) {
            dao.insertShift(shift)
            entry.times?.let { dao.setManualShiftTimes(it.startMinuteOfDay, it.endMinuteOfDay) }
        } else {
            dao.updateShift(shift)
        }
    }

    suspend fun saveShiftRange(range: ManualShiftRange): Int = database.withTransaction {
        val job = checkNotNull(dao.job(range.jobId))
        val plan = range.plan(dao.shiftsForJob(range.jobId))
        require(plan.invalidDay == null)
        if (plan.entries.isNotEmpty()) {
            dao.insertShifts(plan.entries.map { it.toShift(job) })
            dao.setManualShiftTimes(range.times.startMinuteOfDay, range.times.endMinuteOfDay)
        }
        plan.entries.size
    }
}

private fun ManualShiftEntry.toShift(job: Job, original: Shift? = null): Shift {
    require(payroll.isValid())
    if (payroll.vacationPay == null) require(isValidShift(start, end, breakMinutes))
    else require(start == end && breakMinutes == 0)
    val retained = original?.takeIf { it.jobId == jobId }
    val contributions = retained?.payroll?.contributions ?: job.payrollDefaults.contributions
    return Shift(
        jobId = jobId, startMillis = start, endMillis = end, zoneId = zone.id,
        hourlyRate = retained?.hourlyRate ?: job.hourlyRate,
        currencyCode = retained?.currencyCode ?: job.currencyCode,
        breakMinutes = breakMinutes, note = note, id = original?.id ?: 0,
        payroll = payroll.copy(contributions = contributions),
    )
}
