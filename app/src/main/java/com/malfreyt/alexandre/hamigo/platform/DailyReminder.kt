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
import android.os.SystemClock
import android.widget.Toast
import com.malfreyt.alexandre.hamigo.Progress
import com.malfreyt.alexandre.hamigo.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZonedDateTime

/** A battery-friendly daily reminder. Delivery can be delayed by Android's power management. */
object DailyReminder {
    const val CHANNEL_ID = "daily_practice"
    const val ACTION_REMIND = "com.malfreyt.alexandre.hamigo.REMIND"
    const val ACTION_SNOOZE = "com.malfreyt.alexandre.hamigo.SNOOZE"
    const val ACTION_SNOOZED_REMIND = "com.malfreyt.alexandre.hamigo.SNOOZED_REMIND"
    const val SNOOZE_MINUTES = 30
    private const val REQUEST_CODE = 2701
    private const val SNOOZE_REQUEST_CODE = 2702
    internal const val SNOOZE_AT = "reminderSnoozeAt"
    internal const val SNOOZE_DAY = "reminderSnoozeDay"

    fun configure(context: Context, enabled: Boolean, hour: Int = 20, minute: Int = 0) {
        if(context.applicationContext is com.malfreyt.alexandre.hamigo.DiagnosticContext)return
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
        if(context.applicationContext is com.malfreyt.alexandre.hamigo.DiagnosticContext)return
        createChannel(context)
        val manager = context.getSystemService(AlarmManager::class.java)
        val pending = PendingIntent.getBroadcast(
            context, REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_REMIND),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        manager.cancel(pending)
        val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("reminderEnabled", false)) {
            cancelSnooze(context)
            context.getSystemService(NotificationManager::class.java).cancel(REQUEST_CODE)
            return
        }
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

    private fun snoozedIntent(context: Context) = PendingIntent.getBroadcast(context, SNOOZE_REQUEST_CODE,
        Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZED_REMIND),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    internal fun cancelSnooze(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(snoozedIntent(context))
        context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit().remove(SNOOZE_AT).remove(SNOOZE_DAY).apply()
    }

    internal fun snooze(context: Context, notificationDay: String): Boolean {
        val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
        val current = ReminderContent.build(Progress(context.applicationContext))
        context.getSystemService(NotificationManager::class.java).cancel(REQUEST_CODE)
        val toast = when {
            !prefs.getBoolean("reminderEnabled", false) -> "Les rappels sont désactivés."
            notificationDay != current.date.toString() -> "Ce rappel a expiré."
            current.context == ReminderContext.GOAL_REACHED -> "Objectif atteint : pas besoin d’un autre rappel !"
            else -> null
        }
        if (toast != null) {
            cancelSnooze(context)
            Toast.makeText(context, toast, Toast.LENGTH_SHORT).show()
            return false
        }
        val delay = SNOOZE_MINUTES * 60_000L
        // This local, temporary choice is intentionally absent from the cloud backup.
        prefs.edit().putLong(SNOOZE_AT, System.currentTimeMillis()+delay).putString(SNOOZE_DAY, notificationDay).apply()
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime()+delay, snoozedIntent(context))
        Toast.makeText(context, "Rappel reporté de $SNOOZE_MINUTES minutes.", Toast.LENGTH_SHORT).show()
        return true
    }

    /** Recreate after reboot, including a 23:50 postponement that falls after midnight. */
    internal fun restoreSnooze(context: Context) {
        val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
        val at = prefs.getLong(SNOOZE_AT, 0L)
        if (at == 0L) return
        if (!prefs.getBoolean("reminderEnabled", false) || System.currentTimeMillis()-at>6*60*60_000L ||
            ReminderContent.build(Progress(context.applicationContext)).context == ReminderContext.GOAL_REACHED) {
            cancelSnooze(context); return
        }
        context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime()+(at-System.currentTimeMillis()).coerceAtLeast(1000L), snoozedIntent(context))
    }

    internal fun deliverSnooze(context: Context): Boolean {
        val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
        val at = prefs.getLong(SNOOZE_AT, 0L)
        if (at == 0L || at > System.currentTimeMillis()+1000L) return false
        val shouldNotify = prefs.getBoolean("reminderEnabled", false) &&
            System.currentTimeMillis()-at<=6*60*60_000L &&
            ReminderContent.build(Progress(context.applicationContext)).context != ReminderContext.GOAL_REACHED
        cancelSnooze(context)
        return shouldNotify && notify(context)
    }

    internal fun progressChanged(context: Context) {
        val prefs = context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
        if (prefs.contains(SNOOZE_AT) && Progress(context.applicationContext).let { it.todayXp >= it.dailyGoal }) cancelSnooze(context)
    }

    internal fun notify(context: Context, message: String? = null): Boolean {
        val content = ReminderContent.build(Progress(context.applicationContext), LocalDate.now())
        return showPreview(context, if (message == null) content else content.copy(message = message))
    }

    /** Also used by visual QA: the same artwork and Android template as a scheduled reminder. */
    fun showPreview(context: Context, reminder: ReminderMessage): Boolean {
        if(context.applicationContext is com.malfreyt.alexandre.hamigo.DiagnosticContext)return false
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
        val later = PendingIntent.getBroadcast(context, REQUEST_CODE,
            Intent(context, ReminderReceiver::class.java).setAction(ACTION_SNOOZE).putExtra("day", reminder.date.toString()),
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
            .addAction(Notification.Action.Builder(null, "Me rappeler plus tard", later).build())
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
                // A daily reminder replaces an older postponed reminder rather than doubling it.
                DailyReminder.cancelSnooze(context)
                if (context.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
                        .getBoolean("reminderEnabled", false)) DailyReminder.notify(context)
            }
            DailyReminder.ACTION_SNOOZE -> DailyReminder.snooze(context, intent.getStringExtra("day").orEmpty())
            DailyReminder.ACTION_SNOOZED_REMIND -> DailyReminder.deliverSnooze(context)
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                DailyReminder.schedule(context)
                DailyReminder.restoreSnooze(context)
                HomeWidgets.refresh(context)
            }
        }
    }
}
