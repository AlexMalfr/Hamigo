package com.malfreyt.alexandre.hamigo.platform

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.R
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class WidgetConfigurationInstrumentedTest {
    @get:Rule val ui = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val manager get() = AppWidgetManager.getInstance(context)
    private lateinit var host: AppWidgetHost
    private val ids = mutableListOf<Int>()
    private val providers = listOf(StreakWidgetProvider::class.java, GoalWidgetProvider::class.java, WeekWidgetProvider::class.java)
    @Before fun setup() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish"))
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        host = AppWidgetHost(context, 2845)
    }
    @After fun cleanup() {
        WidgetSettings.delete(context, ids.toIntArray())
        host.deleteHost()
        instrumentation.uiAutomation.dropShellPermissionIdentity()
        HomeWidgets.refresh(context)
    }
    private fun options(size: SizeF) = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, size.width.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, size.width.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, size.height.toInt())
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, size.height.toInt())
        putParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, arrayListOf(size))
    }
    private fun bind(provider: Class<*>, size: SizeF = SizeF(170f,150f)): Int {
        val id = host.allocateAppWidgetId(); ids += id
        assertTrue(manager.bindAppWidgetIdIfAllowed(id, ComponentName(context, provider), options(size)))
        return id
    }
    private fun capture(name: String, activity: Activity) {
        repeat(2) {
            val latch = CountDownLatch(1)
            instrumentation.runOnMainSync {
                val view = activity.window.decorView
                view.viewTreeObserver.registerFrameCommitCallback { latch.countDown() }; view.invalidate()
            }
            assertTrue(latch.await(5, TimeUnit.SECONDS))
        }
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        val dir = File(context.getExternalFilesDir(null), "widgets-transparent").apply { mkdirs() }
        try { File(dir, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) } }
        finally { bitmap.recycle() }
    }

    @Test fun configurationSavesOnlyThisInstanceAndCancelKeepsThePreviousValue() {
        val id = bind(GoalWidgetProvider::class.java); val other = bind(GoalWidgetProvider::class.java)
        assertFalse(WidgetSettings.transparent(context,id)); assertFalse(WidgetSettings.transparent(context,other))
        val info = manager.getAppWidgetInfo(id)
        assertEquals(ComponentName(context,WidgetConfigurationActivity::class.java),info.configure)
        assertTrue(info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0)
        assertTrue(info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0)
        fun intent() = Intent(context,WidgetConfigurationActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id)
        ActivityScenario.launchActivityForResult<WidgetConfigurationActivity>(intent()).use { scenario ->
            ui.onNodeWithTag("widget-transparent").performScrollTo().assertIsOff().performClick().assertIsOn()
            var activity: Activity? = null; scenario.onActivity { activity = it }
            capture("configuration-transparent",activity!!)
            ui.onNodeWithTag("widget-light-text").performScrollTo().assertIsOn().performClick().assertIsOff()
            capture("configuration-transparent-dark-text",activity!!)
            ui.onNodeWithTag("widget-config-save").performClick()
            assertEquals(Activity.RESULT_OK,scenario.result.resultCode)
            assertEquals(id,scenario.result.resultData.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,-1))
        }
        assertTrue(WidgetSettings.transparent(context,id)); assertFalse(WidgetSettings.transparent(context,other))
        assertFalse(WidgetSettings.lightText(context,id));assertTrue(WidgetSettings.lightText(context,other))
        ActivityScenario.launchActivityForResult<WidgetConfigurationActivity>(intent()).use { scenario ->
            ui.onNodeWithTag("widget-transparent").performScrollTo().assertIsOn().performClick()
            ui.onNodeWithContentDescription("Annuler").performClick()
            assertEquals(Activity.RESULT_CANCELED,scenario.result.resultCode)
        }
        assertTrue(WidgetSettings.transparent(context,id))
        assertFalse(WidgetSettings.lightText(context,id))
        GoalWidgetProvider().onDeleted(context,intArrayOf(id))
        assertFalse(WidgetSettings.transparent(context,id))
        assertTrue(WidgetSettings.lightText(context,id))
        ActivityScenario.launchActivityForResult<WidgetConfigurationActivity>(Intent(context,WidgetConfigurationActivity::class.java)).use {
            assertEquals(Activity.RESULT_CANCELED,it.result.resultCode)
        }
    }

    @Test fun allThreeModelsKeepTheWallpaperVisibleInARealHostAtTwoSizes() {
        val date = LocalDate.of(2026,10,9)
        val reminder = ReminderContent.build(ReminderState(80,60,(0L..5L).map {date.minusDays(it).toString()}.toSet()),date)
        val monday = date.minusDays((date.dayOfWeek.value-1).toLong())
        val snapshot = WidgetSnapshot(reminder,(0L..6L).map {WidgetDay(monday.plusDays(it),listOf(60,24,88,18,80,0,0)[it.toInt()])})
        val pairs = providers.map { bind(it) to bind(it) }
        pairs.forEach { WidgetSettings.setTransparent(context,it.second,true) }
        ActivityScenario.launch(WidgetVisualHostActivity::class.java).use { scenario ->
            for (lightText in listOf(true,false)) for (size in listOf(SizeF(170f,150f),SizeF(80f,180f))) {
                pairs.forEach {WidgetSettings.save(context,it.second,true,lightText)}
                val wallpaper=if(lightText)Color.rgb(83,99,109) else Color.rgb(238,234,226)
                var activity: Activity? = null
                val regions = mutableListOf<Pair<IntArray,Boolean>>()
                scenario.onActivity { a ->
                    activity = a
                    val density = a.resources.displayMetrics.density
                    fun dp(v: Float) = (v*density).roundToInt()
                    val board = LinearLayout(a).apply {
                        orientation = LinearLayout.VERTICAL; setBackgroundColor(wallpaper)
                        setPadding(dp(16f),dp(36f),dp(16f),dp(16f))
                    }
                    pairs.forEachIndexed { index,pair ->
                        board.addView(TextView(a).apply {text = "${HomeWidgetKind.entries[index]} · fond / transparent";setTextColor(Color.WHITE);textSize=12f})
                        val row = LinearLayout(a)
                        listOf(pair.first,pair.second).forEachIndexed { column,id ->
                            manager.updateAppWidgetOptions(id,options(size))
                            val view = host.createView(a,id,manager.getAppWidgetInfo(id)).apply { setPadding(0,0,0,0) }
                            view.updateAppWidget(HomeWidgets.responsiveViews(a,HomeWidgetKind.entries[index],snapshot,options(size),id))
                            row.addView(view,LinearLayout.LayoutParams(dp(size.width),dp(size.height)).apply { marginEnd=dp(12f) })
                            view.post {val point=IntArray(2);view.getLocationOnScreen(point);point[0]+=view.width/2;point[1]+=dp(2f);regions+=point to (column==1)}
                        }
                        board.addView(row,LinearLayout.LayoutParams(-1,-2).apply {bottomMargin=dp(12f)})
                    }
                    a.setContentView(board)
                }
                instrumentation.waitForIdleSync(); capture("host-${size.width.toInt()}x${size.height.toInt()}-${if(lightText)"light" else "dark"}-text",activity!!)
                val bitmap = instrumentation.uiAutomation.takeScreenshot()
                try {
                    assertEquals(6,regions.size)
                    regions.forEach { (p,transparent) ->
                        val pixel = bitmap.getPixel(p[0],p[1])
                        if(transparent) assertEquals("Host wallpaper must show through, not a solid app tile",wallpaper,pixel)
                        else assertNotEquals(wallpaper,pixel)
                    }
                } finally { bitmap.recycle() }
                // Rendering outside the host also verifies actual alpha and unchanged labels/layout.
                instrumentation.runOnMainSync {
                    HomeWidgetKind.entries.forEach {kind ->
                        val opaque = WidgetPresentation.views(context,kind,snapshot,size).apply(context,FrameLayout(context))
                        val clear = WidgetPresentation.views(context,kind,snapshot,size,transparent=true,lightText=lightText).apply(context,FrameLayout(context))
                        fun pixels(view: View): Bitmap {
                            val d=context.resources.displayMetrics.density
                            view.measure(View.MeasureSpec.makeMeasureSpec((size.width*d).toInt(),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec((size.height*d).toInt(),View.MeasureSpec.EXACTLY))
                            view.layout(0,0,view.measuredWidth,view.measuredHeight)
                            return Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888).also {view.draw(Canvas(it))}
                        }
                        val one=pixels(opaque);val two=pixels(clear)
                        try {assertEquals(255,Color.alpha(one.getPixel(one.width/2,2)));assertEquals(0,Color.alpha(two.getPixel(two.width/2,2)))}
                        finally {one.recycle();two.recycle()}
                        assertEquals(opaque.findViewById<TextView>(R.id.widget_metric).text,clear.findViewById<TextView>(R.id.widget_metric).text)
                        assertEquals(if(lightText)Color.WHITE else Color.rgb(7,61,64),clear.findViewById<TextView>(R.id.widget_metric).currentTextColor)
                    }
                }
            }
        }
    }
}
