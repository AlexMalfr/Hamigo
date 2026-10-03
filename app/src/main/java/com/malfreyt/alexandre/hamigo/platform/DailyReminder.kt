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
import android.os.Build
import java.time.LocalTime
import java.time.ZonedDateTime

/** A battery-friendly daily reminder. Delivery can be delayed by Android's power management. */
object DailyReminder {
    const val CHANNEL_ID = "daily_practice"
    const val ACTION_REMIND = "com.malfreyt.alexandre.hamigo.REMIND"
    private const val REQUEST_CODE = 2701

    fun configure(context: Context, enabled: Boolean, hour: Int = 20, minute: Int = 0) {
        require(hour in 0..23 && minute in 0..59)
        context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit()
            .putBoolean("reminderEnabled", enabled)
            .putInt("reminderHour", hour)
            .putInt("reminderMinute", minute)
            .apply()
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

    fun showTest(context: Context): Boolean = notify(context, "On se remet sur la bonne fréquence ?")

    internal fun notify(context: Context, message: String = "5 minutes, quelques XP et une série qui continue !"): Boolean {
        createChannel(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        if (!manager.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT >= 33 &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
            ?: Intent().setClassName(context.packageName, "${context.packageName}.MainActivity")
        launch.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val content = PendingIntent.getActivity(context, REQUEST_CODE, launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Hamigo t'attend 📻")
            .setContentText(message)
            .setStyle(Notification.BigTextStyle().bigText(message))
            .setContentIntent(content)
            .setAutoCancel(true)
            .setColor(0xff14796b.toInt())
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
