package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File

/** Emulator-only UI regression and screenshots of every packaged reference family. */
@RunWith(AndroidJUnit4::class)
class MemoUpgradeInstrumentedTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private var welcomed = false
    private var autoSync = true
    private val fixture = object : ExternalResource() {
        override fun before() {
            check(android.os.Build.FINGERPRINT.contains("generic") || android.os.Build.MODEL.startsWith("sdk_")) { "Use an emulator for visual fixtures" }
            val prefs=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE)
            welcomed=prefs.getBoolean("welcomed",false);autoSync=prefs.getBoolean("autoSync",true)
            prefs.edit().putBoolean("welcomed",true).putBoolean("autoSync",false).commit()
        }
        override fun after() {
            context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().putBoolean("welcomed",welcomed).putBoolean("autoSync",autoSync).commit()
            stopMorse()
        }
    }
    private val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)
    private lateinit var model:AppModel
    private val scroll = hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
    @Before fun load() {
        model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000){model.content!=null}
        ui.runOnIdle {model.showWelcome=false;model.route="resources"}
    }
    private fun capture(name:String) {
        ui.waitForIdle()
        val dir=File(context.getExternalFilesDir(null),"memo-upgrade").apply{mkdirs()}
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap ->
            File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        }
    }
    private inline fun Bitmap.useBitmap(block:(Bitmap)->Unit) {try{block(this)}finally{recycle()}}

    @Test fun everyReferenceRendersAndBothHeadersStayPinned() {
        ui.onNode(scroll).performScrollToNode(hasText("Consulter le cours complet F6KGL/F5KFF"))
        ui.onNodeWithTag("memo-library-title").assertIsDisplayed()
        capture("00-library-bottom")
        ui.onNodeWithContentDescription("Rechercher un mémo").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("Morse")
        ui.onNode(hasSetTextAction()).assertIsDisplayed()
        capture("00-library-search")
        Espresso.closeSoftKeyboard()
        ui.onNodeWithContentDescription("Fermer la recherche des mémos").performClick()
        for(cat in model.content!!.references) {
            ui.runOnIdle {model.resource=cat}
            ui.onNodeWithText(cat.title).assertIsDisplayed()
            capture("${cat.id}-top")
            val visual=cat.rows.firstOrNull {it.visual.isNotBlank() && it.kind!="flashcard-only" && it.region !in setOf("2","3")}
            if(visual!=null) {
                ui.onNode(scroll).performScrollToNode(hasText(visual.term))
                capture("${cat.id}-diagram")
            }
            ui.onNode(scroll).performScrollToNode(hasText("Consulter le cours complet F6KGL/F5KFF"))
            ui.onNodeWithText(cat.title).assertIsDisplayed()
            capture("${cat.id}-bottom")
            ui.onNodeWithContentDescription("Retour aux mémos").performClick()
        }
        ui.onNodeWithContentDescription("Ouvrir la calculatrice").performClick()
        ui.onNodeWithText("Calculatrice").assertIsDisplayed()
        capture("00-memo-calculator")
        ui.onNodeWithContentDescription("Fermer la calculatrice").performClick()
    }

    @Test fun keyboardMovesActionsAndCalculatorInsertsPlainTwenty() {
        ui.runOnIdle {model.startQuestions("Saisie témoin",listOf(Question("ui-number","Quelle valeur ?",emptyList(),0,"",kind="number",value=20.0,unit="Ω")))}
        val button=ui.onNodeWithText("Vérifier")
        val before=button.fetchSemanticsNode().boundsInRoot.bottom
        ui.onNode(hasSetTextAction()).performClick()
        ui.waitUntil(10_000){ViewCompat.getRootWindowInsets(ui.activity.window.decorView)?.isVisible(WindowInsetsCompat.Type.ime())==true}
        ui.waitForIdle()
        val after=button.fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Vérifier doit remonter avec le clavier",before-after>100)
        val calc=ui.onNodeWithContentDescription("Ouvrir la calculatrice").fetchSemanticsNode().boundsInRoot
        assertTrue("La calculatrice est au-dessus de Vérifier",calc.bottom<after)
        assertTrue("La calculatrice a une vraie cible de 56dp",calc.width>=56*ui.activity.resources.displayMetrics.density-1)
        capture("question-keyboard")
        ui.onNodeWithContentDescription("Ouvrir la calculatrice").performClick()
        ui.onNode(hasSetTextAction() and hasText("Calcul")).performTextReplacement("20")
        Espresso.closeSoftKeyboard()
        ui.onNodeWithText("Utiliser dans ma réponse").performScrollTo().performClick()
        assertEquals("20",ui.onNode(hasSetTextAction()).fetchSemanticsNode().config[SemanticsProperties.EditableText].text)
        capture("question-calculator-twenty")
    }

    @Test fun allFormulaAndAdditionalDiagramsRender() {
        for(cat in model.content!!.references) {
            val diagrams=cat.rows.filter {it.visual.isNotBlank() && it.kind!="flashcard-only" && it.region !in setOf("2","3")}
            if(diagrams.isEmpty())continue
            ui.runOnIdle {model.resource=cat}
            diagrams.forEachIndexed {index,row ->
                ui.onNode(scroll).performScrollToNode(hasText(row.term))
                ui.onNodeWithText(cat.title).assertIsDisplayed()
                val sketch = SemanticsMatcher("Sketch of ${row.term}") { node ->
                    node.config.getOrNull(SemanticsProperties.ContentDescription)?.any { it.startsWith("Schéma de ${row.term}.") } == true
                }
                if (ui.onAllNodes(sketch).fetchSemanticsNodes().isNotEmpty()) {
                    ui.onNode(scroll).performScrollToNode(sketch)
                    val drawing = ui.onNode(sketch).assertIsDisplayed()
                    val image = drawing.captureToImage().asAndroidBitmap()
                    val directory = File(context.getExternalFilesDir(null), "memo-diagram-canvases").apply { mkdirs() }
                    try {
                        File(directory, "detail-${cat.id}-${index.toString().padStart(2,'0')}.png").outputStream().use {
                            check(image.compress(Bitmap.CompressFormat.PNG, 100, it))
                        }
                    } finally { image.recycle() }
                }
                capture("detail-${cat.id}-${index.toString().padStart(2,'0')}")
            }
            ui.runOnIdle {model.resource=null}
        }
    }

    @Test fun customQuestionCountIsSelectedAndExamNavigationPreservesDrafts() {
        ui.runOnIdle {model.route="practice"}
        ui.onNodeWithTag("mix-question-count-label").assertIsDisplayed()
        val customButton = ui.onNodeWithTag("custom-question-count").fetchSemanticsNode().boundsInRoot
        val pencil = ui.onNode(hasContentDescription("Choisir un nombre personnalisé") and hasAnyAncestor(hasTestTag("custom-question-count")), useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(customButton.center.x, pencil.center.x, 1f)
        assertEquals(customButton.center.y, pencil.center.y, 1f)
        capture("practice-custom-empty")
        ui.onNodeWithTag("custom-question-count").performClick()
        ui.onNode(hasSetTextAction()).performTextReplacement("75")
        ui.onNodeWithText("Choisir").performClick()
        ui.onNodeWithText("75").assertIsDisplayed()
        capture("practice-custom")
        ui.onNodeWithText("Choisir les thèmes",substring=true).performScrollTo().performClick()
        capture("practice-themes")
        ui.runOnIdle {model.startQuestions("Navigation témoin",List(40){Question("nav-$it","Question $it",listOf("Oui","Non"),0,"",section=if(it<20)"regulation"else"technique")},exam=true);model.beginExamPart()}
        ui.onNodeWithText("Oui").performClick()
        ui.onNodeWithText("Suivant").performClick()
        ui.runOnIdle {assertEquals(1,model.session!!.index);assertEquals("Oui",model.session!!.responses[0]!!.display)}
        ui.onNodeWithText("Précédent").performClick()
        ui.runOnIdle {assertEquals(0,model.session!!.index)}
        capture("question-navigation")
    }

    @Test fun calculatorSitsAboveMeasuredExamFooter() {
        ui.runOnIdle {model.startQuestions("Placement témoin",List(40){Question("place-$it","Choisis une réponse.",listOf("Oui","Non"),0,"",section=if(it<20)"regulation"else"technique")},exam=true);model.beginExamPart()}
        val action=ui.onNodeWithText("Enregistrer et continuer").fetchSemanticsNode().boundsInRoot
        val calc=ui.onNodeWithContentDescription("Ouvrir la calculatrice").fetchSemanticsNode().boundsInRoot
        assertTrue("Le FAB ne doit recouvrir aucune ligne du pied",calc.bottom<action.top)
        capture("question-exam-footer")
    }

    @Test fun regionalBandsAndLivePowerToolsStayUsable() {
        ui.runOnIdle {model.resource=model.content!!.references.first {it.id=="bands"}}
        ui.onNodeWithContentDescription("Choisir la région UIT").performClick()
        ui.onNodeWithText("Région 3 · Asie, Océanie…").performClick()
        ui.onNodeWithText("Région 3 · Asie, Océanie…").assertIsDisplayed()
        capture("tools-bands-region3")
        ui.runOnIdle {model.resource=model.content!!.references.first {it.id=="decibels"}}
        ui.onNodeWithText("Rapport ↔ gain ou atténuation").performClick()
        ui.onNode(scroll).performScrollToNode(hasText("dB → rapport"))
        ui.onNodeWithText("dB → rapport").performClick()
        ui.onNode(scroll).performScrollToNode(hasContentDescription("Signe plus : passer au moins"))
        ui.onNodeWithContentDescription("Signe plus : passer au moins").performClick()
        ui.onNodeWithContentDescription("Signe moins : passer au plus").assertIsDisplayed()
        capture("tools-decibels-sign")
        ui.onNode(scroll).performScrollToNode(hasText("Rapport ↔ gain ou atténuation"))
        ui.onNodeWithText("Rapport ↔ gain ou atténuation").performClick()
        ui.onNodeWithText("Entrée → gain → sortie : schéma dynamique").performClick()
        ui.onNode(scroll).performScrollToNode(hasText("Puissance de sortie Ps (W)"))
        ui.onNode(hasSetTextAction() and hasText("Puissance de sortie Ps (W)")).performTextReplacement("40")
        Espresso.closeSoftKeyboard()
        ui.onNode(scroll).performScrollToNode(hasText("G ≈ 6,021 dB"))
        ui.onNodeWithText("G ≈ 6,021 dB").assertIsDisplayed()
        capture("tools-decibels-gain")
        ui.onNode(scroll).performScrollToNode(hasText("Ajouter un câble"))
        ui.onNodeWithText("Ajouter un câble").performScrollTo().performClick()
        ui.onNode(scroll).performScrollToNode(hasText("Ps ≈ 19,95 W"))
        ui.onNodeWithText("Ps ≈ 19,95 W").assertIsDisplayed()
        capture("tools-decibels-chain")
    }
}
