package com.malfreyt.alexandre.hamigo.platform

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Outline
import android.graphics.drawable.BitmapDrawable
import android.util.SizeF
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.ImageView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate

/** Direct native rendering: the atlas is drawn from RemoteViews, not resized screenshot files. */
@RunWith(AndroidJUnit4::class)
class WidgetAtlasInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val sides = listOf(40, 56, 72, 96, 120, 160, 220, 300, 420, 600, 800)
    private val detailedSizes = setOf("40x40", "56x72", "72x72", "96x96", "40x800", "800x40", "72x300", "300x72", "120x160", "160x120", "220x300", "600x600")
    private val directory get() = File(context.getExternalFilesDir(null), "widgets-atlas/" +
        InstrumentationRegistry.getArguments().getString("atlasLabel", "current") + "-font" + context.resources.configuration.fontScale).apply { mkdirs() }

    @Test fun renderEveryWidgetAcrossTheSizeAtlas() {
        val date = LocalDate.of(2026, 10, 8)
        val reminder = ReminderContent.build(ReminderState(18, 30, (0L..4L).map { date.minusDays(it).toString() }.toSet()), date)
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        val snapshot = WidgetSnapshot(reminder, (0L..6L).map { WidgetDay(monday.plusDays(it), listOf(30, 12, 48, 18, 0, 0, 0)[it.toInt()]) })
        val report = JSONArray()
        val failures = mutableListOf<String>()
        val gap = 12
        val left = 42
        val top = 48
        val labelHeight = 17
        val boardWidth = left + sides.sum() + gap * (sides.size + 1)
        val boardHeight = top + sides.sum() + (gap + labelHeight) * sides.size + 12
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(227, 237, 240); textSize = 11f }
        HomeWidgetKind.entries.forEach { kind ->
            val bitmap = Bitmap.createBitmap(boardWidth, boardHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap).apply { drawColor(Color.rgb(48, 62, 74)) }
            paint.textSize = 18f; paint.typeface = android.graphics.Typeface.DEFAULT_BOLD
            canvas.drawText("HAMIGO · ${kind.name} · ${sides.size * sides.size} tailles · police ${context.resources.configuration.fontScale}", 16f, 26f, paint)
            paint.textSize = 10f; paint.typeface = android.graphics.Typeface.DEFAULT
            var y = top
            sides.forEach { height ->
                var x = left
                sides.forEach { width ->
                    val size = SizeF(width.toFloat(), height.toFloat())
                    instrumentation.runOnMainSync {
                        val view = HomeWidgets.views(context, kind, snapshot, size).apply(context, FrameLayout(context))
                        measure(view, size)
                        val errors = nativeIssues(view)
                        failures.addAll(errors.map { "${kind.name} ${width}x$height: $it" })
                        report.put(JSONObject().put("kind", kind.name).put("width", width).put("height", height)
                            .put("errors", JSONArray(errors)).put("texts", textReport(view)))
                        canvas.drawText("${width} × $height", x.toFloat(), (y + 11).toFloat(), paint)
                        canvas.save()
                        canvas.translate(x.toFloat(), (y + labelHeight).toFloat())
                        canvas.scale(1f / context.resources.displayMetrics.density, 1f / context.resources.displayMetrics.density)
                        view.draw(canvas)
                        canvas.restore()
                        if ("${width}x$height" in detailedSizes) capture(view, "${kind.name.lowercase()}-${width}x$height")
                    }
                    x += width + gap
                }
                y += height + labelHeight + gap
            }
            save(bitmap, "atlas-${kind.name.lowercase()}")
        }
        File(directory, "report.json").writeText(JSONObject().put("states", report).put("failures", JSONArray(failures)).toString(2))
        println("WIDGET_ATLAS: ${report.length()} native states; ${failures.size} layout issues; ${directory.absolutePath}")
        assertTrue(failures.take(25).joinToString("\n"), failures.isEmpty())
    }

    @Test fun boundariesExtremeShapesAndProgressStatesFitNativeViews() {
        val sizes = mutableSetOf<SizeF>()
        listOf(99,100,101).forEach { h -> listOf(111,112,113,159,160,161,319,320,321).forEach { w -> sizes += SizeF(w.toFloat(),h.toFloat()) } }
        listOf(112,160,220).forEach { side ->
            listOf(1.55f,1.65f,3.2f).forEach { ratio -> (-1..1).forEach { offset -> sizes += SizeF(side * ratio + offset,side.toFloat()) } }
        }
        sizes += listOf(SizeF(40f,40f),SizeF(84f,91f),SizeF(130f,130f),SizeF(220f,150f),SizeF(280f,180f),
            SizeF(40f,1600f),SizeF(1600f,40f),SizeF(137f,59f),SizeF(91f,333f),SizeF(1234f,87f),SizeF(800f,1200f),SizeF(1200f,800f))
        val date = LocalDate.of(2026,10,8)
        val snapshots = listOf(
            "empty" to ReminderContent.build(ReminderState(0,30,emptySet()),date),
            "met" to ReminderContent.build(ReminderState(45,30,setOf(date.toString())),date),
            "restart" to ReminderContent.build(ReminderState(0,60,setOf(date.minusDays(3).toString())),date),
            "large" to ReminderContent.build(ReminderState(123456,1000,setOf(date.toString())),date).copy(streak=12345),
        )
        val failures = mutableListOf<String>()
        var count = 0
        snapshots.forEach { (state,r) ->
            val monday = date.minusDays((date.dayOfWeek.value-1).toLong())
            val snapshot = WidgetSnapshot(r,(0L..6L).map { WidgetDay(monday.plusDays(it),if (it<4) r.todayXp else 0) })
            HomeWidgetKind.entries.forEach { kind -> sizes.forEach { size ->
                instrumentation.runOnMainSync {
                    val view = HomeWidgets.views(context,kind,snapshot,size).apply(context,FrameLayout(context))
                    measure(view,size)
                    failures += nativeIssues(view).map { "$state $kind $size: $it" }
                    failures += compositionIssues(WidgetPresentation.composition(kind,snapshot,size)).map { "$state $kind $size: $it" }
                    if (size in listOf(SizeF(40f,40f),SizeF(84f,91f),SizeF(40f,1600f),SizeF(1600f,40f),SizeF(220f,150f))) {
                        capture(view,"$state-${kind.name.lowercase()}-${size.width.toInt()}x${size.height.toInt()}")
                    }
                    count++
                }
            } }
        }
        File(directory,"boundary-report.json").writeText(JSONObject().put("nativeStates",count).put("sizes",sizes.size)
            .put("failures",JSONArray(failures)).toString(2))
        assertTrue(failures.take(30).joinToString("\n"),failures.isEmpty())
    }

    @Test fun fiveReminderStatesHaveNoFauxActionsAndGoalCompletionChangesTheGraphic() {
        val date=LocalDate.of(2026,10,8)
        val states=listOf(
            ReminderState(0,30,emptySet()),
            ReminderState(0,30,setOf(date.minusDays(1).toString())),
            ReminderState(18,30,setOf(date.toString(),date.minusDays(1).toString())),
            ReminderState(30,30,setOf(date.toString(),date.minusDays(1).toString())),
            ReminderState(0,30,setOf(date.minusDays(3).toString()))
        )
        states.forEach { state ->
            val reminder=ReminderContent.build(state,date)
            val snapshot=WidgetSnapshot(reminder,(0L..6L).map { WidgetDay(date.minusDays(6-it),if(it==6L)state.todayXp else 0) })
            HomeWidgetKind.entries.forEach {kind->
                listOf(SizeF(84f,91f),SizeF(130f,130f),SizeF(350f,220f),SizeF(72f,300f)).forEach {size->
                    instrumentation.runOnMainSync {
                        val view=HomeWidgets.views(context,kind,snapshot,size).apply(context,FrameLayout(context))
                        measure(view,size)
                        assertTrue(nativeIssues(view).joinToString(),nativeIssues(view).isEmpty())
                        assertTrue(compositionIssues(WidgetPresentation.composition(kind,snapshot,size)).joinToString(),compositionIssues(WidgetPresentation.composition(kind,snapshot,size)).isEmpty())
                        assertTrue("The complete tile opens the path without a duplicate CTA",view.findViewById<View>(R.id.widget_review).visibility==View.GONE)
                        if(kind == HomeWidgetKind.WEEK || kind == HomeWidgetKind.GOAL && size == SizeF(84f,91f)) {
                            val bitmap=(view.findViewById<ImageView>(R.id.widget_art).drawable as BitmapDrawable).bitmap
                            // Probe only the plot, not Pico's cream face or the native counters.
                            val chart=WidgetPresentation.composition(kind,snapshot,size).chart
                            val scale=bitmap.width/size.width
                            val left=(chart.left*scale).toInt();val top=(chart.top*scale).toInt()
                            val width=((chart.right*scale).toInt()-left).coerceAtLeast(1)
                            val height=((chart.bottom*scale).toInt()-top).coerceAtLeast(1)
                            val pixels=IntArray(width*height)
                            bitmap.getPixels(pixels,0,width,left,top,width,height)
                            val gold=pixels.count {Color.red(it)>215 && Color.green(it) in 200..245 && Color.blue(it) in 55..185}
                            assertTrue("Completed and incomplete goals must look different at $kind $size",if(state.todayXp>=state.goal)gold>0 else gold==0)
                        }
                        capture(view,"state-${reminder.context.name.lowercase()}-${kind.name.lowercase()}-${size.width.toInt()}x${size.height.toInt()}")
                    }
                }
            }
        }
    }

    @Test fun continuousGeometryHasNoOverlappingOrOutOfBoundsElements() {
        val date=LocalDate.of(2026,10,8)
        val r=ReminderContent.build(ReminderState(18,30,setOf(date.toString())),date)
        val snapshot=WidgetSnapshot(r,(0L..6L).map { WidgetDay(date.minusDays(it),18) })
        val failures=mutableListOf<String>()
        var count=0
        HomeWidgetKind.entries.forEach { kind -> (40..1200 step 13).forEach { w -> (40..1200 step 17).forEach { h ->
            val layout=WidgetPresentation.composition(kind,snapshot,SizeF(w.toFloat(),h.toFloat()))
            failures += compositionIssues(layout).map { "$kind ${w}x$h: $it" }
            count++
        } } }
        File(directory,"geometry-report.json").writeText(JSONObject().put("compositions",count).put("failures",JSONArray(failures)).toString(2))
        assertTrue(failures.take(30).joinToString("\n"),failures.isEmpty())
    }

    @Test fun verticalGaugeGraduationsStayInsideAndVisibleOverTheFilledTrack() {
        val date=LocalDate.of(2026,10,8)
        val r=ReminderContent.build(ReminderState(30,30,setOf(date.toString())),date)
        val snapshot=WidgetSnapshot(r,(0L..6L).map {WidgetDay(date.minusDays(it),0)})
        val size=SizeF(72f,300f)
        instrumentation.runOnMainSync {
            val view=HomeWidgets.views(context,HomeWidgetKind.GOAL,snapshot,size).apply(context,FrameLayout(context))
            measure(view,size)
            val bitmap=(view.findViewById<ImageView>(R.id.widget_art).drawable as BitmapDrawable).bitmap
            val box=WidgetPresentation.composition(HomeWidgetKind.GOAL,snapshot,size).chart
            val scale=bitmap.width/size.width
            val left=box.left+box.width()*.34f;val right=box.right-box.width()*.34f
            val top=box.top+16f;val bottom=box.bottom-16f
            fun dark(x:Float,y:Float):Boolean {
                val pixel=bitmap.getPixel((x*scale).toInt(),(y*scale).toInt())
                return Color.red(pixel)<60 && Color.green(pixel)<100 && Color.blue(pixel)<100
            }
            (1..3).forEach {i->
                val y=bottom-(bottom-top)*i/4
                assertTrue("A graduation must remain visible over a full gauge",(-1..1).any {dark((left+right)/2,y+it*.3f)})
                assertTrue("Graduations must not protrude",!dark(left-2f,y) && !dark(right+2f,y))
            }
            capture(view,"goal-vertical-graduations")
        }
    }

    private fun compositionIssues(layout: WidgetComposition): List<String> = buildList {
        val elements=layout.texts.map { "text ${it.text}" to it.bounds } + listOf("chart" to layout.chart) + listOfNotNull(layout.pico?.let { "Pico" to it })
        elements.forEach { (label,b) ->
            if (b.width()<=0 || b.height()<=0 || b.left<0 || b.top<0 || b.right>layout.size.width+.01f || b.bottom>layout.size.height+.01f) add("Invalid $label: $b")
        }
        elements.forEachIndexed { i,a -> elements.drop(i+1).forEach { b ->
            if (RectF.intersects(a.second,b.second)) add("Collision ${a.first} / ${b.first}")
        } }
    }

    @Test fun dailyGoalDashesStayInFrontOfTheWeeklyBars() {
        val date=LocalDate.of(2026,10,8)
        val r=ReminderContent.build(ReminderState(18,30,setOf(date.toString())),date)
        val monday=date.minusDays((date.dayOfWeek.value-1).toLong())
        val snapshot=WidgetSnapshot(r,(0L..6L).map { WidgetDay(monday.plusDays(it),listOf(30,12,48,18,0,0,0)[it.toInt()]) })
        val size=SizeF(600f,600f)
        instrumentation.runOnMainSync {
            val view=HomeWidgets.views(context,HomeWidgetKind.WEEK,snapshot,size).apply(context,FrameLayout(context))
            measure(view,size)
            val bitmap=(view.findViewById<ImageView>(R.id.widget_art).drawable as BitmapDrawable).bitmap
            val box=WidgetPresentation.composition(HomeWidgetKind.WEEK,snapshot,size).chart
            val scale=bitmap.width/size.width
            val chartTop=box.top+20f
            val bottom=box.bottom-20f
            val goalY=bottom-(bottom-chartTop)*30f/(48f*1.18f)
            val centerX=box.left+box.width()/7*2.5f // Wednesday's mint bar is above the goal.
            var foregroundPixels=0
            for (x in ((centerX-10)*scale).toInt()..((centerX+10)*scale).toInt()) {
                for (y in ((goalY-2)*scale).toInt()..((goalY+2)*scale).toInt()) {
                    val color=bitmap.getPixel(x,y)
                    if (Color.red(color)>230 && Color.green(color) in 165..205 && Color.blue(color) in 90..130) foregroundPixels++
                }
            }
            assertTrue("The goal line must be visible over the filled mint bar",foregroundPixels>=3)
        }
    }

    private val nativeIds = listOf(R.id.widget_title, R.id.widget_metric, R.id.widget_status, R.id.widget_review, R.id.widget_badge)
    private fun nativeIssues(view: View): List<String> = buildList {
        val background=view.findViewById<View>(android.R.id.background)
        if (background==null || !background.clipToOutline) add("Missing explicit launcher rounding contract") else {
            val outline=Outline()
            background.outlineProvider.getOutline(background,outline)
            if (!outline.canClip()) add("Background must have a real clipping outline")
        }
        nativeIds.forEach { id ->
            val text = view.findViewById<TextView>(id) ?: return@forEach
            if (text.visibility != View.VISIBLE) return@forEach
            val width = text.width - text.compoundPaddingLeft - text.compoundPaddingRight
            val height = text.height - text.compoundPaddingTop - text.compoundPaddingBottom
            if (width <= 0 || height <= 0) add("No space for ${text.text}")
            val layout = text.layout
            if (layout == null) add("No native text layout for ${text.text}") else {
                if (layout.height > height) add("Text height ${layout.height} > $height: ${text.text}")
                if ((0 until layout.lineCount).any { layout.getEllipsisCount(it) != 0 }) add("Truncated: ${text.text}")
            }
            val bounds = Rect(text.compoundPaddingLeft, text.compoundPaddingTop,
                text.width - text.compoundPaddingRight, text.height - text.compoundPaddingBottom)
            (view as ViewGroup).offsetDescendantRectToMyCoords(text, bounds)
            if (bounds.left < 0 || bounds.top < 0 || bounds.right > view.width || bounds.bottom > view.height) add("Outside widget: ${text.text}, $bounds")
        }
        if (!view.findViewById<View>(R.id.widget_root).hasOnClickListeners()) add("Missing tap action")
    }
    private fun textReport(view: View) = JSONArray().apply {
        nativeIds.forEach { id ->
            val text = view.findViewById<TextView>(id) ?: return@forEach
            if (text.visibility == View.VISIBLE) put(JSONObject().put("text", text.text.toString()).put("fontPx", text.textSize))
        }
    }
    private fun measure(view: View, size: SizeF) {
        val density = context.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((size.width * density).toInt(), View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((size.height * density).toInt(), View.MeasureSpec.EXACTLY))
        view.layout(0, 0, view.measuredWidth, view.measuredHeight)
    }
    private fun capture(view: View, name: String) {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        save(bitmap, name)
    }
    private fun save(bitmap: Bitmap, name: String) {
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
