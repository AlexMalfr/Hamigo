package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.os.Build
import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith

/** Exercises real user paths with reversible fixtures, restricted to the test emulator. */
@RunWith(AndroidJUnit4::class)
class ApplicationSmokeTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val saved=mutableMapOf<String,Map<String,Any?>>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish")||Build.FINGERPRINT.contains("generic")) {"Emulator fixtures only"}
            for(file in listOf("hamigo","hamigo_social","hamigo_secure")) {
                val prefs=context.getSharedPreferences(file,Context.MODE_PRIVATE)
                saved[file]=prefs.all.mapValues {(_,v)->if(v is Set<*>)v.toSet() else v}
                check(prefs.edit().clear().commit())
            }
            check(context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",true)
                .putBoolean("autoSync",false).putBoolean("reminderEnabled",false).putInt("hamigoTourStep",-1).commit())
            ProgressSyncScheduler.cancel(context)
        }
        override fun after() {
            saved.forEach {(file,values)->
                val edit=context.getSharedPreferences(file,Context.MODE_PRIVATE).edit().clear()
                values.forEach {(key,value)->when(value) {
                    is String->edit.putString(key,value);is Boolean->edit.putBoolean(key,value);is Int->edit.putInt(key,value)
                    is Long->edit.putLong(key,value);is Float->edit.putFloat(key,value)
                    is Set<*>->edit.putStringSet(key,value.filterIsInstance<String>().toSet())
                }}
                check(edit.commit())
            }
            stopMorse();ProgressSyncScheduler.schedule(context)
        }
    }
    private val ui=createAndroidComposeRule<MainActivity>()
    @get:org.junit.Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)

    @Before
    fun awaitContentAndDismissWelcomeWhenNeeded() {
        ui.waitUntil(timeoutMillis = 60_000) {
            exists("Commencer sur cet appareil") || exists("HAMIGO")
        }
        if (exists("Commencer sur cet appareil")) {
            ui.onNodeWithText("Commencer sur cet appareil").performClick()
        }
        awaitText("HAMIGO")
        ui.onNodeWithText("Parcours").assertIsDisplayed()
    }

    @Test
    fun memoFlashcardCanBeFlippedRatedAndLeftForTheProfile() {
        ui.onNodeWithText("Mémo").performClick()
        ui.onNodeWithTag("memo-library-title").assertIsDisplayed()
        ui.onNodeWithTag("memo-row-nato").performScrollTo().performClick()
        ui.onNodeWithText("Réviser avec les flashcards").performScrollTo().performClick()

        awaitText("Retourner la carte")
        ui.onNodeWithText("Retourner la carte").assertIsDisplayed()
        // The selected card may vary with saved review dates; the recall interaction must not.
        ui.onNodeWithText("Retourner la carte").performClick()
        awaitText("Comment était le rappel ?")
        ui.onNodeWithText("Bien").assertIsEnabled().performClick()
        awaitText("Retourner la carte")

        quitQuiz()
        ui.onNodeWithTag("memo-library-title").assertIsDisplayed()
        ui.onNodeWithText("Moi").performClick()
        ui.onNodeWithContentDescription("Réglages").assertExists()
        ui.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasText("Cette semaine"))
        ui.onNodeWithText("Cette semaine").assertIsDisplayed()
    }

    @Test
    fun firstLessonShowsCorrectionAndChecksTheMatchingWidget() {
        val model=ViewModelProvider(ui.activity)[AppModel::class.java]
        val lesson=model.content!!.lessons.first {it.id=="c01-l01"}
        val recall=lesson.questions.first {it.id=="q-f39b165e-c14d-43f1-be43-a5084daf9db4"}
        val matching=lesson.questions.first {it.id=="q-eb65542e-1ed8-4e54-9bdc-6bf695b1fd29"}
        ui.runOnIdle {model.startLesson(lesson)}
        ui.onNodeWithText("D'abord le déclic.\nEnsuite, à toi de jouer.").assertExists()
        ui.runOnIdle {
            model.startQuestions(lesson.title,listOf(recall,matching),lesson.id)
        }

        awaitText(recall.prompt)
        val wrong=recall.choices[recall.choices.indices.first {it!=recall.answer}]
        ui.onNodeWithText(wrong).performScrollTo().performClick()
        ui.onNodeWithText("Vérifier").assertIsEnabled().performClick()
        ui.onNodeWithText("Une occasion de retenir").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Réponse : ${recall.choices[recall.answer]}")
            .performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(recall.explanation)
            .performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Continuer").performClick()

        awaitText(matching.prompt)
        ui.onNodeWithText("Vérifier").assertIsNotEnabled()
        matching.pairs.forEach {pair(it.left,it.right)}
        ui.onNodeWithText("Vérifier").assertIsEnabled().performClick()
        ui.onNodeWithText("Signal reçu !").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText(matching.explanation)
            .performScrollTo().assertIsDisplayed()

        quitQuiz()
        awaitText("HAMIGO")
        ui.onNodeWithText("Parcours").assertIsDisplayed()
    }

    @Test
    fun examCanBeReviewedBeforeFinalizationAndTheRecapKeepsTheChosenAnswer() {
        val model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.runOnIdle {
            model.startQuestions("Examen blanc témoin",List(40) {index->Question("exam-ui-$index","Question témoin ${index+1}",
                listOf("La bonne réponse","Autre réponse"),0,"Une explication à lire.",section=if(index<20)"regulation" else "technique")},exam=true)
        }
        ui.onNodeWithText("Commencer la réglementation").assertIsDisplayed().performClick()
        repeat(20) {index->
            awaitText("Question témoin ${index+1}")
            ui.onNodeWithText("La bonne réponse").performScrollTo().performClick()
            ui.onNodeWithText(if(index<19)"Enregistrer et continuer" else "Enregistrer et relire").performClick()
        }
        awaitText("Relire réglementation")
        ui.onNodeWithText("1").performClick()
        awaitText("Question témoin 1")
        ui.onNodeWithText("Autre réponse").performScrollTo().performClick()
        ui.onNodeWithText("Enregistrer et continuer").performClick()
        ui.onNodeWithText("Relire").performClick()
        ui.runOnIdle {
            val response=model.session!!.responses[0]!!
            assertEquals("Autre réponse",response.display)
            assertEquals("Autre réponse",model.session!!.questions[0].choices[response.choiceIndex])
            org.junit.Assert.assertFalse(response.correct)
        }
        ui.onNodeWithText("Finaliser la réglementation").performScrollTo().performClick()
        ui.onNodeWithText("Finaliser").performClick()
        ui.onNodeWithText("Commencer la technique").assertIsDisplayed().performClick()
        ui.runOnIdle {
            repeat(20) {model.answer(true,display="La bonne réponse",choiceIndex=model.session!!.current!!.answer)}
            model.finishExamPart()
        }
        ui.onNodeWithText("19 / 20").assertIsDisplayed()
        ui.onNodeWithText("Partager mes résultats").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Récap ⬇️").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Autre réponse").performScrollTo().assertIsDisplayed()
        ui.onAllNodesWithText("Réponse : La bonne réponse").onFirst().performScrollTo().assertIsDisplayed()
    }

    private fun pair(left: String, right: String) {
        ui.onNodeWithText(left).performScrollTo().performClick()
        ui.onNodeWithText(right).performScrollTo().assertIsEnabled().performClick()
    }

    private fun quitQuiz() {
        ui.onNodeWithContentDescription("Quitter la séance").performClick()
        awaitText("Faire une pause ?")
        ui.onNodeWithText("Quitter").performClick()
    }

    private fun awaitText(text: String) {
        ui.waitUntil(timeoutMillis = 15_000) { exists(text) }
    }

    private fun exists(text: String): Boolean =
        ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
}
