package app.hourstracker

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupCodecTest {
    private val overnight = BackupShift(
        id = 5, jobId = 2, startMillis = 1_795_377_600_000, endMillis = 1_795_408_200_000,
        zoneId = "Asia/Jerusalem", hourlyRate = "18.500", currencyCode = "ILS", breakMinutes = 30,
        note = "Café Luna\n棚卸し “night shift”",
    )
    private val activeShift = overnight.copy(id = 6, jobId = 3, endMillis = null)
    private val backup = HourThreadBackup(
        settings = BackupSettings(
            "ILS", "he", ThemeMode.Dark.key, AccentColor.Blue.key, AccentColor.Rose.key,
            ManualShiftTimes(LocalTime.of(18, 0), LocalTime.of(2, 30)),
        ),
        jobs = listOf(BackupJob(2, "Café Luna", "20", 30), BackupJob(3, "מחסן", "18.50", 0)),
        shifts = listOf(overnight, activeShift),
    )

    @Test
    fun roundTripKeepsPreferencesJobsAndHistoricalShiftData() {
        val restored = BackupCodec.decode(BackupCodec.encode(backup))
        assertEquals(backup, restored)
        assertNull(restored.shifts.first().toShift().activeSlot)
        assertEquals(1, restored.shifts.last().toShift().activeSlot)
        assertEquals(overnight, restored.shifts.first().toShift().toBackupShift())
    }

    @Test
    fun keepsPayrollDefaultsCreditPointsAndPaidVacation() {
        val contributions = EmployeeContributions("6", "2.5")
        val payroll = JobPayroll("280.00", ShiftAdjustments("7", "10", "18.50"), contributions)
        val vacation = overnight.copy(
            endMillis = overnight.startMillis, breakMinutes = 0,
            payroll = ShiftPayroll(contributions = contributions, vacationPay = "280.00"),
        )
        val withPayroll = backup.copy(
            settings = backup.settings.copy(creditQuarterPoints = 9),
            jobs = listOf(backup.jobs.first().copy(payroll = payroll), backup.jobs.last()),
            shifts = listOf(vacation),
        )
        assertEquals(withPayroll, BackupCodec.decode(BackupCodec.encode(withPayroll)))
        assertEquals(vacation, vacation.toShift().toBackupShift())
        assertEquals(0L, vacation.toShift().completedDurationMillis)
    }

    @Test
    fun roundTripsFuelReimbursementOnJobsAndShifts() {
        val fuel = ShiftAdjustments(travel = "300", fuelReimbursement = true, fuelPaidMonthly = true)
        val withFuel = backup.copy(
            jobs = listOf(backup.jobs.first().copy(payroll = JobPayroll(adjustments = fuel))),
            shifts = listOf(overnight.copy(payroll = ShiftPayroll(adjustments = fuel))),
        )
        assertEquals(withFuel, BackupCodec.decode(BackupCodec.encode(withFuel)))
        assertEquals(fuel, withFuel.shifts.first().toShift().payrollDetails.adjustments)
    }

    @Test(expected = InvalidBackup::class)
    fun rejectsVacationWithWorkedTime() {
        val invalid = backup.copy(shifts = listOf(overnight.copy(payroll = ShiftPayroll(vacationPay = "280"))))
        BackupCodec.decode(BackupCodec.encode(invalid))
    }

    @Test
    fun deviceLanguageAndEmptyHistoryRoundTrip() {
        val empty = backup.copy(settings = BackupSettings("ILS", ""), shifts = emptyList())
        assertEquals(empty, BackupCodec.decode(BackupCodec.encode(empty)))
    }

    @Test
    fun roundTripsIncomeTaxAndWarningPreferences() {
        val settings = backup.settings.copy(includeIncomeTax = false, hideIncomeTaxWarning = true)
        val updated = backup.copy(settings = settings)
        assertEquals(updated, BackupCodec.decode(BackupCodec.encode(updated)))
    }

    @Test
    fun roundTripsJobCurrenciesAndPreservesTheCurrencyOnHistoricalShifts() {
        val usdJob = backup.jobs.first().copy(currencyCode = "USD")
        val mixed = backup.copy(
            jobs = listOf(usdJob, backup.jobs.last().copy(currencyCode = ILS)),
            shifts = listOf(overnight, overnight.copy(id = 7, currencyCode = "USD")),
        )
        val restored = BackupCodec.decode(BackupCodec.encode(mixed))
        assertEquals(mixed, restored)
        assertEquals("USD", restored.jobs.first().toJob(restored.settings.currencyCode).currencyCode)
        assertEquals(listOf(ILS, "USD"), restored.shifts.map { it.toShift().currencyCode })
        assertEquals("JPY", backup.jobs.first().toJob("JPY").currencyCode)
    }

    @Test
    fun legacyBackupDefaultsToSystemTheme() {
        val legacy = """
            {
              "format": "hourthread-backup",
              "version": 1,
              "settings": {"currencyCode": "ILS", "languageTags": "he"},
              "jobs": [{"id": 2, "name": "Café Luna", "hourlyRate": "20", "breakMinutes": 30}],
              "shifts": []
            }
        """.trimIndent()
        val restored = BackupCodec.decode(legacy)
        assertEquals(BackupSettings("ILS", "he", ThemeMode.System.key), restored.settings)
        assertEquals(listOf(backup.jobs.first()), restored.jobs)
    }

    @Test
    fun versionTwoBackupDefaultsBothAccents() {
        val legacy = """
            {
              "format": "hourthread-backup",
              "version": 2,
              "settings": {"currencyCode": "ILS", "languageTags": "he", "themeMode": "dark"},
              "jobs": [{"id": 2, "name": "Café Luna", "hourlyRate": "20", "breakMinutes": 30}],
              "shifts": []
            }
        """.trimIndent()
        val restored = BackupCodec.decode(legacy)
        assertEquals(BackupSettings("ILS", "he", ThemeMode.Dark.key), restored.settings)
    }

    @Test
    fun versionThreeBackupHasNoRememberedTimes() {
        val legacy = """
            {
              "format": "hourthread-backup",
              "version": 3,
              "settings": {"currencyCode": "ILS", "languageTags": "he", "themeMode": "dark",
                           "lightAccent": "blue", "darkAccent": "rose"},
              "jobs": [{"id": 2, "name": "Café Luna", "hourlyRate": "20", "breakMinutes": 30}],
              "shifts": []
            }
        """.trimIndent()
        val restored = BackupCodec.decode(legacy)
        assertEquals(backup.settings.copy(manualShiftTimes = null), restored.settings)
    }

    @Test(expected = InvalidBackup::class)
    fun rejectsRememberedTimesOutsideTheDay() {
        val invalid = BackupCodec.encode(backup).replace("\"startMinuteOfDay\":1080", "\"startMinuteOfDay\":1440")
        BackupCodec.decode(invalid)
    }

    @Test(expected = InvalidBackup::class)
    fun rejectsUnknownAccent() {
        val invalid = backup.copy(settings = backup.settings.copy(darkAccent = "unknown"))
        BackupCodec.decode(BackupCodec.encode(invalid))
    }

    @Test(expected = InvalidBackup::class)
    fun rejectsInvalidTheme() {
        val invalid = backup.copy(settings = backup.settings.copy(themeMode = "unknown"))
        BackupCodec.decode(BackupCodec.encode(invalid))
    }

    @Test(expected = InvalidBackup::class)
    fun rejectsUnknownJobReference() {
        val invalid = backup.copy(shifts = listOf(overnight.copy(jobId = 999)))
        BackupCodec.decode(BackupCodec.encode(invalid))
    }

    @Test(expected = InvalidBackup::class)
    fun rejectsMultipleActiveShifts() {
        val invalid = backup.copy(shifts = listOf(activeShift, activeShift.copy(id = 7)))
        BackupCodec.decode(BackupCodec.encode(invalid))
    }

    @Test(expected = InvalidBackup::class)
    fun rejectsInvalidLanguage() {
        val invalid = backup.copy(settings = BackupSettings("ILS", "ja,"))
        BackupCodec.decode(BackupCodec.encode(invalid))
    }

    @Test(expected = InvalidBackup::class)
    fun rejectsTruncatedJson() {
        BackupCodec.decode(BackupCodec.encode(backup).dropLast(2))
    }

    @Test(expected = UnsupportedBackupVersion::class)
    fun rejectsBackupFromUnsupportedVersion() {
        BackupCodec.decode(BackupCodec.encode(backup.copy(version = backup.version + 1)))
    }
}
