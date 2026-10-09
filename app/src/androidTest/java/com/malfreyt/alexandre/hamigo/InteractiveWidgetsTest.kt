package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class InteractiveWidgetsTest {
    @get:Rule val ui = createComposeRule()

    @Test fun aConnectionCanStartOnTheRightHandSide() {
        var result=emptyMap<Int,Int>()
        val q = Question("test-match", "Associe", emptyList(), 0, "", kind = "match",
            pairs = listOf(PairItem("Fréquence", "Hertz"), PairItem("Résistance", "Ohm")))
        ui.setContent {
            MaterialTheme {
                var matches by remember { mutableStateOf(emptyMap<Int, Int>()) }
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    MatchBoard(q,q.id,matches,true,onChange={matches=it;result=it})
                }
            }
        }
        ui.onNodeWithText("Hertz").assertIsEnabled().performClick()
        ui.onNodeWithText("Fréquence").performClick()
        ui.runOnIdle {assertEquals(mapOf(0 to 0),result)}
        ui.onNodeWithText("Ohm").performClick()
        ui.onNodeWithText("Résistance").performClick()
        ui.runOnIdle {assertEquals(mapOf(0 to 0,1 to 1),result)}
    }

    @Test fun draggingAWordIntoTheStandardSlotSelectsIt() {
        val q = Question("test-cloze", "J'annonce mon ___ sur les ondes.", listOf("indicatif", "adresse", "prénom"), 0, "", kind = "cloze")
        ui.setContent {
            MaterialTheme {
                var selected by remember { mutableIntStateOf(-1) }
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    ClozeBoard(q, selected, true, onChoice = { selected = it })
                    Text("Sélection : ${q.choices.getOrNull(selected) ?: "aucune"}")
                }
            }
        }
        val word = ui.onNodeWithText("indicatif")
        val sourceBounds = word.fetchSemanticsNode().boundsInRoot
        val target = ui.onNodeWithContentDescription("Emplacement pour le mot à compléter").fetchSemanticsNode().boundsInRoot.center
        word.performTouchInput { swipe(center, target - sourceBounds.topLeft, durationMillis = 600) }
        ui.onNodeWithText("Sélection : indicatif").assertExists()
    }

    @Test fun theFloatingCalculatorInsertsItsResultIntoTheQuestion() {
        var inserted: Double? = null
        ui.setContent {
            MaterialTheme {
                var open by remember { mutableStateOf(true) }
                FloatingCalculator(open, onDismiss = { open = false }, onInsertResult = { inserted = it })
            }
        }
        ui.onNode(hasSetTextAction()).performTextInput("10*log(1000)")
        ui.onNodeWithText("Utiliser dans ma réponse").performScrollTo().performClick()
        ui.runOnIdle { assertEquals(30.0, inserted!!, 1e-10) }
        ui.onNodeWithText("Calculatrice").assertDoesNotExist()
    }
}
