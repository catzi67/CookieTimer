// --- src/main/java/com/catto/cookietimer/NotificationHelper.kt ---
package com.catto.cookietimer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

// NotificationHelper: Manages creation and updates of notifications.
class NotificationHelper(private val context: Context) {

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_ID_RUNNING_TIMERS = "running_timers_channel"
        const val CHANNEL_NAME_RUNNING_TIMERS = "Running Timers"
        const val CHANNEL_DESCRIPTION_RUNNING_TIMERS = "Notifications for active CookieTimers"

        const val CHANNEL_ID_ALARM = "timer_alarm_channel"
        const val CHANNEL_NAME_ALARM = "Timer Alarm"
        const val CHANNEL_DESCRIPTION_ALARM = "Notifications when a timer finishes"

        const val RUNNING_TIMER_NOTIFICATION_ID = 1
        const val TIMER_ALARM_NOTIFICATION_ID = 2 // Will be unique per timer later
    }

    init {
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel for running timers (lower importance as it's ongoing)
            val runningTimersChannel = NotificationChannel(
                CHANNEL_ID_RUNNING_TIMERS,
                CHANNEL_NAME_RUNNING_TIMERS,
                NotificationManager.IMPORTANCE_LOW // Low importance for ongoing background tasks
            ).apply {
                description = CHANNEL_DESCRIPTION_RUNNING_TIMERS
            }
            notificationManager.createNotificationChannel(runningTimersChannel)

            // Channel for alarms (high importance as it needs user attention)
            val alarmChannel = NotificationChannel(
                CHANNEL_ID_ALARM,
                CHANNEL_NAME_ALARM,
                NotificationManager.IMPORTANCE_HIGH // High importance for alarms
            ).apply {
                description = CHANNEL_DESCRIPTION_ALARM
                setSound(null, null) // Alarm sound will be played by MediaPlayer, not notification
                enableVibration(true)
            }
            notificationManager.createNotificationChannel(alarmChannel)
        }
    }

    // Builds the notification for the running TimerService.
    // Now takes a count of running timers to update content.
    fun buildRunningTimerNotification(runningCount: Int = 0): NotificationCompat.Builder {
        val notificationText = if (runningCount > 0) {
            context.resources.getQuantityString(R.plurals.running_timers_count, runningCount, runningCount)
        } else {
            context.getString(R.string.no_timers_running_notification_content)
        }

        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_RUNNING_TIMERS)
            .setContentTitle(context.getString(R.string.running_timers_notification_title))
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_notification_icon) // You'll need to create this icon (e.g., mipmap/ic_launcher)
            .setContentIntent(pendingIntent) // Tap to open app
            .setPriority(NotificationCompat.PRIORITY_LOW) // Matches channel importance
            .setOngoing(true) // Makes the notification non-dismissible by user swipe
    }

    // Builds the notification for a completed timer alarm.
    fun buildTimerAlarmNotification(timerName: String, timerId: Long): NotificationCompat.Builder {
        val notificationIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            timerId.toInt(), // Use timerId for unique PendingIntent
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID_ALARM)
            .setContentTitle(context.getString(R.string.timer_alarm_notification_title, timerName))
            .setContentText(context.getString(R.string.timer_alarm_notification_content))
            .setSmallIcon(R.drawable.ic_notification_icon) // You'll need to create this icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH) // High priority for alarm
            .setAutoCancel(true) // Dismisses notification when tapped
    }

    // To show a specific notification (e.g., when a timer finishes)
    fun showNotification(id: Int, notification: NotificationCompat.Builder) {
        notificationManager.notify(id, notification.build())
    }

    // To cancel a specific notification
    fun cancelNotification(id: Int) {
        notificationManager.cancel(id)
    }
}
