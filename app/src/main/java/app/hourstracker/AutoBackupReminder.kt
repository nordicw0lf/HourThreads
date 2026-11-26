package app.hourstracker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.app.LocaleManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

internal const val OPEN_BACKUP_OPTIONS = "app.hourstracker.OPEN_BACKUP_OPTIONS"
private const val BACKUP_CHANNEL = "backup-reminders"
private const val BACKUP_NOTIFICATION_ID = 0x4855

internal fun backupNotificationsEnabled(context: Context): Boolean =
    NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        context.getSystemService(NotificationManager::class.java).getNotificationChannel(BACKUP_CHANNEL)
            ?.importance != NotificationManager.IMPORTANCE_NONE

internal fun showAutoBackupNotification(context: Context, result: AutoBackupResult) {
    if (Build.VERSION.SDK_INT >= 33 &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return
    if (!backupNotificationsEnabled(context)) return

    val tags = LocaleManagerCompat.getApplicationLocales(context).toLanguageTags()
    val strings = if (tags.isEmpty()) context else {
        context.createConfigurationContext(Configuration(context.resources.configuration).apply {
            setLocales(LocaleList.forLanguageTags(tags))
        })
    }
    val notifications = context.getSystemService(NotificationManager::class.java)
    notifications.createNotificationChannel(
        NotificationChannel(
            BACKUP_CHANNEL, strings.getString(R.string.auto_backup_channel), NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )
    val open = PendingIntent.getActivity(
        context, BACKUP_NOTIFICATION_ID, Intent(context, MainActivity::class.java).apply {
            action = OPEN_BACKUP_OPTIONS
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_IMMUTABLE,
    )
    val title = when (result) {
        AutoBackupResult.Saved -> R.string.auto_backup_notification_saved
        AutoBackupResult.ManualReminder -> R.string.auto_backup_notification_due
        AutoBackupResult.WriteFailed -> R.string.auto_backup_notification_failed
    }
    val body = strings.getString(
        if (result == AutoBackupResult.Saved) R.string.auto_backup_notification_saved_body
        else R.string.auto_backup_notification_body,
    )
    val notification = NotificationCompat.Builder(context, BACKUP_CHANNEL)
        .setSmallIcon(R.drawable.ic_backup)
        .setContentTitle(strings.getString(title))
        .setContentText(body)
        .setStyle(NotificationCompat.BigTextStyle().bigText(body))
        .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    try {
        notifications.notify(BACKUP_NOTIFICATION_ID, notification)
    } catch (_: SecurityException) {
        // The permission can be revoked between checking it and posting.
    }
}

internal fun dismissAutoBackupNotification(context: Context) {
    context.getSystemService(NotificationManager::class.java).cancel(BACKUP_NOTIFICATION_ID)
}
