package com.malfreyt.alexandre.hamigo.platform

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.SizeF
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.RemoteViews
import com.malfreyt.alexandre.hamigo.MainActivity
import com.malfreyt.alexandre.hamigo.PicoRenderer
import com.malfreyt.alexandre.hamigo.MascotMood
import com.malfreyt.alexandre.hamigo.MascotPose
import com.malfreyt.alexandre.hamigo.R
import com.malfreyt.alexandre.hamigo.dayCount
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Geometry in launcher dp, shared by native text and the vector illustration. No stretched templates. */
internal data class WidgetTextSlot(val id: Int, val bounds: RectF, val text: String, val color: Int,
    val centered: Boolean = false)
internal data class WidgetComposition(val size: SizeF, val texts: List<WidgetTextSlot>, val chart: RectF,
    val pico: RectF?, val compact: Boolean, val column: Boolean)

internal object WidgetPresentation {
    private class WidgetPaint(val transparent: Boolean, val lightText: Boolean) : Paint(ANTI_ALIAS_FLAG)
    private val ink = Color.rgb(7, 61, 64)
    private val teal = Color.rgb(8, 127, 130)
    private val gold = Color.rgb(186, 99, 32)
    private val coral = Color.rgb(255, 186, 111)
    private val mint = Color.rgb(102, 214, 195)
    private val completed = Color.rgb(255, 209, 102)
    private val french = Locale.FRANCE
    private val dayLabels = listOf("L", "Ma", "Me", "J", "V", "S", "D")

