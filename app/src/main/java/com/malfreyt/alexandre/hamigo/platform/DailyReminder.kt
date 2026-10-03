package com.malfreyt.alexandre.hamigo.platform

import android.Manifest
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import com.malfreyt.alexandre.hamigo.Progress
import com.malfreyt.alexandre.hamigo.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** A battery-friendly daily reminder. Delivery can be delayed by Android's power management. */
object DailyReminder {
    const val CHANNEL_ID = "daily_practice"
    const val ACTION_REMIND = "com.malfreyt.alexandre.hamigo.REMIND"
    private const val REQUEST_CODE = 2701

    fun configure(context: Context, enabled: Boolean, hour: Int = 20, minute: Int = 0) {
        require(hour in 0..23 && minute in 0..59)
        synchronized(Progress.CLOUD_LOCK) {
            val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
            val changed = prefs.getBoolean("reminderEnabled", false) != enabled ||
                prefs.getInt("reminderHour", 20) != hour || prefs.getInt("reminderMinute", 0) != minute
            val edit = prefs.edit().putBoolean("reminderEnabled", enabled)
                .putInt("reminderHour", hour).putInt("reminderMinute", minute)
            if (changed) edit.putLong("preferencesUpdatedAt", System.currentTimeMillis())
            edit.apply()
            if (changed) ProgressSyncScheduler.enqueue(context)
        }
        schedule(context)
    }

    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "La petite onde du jour", NotificationManager.IMPORTANCE_DEFAULT)
                .apply { description = "Un rappel quotidien pour entretenir ta série de révisions." }
        )
    }

    /** Schedule one inexact alarm, then recalculate the next local day after each delivery. */
    fun schedule(context: Context) {
        createChannel(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)
        val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("reminderEnabled", false)) return
        // setAndAllowWhileIdle is inexact and needs no SCHEDULE_EXACT_ALARM permission.
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            nextTriggerMillis(ZonedDateTime.now(), prefs.getInt("reminderHour", 20).coerceIn(0, 23),
                prefs.getInt("reminderMinute", 0).coerceIn(0, 59)), pending)
    }

    internal fun nextTriggerMillis(now: ZonedDateTime, hour: Int, minute: Int): Long {
        val time = LocalTime.of(hour, minute)
        var next = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!next.isAfter(now)) next = now.toLocalDate().plusDays(1).atTime(time).atZone(now.zone)
        return next.toInstant().toEpochMilli()
    }

    fun showTest(context: Context): Boolean = notify(context)

    internal fun notify(context: Context, message: String? = null): Boolean {
        val content = ReminderContent.build(Progress(context.applicationContext), LocalDate.now())
        return showPreview(context, if (message == null) content else content.copy(message = message))
    }

    /** Also used by visual QA: the same artwork and Android template as a scheduled reminder. */
    fun showPreview(context: Context, reminder: ReminderMessage): Boolean {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setClassName(context.packageName, "${context.packageName}.MainActivity")
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        launch.putExtra("hamigo_route", "path")
        val content = PendingIntent.getActivity(context, REQUEST_CODE, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_radio)
            .setLargeIcon(ReminderArtwork.avatar(reminder))
            .setContentTitle(reminder.title)
            .setContentText(reminder.message)
            .setStyle(Notification.BigPictureStyle()
                .bigPicture(ReminderArtwork.render(reminder))
                .bigLargeIcon(null as Bitmap?)
                .setBigContentTitle(reminder.title)
                .setSummaryText(reminder.message))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setColor(reminder.accent)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setVisibility(Notification.VISIBILITY_PRIVATE)
            .setTimeoutAfter(20L * 60 * 60 * 1000)
            .build()
        return try { manager.notify(REQUEST_CODE, notification); true } catch (_: SecurityException) { false }
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            DailyReminder.ACTION_REMIND -> {
                DailyReminder.schedule(context)
                if (context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
                        .getBoolean("reminderEnabled", false)) DailyReminder.notify(context)
            }
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> DailyReminder.schedule(context)
        }
    }
}
