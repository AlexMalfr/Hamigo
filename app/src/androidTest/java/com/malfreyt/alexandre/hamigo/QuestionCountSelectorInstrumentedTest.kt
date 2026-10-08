package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.ExternalResource
import org.junit.rules.RuleChain
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class QuestionCountSelectorLayoutInstrumentedTest {
    @get:Rule val ui=createComposeRule()

    @Test fun labelWrapsAtSmallWidthsAndLargeFontsWithoutClippingChoices() {
        var width by mutableStateOf(360.dp)
        var fontScale by mutableFloatStateOf(1f)
        var count by mutableIntStateOf(20)
        var custom by mutableStateOf(false)
        ui.setContent {
            HamigoTheme {
                val density=LocalDensity.current
                Box(Modifier.fillMaxSize().background(Cream).padding(8.dp)) {
                    CompositionLocalProvider(LocalDensity provides Density(density.density,fontScale)) {
                        Column(Modifier.width(width)) {
                            QuestionCountSelector(count,custom,{n,c->count=n;custom=c},"selector","selector-custom")
                        }
                    }
                }
            }
        }
        val label=ui.onNodeWithTag("selector-label").fetchSemanticsNode().boundsInRoot
        val first=ui.onNodeWithTag("selector-10").fetchSemanticsNode().boundsInRoot
        assertEquals("Wide layout keeps its label inline",label.center.y,first.center.y,1f)
        saveSelector("selector-wide")
        ui.runOnIdle {width=220.dp;fontScale=1.3f}
        val compactLabel=ui.onNodeWithTag("selector-label").fetchSemanticsNode().boundsInRoot
        val compactFirst=ui.onNodeWithTag("selector-10").fetchSemanticsNode().boundsInRoot
        assertTrue("Small layout places the label above",compactLabel.bottom<=compactFirst.top)
        assertContained()
        assertRowsSpreadAcrossWidth()
        saveSelector("selector-narrow-font130")
        ui.onNodeWithTag("selector-input").assertDoesNotExist()
        ui.onNodeWithTag("selector-custom").performClick()
        ui.onNodeWithTag("selector-input").performTextReplacement("1000")
        ui.onNodeWithText("Choisir").performClick()
        ui.onNodeWithTag("selector-custom").assertIsSelected()
        ui.onNodeWithText("1000").assertIsDisplayed()
        assertContained()
        assertRowsSpreadAcrossWidth()
        saveSelector("selector-custom-font130")
        ui.runOnIdle {width=100.dp}
        assertContained()
        assertRowsSpreadAcrossWidth()
        saveSelector("selector-extra-narrow-font130")
        ui.onNodeWithTag("selector-150").performClick().assertIsSelected()
        ui.onNodeWithTag("selector-custom").assertIsNotSelected()
        ui.runOnIdle {assertEquals(150,count);assertFalse(custom)}
    }

    private fun assertContained() {
        val parent=ui.onNodeWithTag("selector").fetchSemanticsNode().boundsInRoot
        (listOf(10,20,40,80,150).map {"selector-$it"}+"selector-custom").forEach {tag ->
            val child=ui.onNodeWithTag(tag).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("$tag keeps a forty-dp touch area",child.height>=with(ui.density){40.dp.toPx()}-1)
            assertTrue("$tag stays inside horizontally",child.left>=parent.left-1&&child.right<=parent.right+1)
            assertTrue("$tag stays inside vertically",child.top>=parent.top-1&&child.bottom<=parent.bottom+1)
        }
    }

    private fun saveSelector(name:String) {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val dir=File(context.getExternalFilesDir(null),"question-count-0.42").apply {mkdirs()}
        val bitmap=ui.onNodeWithTag("selector").captureToImage().asAndroidBitmap()
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }

    private fun assertRowsSpreadAcrossWidth() {
        val parent=ui.onNodeWithTag("selector").fetchSemanticsNode().boundsInRoot
        val choices=(listOf(10,20,40,80,150).map {"selector-$it"}+"selector-custom")
            .map {ui.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot}
        // Fixed-height touch areas share their top coordinate within each measured row.
        choices.groupBy {it.top.toInt()}.values.forEach {row ->
            val ordered=row.sortedBy {it.left}
            if(ordered.size==1)assertEquals("A solitary choice is centered",parent.center.x,ordered.single().center.x,1f)
            else {
                assertEquals("A wrapped row starts at the left edge",parent.left,ordered.first().left,1f)
                assertEquals("A wrapped row ends at the right edge",parent.right,ordered.last().right,1f)
                val gaps=ordered.zipWithNext {a,b->b.left-a.right}
                assertTrue("The row has evenly distributed gaps",gaps.max()-gaps.min()<=1.1f)
            }
        }
    }
}

