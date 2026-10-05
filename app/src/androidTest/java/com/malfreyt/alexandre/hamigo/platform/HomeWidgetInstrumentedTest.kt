package com.malfreyt.alexandre.hamigo.platform

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.util.SizeF
import android.view.View
import android.widget.FrameLayout
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
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

@RunWith(AndroidJUnit4::class)
class HomeWidgetInstrumentedTest {
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val providers=listOf(StreakWidgetProvider::class.java,GoalWidgetProvider::class.java,WeekWidgetProvider::class.java)

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
        val date=LocalDate.now()
        val reminder=ReminderContent.build(ReminderState(18,30,setOf(date.toString(),date.minusDays(1).toString())),date)
        val monday=date.minusDays((date.dayOfWeek.value-1).toLong())
        val snapshot=WidgetSnapshot(reminder,(0L..6L).map { WidgetDay(monday.plusDays(it),if(monday.plusDays(it).isAfter(date))0 else 18) })
        val sizes=listOf(SizeF(120f,110f),SizeF(130f,220f),SizeF(220f,170f),SizeF(350f,220f),SizeF(450f,300f))
        HomeWidgetKind.entries.forEach { kind -> sizes.forEach { size ->
            instrumentation.runOnMainSync {
                val view=HomeWidgets.views(context,kind,snapshot,size).apply(context,FrameLayout(context))
                measure(view,size)
                val metric=view.findViewById<TextView>(R.id.widget_metric)
                assertTrue("The native metric must remain visible at $kind $size",metric.height>0 && metric.width>0)
                assertTrue(metric.text.isNotEmpty())
                assertEquals("The metric must not be truncated at $kind $size",0,metric.layout.getEllipsisCount(0))
                assertTrue("Metric must fit inside widget",metric.right<=view.width)
                val action=view.findViewById<TextView>(R.id.widget_review)
                assertTrue("Review action must fit vertically at $kind $size",action.bottom<=view.height-8)
                assertTrue(view.findViewById<View>(R.id.widget_root).hasOnClickListeners())
                capture(view,"${kind.name.lowercase()}-${size.width.toInt()}x${size.height.toInt()}")
            }
        } }
    }

    @Test fun realWidgetHostAcceptsResponsiveViewsAndResizesAllThreeProviders() {
        // Emulator receives appwidget grantbind before this test; no widget is added to the user's launcher.
        val manager=AppWidgetManager.getInstance(context)
        val host=AppWidgetHost(context,2822)
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.BIND_APPWIDGET)
        try {
            providers.forEachIndexed { index,provider ->
                val id=host.allocateAppWidgetId()
                val options=Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH,120)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT,110)
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
                    listOf(SizeF(120f,110f),SizeF(350f,220f),SizeF(130f,220f)).forEach { size ->
                        view.updateAppWidgetSize(Bundle(),listOf(size))
                        view.updateAppWidget(HomeWidgets.responsiveViews(context,HomeWidgetKind.entries[index],WidgetSnapshot.read(context),options))
                        measure(view,size)
                        assertNotNull("Responsive RemoteViews must inflate in a real host",view.findViewById<View>(R.id.widget_metric))
                    }
                }
                host.deleteAppWidgetId(id)
            }
        } finally { host.deleteHost();instrumentation.uiAutomation.dropShellPermissionIdentity();HomeWidgets.refresh(context) }
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
        File(directory,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }
}
