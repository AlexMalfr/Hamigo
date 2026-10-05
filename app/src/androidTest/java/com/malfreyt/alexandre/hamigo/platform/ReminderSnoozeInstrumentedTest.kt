package com.malfreyt.alexandre.hamigo.platform

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.After
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class ReminderSnoozeInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val prefs get()=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE)
    private val manager get()=context.getSystemService(NotificationManager::class.java)
    private var saved=emptyMap<String,Any?>()

    @Before fun prepare() {
        saved=prefs.all.mapValues { (_,value) -> if(value is Set<*>)value.toSet() else value }
        prefs.edit().clear().putBoolean("reminderEnabled",true).putBoolean("autoSync",false).putInt("dailyGoal",30).commit()
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName,Manifest.permission.POST_NOTIFICATIONS)
        DailyReminder.cancelSnooze(context)
    }
    @After fun restore() {
        DailyReminder.cancelSnooze(context);manager.cancel(2701)
        val edit=prefs.edit().clear()
        saved.forEach { (key,value) -> when(value) {
            is String -> edit.putString(key,value)
            is Int -> edit.putInt(key,value)
            is Long -> edit.putLong(key,value)
            is Boolean -> edit.putBoolean(key,value)
            is Float -> edit.putFloat(key,value)
            is Set<*> -> edit.putStringSet(key,value.filterIsInstance<String>().toSet())
        } }
        edit.commit();DailyReminder.schedule(context)
    }

    @Test fun notificationActionDismissesAndSchedulesThirtyMinutesWithoutChangingTheDailyTime() {
        assertTrue(DailyReminder.showTest(context))
        val notification=manager.activeNotifications.single { it.id==2701 }.notification
        val action=notification.actions.single()
        assertEquals("Me rappeler plus tard",action.title.toString())
        action.actionIntent.send()
        val deadline=System.currentTimeMillis()+5000L
        while((!prefs.contains(DailyReminder.SNOOZE_AT) || manager.activeNotifications.any {it.id==2701}) && System.currentTimeMillis()<deadline)Thread.sleep(20)
        assertTrue(prefs.contains(DailyReminder.SNOOZE_AT))
        val remaining=prefs.getLong(DailyReminder.SNOOZE_AT,0)-System.currentTimeMillis()
        assertTrue(remaining in 1_795_000L..1_800_000L)
        assertEquals(LocalDate.now().toString(),prefs.getString(DailyReminder.SNOOZE_DAY,null))
        assertEquals(20,prefs.getInt("reminderHour",20))
        assertFalse(manager.activeNotifications.any {it.id==2701})
    }

    @Test fun postponedDeliveryReadsFreshProgressAndIsConsumedOnlyOnce() {
        snooze()
        assertFalse("An early callback must not fire",DailyReminder.deliverSnooze(context))
        val today=LocalDate.now().toString()
        prefs.edit().putString("progress",JSONObject().put("dailyXp",JSONObject().put(today,12)).toString())
            .putLong(DailyReminder.SNOOZE_AT,System.currentTimeMillis()-1).commit()
        assertTrue(DailyReminder.deliverSnooze(context))
        assertFalse(prefs.contains(DailyReminder.SNOOZE_AT))
        assertFalse(DailyReminder.deliverSnooze(context))
        val notification=manager.activeNotifications.single {it.id==2701}.notification
        assertTrue(notification.extras.getCharSequence("android.text").toString().contains("12") ||
            notification.extras.getCharSequence("android.text").toString().contains("18"))
    }

    @Test fun goalCompletionAndDisablingRemindersCancelThePostponedAlarm() {
        snooze()
        val progress=com.malfreyt.alexandre.hamigo.Progress(context)
        progress.setDailyGoal(3)
        progress.answer("widget-snooze-fixture",true)
        assertFalse(prefs.contains(DailyReminder.SNOOZE_AT))
        progress.setDailyGoal(30)
        snooze()
        DailyReminder.configure(context,false)
        assertFalse(prefs.contains(DailyReminder.SNOOZE_AT))
        assertFalse(DailyReminder.deliverSnooze(context))
    }

    @Test fun staleNotificationAndExpiredReportCannotCreateOrDeliverAnOldReminder() {
        var accepted=true
        instrumentation.runOnMainSync { accepted=DailyReminder.snooze(context,LocalDate.now().minusDays(1).toString()) }
        assertFalse(accepted)
        snooze()
        prefs.edit().putString(DailyReminder.SNOOZE_DAY,LocalDate.now().minusDays(1).toString())
            .putLong(DailyReminder.SNOOZE_AT,System.currentTimeMillis()-7*60*60_000L).commit()
        assertFalse(DailyReminder.deliverSnooze(context))
        assertFalse(prefs.contains(DailyReminder.SNOOZE_AT))
    }

    @Test fun reportCrossingMidnightStillDeliversWithTodaysContext() {
        snooze()
        prefs.edit().putString(DailyReminder.SNOOZE_DAY,LocalDate.now().minusDays(1).toString())
            .putLong(DailyReminder.SNOOZE_AT,System.currentTimeMillis()-1).commit()
        DailyReminder.restoreSnooze(context)
        assertTrue(prefs.contains(DailyReminder.SNOOZE_AT))
        assertTrue(DailyReminder.deliverSnooze(context))
        assertFalse(prefs.contains(DailyReminder.SNOOZE_AT))
    }

    @Test fun rebootRestoresOnePendingReportAndTheDailyCallbackReplacesIt() {
        snooze()
        val at=prefs.getLong(DailyReminder.SNOOZE_AT,0)
        DailyReminder.restoreSnooze(context)
        assertEquals(at,prefs.getLong(DailyReminder.SNOOZE_AT,0))
        instrumentation.runOnMainSync { ReminderReceiver().onReceive(context,Intent(DailyReminder.ACTION_REMIND)) }
        assertFalse(prefs.contains(DailyReminder.SNOOZE_AT))
        assertTrue(manager.activeNotifications.any {it.id==2701})
    }

    private fun snooze() {
        var accepted=false
        instrumentation.runOnMainSync { accepted=DailyReminder.snooze(context,LocalDate.now().toString()) }
        assertTrue(accepted)
    }
}