    fun composition(kind: HomeWidgetKind, snapshot: WidgetSnapshot, size: SizeF): WidgetComposition {
        val w = size.width.coerceAtLeast(40f); val h = size.height.coerceAtLeast(40f)
        // Keep native text clear of both our corners and the launcher's extra rounding.
        val p = (minOf(w, h) * .10f).coerceIn(6f, 24f)
        val r = snapshot.reminder
        val dark = kind == HomeWidgetKind.WEEK
        val primary = if (dark) Color.WHITE else ink
        val accent = if (dark) mint else if (kind == HomeWidgetKind.STREAK) gold else teal
        val secondary = if (dark) Color.rgb(190, 221, 217) else Color.rgb(69, 103, 106)
        val short = w < 112f || h < 100f
        val sidePanel = w / h >= 1.55f || kind == HomeWidgetKind.GOAL && w >= 150f && h >= 110f && w / h >= 1.28f
        val texts = mutableListOf<WidgetTextSlot>()
        fun text(id: Int, x: Float, y: Float, width: Float, height: Float, value: String,
                 color: Int = primary, center: Boolean = false) {
            texts += WidgetTextSlot(id, RectF(x, y, x + width, y + height), value, color, center)
        }
        val title = when (kind) {
            HomeWidgetKind.STREAK -> if (short) "Série" else "Ta série"
            HomeWidgetKind.GOAL -> if (short) "Objectif" else "Objectif du jour"
            HomeWidgetKind.WEEK -> if (w < 160f || short) "Semaine" else "Cette semaine"
        }
        val value = when (kind) {
            HomeWidgetKind.STREAK -> if (short) "${number(r.streak, true)} j" else dayCount(r.streak)
            HomeWidgetKind.GOAL -> if (short) "${number(r.todayXp, true)}/${number(r.goal, true)}" else "${r.todayXp} XP"
            HomeWidgetKind.WEEK -> "${number(snapshot.weeklyXp, short)} XP"
        }
        val status = when (kind) {
            HomeWidgetKind.STREAK -> when {
                r.todayXp > 0 -> "${number(r.todayXp, short)} XP aujourd’hui"
                r.streak > 0 -> "Une leçon pour continuer"
                r.context == ReminderContext.RESTART -> "Un nouveau départ"
                else -> "Une leçon pour démarrer"
            }
            HomeWidgetKind.GOAL -> if (r.todayXp >= r.goal) "Objectif atteint !" else "Encore ${r.goal - r.todayXp} XP"
            HomeWidgetKind.WEEK -> "${dayCount(snapshot.activeDays)} ${if (snapshot.activeDays > 1) "actifs" else "actif"}"
        }
        val badge = when (kind) {
            HomeWidgetKind.STREAK -> "${number(r.todayXp, short)} XP aujourd’hui"
            HomeWidgetKind.GOAL -> "${number(r.goal, short)} XP / jour"
            HomeWidgetKind.WEEK -> "Objectif : ${number(r.goal, short)} XP / jour"
        }
        var pico: RectF? = null
        val chart: RectF
        var column = false
        if (w < 112f && h < 100f || h < 100f && w / h < 1.55f) {
            // Even a single cell retains Pico, its main statistic and an actual seven-day plot/gauge.
            val icon = (minOf(w, h) * .24f).coerceAtMost(20f)
            val titleH = (h * .18f).coerceIn(7f, 13f)
            text(R.id.widget_title, p, p, w - 2 * p - icon - 2f, titleH, if (kind == HomeWidgetKind.GOAL) "XP" else title, accent)
            pico = RectF(w - p - icon, p, w - p, p + icon)
            val metricTop = p + maxOf(titleH + 2f, icon + 1f)
            val metricH = (h * .32f).coerceAtMost(30f)
            text(R.id.widget_metric, p, metricTop, w - 2 * p, metricH, value, center = true)
            chart = RectF(p, metricTop + metricH + 2f, w - p, h - p)
        } else if ((h < 100f || w / h >= 3.2f) && w >= 112f) {
            // A ribbon keeps a compact identity at the left and gives its length to the data.
            val icon = minOf((h * .68f).coerceIn(20f, 88f), w * .23f)
            pico = RectF(p, (h - icon) / 2, p + icon, (h + icon) / 2)
            val x = p + icon + p * .6f
            val infoW = minOf((w * .29f).coerceIn(38f, 210f), (h * 1.4f).coerceIn(38f,180f)).coerceAtMost(w - x - 32f - p)
            val titleH = (h * .21f).coerceIn(8f, 23f)
            val metricH = (h * .38f).coerceIn(13f, 58f)
            val totalH = titleH + metricH + if (h >= 72f) 18f else 0f
            val y = (h - totalH) / 2
            text(R.id.widget_title, x, y, infoW, titleH, title, accent)
            text(R.id.widget_metric, x, y + titleH + 1f, infoW, metricH, value)
            if (h >= 72f) text(R.id.widget_status, x, y + titleH + metricH + 2f, infoW, 16f,
                if (kind == HomeWidgetKind.WEEK) status else if (kind == HomeWidgetKind.GOAL) "${r.goal} XP / jour" else "${r.todayXp} XP ce jour", secondary)
            chart = RectF(x + infoW + p, p, w - p, h - p)
        } else if (w < 112f && h / w >= 1.65f) {
            column = true
            val titleH = minOf((w * .19f).coerceIn(8f, 18f), h * .11f)
            val metricH = minOf((w * .33f).coerceIn(17f, 34f), h * .20f)
            text(R.id.widget_title, p, p, w - 2 * p, titleH, if (kind == HomeWidgetKind.GOAL) "XP / jour" else title, accent, true)
            text(R.id.widget_metric, p, p + titleH + 3f, w - 2 * p, metricH, value, center = true)
            val icon = minOf(w - 2 * p, h * .15f, 74f)
            val iconTop = p + titleH + metricH + 4f
            pico = RectF((w - icon) / 2, iconTop, (w + icon) / 2, iconTop + icon)
            chart = RectF(p, iconTop + icon + 4f, w - p, h - p)
        } else if (sidePanel) {
            val infoW = ((w - 2 * p) * .38f).coerceAtMost(250f)
            val titleH = (h * .13f).coerceIn(12f, 24f)
            val metricH = (h * .28f).coerceIn(25f, 60f)
            val statusH = (h * .13f).coerceIn(12f, 22f)
            text(R.id.widget_title, p, p, infoW, titleH, title, accent)
            text(R.id.widget_metric, p, p + titleH + 4f, infoW, metricH, value)
            val statusY = p + titleH + metricH + 6f
            text(R.id.widget_status, p, statusY, infoW, statusH, status, secondary)
            if (h >= 180f && kind != HomeWidgetKind.STREAK) text(R.id.widget_badge, p, statusY + statusH + 6f, infoW, 20f, badge, secondary)
            chart = RectF(p + infoW + p, p, w - p, h - p)
            if (kind == HomeWidgetKind.WEEK) {
                val side = minOf(52f, h * .24f, chart.width() * .35f)
                pico = RectF(chart.centerX() - side / 2, chart.top, chart.centerX() + side / 2, chart.top + side)
                chart.top += side + 4f
            }
        } else {
            val cw = w - 2 * p
            val titleH = (h * .085f).coerceIn(12f, 26f)
            val metricH = (h * .21f).coerceIn(23f, 70f)
            val statusH = (h * .085f).coerceIn(11f, 22f)
            val icon = minOf(cw * .31f, (h * .20f).coerceAtMost(86f))
            pico = RectF(w - p - icon, p, w - p, p + icon)
            text(R.id.widget_title, p, p, cw - icon - 3f, titleH, title, accent)
            text(R.id.widget_metric, p, p + titleH + 3f, cw - icon - 3f, metricH, value)
            val y = p + titleH + metricH + 5f
            text(R.id.widget_status, p, y, cw, statusH, status, secondary)
            chart = RectF(p, y + statusH + p * .5f, w - p, h - p)
            if (h >= 230f && w >= 160f && kind != HomeWidgetKind.STREAK) {
                // Reserve a genuine footer line rather than scaling up a small empty composition.
                text(R.id.widget_badge, p, chart.bottom - 22f, cw, 20f, badge, secondary)
                chart.bottom -= 28f
            }
        }
        if (kind == HomeWidgetKind.GOAL && !column && chart.height() >= 34f && w / h < 3.2f && h >= 100f) {
            pico = null
            // The ring contains Pico; let the statistics use the freed header width.
            texts.filter { it.id == R.id.widget_title || it.id == R.id.widget_metric }.forEach {
                if (!sidePanel) it.bounds.right = w - p
            }
        }
        if (kind == HomeWidgetKind.STREAK && !column && chart.width() >= 100f && chart.height() >= 78f) {
            pico = null
            if (!sidePanel) texts.filter { it.id == R.id.widget_title || it.id == R.id.widget_metric }.forEach { it.bounds.right = w - p }
        }
        return WidgetComposition(SizeF(w, h), texts, chart, pico, short, column)
    }

