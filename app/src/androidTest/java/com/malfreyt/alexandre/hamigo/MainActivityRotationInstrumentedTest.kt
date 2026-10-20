package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Actual MainActivity recreation with a stale launch route, on an isolated emulator only. */
@RunWith(AndroidJUnit4::class)
class MainActivityRotationInstrumentedTest {
    @get:Rule val ui=createEmptyComposeRule()
    private val instrumentation get()=InstrumentationRegistry.getInstrumentation()
    private val context get()=instrumentation.targetContext
    private val saved=linkedMapOf<String,Map<String,Any?>>()
    private var scenario:ActivityScenario<MainActivity>?=null

    @Before fun isolateEmulatorPreferences() {
        check(Build.HARDWARE in listOf("ranchu","goldfish")) {"Emulator-only fixture"}
        // Back up values in RAM only. Never export accounts or credentials into test output.
        for(name in listOf("hamigo","hamigo_social","hamigo_secure","hamigo_pending_oauth","hamigo_diagnostics")) {
            val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
            saved[name]=prefs.all.toMap()
            check(prefs.edit().clear().commit())
        }
        check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit()
            .putBoolean("welcomed",true).putBoolean("autoSync",false).putBoolean("reminderEnabled",false)
            .putString("name","Rotation fictive").putInt("hamigoTourStep",-1).putInt("hamigoTourVersion",2).commit())
        ProgressSyncScheduler.cancel(context)
    }
    @After fun restoreEmulatorPreferences() {
        scenario?.close();scenario=null
        saved.forEach {(name,values)->
            val edit=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear()
            values.forEach {(key,value)->when(value) {
                is String->edit.putString(key,value)
                is Boolean->edit.putBoolean(key,value)
                is Int->edit.putInt(key,value)
                is Long->edit.putLong(key,value)
                is Float->edit.putFloat(key,value)
                is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())
            }}
            check(edit.commit())
        }
        DailyReminder.schedule(context)
        ProgressSyncScheduler.schedule(context)
    }
    private fun capture(name:String) {
        ui.awaitForegroundFrames()
        val file=File(context.getExternalFilesDir(null),"rotation-state/"+name+".png")
        file.parentFile!!.mkdirs()
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {"No screenshot returned for foreground window"}
        try {file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
    @Test fun recreationKeepsTheMemoFicheAndOpenCalculatorButANewRouteIntentStillWorks() {
        val launch=Intent(context,MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK).putExtra("hamigo_route","path")
        val active=ActivityScenario.launch<MainActivity>(launch).also {scenario=it}
        lateinit var model:AppModel
        active.onActivity {model=ViewModelProvider(it)[AppModel::class.java]}
        ui.waitUntil(60_000){model.content!=null}
        ui.prepareTestWindow()
        lateinit var fiche:RefCategory
        lateinit var tools:LearningToolsState
        active.onActivity {
            model.tour.finish();model.showWelcome=false;model.navigateTab("resources")
            fiche=model.content!!.references.first {it.id=="units"}
            model.resource=fiche
            tools=LearningToolsNavigation.fiche(model,fiche.id)
        }
        val before=model.progress.export()
        val ids=model.content!!.lessons.map {it.id}
        ui.onNodeWithTag("learning-tool-calculator").performClick()
        listOf("2","+","3","=").forEach {key->ui.onNodeWithTag("calculator-key-"+key).performScrollTo().performClick()}
        ui.onNodeWithTag("calculator-result").assertTextEquals("5")
        active.onActivity {assertTrue(tools.calculatorOpen);assertEquals("2+3",tools.calculator.input.value.text)}
        capture("memo-calculator-before-recreate")
        var targetOrientation=android.content.res.Configuration.ORIENTATION_UNDEFINED
        active.onActivity {activity->
            targetOrientation=if(activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                android.content.res.Configuration.ORIENTATION_PORTRAIT else android.content.res.Configuration.ORIENTATION_LANDSCAPE
            activity.requestedOrientation=if(targetOrientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        ui.waitUntil(20_000) {
            var ready=false
            active.onActivity {ready=it.resources.configuration.orientation==targetOrientation}
            ready
        }
        ui.awaitForegroundFrames()
        active.onActivity {activity->
            assertSame(model,ViewModelProvider(activity)[AppModel::class.java])
            assertEquals("resources",model.route);assertEquals(fiche.id,model.resource?.id)
            assertTrue(tools.calculatorOpen);assertEquals("2+3",tools.calculator.input.value.text)
            assertEquals(before,model.progress.export())
        }
        ui.onNodeWithTag("calculator-result").assertTextEquals("5")
        capture("memo-calculator-after-rotation")
        active.recreate()
        active.onActivity {activity->
            val retained=ViewModelProvider(activity)[AppModel::class.java]
            assertSame("Configuration changes must retain the same learning model",model,retained)
            assertEquals("resources",retained.route)
            assertEquals(fiche.id,retained.resource?.id)
            assertTrue(LearningToolsNavigation.fiche(retained,fiche.id).calculatorOpen)
            assertEquals("2+3",LearningToolsNavigation.fiche(retained,fiche.id).calculator.input.value.text)
            assertEquals(before,retained.progress.export())
            assertEquals(ids,retained.content!!.lessons.map {it.id})
        }
        ui.onNodeWithTag("calculator-surface").assertIsDisplayed()
        ui.onNodeWithTag("calculator-result").assertTextEquals("5")
        capture("memo-calculator-after-recreate")
        ui.onNodeWithContentDescription("Fermer la calculatrice").performClick()
        ui.waitUntil(10_000){ui.onAllNodesWithTag("calculator-surface").fetchSemanticsNodes().isEmpty()}
        active.onActivity {model.resource=null}
        // A fresh widget/reminder request is still delivered through onNewIntent after the fix.
        context.startActivity(Intent(context,MainActivity::class.java).setAction(Intent.ACTION_MAIN)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("hamigo_route","friends"))
        ui.waitUntil(10_000){model.route=="friends"}
        ui.onNodeWithTag("friends-list").assertIsDisplayed()
        active.onActivity {assertSame(model,ViewModelProvider(it)[AppModel::class.java]);assertEquals(before,model.progress.export())}
    }
}
