package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** Exercise the card's whole touch surface and the non-drag alternative without real progress. */
@RunWith(AndroidJUnit4::class)
class CircuitBoardInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private val question=Question("fixture-circuit","Place les appareils.",listOf("Ampèremètre","Voltmètre"),0,"",
        kind="circuit",visual="circuit:measurement",
        pairs=listOf(PairItem("Emplacement A","Ampèremètre"),PairItem("Emplacement B","Voltmètre")))
    private var placements by mutableStateOf<Map<Int,String>>(emptyMap())

    @Before fun prepareWindow() {ui.prepareTestWindow()}

    private fun show(initial:Map<Int,String> = emptyMap()) {
        placements=initial
        ui.setContent {HamigoTheme {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
                CircuitBoard(question,placements,true,null){placements=it}
            }
        }}
    }
    private fun capture(name:String) {
        val file=File(context.getExternalFilesDir(null),"finitions-2026-10/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun dragFromCard(component:String,slot:Int,horizontalFraction:Float) {
        val card=ui.onNodeWithTag("circuit-component-$component").fetchSemanticsNode().boundsInRoot
        val start=Offset(card.left+card.width*horizontalFraction,card.center.y)
        val end=ui.onNodeWithTag("circuit-slot-$slot").fetchSemanticsNode().boundsInRoot.center
        ui.onRoot().performTouchInput {
            down(start)
            repeat(6) {step->
                advanceEventTime(16)
                moveTo(start+(end-start)*((step+1)/6f))
            }
            up()
        }
        ui.waitForIdle()
    }

    @Test fun draggingStartsWithoutALongPressFromTextAndFromTheOtherSideOfTheCard() {
        show()
        dragFromCard("Ampèremètre",0,.82f)
        ui.runOnIdle {assertEquals("Ampèremètre",placements[0])}
        ui.onNodeWithTag("circuit-component-Ampèremètre").assertDoesNotExist()
        dragFromCard("Voltmètre",1,.12f)
        ui.runOnIdle {
            assertEquals(mapOf(0 to "Ampèremètre",1 to "Voltmètre"),placements)
        }
        ui.onNodeWithTag("circuit-component-Voltmètre").assertDoesNotExist()
        capture("circuit-immediate-whole-card-drag")
    }

    @Test fun usedComponentsLeaveTheTrayAndRemovingOrReplacingASlotRestoresTheirChoice() {
        show(mapOf(0 to "Ampèremètre"))
        ui.onNodeWithTag("circuit-component-Ampèremètre").assertDoesNotExist()
        val tray=ui.onNodeWithTag("circuit-tray").fetchSemanticsNode().boundsInRoot
        val remaining=ui.onNodeWithTag("circuit-component-Voltmètre").fetchSemanticsNode().boundsInRoot
        assertTrue("The remaining choice is not centred",abs(remaining.center.x-tray.center.x)<=3f)
        ui.onNodeWithTag("circuit-slot-1").performClick()
        ui.onAllNodesWithText("Voltmètre",substring=false).onLast().performClick()
        ui.runOnIdle {assertEquals(2,placements.size)}
        ui.onNodeWithTag("circuit-component-Voltmètre").assertDoesNotExist()
        ui.onNodeWithTag("circuit-slot-0").performClick()
        ui.onNodeWithText("Retirer Ampèremètre").performClick()
        ui.runOnIdle {assertFalse(placements.containsKey(0))}
        ui.onNodeWithTag("circuit-component-Ampèremètre").performClick()
        ui.onNodeWithTag("circuit-slot-1").performClick()
        ui.runOnIdle {assertEquals(mapOf(1 to "Ampèremètre"),placements)}
        ui.onNodeWithTag("circuit-component-Ampèremètre").assertDoesNotExist()
        ui.onNodeWithTag("circuit-component-Voltmètre").assertIsDisplayed()
        capture("circuit-accessible-replacement-centred-tray")
    }
}
