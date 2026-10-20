package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class DiagnosticFinishingInstrumentedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val saved=mutableMapOf<String,Map<String,Any?>>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish"))
            for(name in listOf("hamigo","hamigo_social","hamigo_secure","hamigo_diagnostics")) {
                val prefs=context.getSharedPreferences(name,Context.MODE_PRIVATE)
                saved[name]=prefs.all.toMap();check(prefs.edit().clear().commit())
            }
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true)
                .putBoolean("autoSync",false).putBoolean("reminderEnabled",false).putBoolean("interactionSound",false).putString("progress","{\"schema\":2,\"xp\":19,\"completed\":[],\"reviews\":{}}").commit())
        }
        override fun after() {
            saved.forEach {(name,values)->val editor=context.getSharedPreferences(name,Context.MODE_PRIVATE).edit().clear()
                values.forEach {(key,value)->when(value){is String->editor.putString(key,value);is Int->editor.putInt(key,value);is Boolean->editor.putBoolean(key,value);is Long->editor.putLong(key,value);is Float->editor.putFloat(key,value);is Set<*>->editor.putStringSet(key,value.filterIsInstance<String>().toSet())}}
                check(editor.commit())
            }
        }
    }
    private val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)
    private lateinit var model:AppModel
    @Before fun load() {
        ui.prepareTestWindow()
        model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000){model.content!=null&&!model.socialRefreshing}
        ui.runOnIdle {model.showWelcome=false;model.debugToolsOpen=true}
    }
    private fun raw()=model.progress.prefs.getString("progress",null)
    private fun back()=ui.onNodeWithContentDescription("Retour des diagnostics").performClick()
    private fun tab(name:String)=ui.onNodeWithTag("diagnostic-tab-$name").performScrollTo().performClick()
    private fun hideKeyboard()=ui.runOnIdle {ui.activity.window.decorView.windowInsetsController?.hide(android.view.WindowInsets.Type.ime())}
    private fun capture(name:String) {
        ui.waitForIdle()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        repeat(2) {
            val frame=CountDownLatch(1)
            instrumentation.runOnMainSync {
                val view=if(Build.VERSION.SDK_INT>=29)android.view.inspector.WindowInspector.getGlobalWindowViews().lastOrNull {it.hasWindowFocus()} ?: ui.activity.window.decorView else ui.activity.window.decorView
                view.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};view.invalidate()
            }
            assertTrue("The foreground dialog committed its frame",frame.await(3,TimeUnit.SECONDS))
        }
        val output=File(context.getExternalFilesDir(null),"diagnostic-finitions").apply {mkdirs()}
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {File(output,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
    @Test fun reopeningDiagnosticsKeepsItsMemoryBoundedAndLeavesProgressUntouched() {
        val before=raw()
        fun retainedHeap():Long {
            ui.waitForIdle()
            repeat(2){Runtime.getRuntime().gc();Thread.sleep(100)}
            return Runtime.getRuntime().let {it.totalMemory()-it.freeMemory()}
        }
        val baseline=retainedHeap()
        val samples=mutableListOf(baseline)
        val firstOhm=model.content!!.allQuestions.values.first {it.id.startsWith("proc-ohm-")}.id
        repeat(12) {cycle->
            if(cycle%4==3) {
                tab("Intros")
                ui.waitUntil(30_000){ui.onAllNodesWithTag("diagnostic-course-$HamigoWelcomeLesson").fetchSemanticsNodes().isNotEmpty()}
                ui.onNodeWithTag("diagnostic-course-$HamigoWelcomeLesson").performClick()
                ui.onNodeWithTag("diagnostic-course-detail").performScrollToNode(hasTestTag("diagnostic-course-open"))
                ui.onNodeWithTag("diagnostic-course-open").performClick()
                ui.onNodeWithTag("lesson-introduction").assertExists()
            } else {
                tab("Questions")
                // The catalogue intentionally retains its query across diagnostic openings.
                // Replace the previous value; appending would search proc-ohm-proc-ohm- on cycle 2.
                ui.onNodeWithTag("diagnostic-search").performTextReplacement("proc-ohm-")
                hideKeyboard()
                ui.waitUntil(15_000){ui.onAllNodesWithTag("diagnostic-question-$firstOhm").fetchSemanticsNodes().isNotEmpty()}
            }
            ui.runOnIdle {model.debugToolsOpen=false}
            ui.onNodeWithTag("diagnostics-screen").assertDoesNotExist()
            ui.runOnIdle {assertNull("The closed diagnostic inspector must release its model at cycle $cycle",model.diagnosticUiState.inspector)}
            samples+=retainedHeap()
            ui.runOnIdle {model.debugToolsOpen=true}
            ui.onNodeWithTag("diagnostics-screen").assertExists()
        }
        val output=File(context.getExternalFilesDir(null),"diagnostic-finitions").apply {mkdirs()}
        File(output,"retained-heap.txt").writeText(samples.joinToString("\n"))
        assertTrue("Repeated dialog openings retained too much heap: $samples",samples.last()<baseline+32L*1024*1024)
        assertEquals(before,raw())
    }
    @Test fun questionSearchAndScrollSurviveDetailAndATestSession() {
        val before=raw()
        tab("Questions")
        ui.onNodeWithTag("diagnostic-search").performTextInput("proc-ohm-")
        hideKeyboard()
        val matches=model.content!!.allQuestions.values.filter {it.id.startsWith("proc-ohm-")}
        assertTrue(matches.size>25)
        val target=matches[25]
        ui.waitUntil(15_000){ui.onAllNodesWithTag("diagnostic-question-${matches.first().id}").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithTag("diagnostic-catalog").performScrollToNode(hasTestTag("diagnostic-question-${target.id}"))
        ui.onNodeWithTag("diagnostic-question-${target.id}").performClick()
        ui.onNodeWithTag("diagnostic-detail-${target.id}").assertExists()
        back()
        ui.onNodeWithTag("diagnostic-search").assertTextContains("proc-ohm-")
        ui.onNodeWithTag("diagnostic-question-${target.id}").assertIsDisplayed()
        capture("catalog-return")
        ui.onNodeWithTag("diagnostic-question-${target.id}").performClick()
        ui.onNodeWithText("Tester cette question").performScrollTo().performClick()
        ui.onNodeWithContentDescription("Quitter la séance").performClick()
        ui.onNodeWithTag("diagnostic-detail-${target.id}").assertExists()
        back()
        ui.onNodeWithTag("diagnostic-question-${target.id}").assertIsDisplayed()
        assertEquals(before,raw())
    }
    @Test fun variantsAndSkillTreeLeadToTheirQuestionsWithoutChangingTheOwner() {
        val before=raw()
        assertTrue(model.content!!.skills.all {SkillHierarchy.familyFor(it.id)!=null})
        tab("Types")
        ui.onNodeWithTag("diagnostic-type-search").performTextInput("SSTV")
        hideKeyboard()
        ui.onNodeWithTag("diagnostic-types").performScrollToNode(hasTestTag("diagnostic-variant-choice-sstv-slant"))
        ui.onNodeWithTag("diagnostic-variant-choice-sstv-slant").assertIsDisplayed()
        ui.onNodeWithTag("diagnostic-types").performScrollToNode(hasTestTag("diagnostic-variant-choice-sstv-loss"))
        ui.onNodeWithTag("diagnostic-variant-choice-sstv-loss").assertIsDisplayed()
        capture("sstv-interfaces")
        tab("Compétences")
        ui.onNodeWithTag("diagnostic-skill-search").performTextInput("ohm-law")
        hideKeyboard()
        ui.onNodeWithTag("diagnostic-skills").performScrollToNode(hasTestTag("diagnostic-skill-open-ohm-law"))
        capture("skill-tree")
        ui.onNodeWithTag("diagnostic-skill-open-ohm-law").performClick()
        ui.onNodeWithTag("diagnostic-skill-filter").assertExists()
        ui.waitUntil(15_000){ui.onAllNodesWithTag("diagnostic-question-${model.content!!.courseQuestions.first {it.skillIds.contains("ohm-law")}.id}").fetchSemanticsNodes().isNotEmpty()}
        back()
        ui.onNodeWithTag("diagnostic-skill-search").assertTextContains("ohm-law")
        assertEquals(before,raw())
    }
    @Test fun compactTypesKeepExpansionAndScrollAfterANativeExampleAndRecreation() {
        val before=raw()
        tab("Types")
        ui.onNodeWithTag("diagnostic-variant-number-number-signed").assertDoesNotExist()
        ui.onNodeWithTag("diagnostic-types").performScrollToNode(hasTestTag("diagnostic-type-variants-number"))
        ui.onNodeWithTag("diagnostic-type-variants-number").performClick()
        ui.onNodeWithTag("diagnostic-types").performScrollToNode(hasTestTag("diagnostic-variant-test-number-number-signed"))
        ui.onNodeWithTag("diagnostic-variant-test-number-number-signed").assertHasClickAction()
        val state=model.diagnosticUiState.types
        val position=ui.runOnIdle {state.listState.firstVisibleItemIndex to state.listState.firstVisibleItemScrollOffset}
        capture("types-compact-expanded")
        ui.onNodeWithTag("diagnostic-variant-test-number-number-signed").performClick()
        ui.onNodeWithTag("question-number-input").assertExists()
        ui.onNodeWithTag("question-number-sign").performScrollTo().assertIsDisplayed()
        val questionId=ui.runOnIdle {requireNotNull(model.diagnosticUiState.inspector!!.session!!.current).id}
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithTag("question-number-input").assertExists()
        assertEquals(questionId,ui.runOnIdle {requireNotNull(model.diagnosticUiState.inspector!!.session!!.current).id})
        ui.onNodeWithContentDescription("Quitter la séance").performClick()
        ui.onNodeWithTag("diagnostic-variant-test-number-number-signed").assertIsDisplayed()
        assertEquals(position,ui.runOnIdle {state.listState.firstVisibleItemIndex to state.listState.firstVisibleItemScrollOffset})
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        assertSame(model,ViewModelProvider(ui.activity)[AppModel::class.java])
        ui.onNodeWithTag("diagnostic-tab-Types").assertExists()
        ui.onNodeWithTag("diagnostic-variant-test-number-number-signed").assertIsDisplayed()
        assertTrue("number" in state.expanded)
        assertEquals(position,ui.runOnIdle {state.listState.firstVisibleItemIndex to state.listState.firstVisibleItemScrollOffset})
        capture("types-return-recreated")
        assertEquals(before,raw())
    }
    @Test fun signedNumericInterfaceIsSearchableAndItsButtonActuallyChangesTheSign() {
        val before=raw()
        tab("Types")
        ui.onNodeWithTag("diagnostic-type-search").performTextInput("signe inversible")
        hideKeyboard()
        ui.onNodeWithTag("diagnostic-types").performScrollToNode(hasTestTag("diagnostic-variant-test-number-number-signed"))
        capture("types-signed-search")
        ui.onNodeWithTag("diagnostic-variant-test-number-number-signed").performClick()
        ui.onNodeWithTag("question-number-input").performScrollTo().performTextInput("3")
        hideKeyboard()
        ui.onNodeWithTag("question-number-sign").performScrollTo().performClick()
        ui.onNodeWithTag("question-number-input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("3")))
        ui.onNodeWithTag("question-number-sign").assertTextContains("−")
        ui.onNodeWithTag("question-number-sign").performClick()
        ui.onNodeWithTag("question-number-input").assertTextContains("3")
        ui.onNodeWithTag("question-number-input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("3")))
        ui.onNodeWithTag("question-number-sign").assertTextContains("+")
        // A pasted sign belongs to the connected button and survives Activity recreation.
        ui.onNodeWithTag("question-number-input").performTextReplacement("−3,5")
        hideKeyboard()
        ui.onNodeWithTag("question-number-input").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.EditableText,androidx.compose.ui.text.AnnotatedString("3,5")))
        ui.onNodeWithTag("question-number-sign").assertTextContains("−")
        ui.activityRule.scenario.recreate()
        ui.onNodeWithTag("question-number-input").assertTextContains("3,5")
        ui.onNodeWithTag("question-number-sign").assertTextContains("−")
        capture("signed-number-restored")
        val expected=ui.runOnIdle {kotlin.math.abs(requireNotNull(model.diagnosticUiState.inspector!!.session!!.current!!.value)).toString()}
        ui.onNodeWithTag("question-number-input").performTextReplacement(expected)
        hideKeyboard()
        ui.onNodeWithText("Vérifier",substring=false).assertIsDisplayed().performClick()
        ui.runOnIdle {assertEquals(true,model.diagnosticUiState.inspector!!.session!!.feedback)}
        ui.onNodeWithContentDescription("Quitter la séance").performClick()
        ui.onNodeWithTag("diagnostic-type-search").assertTextContains("signe inversible")
        assertEquals(before,raw())
    }
    @Test fun primaryInterfaceIsSearchableAndTheThreeBatchesContainEveryCompositionOnce() {
        val before=raw()
        val groups=DiagnosticUiExamples.all(model.content!!).groupBy {it.kind}.toSortedMap()
        tab("Types")
        val primary=QuestionUiVariants.primary(groups.getValue("circuit"))
        val query=QuestionUiVariants.label(QuestionUiVariants.ids(primary).single())
        ui.onNodeWithTag("diagnostic-type-search").performTextInput(query)
        hideKeyboard()
        ui.onNodeWithTag("diagnostic-type-circuit").assertExists()
        ui.onNodeWithTag("diagnostic-type-test-circuit").performScrollTo().performClick()
        assertEquals(QuestionUiVariants.compositionKey(primary),ui.runOnIdle {QuestionUiVariants.compositionKey(requireNotNull(model.diagnosticUiState.inspector!!.session!!.current))})
        ui.onNodeWithContentDescription("Quitter la séance").performClick()
        ui.onNodeWithTag("diagnostic-type-search").assertTextContains(query)
        ui.onNodeWithTag("diagnostic-type-search").performTextClearance()
        hideKeyboard()
        fun assertBatch(button:String,expected:List<Question>) {
            ui.onNodeWithTag(button).performClick()
            val actual=ui.runOnIdle {model.diagnosticUiState.inspector!!.session!!.questions.toList()}
            assertEquals(expected.size,actual.size)
            // Templates may legitimately change response length when a session is created.
            // The batch contract selects each source composition once, with those exact stable IDs.
            assertEquals(expected.map {it.id}.toSet(),actual.map {it.id}.toSet())
            assertEquals(expected.size,expected.map(QuestionUiVariants::compositionKey).distinct().size)
            assertEquals(actual.size,actual.map {it.id}.distinct().size)
            ui.onNodeWithContentDescription("Quitter la séance").performClick()
        }
        assertBatch("diagnostic-types-test-all",groups.values.map(QuestionUiVariants::primary))
        assertBatch("diagnostic-types-test-variants-all",groups.values.flatMap(QuestionUiVariants::allCompositions))
        ui.onNodeWithTag("diagnostic-types").performScrollToNode(hasTestTag("diagnostic-type-test-variants-circuit"))
        assertBatch("diagnostic-type-test-variants-circuit",QuestionUiVariants.allCompositions(groups.getValue("circuit")))
        assertEquals(before,raw())
    }
    @Test fun developedWrittenAnswerAndGraphicalMorseFlashcardUseTheirNativeInterfaces() {
        val before=raw()
        tab("Types")
        ui.onNodeWithTag("diagnostic-type-search").performTextInput("Écrit réponse développée")
        hideKeyboard()
        ui.onNodeWithTag("diagnostic-types").performScrollToNode(hasTestTag("diagnostic-variant-test-text-text-long"))
        ui.onNodeWithTag("diagnostic-variant-test-text-text-long").performClick()
        ui.onNodeWithTag("question-text-input").performScrollTo().performTextInput("Je vérifie le mode tension")
        hideKeyboard()
        capture("types-written-developed")
        assertEquals("diagnostic-ui-text-long",ui.runOnIdle {requireNotNull(model.diagnosticUiState.inspector!!.session!!.current).id})
        ui.onNodeWithContentDescription("Quitter la séance").performClick()
        ui.onNodeWithTag("diagnostic-type-search").performTextClearance()
        ui.onNodeWithTag("diagnostic-type-search").performTextInput("Morse graphique")
        hideKeyboard()
        ui.onNodeWithTag("diagnostic-types").performScrollToNode(hasTestTag("diagnostic-variant-test-flash-flash-morse"))
        ui.onNodeWithTag("diagnostic-variant-test-flash-flash-morse").performClick()
        ui.onNodeWithTag("flashcard").performScrollTo().performClick()
        ui.waitForIdle()
        ui.onNodeWithTag("flashcard").assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription,"Réponse"))
        capture("types-flash-morse-graphical")
        ui.onNodeWithContentDescription("Quitter la séance").performClick()
        assertEquals(before,raw())
    }
    @Test fun compactSkillsKeepTheirSearchAndPositionThroughQuestionsAndRecreation() {
        val before=raw()
        tab("Compétences")
        ui.onNodeWithTag("diagnostic-skill-search").performTextInput("électricité")
        hideKeyboard()
        val skill="ohm-law"
        ui.onNodeWithTag("diagnostic-skills").performScrollToNode(hasTestTag("diagnostic-skill-open-$skill"))
        val state=model.diagnosticUiState.skillsList
        val position=ui.runOnIdle {state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset}
        capture("skills-compact")
        ui.onNodeWithTag("diagnostic-skill-open-$skill").performClick()
        ui.onNodeWithTag("diagnostic-skill-filter").assertExists()
        ui.activityRule.scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithTag("diagnostic-skill-filter").assertExists()
        back()
        ui.onNodeWithTag("diagnostic-skill-search").assertTextContains("électricité")
        ui.onNodeWithTag("diagnostic-skill-open-$skill").assertIsDisplayed()
        assertEquals(position,ui.runOnIdle {state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset})
        capture("skills-return-recreated")
        assertEquals(before,raw())
    }
    @Test fun simulatedExamThresholdsUseCompleteTranscriptsOnlyInMemory() {
        val before=raw()
        tab("Aperçus")
        ui.onNodeWithTag("diagnostic-preview-search").performTextInput("historique")
        hideKeyboard()
        for((label,count)in listOf("aucun" to 0,"deux" to 2,"trois" to 3)) {
            ui.onNodeWithTag("diagnostic-preview-list").performScrollToNode(hasTestTag("diagnostic-preview-Historique · $label examen${if(count==0)"" else "s"}"))
            ui.onNodeWithTag("diagnostic-preview-Historique · $label examen${if(count==0)"" else "s"}").performClick()
            ui.onNodeWithTag("exam-history-list").assertExists()
            if(count==0)ui.onNodeWithText("Ton premier examen ouvre l’historique.").assertExists()
            else ui.onNodeWithText("L’évolution de tes résultats").assertExists()
            capture("exam-history-$count")
            ui.onNodeWithTag("exam-history-list").performScrollToNode(hasTestTag("start-customized-exam"))
            if(count<3)ui.onNodeWithTag("start-customized-exam").assertIsNotEnabled()
            else if(model.content!!.examSnapshot!=null)ui.onNodeWithTag("start-customized-exam").assertIsEnabled()
            back()
            ui.onNodeWithTag("diagnostic-preview-search").assertTextContains("historique")
        }
        ui.runOnIdle {
            val simulated=AppModel().apply {initializeDiagnostics(context,model,model.content!!)}
            try {
                DiagnosticData.seed(simulated)
                DiagnosticPreviewCatalog.seedExams(simulated,3)
                assertEquals(3,simulated.progress.examHistory.size)
                simulated.progress.examHistory.forEach {attempt->assertEquals(40,attempt.responses.size);ExamHistory.validateAttempt(attempt)}
                assertEquals(0,simulated.progress.skillReviews.size)
            } finally {simulated.disposeDiagnostics()}
        }
        assertEquals(before,raw())
    }
}
