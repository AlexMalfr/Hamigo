package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import android.os.Build
import android.view.WindowInsets
import android.view.inspector.WindowInspector
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Real Memo pages, RAM-only account, and save/restore of primitive tool UI state. */
@RunWith(AndroidJUnit4::class)
class MemoToolStateRetentionInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var owner:AppModel
    private lateinit var model:AppModel
    private lateinit var content:Content
    private lateinit var restoration:StateRestorationTester
    @Before fun prepare() {
        check(Build.HARDWARE in listOf("ranchu","goldfish"))
        ui.prepareTestWindow()
        content=Content(context,examSnapshot=null)
        ui.runOnUiThread {
            val memory=DiagnosticContext(context)
            owner=AppModel().apply {
                progress=Progress(memory,sideEffects=false)
                diagnostics=DiagnosticSettings(memory)
                progress.prefs.edit().putBoolean("interactionSound",false).putBoolean("interactionHaptics",false).commit()
                interactionFeedback=AppFeedback(memory)
            }
            model=AppModel().apply {initializeDiagnostics(context,owner,this@MemoToolStateRetentionInstrumentedTest.content)}
            GameplayPreferences.save(model.progress.prefs,MorseInputSettings(singleKey=false,liveSound=false,autoSpaces=false))
            model.showWelcome=false;model.tour.finish();model.route="resources"
        }
    }
    @After fun close()=ui.runOnUiThread {
        stopMorse()
        if(::model.isInitialized)model.disposeDiagnostics()
        if(::owner.isInitialized)owner.interactionFeedback.close()
    }
    private fun show(category:String) {
        ui.runOnUiThread {model.resource=content.references.single {it.id==category}}
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
    private fun captureState(name:String,details:String="") {
        val dir=File(context.getExternalFilesDir(null),"memo-state").apply {mkdirs()}
        val state=listOf("route="+model.route,"resource="+model.resource?.id,"lesson="+model.lesson?.id,
            "session="+(model.session!=null),"pageKey="+model.visibleScreenKey(),details).joinToString("\n")
        val trees=runCatching {
            val roots=ui.onAllNodes(isRoot(),useUnmergedTree=true)
            roots.fetchSemanticsNodes().indices.joinToString("\n\n") {index->roots[index].printToString()}
        }.getOrElse {"Semantics capture failed: $it"}
        File(dir,"$name.txt").writeText(state+"\n"+trees)
        runCatching {
            val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
        }.onFailure {File(dir,"$name-screenshot-error.txt").writeText(it.toString())}
    }
    private fun reveal(matcher:SemanticsMatcher):SemanticsNodeInteraction {
        try {
            ui.onNodeWithTag("memo-detail-list").performScrollToNode(matcher)
            val target=ui.onNode(matcher).performScrollTo()
            val header=ui.onNodeWithTag("memo-detail-header").getUnclippedBoundsInRoot()
            val bounds=target.getUnclippedBoundsInRoot()
            if(bounds.top<header.bottom+8.dp) {
                val shift=(bounds.top-header.bottom-8.dp).value*context.resources.displayMetrics.density
                ui.onNodeWithTag("memo-detail-list").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy){it(0f,shift)}
            }
            return target.assertIsDisplayed()
        } catch(error:Throwable) {
            captureState("reveal-failed",matcher.toString())
            throw error
        }
    }
    private fun tool(id:String,capturePrefix:String?=null) {
        val toggle=reveal(hasTestTag("memo-tool-toggle-$id"))
        capturePrefix?.let {captureState("$it-before","toggleBounds="+toggle.getUnclippedBoundsInRoot())}
        if(toggle.fetchSemanticsNode().config[SemanticsProperties.StateDescription]!="Déplié")toggle.performClick()
        capturePrefix?.let {captureState("$it-after")}
        ui.runOnIdle {assertNull("Switching a Memo tool must not open flashcards",model.session)}
    }
    private fun field(label:String)=hasSetTextAction() and hasText(label)
    private fun edit(label:String,value:String) {reveal(field(label)).performTextReplacement(value);hideKeyboard()}
    private fun choose(label:String)=reveal(hasText(label) and hasClickAction()).performClick()
    private fun reopen(category:String) {
        hideKeyboard()
        ui.onNodeWithContentDescription("Retour aux mémos").performClick()
        ui.onNodeWithTag("memo-library-list").performScrollToNode(hasTestTag("memo-row-$category"))
        ui.onNodeWithTag("memo-row-$category").performScrollTo().assertIsDisplayed().performClick()
        ui.waitUntil(10_000){model.resource?.id==category}
        ui.onNodeWithTag("memo-detail-list").assertIsDisplayed()
        captureState("reopen-$category")
    }

    @Test fun decibelAndRfChainInputsSurviveChangingToolsRestorationAndReturningToTheFiche() {
        val before=owner.progress.export();val local=model.progress.export()
        show("decibels")
        tool("db")
        choose("Tension");choose("dB → rapport")
        edit("Valeur en dB","9")
        reveal(hasContentDescription("Signe plus : passer au moins")).performClick()
        tool("dbchain")
        choose("Ajouter un câble");choose("mW")
        edit("Puissance d’entrée Pe (mW)","250")
        edit("Gain du bloc (dB)","6")
        edit("Perte du câble (dB, valeur positive)","2")
        reveal(hasContentDescription("Signe plus : passer au moins")).performClick()
        restoration.emulateSavedInstanceStateRestore()
        reveal(hasText("Ajouter un câble") and hasClickAction()).assertIsSelected()
        reveal(field("Puissance d’entrée Pe (mW)")).assertTextContains("250")
        reveal(field("Gain du bloc (dB)")).assertTextContains("6")
        reveal(field("Perte du câble (dB, valeur positive)")).assertTextContains("2")
        reveal(hasContentDescription("Signe moins : passer au plus")).assertIsDisplayed()
        tool("db",capturePrefix="decibel-chain-to-db")
        reveal(hasText("Tension") and hasClickAction()).assertIsSelected()
        reveal(field("Valeur en dB")).assertTextContains("9")
        reveal(hasContentDescription("Signe moins : passer au plus")).assertIsDisplayed()
        reopen("decibels")
        // The expanded tool is restored too; opening it again would hide it.
        reveal(field("Valeur en dB")).assertTextContains("9")
        reveal(hasText("Tension") and hasClickAction()).assertIsSelected()
        assertEquals(before,owner.progress.export());assertEquals(local,model.progress.export())
    }

    @Test fun morseKeepsBothMessagesWhenCollapsedRestoredAndVisitedAgainWithoutStartingPlayback() {
        val before=owner.progress.export();val local=model.progress.export()
        show("morse")
        tool("morse")
        val message=hasSetTextAction() and hasAnyAncestor(hasTestTag("memo-tool-morse"))
        reveal(message).performTextReplacement("F4ABC")
        hideKeyboard()
        choose("Morse → texte")
        reveal(hasContentDescription("Effacer le dernier symbole")).performTouchInput {longClick()}
        choose("Trait");choose("Trait")
        val translated=hasText("M") and hasAnyAncestor(hasTestTag("memo-tool-morse"))
        reveal(translated).assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        reveal(hasText("Morse → texte") and hasClickAction()).assertIsSelected()
        reveal(translated).assertIsDisplayed()
        choose("Texte → Morse")
        reveal(message).assertTextContains("F4ABC")
        reveal(hasTestTag("memo-tool-toggle-morse")).performClick()
        reopen("morse")
        tool("morse")
        reveal(message).assertTextContains("F4ABC")
        choose("Morse → texte")
        reveal(translated).assertIsDisplayed()
        assertEquals(before,owner.progress.export());assertEquals(local,model.progress.export())
    }

    @Test fun resistorChoicesAndTheFicheFilterAreRestoredTogetherAfterNavigation() {
        val before=owner.progress.export();val local=model.progress.export()
        show("resistors")
        tool("resistor")
        choose("Ω → couleurs")
        reveal(hasContentDescription("Utiliser cinq anneaux")).performClick()
        edit("Résistance en Ω","12300")
        reveal(hasText("Or") and hasClickAction() and hasAnyAncestor(hasTestTag("memo-tool-resistor"))).performClick()
        ui.onNode(hasText("Rouge") and hasClickAction()).performClick()
        ui.onNodeWithContentDescription("Filtrer cette fiche").performClick()
        ui.onNode(field("Filtrer cette fiche")).performTextReplacement("tolérance")
        hideKeyboard()
        restoration.emulateSavedInstanceStateRestore()
        ui.onNode(field("Filtrer cette fiche")).assertTextContains("tolérance")
        reveal(field("Résistance en Ω")).assertTextContains("12300")
        reveal(hasContentDescription("Utiliser cinq anneaux")).assertIsOn()
        reveal(hasText("12300 Ω ± 2 %")).assertIsDisplayed()
        reopen("resistors")
        ui.onNode(field("Filtrer cette fiche")).assertTextContains("tolérance")
        reveal(field("Résistance en Ω")).assertTextContains("12300")
        reveal(hasText("Ω → couleurs") and hasClickAction()).assertIsSelected()
        reveal(hasContentDescription("Utiliser cinq anneaux")).assertIsOn()
        reveal(hasText("12300 Ω ± 2 %")).assertIsDisplayed()
        assertEquals(before,owner.progress.export());assertEquals(local,model.progress.export())
    }
    @Test fun logicInputsAndDiagramSurviveHiddenInputRestorationAndTheFicheScrollIsKept() {
        val before=owner.progress.export();val local=model.progress.export()
        show("binary-logic")
        tool("logic")
        choose("OU")
        choose("A = 0");choose("B = 0")
        choose("NON")
        reveal(hasTestTag("logic-tool-output")).assertTextEquals("S = 0")
        restoration.emulateSavedInstanceStateRestore()
        reveal(hasText("NON") and hasClickAction()).assertIsSelected()
        reveal(hasText("A = 1") and hasClickAction()).assertIsSelected()
        choose("OU")
        reveal(hasText("B = 1") and hasClickAction()).assertIsSelected()
        reveal(hasTestTag("logic-tool-output")).assertTextEquals("S = 1")
        val footer=reveal(hasTestTag("memo-footer-pico"))
        val top=footer.getUnclippedBoundsInRoot().top
        restoration.emulateSavedInstanceStateRestore()
        ui.onNodeWithTag("memo-footer-pico").assertIsDisplayed()
        assertEquals(top.value,ui.onNodeWithTag("memo-footer-pico").getUnclippedBoundsInRoot().top.value,1.5f)
        reopen("binary-logic")
        ui.onNodeWithTag("memo-footer-pico").assertIsDisplayed()
        assertEquals(top.value,ui.onNodeWithTag("memo-footer-pico").getUnclippedBoundsInRoot().top.value,1.5f)
        reveal(hasText("OU") and hasClickAction()).assertIsSelected()
        reveal(hasText("A = 1") and hasClickAction()).assertIsSelected()
        reveal(hasText("B = 1") and hasClickAction()).assertIsSelected()
        assertEquals(before,owner.progress.export());assertEquals(local,model.progress.export())
    }

    @Test fun formulaToolsKeepIndependentValuesWhileSwitchingBetweenThemAndReopeningTheFiche() {
        val before=owner.progress.export();val local=model.progress.export()
        show("formulas")
        tool("ohm");choose("Trouver R")
        edit("Tension U (V)","12");edit("Courant I (A)","0,25")
        tool("wavelength");choose("λ → fréquence");choose("kHz")
        edit("Longueur d’onde en mètres","160")
        tool("networks");choose("En parallèle")
        edit("R₁ (Ω)","220");edit("R₂ (Ω)","330")
        restoration.emulateSavedInstanceStateRestore()
        reveal(hasText("En parallèle") and hasClickAction()).assertIsSelected()
        reveal(field("R₁ (Ω)")).assertTextContains("220")
        reveal(field("R₂ (Ω)")).assertTextContains("330")
        tool("ohm")
        reveal(hasText("Trouver R") and hasClickAction()).assertIsSelected()
        reveal(field("Tension U (V)")).assertTextContains("12")
        reveal(field("Courant I (A)")).assertTextContains("0,25")
        tool("wavelength")
        reveal(hasText("λ → fréquence") and hasClickAction()).assertIsSelected()
        reveal(hasText("kHz") and hasClickAction()).assertIsSelected()
        reveal(field("Longueur d’onde en mètres")).assertTextContains("160")
        reopen("formulas")
        reveal(field("Longueur d’onde en mètres")).assertTextContains("160")
        tool("networks")
        reveal(hasText("En parallèle") and hasClickAction()).assertIsSelected()
        reveal(field("R₁ (Ω)")).assertTextContains("220")
        reveal(field("R₂ (Ω)")).assertTextContains("330")
        assertEquals(before,owner.progress.export());assertEquals(local,model.progress.export())
    }
}