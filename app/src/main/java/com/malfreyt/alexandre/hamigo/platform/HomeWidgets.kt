package com.malfreyt.alexandre.hamigo.platform

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.malfreyt.alexandre.hamigo.PicoRenderer
import com.malfreyt.alexandre.hamigo.Progress
import com.malfreyt.alexandre.hamigo.R
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
                manager.updateAppWidget(id,responsiveViews(context,kind,snapshot,manager.getAppWidgetOptions(id)))
            } }
        }
        scheduleMidnight(context,installed.any { it.second.isNotEmpty() })
    }

    fun update(context: Context, manager: AppWidgetManager, ids: IntArray, kind: HomeWidgetKind) {
        val snapshot = WidgetSnapshot.read(context)
        ids.forEach { manager.updateAppWidget(it,responsiveViews(context,kind,snapshot,manager.getAppWidgetOptions(it))) }
        scheduleMidnight(context,true)
    }

    internal fun responsiveViews(context: Context, kind: HomeWidgetKind, snapshot: WidgetSnapshot, options: Bundle): RemoteViews {
        return if(Build.VERSION.SDK_INT>=31) {
            RemoteViews(listOf(SizeF(120f,110f),SizeF(120f,220f),SizeF(220f,170f),SizeF(320f,220f))
                .associateWith { views(context,kind,snapshot,it) })
        } else {
            views(context,kind,snapshot,SizeF(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,130).toFloat(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,130).toFloat()))
        }
    }

    internal fun views(context: Context, kind: HomeWidgetKind, snapshot: WidgetSnapshot, size: SizeF): RemoteViews {
        val expanded = size.height>=220f || size.width>=220f && size.height>=170f
        val r = snapshot.reminder
        val title = when(kind) { HomeWidgetKind.STREAK -> "SÉRIE"; HomeWidgetKind.GOAL -> "OBJECTIF DU JOUR"; HomeWidgetKind.WEEK -> "CETTE SEMAINE" }
        val metric = when(kind) {
            HomeWidgetKind.STREAK -> "${r.streak} ${if(r.streak<=1)"jour" else "jours"}"
            HomeWidgetKind.GOAL -> "${r.todayXp} / ${r.goal} XP"
            HomeWidgetKind.WEEK -> "${snapshot.weeklyXp} XP"
        }
        val status = when(kind) {
            HomeWidgetKind.STREAK -> when(r.context) {
                ReminderContext.START -> "Une petite leçon pour commencer"
                ReminderContext.RESTART -> "Une nouvelle série commence ici"
                ReminderContext.CONTINUE -> "Une révision aujourd’hui la prolonge"
                else -> "Ta série continue aujourd’hui !"
            }
            HomeWidgetKind.GOAL -> if(r.todayXp>=r.goal)"Objectif atteint !" else "Encore ${r.goal-r.todayXp} XP aujourd’hui"
            HomeWidgetKind.WEEK -> "${snapshot.activeDays} ${if(snapshot.activeDays==1)"jour actif" else "jours actifs"} · objectif ${r.goal} XP/j"
        }
        val layout = if(expanded) R.layout.widget_expanded else R.layout.widget_compact
        return RemoteViews(context.packageName,layout).apply {
            setInt(R.id.widget_root,"setBackgroundResource",when(kind) {
                HomeWidgetKind.STREAK -> R.drawable.widget_streak_background
                HomeWidgetKind.GOAL -> R.drawable.widget_goal_background
                HomeWidgetKind.WEEK -> R.drawable.widget_week_background
            })
            val compactTitle=when(kind) { HomeWidgetKind.STREAK -> "SÉRIE"; HomeWidgetKind.GOAL -> "OBJECTIF"; HomeWidgetKind.WEEK -> "SEMAINE" }
            setTextViewText(R.id.widget_title,if(size.width<180f)compactTitle else "HAMIGO · $title")
            setTextViewText(R.id.widget_metric,metric)
            setTextViewText(R.id.widget_status,status)
            setViewVisibility(R.id.widget_status,if(!expanded && size.height<130f)View.GONE else View.VISIBLE)
            setProgressBar(R.id.widget_progress,r.goal,r.todayXp.coerceAtMost(r.goal),false)
            setContentDescription(R.id.widget_progress,"${r.todayXp} XP sur un objectif de ${r.goal} XP aujourd’hui")
            setViewVisibility(R.id.widget_progress,if(kind==HomeWidgetKind.WEEK)View.GONE else View.VISIBLE)
            setImageViewBitmap(R.id.widget_mascot,ReminderArtwork.avatar(r))
            // On a narrow, tall widget, let the metric occupy the full width; the artwork includes Pico.
            setViewVisibility(R.id.widget_mascot,if(expanded && size.width<220f || kind==HomeWidgetKind.GOAL && size.width<180f)View.GONE else View.VISIBLE)
            if(expanded) {
                setImageViewBitmap(R.id.widget_art,WidgetArtwork.render(kind,snapshot,size.width<220f))
                setContentDescription(R.id.widget_art,if(kind==HomeWidgetKind.GOAL)"${(100L*r.todayXp/r.goal).coerceAtMost(100)} % de l’objectif" else
                    snapshot.week.joinToString(" ; ") { "${it.date} : ${it.xp} XP" })
            }
            val launch = Intent(context,com.malfreyt.alexandre.hamigo.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("hamigo_route","path")
            val action = PendingIntent.getActivity(context,2802,launch,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.widget_root,action)
            setOnClickPendingIntent(R.id.widget_review,action)
            setContentDescription(R.id.widget_root,"Hamigo, $title. $metric. $status. Ouvrir le parcours.")
        }
    }

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
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context,intent)
        HomeWidgets.received(context,intent)
    }
}
class StreakWidgetProvider : HamigoWidgetProvider() { override val kind = HomeWidgetKind.STREAK }
class GoalWidgetProvider : HamigoWidgetProvider() { override val kind = HomeWidgetKind.GOAL }
class WeekWidgetProvider : HamigoWidgetProvider() { override val kind = HomeWidgetKind.WEEK }

