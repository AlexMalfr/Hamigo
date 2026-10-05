package com.malfreyt.alexandre.hamigo

import androidx.compose.ui.test.*
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.lifecycle.ViewModelProvider
import org.junit.Before
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises real user paths while retaining any progress already stored on the test emulator. */
@RunWith(AndroidJUnit4::class)
class ApplicationSmokeTest {
    @get:Rule
    val ui = createAndroidComposeRule<MainActivity>()

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
        ui.onNodeWithText("FLASHCARD · RAPPEL ACTIF").performScrollTo().assertIsDisplayed()
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
        // Explore an explicit lesson, rather than depending on the user's recommended next lesson.
        ui.onNodeWithText("Parcours").performClick()
        ui.onNode(hasScrollAction()).performScrollToNode(hasText("Bienvenue sur les ondes"))
        if (!exists("Le service amateur")) {
            ui.onNodeWithText("Bienvenue sur les ondes").performClick()
        }
        ui.onNodeWithText("Le service amateur").performScrollTo().performClick()
        ui.onNodeWithText("D'abord le déclic.\nEnsuite, à toi de jouer.").assertExists()
        // Keep this correction/matching smoke path stable while normal lesson sessions randomize.
        val model = ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.runOnIdle {
            val lesson = model.lesson!!
            model.startQuestions(lesson.title, lesson.questions.take(2), lesson.id)
        }

        awaitText("Quel est l’objectif du service amateur ?")
        ui.onNodeWithText("Diffuser des publicités").performScrollTo().performClick()
        ui.onNodeWithText("Vérifier").assertIsEnabled().performClick()
        ui.onNodeWithText("Une occasion de retenir").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Réponse : L’instruction et l’expérimentation personnelles")
            .performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Le service amateur est une activité personnelle sans intérêt pécuniaire.")
            .performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Continuer").performClick()

        awaitText("Associe chaque élément à son rôle.")
        ui.onNodeWithText("LES BONNES CONNEXIONS").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Vérifier").assertIsNotEnabled()
        pair("Certificat", "Compétences de l’opérateur")
        pair("Indicatif", "Identification de la station")
        pair("ANFR", "Organisation de l’examen")
        ui.onNodeWithText("Vérifier").assertIsEnabled().performClick()
        ui.onNodeWithText("Signal reçu !").performScrollTo().assertIsDisplayed()
        ui.onNodeWithText("Certificat et indicatif remplissent deux fonctions distinctes.")
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
        ui.runOnIdle {assertEquals(1,model.session!!.responses[0]!!.choiceIndex)}
        ui.onNodeWithText("Finaliser la réglementation").performScrollTo().performClick()
        ui.onNodeWithText("Finaliser").performClick()
        ui.onNodeWithText("Commencer la technique").assertIsDisplayed().performClick()
        ui.runOnIdle {
            repeat(20) {model.answer(true,display="La bonne réponse",choiceIndex=0)}
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