@RunWith(AndroidJUnit4::class)
class PracticeQuestionCountInstrumentedTest {
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private var saved=emptyMap<String,Any?>()
    private val fixture=object:ExternalResource() {
        override fun before() {
            check(Build.HARDWARE in listOf("ranchu","goldfish")||Build.FINGERPRINT.contains("generic")) {"Emulator fixtures only"}
            val prefs=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE)
            saved=prefs.all.mapValues {(_,v)->if(v is Set<*>)v.toSet() else v}
            check(prefs.edit().clear().putBoolean("welcomed",true).putBoolean("autoSync",false).putString("name","Sélecteurs test").commit())
            ProgressSyncScheduler.cancel(context)
        }
        override fun after() {
            val edit=context.getSharedPreferences("hamigo",Context.MODE_PRIVATE).edit().clear()
            saved.forEach {(k,v)->when(v) {
                is String->edit.putString(k,v);is Int->edit.putInt(k,v);is Long->edit.putLong(k,v)
                is Float->edit.putFloat(k,v);is Boolean->edit.putBoolean(k,v);is Set<*>->edit.putStringSet(k,v.filterIsInstance<String>().toSet())
            }}
            check(edit.commit());ProgressSyncScheduler.schedule(context);DailyReminder.schedule(context)
        }
    }
    private val ui=createAndroidComposeRule<MainActivity>()
    @get:Rule val rules:RuleChain=RuleChain.outerRule(fixture).around(ui)
    private lateinit var model:AppModel

    @Before fun load() {
        model=ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(60_000) {model.content!=null}
        ui.runOnIdle {
            model.refresh();model.showWelcome=false;model.route="practice"
        }
    }

    @Test fun reviewPresetsAndCustomCountStayIndependentFromMixAndNeverTruncateCustom() {
        ui.runOnIdle {model.content!!.lessons.take(30).forEach {model.progress.complete(it.id)};model.refresh()}
        val completed=model.progress.completed
        val pool=CourseRevisionBuilder.eligible(model.content!!.lessons,completed,model.progress.reviews,model.progress.due(model.content!!))
        assertTrue(pool.size>=150)
        assertTrue(pool.size<1000)
        showReviewCard()
        ui.onNodeWithTag("review-question-count-80").performClick().assertIsSelected()
        ui.onNodeWithText("Lancer les révisions").assertIsEnabled()
        ui.onNodeWithTag("review-question-count-150").performClick().assertIsSelected()
        ui.onNodeWithText("Lancer les révisions").assertIsEnabled()
        ui.onNodeWithTag("review-question-count-${pool.size}").assertIsDisplayed().performClick().assertIsSelected()
        assertEquals(1,ui.onAllNodesWithTag("review-question-count-${pool.size}").fetchSemanticsNodes().size)
        ui.onNodeWithTag("review-question-count-150").performClick()
        capture("practice-reviews-150")
        ui.onNodeWithTag("practice-list").performScrollToNode(hasTestTag("mix-question-count"))
        ui.onNodeWithTag("mix-question-count-20").assertIsSelected()
        ui.onNodeWithTag("mix-question-count-40").performClick().assertIsSelected()
        ui.onNodeWithTag("practice-list").performScrollToNode(hasTestTag("review-question-count"))
        ui.onNodeWithTag("review-question-count-150").assertIsSelected()
        ui.onNodeWithTag("custom-review-question-count").performClick()
        ui.onNodeWithTag("review-question-count-input").performTextReplacement("1000")
        ui.onNodeWithText("Choisir").assertIsNotEnabled()
        ui.onNodeWithText("De 1 à ${pool.size}").assertIsDisplayed()
        ui.onNodeWithTag("review-question-count-input").performTextReplacement("37")
        ui.onNodeWithText("Choisir").performClick()
        ui.onNodeWithTag("custom-review-question-count").assertIsSelected()
        ui.onNodeWithText("Lancer les révisions").assertIsEnabled()
        capture("practice-reviews-custom37")
        ui.onNodeWithTag("practice-list").performScrollToNode(hasTestTag("mix-question-count"))
        ui.onNodeWithTag("mix-question-count-40").assertIsSelected()
        showReviewCard()
        val launch=ui.onNodeWithText("Lancer les révisions").assertIsDisplayed().assertIsEnabled()
        val button=launch.fetchSemanticsNode().boundsInRoot
        val navigation=ui.onNodeWithTag("navigation-icon-path",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("The review launch button is wholly above the raised navigation button",button.bottom+with(ui.density){8.dp.toPx()}<navigation.top)
        launch.performClick()
        ui.runOnIdle {
            assertEquals(37,model.session!!.questions.size)
            assertEquals(37,model.session!!.questions.map {it.id}.distinct().size)
            assertTrue(model.session!!.questions.all {q->pool.any {it.id==q.id}})
            assertEquals(completed,model.progress.completed)
        }
    }

    @Test fun aTwoQuestionBankShowsAndLaunchesTwoWithoutUnavailablePresets() {
        ui.onNodeWithTag("practice-list").performScrollToNode(hasText("Lancer les révisions"))
        ui.onNodeWithText("Lancer les révisions").assertIsNotEnabled()
        ui.onNodeWithTag("review-question-count").assertDoesNotExist()
        val studied=model.content!!.lessons.first().questions.take(2)
        assertEquals(2,studied.size)
        ui.runOnIdle {studied.forEach {model.progress.answer(it.id,true)};model.refresh()}
        showReviewCard()
        ui.onNodeWithTag("review-question-count-2").assertIsSelected()
        listOf(10,20,40,80,150).forEach {ui.onNodeWithTag("review-question-count-$it").assertDoesNotExist()}
        capture("practice-reviews-exact-two")
        ui.onNodeWithTag("custom-review-question-count").performClick()
        ui.onNodeWithTag("review-question-count-input").performTextReplacement("3")
        ui.onNodeWithText("Choisir").assertIsNotEnabled()
        ui.onNodeWithText("De 1 à 2").assertIsDisplayed()
        ui.onNodeWithTag("review-question-count-input").performTextReplacement("1")
        ui.onNodeWithText("Choisir").performClick()
        ui.onNodeWithTag("custom-review-question-count").assertIsSelected()
        ui.onNodeWithTag("review-question-count-2").performClick().assertIsSelected()
        showReviewCard()
        val launch=ui.onNodeWithText("Lancer les révisions").assertIsEnabled()
        val navigation=ui.onNodeWithTag("navigation-icon-path",useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue(launch.fetchSemanticsNode().boundsInRoot.bottom+with(ui.density){8.dp.toPx()}<navigation.top)
        launch.performClick()
        ui.runOnIdle {
            assertEquals(2,model.session!!.questions.size)
            assertEquals(studied.map {it.id}.toSet(),model.session!!.questions.map {it.id}.toSet())
            assertTrue(model.progress.completed.isEmpty())
        }
    }

    private fun showReviewCard() {
        val list=ui.onNodeWithTag("practice-list")
        list.performScrollToIndex(3)
        val headerBottom=ui.onNodeWithText("Entraînement, révisions et examen blanc.").fetchSemanticsNode().boundsInRoot.bottom
        val listTop=list.fetchSemanticsNode().boundsInRoot.top
        // Reveal the card's title below the sticky header, rather than merely exposing the CTA
        // at the bottom of the viewport where the transparent navigation still handles touches.
        list.performSemanticsAction(SemanticsActions.ScrollBy) {it(0f,-(headerBottom-listTop+with(ui.density){8.dp.toPx()}))}
        ui.waitForIdle()
        ui.onNodeWithTag("review-question-count").assertIsDisplayed()
    }

    private fun capture(name:String) {
        showReviewCard()
        ui.waitForIdle()
        repeat(2) {
            val frame=CountDownLatch(1)
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                ui.activity.window.decorView.let {view->view.viewTreeObserver.registerFrameCommitCallback {frame.countDown()};view.invalidate()}
            }
            assertTrue(frame.await(5,TimeUnit.SECONDS))
        }
        val dir=File(context.getExternalFilesDir(null),"question-count-0.42").apply {mkdirs()}
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        try {File(dir,"$name.png").outputStream().use {bitmap.compress(Bitmap.CompressFormat.PNG,100,it)}} finally {bitmap.recycle()}
    }
}