internal object WidgetArtwork {
    private val teal = Color.rgb(8,127,130)
    private val coral = Color.rgb(224,130,71)
    fun render(kind: HomeWidgetKind, snapshot: WidgetSnapshot, narrow: Boolean = false): Bitmap {
        val r = snapshot.reminder
        val bitmap = Bitmap.createBitmap(if(narrow)280 else 640,if(narrow)240 else 190,Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val w = bitmap.width.toFloat(); val h = bitmap.height.toFloat()
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        if(kind==HomeWidgetKind.GOAL) {
            val radius = h*.36f; val centerX = w/2; val centerY=h/2
            paint.style=Paint.Style.STROKE; paint.strokeWidth=12f; paint.color=Color.argb(100,255,255,255)
            canvas.drawCircle(centerX,centerY,radius,paint)
            paint.color=teal; paint.strokeCap=Paint.Cap.ROUND
            canvas.drawArc(RectF(centerX-radius,centerY-radius,centerX+radius,centerY+radius),-90f,
                360f*(r.todayXp.toFloat()/r.goal).coerceIn(0f,1f),false,paint)
            PicoRenderer.draw(canvas,RectF(centerX-radius*.8f,centerY-radius*.8f,centerX+radius*.8f,centerY+radius*.8f),r.mood,r.pose,.3f)
        } else if(kind==HomeWidgetKind.STREAK) {
            val dayWidth=w/7; val y=if(narrow)h*.68f else h*.40f
            if(narrow)PicoRenderer.draw(canvas,RectF(w/2-55f,0f,w/2+55f,110f),r.mood,r.pose,.3f)
            snapshot.week.forEachIndexed { i,day ->
                val x=dayWidth*(i+.5f); val radius=dayWidth*.34f
                paint.color=Color.argb(150,255,255,255); canvas.drawCircle(x,y,radius,paint)
                val ratio=(day.xp.toFloat()/r.goal).coerceIn(0f,1f)
                if(ratio>0) { paint.color=teal; canvas.drawCircle(x,y,radius*ratio,paint) }
                if(day.date==r.date) {
                    paint.style=Paint.Style.STROKE; paint.strokeWidth=3f; paint.color=coral
                    canvas.drawCircle(x,y,radius+4f,paint); paint.style=Paint.Style.FILL
                }
                label(canvas,paint,i,x,y+radius+27f,if(narrow)16f else 22f)
            }
        } else {
            val chartTop=12f; val chartBottom=h-32f; val chartHeight=chartBottom-chartTop
            val maximum=maxOf(r.goal,snapshot.week.maxOf {it.xp},1)*1.15f
            val dayWidth=w/7
            val goalY=chartBottom-chartHeight*r.goal/maximum
            paint.color=coral; paint.style=Paint.Style.STROKE; paint.strokeWidth=2f; paint.pathEffect=DashPathEffect(floatArrayOf(7f,7f),0f)
            canvas.drawLine(0f,goalY,w,goalY,paint); paint.pathEffect=null; paint.style=Paint.Style.FILL
            snapshot.week.forEachIndexed { i,day ->
                val x=dayWidth*(i+.5f); val barWidth=dayWidth*.54f
                paint.color=if(day.date.isAfter(r.date))Color.argb(25,8,127,130) else Color.argb(50,8,127,130)
                canvas.drawRoundRect(RectF(x-barWidth/2,chartTop,x+barWidth/2,chartBottom),8f,8f,paint)
                if(day.xp>0) {
                    paint.color=if(day.date==r.date)coral else teal
                    canvas.drawRoundRect(RectF(x-barWidth/2,chartBottom-chartHeight*day.xp/maximum,x+barWidth/2,chartBottom),8f,8f,paint)
                }
                label(canvas,paint,i,x,h-6f,if(narrow)16f else 22f)
            }
        }
        return bitmap
    }
    private fun label(canvas: Canvas, paint: Paint, day: Int, x: Float, y: Float, size: Float) {
        paint.color=Color.rgb(69,103,106);paint.textSize=size;paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
        canvas.drawText(listOf("L","Ma","Me","J","V","S","D")[day],x,y,paint)
    }
}
