package com.malfreyt.alexandre.hamigo.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.widget.RemoteViews
import com.malfreyt.alexandre.hamigo.Progress
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

internal enum class HomeWidgetKind { STREAK, GOAL, WEEK }
internal data class WidgetDay(val date: LocalDate, val xp: Int)
internal data class WidgetSnapshot(val reminder: ReminderMessage, val week: List<WidgetDay>) {
    val weeklyXp get() = week.sumOf { it.xp }
    val activeDays get() = week.count { it.xp > 0 }
    companion object {
        fun read(context: Context, date: LocalDate = LocalDate.now()): WidgetSnapshot = synchronized(Progress.CLOUD_LOCK) {
            val progress = Progress(context.applicationContext)
            val monday = date.minusDays((date.dayOfWeek.value-1).toLong())
            WidgetSnapshot(ReminderContent.build(progress,date), (0L..6L).map { offset ->
                val day = monday.plusDays(offset)
                WidgetDay(day,if(day.isAfter(date))0 else progress.dayXp(day).coerceAtLeast(0))
            })
        }
    }
}

/** Native RemoteViews: no course bank, network, or Compose runtime is needed by the launcher. */
internal object HomeWidgets {
    private const val ACTION_DAY_CHANGED = "com.malfreyt.alexandre.hamigo.WIDGET_DAY_CHANGED"
    private const val REQUEST_CODE = 2801
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var pendingRefresh: ScheduledFuture<*>? = null
    private val providers = listOf(
        StreakWidgetProvider::class.java to HomeWidgetKind.STREAK,
        GoalWidgetProvider::class.java to HomeWidgetKind.GOAL,
        WeekWidgetProvider::class.java to HomeWidgetKind.WEEK,
    )

    /** Debounce successive answer/save events, keeping rendering off the question screen's UI thread. */
    @Synchronized fun progressChanged(context: Context) {
        val app = context.applicationContext
        pendingRefresh?.cancel(false)
        pendingRefresh = executor.schedule({ refresh(app) },600,TimeUnit.MILLISECONDS)
    }

    fun refresh(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val installed = providers.map { (provider,kind) -> kind to manager.getAppWidgetIds(ComponentName(context,provider)) }
        if(installed.any { it.second.isNotEmpty() }) {
            val snapshot = WidgetSnapshot.read(context)
            installed.forEach { (kind,ids) -> ids.forEach { id ->
                manager.updateAppWidget(id,responsiveViews(context,kind,snapshot,manager.getAppWidgetOptions(id),id))
            } }
        }
        scheduleMidnight(context,installed.any { it.second.isNotEmpty() })
    }

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray, kind: HomeWidgetKind) {
        val snapshot = WidgetSnapshot.read(context)
        ids.forEach { manager.updateAppWidget(it,responsiveViews(context,kind,snapshot,manager.getAppWidgetOptions(it),it)) }
        scheduleMidnight(context,true)
    }

    internal fun responsiveViews(context: Context, kind: HomeWidgetKind, snapshot: WidgetSnapshot, options: Bundle,
        appWidgetId: Int = AppWidgetManager.INVALID_APPWIDGET_ID): RemoteViews {
        val transparent = WidgetSettings.transparent(context,appWidgetId)
        val lightText = WidgetSettings.lightText(context,appWidgetId)
        val defaults = when (kind) {
            HomeWidgetKind.STREAK -> SizeF(130f,130f)
            HomeWidgetKind.GOAL -> SizeF(220f,150f)
            HomeWidgetKind.WEEK -> SizeF(280f,180f)
        }
        fun option(key: String, fallback: Float) = options.getInt(key,fallback.toInt()).toFloat().coerceAtLeast(40f)
        val portrait = SizeF(option(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,defaults.width), option(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,defaults.height))
        val landscape = SizeF(option(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,defaults.width), option(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,defaults.height))
        return if(Build.VERSION.SDK_INT>=31) {
            // Only real host sizes: a generic smaller template can otherwise win the host's matching
            // algorithm and stretch across a shape for which it was never composed.
            @Suppress("DEPRECATION")
            val hostSizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
            val sizes = hostSizes.filter { it.width.isFinite() && it.height.isFinite() && it.width>=40f && it.height>=40f }
                .ifEmpty { listOf(portrait,landscape) }.distinct().take(16)
            val metrics = context.resources.displayMetrics
            val perSizeBudget = minOf(520_000f, minOf(1_000_000f, metrics.widthPixels.toFloat() * metrics.heightPixels * .75f) / sizes.size)
            RemoteViews(sizes.associateWith { WidgetPresentation.views(context,kind,snapshot,it,perSizeBudget,transparent,lightText) })
        } else {
            val metrics = context.resources.displayMetrics
            val count = if (portrait == landscape) 1 else 2
            val perSizeBudget = minOf(520_000f, minOf(1_000_000f, metrics.widthPixels.toFloat() * metrics.heightPixels * .75f) / count)
            val portraitViews = WidgetPresentation.views(context,kind,snapshot,portrait,perSizeBudget,transparent,lightText)
            if (count == 1) portraitViews else RemoteViews(WidgetPresentation.views(context,kind,snapshot,landscape,perSizeBudget,transparent,lightText), portraitViews)
        }
    }

    internal fun views(context: Context, kind: HomeWidgetKind, snapshot: WidgetSnapshot, size: SizeF): RemoteViews =
        WidgetPresentation.views(context, kind, snapshot, size)
    private fun scheduleMidnight(context: Context, installed: Boolean) {
        val pending = PendingIntent.getBroadcast(context,REQUEST_CODE,
            Intent(context,StreakWidgetProvider::class.java).setAction(ACTION_DAY_CHANGED),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val manager = context.getSystemService(AlarmManager::class.java)
        manager.cancel(pending)
        if(installed) manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
            ZonedDateTime.now().toLocalDate().plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).plusMinutes(1).toInstant().toEpochMilli(),pending)
    }

    fun received(context: Context, intent: Intent) {
        if(intent.action==ACTION_DAY_CHANGED)refresh(context)
    }
}

abstract class HamigoWidgetProvider : AppWidgetProvider() {
    internal abstract val kind: HomeWidgetKind
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) = HomeWidgets.update(context,appWidgetManager,appWidgetIds,kind)
    override fun onAppWidgetOptionsChanged(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, newOptions: Bundle) =
        HomeWidgets.update(context,appWidgetManager,intArrayOf(appWidgetId),kind)
    override fun onDisabled(context: Context) = HomeWidgets.refresh(context)
    override fun onDeleted(context: Context, appWidgetIds: IntArray) = WidgetSettings.delete(context,appWidgetIds)
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context,intent)
        HomeWidgets.received(context,intent)
    }
}
class StreakWidgetProvider : HamigoWidgetProvider() { override val kind = HomeWidgetKind.STREAK }
class GoalWidgetProvider : HamigoWidgetProvider() { override val kind = HomeWidgetKind.GOAL }
class WeekWidgetProvider : HamigoWidgetProvider() { override val kind = HomeWidgetKind.WEEK }
