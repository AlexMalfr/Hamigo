package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

/** Tests process-independent recovery and rejection of expired/tampered authorization state. */
class PendingGitHubAuthorizationTest {
    private val base = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefix = "pkce-state-test-${UUID.randomUUID()}"
    private val context = object : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$prefix-$name", mode)
    }
    private val now = System.currentTimeMillis()
    private val session get() = GitHubPkce.Session("s".repeat(43), "v".repeat(43), now + 600_000L)

    @After fun cleanup() {
        base.deleteSharedPreferences("$prefix-hamigo_pending_oauth")
        base.deleteSharedPreferences("$prefix-hamigo_secure")
    }

    @Test fun aFreshStoreRestoresTheSameTransactionWithoutPlaintextPreferences() {
        PendingGitHubAuthorization(context).save(session)
        val recovered = PendingGitHubAuthorization(context).restore(now)!!
        assertEquals(session.state, recovered.state)
        assertEquals(session.verifier, recovered.verifier)
        assertEquals(session.expiresAtMillis, recovered.expiresAtMillis)
        val values = context.getSharedPreferences("hamigo_pending_oauth", Context.MODE_PRIVATE).all.toString()
        assertFalse(values.contains(session.verifier)); assertFalse(values.contains(session.state))
    }

    @Test fun expirationClearsUnusableStateInsteadOfReusingIt() {
        val store = PendingGitHubAuthorization(context)
        store.save(session)
        assertNull(store.restore(session.expiresAtMillis))
        assertTrue(context.getSharedPreferences("hamigo_pending_oauth", Context.MODE_PRIVATE).all.isEmpty())
    }

    @Test fun modifiedCiphertextCannotCreateAnAuthorization() {
        val store = PendingGitHubAuthorization(context)
        store.save(session)
        val prefs = context.getSharedPreferences("hamigo_pending_oauth", Context.MODE_PRIVATE)
        val original = prefs.getString("ciphertext", "")!!
        prefs.edit().putString("ciphertext", (if(original[0] == 'A') "B" else "A") + original.drop(1)).commit()
        assertNull(store.restore(now)); assertTrue(prefs.all.isEmpty())
    }

    @Test fun clearingThePendingTransactionPreservesTheConnectedUserToken() {
        val tokens = SecureTokenStore(context)
        val fixtureToken = "synthetic-user-token-never-sent"
        tokens.store(fixtureToken)
        val store = PendingGitHubAuthorization(context)
        store.save(session); store.clear()
        assertNull(store.restore(now))
        assertEquals(fixtureToken, tokens.get())
    }

    @Test fun replacementKeepsOnlyTheMostRecentTransaction() {
        val store = PendingGitHubAuthorization(context)
        store.save(session)
        val replacement = GitHubPkce.Session("n".repeat(43), "w".repeat(43), now + 500_000L)
        store.save(replacement)
        assertEquals(replacement.state, store.restore(now)!!.state)
    }
}
