package com.malfreyt.alexandre.hamigo.platform

import android.content.ClipboardManager
import android.content.ClipData
import android.app.PendingIntent
import android.graphics.Bitmap
import android.graphics.Rect
import android.content.Intent
import android.os.SystemClock
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
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
        assertEquals("Switching to a password manager or authenticator must preserve authorization",
            0, intent.flags and Intent.FLAG_ACTIVITY_NO_HISTORY)
        val result = GitHubBrowser.returnIntent(ui.activity)
        assertEquals(MainActivity::class.java.name, result.component?.className)
        assertTrue(result.flags and Intent.FLAG_ACTIVITY_CLEAR_TOP != 0)
        assertTrue(result.flags and Intent.FLAG_ACTIVITY_SINGLE_TOP != 0)
    }

    @Test fun toolbarShowsTheCodeBesideCopyWithinTheTopActionSizeLimit() {
        val intent = GitHubBrowser.intentFor(ui.activity, session)
        val action = intent.getBundleExtra(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE)!!
        @Suppress("DEPRECATION")
        val bitmap = action.getParcelable<Bitmap>(CustomTabsIntent.KEY_ICON)!!
        val density = ui.activity.resources.displayMetrics.density
        assertEquals(24f, bitmap.height / density, 1f)
        assertTrue("Wider icons may be relocated to the bottom", bitmap.width <= 2 * bitmap.height)
        assertTrue(bitmap.width > bitmap.height)
        val description = action.getString(CustomTabsIntent.KEY_DESCRIPTION).orEmpty()
        assertTrue("Screen readers need the complete code", description.contains("ABCD-EFGH"))
        assertTrue(description.contains("copier", ignoreCase = true))
        assertFalse(intent.getBooleanExtra(CustomTabsIntent.EXTRA_TINT_ACTION_BUTTON, true))
        @Suppress("DEPRECATION")
        val entries = intent.getParcelableArrayListExtra<android.os.Bundle>(CustomTabsIntent.EXTRA_MENU_ITEMS).orEmpty()
        assertTrue("A readable copy menu entry remains available", entries.any {
            it.getString(CustomTabsIntent.KEY_MENU_ITEM_TITLE).orEmpty().let { text ->
                text.contains("ABCD-EFGH") && text.contains("copier", ignoreCase = true)
            }
        })
    }

    @Test fun hostResumeAndRecreationDoNotPretendTheTabHasClosed() {
        ui.runOnIdle { model.githubBrowserState.open = true }
        ui.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        ui.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        assertTrue("Returning to Hamigo is not proof that a minimized tab finished", model.githubTabOpen)
        ui.activityRule.scenario.recreate()
        assertTrue("The open-tab state must survive host recreation", model.githubTabOpen)
        ui.runOnIdle {
            GitHubBrowser(ui.activity, model.githubBrowserState) {}.onTabResult()
            assertFalse("Only a completed browser activity result clears the open state", model.githubTabOpen)
        }
    }

    @Test fun expiredOrUnrelatedCodeActionsDoNotOverwriteClipboard() {
        ui.runOnIdle {
            val clipboard = ui.activity.getSystemService(ClipboardManager::class.java)
            clipboard.setPrimaryClip(ClipData.newPlainText("fixture", "keep-this"))
            val action = Intent(ui.activity, GitHubCodeReceiver::class.java)
                .setAction(GitHubCodeReceiver.ACTION)
                .putExtra("user_code", "ABCD-EFGH")
                .putExtra("expires_elapsed", SystemClock.elapsedRealtime() - 1)
            GitHubCodeReceiver().onReceive(ui.activity, action)
            assertEquals("keep-this", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
            action.setAction("unrelated").putExtra("expires_elapsed", SystemClock.elapsedRealtime() + 60_000)
            GitHubCodeReceiver().onReceive(ui.activity, action)
            assertEquals("keep-this", clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
    }

    @Test fun anOldTabResultCannotClearTheNewlyOpenedTab() {
        ui.runOnIdle {
            val state = GitHubBrowserState()
            val launches = mutableListOf<Intent>()
            val browser = GitHubBrowser(ui.activity, state) { launches.add(it) }
            browser.open(session)
            assertEquals(1, state.launchesAwaitingResult)
            browser.close()
            assertFalse(state.open)
            assertEquals("Closing the UI does not deliver its result synchronously", 1, state.launchesAwaitingResult)
            browser.open(session)
            assertEquals(2, state.launchesAwaitingResult)
            browser.onTabResult()
            assertEquals(1, state.launchesAwaitingResult)
            assertTrue("The old tab's delayed result must preserve the new tab", state.open)
            browser.close()
            assertFalse("The new tab must remain closable after the old result", state.open)
            browser.onTabResult()
            assertEquals(0, state.launchesAwaitingResult)
            assertFalse(state.open)
            assertEquals(2, launches.size)

            // Reopening before the first tab reports dismissal has the same ordering risk.
            browser.open(session)
            browser.open(session)
            browser.onTabResult()
            assertEquals(1, state.launchesAwaitingResult)
            assertTrue(state.open)
            browser.onTabResult()
            assertEquals(0, state.launchesAwaitingResult)
            assertFalse(state.open)
        }
    }

    @Test fun copyActionsRemainBoundToTheirOwnDeviceSession() {
        val first = session
        val second = DeviceOAuth.Session("synthetic-other-authorization", "IJKL-MNOP",
            "https://github.com/login/device", 900, 5, SystemClock.elapsedRealtime() + 900_000)
        val firstCopy = copyAction(first)
        val secondCopy = copyAction(second)
        assertNotEquals("A new session must not update an older tab's copy action", firstCopy, secondCopy)
        assertEquals("Reopening the same session retains its action identity", firstCopy, copyAction(first))
        firstCopy.send()
        waitFor("The first tab still copies its own visible code") { clipboardValue() == "ABCDEFGH" }
        secondCopy.send()
        waitFor("The second tab copies the new code") { clipboardValue() == "IJKLMNOP" }
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
            findCodeAction(instrumentation.uiAutomation.rootInActiveWindow) != null
        }
        val codeButton = findCodeAction(instrumentation.uiAutomation.rootInActiveWindow)!!
        val bounds = Rect().also { codeButton.getBoundsInScreen(it) }
        assertTrue("The visible code and copy control belong in the top toolbar, bounds=$bounds",
            bounds.centerY() < ui.activity.resources.displayMetrics.heightPixels / 4)
        val taskId = ui.activity.taskId
        val before = activityRecords()
        assertTrue("The tab must be launched into Hamigo's task before testing its removal",
            before.any { isTabInTask(it, taskId, expected!!) })
        auditFolder().resolve("tasks-with-tab.txt").writeText(before.joinToString("\n"))
        assertTrue(model.githubTabOpen)
        capture("custom-tab")
        instrumentation.runOnMainSync {
            ui.activity.getSystemService(ClipboardManager::class.java)
                .setPrimaryClip(ClipData.newPlainText("fixture", "before-toolbar-action"))
        }
        assertTrue("Copy must be usable from the actual browser toolbar",
            codeButton.performAction(AccessibilityNodeInfo.ACTION_CLICK))
        SystemClock.sleep(200)
        // Same event as awaitToken succeeding, on the main dispatcher while the host is paused.
        instrumentation.runOnMainSync {
            model.oauthSession = null
            model.githubBrowserCommands.value = GitHubBrowserCommand.Close
        }
        waitFor("Authorization should restore Hamigo automatically") {
            instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == ui.activity.packageName
        }
        waitFor("Completion must destroy the temporary browser activity, not just cover it") {
            activityRecords().none { isTabInTask(it, taskId, expected!!) }
        }
        auditFolder().resolve("tasks-after-close.txt").writeText(activityRecords().joinToString("\n"))
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
        val folder = auditFolder()
        SystemClock.sleep(500)
        val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        checkNotNull(bitmap)
        File(folder, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun findCodeAction(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        val description = node.contentDescription?.toString().orEmpty()
        val matches = mutableListOf<AccessibilityNodeInfo>()
        if (description.contains("ABCD-EFGH") && description.contains("copier", ignoreCase = true)) matches.add(node)
        for (index in 0 until node.childCount) findCodeAction(node.getChild(index))?.let { matches.add(it) }
        return matches.minByOrNull { Rect().also(it::getBoundsInScreen).centerY() }
    }

    private fun auditFolder(): File {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        return File(context.getExternalFilesDir(null), "browser-audit").apply { mkdirs() }
    }

    @Suppress("DEPRECATION")
    private fun copyAction(device: DeviceOAuth.Session): PendingIntent =
        GitHubBrowser.intentFor(ui.activity, device)
            .getBundleExtra(CustomTabsIntent.EXTRA_ACTION_BUTTON_BUNDLE)!!
            .getParcelable(CustomTabsIntent.KEY_PENDING_INTENT)!!

    private fun clipboardValue(): String? {
        var value: String? = null
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            value = ui.activity.getSystemService(ClipboardManager::class.java)
                .primaryClip?.getItemAt(0)?.text?.toString()
        }
        return value
    }

    private fun activityRecords(): List<String> {
        val descriptor = InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("dumpsys activity activities")
        val output = ParcelFileDescriptor.AutoCloseInputStream(descriptor).bufferedReader().use { it.readText() }
        return output.lineSequence().filter { Regex("Hist\\s+#\\d+").containsMatchIn(it) && it.contains("ActivityRecord{") }.toList()
    }

    private fun isTabInTask(line: String, taskId: Int, browser: String) =
        line.contains(browser) && line.contains("CustomTabActivity") && Regex(" t$taskId(?:[ }])").containsMatchIn(line)
}
