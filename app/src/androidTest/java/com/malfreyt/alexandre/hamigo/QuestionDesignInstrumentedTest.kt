package com.malfreyt.alexandre.hamigo

import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import android.app.Instrumentation
import android.content.Context
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.TimePicker
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.CloudProgress
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.hamcrest.Matcher
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class QuestionDesignInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val saved = mutableMapOf<String, Map<String, Any?>>()
    private val intents = CopyOnWriteArrayList<Intent>()
    private val monitor = object : Instrumentation.ActivityMonitor() {
        override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
            if (intent.action in listOf(Intent.ACTION_VIEW, Intent.ACTION_SENDTO)) {
                intents += Intent(intent)
                return Instrumentation.ActivityResult(0, null) // Never open or send a real report.
            }
            return null
        }
    }
    private val fixture = object : ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic"))
            for (file in listOf("hamigo", "hamigo_social", "hamigo_secure")) {
                val prefs = context.getSharedPreferences(file, Context.MODE_PRIVATE)
                saved[file] = prefs.all.mapValues { (_, v) -> if (v is Set<*>) v.toSet() else v }
                check(prefs.edit().clear().commit())
            }
            check(context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit()
                .putBoolean("welcomed", true).putBoolean("autoSync", false)
                .putBoolean("reminderEnabled", false).putInt("reminderHour", 20).putInt("reminderMinute", 0).commit())
            ProgressSyncScheduler.cancel(context)
            instrumentation.addMonitor(monitor)
        }
        override fun after() {
            instrumentation.removeMonitor(monitor)
            saved.forEach { (file, values) ->
                val edit = context.getSharedPreferences(file, Context.MODE_PRIVATE).edit().clear()
                values.forEach { (k, v) -> when (v) {
                    is String -> edit.putString(k,v); is Int -> edit.putInt(k,v); is Long -> edit.putLong(k,v)
                    is Float -> edit.putFloat(k,v); is Boolean -> edit.putBoolean(k,v)
                    is Set<*> -> edit.putStringSet(k,v.filterIsInstance<String>().toSet())
                } }
                check(edit.commit())
            }
            ProgressSyncScheduler.schedule(context)
            DailyReminder.schedule(context)
        }
    }
    private val ui = createAndroidComposeRule<MainActivity>()
    @get:Rule val rules: RuleChain = RuleChain.outerRule(fixture).around(ui)
    private lateinit var model: AppModel
    @Before fun load() {
        model = ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000) { model.content != null }
        ui.runOnIdle { model.showWelcome = false }
    }

    private fun examples():List<Question> = listOf("choice","truefalse","match","order","number","cloze","resistor","waveform").map {kind->
        model.content!!.allQuestions.values.first {it.kind==kind&&it.image==null&&(kind!="match"||it.pairs.size==3)}
    }
    private fun placement(value:QuestionAnswerPlacement) {
        ui.activity.setContent {HamigoTheme {CompositionLocalProvider(LocalQuestionAnswerPlacement provides value) {HamigoApp(model)}}}
    }
    @Test fun compareTopAndCenterForEightRealQuestionTypes() {
        for(place in listOf(QuestionAnswerPlacement.TOP,QuestionAnswerPlacement.CENTER)) {
            placement(place)
            for(q in examples()) {
                ui.runOnIdle {model.startQuestions("Questions de Pico",listOf(q))}
                ui.onNodeWithTag("question-pico").assertIsDisplayed()
                ui.onNodeWithTag("question-tools").assertIsDisplayed()
                ui.onNodeWithTag("question-pico-audio-hint",useUnmergedTree=true).assertIsDisplayed()
                val notes=ui.onNodeWithContentDescription("Ouvrir le brouillon").fetchSemanticsNode().boundsInRoot
                val calculator=ui.onNodeWithContentDescription("Ouvrir la calculatrice").fetchSemanticsNode().boundsInRoot
                assertTrue("Tools stack vertically on a tall screen",notes.bottom<calculator.top)
                capture("compare-${place.name.lowercase()}-${q.kind}")
                assertLastAnswerClearsTheTools(q)
            }
        }
    }
    @Test fun matchingDragsFromBothSidesAndMarksTheSubmittedPairs() {
        val q=Question("drag-match","Relie les grandeurs et leurs unités.",emptyList(),0,"Une grandeur a son unité.",kind="match",
            pairs=listOf(PairItem("Tension","Volt"),PairItem("Résistance","Ohm"),PairItem("Fréquence","Hertz")))
        ui.runOnIdle {model.startQuestions("Connexions",listOf(q))}
        fun drag(a:String,b:String) {
            val source=ui.onNodeWithTag(a);val origin=source.fetchSemanticsNode().boundsInRoot
            val target=ui.onNodeWithTag(b).fetchSemanticsNode().boundsInRoot.center
            source.performTouchInput {down(center);moveTo(center+Offset(if(a.contains("left"))25f else -25f,0f),delayMillis=80)}
            capture("wire-mid-${a}")
            source.performTouchInput {moveTo(target-origin.topLeft,delayMillis=200);up()}
        }
        drag("match-left-0","match-right-0")
        drag("match-right-1","match-left-2") // Deliberately wrong.
        ui.onNodeWithTag("match-left-1").performClick();ui.onNodeWithTag("match-right-2").performClick()
        ui.onNodeWithText("Vérifier").performClick()
        ui.runOnIdle {assertFalse(model.session!!.feedback!!);assertTrue(model.session!!.responses.values.last().display.contains("Tension → Volt"))}
        ui.onNodeWithText("Continuer").assertIsDisplayed()
        capture("match-feedback-green-red")
        ui.onNodeWithText("liens créés",substring=true).assertDoesNotExist()
    }
    @Test fun orderHandlesReorderBothDirectionsAndTheAnswerIsChecked() {
        val q=Question("drag-order","Range ces fréquences de la plus petite à la plus grande.",listOf("10 Hz","20 kHz","30 MHz","40 GHz"),0,"Hz, kHz, MHz, GHz.",kind="order")
        ui.runOnIdle {model.startQuestions("Ordre",listOf(q))}
        fun order()=q.choices.indices.sortedBy {ui.onNodeWithTag("order-item-$it").fetchSemanticsNode().boundsInRoot.top}
        val before=order()
        val last=before.last();val first=before.first()
        val source=ui.onNodeWithTag("order-handle-$last");val origin=source.fetchSemanticsNode().boundsInRoot
        val target=ui.onNodeWithTag("order-item-$first").fetchSemanticsNode().boundsInRoot.center
        source.performTouchInput {swipe(center,target-origin.topLeft,600)}
        assertEquals(last,order().first())
        capture("order-after-drag")
        for(position in q.choices.indices) {
            val sorted=order();val item=position
            if(sorted[position]!=item) {
                val handle=ui.onNodeWithTag("order-handle-$item");val box=handle.fetchSemanticsNode().boundsInRoot
                val dest=ui.onNodeWithTag("order-item-${sorted[position]}").fetchSemanticsNode().boundsInRoot.center
                handle.performTouchInput {swipe(center,dest-box.topLeft,650)}
            }
        }
        assertEquals(q.choices.indices.toList(),order())
        ui.onNodeWithText("Vérifier").performClick()
        ui.runOnIdle {assertTrue(model.session!!.feedback!!)}
        capture("order-correct")
    }
    @Test fun repeatedMascotTapsCloseItsEyesWithoutChangingTheAnswer() {
        ui.runOnIdle {model.startQuestions("Pico",listOf(examples().first()))}
        val pico=ui.onNodeWithTag("question-pico")
        pico.assertIsDisplayed()
        ui.mainClock.autoAdvance=false
        try {
            val tap=pico.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsActions.OnClick].action!!
            ui.runOnIdle {repeat(4){tap()}}
            ui.mainClock.advanceTimeBy(32)
            pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pico ferme les yeux"))
            capture("pico-bashful")
            ui.runOnIdle {assertTrue(model.session!!.responses.isEmpty())}
            ui.mainClock.advanceTimeBy(3000)
            pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pico présente la question"))
        } finally {ui.mainClock.autoAdvance=true}
    }

    @Test fun allOtherTypesLongPromptsAndFlashcardsRemainUsable() {
        val short=examples().first().copy(id="short-question",prompt="Quel est le rôle de l’antenne ?")
        ui.runOnIdle {model.startQuestions("Énoncé court",listOf(short))}
        val shortPico=ui.onNodeWithTag("question-pico").fetchSemanticsNode().boundsInRoot
        val shortText=ui.onNodeWithTag("question-text-region").fetchSemanticsNode().boundsInRoot
        val shortGap=if(shortPico.center.x<shortText.center.x)shortText.left-shortPico.right else shortPico.left-shortText.right
        capture("final-short-prompt")
        val kinds=listOf("frequency","estimate","binary","morseEncode","morseListen","multiselect","flash")
        for(kind in kinds) {
            val q=model.content!!.allQuestions.values.first {it.kind==kind&&it.image==null}
            ui.runOnIdle {model.startQuestions("Questions de Pico",listOf(q))}
            ui.onNodeWithTag("question-pico").assertIsDisplayed()
            capture("final-$kind")
        }
        val q=examples().first().copy(id="long-question",prompt="Une station utilise un émetteur radio relié à une antenne par un câble coaxial. En tenant compte du rôle de chaque élément, quelle affirmation décrit correctement l’objectif du service amateur et les conditions dans lesquelles il est utilisé ?")
        ui.runOnIdle {model.startQuestions("Énoncé long",listOf(q))}
        val longPico=ui.onNodeWithTag("question-pico").fetchSemanticsNode().boundsInRoot
        val longText=ui.onNodeWithTag("question-text-region").fetchSemanticsNode().boundsInRoot
        assertTrue("Long questions give more width to their text",longPico.width<shortPico.width*.85f)
        assertTrue("Long questions put Pico above their full-width text",longPico.bottom<longText.top+1f)
        assertTrue("Short questions leave space around Pico",shortGap/ui.activity.resources.displayMetrics.density>=14f)
        capture("final-long-prompt")
        ui.onNodeWithText(q.choices.first()).performScrollTo().performClick()
        ui.onNodeWithText("Vérifier").assertIsEnabled()
        val exam=model.content!!.activeExam.first {it.image!=null}
        ui.runOnIdle {model.startQuestions("Exam1",listOf(exam))}
        ui.waitUntil(15000) {ui.onAllNodesWithText("Toucher pour agrandir").fetchSemanticsNodes().isNotEmpty()}
        capture("final-exam-illustration")
        ui.onNodeWithText("Toucher pour agrandir").performClick()
        ui.onNodeWithContentDescription("Illustration originale : pincer pour zoomer").assertIsDisplayed()
        capture("final-exam-fullscreen")
    }
    @Test fun clozeCanBeDraggedIntoThePromptAboveTheAnswerArea() {
        val q=Question("split-cloze","Une résistance se mesure en ___ .",listOf("ohms","volts","watts"),0,"La résistance utilise l’ohm.",kind="cloze")
        ui.runOnIdle {model.startQuestions("Compléter",listOf(q))}
        val source=ui.onNodeWithText("ohms");val bounds=source.fetchSemanticsNode().boundsInRoot
        val target=ui.onNodeWithContentDescription("Emplacement pour le mot à compléter").fetchSemanticsNode().boundsInRoot.center
        source.performTouchInput {swipe(center,target-bounds.topLeft,800)}
        ui.onNodeWithText("Vérifier").assertIsEnabled().performClick()
        ui.runOnIdle {assertTrue(model.session!!.feedback!!)}
        capture("final-cloze-drag")
    }
    @Test fun mascotReadsTheQuestionAndStopsWhenQuestionChanges() {
        val first=Question("spoken","Une résistance de dix ohms est parcourue par un courant de deux ampères. Quelle tension est présente à ses bornes ?",emptyList(),0,"Vingt volts.",kind="number",value=20.0,unit="V")
        ui.runOnIdle {model.startQuestions("Lecture",listOf(first,first.copy(id="spoken-next",prompt="Et maintenant, trois ampères ?")))}
        val pico=ui.onNodeWithTag("question-pico")
        pico.assertIsDisplayed()
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithTag("question-text-region").performClick()
            var speaking=false
            val start=System.currentTimeMillis()
            while(System.currentTimeMillis()-start<30000) {
                ui.mainClock.advanceTimeBy(32)
                speaking=pico.fetchSemanticsNode().config[SemanticsProperties.StateDescription]=="Pico parle"
                if(speaking)break
                Thread.sleep(60)
            }
            assertTrue("The French engine reaches its real onStart callback",speaking)
            ui.onNodeWithTag("question-pico-audio-hint").assert(hasContentDescription("Arrêter la lecture de la question"))
            ui.mainClock.advanceTimeBy(180);capture("pico-speaking-open")
            ui.mainClock.advanceTimeBy(360);capture("pico-speaking-next-mouth")
            ui.onNodeWithTag("question-pico-audio-hint").performClick();ui.mainClock.advanceTimeBy(32)
            pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pico présente la question"))
            pico.performClick();ui.mainClock.advanceTimeBy(32)
            ui.runOnIdle {model.session!!.index=1;model.refresh()}
            ui.mainClock.advanceTimeBy(32)
            pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pico présente la question"))
            capture("pico-next-question-stopped")
        } finally {ui.mainClock.autoAdvance=true}
    }

    @Test fun compactScreensKeepTheMainInteractionsAboveTheTools() {
        for(q in examples().filter {it.kind in listOf("choice","cloze","number","match","order")}) {
            ui.runOnIdle {model.startQuestions("Écran compact",listOf(q))}
            ui.onNodeWithTag("question-pico").assertIsDisplayed()
            assertLastAnswerClearsTheTools(q)
            ui.onNodeWithText("Vérifier").assertIsDisplayed()
            capture("compact-${q.kind}")
        }
    }

    private fun assertLastAnswerClearsTheTools(q:Question) {
        ui.onNodeWithTag("question-answers").performSemanticsAction(SemanticsActions.ScrollBy) {it(0f,100_000f)}
        val last=when(q.kind) {
            "match" -> ui.onNodeWithTag("match-board")
            "order" -> ui.onNodeWithTag("order-board")
            "number" -> ui.onNodeWithTag("question-number-input")
            else -> ui.onNodeWithText(q.choices.last(),substring=false)
        }
        last.assertIsDisplayed()
        val bottom=last.fetchSemanticsNode().boundsInRoot.bottom
        val toolsTop=ui.onNodeWithTag("question-tools").fetchSemanticsNode().boundsInRoot.top
        assertTrue("The final answer clears the floating buttons after scrolling",bottom<=toolsTop+1f)
    }

    @Test fun morseIntroductionShowsLiteralSlashAndEachPunctuationHasAnIndependentListeningButton() {
        val lesson=model.content!!.lessons.first {it.id=="c15-l10"}
        val before=model.progress.prefs.getString("progress",null)
        ui.runOnIdle {model.route="path";model.startLesson(lesson);FeedbackPreferences.save(model.progress.prefs,FeedbackSettings(sound=false))}
        capture("intro47-punctuation-top")
        ui.onNodeWithTag("lesson-listen").performClick()
        ui.waitUntil(30_000) {ui.onNodeWithTag("lesson-pico").fetchSemanticsNode().config[SemanticsProperties.StateDescription]=="Pico parle"}
        ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag("lesson-morse-row-/"))
        ui.onNodeWithText("/",useUnmergedTree=true).assertExists()
        ui.onAllNodesWithContentDescription("séparation entre mots",useUnmergedTree=true).assertCountEquals(0)
        ui.onNodeWithTag("lesson-morse-listen-/").performClick()
        ui.waitUntil {FeedbackAudioGate.busy}
        capture("intro47-punctuation-rows")
        ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag("lesson-morse-row-@"))
        ui.onNodeWithTag("lesson-morse-listen-@").assertIsDisplayed().performClick()
        capture("intro47-punctuation-last")
        ui.runOnIdle {model.lesson=null}
        ui.waitUntil { !FeedbackAudioGate.busy }
        assertEquals(before,model.progress.prefs.getString("progress",null))
    }
    @Test fun letterAndDigitIntroductionsOfferPlayableRowsWithoutStartingTheQuestions() {
        val before=model.progress.prefs.getString("progress",null)
        for((id,symbol) in listOf("c15-l01" to "T","c15-l07" to "5")) {
            ui.runOnIdle {model.previewLesson(model.content!!.lessons.first {it.id==id})}
            ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasTestTag("lesson-morse-row-$symbol"))
            ui.onNodeWithTag("lesson-morse-listen-$symbol").assertIsDisplayed().performClick()
            capture("intro47-$id-rows")
            ui.onNodeWithText("À toi de jouer").assertDoesNotExist()
            assertNull(model.session)
            ui.runOnIdle {model.lesson=null}
            ui.waitUntil { !FeedbackAudioGate.busy }
        }
        assertEquals(before,model.progress.prefs.getString("progress",null))
    }
    @Test fun courseIntroductionHasLargerPicoAndUsesTheSameSpeechMotion() {
        ui.runOnIdle {model.route="path";model.startLesson(model.content!!.lessons.first())}
        val pico=ui.onNodeWithTag("lesson-pico");pico.assertIsDisplayed()
        capture("lesson-large-pico")
        ui.mainClock.autoAdvance=false
        try {
            pico.performClick()
            var talking=false;val started=System.currentTimeMillis()
            while(System.currentTimeMillis()-started<30000) {
                ui.mainClock.advanceTimeBy(32)
                talking=pico.fetchSemanticsNode().config[SemanticsProperties.StateDescription]=="Pico parle"
                if(talking)break
                Thread.sleep(60)
            }
            assertTrue(talking)
            ui.mainClock.advanceTimeBy(180);capture("lesson-speaking-open")
            ui.mainClock.advanceTimeBy(360);capture("lesson-speaking-closed")
            ui.onNodeWithTag("lesson-listen").performClick();ui.mainClock.advanceTimeBy(32)
            pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,MascotMood.HAPPY.description))
            ui.onNodeWithContentDescription("Écouter le cours").assertIsDisplayed()
        } finally {ui.mainClock.autoAdvance=true}
    }

    @Test fun coursePicoChangesOverTimeAndReactsToTouch() {
        ui.runOnIdle {model.route="path";model.startLesson(model.content!!.lessons.first())}
        val pico=ui.onNodeWithTag("lesson-pico");pico.assertIsDisplayed()
        ui.mainClock.autoAdvance=false
        try {
            capture("lesson-pico-initial")
            ui.mainClock.advanceTimeBy(12_500)
            pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,MascotMood.THINKING.description))
            capture("lesson-pico-reading")
            val tap=pico.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
            ui.runOnIdle {repeat(3){tap()}};ui.mainClock.advanceTimeBy(320)
            pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,"Pico fait le clown"))
            capture("lesson-pico-play")
            ui.mainClock.advanceTimeBy(3000)
            pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,MascotMood.THINKING.description))
        } finally {ui.mainClock.autoAdvance=true}
    }

    @Test fun memoHeaderStaysCompactAndPicoWavesAtTheBottom() {
        val references=model.content!!.references
        ui.runOnIdle {model.route="memo";model.resource=references.first {it.id=="resistors"}}
        ui.onNodeWithTag("memo-pico").assertDoesNotExist()
        capture("memo-resistors-header")
        ui.runOnIdle {model.resource=references.first {it.id=="nato"}}
        ui.onNodeWithTag("memo-detail-list").performScrollToNode(hasTestTag("memo-footer-pico"))
        ui.onNodeWithTag("memo-footer-pico").assertIsDisplayed()
        capture("memo-footer-wave")
    }

    @Test fun largerFlashcardPicoReactsToFlippingAndKeepsTheRatingsAccessible() {
        val q=model.content!!.allQuestions.values.first {it.kind=="flash"&&it.image==null}
        ui.runOnIdle {model.startQuestions("Flashcards",listOf(q))}
        val pico=ui.onNodeWithTag("question-pico");pico.assertIsDisplayed()
        assertTrue(pico.fetchSemanticsNode().boundsInRoot.width/ui.activity.resources.displayMetrics.density>=120f)
        capture("flash-pico-larger")
        ui.onNodeWithTag("flashcard").performClick()
        pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,MascotMood.HAPPY.description))
        capture("flash-pico-answer")
        ui.onNodeWithTag("flashcard").performClick()
        pico.assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription,MascotMood.DETERMINED.description))
        listOf("À revoir","Difficile","Bien","Facile").forEach {ui.onNodeWithText(it,substring=false).assertIsDisplayed()}
        capture("flash-pico-question-again")
    }

    @Test fun sharedPicoShadowStaysLighterThanTheFeetOnDifferentBackgrounds() {
        fun luminance(pixel:Int)=.2126*android.graphics.Color.red(pixel)+.7152*android.graphics.Color.green(pixel)+.0722*android.graphics.Color.blue(pixel)
        for(edge in listOf(52,84,132))for(background in listOf(0xFFFFFFFF.toInt(),0xFFFAF8F2.toInt(),0xFFE1F2EF.toInt())) {
            val bitmap=Bitmap.createBitmap(edge,edge,Bitmap.Config.ARGB_8888)
            try {
                val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(background)
                PicoRenderer.draw(canvas,android.graphics.RectF(0f,0f,edge.toFloat(),edge.toFloat()),MascotMood.HAPPY,MascotPose.IDLE,.25f)
                val shadow=bitmap.getPixel(edge/2,edge*120/128)
                val foot=bitmap.getPixel(edge*95/128,edge*117/128)
                assertTrue("The ground stays much lighter than the feet at $edge px",luminance(shadow)>luminance(foot)+50)
            } finally {bitmap.recycle()}
        }
    }

    @Test fun reportedExamImagesGrowThePromptAndLongContentSharesOnePageScroll() {
        for(id in listOf("21150","38473","20978","20003")) {
            val q=model.content!!.exam.first {it.id==id}
            ui.runOnIdle {model.startQuestions("Exam1 $id",listOf(q))}
            ui.waitUntil(15000){ui.onAllNodesWithText("Toucher pour agrandir").fetchSemanticsNodes().isNotEmpty()}
            val prompt=ui.onNodeWithTag("question-prompt-area").fetchSemanticsNode().boundsInRoot
            val pico=ui.onNodeWithTag("question-pico").fetchSemanticsNode().boundsInRoot
            val art=ui.onNodeWithContentDescription("Question illustrée Exam1").fetchSemanticsNode().boundsInRoot
            assertTrue(pico.top>=prompt.top);assertTrue(art.bottom<=prompt.bottom)
            capture("correction-exam-$id")
            if(id=="20003")ui.onAllNodesWithTag("math-formula",useUnmergedTree=true).assertCountEquals(4)
        }
        val long=examples().first().copy(id="one-scroll",prompt=("Une station transmet un signal vers une antenne distante. ").repeat(14))
        ui.runOnIdle {model.startQuestions("Page longue",listOf(long))}
        val page=ui.onNodeWithTag("question-answers")
        ui.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)).assertCountEquals(1)
        val initialScroll=page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()
        capture("correction-long-page-start")
        page.performSemanticsAction(SemanticsActions.ScrollBy){it(0f,200f)}
        assertTrue(page.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value()>initialScroll+150f)
        ui.onNodeWithText(long.choices.last()).performScrollTo().performClick()
        ui.onNodeWithText("Vérifier").assertIsEnabled()
        capture("correction-long-page-answers")
    }

    @Test fun question20081HasACentredNativePreviewAndKeepsItsFullOriginal() {
        val q=model.content!!.exam.first {it.id=="20081"}
        ui.runOnIdle {model.startQuestions("Exam1 20081",listOf(q))}
        ui.waitUntil(15000){ui.onAllNodesWithText("Toucher pour agrandir").fetchSemanticsNodes().isNotEmpty()}
        val preview=ui.onNodeWithContentDescription("Question illustrée Exam1",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("The preview should fit only the two text lines, with balanced gutters",preview.width/preview.height in 2.4f..2.8f)
        capture("correction-exam-20081-centred")
        ui.onNodeWithText("Toucher pour agrandir").performClick()
        val original=ui.onNodeWithTag("exam-image-original")
        original.assertIsDisplayed()
        val full=original.fetchSemanticsNode().boundsInRoot
        assertTrue("Enlarging keeps the source's entire 770 x 365 image",full.width/full.height in 2f..2.2f)
        capture("correction-exam-20081-original")
        ui.onNodeWithTag("exam-image-overlay").performTouchInput {click(Offset(8f,8f))}
        ui.onNodeWithTag("exam-image-overlay").assertDoesNotExist()
    }

    @Test fun originalExamImageFloatsOverThePageZoomsAndClosesFromTheScrim() {
        val q=model.content!!.exam.first {it.id=="38473"}
        ui.runOnIdle {model.startQuestions("Exam1 38473",listOf(q))}
        ui.waitUntil(15000){ui.onAllNodesWithText("Toucher pour agrandir").fetchSemanticsNodes().isNotEmpty()}
        ui.onNodeWithText("Toucher pour agrandir").performClick()
        val original=ui.onNodeWithTag("exam-image-original")
        original.assertIsDisplayed();capture("correction-exam-overlay")
        val overlay=ui.onNodeWithTag("exam-image-overlay")
        val fittedHeight=original.fetchSemanticsNode().boundsInRoot.height
        fun zoomPercent()=original.fetchSemanticsNode().config[SemanticsProperties.StateDescription].removePrefix("Zoom ").removeSuffix(" %").toInt()
        overlay.performTouchInput {
            down(0,center-Offset(50f,0f));down(1,center+Offset(50f,0f))
            for(i in 1..10){moveTo(0,center-Offset(50f+i*6f,0f));moveTo(1,center+Offset(50f+i*6f,0f));advanceEventTime(20);move()}
            up(0);up(1)
        }
        assertTrue(original.fetchSemanticsNode().config[SemanticsProperties.StateDescription]!="Zoom 100 %")
        capture("correction-exam-overlay-zoom")
        val enlarged=zoomPercent()
        overlay.performTouchInput {click(center+Offset(0f,fittedHeight*.8f))}
        overlay.assertIsDisplayed() // Inside the zoomed image, outside its old unscaled bounds.
        overlay.performTouchInput {
            down(0,center-Offset(110f,0f));down(1,center+Offset(110f,0f))
            for(i in 1..10){moveTo(0,center-Offset(110f-i*6f,0f));moveTo(1,center+Offset(110f-i*6f,0f));advanceEventTime(20);move()}
            up(0);up(1)
        }
        assertTrue("Pinching inward reduces the zoom",zoomPercent()<enlarged)
        capture("correction-exam-overlay-dezoom")
        overlay.performTouchInput {doubleClick(center)}
        assertTrue(zoomPercent()>100)
        overlay.performTouchInput {doubleClick(center)}
        assertEquals(100,zoomPercent())
        overlay.performTouchInput {click(Offset(8f,8f))}
        ui.onNodeWithTag("exam-image-overlay").assertDoesNotExist()
        ui.onNodeWithText(q.choices.first()).performScrollTo().performClick()
        ui.onNodeWithText("Vérifier").performClick()
        ui.onNodeWithText("La banque Exam1 ne fournit pas de commentaire",substring=true).assertDoesNotExist()
    }

    @Test fun theSpeakingMouthUsesTheActualFaceColourOnEveryPageBackground() {
        for(background in listOf(android.graphics.Color.WHITE,0xFFFAF8F2.toInt(),0xFFE1F2EF.toInt()))for(open in listOf(.1f,.9f)) {
            val bitmap=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888)
            try {
                val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(background)
                PicoRenderer.draw(canvas,android.graphics.RectF(0f,0f,256f,256f),MascotMood.HAPPY,MascotPose.IDLE,0f,mouthOpen=open)
                assertEquals(bitmap.getPixel(80,160),bitmap.getPixel(88,136))
            } finally {bitmap.recycle()}
        }
    }

    @Test fun mathRecognitionInventoryIncludesExamChoicesAndPromptEquations() {
        val report=org.json.JSONArray()
        for(q in model.content!!.exam) {
            val choices=q.choices.mapIndexedNotNull {i,text->MathFormula.parse(text)?.let {org.json.JSONObject().put("index",i).put("source",text).put("tex",MathFormula.latex(it))}}
            val prompt=MathFormula.fragments(q.prompt).filter {it.formula!=null}
            val undecoded=q.choices.mapIndexedNotNull {i,text->i.takeIf {MathFormula.hasPossibleEquality(text)&&MathFormula.parse(text)==null}}
            val needsImageReview=q.prompt.contains(Regex("formule|équation|expression exacte",RegexOption.IGNORE_CASE))&&prompt.isEmpty()
            if(choices.isNotEmpty()||prompt.isNotEmpty()||undecoded.isNotEmpty()||needsImageReview) {
                report.put(org.json.JSONObject().put("id",q.id).put("answers",org.json.JSONArray(choices)).put("promptEquations",org.json.JSONArray(prompt.map {org.json.JSONObject().put("source",it.source).put("tex",MathFormula.latex(it.formula!!))}))
                    .put("unrecognisedAnswerIndices",org.json.JSONArray(undecoded)).put("imageReviewWithoutOcr",needsImageReview).put("image",q.image))
            }
        }
        val example=(0 until report.length()).map {report.getJSONObject(it)}.first {it.getString("id")=="20003"}
        assertEquals(4,example.getJSONArray("answers").length())
        val dir=File(context.getExternalFilesDir(null),"ui-0.43").apply {mkdirs()}
        File(dir,"math-inventory.json").writeText(org.json.JSONObject().put("examQuestions",model.content!!.exam.size).put("cases",report).toString(2))
        val native=examples().first().copy(id="inline-maths",prompt="Pour R = 10 Ω et I = 2 A, calcule U. Quelle formule utilise P = U² / R ?")
        ui.runOnIdle {model.startQuestions("Équations dans l’énoncé",listOf(native))}
        ui.onNodeWithTag("question-text-region").assertIsDisplayed();capture("correction-inline-equations")
    }

    private fun capture(name: String) {
        ui.waitForIdle()
        repeat(2) {
            val frame=CountDownLatch(1)
            instrumentation.runOnMainSync {ui.activity.window.decorView.let {v->v.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};v.invalidate()} }
            assertTrue(frame.await(5,TimeUnit.SECONDS))
        }
        val dir=File(context.getExternalFilesDir(null),if(name.startsWith("intro47-"))"ui-0.47-intro" else "ui-0.43").apply {mkdirs()}
        val bitmap=instrumentation.uiAutomation.takeScreenshot()
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
}
