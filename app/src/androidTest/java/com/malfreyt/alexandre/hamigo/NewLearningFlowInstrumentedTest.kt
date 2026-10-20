package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Real input/drag/hole hit testing with RAM-only learning, no account or microphone permission. */
@RunWith(AndroidJUnit4::class)
class NewLearningFlowInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var owner:AppModel
    private lateinit var model:AppModel
    private lateinit var content:Content
    @Before fun prepare() {
        ui.prepareTestWindow()
        ui.runOnUiThread {WindowInspector.getGlobalWindowViews().forEach {it.windowInsetsController?.hide(WindowInsets.Type.ime())}}
        ui.waitUntil(30_000){WindowInspector.getGlobalWindowViews().all {(it.rootWindowInsets?.getInsets(WindowInsets.Type.ime())?.bottom ?: 0)==0}}
        content=Content(context,examSnapshot=null)
        ui.runOnUiThread {
            val memory=DiagnosticContext(context)
            owner=AppModel().apply {
                progress=Progress(memory,sideEffects=false)
                diagnostics=DiagnosticSettings(memory)
                interactionFeedback=AppFeedback(memory)
            }
            model=AppModel().apply {initializeDiagnostics(context,owner,this@NewLearningFlowInstrumentedTest.content)}
        }
    }
    @After fun close()=ui.runOnUiThread {
        if(::model.isInitialized)model.disposeDiagnostics()
        if(::owner.isInitialized)owner.interactionFeedback.close()
    }
    private fun show() {
        ui.setContent {HamigoTheme {FeedbackHost(owner.interactionFeedback) {
            CompositionLocalProvider(LocalContext provides model.diagnosticContext,LocalDiagnosticsCovered provides false) {
                HamigoAppContent(model,model)
            }
        }}}
    }
    private fun capture(name:String) {
        val file=File(context.getExternalFilesDir(null),"parcours-progressif/$name.png");file.parentFile!!.mkdirs()
        file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun revealPath(tag:String):SemanticsNodeInteraction {
        val list=ui.onNodeWithTag("path-list")
        list.performScrollToNode(hasTestTag(tag))
        val target=ui.onNodeWithTag(tag).performScrollTo()
        val header=ui.onNodeWithTag("path-sticky-header").getUnclippedBoundsInRoot()
        val bounds=target.getUnclippedBoundsInRoot()
        val viewport=list.getUnclippedBoundsInRoot()
        // Keep the real touch away from both the sticky header and the jump FAB.
        val center=(header.bottom.value+viewport.bottom.value-88f)/2f
        val shift=((bounds.top.value+bounds.bottom.value)/2f-center)*context.resources.displayMetrics.density
        list.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){it(0f,shift)}
        return target.assertIsDisplayed()
    }
    private fun captureDialog(name:String) {
        ui.awaitForegroundFrames()
        val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        val file=File(context.getExternalFilesDir(null),"parcours-progressif/$name.png")
        file.parentFile!!.mkdirs()
        try {file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
    @Test fun personalizedSignalsRespectTheLearnedAlphabetAndDoNotRevealTheAnswerByItsShape() {
        val template=JSONObject().put("type","morse-call").put("characters","ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789")
            .put("sourceText","Quel indicatif entends-tu ?").put("fallback","F4ABC")
        val q=Question("debug-call","Quel indicatif entends-tu ?",listOf("F4ABC","F4ABD","F4ABE","F4ABF"),0,"Écoute le groupe.",kind="morseListen",template=template.toString())
        val calls=(0..100).map {seed->
            val result=QuestionVariants.create(q,"Sam Durand",listOf("Mia Murphy"),seed=seed)
            assertEquals(q.id,result.id)
            assertEquals(MorseReference.decode(result.bands.first()).output,result.choices[result.answer])
            assertEquals(4,result.choices.distinct().size)
            assertTrue(result.choices.all {it.matches(Regex("F[0-9][A-Z]{3}"))})
            result.choices[result.answer]
        }
        assertTrue(calls.distinct().size>80)
        val name=Question("debug-name","Compose {{name}}.",emptyList(),0,"",kind="morseEncode",
            template=JSONObject().put("type","morse-name").put("characters","ETIMAS").put("fallback","MIMI").put("sourceText","Compose {{name}}.").toString())
        repeat(30) {seed->
            val result=QuestionVariants.create(name,"Sam Durand",emptyList(),seed=seed)
            val decoded=MorseReference.decode(result.bands.first()).output
            assertTrue(decoded.all {it in "ETIMAS"})
            assertFalse(result.prompt.contains("Durand"))
        }
        val digits=q.copy(template=JSONObject().put("type","morse-call").put("characters","0123456789").put("length",2).put("sourceText","Quel nombre entends-tu ?").toString())
        assertTrue((0..20).map {seed->QuestionVariants.create(digits,"Sam",emptyList(),seed=seed).let {r->
            assertTrue(r.choices.all {it.matches(Regex("[0-9]{2}"))});r.choices[r.answer]
        }}.distinct().size>10)
    }
    @Test fun writtenAndSpokenFallbackAnswersCanBeEditedAndCheckedWithoutChangingRealProgress() {
        val before=owner.progress.export()
        ui.runOnIdle {model.startQuestions("Rappel libre",listOf(
            Question("debug-text","Dans quelle unité mesure-t-on un courant ?",emptyList(),0,"L’intensité se mesure en ampères.",kind="text",acceptedAnswers=listOf("ampère","ampères","A")),
            Question("debug-spoken","Épelle la lettre F.",emptyList(),0,"Le mot international de F est Foxtrot.",kind="spoken",acceptedAnswers=listOf("Foxtrot"))
        ))}
        show()
        ui.onNodeWithTag("question-text-input").performTextInput("AMPERE")
        capture("written-keyboard")
        ui.onNodeWithText("Vérifier").performClick()
        ui.runOnIdle {assertEquals(true,model.session!!.feedback)}
        ui.onNodeWithText("Continuer",substring=false).performClick()
        ui.onNodeWithTag("question-microphone").assertIsNotEnabled()
        ui.onNodeWithTag("question-use-keyboard").performClick()
        ui.onNodeWithTag("question-text-input").performTextInput("Foxtrot")
        capture("spoken-keyboard-fallback")
        ui.onNodeWithText("Vérifier").performClick()
        ui.runOnIdle {assertEquals(true,model.session!!.feedback);assertEquals(before,owner.progress.export())}
    }
    @Test fun aComponentCanBeDraggedToTheMeasuredCircuitAndTheOtherSlotHasAKeyboardFreeAlternative() {
        val q=Question("debug-circuit","Place les appareils pour mesurer le courant et la tension.",listOf("Ampèremètre","Voltmètre","Ohmmètre","Fréquencemètre"),0,
            "L’ampèremètre se place en série ; le voltmètre est en parallèle sur la résistance.",kind="circuit",visual="circuit:measurement",
            pairs=listOf(PairItem("Emplacement A","Ampèremètre"),PairItem("Emplacement B","Voltmètre")))
        ui.runOnIdle {model.startQuestions("Mesurer",listOf(q))};show()
        ui.onNodeWithTag("circuit-component-Ampèremètre").performScrollTo()
        val start=ui.onNodeWithTag("circuit-component-Ampèremètre").fetchSemanticsNode().boundsInRoot.center
        val end=ui.onNodeWithTag("circuit-slot-0").assertIsDisplayed().fetchSemanticsNode().boundsInRoot.center
        ui.onRoot().performTouchInput {down(start);advanceEventTime(700);moveTo(end);up()}
        ui.onNodeWithTag("circuit-slot-0").assertTextContains("Ampèremètre",substring=true)
        ui.onNodeWithTag("circuit-slot-1").performClick()
        ui.onAllNodesWithText("Voltmètre",substring=false).onLast().performClick()
        capture("circuit-measurement-completed")
        ui.onNodeWithText("Vérifier").performClick()
        ui.runOnIdle {assertEquals(true,model.session!!.feedback)}
    }
    @Test fun theRealKeyboardKeepsTheWrittenAnswerAboveBothFloatingTools() {
        val short=Question("debug-spoken-ime","Épelle la lettre F avec le mot de l’alphabet international.",emptyList(),0,"F s’épelle Foxtrot.",kind="spoken",acceptedAnswers=listOf("Foxtrot"))
        val long=short.copy(id="debug-spoken-ime-long",prompt="Épelle l’indicatif complet avec l’alphabet international.",acceptedAnswers=listOf("Foxtrot quatre Alfa Bravo Charlie Delta Echo Foxtrot"))
        ui.runOnIdle {model.startQuestions("Réponse au clavier",listOf(short))};show()
        for((index,question) in listOf(short,long).withIndex()) {
        if(index>0)ui.runOnIdle {model.startQuestions("Réponse au clavier",listOf(question))}
        ui.onNodeWithTag("question-use-keyboard").performClick()
        ui.waitUntil(30_000){WindowInspector.getGlobalWindowViews().any {it.rootWindowInsets?.isVisible(WindowInsets.Type.ime())==true}}
        ui.onNodeWithTag("question-text-input").performTextInput(question.acceptedAnswers.first())
        try { ui.waitUntil(10_000) {
            val field=ui.onNodeWithTag("question-text-input").fetchSemanticsNode().boundsInRoot
            val notes=ui.onNodeWithContentDescription("Ouvrir le brouillon").fetchSemanticsNode().boundsInRoot
            val calculator=ui.onNodeWithContentDescription("Ouvrir la calculatrice").fetchSemanticsNode().boundsInRoot
            val microphone=ui.onAllNodesWithTag("question-microphone").fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            val stage=ui.onNodeWithTag("question-stage").fetchSemanticsNode().boundsInRoot
            field.height>=with(ui.density){48.dp.toPx()}&&notes.height>=with(ui.density){48.dp.toPx()}&&
                calculator.height>=with(ui.density){48.dp.toPx()}&&field.top>=stage.top&&field.bottom<=stage.bottom&&
                (if(microphone!=null)microphone.height>0f&&field.bottom<=minOf(notes.top,calculator.top,microphone.top)
                else !field.overlaps(notes)&&!field.overlaps(calculator))
        }} finally {
        println("IME geometry: stage="+ui.onNodeWithTag("question-stage").fetchSemanticsNode().boundsInRoot+
            " field="+ui.onNodeWithTag("question-text-input").fetchSemanticsNode().boundsInRoot+
            " tools="+ui.onNodeWithTag("question-tools").fetchSemanticsNode().boundsInRoot+
            " verify="+ui.onNodeWithText("Vérifier").fetchSemanticsNode().boundsInRoot)
        ui.runOnUiThread {WindowInspector.getGlobalWindowViews().forEach {view->
            println("IME window: ${view.width}x${view.height}, ime="+view.rootWindowInsets?.getInsets(WindowInsets.Type.ime()))
        }}
        val suffix=if(index==0)"" else "-long"
        val file=File(context.getExternalFilesDir(null),"parcours-progressif/spoken-real-ime$suffix.png");file.parentFile!!.mkdirs()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().let {bitmap->
            file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        }
        }
        ui.onNodeWithText("Vérifier").assertIsDisplayed()
        ui.onNodeWithText("Vérifier").performClick()
        ui.runOnIdle {assertEquals(true,model.session!!.feedback)}
        }
    }
    @Test fun speechControlsStayCentredAtTheBottomAndExpectedLongAnswersHaveMoreThanOneLine() {
        val short=Question("debug-spoken-layout","Épelle la lettre F.",emptyList(),0,"F s’épelle Foxtrot.",kind="spoken",acceptedAnswers=listOf("Foxtrot"))
        ui.runOnIdle {model.startQuestions("Réponse orale",listOf(short))};show()
        fun bounds(tag:String)=ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val stage=bounds("question-stage");val mic=bounds("question-microphone");val keyboard=bounds("question-use-keyboard")
        assertEquals(stage.center.x,mic.center.x,1f)
        assertTrue(keyboard.right<mic.left);assertEquals(mic.center.y,keyboard.center.y,1f)
        assertTrue("The main action has generous bottom space",mic.bottom<=stage.bottom-with(ui.density){40.dp.toPx()})
        assertTrue("The main microphone is larger",mic.width>=with(ui.density){100.dp.toPx()})
        val shortField=bounds("question-text-input")
        assertTrue(shortField.bottom<mic.top)
        for(label in listOf("Ouvrir le brouillon","Ouvrir la calculatrice")) {
            val tool=ui.onNodeWithContentDescription(label).fetchSemanticsNode().boundsInRoot
            assertFalse(mic.overlaps(tool));assertFalse(keyboard.overlaps(tool))
            assertTrue("The tools retain their original right-hand side",tool.left>stage.center.x)
        }
        ui.onNodeWithText("Maintiens pour parler · relâche pour terminer").assertDoesNotExist()
        ui.onNodeWithText("Le service vocal Android traite l’audio ; Hamigo ne l’enregistre pas.").assertDoesNotExist()
        capture("spoken-bottom-centred-short")
        val long=short.copy(prompt="Épelle l’indicatif complet.",acceptedAnswers=listOf("Foxtrot quatre Alfa Bravo Charlie Delta Echo Foxtrot"))
        ui.runOnIdle {model.startQuestions("Réponse orale",listOf(long))}
        val longField=bounds("question-text-input")
        assertTrue("Expected longer speech gets a genuinely taller text field",longField.height>shortField.height+with(ui.density){12.dp.toPx()})
        capture("spoken-bottom-centred-long")
    }
    @Test fun aNarrowShortWindowSeparatesSpeechFromTheUnmovedToolRow() {
        val width=mutableStateOf(320.dp)
        ui.runOnIdle {model.startQuestions("Réponse orale",listOf(Question("debug-small-spoken","Épelle F.",emptyList(),0,"Foxtrot.",kind="spoken",acceptedAnswers=listOf("Foxtrot"))))}
        ui.setContent {HamigoTheme {FeedbackHost(owner.interactionFeedback) {
            val actual=LocalWindowInfo.current
            val density=LocalDensity.current
            val simulated=remember(actual,density) {object:WindowInfo by actual {
                override val containerSize:IntSize get()=with(density){IntSize(width.value.roundToPx(),480.dp.roundToPx())}
            }}
            CompositionLocalProvider(LocalContext provides model.diagnosticContext,LocalDiagnosticsCovered provides false,LocalWindowInfo provides simulated) {
                Box(Modifier.width(width.value).height(480.dp)){HamigoAppContent(model,model)}
            }
        }}}
        for(dp in listOf(320,360,400)) {
            ui.runOnIdle {width.value=dp.dp}
            fun bounds(tag:String)=ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
            val stage=bounds("question-stage");val microphone=bounds("question-microphone");val keyboard=bounds("question-use-keyboard")
            assertEquals(stage.center.x,microphone.center.x,1f)
            assertTrue(keyboard.right<microphone.left)
            val notes=ui.onNodeWithContentDescription("Ouvrir le brouillon").fetchSemanticsNode().boundsInRoot
            val calculator=ui.onNodeWithContentDescription("Ouvrir la calculatrice").fetchSemanticsNode().boundsInRoot
            assertEquals(notes.center.y,calculator.center.y,1f)
            assertTrue(notes.right<calculator.left)
            assertTrue(calculator.right<=stage.right&&calculator.left>stage.center.x)
            for(tool in listOf(notes,calculator)) {
                assertFalse("Speech and tools overlap at $dp dp",microphone.overlaps(tool))
                assertFalse("Keyboard and tools overlap at $dp dp",keyboard.overlaps(tool))
            }
            ui.onNodeWithTag("question-answers").performTouchInput {swipeUp()}
            val field=bounds("question-text-input")
            assertTrue(field.bottom<=minOf(microphone.top,notes.top,calculator.top))
            capture("spoken-short-window-$dp")
        }
    }
    @Test fun completeCwExchangesKeepEveryElementAndTheirPlaybackCanBeStopped() {
        val exchanges=content.courseQuestions.filter {it.morseComprehension}
        assertEquals(2,exchanges.size)
        exchanges.forEach {q->
            assertTrue(q.bands.first().length>240)
            val pcm=MorseSignalAudio.render(q.bands.first())
            assertEquals(q.bands.first().count {it=='.'||it=='-'},pcm.signalRanges.size)
            assertEquals(MorseSignalAudio.durationMs(q.bands.first()),pcm.samples.size*1000L/pcm.rate)
            assertTrue(pcm.samples.size*1000L/pcm.rate<MorseSignalAudio.MAX_DURATION_MS)
        }
        val before=owner.progress.export()
        ui.runOnIdle {model.startQuestions("Comprendre la CW",listOf(exchanges.first()))};show()
        ui.onNodeWithText("Écouter le signal").performScrollTo().performClick()
        ui.waitUntil(10_000){MorsePlayback.code!=null}
        ui.onNodeWithText("Arrêter le signal").assertIsDisplayed()
        capture("cw-complete-exchange-playing")
        ui.onNodeWithText("Arrêter le signal").performClick()
        ui.runOnIdle {assertNull(MorsePlayback.code);assertEquals(before,owner.progress.export())}
    }
    @Test fun simulatedSstvDefectsCanBeComparedAndAnsweredWithoutChangingRealProgress() {
        val questions=content.courseQuestions.filter {it.visual.startsWith("sstv:")}
        assertEquals(2,questions.size)
        val before=owner.progress.export()
        ui.runOnIdle {model.startQuestions("Comparer les réceptions SSTV",listOf(questions.first()))};show()
        questions.forEach {q->
            ui.runOnIdle {model.startQuestions("Comparer les réceptions SSTV",listOf(q))}
            ui.onNodeWithTag("sstv-comparison").performScrollTo().assertIsDisplayed()
            val caption=ui.onNodeWithTag("question-text-region").getUnclippedBoundsInRoot()
            val mire=ui.onNodeWithTag("sstv-comparison").getUnclippedBoundsInRoot()
            val firstAnswer=ui.onNodeWithText(q.choices.first(),substring=false).getUnclippedBoundsInRoot()
            assertTrue("The mire follows the question closely",mire.top-caption.bottom<=9.dp)
            assertTrue("The answers have more separation than the question/mire",firstAnswer.top-mire.bottom>mire.top-caption.bottom)
            capture(q.visual.replace(':','-'))
            ui.onNodeWithText(q.choices[q.answer]).performScrollTo().performClick()
            ui.onNodeWithText("Vérifier").performClick()
            ui.runOnIdle {assertEquals(true,model.session!!.feedback);assertEquals(before,owner.progress.export())}
        }
    }
    @Test fun practicalExamLessonsExposeOfficialLinksAndTheActualQuestionsShowTheToolsTip() {
        val registration=content.lessons.first {it.id=="l-examen-inscription"}
        val preparation=content.lessons.first {it.id=="l-examen-materiel"}
        val before=owner.progress.export()
        ui.runOnIdle {model.startLesson(registration)};show()
        registration.links.forEach {link->
            ui.onNodeWithTag("lesson-introduction-list").performScrollToNode(hasText(link.label))
            ui.onNodeWithText(link.label).assertIsDisplayed().performClick()
        }
        capture("exam-registration-links")
        ui.runOnIdle {assertNull(model.session);model.startLesson(preparation)}
        ui.onNodeWithTag("study-tools-hint").assertDoesNotExist()
        capture("exam-preparation-without-tools")
        val question=preparation.questions.first {it.studyToolsHint}
        ui.runOnIdle {model.startQuestions(preparation.title,listOf(question))}
        ui.onNodeWithTag("study-tools-hint").performScrollTo().assertIsDisplayed()
        ui.onNodeWithContentDescription("Ouvrir la calculatrice").assertIsDisplayed()
        ui.onNodeWithContentDescription("Ouvrir le brouillon").assertIsDisplayed()
        capture("exam-question-tools-hint")
        ui.runOnIdle {assertEquals(before,owner.progress.export())}
    }
    @Test fun reviewedPracticeSituationsRenderAndAcceptTheirAnswersWithoutChangingRealProgress() {
        val ids=listOf(
            "q-078083d5-28e4-4d5e-a11a-09a520e46e8c", // Long instrumental decision.
            "q-e4f65301-fe2b-42a9-96eb-e7840b6c96e3", // Ideal LC, before its real-loss contrast.
            "q-c2f6cf3c-1d44-4826-ac06-cb0b043b05de", // Delayed logic transfer.
            "q-f87ad02b-7450-4823-b482-0b2e93977d09", // Energy over two phases.
            "q-d87a3efe-a23f-45fd-8cbd-6c4ec7a0310d", // dB applied to an SDR filter.
            "q-d5e71737-dfeb-4938-b53d-38a40f9b1704" // Preserve repeated letters in free recall.
        )
        val questions=ids.map {content.allQuestions.getValue(it)}
        val before=owner.progress.export()
        ui.runOnIdle {model.startQuestions("Applications et reprises",listOf(questions.first()))};show()
        questions.forEachIndexed {index,question->
            ui.runOnIdle {model.startQuestions("Applications et reprises",listOf(question))}
            capture("practice-density-$index-start")
            when(question.kind) {
                "number" -> ui.onNodeWithTag("question-number-input").performScrollTo().performClick()
                    .performTextInput(question.value.toString())
                "text" -> ui.onNodeWithTag("question-text-input").performScrollTo().performClick()
                    .performTextInput(question.acceptedAnswers.first())
                else -> ui.onNodeWithText(question.choices[question.answer]).performScrollTo().performClick()
            }
            capture("practice-density-$index-answer")
            ui.onNodeWithText("Vérifier").performClick()
            ui.runOnIdle {assertEquals("Reviewed answer ${question.id}",true,model.session!!.feedback)}
            capture("practice-density-$index-feedback")
            ui.runOnIdle {assertEquals(before,owner.progress.export())}
        }
    }
    @Test fun independentWrittenDropdownAndMixedRowsCanBeAnsweredAndCorrected() {
        ui.runOnIdle {model.tour.finish();model.startQuestions("Lignes",listOf(DiagnosticUiExamples.questions.first()))}
        show()
        for((index,question) in DiagnosticUiExamples.questions.filter {it.kind=="rows"}.withIndex()) {
            ui.runOnIdle {model.startQuestions("Lignes",listOf(question))}
            for(row in question.rows) {
                if(row.choices.isEmpty())ui.onNodeWithTag("answer-row-input-${row.id}").performScrollTo().performTextInput(row.acceptedAnswers.first())
                else {
                    ui.onNodeWithTag("answer-row-select-${row.id}").performScrollTo().performClick()
                    ui.onNodeWithText(row.choices[row.answer],substring=false).performClick()
                }
            }
            capture("rows-$index-answered")
            ui.onNodeWithText("Vérifier").performClick()
            ui.runOnIdle {assertEquals(true,model.session!!.feedback)}
            capture("rows-$index-feedback")
        }
    }
    @Test fun everyActualIntroductionHasPreparedNarrationWithoutUnresolvedAuditMarkers() {
        val entries=NarrationAudit.entries(content.chapters)
        assertEquals(content.lessons.size,entries.size)
        val flagged=entries.filter {it.warnings.isNotEmpty()}
        assertTrue(flagged.joinToString("\n") {"${it.lesson.id}: ${it.warnings}"},flagged.isEmpty())
        assertTrue(entries.all {it.spoken.isNotEmpty()&&it.spoken.all {segment->segment.length<=4000}})
        val rows=content.courseQuestions.filter {it.kind=="rows"}
        assertEquals(2,rows.size)
        rows.forEach {question->
            assertTrue(question.rows.size>=2)
            val narration=QuestionNarration.text(question)
            question.rows.filter {it.choices.isEmpty()}.forEach {row->
                row.acceptedAnswers.forEach {assertFalse("Written answer leaked in narration",narration.contains(it))}
            }
        }
    }
    @Test fun theGuideTransmitsTargetTouchesAndCompletesAllTabsWithoutGrantingMastery() {
        val before=owner.progress.export()
        ui.runOnIdle {model.beginTour()};show()
        ui.waitForIdle()
        capture("guide-intro-target")
        val introTarget=ui.onNodeWithTag("path-lesson-$HamigoWelcomeLesson").fetchSemanticsNode().boundsInRoot
        val introCard=ui.onNodeWithTag("hamigo-tour-card").fetchSemanticsNode().boundsInRoot
        assertFalse("The welcome course remains touchable with a large font on a narrow screen",introCard.contains(introTarget.center))
        ui.onNodeWithTag("path-lesson-$HamigoWelcomeLesson").performTouchInput {click(center)}
        ui.runOnIdle {assertEquals(1,model.tour.step);assertEquals(HamigoWelcomeLesson,model.lesson?.id)}
        ui.onNodeWithTag("hamigo-tour-lesson-pico").assertIsDisplayed()
        capture("guide-reading")
        ui.onNodeWithTag("hamigo-tour-veil").assertDoesNotExist()
        val welcome=content.lessons.first {it.id==HamigoWelcomeLesson}
        val lastIntroItem=1+welcome.body.size+(if(welcome.formula.isNotBlank())1 else 0)+content.referencesFor(welcome).size+(if(welcome.links.isEmpty())0 else 1)
        ui.onNodeWithTag("lesson-introduction-list").performScrollToIndex(lastIntroItem)
        // With a large font the last item can exceed the space above the fixed hint.
        // Reach the actual end, including the reserved padding, instead of stopping at its top.
        ui.onNodeWithTag("lesson-introduction-list").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){it(0f,100_000f)}
        val lastParagraph=ui.onNodeWithText(welcome.body.last(),substring=false).fetchSemanticsNode().boundsInRoot
        val hint=ui.onNodeWithTag("hamigo-tour-lesson-hint").fetchSemanticsNode().boundsInRoot
        capture("guide-reading-end-measured")
        assertFalse("The last paragraph can be read without being covered by the fixed hint: text=$lastParagraph, hint=$hint",lastParagraph.overlaps(hint))
        listOf("learning-tool-calculator","learning-tool-scratchpad").forEach {tag->
            ui.onNodeWithTag(tag).assertDoesNotExist()
        }
        capture("guide-reading-end")
        ui.onNodeWithTag("hamigo-tour-next").performTouchInput {click(center)}
        ui.onNodeWithTag("hamigo-tour").assertDoesNotExist()
        ui.onNodeWithTag("hamigo-tour-next").assertDoesNotExist()
        ui.runOnIdle {
            val untouched=model.progress.export()
            model.leaveSession()
            assertEquals(1,model.tour.step)
            assertEquals(HamigoWelcomeLesson,model.lesson?.id)
            assertEquals(untouched,model.progress.export())
        }
        ui.onNodeWithTag("hamigo-tour-lesson-hint").assertIsDisplayed()
        ui.onNodeWithTag("hamigo-tour-next").performClick()
        ui.runOnIdle {
            assertNotNull(model.session)
            while(!model.session!!.done) {
                val q=model.session!!.current!!
                model.answer(true,display=q.choices.getOrNull(q.answer).orEmpty(),choiceIndex=q.answer);model.next()
            }
        }
        capture("guide-results")
        ui.onNodeWithTag("results-list").performScrollToNode(hasText("Revenir au parcours",substring=false))
        ui.onNodeWithText("Revenir au parcours",substring=false).performClick()
        listOf("practice","resources","friends","profile","path").forEachIndexed {index,destination->
            ui.runOnIdle {assertEquals(3+index,model.tour.step)}
            ui.onNodeWithTag("hamigo-tour-veil").assertDoesNotExist()
            ui.onNodeWithTag("hamigo-tour-tab-arrow").assertIsDisplayed()
            if(index==0)ui.runOnIdle {
                model.navigateTab("profile");assertEquals("path",model.route);assertEquals(3,model.tour.step)
                model.startLesson(content.lessons.last());assertNull(model.lesson)
                model.startQuestions("Blocked",listOf(content.courseQuestions.first()));assertNull(model.session)
                model.resource=content.references.first();assertNull(model.resource)
            }
            ui.onNodeWithTag("navigation-icon-$destination",useUnmergedTree=true).performTouchInput {click(center)}
            ui.runOnIdle {assertEquals(destination,model.route);assertEquals(4+index,model.tour.step)}
            capture("guide-$destination")
        }
        ui.onNodeWithTag("hamigo-tour-next").assertDoesNotExist()
        ui.onNodeWithTag("hamigo-tour-skip-all").assertDoesNotExist()
        ui.onNodeWithTag("hamigo-tour-veil").assertIsDisplayed()
        ui.onNodeWithText("Commencer",substring=false).assertIsDisplayed()
        ui.onNodeWithTag("hamigo-tour-final-pico").assertIsDisplayed()
        ui.onNodeWithTag("hamigo-tour-finish").performTouchInput {click(center)}
        ui.runOnIdle {assertFalse(model.tour.active);assertEquals(before,owner.progress.export())}
    }
    @Test fun passingTheGuideRequiresConfirmationAndCancellationKeepsTheCurrentStep() {
        val before=owner.progress.export()
        ui.runOnIdle {model.beginTour()};show()
        ui.onNodeWithTag("hamigo-tour-skip-all").performClick()
        ui.onNodeWithText("Passer le tutoriel ?",substring=false).assertIsDisplayed()
        ui.runOnIdle {assertEquals(0,model.tour.step)}
        ui.onNodeWithTag("hamigo-tour-continue-guide").performClick()
        ui.runOnIdle {assertEquals(0,model.tour.step);assertFalse(model.tour.skipRequested)}
        ui.onNodeWithTag("hamigo-tour-skip-all").performClick()
        ui.onNodeWithTag("hamigo-tour-confirm-skip").performClick()
        ui.runOnIdle {assertFalse(model.tour.active);assertEquals(before,owner.progress.export())}
    }

    @Test fun ignoredLessonsChangeOnlyThePathAndExplainHowToEarnXpAfterReintegration() {
        val chapter=content.chapters.first()
        val lesson=chapter.lessons.first()
        ui.runOnIdle {model.tour.finish();DiagnosticData.setCompleted(model,emptySet());model.route="path"}
        val ownerBefore=owner.progress.export()
        val xpBefore=model.progress.xp
        val answersBefore=model.progress.totalAnswers
        val reviewsBefore=model.progress.reviews
        show()
        val list=ui.onNodeWithTag("path-list")
        list.performScrollToNode(hasTestTag("lesson-menu-"+lesson.id))
        revealPath("lesson-menu-"+lesson.id)
        captureDialog("ignore-lesson-menu-target")
        ui.onNodeWithTag("lesson-menu-"+lesson.id).performClick()
        ui.onNodeWithText("Ignorer ce cours",substring=false).performClick()
        ui.onNodeWithText("Ignorer ce cours ?",substring=false).assertIsDisplayed()
        captureDialog("ignore-lesson-confirmation")
        ui.onNodeWithText("sans gagner d’XP",substring=true).assertIsDisplayed()
        ui.onNodeWithTag("path-cancel-ignore").performClick()
        ui.runOnIdle {
            assertFalse(lesson.id in model.ignoredLessons)
            assertEquals(xpBefore,model.progress.xp)
        }
        revealPath("lesson-menu-"+lesson.id).performClick()
        ui.onNodeWithText("Ignorer ce cours",substring=false).performClick()
        ui.onNodeWithTag("path-confirm-ignore").performClick()
        ui.runOnIdle {
            assertTrue(lesson.id in model.ignoredLessons)
            assertTrue(lesson.id in model.pathCompletedLessons)
            assertFalse(lesson.id in model.progress.completed)
            assertNotEquals(lesson.id,content.nextLesson(model.pathCompletedLessons)?.id)
            assertEquals(xpBefore,model.progress.xp)
            assertEquals(answersBefore,model.progress.totalAnswers)
        }
        revealPath("lesson-copy-"+lesson.id).performClick()
        ui.onNodeWithText("Ouvrir ce cours ignoré ?",substring=false).assertIsDisplayed()
        ui.onNodeWithText("Annuler",substring=false).performClick()
        ui.runOnIdle {assertNull(model.lesson);assertNull(model.session);assertTrue(lesson.id in model.ignoredLessons)}
        revealPath("lesson-copy-"+lesson.id).performClick()
        ui.onNodeWithText("Réintégrer et ouvrir",substring=false).performClick()
        ui.runOnIdle {
            assertEquals(lesson.id,model.lesson?.id)
            assertFalse(lesson.id in model.ignoredLessons)
            assertFalse(lesson.id in model.pathCompletedLessons)
            assertEquals(xpBefore,model.progress.xp)
            model.lesson=null
        }
        list.performScrollToNode(hasTestTag("lesson-menu-"+lesson.id))
        revealPath("lesson-menu-"+lesson.id).performClick()
        ui.onNodeWithText("Ignorer ce cours",substring=false).performClick()
        ui.onNodeWithTag("path-confirm-ignore").performClick()
        revealPath("lesson-menu-"+lesson.id).performClick()
        ui.onNodeWithText("Réintégrer ce cours",substring=false).performClick()
        ui.runOnIdle {
            assertFalse(lesson.id in model.ignoredLessons)
            assertFalse(lesson.id in model.pathCompletedLessons)
            assertEquals(xpBefore,model.progress.xp)
            assertEquals(answersBefore,model.progress.totalAnswers)
            assertEquals(reviewsBefore,model.progress.reviews)
            assertEquals(ownerBefore,owner.progress.export())
        }
    }

    @Test fun aChapterLongPressExplainsSkippingWithoutXpAndCanRestoreWithoutInventingMastery() {
        val chapter=content.chapters.first {it.lessons.size>1}
        ui.runOnIdle {
            model.tour.finish();DiagnosticData.setCompleted(model,setOf(chapter.lessons.first().id));model.route="path"
        }
        val completedBefore=model.progress.completed
        val xpBefore=model.progress.xp
        val answersBefore=model.progress.totalAnswers
        show()
        val list=ui.onNodeWithTag("path-list")
        list.performScrollToNode(hasTestTag("chapter-"+chapter.id))
        ui.onNodeWithTag("chapter-started-badge-"+chapter.id,useUnmergedTree=true).assertExists()
        ui.onNodeWithTag("chapter-"+chapter.id).performTouchInput {longClick(center)}
        ui.onNodeWithText("Ignorer ce chapitre",substring=false).performClick()
        ui.onNodeWithText("Ignorer ce chapitre ?",substring=false).assertIsDisplayed()
        captureDialog("ignore-chapter-confirmation")
        ui.onNodeWithText("sans gagner d’XP",substring=true).assertIsDisplayed()
        ui.onNodeWithTag("path-cancel-ignore").performClick()
        ui.runOnIdle {
            assertTrue(chapter.lessons.none {it.id in model.ignoredLessons})
            assertEquals(xpBefore,model.progress.xp)
        }
        ui.onNodeWithTag("chapter-"+chapter.id).performTouchInput {longClick(center)}
        ui.onNodeWithText("Ignorer ce chapitre",substring=false).performClick()
        ui.onNodeWithTag("path-confirm-ignore").performClick()
        ui.runOnIdle {
            assertTrue(chapter.lessons.all {it.id in model.ignoredLessons})
            assertEquals(completedBefore,model.progress.completed)
            assertEquals(xpBefore,model.progress.xp)
            assertEquals(answersBefore,model.progress.totalAnswers)
        }
        capture("path-chapter-ignored")
        ui.onNodeWithTag("chapter-"+chapter.id).performTouchInput {longClick(center)}
        ui.onNodeWithText("Réintégrer ce chapitre",substring=false).performClick()
        ui.runOnIdle {
            assertTrue(chapter.lessons.none {it.id in model.ignoredLessons})
            assertEquals(completedBefore,model.progress.completed)
            assertEquals(xpBefore,model.progress.xp)
        }
        capture("path-chapter-started-dashed")
    }

    @Test fun startingAnIgnoredLessonFromAnotherPageRequiresReintegration()=assertIgnoredLaunchGuard("lesson")
    @Test fun previewingAnIgnoredLessonFromAnotherPageRequiresReintegration()=assertIgnoredLaunchGuard("preview")
    @Test fun startingAnIgnoredLessonQuizFromAnotherPageRequiresReintegration()=assertIgnoredLaunchGuard("questions")

    private fun assertIgnoredLaunchGuard(mode:String) {
        val lesson=content.lessons.first()
        val ownerBefore=owner.progress.export()
        ui.runOnIdle {
            model.tour.finish();DiagnosticData.setCompleted(model,emptySet());model.route="resources"
        }
        val xpBefore=model.progress.xp
        val answersBefore=model.progress.totalAnswers
        val reviewsBefore=model.progress.reviews
        ui.runOnIdle {model.setLessonsIgnored(setOf(lesson.id),true)}
        fun request()=ui.runOnIdle {
            when(mode) {
                "lesson"->model.startLesson(lesson)
                "preview"->model.previewLesson(lesson)
                "questions"->model.startQuestions(lesson.title,LessonSessionBuilder.create(lesson),lesson.id)
                else->error("Unknown launch mode")
            }
            assertNull(model.lesson)
            assertNull(model.session)
            assertEquals(lesson.id,model.ignoredLessonRequest?.first?.id)
            assertTrue(lesson.id in model.ignoredLessons)
        }
        show()
        request()
        ui.onNodeWithText("Suivre ce cours ?",substring=false).assertIsDisplayed()
        ui.onNodeWithText("Annuler",substring=false).performClick()
        ui.runOnIdle {
            assertNull(model.ignoredLessonRequest)
            assertNull(model.lesson)
            assertNull(model.session)
            assertTrue(lesson.id in model.ignoredLessons)
            assertEquals(xpBefore,model.progress.xp)
        }
        request()
        ui.onNodeWithText("Désignorer et ouvrir",substring=false).performClick()
        ui.runOnIdle {
            assertNull(model.ignoredLessonRequest)
            assertFalse(lesson.id in model.ignoredLessons)
            assertFalse(lesson.id in model.pathCompletedLessons)
            if(mode=="questions") {
                assertNull(model.lesson)
                assertEquals(lesson.id,model.session?.lessonId)
                assertEquals("resources",model.session?.returnRoute)
            } else {
                assertEquals(lesson.id,model.lesson?.id)
                assertNull(model.session)
                assertEquals(mode=="preview",model.lessonPreviewOnly)
            }
            assertEquals(xpBefore,model.progress.xp)
            assertEquals(answersBefore,model.progress.totalAnswers)
            assertEquals(reviewsBefore,model.progress.reviews)
            assertEquals(ownerBefore,owner.progress.export())
        }
    }

    @Test fun theFooterPicoEmitsOneSpecificFeedbackAndNeverChangesLearningProgress() {
        val events=java.util.concurrent.CopyOnWriteArrayList<FeedbackCue>()
        ui.runOnIdle {model.tour.finish();model.route="path";owner.interactionFeedback.observer={cue,_,_->events+=cue}}
        val before=model.progress.export()
        val ownerBefore=owner.progress.export()
        show()
        ui.onNodeWithTag("path-list").performScrollToNode(hasTestTag("path-goodbye"))
        ui.onNodeWithTag("path-footer-pico").performClick()
        assertEquals(1,events.count {it==FeedbackCue.PICO_FOOTER})
        ui.runOnIdle {assertEquals(before,model.progress.export());assertEquals(ownerBefore,owner.progress.export())}
        capture("path-footer-interactive")
    }

    @Test fun confirmingATutorialBackRequestLeavesTheIntroductionOnlyAfterConfirmation() {
        val before=owner.progress.export()
        ui.runOnIdle {model.beginTour();model.startLesson(content.lessons.first {it.id==HamigoWelcomeLesson})}
        show()
        ui.runOnIdle {model.tour.requestSkip(exitToPath=true)}
        ui.onNodeWithTag("hamigo-tour-continue-guide").performClick()
        ui.runOnIdle {assertEquals(1,model.tour.step);assertEquals(HamigoWelcomeLesson,model.lesson?.id)}
        ui.runOnIdle {model.tour.requestSkip(exitToPath=true)}
        ui.onNodeWithTag("hamigo-tour-confirm-skip").performClick()
        ui.runOnIdle {assertFalse(model.tour.active);assertNull(model.lesson);assertNull(model.session);assertEquals("path",model.route);assertEquals(before,owner.progress.export())}
    }

}
