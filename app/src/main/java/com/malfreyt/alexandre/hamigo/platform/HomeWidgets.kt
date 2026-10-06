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
            // Exact launcher sizes avoid a stretched 2x2 composition on a wide 4x1 or tall 2x4 tile.
            @Suppress("DEPRECATION")
            val hostSizes = options.getParcelableArrayList<SizeF>(AppWidgetManager.OPTION_APPWIDGET_SIZES).orEmpty()
            val sizes = (hostSizes.filter { it.width>=40f && it.height>=40f }.take(12) + listOf(
                SizeF(40f,40f), SizeF(60f,60f), SizeF(100f,60f),
                SizeF(120f,110f), SizeF(120f,180f), SizeF(220f,110f), SizeF(220f,170f),
                SizeF(320f,110f), SizeF(320f,220f), SizeF(420f,300f)
            )).distinct().take(16)
            RemoteViews(sizes.associateWith { views(context,kind,snapshot,it) })
        } else {
            views(context,kind,snapshot,SizeF(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,130).toFloat(),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,130).toFloat()))
        }
    }

    internal fun views(context: Context, kind: HomeWidgetKind, snapshot: WidgetSnapshot, size: SizeF): RemoteViews {
        val format = WidgetFormat.forSize(size)
        val r = snapshot.reminder
        val title = when(kind) { HomeWidgetKind.STREAK -> "SÉRIE"; HomeWidgetKind.GOAL -> "OBJECTIF"; HomeWidgetKind.WEEK -> if(size.width>=200f)"CETTE SEMAINE" else "SEMAINE" }
        val miniature = format==WidgetFormat.MINI
        val metric = when(kind) {
            HomeWidgetKind.STREAK -> "${r.streak} ${if(r.streak<=1)"jour" else "jours"}"
            HomeWidgetKind.GOAL -> "${r.todayXp} XP"
            HomeWidgetKind.WEEK -> "${snapshot.weeklyXp} XP"
        }
        val status = when(kind) {
            HomeWidgetKind.STREAK -> when {
                r.todayXp>0 -> "Ta série continue !"
                r.streak>0 -> "À prolonger aujourd’hui"
                r.context==ReminderContext.RESTART -> "Un nouveau départ"
                else -> "Une leçon pour démarrer"
            }
            HomeWidgetKind.GOAL -> if(r.todayXp>=r.goal)"Objectif atteint !" else "Encore ${r.goal-r.todayXp} XP"
            HomeWidgetKind.WEEK -> "${snapshot.activeDays} ${if(snapshot.activeDays==1)"jour actif" else "jours actifs"}"
        }
        val dark = kind==HomeWidgetKind.WEEK
        val accent = if(kind==HomeWidgetKind.STREAK) Color.rgb(163,87,31) else Color.rgb(8,127,130)
        val ink = if(dark)Color.WHITE else Color.rgb(7,61,64)
        val secondary = if(dark)Color.rgb(190,221,217) else Color.rgb(69,103,106)
        val density = context.resources.displayMetrics.density
        fun dp(value: Float)=(value*density).toInt()
        val padding = when(format) { WidgetFormat.MINI -> if(minOf(size.width,size.height)<60f)4f else 6f; WidgetFormat.COMPACT -> 10f; WidgetFormat.TALL, WidgetFormat.WIDE -> 12f; else -> if(size.height<200f)12f else 16f }
        val artworkWidth = if(format==WidgetFormat.WIDE)((size.width-2*padding-8f)*.40f).coerceAtLeast(48f) else size.width-2*padding
        val artworkHeight = when(format) {
            WidgetFormat.MINI -> 0f
            WidgetFormat.COMPACT -> if(kind==HomeWidgetKind.GOAL)0f else (size.height-91f).coerceAtLeast(10f)
            WidgetFormat.WIDE -> size.height-2*padding
            WidgetFormat.TALL -> (size.height-144f).coerceAtLeast(26f)
            WidgetFormat.EXPANDED -> (size.height-2*padding-112f).coerceAtLeast(24f)
        }
        val layout = when(format) {
            WidgetFormat.MINI -> R.layout.widget_mini
            WidgetFormat.COMPACT -> R.layout.widget_compact
            WidgetFormat.WIDE -> R.layout.widget_wide
            WidgetFormat.TALL -> R.layout.widget_tall
            WidgetFormat.EXPANDED -> R.layout.widget_expanded
        }
        return RemoteViews(context.packageName,layout).apply {
            setInt(R.id.widget_root,"setBackgroundResource",when(kind) {
                HomeWidgetKind.STREAK -> R.drawable.widget_streak_background
                HomeWidgetKind.GOAL -> R.drawable.widget_goal_background
                HomeWidgetKind.WEEK -> R.drawable.widget_week_background
            })
            setViewPadding(R.id.widget_root,dp(padding),dp(padding),dp(padding),dp(padding))
            setTextViewText(R.id.widget_title,if(miniature)when(kind) {
                HomeWidgetKind.STREAK -> "SÉRIE"; HomeWidgetKind.GOAL -> "XP / J"; HomeWidgetKind.WEEK -> "XP / SEM."
            } else title)
            setTextColor(R.id.widget_title,if(dark)Color.rgb(124,213,199) else accent)
            val tinyMetric = when(kind) {
                HomeWidgetKind.STREAK -> "${shortMetric(r.streak)} j"
                HomeWidgetKind.GOAL -> shortMetric(r.todayXp)
                HomeWidgetKind.WEEK -> shortMetric(snapshot.weeklyXp)
            }
            setTextViewText(R.id.widget_metric,if(miniature)tinyMetric else metric)
            setTextColor(R.id.widget_metric,ink)
            setTextViewText(R.id.widget_status,if(format==WidgetFormat.COMPACT && kind==HomeWidgetKind.GOAL)"/ ${r.goal} XP" else status)
            setTextColor(R.id.widget_status,secondary)
            setViewVisibility(R.id.widget_status,if(miniature || format==WidgetFormat.COMPACT && kind!=HomeWidgetKind.GOAL || format==WidgetFormat.WIDE && size.height<135f)View.GONE else View.VISIBLE)
            setTextViewText(R.id.widget_badge,if(kind==HomeWidgetKind.STREAK)"Aujourd’hui" else "${r.goal} XP/j")
            setViewVisibility(R.id.widget_badge,if(format==WidgetFormat.EXPANDED && size.width>=280f)View.VISIBLE else View.GONE)
            setTextColor(R.id.widget_badge,Color.rgb(7,61,64))
            // The large goal has one ring. Only its tiny version needs a linear gauge.
            setProgressBar(R.id.widget_progress,r.goal,r.todayXp.coerceAtMost(r.goal),false)
            setViewVisibility(R.id.widget_progress,if(kind==HomeWidgetKind.GOAL && (miniature || format==WidgetFormat.COMPACT))View.VISIBLE else View.GONE)
            setViewVisibility(R.id.widget_review,if(miniature)View.GONE else View.VISIBLE)
            setContentDescription(R.id.widget_progress,"${r.todayXp} XP sur un objectif de ${r.goal} XP aujourd’hui")
            // On a short card Pico sits by the metric; taller cards give it its own illustrated area.
            val mascotVisible=kind==HomeWidgetKind.STREAK && format==WidgetFormat.EXPANDED && artworkHeight<75f
            setViewVisibility(R.id.widget_mascot,if(mascotVisible)View.VISIBLE else View.GONE)
            if(mascotVisible)setImageViewBitmap(R.id.widget_mascot,WidgetArtwork.mascot(r))
            setViewVisibility(R.id.widget_art,if(artworkHeight>0)View.VISIBLE else View.GONE)
            if(artworkHeight>0) {
                setImageViewBitmap(R.id.widget_art,WidgetArtwork.render(kind,snapshot,artworkWidth,artworkHeight,format))
                setContentDescription(R.id.widget_art,if(kind==HomeWidgetKind.GOAL)"${(100L*r.todayXp/r.goal).coerceAtMost(100)} % de l’objectif" else
                    snapshot.week.joinToString(" ; ") { "${it.date} : ${it.xp} XP" })
            }
            setTextColor(R.id.widget_review,if(kind==HomeWidgetKind.STREAK)accent else Color.rgb(8,127,130))
            val launch = Intent(context,com.malfreyt.alexandre.hamigo.MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("hamigo_route","path")
            val action = PendingIntent.getActivity(context,2802,launch,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.widget_root,action)
            setOnClickPendingIntent(R.id.widget_review,action)
            setContentDescription(R.id.widget_root,"Hamigo, $title. $metric. $status. Objectif quotidien : ${r.goal} XP. Ouvrir le parcours.")
        }
    }
    private fun shortMetric(value: Int): String = when {
        value>=1_000_000 -> "${value/1_000_000}M"
        value>=10_000 -> "${value/1000}k"
        else -> value.toString()
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

/** Native compositions selected for the launcher's actual available space. */
internal enum class WidgetFormat {
    MINI, COMPACT, WIDE, TALL, EXPANDED;
    companion object {
        fun forSize(size: SizeF): WidgetFormat = when {
            size.width<120f || size.height<110f -> MINI
            size.width>=220f && size.height<160f -> WIDE
            size.width<200f && size.height>=170f -> TALL
            size.width>=200f && size.height>=160f -> EXPANDED
            else -> COMPACT
        }
    }
}

internal object WidgetArtwork {
    private val teal = Color.rgb(8,127,130)
    private val warm = Color.rgb(192,111,48)
    private val coral = Color.rgb(255,186,111)
    private val pale = Color.rgb(102,214,195)

    fun mascot(r: ReminderMessage): Bitmap {
        val bitmap=Bitmap.createBitmap(192,192,Bitmap.Config.ARGB_8888)
        PicoRenderer.draw(Canvas(bitmap),RectF(0f,0f,192f,192f),r.mood,r.pose,.3f)
        return bitmap
    }

    fun render(kind: HomeWidgetKind, snapshot: WidgetSnapshot, width: Float, height: Float, format: WidgetFormat): Bitmap {
        // Bound the IPC payload even on a tablet. Draw in dp at 2x for crisp curves and day labels.
        val scale=minOf(2f,760f/width.coerceAtLeast(1f),640f/height.coerceAtLeast(1f))
        val bitmap=Bitmap.createBitmap((width*scale).toInt().coerceAtLeast(1),(height*scale).toInt().coerceAtLeast(1),Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap).apply { scale(scale,scale) }
        val paint=Paint(Paint.ANTI_ALIAS_FLAG)
        val r=snapshot.reminder
        val w=width; val h=height
        when(kind) {
            HomeWidgetKind.GOAL -> {
                val radius=minOf(w,h)*.43f
                val x=w/2; val y=h/2
                val thickness=(radius*.095f).coerceIn(3f,8f)
                paint.style=Paint.Style.STROKE;paint.strokeWidth=thickness;paint.strokeCap=Paint.Cap.ROUND
                paint.color=Color.argb(38,8,127,130)
                canvas.drawCircle(x,y,radius,paint)
                paint.color=teal
                canvas.drawArc(RectF(x-radius,y-radius,x+radius,y+radius),-90f,
                    360f*(r.todayXp.toFloat()/r.goal).coerceIn(0f,1f),false,paint)
                PicoRenderer.draw(canvas,RectF(x-radius*.79f,y-radius*.79f,x+radius*.79f,y+radius*.79f),r.mood,r.pose,.3f)
                if(r.todayXp>=r.goal && radius>=28f) {
                    val bx=x+radius*.76f;val by=y+radius*.67f
                    paint.style=Paint.Style.FILL;paint.color=teal
                    canvas.drawCircle(bx,by,radius*.22f,paint)
                    paint.style=Paint.Style.STROKE;paint.strokeWidth=2.3f;paint.color=Color.WHITE
                    canvas.drawLine(bx-radius*.09f,by,bx-radius*.025f,by+radius*.07f,paint)
                    canvas.drawLine(bx-radius*.025f,by+radius*.07f,bx+radius*.10f,by-radius*.08f,paint)
                }
            }
            HomeWidgetKind.STREAK -> {
                val mascotSpace=when {
                    format==WidgetFormat.WIDE -> h*.72f
                    format==WidgetFormat.TALL && h>=75f -> h*.65f
                    format==WidgetFormat.COMPACT && h>=75f -> h*.65f
                    format==WidgetFormat.EXPANDED && h>=75f -> h*.65f
                    else -> 0f
                }
                if(mascotSpace>0f) {
                    val side=minOf(w*.85f,mascotSpace)
                    PicoRenderer.draw(canvas,RectF(w/2-side/2,0f,w/2+side/2,side),r.mood,r.pose,.3f)
                }
                drawDays(canvas,paint,snapshot,w,h,mascotSpace)
            }
            HomeWidgetKind.WEEK -> drawWeek(canvas,paint,snapshot,w,h)
        }
        return bitmap
    }

    private fun drawDays(canvas: Canvas, paint: Paint, snapshot: WidgetSnapshot, width: Float, height: Float, top: Float) {
        val r=snapshot.reminder
        val area=height-top
        val labels=area>=27f
        val spacing=width/7f
        val radius=minOf(spacing*.31f,(area-if(labels)14f else 2f)*.40f).coerceAtLeast(2f)
        val y=top+(area-if(labels)13f else 0f)/2
        paint.style=Paint.Style.STROKE;paint.strokeWidth=1.4f;paint.color=Color.argb(30,192,111,48)
        canvas.drawLine(spacing*.5f,y,width-spacing*.5f,y,paint)
        snapshot.week.forEachIndexed { i,day ->
            val x=spacing*(i+.5f)
            paint.style=Paint.Style.FILL;paint.color=Color.argb(220,255,255,255)
            canvas.drawCircle(x,y,radius,paint)
            val ratio=(day.xp.toFloat()/r.goal).coerceIn(0f,1f)
            if(ratio>0f) {
                paint.color=warm
                canvas.drawCircle(x,y,radius*ratio,paint)
            }
            if(day.date==r.date) {
                paint.style=Paint.Style.STROKE;paint.strokeWidth=1.3f;paint.color=warm
                canvas.drawCircle(x,y,radius+2f,paint)
            }
            if(labels) label(canvas,paint,i,x,y+radius+12f,10f,Color.rgb(127,80,43))
        }
    }

    private fun drawWeek(canvas: Canvas, paint: Paint, snapshot: WidgetSnapshot, width: Float, height: Float) {
        val r=snapshot.reminder
        val labels=height>=38f
        val values=height>=90f && width>=260f
        val chartTop=if(values)17f else 3f
        val bottom=height-if(labels)17f else 2f
        val chartHeight=(bottom-chartTop).coerceAtLeast(4f)
        val maximum=maxOf(r.goal,snapshot.week.maxOf {it.xp},1)*1.15f
        val spacing=width/7
        val goalY=bottom-chartHeight*r.goal/maximum
        paint.color=coral;paint.style=Paint.Style.STROKE;paint.strokeWidth=1.2f
        paint.pathEffect=DashPathEffect(floatArrayOf(3f,4f),0f)
        canvas.drawLine(0f,goalY,width,goalY,paint)
        paint.pathEffect=null
        snapshot.week.forEachIndexed { i,day ->
            val x=spacing*(i+.5f)
            val barWidth=(spacing*.46f).coerceAtMost(38f)
            paint.style=Paint.Style.FILL
            paint.color=Color.argb(if(day.date.isAfter(r.date))12 else 24,255,255,255)
            canvas.drawRoundRect(RectF(x-barWidth/2,chartTop,x+barWidth/2,bottom),5f,5f,paint)
            if(day.xp>0) {
                val y=bottom-chartHeight*day.xp/maximum
                paint.color=if(day.date==r.date)coral else pale
                canvas.drawRoundRect(RectF(x-barWidth/2,y,x+barWidth/2,bottom),5f,5f,paint)
                if(values) {
                    paint.color=Color.rgb(216,241,236);paint.textSize=11f;paint.textAlign=Paint.Align.CENTER
                    paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
                    canvas.drawText(day.xp.toString(),x,(y-5f).coerceAtLeast(12f),paint)
                }
            }
            if(labels) label(canvas,paint,i,x,height-3f,10f,if(day.date==r.date)coral else Color.rgb(179,214,207))
        }
    }

    private fun label(canvas: Canvas, paint: Paint, day: Int, x: Float, y: Float, size: Float, color: Int) {
        paint.style=Paint.Style.FILL;paint.color=color;paint.textSize=size;paint.textAlign=Paint.Align.CENTER
        paint.typeface=Typeface.create(Typeface.DEFAULT,Typeface.BOLD)
        canvas.drawText(listOf("L","Ma","Me","J","V","S","D")[day],x,y,paint)
    }
}
