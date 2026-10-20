package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.os.Build
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TimePicker
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Production pages and their real navigation, with a RAM-only learning/account fixture. */
@RunWith(AndroidJUnit4::class)
class PageStateRetentionInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var owner:AppModel
    private lateinit var model:AppModel
    private lateinit var content:Content
    private lateinit var restoration:StateRestorationTester
    private val questions=listOf("regulation","technique").flatMap {part->(0 until 60).map {index->
        Question("state-$part-$index","Quelle réponse convient pour cette situation $index ?",listOf("A","B","C","D"),0,
            "La correction de cette situation.",section=part,topic="${if(part=="regulation")"Règle" else "Circuit"} ${index%4}")
    }}

    @Before fun prepare() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        ui.prepareTestWindow()
        content=Content(context,ExamBankSnapshot("a".repeat(64),"state-retention-fixture",questions,emptySet()))
        ui.runOnUiThread {
            val memory=DiagnosticContext(context)
            owner=AppModel().apply {
                progress=Progress(memory,sideEffects=false)
                diagnostics=DiagnosticSettings(memory)
                interactionFeedback=AppFeedback(memory)
            }
            model=AppModel().apply {initializeDiagnostics(context,owner,this@PageStateRetentionInstrumentedTest.content)}
            model.showWelcome=false
            model.tour.finish()
        }
    }
    @After fun close()=ui.runOnUiThread {
        if(::model.isInitialized)model.disposeDiagnostics()
        if(::owner.isInitialized)owner.interactionFeedback.close()
    }
    private fun show(route:String) {
        ui.runOnUiThread {model.route=route}
        restoration=StateRestorationTester(ui)
        restoration.setContent {HamigoTheme {FeedbackHost(owner.interactionFeedback) {
            CompositionLocalProvider(LocalContext provides model.diagnosticContext,LocalDiagnosticsCovered provides false) {
                HamigoAppContent(model,model,diagnosticPreview=true)
            }
        }}}
    }
    private fun hideKeyboard() {
        ui.runOnUiThread {WindowInspector.getGlobalWindowViews().forEach {it.windowInsetsController?.hide(WindowInsets.Type.ime())}}
        ui.waitForIdle()
    }
    private fun tab(label:String)=ui.onNode(hasText(label) and hasClickAction()).performClick()

    @Test fun transcriptRestorationKeepsItsQuestionAndReturningKeepsTheHistoryPosition() {
        val attempts=ExamHistory.fixtures(questions)
        ui.runOnUiThread {attempts.forEach {model.progress.recordExam(it)};model.refresh()}
        val target=attempts.minBy {it.at}
        show("exam-history")
        val cardTag="exam-attempt-${target.id}"
        ui.onNodeWithTag("exam-history-list").performScrollToNode(hasTestTag(cardTag))
        val before=ui.onNodeWithTag(cardTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        ui.onNodeWithTag(cardTag).performClick()
        ui.onNodeWithTag("exam-transcript-list").performScrollToNode(hasTestTag("exam-response-27"))
        ui.onNodeWithTag("exam-response-27").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("exam-response-27").assertIsDisplayed()
        ui.onNodeWithContentDescription("Revenir à l’historique").performClick()
        val returned=ui.onNodeWithTag(cardTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals("Opening a transcript must not discard the history scroll.",before.top.value,returned.top.value,1.5f)
        ui.onNodeWithTag(cardTag).performClick()
        ui.onNodeWithTag("exam-response-27").assertIsDisplayed()
    }

    private fun captureProfilePosition(name:String):Float {
        ui.waitForIdle()
        var scroll=0f
        val range=ui.onNodeWithTag("profile-list").fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        ui.runOnIdle {scroll=range.value()}
        val dir=File(context.getExternalFilesDir(null),"page-state").apply {mkdirs()}
        val bitmap=ui.onRoot().captureToImage().asAndroidBitmap()
        File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}
        File(dir,"$name.txt").writeText("verticalScrollAxis=$scroll\n"+ui.onNodeWithTag("profile-list").printToString())
        return scroll
    }

    @Test fun settingsDraftAndExpandedBackupSurviveRestorationAndARealReturnToTheProfile() {
        show("profile")
        ui.onNodeWithTag("profile-list").performScrollToNode(hasContentDescription("Voir le mois précédent"))
        ui.onNodeWithContentDescription("Voir le mois précédent").performClick()
        val previousMonth=YearMonth.from(StudyDay.date()).minusMonths(1).format(DateTimeFormatter.ofPattern("MMMM yyyy",Locale.FRENCH))
            .replaceFirstChar {it.titlecase(Locale.FRENCH)}
        ui.onNodeWithText(previousMonth).assertIsDisplayed()
        ui.onNodeWithTag("profile-list").performScrollToNode(hasTestTag("profile-memory-tip"))
        val profileTop=ui.onNodeWithTag("profile-memory-tip").performScrollTo().assertIsDisplayed().getUnclippedBoundsInRoot().top
        val profileScroll=captureProfilePosition("profile-before-settings")
        ui.onNodeWithContentDescription("Réglages").performClick()
        val originalName=model.progress.name
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Pseudo"))
        ui.onNodeWithTag("settings-name").performTextReplacement("Brouillon non enregistré")
        hideKeyboard()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasContentDescription("Afficher les options de sauvegarde"))
        ui.onNodeWithContentDescription("Afficher les options de sauvegarde").performClick()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-backup-links"))
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("settings-backup-links").assertIsDisplayed()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Pseudo"))
        ui.onNodeWithTag("settings-name").assertTextContains("Brouillon non enregistré")
        assertEquals("Keeping a draft must not save the profile automatically.",originalName,model.progress.name)
        ui.onNodeWithTag("settings-list").performScrollToIndex(0)
        ui.onNodeWithContentDescription("Retour").performClick()
        ui.runOnIdle {assertEquals("profile",model.route)}
        val returnedScroll=captureProfilePosition("profile-after-settings")
        assertEquals("Returning from settings must keep the profile scroll value",profileScroll,returnedScroll,.001f)
        ui.onNodeWithTag("profile-memory-tip").assertIsDisplayed()
        assertEquals(profileTop.value,ui.onNodeWithTag("profile-memory-tip").getUnclippedBoundsInRoot().top.value,1.5f)
        ui.onNodeWithTag("profile-list").performScrollToNode(hasContentDescription("Voir le mois précédent"))
        ui.onNodeWithText(previousMonth).assertIsDisplayed()
        ui.onNodeWithContentDescription("Réglages").performClick()
        ui.onNodeWithTag("settings-list").performScrollToNode(hasText("Pseudo"))
        ui.onNodeWithTag("settings-name").assertTextContains("Brouillon non enregistré")
        ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-backup-links"))
        ui.onNodeWithTag("settings-backup-links").assertIsDisplayed()
    }

    /** Native Views are inspected only on their owning thread; a Dialog may be visible before it gains focus. */
    private fun visibleTimePickerOnMainThread():TimePicker? {
        check(Looper.myLooper()==Looper.getMainLooper())
        val matches=mutableListOf<TimePicker>()
        fun find(view:View) {
            if(!view.isShown)return
            if(view is TimePicker)matches+=view
            else if(view is ViewGroup)for(index in 0 until view.childCount)find(view.getChildAt(index))
        }
        WindowInspector.getGlobalWindowViews().filter {it.isAttachedToWindow&&it.isShown}.forEach(::find)
        check(matches.size<=1) {"More than one visible native reminder picker"}
        return matches.singleOrNull()
    }
    private fun awaitTimePicker(visible:Boolean=true) {
        ui.waitUntil(10_000) {
            var found=false
            ui.runOnUiThread {found=visibleTimePickerOnMainThread()!=null}
            found==visible
        }
    }

    @Test fun reminderTimePickerKeepsItsDraftTimeWithoutChangingTheSavedReminder() {
        show("settings")
        ui.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("reminder-time-picker"))
        val hourBefore=model.progress.prefs.getInt("reminderHour",20)
        val minuteBefore=model.progress.prefs.getInt("reminderMinute",0)
        ui.onNodeWithTag("reminder-time-picker").performClick()
        awaitTimePicker()
        ui.runOnIdle {requireNotNull(visibleTimePickerOnMainThread()).apply {hour=7;minute=23}}
        restoration.emulateSavedInstanceStateRestore()
        awaitTimePicker()
        ui.runOnIdle {
            val picker=requireNotNull(visibleTimePickerOnMainThread())
            assertEquals(7,picker.hour);assertEquals(23,picker.minute)
            assertEquals(hourBefore,model.progress.prefs.getInt("reminderHour",20))
            assertEquals(minuteBefore,model.progress.prefs.getInt("reminderMinute",0))
            // Close the actual native window rather than leaving it to the next test fixture.
            check(requireNotNull(picker.rootView.findViewById<View>(android.R.id.button2)).performClick())
        }
        awaitTimePicker(visible=false)
        ui.onNodeWithTag("reminder-time-picker").assertIsDisplayed()
        assertEquals(hourBefore,model.progress.prefs.getInt("reminderHour",20))
        assertEquals(minuteBefore,model.progress.prefs.getInt("reminderMinute",0))
    }

    @Test fun mixLengthAndThemeChoiceSurviveRestorationAndChangingTabs() {
        show("practice")
        ui.onNodeWithTag("practice-list").performScrollToNode(hasTestTag("mix-question-count"))
        ui.onNodeWithTag("mix-question-count-40").performClick().assertIsSelected()
        val all=content.mixIndex.keys.size
        ui.onNodeWithTag("practice-list").performScrollToNode(hasText("Choisir les thèmes · $all/$all"))
        ui.onNodeWithText("Choisir les thèmes · $all/$all").performClick()
        ui.onNodeWithTag("practice-list").performScrollToNode(hasText("Règle 0"))
        ui.onNodeWithText("Règle 0").performClick().assertIsNotSelected()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithText("Règle 0").assertIsNotSelected()
        tab("Mémo")
        tab("Défis")
        ui.onNodeWithText("Règle 0").assertIsDisplayed().assertIsNotSelected()
        ui.onNodeWithTag("practice-list").performScrollToNode(hasTestTag("mix-question-count"))
        ui.onNodeWithTag("mix-question-count-40").assertIsSelected()
    }

    @Test fun friendDetailAndTeamPositionsSurviveRestorationAndReopeningTheDetail() {
        val friend=model.friends.first()
        show("friends")
        val cardTag="friend-card-${friend.gist}"
        ui.onNodeWithTag("friends-list").performScrollToNode(hasTestTag(cardTag))
        val before=ui.onNodeWithTag(cardTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        ui.onNodeWithTag(cardTag).performClick()
        ui.onNodeWithTag("friend-progress-list").performScrollToNode(hasTestTag("friend-technical-info"))
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("friend-progress-screen").assertIsDisplayed()
        ui.onNodeWithTag("friend-technical-info").assertIsDisplayed()
        ui.onNodeWithContentDescription("Revenir à l’équipe").performClick()
        val returned=ui.onNodeWithTag(cardTag).assertIsDisplayed().getUnclippedBoundsInRoot()
        assertEquals(before.top.value,returned.top.value,1.5f)
        ui.onNodeWithTag(cardTag).performClick()
        ui.onNodeWithTag("friend-technical-info").assertIsDisplayed()
    }
}