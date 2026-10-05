package com.malfreyt.alexandre.hamigo.platform

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.net.URLDecoder
import java.util.concurrent.TimeUnit

/** In-memory JSON and local HTTP only: never opens a browser or uses a real account. */
@RunWith(AndroidJUnit4::class)
class GitHubPkceInstrumentedTest {
    private val clientId = "test-client-id"
    private val secret = "fake-app-secret+&/="
    private val code = "fake_authorization-code"
    private fun success() = JSONObject().put("access_token", "fake_test_token").put("token_type", "bearer").put("scope", "gist")

    @Before fun requireEmulator() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic")) {
            "These isolated OAuth tests must run on an emulator, never the user’s phone."
        }
    }

    @Test fun tokenExchangePostsTheVerifierAndRegisteredCallbackOnlyToTheOAuthEndpoint() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(json(200, success()))
            val transport = GitHubHttpClient(oauthOrigin = server.url("/").toString().trimEnd('/'))
            val session = GitHubPkce.start()
            assertEquals("fake_test_token", GitHubPkce.exchange(clientId, secret, session, code, transport::oauth))
            val request = server.takeRequest(3, TimeUnit.SECONDS) ?: throw AssertionError("Missing local token exchange.")
            assertEquals("POST", request.method)
            assertEquals("/login/oauth/access_token", request.path)
            assertEquals("application/json", request.getHeader("Accept"))
            assertNull(request.getHeader("Authorization"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
            assertEquals(mapOf("client_id" to clientId, "client_secret" to secret, "code" to code,
                "redirect_uri" to GitHubPkce.CALLBACK, "code_verifier" to session.verifier), form(request.body.readUtf8()))
            assertEquals(1, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun http400OAuthErrorsAreDecodedWithoutEchoingTheResponse() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(json(400, JSONObject().put("error", "bad_verification_code").put("error_description", "private-code-$code-$secret")))
            val transport = GitHubHttpClient(oauthOrigin = server.url("/").toString().trimEnd('/'))
            val failure = expectFailure { GitHubPkce.exchange(clientId, secret, GitHubPkce.start(), code, transport::oauth) }
            assertTrue(failure.message!!.contains("expiré"))
            assertFalse(failure.message!!.contains(code))
            assertFalse(failure.message!!.contains(secret))
        } finally { server.shutdown() }
    }

    @Test fun missingOrMalformedTokenMetadataIsRejectedBeforeTheTokenCanBeSaved() = runBlocking {
        val replies = listOf(
            JSONObject().put("access_token", "fake_test_token").put("scope", "gist"),
            success().put("token_type", "mac"), success().put("token_type", 123),
            success().put("access_token", 123), success().put("access_token", ""),
            success().put("access_token", "fake\r\ntoken"), success().put("access_token", "x".repeat(4097)),
            success().put("scope", 123), success().put("scope", "read:user"), success().put("scope", "gist:write"),
            success().put("error", "access_denied"), success().put("scope", "gist\nread:user")
        )
        for (reply in replies) expectFailure {
            GitHubPkce.exchange(clientId, secret, GitHubPkce.start(), code) { _, _ -> reply }
        }
    }

    @Test fun gistMustBeAnExactScopeWhileBearerTypeIsCaseInsensitive() = runBlocking {
        val reply = success().put("token_type", "Bearer").put("scope", "read:user,gist user:email")
        assertEquals("fake_test_token", GitHubPkce.exchange(clientId, secret, GitHubPkce.start(), code) { _, _ -> reply })
    }

    @Test fun invalidInputsAndExpiredSessionsNeverSendCredentials() = runBlocking {
        var requests = 0
        val post: suspend (String, String) -> JSONObject = { _, _ -> requests++; success() }
        expectFailure { GitHubPkce.exchange(clientId, "", GitHubPkce.start(), code, post) }
        expectFailure { GitHubPkce.exchange(clientId, "fake\nsecret", GitHubPkce.start(), code, post) }
        expectFailure { GitHubPkce.exchange(clientId, secret, GitHubPkce.start(), "code with spaces", post) }
        expectFailure { GitHubPkce.exchange("bad&client", secret, GitHubPkce.start(), code, post) }
        expectFailure { GitHubPkce.exchange(clientId, secret, GitHubPkce.start(1L), code, post) }
        assertEquals(0, requests)
    }

    @Test fun cancellationPropagatesWithoutBeingReportedAsAnOAuthFailure() = runBlocking {
        try {
            GitHubPkce.exchange(clientId, secret, GitHubPkce.start(), code) { _, _ -> throw CancellationException("local test cancellation") }
            fail("A cancelled exchange must stay cancelled.")
        } catch (_: CancellationException) { /* Expected; no token was returned. */ }
    }

    @Test fun unknownErrorsDoNotEchoTheirNameOrDescription() = runBlocking {
        val failure = expectFailure {
            GitHubPkce.exchange(clientId, secret, GitHubPkce.start(), code) { _, _ ->
                JSONObject().put("error", "secret-token-in-error").put("error_description", secret)
            }
        }
        assertFalse(failure.message!!.contains("secret-token-in-error"))
        assertFalse(failure.message!!.contains(secret))
    }

    private fun json(status: Int, body: JSONObject) = MockResponse().setResponseCode(status)
        .setHeader("Content-Type", "application/json").setBody(body.toString())
    private fun form(raw: String): Map<String, String> = raw.split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
    }
    private suspend fun expectFailure(block: suspend () -> Unit): SocialException {
        try { block() } catch (failure: SocialException) { return failure }
        throw AssertionError("Invalid OAuth response was accepted.")
    }
}
