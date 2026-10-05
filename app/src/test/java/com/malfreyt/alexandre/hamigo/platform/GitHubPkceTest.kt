package com.malfreyt.alexandre.hamigo.platform

import org.junit.Assert.*
import org.junit.Test
import java.net.URI
import java.net.URLDecoder

class GitHubPkceTest {
    private val state = "test_state_0123456789abcdefghijklmnopqrstuv"
    private val verifier = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
    private val session get() = GitHubPkce.Session(state, verifier, 601_000L)
    private fun callback(query: String = "state=$state&code=valid-code") = GitHubPkce.CALLBACK + "?" + query

    @Test fun matchesThePublishedRfc7636S256Vector() {
        assertEquals("E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", GitHubPkce.challenge(verifier))
    }

    @Test fun newSessionsUseIndependent256BitUrlSafeNoncesAndAClockBoundedExpiry() {
        val sessions = List(64) { GitHubPkce.start(1_000L) }
        val nonces = sessions.flatMap { listOf(it.state, it.verifier) }
        assertEquals(128, nonces.toSet().size)
        nonces.forEach { assertTrue(Regex("[A-Za-z0-9_-]{43}").matches(it)) }
        sessions.forEach { assertEquals(601_000L, it.expiresAtMillis) }
        expectFailure { GitHubPkce.start(-1) }
        expectFailure { GitHubPkce.start(Long.MAX_VALUE) }
    }

    @Test fun authorizeUsesOnlyTheRegisteredCallbackAndTheS256Challenge() {
        val uri = URI(GitHubPkce.authorizationUrl("test-client-id", session))
        assertEquals("https", uri.scheme)
        assertEquals("github.com", uri.host)
        assertEquals("/login/oauth/authorize", uri.path)
        assertEquals(mapOf(
            "client_id" to "test-client-id", "redirect_uri" to GitHubPkce.CALLBACK,
            "scope" to "gist", "state" to state,
            "code_challenge" to "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM", "code_challenge_method" to "S256"
        ), query(uri.rawQuery))
        assertFalse(uri.toString().contains(verifier))
        assertFalse(query(uri.rawQuery).containsKey("client_secret"))
        expectFailure { GitHubPkce.authorizationUrl("client&redirect_uri=attacker", session) }
    }

    @Test fun callbackRequiresTheExactRegisteredSchemeAuthorityAndPath() {
        assertTrue(GitHubPkce.isCallback(GitHubPkce.CALLBACK))
        assertTrue(GitHubPkce.isCallback(callback()))
        val impostors = listOf(
            "http://alexmalfr.github.io/hamigo/oauth/", "HTTPS://alexmalfr.github.io/hamigo/oauth/",
            "https://ALEXMALFR.github.io/hamigo/oauth/", "https://alexmalfr.github.io:443/hamigo/oauth/",
            "https://user@alexmalfr.github.io/hamigo/oauth/", "https://alexmalfr.github.io.attacker.example/hamigo/oauth/",
            "https://alexmalfr.github.io/hamigo/oauth", "https://alexmalfr.github.io/hamigo/oauth/extra",
            "https://alexmalfr.github.io/hamigo/%6fAuth/", "https://alexmalfr.github.io/hamigo/%6fauth/",
            "https://alexmalfr.github.io/hamigo/other/../oauth/", "https://alexmalfr.github.io//hamigo/oauth/",
            GitHubPkce.CALLBACK + "#state=$state&code=valid-code", GitHubPkce.CALLBACK + "#",
            "https://alexmalfr.github.io\\@attacker.example/hamigo/oauth/", " " + GitHubPkce.CALLBACK
        )
        impostors.forEach { assertFalse("Accepted unexpected callback origin/path", GitHubPkce.isCallback(it)) }
    }

