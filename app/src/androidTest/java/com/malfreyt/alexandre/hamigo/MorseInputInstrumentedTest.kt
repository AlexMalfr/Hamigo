package com.malfreyt.alexandre.hamigo

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MorseInputInstrumentedTest {
    @get:Rule val ui = createComposeRule()
    private val prefs get() = InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("hamigo", Context.MODE_PRIVATE)
    private var priorSingle: Boolean? = null
    private var priorThreshold: Int? = null
    @Before fun rememberPreferences() {
        priorSingle = if(prefs.contains(GameplayPreferences.SINGLE_KEY)) prefs.getBoolean(GameplayPreferences.SINGLE_KEY, false) else null
        priorThreshold = if(prefs.contains(GameplayPreferences.THRESHOLD)) prefs.getInt(GameplayPreferences.THRESHOLD, 300) else null
        prefs.edit().putBoolean(GameplayPreferences.SINGLE_KEY, true).putInt(GameplayPreferences.THRESHOLD, 300).commit()
    }
    @After fun restorePreferences() {
        prefs.edit().apply {
            priorSingle?.let { putBoolean(GameplayPreferences.SINGLE_KEY, it) } ?: remove(GameplayPreferences.SINGLE_KEY)
            priorThreshold?.let { putInt(GameplayPreferences.THRESHOLD, it) } ?: remove(GameplayPreferences.THRESHOLD)
        }.commit()
    }
    @Test fun shortLongAndCancelledPressesUseOneKey() {
        var received = ""
        ui.setContent { HamigoTheme { MorseSignalInput { received += it } } }
        val key = ui.onNodeWithTag("morse-single-key")
        key.performTouchInput { down(center); advanceEventTime(120); up() }
        key.performTouchInput { down(center); advanceEventTime(350); up() }
        key.performTouchInput { down(center); advanceEventTime(450); cancel() }
        ui.runOnIdle { assertEquals(".-", received) }
        key.performClick()
        key.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick) { it() }
        ui.runOnIdle { assertEquals(".-.-", received) }
    }
    @Test fun changingTimingAndModeUpdatesTheLiveInput() {
        var received = ""
        ui.setContent { HamigoTheme { MorseSignalInput { received += it } } }
        ui.runOnIdle { prefs.edit().putInt(GameplayPreferences.THRESHOLD, 600).commit() }
        ui.onNodeWithTag("morse-single-key").performTouchInput { down(center); advanceEventTime(350); up() }
        ui.runOnIdle { assertEquals(".", received); prefs.edit().putBoolean(GameplayPreferences.SINGLE_KEY, false).commit() }
        ui.onNodeWithText("Point").performClick()
        ui.onNodeWithText("Trait").performClick()
        ui.onNodeWithTag("morse-single-key").assertDoesNotExist()
        ui.runOnIdle { assertEquals("..-", received) }
    }
    @Test fun disabledInputCannotTransmit() {
        var received = ""
        val enabled = mutableStateOf(true)
        ui.setContent { HamigoTheme { MorseSignalInput(enabled.value) { received += it } } }
        ui.runOnIdle { enabled.value = false }
        ui.onNodeWithTag("morse-single-key").assertIsNotEnabled().performTouchInput { click() }
        ui.runOnIdle { assertEquals("", received) }
    }

    @Test fun settingsPreviewSeparatesLettersAndWordsAndClearCancelsItsPendingPause() {
        ui.setContent { HamigoTheme { MorseSettingsPreview(MorseInputSettings(true,300)) } }
        val key=ui.onNodeWithTag("morse-single-key")
        key.performTouchInput {
            down(center);advanceEventTime(120);up()
            advanceEventTime(450);down(center);advanceEventTime(350);up()
            advanceEventTime(1050);down(center);advanceEventTime(120);up()
        }
        ui.onNodeWithTag("morse-preview-text").assertTextEquals("ET E")
        org.junit.Assert.assertTrue(ui.onAllNodesWithContentDescription("séparation entre mots",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty())
        val bitmap=ui.onNodeWithTag("morse-settings-preview").captureToImage().asAndroidBitmap()
        val folder=java.io.File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),"morse-audit").apply {mkdirs()}
        java.io.File(folder,"preview-letter-word.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
        ui.onNodeWithContentDescription("Effacer l’essai Morse").performClick()
        ui.onNodeWithTag("morse-preview-text").assertDoesNotExist()
        ui.mainClock.advanceTimeBy(2200)
        ui.onAllNodesWithContentDescription("séparation entre mots",useUnmergedTree=true).assertCountEquals(0)
    }
}
