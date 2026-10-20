package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/** RAM-only progress, no account, downloads, permission prompt or external message. */
@RunWith(AndroidJUnit4::class)
class PathNavigationInstrumentedTest {
    @get:Rule val ui=createComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun prepareWindow() {ui.prepareTestWindow()}
    private fun model(content:Content)=AppModel().apply {
        val memory=DiagnosticContext(context)
        progress=Progress(memory,sideEffects=false)
        sync=com.malfreyt.alexandre.hamigo.platform.GitHubSync(memory,object:com.malfreyt.alexandre.hamigo.platform.GitHubGateway {
            override suspend fun api(method:String,path:String,token:String?,body:String?):String=error("RAM-only test")
            override suspend fun rawBackup(rawUrl:String,owner:String,gist:String,fileName:String):String=error("RAM-only test")
        })
        this.content=content
    }
    private fun capture(name:String) {
        val file=File(context.getExternalFilesDir(null),"parcours-progressif/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use {ui.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG,100,it)}
    }

    private fun assertAtFraction(tag:String,fraction:Float) {
        val viewport=ui.onNodeWithTag("path-list").fetchSemanticsNode().boundsInRoot
        val header=ui.onNodeWithTag("path-sticky-header").fetchSemanticsNode().boundsInRoot
        val row=ui.onNodeWithTag(tag).fetchSemanticsNode().boundsInRoot
        val bottom=76f*context.resources.displayMetrics.density
        val first=header.bottom+row.height/2
        val last=(viewport.bottom-bottom-row.height/2).coerceAtLeast(first)
        val expected=(viewport.top+viewport.height*fraction).coerceIn(first,last)
        assertTrue("Expected row centre $expected, observed ${row.center.y}: $row / $viewport",abs(row.center.y-expected)<=12f)
    }

    private fun assertGroupCentered(model:AppModel,chapterId:String) {
        val state=model.pathNavigationState!!.list
        val layout=state.layoutInfo
        val group=layout.visibleItemsInfo.single {it.key=="chapter-$chapterId"}
        val header=ui.onNodeWithTag("path-sticky-header").fetchSemanticsNode().boundsInRoot
        val first=layout.viewportStartOffset+header.height
        val last=(layout.viewportEndOffset-76f*context.resources.displayMetrics.density-group.size).coerceAtLeast(first)
        val expected=(layout.viewportStartOffset+(layout.viewportEndOffset-layout.viewportStartOffset)/2f-group.size/2f).coerceIn(first,last)
        assertEquals("The complete chapter, including all unfolded lessons, should be centred",expected,group.offset.toFloat(),12f)
    }

    @Test fun openingAnotherChapterCentresItsWholeExpandedBoundsAfterThePreviousGroupCloses() {
        val content=Content(context,examSnapshot=null)
        val model=model(content)
        ui.setContent {HamigoTheme {PathScreen(model,content)}}
        ui.onNodeWithText("Commencer le cours").assertIsDisplayed()
        capture("path-top")
        val list=ui.onNodeWithTag("path-list")
        for(index in listOf(4,1,5).filter {it<=content.chapters.lastIndex}) {
            val chapter=content.chapters[index]
            list.performScrollToNode(hasTestTag("chapter-${chapter.id}"))
            ui.onNodeWithTag("chapter-${chapter.id}").performClick()
            ui.waitForIdle()
            assertGroupCentered(model,chapter.id)
            ui.onNodeWithTag("chapter-${chapter.id}").assertIsDisplayed()
        }
        capture("path-chapter-anchored")
    }

    @Test fun collapsingALongGroupNeverLosesTheNewHeaderDuringTheFirstAnimationFrames() {
        val content=Content(context,examSnapshot=null)
        val model=model(content)
        ui.setContent {HamigoTheme {PathScreen(model,content)}}
        val firstIndex=(0 until content.chapters.lastIndex).maxBy {content.chapters[it].lessons.size}
        val list=ui.onNodeWithTag("path-list")
        val first=content.chapters[firstIndex]
        list.performScrollToNode(hasTestTag("chapter-${first.id}"))
        ui.onNodeWithTag("chapter-${first.id}").performClick()
        val target="chapter-${content.chapters[firstIndex+1].id}"
        list.performScrollToNode(hasTestTag(target))
        val previousTop=ui.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot.top
        var distance:Float?=null
        capture("path-opening-before")
        val frames=mutableListOf<String>()
        fun recordFrame(label:String) {
            val state=model.pathNavigationState!!.list
            frames+="$label bounds=${ui.onNodeWithTag(target).fetchSemanticsNode().boundsInRoot} first=${state.firstVisibleItemIndex}/${state.firstVisibleItemScrollOffset} items=${state.layoutInfo.visibleItemsInfo.joinToString {"${it.key}:${it.offset}:${it.size}"}}"
            File(context.getExternalFilesDir(null),"parcours-progressif/path-opening-frames.txt").writeText(frames.joinToString("\n"))
        }
        recordFrame("before")
        ui.mainClock.autoAdvance=false
        try {
            ui.onNodeWithTag(target).performTouchInput {click(center)}
            repeat(4) {frame->
                ui.mainClock.advanceTimeByFrame();ui.waitForIdle()
                ui.onNodeWithTag(target).assertIsDisplayed()
                recordFrame("$frame")
                capture("path-opening-frame-$frame")
                val state=model.pathNavigationState!!.list
                val layout=state.layoutInfo
                val group=layout.visibleItemsInfo.single {it.key==target}
                val header=ui.onNodeWithTag("path-sticky-header").fetchSemanticsNode().boundsInRoot
                val firstTop=layout.viewportStartOffset+header.height
                val lastTop=(layout.viewportEndOffset-76f*context.resources.displayMetrics.density-group.size).coerceAtLeast(firstTop)
                val targetTop=(layout.viewportStartOffset+(layout.viewportEndOffset-layout.viewportStartOffset)/2f-group.size/2f).coerceIn(firstTop,lastTop)
                val nextDistance=abs(group.offset-targetTop)
                val previousDistance=distance ?: abs(previousTop-targetTop)
                assertTrue("The expanded group moved away from its measured destination: $previousDistance -> $nextDistance",nextDistance<=previousDistance+12f)
                distance=nextDistance
            }
        } finally {ui.mainClock.autoAdvance=true}
        ui.waitForIdle()
        assertGroupCentered(model,content.chapters[firstIndex+1].id)
    }
    @Test fun theFloatingButtonFindsTheNextLessonThenReturnsToTheTop() {
        val content=Content(context,examSnapshot=null)
        val model=model(content)
        val chapter=content.chapters[minOf(3,content.chapters.lastIndex)]
        content.lessons.takeWhile {it.id!=chapter.lessons.first().id}.forEach {model.progress.complete(it.id)}
        val next=content.nextLesson(model.progress.completed)!!
        val before=model.progress.export()
        ui.setContent {HamigoTheme {PathScreen(model,content)}}
        ui.onNodeWithTag("path-jump-next").performClick()
        ui.waitForIdle()
        ui.onNodeWithTag("path-lesson-${next.id}").assertIsDisplayed()
        assertAtFraction("path-lesson-${next.id}",.5f)
        ui.onNodeWithContentDescription("Revenir en haut du parcours").assertIsDisplayed()
        capture("path-next-target")
        ui.onNodeWithTag("path-jump-next").performClick()
        ui.waitForIdle()
        ui.onNodeWithText("Commencer le cours").assertIsDisplayed()
        assertEquals(before,model.progress.export())
        capture("path-return-top")
    }

    @Test fun theJumpArrowHidesNearTheTopOnlyWhileTheNextLessonIsAlreadyVisible() {
        val content=Content(context,examSnapshot=null)
        val model=model(content)
        ui.setContent {HamigoTheme {PathScreen(model,content)}}
        val list=ui.onNodeWithTag("path-list")
        fun assertTopCondition() {
            val header=ui.onNodeWithTag("path-sticky-header").fetchSemanticsNode().boundsInRoot
            val viewport=list.fetchSemanticsNode().boundsInRoot
            val row=ui.onAllNodesWithTag("path-lesson-$HamigoWelcomeLesson").fetchSemanticsNodes().singleOrNull()?.boundsInRoot
            val visible=row!=null&&row.bottom>header.bottom&&row.top<viewport.bottom-76f*context.resources.displayMetrics.density
            if(visible)ui.onNodeWithTag("path-jump-next").assertDoesNotExist()
            else ui.onNodeWithTag("path-jump-next").assertIsDisplayed()
        }
        assertTopCondition()
        capture("path-arrow-near-top")
        list.performScrollToNode(hasTestTag("path-goodbye"))
        ui.onNodeWithTag("path-jump-next").assertIsDisplayed()
        list.performScrollToIndex(0)
        assertTopCondition()
    }

    @Test fun purposeIconsRemainBesideALongTitleInACompactWindowWithLargeText() {
        val content=Content(context,examSnapshot=null)
        val lesson=content.lessons.filter {it.certificateWeight>0&&it.explorationWeight>0}.maxBy {it.title.length}
        val chapter=content.chapters.first {c->c.lessons.any {it.id==lesson.id}}
        val model=model(content).apply {
            progress.complete(lesson.id)
            pathNavigationState=PathNavigationState(chapter.id)
        }
        ui.setContent {
            val current=androidx.compose.ui.platform.LocalDensity.current
            CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(current.density,1.3f)) {
                HamigoTheme {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(320.dp).fillMaxHeight()) {
                        PathScreen(model,content)
                    }
                }
            }
        }
        ui.onNodeWithTag("path-list").performScrollToNode(hasTestTag("lesson-copy-"+lesson.id))
        ui.onNodeWithTag("lesson-copy-"+lesson.id).performScrollTo()
        val titleNode=ui.onNodeWithTag("lesson-title-"+lesson.id,useUnmergedTree=true)
        val title=titleNode.fetchSemanticsNode().boundsInRoot
        val summary=ui.onNodeWithTag("lesson-summary-"+lesson.id,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val copy=ui.onNodeWithTag("lesson-copy-"+lesson.id,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        titleNode.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult){it(layouts)}
        val layout=layouts.single()
        capture("path-purpose-icons-before-assert")
        assertTrue("A long title must wrap in this compact, enlarged-text fixture",layout.getLineForOffset(lesson.title.lastIndex)>0)
        assertEquals("The title must use the complete copy width, without reserving a status column",copy.width,title.width,1f)
        assertEquals("Purpose and completion both participate in the actual text layout",2,layout.placeholderRects.size)
        ui.onNodeWithTag("lesson-exploration-"+lesson.id,useUnmergedTree=true).assertDoesNotExist()
        val lastTitleLine=layout.getLineForOffset(lesson.title.lastIndex)
        val lastTitleGlyph=layout.getBoundingBox(lesson.title.lastIndex)
        val placeholders=layout.placeholderRects.map {requireNotNull(it)}
        val firstIconLine=layout.getLineForVerticalPosition(placeholders.first().center.y)
        assertTrue("The status group must follow the final title line, rather than the middle of the block",firstIconLine in lastTitleLine..lastTitleLine+1)
        if(firstIconLine==lastTitleLine) {
            assertTrue("Inline icons must follow the final title glyph",placeholders.first().left>=lastTitleGlyph.right)
        } else {
            val groupWidth=placeholders.last().right-placeholders.first().left
            val wordGap=with(layout.layoutInput.density){4.sp.toPx()}
            assertTrue("An icon-only line is justified only when the group cannot fit after the final word",
                lastTitleGlyph.right+wordGap+groupWidth>layout.size.width-1f)
        }
        for((index,prefix) in listOf("lesson-certificate-","lesson-completed-").withIndex()) {
            val icon=ui.onNodeWithTag(prefix+lesson.id,useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            val placeholder=placeholders[index]
            assertEquals("The real icon must occupy its inline placeholder",title.left+placeholder.left,icon.left,1.5f)
            assertEquals("The real icon must occupy its inline placeholder",title.top+placeholder.top,icon.top,1.5f)
            assertEquals("Statuses remain a single readable group",firstIconLine,layout.getLineForVerticalPosition(placeholder.center.y))
            if(index>0)assertTrue("Inline statuses must keep their order and spacing",placeholder.left>placeholders[index-1].right)
            assertTrue("The status icon must leave the summary clear",icon.bottom<=summary.top+1f)
            assertTrue("The status icon must fit before the menu",icon.right<=copy.right+1f)
        }
        assertTrue("The full title must fit above its summary",title.bottom<=summary.top)
        capture("path-purpose-icons-compact-font130")
    }

    @Test fun weightedProgressAndPurposeHelpAreReadableWithoutChangingTheProgress() {
        val content=Content(context,examSnapshot=null)
        val lesson=content.lessons.first {it.certificateWeight>0}
        ui.setContent {HamigoTheme {
            androidx.compose.foundation.layout.Column {
                CourseProgressBars(PathCompletion(.42f,.18f))
                CoursePurposeBadge(lesson)
            }
        }}
        ui.onNodeWithText("42 %").assertIsDisplayed()
        ui.onNodeWithText("18 %").assertIsDisplayed()
        capture("course-weighted-progress")
        ui.onNodeWithTag("course-purpose-${lesson.id}").performClick()
        ui.onNodeWithText("Compris").assertIsDisplayed()
        ui.onNodeWithText("Compris").performClick()
    }

    @Test fun theGuidedIntroductionIsFullyVisibleBeforeItsTargetIsExposed() {
        val content=Content(context,examSnapshot=null)
        val model=model(content)
        val targets=HamigoTourTargets()
        model.tour.start()
        ui.setContent {HamigoTheme {CompositionLocalProvider(LocalHamigoTourTargets provides targets) {PathScreen(model,content)}}}
        ui.waitUntil(10_000){targets.bounds["intro"]!=null}
        ui.waitForIdle()
        val list=ui.onNodeWithTag("path-list").fetchSemanticsNode().boundsInRoot
        val row=ui.onNodeWithTag("path-lesson-$HamigoWelcomeLesson").fetchSemanticsNode().boundsInRoot
        assertTrue("Introduction starts above viewport: $row / $list",row.top>=list.top)
        assertTrue("Introduction ends below viewport: $row / $list",row.bottom<=list.bottom)
        assertAtFraction("path-lesson-$HamigoWelcomeLesson",.56f)
        ui.onNodeWithTag("lesson-menu-$HamigoWelcomeLesson").assertIsNotEnabled()
        capture("path-guided-intro-visible")
    }

    @Test fun theStartedChapterOutlinesItsFutureStickerAtTheSameRightHandPosition() {
        val content=Content(context,examSnapshot=null)
        val chapter=content.chapters.first {it.lessons.size>1}
        val model=model(content)
        model.progress.complete(chapter.lessons.first().id)
        model.pathNavigationState=PathNavigationState(chapter.id)
        ui.setContent {HamigoTheme {PathScreen(model,content)}}
        ui.onNodeWithTag("path-list").performScrollToNode(hasTestTag("chapter-"+chapter.id))
        val started=ui.onNodeWithTag("chapter-started-badge-"+chapter.id,useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val number=ui.onNodeWithTag("chapter-number-"+chapter.id,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        val copy=ui.onNodeWithTag("chapter-copy-"+chapter.id,useUnmergedTree=true).fetchSemanticsNode().boundsInRoot
        assertTrue("The dashed future sticker belongs to the right of the chapter number",started.left>number.right)
        assertTrue("The future sticker must occupy the right part of the title zone",started.center.x>copy.center.x)
        capture("path-future-sticker-outline")
        ui.runOnIdle {
            chapter.lessons.forEach {model.progress.complete(it.id)}
            model.revision++
        }
        val finished=ui.onNodeWithTag("chapter-finished-badge-"+chapter.id,useUnmergedTree=true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        assertEquals("Finished and future stickers must share their placement",started.left,finished.left,1f)
        assertEquals(started.top,finished.top,1f)
        assertEquals(started.width,finished.width,1f)
        assertEquals(started.height,finished.height,1f)
        ui.onNodeWithTag("chapter-started-badge-"+chapter.id,useUnmergedTree=true).assertDoesNotExist()
        capture("path-future-sticker-completed")
    }

    @Test fun theFirstGuideHighlightsMoreThanTheRowWithoutEnablingItsNeighbours() {
        val content=Content(context,examSnapshot=null)
        val model=model(content)
        val targets=HamigoTourTargets()
        model.tour.start()
        ui.setContent {HamigoTheme {CompositionLocalProvider(LocalHamigoTourTargets provides targets) {
            androidx.compose.foundation.layout.Box {
                PathScreen(model,content)
                HamigoTourOverlay(model,targets)
            }
        }}}
        ui.waitUntil(10_000){targets.bounds["intro"]!=null}
        ui.waitForIdle()
        val row=ui.onNodeWithTag("path-lesson-$HamigoWelcomeLesson").fetchSemanticsNode().boundsInRoot
        val light=ui.onNodeWithTag("hamigo-tour-intro-highlight").fetchSemanticsNode().boundsInRoot
        val density=context.resources.displayMetrics.density
        assertTrue("The top of the highlight must breathe above the whole course row",row.top-light.top>=7f*density)
        assertTrue("The bottom of the highlight must breathe below the whole course row",light.bottom-row.bottom>=7f*density)
        assertTrue(light.height>=103f*density)
        assertTrue("The halo stays close to the actual course row",light.height<=maxOf(row.height+16f*density,104f*density)+2f)
        val expandedBefore=model.pathNavigationState!!.expanded.value
        ui.onRoot().performTouchInput {click(androidx.compose.ui.geometry.Offset(light.center.x,light.bottom-3f*density))}
        ui.runOnIdle {
            assertEquals(expandedBefore,model.pathNavigationState!!.expanded.value)
            assertNull(model.lesson)
            assertEquals(0,model.tour.step)
        }
        capture("path-guide-generous-highlight")
        ui.onNodeWithTag("lesson-copy-$HamigoWelcomeLesson").performClick()
        ui.runOnIdle {assertEquals(HamigoWelcomeLesson,model.lesson?.id);assertEquals(1,model.tour.step)}
    }

    @Test fun theFirstSectionHasItsSeparatorAndCoolPicoClosesThePath() {
        val content=Content(context,examSnapshot=null)
        val model=model(content)
        ui.setContent {HamigoTheme {PathScreen(model,content)}}
        val list=ui.onNodeWithTag("path-list")
        list.performScrollToNode(hasTestTag("path-section-divider-${content.sections.first().id}"))
        ui.onNodeWithTag("path-section-divider-${content.sections.first().id}").assertIsDisplayed()
        list.performScrollToNode(hasTestTag("path-goodbye"))
        ui.onNodeWithTag("path-goodbye").assertIsDisplayed()
        ui.onNodeWithContentDescription("Pico, la mascotte radio. Pico te salue avec ses lunettes de soleil").assertIsDisplayed()
        capture("path-cool-pico-footer")
    }

    @Test fun theMemoryTipIsTheLastProfilePanelAfterCourseProgress() {
        val content=Content(context,examSnapshot=null)
        val model=model(content)
        ui.setContent {HamigoTheme {ProfileScreen(model,content)}}
        val list=ui.onNodeWithTag("profile-list")
        list.performScrollToNode(hasTestTag("profile-memory-tip"))
        ui.onNodeWithTag("profile-memory-tip").assertIsDisplayed()
        list.performScrollToNode(hasText("Ton parcours"))
        val progression=ui.onNodeWithText("Ton parcours").fetchSemanticsNode().boundsInRoot
        val tip=ui.onNodeWithTag("profile-memory-tip").fetchSemanticsNode().boundsInRoot
        assertTrue("Memory tip must follow course progress",tip.top>progression.bottom)
        capture("profile-memory-tip-last")
    }
}