    @Test fun validEncodedCallbackMatchesTheSessionAndDecodesTheAuthorizationCode() {
        val url = callback("state=$state&code=valid%2Dcode&extra=ignored")
        assertTrue(GitHubPkce.matchesState(url, session))
        assertEquals("valid-code", GitHubPkce.codeFromCallback(url, session, 1_000L))
        // A restored session is equivalent without exposing its contents in diagnostics.
        val restored = GitHubPkce.Session(session.state, session.verifier, session.expiresAtMillis)
        assertEquals("valid-code", GitHubPkce.codeFromCallback(url, restored, 600_999L))
        assertFalse(restored.toString().contains(state))
        assertFalse(restored.toString().contains(verifier))
    }

    @Test fun duplicateAndMalformedParametersCannotTerminateAnotherActiveSession() {
        val invalid = listOf(
            "state=$state&state=$state&code=valid-code",
            "state=$state&%73tate=$state&code=valid-code",
            "state=$state&code=one&code=two",
            "state=$state&code=one&%63ode=two",
            "state=$state&code=one&extra=a&extra=b",
            "state=$state&code=one&=bad", "state=$state&code=one&unassigned",
            "state=$state&code=one&error_description=%C0%AF",
            "state=$state&code=one&error_description=%FF",
            "state=$state&code=one&error_description=%00",
            "state=$state&code=one&error_description=%", "state=$state&code=one&"
        )
        invalid.forEach { value ->
            assertFalse("Ambiguous callback matched an active session", GitHubPkce.matchesState(callback(value), session))
            expectFailure { GitHubPkce.codeFromCallback(callback(value), session, 1_000L) }
        }
        assertFalse(GitHubPkce.matchesState(callback("state=${state.dropLast(1)}x&code=one"), session))
        assertFalse(GitHubPkce.matchesState(callback("code=one"), session))
    }

    @Test fun codeAndErrorAreMutuallyExclusiveAndNeitherMayBeMissing() {
        listOf("state=$state", "state=$state&code=one&error=access_denied", "state=$state&code=",
            "state=$state&code=code+with+spaces", "state=$state&code=" + "x".repeat(513)).forEach {
            expectFailure { GitHubPkce.codeFromCallback(callback(it), session, 1_000L) }
        }
    }

    @Test fun expiredOrImpossibleSessionsCannotUseEvenAValidCallback() {
        expectFailure { GitHubPkce.codeFromCallback(callback(), session, 601_000L) }
        expectFailure { GitHubPkce.codeFromCallback(callback(), session, 601_001L) }
        expectFailure { GitHubPkce.codeFromCallback(callback(), session, 999L) }
        expectFailure { GitHubPkce.codeFromCallback(callback(), session, -1L) }
    }

    @Test fun providerErrorsAreUsefulButNeverEchoServerSuppliedSecrets() {
        val url = callback("state=$state&error=access_denied&error_description=secret-user-token")
        assertTrue(GitHubPkce.matchesState(url, session))
        val failure = expectFailure { GitHubPkce.codeFromCallback(url, session, 1_000L) }
        assertTrue(failure.message!!.contains("annulée"))
        assertFalse(failure.message!!.contains("secret-user-token"))
        val unknown = expectFailure { GitHubPkce.codeFromCallback(callback("state=$state&error=secret-user-token"), session, 1_000L) }
        assertFalse(unknown.message!!.contains("secret-user-token"))
    }

    @Test fun protocolBoundsRejectOverlongUrlsAndQueries() {
        assertFalse(GitHubPkce.isCallback(callback("extra=" + "x".repeat(8192))))
        expectFailure { GitHubPkce.codeFromCallback(callback("state=$state&code=one&extra=" + "x".repeat(4097)), session, 1_000L) }
        val many = (0..16).joinToString("&") { "extra$it=value" }
        assertFalse(GitHubPkce.matchesState(callback("state=$state&code=one&$many"), session))
    }

    private fun query(raw: String): Map<String, String> = raw.split('&').associate {
        val pair = it.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair[1], "UTF-8")
    }
    private fun expectFailure(block: () -> Unit): Exception {
        try { block() } catch (failure: SocialException) { return failure }
        catch (failure: IllegalArgumentException) { return failure }
        throw AssertionError("Invalid OAuth input was accepted.")
    }
}
