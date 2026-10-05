package com.malfreyt.alexandre.hamigo

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the same navigation component as the app, without changing saved user data. */
@RunWith(AndroidJUnit4::class)
class NavigationBarInstrumentedTest {
    @get:Rule val ui = createComposeRule()

    @Test fun pendingRequestBadgeAppearsAndClearsWithoutChangingTheBarBounds() {
        var count by mutableStateOf(0)
        ui.setContent { HamigoTheme {
            Box(Modifier.fillMaxSize()) {
                HamigoBottomBar("path",{},Modifier.align(Alignment.BottomCenter).testTag("badge-test-bar"),friendRequestCount=count)
            }
        } }
        val before=ui.onNodeWithTag("badge-test-bar").fetchSemanticsNode().boundsInRoot
        ui.onNodeWithTag("friend-request-badge",useUnmergedTree=true).assertDoesNotExist()
        ui.runOnIdle {count=2}
        ui.onNodeWithContentDescription("2 demandes d’amis en attente",useUnmergedTree=true).assertIsDisplayed()
        assertEquals(before,ui.onNodeWithTag("badge-test-bar").fetchSemanticsNode().boundsInRoot)
        ui.runOnIdle {count=0}
        ui.onNodeWithTag("friend-request-badge",useUnmergedTree=true).assertDoesNotExist()
    }

