package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.os.Build
import android.view.inspector.WindowInspector
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ExamIntroductionInstrumentedTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Before fun prepareWindow(){ui.prepareTestWindow()}
    private fun capture(name:String) {
        ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        repeat(2) {
            val frame=CountDownLatch(1)
            instrumentation.runOnMainSync {
                val view=WindowInspector.getGlobalWindowViews().last {it.hasWindowFocus()}
                view.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};view.invalidate()
            }
            assertTrue(frame.await(3,TimeUnit.SECONDS))
        }
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"exam-introduction/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try{file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
    }
    @Test fun bothPartsCanBeStartedAfterReadingTheWholeIntroductionWithoutRecordingAnAnswer() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        val model=AppModel()
        fun part(index:Int)=Session("Examen fictif",MutableList(40){Question("fixture-$it","Question",listOf("A","B"),0,"")},exam=true).apply {examPart=index}
        var current by mutableStateOf(part(0))
        ui.setContent {HamigoTheme {Box(Modifier.fillMaxSize().safeDrawingPadding()){ExamIntroduction(model,current)}}}
        repeat(2) {index->
            ui.runOnIdle {current=part(index);model.session=current}
            val label=if(index==0)"la réglementation" else "la technique"
            ui.onNodeWithText("Commencer $label").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Revenir aux défis").performScrollTo().assertIsDisplayed()
            ui.onNodeWithText("Commencer $label").performScrollTo()
            capture("part-$index-start-visible")
            val before=System.currentTimeMillis()
            ui.onNodeWithText("Commencer $label").performClick()
            ui.runOnIdle {
                assertFalse(current.examIntroPending)
                assertTrue(current.started>=before)
                assertTrue(current.responses.isEmpty())
                assertEquals(0,current.gain)
                assertEquals(0,current.correct)
            }
        }
    }
}
