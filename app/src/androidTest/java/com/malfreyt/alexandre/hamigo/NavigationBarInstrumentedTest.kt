package com.malfreyt.alexandre.hamigo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        // Every tab retains a full-height touch target; prominence belongs to its visible icon.
        val centre = bounds[2]
        val centreIcon = ui.onNodeWithTag("navigation-icon-path", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val leftIcon = ui.onNodeWithTag("navigation-icon-resources", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rightIcon = ui.onNodeWithTag("navigation-icon-friends", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Parcours should stand above the neighbouring icons.", centreIcon.top < minOf(leftIcon.top, rightIcon.top))
        val minimumTarget = with(ui.density) { 48.dp.toPx() }
        assertTrue("The central destination needs a generous touch target.", centre.width >= minimumTarget && centre.height >= minimumTarget)
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

    private fun tab(label: String) = ui.onNode(
        hasText(label) and hasClickAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.Selected)
    )
}
