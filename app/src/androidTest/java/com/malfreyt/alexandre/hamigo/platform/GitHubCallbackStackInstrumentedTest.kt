package com.malfreyt.alexandre.hamigo.platform

import android.content.Intent
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.AppModel
import com.malfreyt.alexandre.hamigo.MainActivity
import org.junit.Assert.*
import org.junit.Test

/** A real Custom Tab covers MainActivity; a synthetic App Link must reuse its instance. */
class GitHubCallbackStackInstrumentedTest {
    @Test fun fallbackReturnReusesTheActivityUnderTheBrowserAndRemovesTheTab() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            lateinit var original: MainActivity
            lateinit var model: AppModel
            scenario.onActivity {
                original = it; model = ViewModelProvider(it)[AppModel::class.java]
                model.showWelcome = false
                model.route = "friends"
                model.oauthSession = DeviceOAuth.Session("synthetic-no-authorization", "ABCD-EFGH",
                    "https://github.com/login/device", 900, 5, SystemClock.elapsedRealtime() + 900_000)
                model.openGitHubBrowser()
            }
            val browser = GitHubBrowser.intentFor(context, model.oauthSession!!).`package`
            waitFor("A real browser tab must cover Hamigo") {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == browser
            }
            val callback = Uri.parse("${GitHubPkce.CALLBACK}?code=synthetic-no-authorization&state=synthetic-unmatched")
            // Same ACTION_VIEW dispatch as a browser's App Link, with no CLEAR_TOP supplied by Hamigo.
            instrumentation.runOnMainSync {
                original.startActivity(Intent(Intent.ACTION_VIEW, callback).addCategory(Intent.CATEGORY_BROWSABLE)
                    .setPackage(context.packageName))
            }
            waitFor("The callback must return to Hamigo") {
                instrumentation.uiAutomation.rootInActiveWindow?.packageName?.toString() == context.packageName
            }
            // Android resumes the underlying activity before the tab's asynchronous destruction.
            waitFor("Returning must destroy the tab above Hamigo; records=${activityRecords()}") {
                activityRecords().none { it.contains(browser!!) && it.contains(" t${original.taskId}") }
            }
            val records = activityRecords()
            val mains = records.filter { it.contains("${context.packageName}/.MainActivity") }
            assertEquals("OAuth fallback must retain one MainActivity; records=$records", 1, mains.size)
            assertFalse("Returning must remove the temporary browser from Hamigo's task",
                records.any { it.contains(browser!!) && it.contains(" t${original.taskId}") })
            scenario.onActivity { resumed ->
                assertSame("The pending authorization belongs to the original ViewModel", original, resumed)
                assertSame(model, ViewModelProvider(resumed)[AppModel::class.java])
                assertNull("Callbacks must be removed from the launch Intent", resumed.intent.data)
                model.oauthSession = null
            }
        }
    }

    private fun activityRecords(): List<String> {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        return ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand("dumpsys activity activities"))
            .bufferedReader().use { reader -> reader.readLines().filter { it.trimStart().startsWith("* Hist") } }
    }
    private fun waitFor(message: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 20_000L
        while(SystemClock.elapsedRealtime() < deadline) { if(condition()) return; SystemClock.sleep(100) }
        fail(message)
    }
}
