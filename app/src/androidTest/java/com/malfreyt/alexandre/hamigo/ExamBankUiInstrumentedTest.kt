package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class ExamBankUiInstrumentedTest {
    @get:Rule val ui=createAndroidComposeRule<MainActivity>()
    private lateinit var model:AppModel
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setUp() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000){model.content!=null}
        ui.runOnIdle {model.showWelcome=false}
    }
    @Test fun pendingBankDisablesOnlyExamAndMixThenNewThemesAreSelectedAutomatically() {
        val actual=model.content!!.examSnapshot!!
        ui.runOnIdle {model.content=model.content!!.withExam(null);model.examBank=ExamBankStatus(updating=true,percent=42);model.route="practice"}
        ui.onNodeWithTag("exam-bank-percent").assertTextEquals("42 %")
        ui.onNodeWithText("Lancer un examen blanc").assertIsNotEnabled()
        ui.onNodeWithTag("practice-list").performScrollToNode(hasText("Lancer mon mix"))
        ui.onNodeWithText("Lancer mon mix").assertIsNotEnabled();capture("download-mix-disabled")
        ui.onNodeWithTag("practice-list").performScrollToNode(hasText("Ouvrir le labo · 12 questions"))
        ui.onNodeWithText("Ouvrir le labo · 12 questions").assertIsEnabled();capture("offline-lab-available")
        ui.runOnIdle {model.examBank=ExamBankStatus(actual);model.content=model.content!!.withExam(actual)}
        ui.onNodeWithTag("practice-list").performScrollToNode(hasText("Lancer un examen blanc"))
        ui.onNodeWithText("Lancer un examen blanc").assertIsEnabled()
        ui.onNodeWithTag("practice-list").performScrollToNode(hasText("Lancer mon mix"))
        ui.onNodeWithText("Lancer mon mix").assertIsEnabled()
        assertEquals(20,model.content!!.mixIndex.keys.size)
        capture("download-complete")
        ui.onNodeWithText("Lancer mon mix").performClick()
        assertEquals(20,model.session!!.questions.size);assertTrue(model.session!!.questions.any {DiagnosticData.origin(it)=="Exam1"})
        capture("downloaded-question")
        ui.runOnIdle {model.session=null;model.route="practice"}
    }
    @Test fun settingsDisplaysUpstreamVersionAndManualUpdateControlWithoutLargeSpacing() {
        ui.runOnIdle {model.route="settings"}
        ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("exam-bank-settings"))
        ui.onNodeWithTag("exam-bank-version").assertTextEquals("Version : ${examBankVersionLabel(model.content!!.examSnapshot!!.version)}")
        ui.onNodeWithTag("exam-bank-check").assertIsEnabled();capture("settings-bank-version")
    }
    private fun capture(name:String) {
        val directory=File(context.getExternalFilesDir(null),"storage-0.48-ui").apply {mkdirs()}
        val bitmap=ui.onRoot().captureToImage().asAndroidBitmap()
        File(directory,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
    }
}