    @Test fun destinationsHaveTheRequestedOrderAndEveryTapUpdatesTheirSelectedState() {
        var route by mutableStateOf("path")
        ui.setContent {
            HamigoTheme {
                Box(Modifier.fillMaxSize()) {
                    HamigoBottomBar(route, { route = it }, Modifier.align(Alignment.BottomCenter).fillMaxWidth())
                }
            }
        }
        val destinations = listOf(
            "practice" to "Défis", "resources" to "Mémo", "path" to "Parcours",
            "friends" to "Équipe", "profile" to "Moi"
        )
        val bounds = destinations.map { (_, label) -> tab(label).fetchSemanticsNode().boundsInRoot }
        bounds.zipWithNext().forEach { (left, right) ->
            assertTrue("The tabs should read Défis, Mémo, Parcours, Équipe, Moi from left to right.", left.center.x < right.center.x)
            assertTrue("Adjacent destinations must have separate touch targets.", left.right <= right.left)
        }
        // The centre includes its overhang; side tabs only occupy the solid navigation body.
        val centre = bounds[2]
        val centreIcon = ui.onNodeWithTag("navigation-icon-path", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val leftIcon = ui.onNodeWithTag("navigation-icon-resources", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rightIcon = ui.onNodeWithTag("navigation-icon-friends", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Parcours should stand above the neighbouring icons.", centreIcon.top < minOf(leftIcon.top, rightIcon.top))
        val minimumTarget = with(ui.density) { 48.dp.toPx() }
        bounds.forEach { target ->
            assertTrue("Every destination needs a generous touch target.", target.width >= minimumTarget && target.height >= minimumTarget)
        }
        val bodyTop = centre.top + with(ui.density) { HamigoNavigationOverhang.toPx() }
        bounds.filterIndexed { index, _ -> index != 2 }.forEach { target ->
            assertTrue("Side tabs must not intercept touches in the content above the bar.", target.top >= bodyTop - 1f)
        }
        assertTrue("The central circle should be visually larger than the side icons.", centreIcon.height > maxOf(leftIcon.height, rightIcon.height))
        tab("Parcours").assertIsSelected()

        destinations.forEach { (destination, label) ->
            tab(label).assertIsDisplayed().assertHasClickAction().performClick()
            ui.runOnIdle { assertEquals(destination, route) }
            destinations.forEach { (other, otherLabel) ->
                if (other == destination) tab(otherLabel).assertIsSelected()
                else tab(otherLabel).assertIsNotSelected()
            }
        }
    }

    @Test fun raisedCircleOverlaysTheContentWithoutAnOpaqueBandAndRemainsClickable() {
        val backdrop = Color(0xFF75309B)
        var route by mutableStateOf("resources")
        ui.setContent {
            HamigoTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize().testTag("overhang-root"),
                    containerColor = Color.Black,
                    contentWindowInsets = WindowInsets(0),
                    bottomBar = {
                        HamigoBottomBar(route, { route = it }, Modifier.testTag("overhang-bar"))
                    },
                ) { padding ->
                    // Use the production padding calculation: only the solid body reserves space.
                    val contentPadding = hamigoContentPadding(padding, LocalLayoutDirection.current, raisedBarVisible = true)
                    Box(Modifier.fillMaxSize().padding(contentPadding).consumeWindowInsets(contentPadding)) {
                        Box(Modifier.fillMaxSize().background(backdrop).testTag("overhang-content"))
                    }
                }
            }
        }
        val root = ui.onNodeWithTag("overhang-root")
        val rootBounds = root.fetchSemanticsNode().boundsInRoot
        val bar = ui.onNodeWithTag("overhang-bar").fetchSemanticsNode().boundsInRoot
        val content = ui.onNodeWithTag("overhang-content").fetchSemanticsNode().boundsInRoot
        val circle = ui.onNodeWithTag("navigation-icon-path", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val overhang = with(ui.density) { HamigoNavigationOverhang.toPx() }
        val bodyTop = bar.top + overhang
        val cutoutDepth = with(ui.density) { HamigoNavigationCutoutDepth.toPx() }
        assertEquals("Real content must continue behind the entire cutout, including below the white body's top.", bar.top + cutoutDepth, content.bottom, 1f)
        assertEquals("The reserved area must start below the transparent hole.", bar.height - cutoutDepth, rootBounds.bottom - content.bottom, 1f)
        assertTrue("The centre circle should extend above the white body.", circle.top < bodyTop)

        var pixels = root.captureToImage().toPixelMap()
        fun assertPixel(expected: Color, x: Float, y: Float, reason: String) {
            val actual = pixels[(x - rootBounds.left).toInt(), (y - rootBounds.top).toInt()]
            assertEquals("$reason (red)", expected.red, actual.red, .02f)
            assertEquals("$reason (green)", expected.green, actual.green, .02f)
            assertEquals("$reason (blue)", expected.blue, actual.blue, .02f)
            assertEquals("$reason (alpha)", expected.alpha, actual.alpha, .02f)
        }
        val edge = with(ui.density) { 12.dp.toPx() }
        val belowBody = with(ui.density) { 4.dp.toPx() }
        // Sampling both sides catches a full-width beige or white strip behind the raised button.
        listOf(rootBounds.left + edge, rootBounds.right - edge).forEach { x ->
            assertPixel(backdrop, x, bar.top + overhang / 2f, "The overhang must reveal the content behind it")
            assertPixel(Color.White, x, bodyTop + belowBody, "The navigation body should still be white")
        }
        fun assertCutoutShowsBackdrop(x: Float, y: Float) {
            val actual = pixels[(x-rootBounds.left).toInt(), (y-rootBounds.top).toInt()]
            // The inset shadow may darken the real colour, but must never replace it with Cream.
            assertTrue("The hole must reveal the coloured screen through its translucent shadow.",
                actual.red > backdrop.red*.65f && actual.red <= backdrop.red+.03f &&
                actual.green > backdrop.green*.65f && actual.green <= backdrop.green+.03f &&
                actual.blue > backdrop.blue*.65f && actual.blue <= backdrop.blue+.03f)
        }
        val ringOffset = with(ui.density) { 36.dp.toPx() }
        assertCutoutShowsBackdrop(circle.center.x-ringOffset, circle.center.y)
        assertCutoutShowsBackdrop(circle.center.x+ringOffset, circle.center.y)
        assertCutoutShowsBackdrop(circle.center.x, circle.center.y+ringOffset)
        val shadowPixel = pixels[(circle.center.x+ringOffset-rootBounds.left).toInt(), (circle.center.y-rootBounds.top).toInt()]
        assertTrue("The concave edge must cast a soft visible shadow inside the hole.", shadowPixel.blue < backdrop.blue-.01f)
        capture(root, "navbar-real-cutout")
        val topOfCircle = circle.top + with(ui.density) { 6.dp.toPx() }
        assertTrue("The regression click must hit the protruding part of the circle.", topOfCircle < bodyTop)
        assertPixel(Coral, circle.center.x, topOfCircle, "The visible raised circle must still be drawn")
        val centreTarget = tab("Parcours").fetchSemanticsNode().boundsInRoot
        // Freeze automatic time advancement so the press stays down while its ripple is captured.
        ui.mainClock.autoAdvance = false
        root.performTouchInput {
            down(Offset(circle.center.x - rootBounds.left, topOfCircle - rootBounds.top))
            advanceEventTime(650)
        }
        try {
            ui.mainClock.advanceTimeBy(650)
            pixels = root.captureToImage().toPixelMap()
            listOf(rootBounds.left + edge, rootBounds.right - edge).forEach { x ->
                assertPixel(backdrop, x, bar.top + overhang / 2f, "A held press must not tint the content beside the circle")
                assertPixel(Color.White, x, bodyTop + belowBody, "A held press must not tint the whole navigation body")
            }
            assertPixel(backdrop, centreTarget.right - belowBody, bar.top + belowBody, "The centre tab ripple must stay inside the circular icon")
            assertPixel(Color.White, circle.center.x, centreTarget.bottom - belowBody, "The centre tab ripple must not fill its rectangular touch target")
            assertCutoutShowsBackdrop(circle.center.x-ringOffset, circle.center.y)
            assertCutoutShowsBackdrop(circle.center.x+ringOffset, circle.center.y)
            assertCutoutShowsBackdrop(circle.center.x, circle.center.y+ringOffset)
            capture(root, "navbar-real-cutout-held")
        } finally {
            root.performTouchInput { up() }
            ui.mainClock.autoAdvance = true
        }
        ui.runOnIdle { assertEquals("path", route) }
        tab("Parcours").assertIsSelected()
    }

    private fun tab(label: String) = ui.onNode(
        hasText(label) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
    )

    private fun capture(node: SemanticsNodeInteraction, name: String) {
        val directory = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "navigation-audit").apply { mkdirs() }
        val bitmap = node.captureToImage().asAndroidBitmap()
        try {
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
