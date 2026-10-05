package com.malfreyt.alexandre.hamigo

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
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the same navigation component as the app, without changing saved user data. */
@RunWith(AndroidJUnit4::class)
class NavigationBarInstrumentedTest {
    @get:Rule val ui = createComposeRule()

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
        assertEquals("The content must continue under the circle's overhang, up to the white body.", bodyTop, content.bottom, 1f)
        assertEquals("Only the body and system navigation inset reserve content space.", bar.height - overhang, rootBounds.bottom - content.bottom, 1f)
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
}