    fun views(context: Context, kind: HomeWidgetKind, snapshot: WidgetSnapshot, size: SizeF, pixelBudget: Float = 520_000f,
        transparent: Boolean = false, lightText: Boolean = true): RemoteViews {
        val layout = composition(kind, snapshot, size)
        val density = context.resources.displayMetrics.density
        fun px(dp: Float) = (dp * density).roundToInt()
        return RemoteViews(context.packageName, if(transparent&&lightText)R.layout.widget_responsive_transparent else R.layout.widget_responsive).apply {
            // Named, clipped background tells conforming launchers that we already round the tile.
            // The transparent outline owns the clip; the illustration supplies its colour.
            val corner = minOf(layout.size.width,layout.size.height)*.21f
            setInt(android.R.id.background,"setBackgroundResource",when {
                corner>=28f -> R.drawable.widget_outline_28
                corner>=24f -> R.drawable.widget_outline_24
                corner>=20f -> R.drawable.widget_outline_20
                corner>=16f -> R.drawable.widget_outline_16
                corner>=12f -> R.drawable.widget_outline_12
                else -> R.drawable.widget_outline_8
            })
            setImageViewBitmap(R.id.widget_art, artwork(context, kind, snapshot, layout, pixelBudget, transparent, lightText))
            listOf(R.id.widget_title, R.id.widget_metric, R.id.widget_status, R.id.widget_badge, R.id.widget_review).forEach { id ->
                val slot = layout.texts.firstOrNull { it.id == id }
                setViewVisibility(id, if (slot == null) View.GONE else View.VISIBLE)
                if (slot != null) {
                    setTextViewText(id, slot.text)
                    setTextColor(id,if(!transparent)slot.color else if(!lightText)when(id) {
                        R.id.widget_title -> if(kind==HomeWidgetKind.STREAK)gold else teal
                        R.id.widget_status,R.id.widget_badge -> Color.rgb(69,103,106)
                        else -> ink
                    } else when(id) {
                        R.id.widget_title -> if(kind==HomeWidgetKind.STREAK)coral else mint
                        R.id.widget_status,R.id.widget_badge -> Color.rgb(215,237,232)
                        else -> Color.WHITE
                    })
                    setViewPadding(id, px(slot.bounds.left), px(slot.bounds.top), px(layout.size.width - slot.bounds.right), px(layout.size.height - slot.bounds.bottom))
                    setInt(id, "setGravity", Gravity.CENTER_VERTICAL or if (slot.centered) Gravity.CENTER_HORIZONTAL else Gravity.START)
                    // TextView auto-size with ellipsize may accept an ellipsized candidate. Fit the
                    // exact native string ourselves, with real font metrics and rounding clearance.
                    val font = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        typeface = if (id == R.id.widget_status) Typeface.DEFAULT else Typeface.DEFAULT_BOLD
                        textSize = when (id) { R.id.widget_metric -> 56f; R.id.widget_title -> 20f; R.id.widget_status -> 16f; else -> 13f } * context.resources.displayMetrics.scaledDensity
                    }
                    val fm = font.fontMetrics
                    font.textSize *= minOf(1f, (px(slot.bounds.width()) - 3f) * .96f / font.measureText(slot.text).coerceAtLeast(1f),
                        (px(slot.bounds.height()) - 3f) * .96f / (fm.descent - fm.ascent).coerceAtLeast(1f))
                    setTextViewTextSize(id, TypedValue.COMPLEX_UNIT_PX, font.textSize.coerceAtLeast(1f))
                }
            }
            val intent = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra("hamigo_route", "path")
            val action = PendingIntent.getActivity(context, 2802, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            setOnClickPendingIntent(R.id.widget_root, action)
            val r = snapshot.reminder
            setContentDescription(R.id.widget_root, "Hamigo. ${when(kind) {
                HomeWidgetKind.STREAK -> "Série : ${dayCount(r.streak)}."
                HomeWidgetKind.GOAL -> "Aujourd’hui : ${r.todayXp} XP."
                HomeWidgetKind.WEEK -> "Cette semaine : ${snapshot.weeklyXp} XP, ${dayCount(snapshot.activeDays)} ${if (snapshot.activeDays > 1) "actifs" else "actif"}."
            }} Objectif : ${r.goal} XP par jour. ${snapshot.week.joinToString(" ; ") { "${it.date} : ${it.xp} XP" }}. Ouvrir le parcours.")
        }
    }

    private fun artwork(context: Context, kind: HomeWidgetKind, snapshot: WidgetSnapshot, layout: WidgetComposition, pixelBudget: Float,
        transparent: Boolean, lightText: Boolean): Bitmap {
        val w = layout.size.width; val h = layout.size.height
        // One bitmap per actual host orientation; bounded memory even on tablets and huge test shapes.
        val scale = minOf(context.resources.displayMetrics.density, 2.5f, 1400f / maxOf(w, h), sqrt(pixelBudget / (w * h)))
        val bitmap = Bitmap.createBitmap((w * scale).roundToInt().coerceAtLeast(1), (h * scale).roundToInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { scale(scale, scale) }
        val paint = WidgetPaint(transparent, lightText)
        val dark = kind == HomeWidgetKind.WEEK
        val bg = when (kind) { HomeWidgetKind.STREAK -> Color.rgb(255, 241, 214); HomeWidgetKind.GOAL -> Color.rgb(219, 244, 238); HomeWidgetKind.WEEK -> Color.rgb(18, 63, 67) }
        val end = when (kind) { HomeWidgetKind.STREAK -> Color.rgb(255, 225, 178); HomeWidgetKind.GOAL -> Color.rgb(195, 232, 224); HomeWidgetKind.WEEK -> Color.rgb(27, 81, 83) }
        val corner = (minOf(w, h) * .21f).coerceAtMost(28f)
        val outline = Path().apply { addRoundRect(RectF(0f, 0f, w, h), corner, corner, Path.Direction.CW) }
        canvas.clipPath(outline)
        if (!transparent) {
            paint.shader = LinearGradient(0f, 0f, w, h, bg, end, Shader.TileMode.CLAMP)
            canvas.drawRect(0f, 0f, w, h, paint); paint.shader = null
            // Soft radio waves belong to the background, not the data illustration.
            paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.3f; paint.color = if (dark) Color.argb(9, 255, 255, 255) else Color.argb(8, 8, 127, 130)
            val radius = minOf(w, h) * .35f
            (1..3).forEach { canvas.drawCircle(w, 0f, radius * it, paint) }
        }
        paint.style = Paint.Style.FILL
        layout.pico?.let { drawPico(canvas, it, snapshot) }
        val chart = layout.chart
        if (chart.width() > 1f && chart.height() > 1f) {
            when (kind) {
                HomeWidgetKind.STREAK -> streak(canvas, paint, snapshot, chart, layout.column, context.resources.configuration.fontScale)
                HomeWidgetKind.GOAL -> goal(canvas, paint, snapshot, chart, layout.column,
                    layout.compact && !layout.column || layout.size.width / layout.size.height >= 3.2f, context.resources.configuration.fontScale)
                HomeWidgetKind.WEEK -> week(canvas, paint, snapshot, chart, layout.column, context.resources.configuration.fontScale)
            }
        }
        return bitmap
    }

    private fun streak(canvas: Canvas, p: Paint, snapshot: WidgetSnapshot, box: RectF, column: Boolean, font: Float) {
        if (column || box.height() < 78f || box.width() < 100f) {
            days(canvas,p,snapshot,box,column,font)
            return
        }
        if (box.width() / box.height() >= 1.8f) {
            val side = minOf(box.height() * .80f,box.width() * .30f,180f)
            val gap = (side*.14f).coerceIn(10f,24f)
            val pico = RectF(box.left,box.centerY()-side/2,box.left+side,box.centerY()+side/2)
            hero(canvas,p,snapshot,pico)
            days(canvas,p,snapshot,RectF(pico.right+gap,box.top,box.right,box.bottom),false,font)
        } else {
            val tall = box.height() / box.width() > 2.6f
            val dayH = if (tall) box.height() * .65f else minOf(64f,box.height() * .34f)
            val gap = (minOf(box.width(),box.height())*.07f).coerceIn(10f,24f)
            val side = minOf(box.width() * .70f,box.height()-dayH-gap,200f)
            val top = if (tall) box.top else box.top + (box.height()-side-dayH-gap)/2
            val pico = RectF(box.centerX()-side/2,top,box.centerX()+side/2,top+side)
            hero(canvas,p,snapshot,pico)
            val history = RectF(box.left,pico.bottom+gap,box.right,if (tall) box.bottom else pico.bottom+gap+dayH)
            days(canvas,p,snapshot,history,tall,font)
        }
    }

    private fun hero(canvas: Canvas, p: Paint, snapshot: WidgetSnapshot, bounds: RectF) {
        p.style=Paint.Style.FILL; p.color=Color.argb(95,255,255,255)
        canvas.drawCircle(bounds.centerX(),bounds.centerY()+bounds.height()*.06f,bounds.width()*.46f,p)
        drawPico(canvas,bounds,snapshot)
    }

    /** Widget expressions describe the present state; daily notification variants stay independent. */
    private fun drawPico(canvas: Canvas, bounds: RectF, snapshot: WidgetSnapshot) {
        val (mood, pose) = when (snapshot.reminder.context) {
            ReminderContext.START -> MascotMood.HAPPY to MascotPose.WAVE
            ReminderContext.CONTINUE -> MascotMood.DETERMINED to MascotPose.POINT
            ReminderContext.IN_PROGRESS -> MascotMood.THINKING to MascotPose.HUG
            ReminderContext.GOAL_REACHED -> MascotMood.CELEBRATE to MascotPose.DANCE
            ReminderContext.RESTART -> MascotMood.SAD to MascotPose.HUG
        }
        PicoRenderer.draw(canvas, bounds, mood, pose, .3f)
    }

    private fun goal(canvas: Canvas, p: Paint, snapshot: WidgetSnapshot, box: RectF, column: Boolean, horizontal: Boolean, font: Float) {
        val r = snapshot.reminder
        val ratio = (r.todayXp.toFloat() / r.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        val fill = if (ratio >= 1f) completed else teal
        if (column) {
            val labelH = if (box.height() >= 70f) 16f else 0f
            val track = RectF(box.left + box.width() * .34f, box.top + labelH, box.right - box.width() * .34f, box.bottom - labelH)
            round(canvas, p, track, Color.argb(34, 8, 127, 130), track.width() / 2)
            if (ratio > 0f) round(canvas, p, RectF(track.left, track.bottom - track.height() * ratio, track.right, track.bottom), fill, track.width() / 2)
            if (labelH > 0f) {
                fitted(canvas, p, "${(ratio * 100).roundToInt()} %", RectF(box.left, box.top, box.right, box.top + labelH), 12f * font, teal)
                fitted(canvas, p, "${number(r.goal, true)} XP", RectF(box.left, box.bottom - labelH, box.right, box.bottom), 10f * font, ink)
            }
            if (track.height() >= 70f) {
                // Foreground graduations are inset, including when the goal is completely filled.
                p.color = ink; p.strokeWidth = 1.1f
                val inset = (track.width() * .20f).coerceIn(1f, 4f)
                (1..3).forEach { i -> val y = track.bottom - track.height() * i / 4; canvas.drawLine(track.left + inset, y, track.right - inset, y, p) }
            }
        } else if (box.height() < 34f || horizontal) {
            // The small tile already says XP/goal; two tiny axis labels would repeat it.
            val labels = box.height() >= 27f && box.width() >= 140f
            val gap = if (labels) minOf(box.height() * .32f, 18f) else 0f
            val thickness = (box.height() - gap).coerceAtMost(26f)
            val top = box.top + (box.height() - gap - thickness) / 2
            val track = RectF(box.left, top, box.right, top + thickness)
            round(canvas, p, track, Color.argb(35, 8, 127, 130), thickness / 2)
            if (ratio > 0) round(canvas, p, RectF(track.left, top, track.left + track.width() * ratio, track.bottom), fill, thickness / 2)
            if (track.width() >= 130f) {
                p.color = Color.argb(85, 255, 255, 255); p.strokeWidth = 1f
                (1..3).forEach { i -> val x = track.left + track.width() * i / 4; canvas.drawLine(x, top + 2, x, track.bottom - 2, p) }
            }
            if (labels) {
                fitted(canvas, p, "${(ratio * 100).roundToInt()} %", RectF(box.left, track.bottom + 1, box.centerX(), box.bottom), 12f * font, teal, false)
                fitted(canvas, p, "${r.goal} XP", RectF(box.centerX(), track.bottom + 1, box.right, box.bottom), 12f * font, teal)
            }
        } else {
            // A quiet hero ring and a small calendar strip; no repeated table of XP beside it.
            val side = minOf(box.width()*.88f, box.height(), 260f)
            val tall = box.height()/box.width()>2.6f
            val hasHistory = box.height()-side>=42f
            val historyH = if (tall) box.height()-side-12f else minOf(64f,box.height()*.24f)
            val totalH = side + if (hasHistory) historyH+12f else 0f
            val ringTop = if (tall) box.top else box.top+(box.height()-totalH)/2
            val cx = box.centerX(); val cy = ringTop+side/2
            val radius = side * .43f
            val thickness = (side * .045f).coerceIn(3f, 12f)
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = thickness
            p.color = Color.argb(35, 8, 127, 130); canvas.drawCircle(cx, cy, radius, p)
            val ring = RectF(cx - radius, cy - radius, cx + radius, cy + radius)
            p.color = fill; if (ratio >= 1f) p.shader = goldReflection(ring)
            canvas.drawArc(ring, -90f, 360f * ratio, false, p); p.shader = null
            p.style = Paint.Style.FILL
            drawPico(canvas, RectF(cx - radius * .55f, cy - radius * .67f, cx + radius * .55f, cy + radius * .39f), snapshot)
            fitted(canvas, p, "${(ratio * 100).roundToInt()} %", RectF(cx - radius * .58f, cy + radius * .52f, cx + radius * .58f, cy + radius * .80f), minOf(26f, side * .14f) * font, teal)
            if (hasHistory) {
                val history = RectF(box.left,ringTop+side+12f,box.right,ringTop+side+12f+historyH)
                days(canvas,p,snapshot,history,tall,font,teal)
            }
        }
    }

    private fun days(canvas: Canvas, p: Paint, snapshot: WidgetSnapshot, box: RectF, forceColumn: Boolean, font: Float, color: Int = gold) {
        val vertical = forceColumn && box.height() >= 60f
        val warm = color
        if (vertical) {
            val step = box.height() / 7
            val radius = minOf(box.width() * .20f, step * .29f, 21f)
            val detailed = box.width() >= 112f && step >= 20f
            val cx = box.left + if (detailed) radius + 2f else box.width() * .38f
            p.color = Color.argb(38, 186, 99, 32); p.strokeWidth = 2f
            canvas.drawLine(cx, box.top + step / 2, cx, box.bottom - step / 2, p)
            snapshot.week.forEachIndexed { i, day ->
                val cy = box.top + (i + .5f) * step
                dayCircle(canvas, p, snapshot, day, cx, cy, radius, warm)
                val labelBox = if (detailed) RectF(cx + radius + 8f, cy - step * .30f, box.right, cy + step * .30f) else
                    RectF(cx + radius + 4f, cy - step * .25f, box.right, cy + step * .25f)
                val label = if (detailed) day.date.format(DateTimeFormatter.ofPattern("EEE d", french)) else dayLabels[i]
                if (!detailed && step >= 32f) {
                    fitted(canvas, p, label, RectF(labelBox.left, cy - 15f, labelBox.right, cy - 1f), 10f * font, ink, false)
                    fitted(canvas, p, day.date.dayOfMonth.toString(), RectF(labelBox.left, cy + 1f, labelBox.right, cy + 14f), 10f * font, warm, false)
                } else fitted(canvas, p, label, labelBox, if (detailed) minOf(23f, box.width() * .06f) * font else 10f * font, ink, false)
            }
        } else {
            val step = box.width() / 7
            val labels = box.height() >= 25f
            val labelH = if (labels) minOf(20f, box.height() * .28f) else 0f
            val radius = minOf(step * .30f, (box.height() - labelH) * .36f, 24f)
            val contentH = 2 * radius + labelH + if (labels) 5f else 0f
            val cy = box.centerY() - contentH / 2 + radius
            p.color = Color.argb(35, 186, 99, 32); p.strokeWidth = 2f
            canvas.drawLine(box.left + step / 2, cy, box.right - step / 2, cy, p)
            snapshot.week.forEachIndexed { i, day ->
                val cx = box.left + (i + .5f) * step
                dayCircle(canvas, p, snapshot, day, cx, cy, radius, warm)
                val labelTop = cy + radius + 5f
                if (labels) horizontalDayLabel(canvas, p, i, RectF(cx - step * .45f, labelTop, cx + step * .45f, labelTop + labelH), font, ink)
            }
        }
    }

    private fun dayCircle(canvas: Canvas, p: Paint, snapshot: WidgetSnapshot, day: WidgetDay, cx: Float, cy: Float, radius: Float, color: Int) {
        p.style = Paint.Style.FILL; p.color = Color.argb(180, 255, 255, 255)
        canvas.drawCircle(cx, cy, radius, p)
        val ratio = (day.xp.toFloat() / snapshot.reminder.goal.coerceAtLeast(1)).coerceIn(0f, 1f)
        if (ratio > 0) {
            p.color = if (ratio >= 1f) completed else color
            if (ratio >= 1f) p.shader = goldReflection(RectF(cx-radius,cy-radius,cx+radius,cy+radius))
            canvas.drawCircle(cx, cy, radius * sqrt(ratio), p); p.shader = null
        }
        if (ratio >= 1f && radius >= 6f) {
            p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND; p.strokeWidth = radius * .18f; p.color = ink
            canvas.drawPath(Path().apply { moveTo(cx-radius*.4f,cy); lineTo(cx-radius*.1f,cy+radius*.28f); lineTo(cx+radius*.43f,cy-radius*.32f) }, p)
            p.style = Paint.Style.FILL
        }
        if (day.date == snapshot.reminder.date) {
            p.style = Paint.Style.STROKE; p.strokeWidth = (radius*.13f).coerceIn(.6f,1.2f); p.color = color
            // A fixed 2dp halo makes adjacent dots touch in the smallest calendars.
            canvas.drawCircle(cx, cy, radius + (radius*.25f).coerceIn(.6f,2f), p); p.style = Paint.Style.FILL
        }
    }

    private fun week(canvas: Canvas, p: Paint, snapshot: WidgetSnapshot, box: RectF, forceColumn: Boolean, font: Float) {
        val maximum = maxOf(snapshot.reminder.goal, snapshot.week.maxOfOrNull { it.xp } ?: 0, 1).toFloat() * 1.18f
        val vertical = forceColumn || box.height() > box.width() * 1.35f
        if (vertical) {
            val step = box.height() / 7
            val labelsW = (box.width() * .15f).coerceIn(8f, 35f)
            val valuesW = if (box.width() >= 90f || step >= 32f) minOf(box.width() * .24f, 70f) else 0f
            val left = box.left + labelsW + 2; val right = box.right - valuesW
            val thickness = (step * .40f).coerceIn(3f, 28f)
            snapshot.week.forEachIndexed { i, day ->
                val cy = box.top + (i + .5f) * step
                fitted(canvas, p, dayLabels[i], RectF(box.left, cy - step * .30f, left - 2, cy + step * .30f), 12f * font, mint, false)
                round(canvas, p, RectF(left, cy - thickness / 2, right, cy + thickness / 2), Color.argb(23, 255, 255, 255), thickness / 2)
                if (day.xp > 0) round(canvas, p, RectF(left, cy - thickness / 2, left + (right - left) * day.xp / maximum, cy + thickness / 2), if (day.xp >= snapshot.reminder.goal) completed else if (day.date == snapshot.reminder.date) coral else mint, thickness / 2)
                if (valuesW > 0 && day.xp>0) fitted(canvas, p, number(day.xp, valuesW < 35), RectF(right + 3, cy - step * .30f, box.right, cy + step * .30f), 13f * font, Color.WHITE)
            }
            val goalX = left + (right - left) * snapshot.reminder.goal / maximum
            dashed(canvas, p, goalX, box.top + step * .2f, goalX, box.bottom - step * .2f, coral)
        } else {
            val labels = box.height() >= 24f
            val values = box.height() >= 90f && box.width() >= 240f
            val labelH = if (labels) minOf(box.height() * .23f, 20f) else 0f
            val top = box.top + if (values) 20f else 1f
            val bottom = box.bottom - labelH
            val height = bottom - top
            val step = box.width() / 7
            val thickness = (step * .52f).coerceAtMost(65f)
            val goalY = bottom - height * snapshot.reminder.goal / maximum
            snapshot.week.forEachIndexed { i, day ->
                val cx = box.left + (i + .5f) * step
                round(canvas, p, RectF(cx - thickness / 2, top, cx + thickness / 2, bottom), Color.argb(23, 255, 255, 255), minOf(thickness / 2, 8f))
                val y = bottom - height * day.xp / maximum
                if (day.xp > 0) round(canvas, p, RectF(cx - thickness / 2, y, cx + thickness / 2, bottom), if (day.xp >= snapshot.reminder.goal) completed else if (day.date == snapshot.reminder.date) coral else mint, minOf(thickness / 2, 8f))
                if (labels) horizontalDayLabel(canvas, p, i, RectF(cx - step * .45f, bottom + 1, cx + step * .45f, box.bottom), font, if (day.date == snapshot.reminder.date) coral else mint)
                if (values && day.xp>0) fitted(canvas, p, number(day.xp, step < 50f), RectF(cx - step * .45f, maxOf(box.top, y - 20), cx + step * .45f, y - 1), 14f * font, Color.WHITE)
            }
            // The goal is a reference in the foreground, including across a bar that exceeds it.
            dashed(canvas, p, box.left, goalY, box.right, goalY, coral)
        }
    }

    private fun round(c: Canvas, p: Paint, rect: RectF, color: Int, radius: Float) {
        if (rect.width() <= 0 || rect.height() <= 0) return
        p.style = Paint.Style.FILL; p.color = color; p.pathEffect = null
        if (color == completed) p.shader = goldReflection(rect)
        c.drawRoundRect(rect, radius, radius, p); p.shader = null
    }
    private fun goldReflection(rect: RectF) = LinearGradient(rect.left,rect.top,rect.right,rect.bottom,
        intArrayOf(Color.rgb(242,195,84),completed,Color.rgb(255,228,160),completed),
        floatArrayOf(0f,.35f,.53f,1f),Shader.TileMode.CLAMP)

    private fun horizontalDayLabel(c: Canvas, p: Paint, day: Int, rect: RectF, font: Float, color: Int) {
        // All main initials share one baseline; Tuesday/Wednesday have a smaller letter below.
        val first = RectF(rect.left,rect.top,rect.right,rect.top+rect.height()*.64f)
        fitted(c,p,dayLabels[day].take(1),first,12f*font,color)
        if (day in 1..2) fitted(c,p,dayLabels[day].takeLast(1),
            RectF(rect.left,rect.top+rect.height()*.64f,rect.right,rect.bottom),8f*font,color)
    }
    private fun dashed(c: Canvas, p: Paint, x1: Float, y1: Float, x2: Float, y2: Float, color: Int) {
        p.style = Paint.Style.STROKE; p.color = color; p.strokeWidth = 1.2f
        p.pathEffect = DashPathEffect(floatArrayOf(3f, 4f), 0f)
        c.drawLine(x1, y1, x2, y2, p); p.pathEffect = null; p.style = Paint.Style.FILL
    }
    /** Canvas labels get real font metrics; they cannot overflow their reserved rectangle. */
    private fun fitted(c: Canvas, p: Paint, text: String, rect: RectF, size: Float, color: Int, center: Boolean = true) {
        if (rect.width() <= 0 || rect.height() <= 0) return
        val theme = p as? WidgetPaint
        val light = theme?.transparent == true && theme.lightText
        p.style = Paint.Style.FILL
        p.color = if(theme?.transparent==true)when(color) {
            ink,Color.WHITE -> if(light)Color.WHITE else ink
            teal,mint -> if(light)mint else teal
            gold,coral -> if(light)coral else gold
            else -> color
        } else color
        p.typeface = Typeface.DEFAULT_BOLD; p.textSize = size
        if(light)p.setShadowLayer(1.5f,0f,.6f,Color.argb(160,0,0,0))
        val fm = p.fontMetrics
        p.textSize *= minOf(1f, rect.width() / p.measureText(text).coerceAtLeast(1f), rect.height() / (fm.descent - fm.ascent).coerceAtLeast(1f))
        p.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
        val baseline = rect.centerY() - (p.fontMetrics.ascent + p.fontMetrics.descent) / 2
        c.drawText(text, if (center) rect.centerX() else rect.left, baseline, p)
        p.clearShadowLayer()
    }
    internal fun number(value: Int, compact: Boolean): String {
        if (!compact || value < 10_000) return value.toString()
        val divisor = when { value >= 1_000_000_000 -> 1_000_000_000; value >= 1_000_000 -> 1_000_000; else -> 1000 }
        val suffix = when (divisor) { 1_000_000_000 -> "G"; 1_000_000 -> "M"; else -> "k" }
        return (if (value / divisor < 10) String.format(french, "%.1f", value.toDouble() / divisor) else (value / divisor).toString()) + suffix
    }
}
