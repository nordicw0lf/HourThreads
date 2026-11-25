package app.hourstracker

import java.time.ZoneId
import java.util.Currency
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

private const val BACKUP_FORMAT = "hourthread-backup"
private const val BACKUP_VERSION = 9

@Serializable
internal data class HourThreadBackup(
    val format: String = BACKUP_FORMAT,
    val version: Int = BACKUP_VERSION,
    val settings: BackupSettings,
    val jobs: List<BackupJob>,
    val shifts: List<BackupShift>,
)

@Serializable
internal data class BackupSettings(
    val currencyCode: String,
    val languageTags: String,
    val themeMode: String = ThemeMode.System.key,
    val lightAccent: String = DEFAULT_ACCENT_KEY,
    val darkAccent: String = DEFAULT_ACCENT_KEY,
    val manualShiftTimes: ManualShiftTimes? = null,
    val creditQuarterPoints: Int = 0,
    val includeIncomeTax: Boolean = true,
    val hideIncomeTaxWarning: Boolean = false,
)

@Serializable
internal data class BackupJob(
    val id: Long,
    val name: String,
    val hourlyRate: String,
    val breakMinutes: Int,
    val payroll: JobPayroll? = null,
    val currencyCode: String? = null,
) {
    fun toJob(defaultCurrency: String) = Job(name, hourlyRate, breakMinutes, id, payroll, currencyCode ?: defaultCurrency)
}

@Serializable
internal data class BackupShift(
    val id: Long,
    val jobId: Long,
    val startMillis: Long,
    val endMillis: Long?,
    val zoneId: String,
    val hourlyRate: String,
    val currencyCode: String,
    val breakMinutes: Int,
    val note: String,
    val payroll: ShiftPayroll? = null,
) {
    fun toShift() = Shift(
        jobId, startMillis, zoneId, hourlyRate, currencyCode, breakMinutes, endMillis, note,
        activeSlot = if (endMillis == null) 1 else null, id = id, payroll = payroll,
    )
}

internal fun Job.toBackupJob() = BackupJob(id, name, hourlyRate, breakMinutes, payroll, currencyCode)

internal fun Shift.toBackupShift() =
    BackupShift(id, jobId, startMillis, endMillis, zoneId, hourlyRate, currencyCode, breakMinutes, note, payroll)

internal class UnsupportedBackupVersion : Exception()
internal class InvalidBackup : Exception()

internal object BackupCodec {
    private val json = Json { encodeDefaults = true }
    private val decimal = Regex("[0-9]+(?:\\.[0-9]+)?")
    private val supportedLanguages = setOf("en", "he", "ja")

    fun encode(backup: HourThreadBackup): String = json.encodeToString(backup)

    fun decode(contents: String): HourThreadBackup {
        try {
            val document = json.parseToJsonElement(contents.removePrefix("\uFEFF")) as? JsonObject
            require(document != null && document["format"] == JsonPrimitive(BACKUP_FORMAT))
            val fileVersion = (document["version"] as? JsonPrimitive)?.takeUnless { it.isString }?.intOrNull
                ?: throw InvalidBackup()
            if (fileVersion !in 1..BACKUP_VERSION) throw UnsupportedBackupVersion()
            return json.decodeFromJsonElement(HourThreadBackup.serializer(), document).also(::validate)
        } catch (_: IllegalArgumentException) {
            throw InvalidBackup()
        }
    }

    private fun validate(backup: HourThreadBackup) {
        val currency = backup.settings.currencyCode
        require(Currency.getInstance(currency).defaultFractionDigits >= 0)
        require(ThemeMode.fromKey(backup.settings.themeMode) != null)
        require(AccentColor.fromKey(backup.settings.lightAccent) != null)
        require(AccentColor.fromKey(backup.settings.darkAccent) != null)
        require(backup.settings.creditQuarterPoints >= 0)
        val tags = backup.settings.languageTags
        if (tags.isNotEmpty()) {
            require(tags.split(',').all { tag ->
                val normalized = runCatching { Locale.Builder().setLanguageTag(tag).build().toLanguageTag() }.getOrNull()
                    ?: return@all false
                normalized.substringBefore('-') in supportedLanguages
            })
        }

        require(backup.jobs.isNotEmpty())
        val jobIds = HashSet<Long>()
        for (job in backup.jobs) {
            require(job.id in 1 until Long.MAX_VALUE && jobIds.add(job.id))
            require(job.name.isNotBlank() && decimal.matches(job.hourlyRate) && job.breakMinutes >= 0)
            require(job.payroll?.isValid() != false)
            require(Currency.getInstance(job.currencyCode ?: currency).defaultFractionDigits >= 0)
        }
        val shiftIds = HashSet<Long>()
        var hasRunningShift = false
        for (shift in backup.shifts) {
            require(shift.id in 1 until Long.MAX_VALUE && shiftIds.add(shift.id) && shift.jobId in jobIds)
            require(backup.version >= 9 || shift.currencyCode == currency)
            require(Currency.getInstance(shift.currencyCode).defaultFractionDigits >= 0)
            require(decimal.matches(shift.hourlyRate) && shift.breakMinutes >= 0)
            require(runCatching { ZoneId.of(shift.zoneId) }.isSuccess)
            require(shift.payroll?.isValid() != false)
            val end = shift.endMillis
            if (end == null) {
                require(!hasRunningShift && shift.payroll?.vacationPay == null)
                hasRunningShift = true
            } else if (shift.payroll?.vacationPay != null) {
                require(end == shift.startMillis && shift.breakMinutes == 0)
            } else {
                val duration = runCatching { Math.subtractExact(end, shift.startMillis) }.getOrNull()
                require(duration != null && duration > 0 && isValidShift(shift.startMillis, end, shift.breakMinutes))
            }
        }
    }
}
