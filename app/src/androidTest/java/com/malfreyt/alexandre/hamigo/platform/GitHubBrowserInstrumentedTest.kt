package com.malfreyt.alexandre.hamigo.platform

import android.content.ClipboardManager
import android.content.ClipData
import android.app.PendingIntent
import android.graphics.Bitmap
import android.content.Intent
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.browser.customtabs.CustomTabsIntent
import com.malfreyt.alexandre.hamigo.AppModel
import com.malfreyt.alexandre.hamigo.MainActivity
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File

class GitHubBrowserInstrumentedTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()
    private lateinit var model: AppModel
    private val session get() = DeviceOAuth.Session("synthetic-no-authorization", "ABCD-EFGH",
        "https://github.com/login/device", 900, 5, SystemClock.elapsedRealtime() + 900_000)

    @Before fun prepare() {
        model = ViewModelProvider(ui.activity)[AppModel::class.java]
        ui.waitUntil(30_000) { model.content != null }
        ui.runOnIdle { model.showWelcome = false; model.route = "friends" }
    }

    @Test fun intentUsesDefaultBrowserSessionAndCloseButton() {
        val intent = GitHubBrowser.intentFor(ui.activity, session)
        val browser = ui.activity.packageManager.resolveActivity(
            Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://example.com/"))
                .addCategory(Intent.CATEGORY_BROWSABLE), android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeUnless { it == "android" }
        assertEquals(browser, intent.`package`)
        assertEquals("https://github.com/login/device", intent.dataString)
        assertTrue(intent.hasExtra("android.support.customtabs.extra.SESSION"))
        assertFalse(intent.getBooleanExtra("androidx.browser.customtabs.extra.ENABLE_EPHEMERAL_BROWSING", false))
        assertEquals(0, intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK)
        val result = GitHubBrowser.returnIntent(ui.activity)
        assertEquals(MainActivity::class.java.name, result.component?.className)
        assertTrue(result.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(result.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test fun compactCodeControlsCopyEightCharactersWithoutOpeningWebView() {
        ui.runOnIdle { model.sync.disconnect(); model.oauthSession = session }
        ui.onNodeWithText("ABCD-EFGH").assertIsDisplayed()
        ui.onNodeWithContentDescription("Rouvrir l’onglet GitHub").assertIsDisplayed()
        ui.onNodeWithText("Ouvrir GitHub ici").assertDoesNotExist()
        ui.runOnIdle {
            copyGitHubCode(ui.activity, "ABCD-EFGH", notify = false)
            val manager = ui.activity.getSystemService(ClipboardManager::class.java)
            assertEquals("ABCDEFGH", manager.primaryClip?.getItemAt(0)?.text?.toString())
            model.oauthSession = null
        }
    }

    @Test fun completedAuthorizationReturnsFromRealCustomTabWhileHamigoIsPaused() {
        val device = session
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        ui.runOnIdle { model.oauthSession = device; model.openGitHubBrowser() }
        val expected = GitHubBrowser.intentFor(ui.activity, device).`package`
        assertNotNull("Test emulator needs a default browser", expected)
        waitFor("Custom Tab should cover Hamigo") {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == expected
        }
        waitFor("Custom Tab toolbar should expose its code-copy action") {
            hasCodeAction(instrumentation.uiAutomation.rootInActiveWindow)
        }
        assertTrue(model.githubTabOpen)
        capture("custom-tab")
        instrumentation.runOnMainSync {
            ui.activity.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("fixture", "before-toolbar-action"))
        }
        @Suppress("DEPRECATION")
        val copy = GitHubBrowser.intentFor(ui.activity, device)
            .getBundleExtra(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE)
            ?.getParcelable<PendingIntent>(CustomTabsIntent.KEY_PENDING_INTENT)
        assertNotNull(copy)
        copy!!.send()
        SystemClock.sleep(200)
        // Same event as awaitToken succeeding, on the main dispatcher while the host is paused.
        instrumentation.runOnMainSync {
            model.oauthSession = null
            model.githubBrowserCommands.value = GitHubBrowserCommand.Close
        }
        waitFor("Authorization should restore Hamigo automatically") {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == ui.activity.packageName
        }
        assertFalse(model.githubTabOpen)
        ui.onNodeWithText("Sur la même fréquence").assertIsDisplayed()
        ui.runOnIdle {
            assertEquals("ABCDEFGH", ui.activity.getSystemService(ClipboardManager::class.java)
                .primaryClip?.getItemAt(0)?.text?.toString())
        }
        capture("hamigo-return")
    }

    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (SystemClock.elapsedRealtime() < deadline) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        fail(message)
    }

    private fun capture(name: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val folder = File(context.getExternalFilesDir(null), "browser-audit").apply { mkdirs() }
        SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        checkNotNull(bitmap)
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun hasCodeAction(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        if (node.contentDescription?.toString() == "Copier le code GitHub") return true
        return (0 until node.childCount).any { hasCodeAction(node.getChild(it)) }
    }
}
