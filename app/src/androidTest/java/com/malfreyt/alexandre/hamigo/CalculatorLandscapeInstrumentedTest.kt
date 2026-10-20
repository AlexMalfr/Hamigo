package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.os.Build
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.width
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Real landscape dialog, native expression editor and production calculator state. */
@RunWith(AndroidJUnit4::class)
class CalculatorLandscapeInstrumentedTest {
    @get:Rule val ui=createAndroidComposeRule<ComponentActivity>()
    @Before fun prepareWindow(){ui.prepareTestWindow()}
    private fun capture(name:String) {
        ui.awaitForegroundFrames()
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"calculator-landscape/$name.png")
        file.parentFile!!.mkdirs()
        val bitmap=requireNotNull(instrumentation.uiAutomation.takeScreenshot()) {"No screenshot returned for the focused window"}
        try {file.outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}}finally{bitmap.recycle()}
    }
    private fun assertBalancedKeypadsAndSplitEditor() {
        val expression=ui.onNodeWithTag("calculator-expression").assertIsDisplayed().getUnclippedBoundsInRoot()
        val keypads=ui.onNodeWithTag("calculator-landscape").getUnclippedBoundsInRoot()
        val scientific=ui.onNodeWithTag("calculator-scientific").getUnclippedBoundsInRoot()
        val numbers=ui.onNodeWithTag("calculator-numbers").getUnclippedBoundsInRoot()
        val actions=ui.onNodeWithTag("calculator-result-actions").getUnclippedBoundsInRoot()
        assertEquals("Input aligns with the left scientific keypad.",scientific.left.value,expression.left.value,1f)
        assertEquals("Input aligns with the left scientific keypad.",scientific.right.value,expression.right.value,1f)
        assertEquals("Result and actions align with the right numeric keypad.",numbers.left.value,actions.left.value,1f)
        assertEquals("Result and actions align with the right numeric keypad.",numbers.right.value,actions.right.value,1f)
        assertTrue("Both halves of the editor sit above the keys.",expression.bottom<=keypads.top&&actions.bottom<=keypads.top)
        val title=ui.onNodeWithTag("calculator-title").getUnclippedBoundsInRoot()
        val close=ui.onNodeWithTag("calculator-close").assertIsDisplayed().getUnclippedBoundsInRoot()
        val output=ui.onNodeWithTag("calculator-output-row").getUnclippedBoundsInRoot()
        assertEquals("Close is aligned with the title.",(title.top.value+title.bottom.value)/2f,(close.top.value+close.bottom.value)/2f,1f)
        assertEquals("Input and output have the same top.",expression.top.value,output.top.value,1f)
        assertEquals("Input and output have the same bottom.",expression.bottom.value,output.bottom.value,1f)
        assertTrue("Close sits above the output.",close.bottom.value<=output.top.value+.01f)
        assertEquals("Both keypads have equal visual width.",scientific.width.value,numbers.width.value,1f)
        assertEquals("Both keypads start together.",scientific.top.value,numbers.top.value,1f)
        assertEquals("Both keypads finish together.",scientific.bottom.value,numbers.bottom.value,1f)
    }
    @Test fun allKeysAndTheResultAreVisibleAndUsableWithoutScrolling() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        ui.setContent {HamigoTheme {FloatingCalculator(true,{})}}
        ui.onNodeWithTag("calculator-landscape").assertIsDisplayed()
        val viewport=ui.onNodeWithTag("calculator-backdrop").getUnclippedBoundsInRoot()
        val labels=listOf("sin","cos","tan","(",")","asin","acos","atan","x²","√","log","ln","10ˣ","eˣ","1/x","π","EXP","DEG","Ans","%","0","1","2","3","4","5","6","7","8","9",",","÷","×","−","+","⌫","C","=")
        labels.forEach {label->
            val key=ui.onNodeWithTag("calculator-key-$label").assertIsDisplayed().getUnclippedBoundsInRoot()
            assertTrue("Entire key $label must fit: $key / $viewport",key.top>=viewport.top&&key.bottom<=viewport.bottom&&key.left>=viewport.left&&key.right<=viewport.right)
        }
        assertBalancedKeypadsAndSplitEditor()
        val editor=CalculatorEditorDriver(ui)
        editor.performTextInput("2+3")
        ui.onNodeWithTag("calculator-key-=").performClick()
        ui.onNodeWithTag("calculator-result").assertTextEquals("5")
        capture("all-keys-result")
        editor.performTextReplacement("sin(30)")
        ui.onNodeWithTag("calculator-key-=").performClick()
        ui.onNodeWithTag("calculator-result").assertTextEquals("0,5")
    }
    @Test fun insertingAResultAndReopeningRetainTheExpressionAndAngleMode() {
        var open by mutableStateOf(true)
        val state=CalculatorState()
        val inserted=mutableListOf<Double>()
        ui.setContent {HamigoTheme {Box(Modifier.fillMaxSize()) {
            FloatingCalculator(open,{open=false},{inserted+=it},state=state)
        }}}
        assertBalancedKeypadsAndSplitEditor()
        val editor=CalculatorEditorDriver(ui)
        editor.performTextInput("2+3")
        ui.onNodeWithTag("calculator-key-=").performClick()
        ui.onNodeWithTag("calculator-key-DEG").performClick()
        ui.onNodeWithContentDescription("Fermer la calculatrice").performClick()
        ui.waitForIdle()
        ui.runOnIdle {open=true}
        editor.assertTextEquals("2+3")
        ui.onNodeWithTag("calculator-key-RAD").assertIsDisplayed()
        ui.onNodeWithTag("calculator-insert").assertIsDisplayed()
        capture("insert-ready")
        val click=ui.onNodeWithTag("calculator-insert").fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
        ui.runOnIdle {click();click()}
        ui.runOnIdle {assertEquals(listOf(5.0),inserted);assertFalse(open)}
    }
}
