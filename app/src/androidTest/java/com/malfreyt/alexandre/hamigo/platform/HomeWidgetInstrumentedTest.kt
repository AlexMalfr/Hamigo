package com.malfreyt.alexandre.hamigo.platform

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.util.SizeF
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.Progress
import com.malfreyt.alexandre.hamigo.R
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class HomeWidgetInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val providers=listOf(StreakWidgetProvider::class.java,GoalWidgetProvider::class.java,WeekWidgetProvider::class.java)

    @Test fun widgetsKeepTheirRoundedBackgroundAndTextsInAHardwareWindow() {
        val manager=AppWidgetManager.getInstance(context)
        val host=AppWidgetHost(context,2823)
        val scenario=ActivityScenario.launch(WidgetVisualHostActivity::class.java)
        val date=LocalDate.of(2026,10,8)
        val r=ReminderContent.build(ReminderState(18,30,(0L..4L).map { date.minusDays(it).toString() }.toSet()),date)
        val monday=date.minusDays((date.dayOfWeek.value-1).toLong())
        val snapshot=WidgetSnapshot(r,(0L..6L).map { WidgetDay(monday.plusDays(it),listOf(30,12,48,18,0,0,0)[it.toInt()]) })
        val views=mutableListOf<View>()
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        try {
            scenario.onActivity { activity ->
                val density=context.resources.displayMetrics.density
                fun dp(value: Int)=(value*density).roundToInt()
                val board=LinearLayout(activity).apply {
                    orientation=LinearLayout.VERTICAL
                    setBackgroundColor(Color.rgb(48,62,74)); setPadding(dp(8),dp(8),dp(8),dp(8))
                }
                board.setOnApplyWindowInsetsListener { view,insets ->
                    val bars=insets.getInsets(android.view.WindowInsets.Type.systemBars())
                    view.setPadding(dp(8),dp(8)+bars.top,dp(8),dp(8)+bars.bottom)
                    insets
                }
                listOf(SizeF(40f,40f),SizeF(84f,91f),SizeF(120f,170f),SizeF(120f,260f)).forEach { size ->
                    board.addView(TextView(activity).apply {
                        text="${size.width.toInt()} × ${size.height.toInt()} dp"; textSize=12f
                        setTextColor(Color.WHITE); setPadding(0,dp(5),0,dp(5))
                    })
                    val row=LinearLayout(activity).apply { orientation=LinearLayout.HORIZONTAL }
                    providers.forEachIndexed { index,provider ->
                        val id=host.allocateAppWidgetId()
                        val options=Bundle().apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(size)) }
                        assertTrue(manager.bindAppWidgetIdIfAllowed(id,ComponentName(context,provider),options))
                        val view=host.createView(activity,id,manager.getAppWidgetInfo(id)).apply { setPadding(0,0,0,0) }
                        view.updateAppWidget(HomeWidgets.responsiveViews(activity,HomeWidgetKind.entries[index],snapshot,options))
                        val params=LinearLayout.LayoutParams(dp(size.width.toInt()),dp(size.height.toInt())).apply { marginEnd=dp(8) }
                        row.addView(view,params); views+=view
                    }
                    board.addView(row)
                }
                activity.setContentView(board)
                board.requestApplyInsets()
            }
            instrumentation.waitForIdleSync()
            val committed=CountDownLatch(1)
            scenario.onActivity {
                views.forEach { view ->
                    assertTrue(view.isAttachedToWindow)
                    assertTrue("Screenshot must exercise hardware clipping",view.isHardwareAccelerated)
                    val background=view.findViewById<View>(android.R.id.background)
                    assertTrue(background.clipToOutline)
                    val outline=android.graphics.Outline()
                    background.outlineProvider.getOutline(background,outline)
                    assertTrue("Transparent background still needs a real round outline",outline.canClip())
                }
                val content=it.findViewById<View>(android.R.id.content)
                content.viewTreeObserver.registerFrameCommitCallback { committed.countDown() }
                content.invalidate()
            }
            assertTrue("The hardware frame must be submitted before capture",committed.await(5,TimeUnit.SECONDS))
            val regions=mutableListOf<android.graphics.Rect>()
            scenario.onActivity {
                views.forEach { view ->
                    val location=IntArray(2); view.getLocationOnScreen(location)
                    regions+=android.graphics.Rect(location[0],location[1],location[0]+view.width,location[1]+view.height)
                }
            }
            fun widgetsArePainted(bitmap: Bitmap): Boolean = regions.withIndex().all { (index,rect) ->
                if (rect.right>bitmap.width || rect.bottom>bitmap.height) return@all false
                listOf(.25f,.50f,.75f).any { fx -> listOf(.25f,.50f,.75f).any { fy ->
                    val color=bitmap.getPixel(rect.left+(rect.width()*fx).toInt(),rect.top+(rect.height()*fy).toInt())
                    val red=Color.red(color); val green=Color.green(color); val blue=Color.blue(color)
                    when(index%3) {
                        0 -> red>=220 && green>=130 && blue<230
                        1 -> red>=150 && green>=180 && blue>=170 && green>red
                        else -> red<40 && green>35 && blue>35
                    }
                } }
            }
            var bitmap=instrumentation.uiAutomation.takeScreenshot()
            val deadline=android.os.SystemClock.uptimeMillis()+5000
            while (!widgetsArePainted(bitmap) && android.os.SystemClock.uptimeMillis()<deadline) {
                Thread.sleep(100)
                bitmap=instrumentation.uiAutomation.takeScreenshot()
            }
            assertTrue("The presented screenshot must contain every widget's coloured surface",widgetsArePainted(bitmap))
            val suffix=if(context.resources.configuration.fontScale>1.01f)"-font${context.resources.configuration.fontScale}" else ""
            val directory=File(context.getExternalFilesDir(null),"widgets-audit").apply { mkdirs() }
            File(directory,"hardware-gallery$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        } finally { scenario.close();host.deleteHost();instrumentation.uiAutomation.dropShellPermissionIdentity();HomeWidgets.refresh(context) }
    }

    @Test fun threeDifferentWidgetsAreRegisteredAndResizableInBothDirections() {
        val manager=AppWidgetManager.getInstance(context)
        val infos=providers.map { provider -> manager.installedProviders.first { it.provider==ComponentName(context,provider) } }
        assertEquals(3,infos.map { it.loadLabel(context.packageManager) }.toSet().size)
        infos.forEach { info ->
            assertEquals(AppWidgetProviderInfo.RESIZE_BOTH,info.resizeMode)
            assertTrue(info.minResizeWidth>0 && info.minResizeHeight>0)
            assertEquals(30*60*1000,info.updatePeriodMillis)
        }
    }

    @Test fun snapshotUsesTheCalendarWeekAndFiltersFutureDaysWithoutLoadingCourses() {
        val namespace="widgets-test-${UUID.randomUUID()}"
        val isolated=object : android.content.ContextWrapper(context) {
            override fun getApplicationContext(): Context=this
            override fun getSharedPreferences(name: String,mode: Int)=baseContext.getSharedPreferences("$namespace-$name",mode)
        }
        val prefs=isolated.getSharedPreferences("hamigo",Context.MODE_PRIVATE)
        val date=LocalDate.of(2026,10,7) // Wednesday.
        try {
            prefs.edit().putInt("dailyGoal",50).putString("progress",JSONObject().put("dailyXp",JSONObject()
                .put(date.minusDays(2).toString(),20).put(date.toString(),30).put(date.plusDays(1).toString(),100)).toString()).commit()
            val snapshot=WidgetSnapshot.read(isolated,date)
            assertEquals(date.minusDays(2),snapshot.week.first().date)
            assertEquals(50,snapshot.weeklyXp)
            assertEquals(2,snapshot.activeDays)
            assertEquals(30,snapshot.reminder.todayXp)
            assertEquals(50,snapshot.reminder.goal)
            assertEquals(0,snapshot.week[3].xp)
        } finally { prefs.edit().clear().commit() }
    }

    @Test fun everyDesignRendersAtCompactNarrowWideAndLargeSizesWithReadableNativeMetrics() {
        val date=LocalDate.of(2026,10,10)
        val reminder=ReminderContent.build(ReminderState(18,30,setOf(date.toString(),date.minusDays(1).toString())),date)
        val monday=date.minusDays((date.dayOfWeek.value-1).toLong())
        val snapshot=WidgetSnapshot(reminder,(0L..6L).map { WidgetDay(monday.plusDays(it),if(monday.plusDays(it).isAfter(date))0 else listOf(30,12,40,8,30,18,0)[it.toInt()]) })
        val sizes=listOf(SizeF(120f,110f),SizeF(130f,170f),SizeF(130f,180f),SizeF(130f,220f),SizeF(200f,160f),SizeF(220f,110f),SizeF(220f,159f),SizeF(220f,170f),SizeF(350f,110f),SizeF(350f,220f),SizeF(450f,300f))
        HomeWidgetKind.entries.forEach { kind -> sizes.forEach { size ->
            instrumentation.runOnMainSync {
                val view=HomeWidgets.views(context,kind,snapshot,size).apply(context,FrameLayout(context))
                measure(view,size)
                assertNativeContentFits(view,kind,size)
                assertTrue(view.findViewById<View>(R.id.widget_root).hasOnClickListeners())
                capture(view,"${kind.name.lowercase()}-${size.width.toInt()}x${size.height.toInt()}")
            }
        } }
    }

    @Test fun largeCountsAndGoalStayLegibleAtTheMinimumSizeAndTallGoalHasOnlyOneGauge() {
        val date=LocalDate.of(2026,10,10)
        val reminder=ReminderContent.build(ReminderState(12345,1000,setOf(date.toString())),date).copy(streak=365)
        val monday=date.minusDays((date.dayOfWeek.value-1).toLong())
        val snapshot=WidgetSnapshot(reminder,(0L..6L).map { WidgetDay(monday.plusDays(it),if(it<6)12345 else 0) })
        instrumentation.runOnMainSync {
            HomeWidgetKind.entries.forEach { kind ->
                val size=SizeF(120f,110f)
                val view=HomeWidgets.views(context,kind,snapshot,size).apply(context,FrameLayout(context))
                measure(view,size)
                assertNativeContentFits(view,kind,size)
                capture(view,"${kind.name.lowercase()}-large-count-120x110")
            }
            val size=SizeF(130f,220f)
            val view=HomeWidgets.views(context,HomeWidgetKind.GOAL,snapshot,size).apply(context,FrameLayout(context))
            measure(view,size)
            assertEquals(View.VISIBLE,view.findViewById<View>(R.id.widget_art).visibility)
            assertEquals("The goal ring replaces the compact linear gauge",View.GONE,view.findViewById<View>(R.id.widget_progress).visibility)
            assertEquals("Pico already lives inside the goal ring",View.GONE,view.findViewById<View>(R.id.widget_mascot).visibility)
        }
    }

    @Test fun launcherPreviewsFitTheirDefaultSizesWithoutFlatteningTheGoalRing() {
        val previews=listOf(
            Triple(HomeWidgetKind.STREAK,R.layout.widget_streak_preview,SizeF(130f,130f)),
            Triple(HomeWidgetKind.GOAL,R.layout.widget_goal_preview,SizeF(220f,150f)),
            Triple(HomeWidgetKind.WEEK,R.layout.widget_week_preview,SizeF(280f,180f)),
        )
        instrumentation.runOnMainSync {
            previews.forEach { (kind,layout,size) ->
                val view=LayoutInflater.from(context).inflate(layout,FrameLayout(context),false)
                measure(view,size)
                assertNativeContentFits(view,kind,size)
                val art=view.findViewById<View>(R.id.widget_art)
                assertTrue("Launcher preview needs a visible illustration",art.height>0)
                if(kind==HomeWidgetKind.GOAL)assertTrue("Goal preview keeps room for a round ring",art.height>=art.width)
                capture(view,"preview-${kind.name.lowercase()}")
            }
        }
    }

    @Test fun realWidgetHostAcceptsResponsiveViewsAndResizesAllThreeProviders() {
        // Binding permission belongs only to this isolated test host, never to the user's launcher.
        val manager=AppWidgetManager.getInstance(context)
        val host=AppWidgetHost(context,2822)
        val date=LocalDate.of(2026,10,8)
        val reminder=ReminderContent.build(ReminderState(18,30,(0L..4L).map { date.minusDays(it).toString() }.toSet()),date)
        val monday=date.minusDays((date.dayOfWeek.value-1).toLong())
        val snapshot=WidgetSnapshot(reminder,(0L..6L).map { WidgetDay(monday.plusDays(it),listOf(30,12,48,18,0,0,0)[it.toInt()]) })
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        try {
            providers.forEachIndexed { index,provider ->
                val id=host.allocateAppWidgetId()
                val options=Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,40)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,40)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,350)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,220)
                    putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(SizeF(120f,110f),SizeF(350f,220f)))
                }
                assertTrue("Test host needs emulator-only widget binding permission",manager.bindAppWidgetIdIfAllowed(id,ComponentName(context,provider),options))
                val info=manager.getAppWidgetInfo(id)
                assertNotNull(info)
                instrumentation.runOnMainSync {
                    val view=host.createView(context,id,info)
                    view.setPadding(0,0,0,0)
                    listOf(SizeF(40f,40f),SizeF(60f,60f),SizeF(84f,91f),SizeF(100f,60f),SizeF(60f,180f),SizeF(96f,120f),
                        SizeF(120f,110f),SizeF(130f,130f),SizeF(220f,150f),SizeF(280f,180f),SizeF(350f,110f),SizeF(350f,220f),
                        SizeF(130f,220f),SizeF(450f,300f),SizeF(40f,800f),SizeF(800f,40f),SizeF(137f,59f),SizeF(91f,333f)).forEach { size ->
                        view.updateAppWidgetSize(Bundle(),listOf(size))
                        val resizedOptions=Bundle(options).apply {
                            putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,arrayListOf(size))
                        }
                        view.updateAppWidget(HomeWidgets.responsiveViews(context,HomeWidgetKind.entries[index],snapshot,resizedOptions))
                        measure(view,size)
                        assertNotNull("Responsive RemoteViews must inflate in a real host",view.findViewById<View>(R.id.widget_metric))
                        assertNativeContentFits(view,HomeWidgetKind.entries[index],size)
                        capture(view,"host-${HomeWidgetKind.entries[index].name.lowercase()}-${size.width.toInt()}x${size.height.toInt()}")
                    }
                    val hostSizes=listOf(SizeF(84f,91f),SizeF(300f,72f),SizeF(72f,300f),SizeF(600f,600f))
                    val multipleOptions=Bundle(options).apply { putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,ArrayList(hostSizes)) }
                    val response=HomeWidgets.responsiveViews(context,HomeWidgetKind.entries[index],snapshot,multipleOptions)
                    manager.updateAppWidget(id,response) // Real framework IPC and bitmap budget check.
                    hostSizes.forEach { size ->
                        view.updateAppWidgetSize(Bundle(),listOf(size))
                        view.updateAppWidget(response)
                        measure(view,size)
                        assertNativeContentFits(view,HomeWidgetKind.entries[index],size)
                        val expected=WidgetPresentation.composition(HomeWidgetKind.entries[index],snapshot,size).texts.first { it.id==R.id.widget_metric }.bounds
                        val metric=view.findViewById<TextView>(R.id.widget_metric)
                        assertEquals("Host must select its exact composition",(expected.left*context.resources.displayMetrics.density).roundToInt(),metric.paddingLeft)
                        assertEquals("Host must select its exact composition",(expected.top*context.resources.displayMetrics.density).roundToInt(),metric.paddingTop)
                        capture(view,"host-multiple-${HomeWidgetKind.entries[index].name.lowercase()}-${size.width.toInt()}x${size.height.toInt()}")
                    }
                    val fallback=Bundle().apply {
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,84)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,72)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH,300)
                        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT,300)
                    }
                    val fallbackResponse=HomeWidgets.responsiveViews(context,HomeWidgetKind.entries[index],snapshot,fallback)
                    listOf(SizeF(84f,300f),SizeF(300f,72f)).forEach { size ->
                        view.updateAppWidgetSize(Bundle(),listOf(size))
                        view.updateAppWidget(fallbackResponse)
                        measure(view,size)
                        assertNativeContentFits(view,HomeWidgetKind.entries[index],size)
                        val expected=WidgetPresentation.composition(HomeWidgetKind.entries[index],snapshot,size).texts.first { it.id==R.id.widget_metric }.bounds
                        assertEquals("Min/max fallback retains both orientations",(expected.top*context.resources.displayMetrics.density).roundToInt(),view.findViewById<TextView>(R.id.widget_metric).paddingTop)
                        capture(view,"host-fallback-${HomeWidgetKind.entries[index].name.lowercase()}-${size.width.toInt()}x${size.height.toInt()}")
                    }
                    val many=Bundle(options).apply {
                        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES,ArrayList((0..15).map { SizeF(600f+it*7,600f+it*11) }))
                    }
                    manager.updateAppWidget(id,HomeWidgets.responsiveViews(context,HomeWidgetKind.entries[index],snapshot,many))
                }
                host.deleteAppWidgetId(id)
            }
        } finally { host.deleteHost();instrumentation.uiAutomation.dropShellPermissionIdentity();HomeWidgets.refresh(context) }
    }

    private fun assertNativeContentFits(view: View,kind: HomeWidgetKind,size: SizeF) {
        listOf(R.id.widget_title,R.id.widget_metric,R.id.widget_status,R.id.widget_review,R.id.widget_badge).forEach { id ->
            val text=view.findViewById<TextView>(id)
            if(text.visibility==View.VISIBLE) {
                assertTrue("Native text must remain visible at $kind $size: ${text.text}",text.height>0 && text.width>0)
                assertTrue(text.text.isNotEmpty())
                val layout=text.layout
                assertNotNull(layout)
                (0 until layout.lineCount).forEach { line ->
                    assertEquals("Native text must not be truncated at $kind $size: ${text.text}",0,layout.getEllipsisCount(line))
                }
                assertTrue("Text lines must fit vertically at $kind $size: ${text.text}",layout.height<=text.height)
                val bounds=Rect(0,0,text.width,text.height)
                (view as ViewGroup).offsetDescendantRectToMyCoords(text,bounds)
                assertTrue("Native text ${context.resources.getResourceEntryName(id)} (${text.text}) must fit inside widget at $kind $size: $bounds in ${view.width}x${view.height}",bounds.left>=0 && bounds.top>=0 && bounds.right<=view.width && bounds.bottom<=view.height)
            }
        }
    }

    private fun measure(view: View,size: SizeF) {
        val density=context.resources.displayMetrics.density
        view.measure(View.MeasureSpec.makeMeasureSpec((size.width*density).toInt(),View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec((size.height*density).toInt(),View.MeasureSpec.EXACTLY))
        view.layout(0,0,view.measuredWidth,view.measuredHeight)
    }
    private fun capture(view: View,name: String) {
        val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(bitmap);canvas.drawColor(Color.rgb(72,84,98));view.draw(canvas)
        val directory=File(context.getExternalFilesDir(null),"widgets-audit").apply { mkdirs() }
        val font=context.resources.configuration.fontScale
        val suffix=if(font>1.01f)"-font$font" else ""
        File(directory,"$name$suffix.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
