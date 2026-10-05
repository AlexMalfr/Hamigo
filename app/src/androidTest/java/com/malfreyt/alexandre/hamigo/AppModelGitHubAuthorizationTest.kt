package com.malfreyt.alexandre.hamigo

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.os.Build
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.platform.DailyReminder
import com.malfreyt.alexandre.hamigo.platform.GitHubAuthorizationClient
import com.malfreyt.alexandre.hamigo.platform.GitHubBrowserCommand
import com.malfreyt.alexandre.hamigo.platform.GitHubPkce
import com.malfreyt.alexandre.hamigo.platform.PendingGitHubAuthorization
import com.malfreyt.alexandre.hamigo.platform.ProgressSyncScheduler
import com.malfreyt.alexandre.hamigo.platform.SocialException
import kotlinx.coroutines.CompletableDeferred
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Real model and encrypted pending store; fictitious exchange, isolated prefs, no browser/network. */
@RunWith(AndroidJUnit4::class)
class AppModelGitHubAuthorizationTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val baseContext get() = instrumentation.targetContext
    private lateinit var isolated: IsolatedContext
    private val models = mutableListOf<ViewModelStore>()

    @Before fun isolatePreferences() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic")) {
            "These isolated authorization tests must run on an emulator, never the user’s phone."
        }
        isolated = IsolatedContext(baseContext)
        check(isolated.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit()
            .putBoolean("welcomed", true).putBoolean("autoSync", false)
            .putBoolean("reminderEnabled", false).putString("name", "Témoin OAuth").commit())
    }

    @After fun cleanUp() {
        onMain { models.forEach(ViewModelStore::clear) }
        if (::isolated.isInitialized) isolated.cleanup()
        // Initialization disables common emulator alarms/jobs; restore their actual preferences.
        DailyReminder.schedule(baseContext)
        ProgressSyncScheduler.schedule(baseContext)
    }

    @Test fun aPersistedAttemptConsumesAValidCallbackOnlyOnceAndClosesBeforeExchangeFinishes() {
        val fake = HeldAuthorizationClient()
        val model = createModel(fake)
        val active = onMain {
            model.startGitHubConnection()
            assertTrue(model.busy)
            assertTrue(model.githubBrowserCommands.value is GitHubBrowserCommand.OpenAuthorization)
            assertNotNull(model.authSession)
            model.authSession!!
        }
        val restored = pending().restore()
        assertNotNull(restored)
        assertEquals(active.state, restored!!.state)
        assertEquals(active.verifier, restored.verifier)
        assertEquals(active.expiresAtMillis, restored.expiresAtMillis)
        val persisted = isolated.getSharedPreferences("hamigo_pending_oauth", Context.MODE_PRIVATE)
        assertTrue(persisted.contains("ciphertext"))
        assertFalse(persisted.all.values.toString().contains(active.state))
        assertFalse(persisted.all.values.toString().contains(active.verifier))

        onMain {
            model.receiveGitHubAuthorization(callback(active))
            model.receiveGitHubAuthorization(callback(active))
        }
        await("A valid authorization callback did not reach the injected exchange.") { fake.calls.get() == 1 }
        onMain {
            assertTrue(model.busy)
            assertNull(model.authSession)
            assertEquals(GitHubBrowserCommand.Close, model.githubBrowserCommands.value)
        }
        assertSame(active, fake.lastSession.get())
        assertEquals("fake_authorization-code", fake.lastCode.get())
        assertNull(pending().restore())
        assertTrue(persisted.all.isEmpty())
        assertFalse(fake.release.isCompleted)

        fake.release.complete(Unit)
        await("The fixture failure did not complete authorization cleanup.") { !model.busy }
        onMain { assertEquals(HeldAuthorizationClient.FAILURE, model.message) }
        assertEquals(1, fake.calls.get())
    }

    @Test fun malformedAndUnrelatedCallbacksLeaveTheLegitimateAttemptPending() {
        val fake = HeldAuthorizationClient()
        val model = createModel(fake)
        val active = onMain { model.startGitHubConnection(); model.authSession!! }
        val urls = listOf(
            GitHubPkce.CALLBACK + "?state=" + "x".repeat(43) + "&code=wrong-session",
            GitHubPkce.CALLBACK + "?code=missing-state",
            GitHubPkce.CALLBACK + "?state=${active.state}&state=${active.state}&code=duplicate-state",
            GitHubPkce.CALLBACK + "?state=${active.state}&code=one&%63ode=two",
            "https://attacker.example/hamigo/oauth/?state=${active.state}&code=wrong-origin",
            callback(active) + "#fragment"
        )
        urls.forEach { url ->
            onMain {
                model.receiveGitHubAuthorization(url)
                assertTrue(model.busy)
                assertSame(active, model.authSession)
            }
            assertNotNull(pending().restore())
            assertEquals(0, fake.calls.get())
        }
        onMain { model.cancelTask() }
        await("Cancelling a pending attempt left its waiter or browser open.") { !model.busy && model.authSession == null }
        assertNull(pending().restore())
        onMain { assertEquals(GitHubBrowserCommand.Close, model.githubBrowserCommands.value) }
    }

    @Test fun browserLaunchFailureEndsOnlyThePreparedAttemptAndClearsItsPersistedVerifier() {
        val fake = HeldAuthorizationClient()
        val model = createModel(fake)
        onMain {
            model.startGitHubConnection()
            assertNotNull(model.authSession)
            model.githubBrowserFailed("Navigateur indisponible (fixture).")
        }
        await("A browser launch failure did not end the pending attempt.") { !model.busy && model.authSession == null }
        assertNull(pending().restore())
        assertEquals(0, fake.calls.get())
        onMain {
            assertEquals("Navigateur indisponible (fixture).", model.message)
            assertEquals(GitHubBrowserCommand.Close, model.githubBrowserCommands.value)
        }
    }

    @Test fun initializationRestoresTheWaiterBeforeTheCallbackWithoutReopeningTheBrowser() {
        val saved = GitHubPkce.start()
        pending().save(saved)
        val fake = HeldAuthorizationClient()
        val model = createModel(fake) { restored ->
            // This assertion runs in the same main-thread turn as initialize, before a callback.
            assertTrue(restored.busy)
            assertNotNull(restored.authSession)
            assertEquals(saved.state, restored.authSession!!.state)
            assertTrue(restored.githubTabOpen)
            assertNull(restored.githubBrowserCommands.value)
            assertEquals(0, fake.calls.get())
            restored.receiveGitHubAuthorization(callback(saved))
        }
        await("The process-restored waiter lost its first callback.") { fake.calls.get() == 1 }
        assertEquals(saved.verifier, fake.lastSession.get()!!.verifier)
        assertNull(pending().restore())
        onMain {
            assertNull(model.authSession)
            assertEquals(GitHubBrowserCommand.Close, model.githubBrowserCommands.value)
        }
        fake.release.complete(Unit)
        await("A restored attempt did not finish cleanup.") { !model.busy }
    }

    @Test fun cancellingARestoredAttemptClearsThePendingStoreAndRejectsItsLaterCallback() {
        val saved = GitHubPkce.start()
        pending().save(saved)
        val fake = HeldAuthorizationClient()
        val model = createModel(fake)
        onMain {
            assertTrue(model.busy)
            model.cancelTask()
        }
        await("The restored attempt remained busy after cancellation.") { !model.busy && model.authSession == null }
        assertNull(pending().restore())
        onMain {
            model.receiveGitHubAuthorization(callback(saved))
            assertFalse(model.busy)
            assertEquals(GitHubBrowserCommand.Close, model.githubBrowserCommands.value)
        }
        assertEquals(0, fake.calls.get())
    }

    @Test fun cancellingDuringExchangeKeepsTheConsumedSessionClearedAndNeverReconnects() {
        val fake = HeldAuthorizationClient()
        val model = createModel(fake)
        val active = onMain { model.startGitHubConnection(); model.authSession!! }
        onMain { model.receiveGitHubAuthorization(callback(active)) }
        await("The local held exchange was not entered.") { fake.calls.get() == 1 }
        onMain { model.cancelTask() }
        await("Cancelling an exchange left the model busy.") { !model.busy && model.authSession == null }
        fake.release.complete(Unit)
        onMain { model.receiveGitHubAuthorization(callback(active)) }
        assertNull(pending().restore())
        assertEquals(1, fake.calls.get())
        onMain {
            assertFalse(model.busy)
            assertEquals(GitHubBrowserCommand.Close, model.githubBrowserCommands.value)
            assertNull(model.sync.tokens.get())
        }
    }

    private fun createModel(fake: HeldAuthorizationClient, afterInitialize: (AppModel) -> Unit = {}): AppModel = onMain {
        val store = ViewModelStore().also(models::add)
        val model = ViewModelProvider(store, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = AppModel(fake) as T
        })[AppModel::class.java]
        model.initialize(isolated)
        afterInitialize(model)
        model
    }
    private fun pending() = PendingGitHubAuthorization(isolated)
    private fun callback(session: GitHubPkce.Session) = GitHubPkce.CALLBACK + "?state=${session.state}&code=fake_authorization-code"
    private fun await(reason: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 10_000L
        while (!onMain(condition)) {
            if (SystemClock.elapsedRealtime() >= deadline) throw AssertionError(reason)
            Thread.sleep(20L)
        }
    }
    private fun <T> onMain(action: () -> T): T {
        val result = AtomicReference<Result<T>>()
        instrumentation.runOnMainSync { result.set(runCatching(action)) }
        return result.get().getOrThrow()
    }

    private class HeldAuthorizationClient : GitHubAuthorizationClient {
        override val enabled = true
        val calls = AtomicInteger()
        val lastSession = AtomicReference<GitHubPkce.Session>()
        val lastCode = AtomicReference<String>()
        val release = CompletableDeferred<Unit>()
        override suspend fun exchange(session: GitHubPkce.Session, code: String): String {
            lastSession.set(session); lastCode.set(code); calls.incrementAndGet()
            release.await()
            // This deliberately stops before the model can call its real GitHubSync gateway.
            throw SocialException(FAILURE)
        }
        companion object { const val FAILURE = "Échange interrompu par le client de test local." }
    }
    private class IsolatedContext(base: Context) : ContextWrapper(base) {
        private val prefix = "app-model-oauth-test-${UUID.randomUUID()}"
        private val preferenceNames = mutableSetOf<String>()
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int): SharedPreferences {
            val unique = "$prefix-$name"
            synchronized(preferenceNames) { preferenceNames += unique }
            return baseContext.getSharedPreferences(unique, mode)
        }
        fun cleanup() {
            synchronized(preferenceNames) { preferenceNames.toList() }.forEach { name ->
                baseContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
                baseContext.deleteSharedPreferences(name)
            }
        }
    }
}
