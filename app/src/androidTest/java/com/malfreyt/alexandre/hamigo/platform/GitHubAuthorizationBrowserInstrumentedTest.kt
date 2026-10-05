package com.malfreyt.alexandre.hamigo.platform

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.SystemClock
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.browser.auth.AuthTabIntent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleCallback
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.malfreyt.alexandre.hamigo.AppModel
import com.malfreyt.alexandre.hamigo.MainActivity
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicReference

/** Synthetic URLs verify Android integration without granting a personal GitHub authorization. */
@RunWith(AndroidJUnit4::class)
class GitHubAuthorizationBrowserInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val authorization = "https://github.com/login/oauth/authorize?client_id=synthetic&state=synthetic"

    @Test fun authorizationSharesDefaultBrowserSessionAndRequestsVerifiedHttpsReturn() {
        val tab = GitHubBrowser.authorizationTabFor(context)
        val launcher = RecordingLauncher()
        val callback = Uri.parse(GitHubPkce.CALLBACK)
        tab.launch(launcher, Uri.parse(authorization), callback.host!!, callback.path!!)
        val intent = launcher.intent!!
        val browser = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com/"))
                .addCategory(Intent.CATEGORY_BROWSABLE), PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo?.packageName?.takeUnless { it == "android" }
        assertEquals(browser, intent.`package`)
        assertEquals(authorization, intent.dataString)
        assertTrue(intent.getBooleanExtra(AuthTabIntent.EXTRA_LAUNCH_AUTH_TAB, false))
        assertEquals(callback.host, intent.getStringExtra(AuthTabIntent.EXTRA_HTTPS_REDIRECT_HOST))
        assertEquals(callback.path, intent.getStringExtra(AuthTabIntent.EXTRA_HTTPS_REDIRECT_PATH))
        assertFalse(tab.isEphemeralBrowsingEnabled)
        assertFalse(intent.getBooleanExtra(CustomTabsIntent.EXTRA_ENABLE_EPHEMERAL_BROWSING, true))
        assertTrue(intent.hasExtra(CustomTabsIntent.EXTRA_SESSION))
        assertEquals(CustomTabsIntent.CLOSE_BUTTON_POSITION_START, CustomTabsIntent.getCloseButtonPosition(intent))
        assertEquals(CustomTabsIntent.SHARE_STATE_OFF, intent.getIntExtra(CustomTabsIntent.EXTRA_SHARE_STATE, -1))
        assertEquals(0, intent.flags and (Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_HISTORY))
    }

    @Test fun oldDeviceResultCannotClearNewAuthorizationTab() {
        withActivity { activity ->
            val state = GitHubBrowserState()
            var authorizations = 0
            val browser = GitHubBrowser(activity, state, launchAuthorization = { _, _ -> authorizations++ }) {}
            browser.open(DeviceOAuth.Session("synthetic", "ABCD-EFGH", "https://github.com/login/device",
                900, 5, SystemClock.elapsedRealtime() + 900_000))
            browser.close()
            browser.openAuthorization(authorization)
            assertEquals(2, state.launchesAwaitingResult)
            browser.onTabResult()
            assertEquals(1, state.launchesAwaitingResult)
            assertTrue("The previous device tab result must preserve the new authorization tab", state.open)
            browser.close()
            assertFalse(state.open)
            browser.onTabResult()
            assertEquals(0, state.launchesAwaitingResult)
            assertEquals(1, authorizations)
        }
    }

    @Test fun authorizationLaunchFailureRestoresPriorTabState() {
        withActivity { activity ->
            val state = GitHubBrowserState().apply { open = true; launchesAwaitingResult = 1 }
            val browser = GitHubBrowser(activity, state,
                launchAuthorization = { _, _ -> throw IllegalStateException("synthetic launch failure") }) {}
            val failure = runCatching { browser.openAuthorization(authorization) }.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertEquals(1, state.launchesAwaitingResult)
            assertTrue("A failed new launch must preserve the earlier tab", state.open)
        }
    }

    @Test fun unrelatedAuthorizationUrlsAreNeverLaunched() {
        withActivity { activity ->
            val state = GitHubBrowserState()
            var launches = 0
            val browser = GitHubBrowser(activity, state, launchAuthorization = { _, _ -> launches++ }) {}
            listOf("http://github.com/login/oauth/authorize", "https://github.com.evil.example/login/oauth/authorize",
                "https://github.com/login/device", "https://github.com/login/oauth/authorize#unexpected",
                "https://attacker@github.com/login/oauth/authorize").forEach { url ->
                assertTrue(runCatching { browser.openAuthorization(url) }.exceptionOrNull() is IllegalArgumentException)
            }
            assertEquals(0, launches)
            assertEquals(0, state.launchesAwaitingResult)
            assertFalse(state.open)
        }
    }

    @Test fun coldHttpsCallbackIsSanitizedBeforeRecreationAndNeverBecomesAnInvite() {
        val callback = "${GitHubPkce.CALLBACK}?code=synthetic-no-authorization&state=synthetic-unmatched"
        // singleTask deliberately reuses a running host; force a fresh test task to exercise onCreate.
        val intent = Intent(context, MainActivity::class.java).setAction(Intent.ACTION_VIEW).setData(Uri.parse(callback))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resumed = AtomicReference<MainActivity?>()
        val lastActivity = AtomicReference<MainActivity?>()
        val observer = ActivityLifecycleCallback { activity, stage ->
            if (activity is MainActivity) {
                lastActivity.set(activity)
                if (stage == Stage.RESUMED) resumed.set(activity)
            }
        }
        val monitor = ActivityLifecycleMonitorRegistry.getInstance()
        instrumentation.runOnMainSync { monitor.addLifecycleCallback(observer) }
        try {
            // ActivityScenario matches by launch Intent, which intentionally changes when its code is scrubbed.
            context.startActivity(intent)
            val first = awaitResumed(resumed)
            instrumentation.runOnMainSync {
                val activity = first
                val model = ViewModelProvider(activity)[AppModel::class.java]
                assertNull("Callback must not be interpreted as a friend invitation", model.pendingInvite)
                assertNull("Authorization URLs must not enter JSON import", model.incoming)
                assertNull("The Activity launch intent must not retain an authorization code", activity.intent.data)
                resumed.set(null)
                activity.recreate()
            }
            val recreated = awaitResumed(resumed, first)
            instrumentation.runOnMainSync {
                assertNotSame(first, recreated)
                assertNull("Recreation must not replay the consumed callback", recreated.intent.data)
            }
        } finally {
            instrumentation.runOnMainSync {
                monitor.removeLifecycleCallback(observer)
                listOfNotNull(resumed.get(), lastActivity.get()).distinct()
                    .filterNot { it.isFinishing || it.isDestroyed }.forEach { it.finish() }
            }
        }
    }

    private fun awaitResumed(resumed: AtomicReference<MainActivity?>, previous: MainActivity? = null): MainActivity {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        while (SystemClock.elapsedRealtime() < deadline) {
            resumed.get()?.takeUnless { it === previous }?.let { return it }
            SystemClock.sleep(100)
        }
        throw AssertionError("MainActivity did not resume after the synthetic callback or recreation")
    }

    private fun withActivity(block: (MainActivity) -> Unit) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario -> scenario.onActivity { block(it) } }
    }

    private class RecordingLauncher : ActivityResultLauncher<Intent>() {
        var intent: Intent? = null
        override fun launch(input: Intent, options: ActivityOptionsCompat?) { intent = Intent(input) }
        override fun unregister() = Unit
        override val contract: ActivityResultContract<Intent, *>
            get() = AuthTabIntent.AuthenticateUserResultContract()
    }
}
