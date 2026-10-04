package com.malfreyt.alexandre.hamigo.platform

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.malfreyt.alexandre.hamigo.Progress
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.net.URLDecoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Local HTTP servers and fake credentials only; never sends a request to GitHub. */
@RunWith(AndroidJUnit4::class)
class AuthFlowInstrumentedTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val clientId = "test-client-id"
    private fun session(code: String = "device +/&=code") = DeviceOAuth.Session(code, "ABCD-EFGH", "https://github.com/login/device", 120, 5, 120_000L)

    @Before fun requireEmulator() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic")) {
            "These isolated account tests must run on an emulator, never the user's phone."
        }
    }

    @Test fun http400PollingResponsesAreDecodedAndSlowDownChangesTheInterval() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(jsonResponse(400, JSONObject().put("error", "authorization_pending")))
            server.enqueue(jsonResponse(400, JSONObject().put("error", "slow_down").put("interval", 10)))
            server.enqueue(jsonResponse(200, JSONObject().put("access_token", "test_only_token").put("scope", "gist")))
            val transport = GitHubHttpClient(apiOrigin = server.url("/").toString().trimEnd('/'), oauthOrigin = server.url("/").toString().trimEnd('/'))
            var elapsed = 0L
            val waits = mutableListOf<Long>()
            val token = DeviceOAuth.awaitTokenWith(clientId, session(), transport::oauth,
                wait = { waits += it; elapsed += it }, elapsed = { elapsed })
            assertEquals("test_only_token", token)
            assertEquals(listOf(5000L, 5000L, 10_000L), waits)
            repeat(3) {
                val request = takeRequest(server)
                assertEquals("POST", request.method)
                assertEquals("/login/oauth/access_token", request.path)
                assertEquals("application/json", request.getHeader("Accept"))
                assertNull(request.getHeader("Authorization"))
                assertTrue(request.getHeader("Content-Type")!!.startsWith("application/x-www-form-urlencoded"))
                assertEquals(mapOf("client_id" to clientId, "device_code" to "device +/&=code",
                    "grant_type" to "urn:ietf:params:oauth:grant-type:device_code"), decodeForm(request.body.readUtf8()))
            }
        } finally { server.shutdown() }
    }

    @Test fun aDisconnectedSocketCanRecoverWithoutDiscardingTheAuthorizedDeviceSession() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START))
            server.enqueue(jsonResponse(200, JSONObject().put("access_token", "recovered_test_token").put("scope", "gist")))
            val http = OkHttpClient.Builder().retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false)
                .connectTimeout(2, TimeUnit.SECONDS).readTimeout(2, TimeUnit.SECONDS).callTimeout(3, TimeUnit.SECONDS).build()
            val transport = GitHubHttpClient(http, server.url("/").toString().trimEnd('/'), server.url("/").toString().trimEnd('/'))
            val status = mutableListOf<String?>()
            var elapsed = 0L
            val token = DeviceOAuth.awaitTokenWith(clientId, session(), transport::oauth,
                wait = { elapsed += it }, elapsed = { elapsed }, onNetworkWait = { status += it })
            assertEquals("recovered_test_token", token)
            assertTrue(status.any { it != null })
            assertNull(status.last())
            assertEquals(2, server.requestCount)
        } finally { server.shutdown() }
    }

    @Test fun repeatedTransientErrorsBackOffAndThenResumeNormalPolling() = runBlocking {
        var calls = 0
        var elapsed = 0L
        val waits = mutableListOf<Long>()
        val statuses = mutableListOf<String?>()
        val token = DeviceOAuth.awaitTokenWith(clientId, session(), exchange = { _, _ ->
            when (calls++) {
                0 -> throw GitHubNetworkException("offline", IOException("simulated local outage"))
                1 -> throw SocialException("temporarily unavailable", 503)
                2 -> throw SocialException("rate limited", 429)
                3 -> JSONObject().put("error", "authorization_pending")
                else -> JSONObject().put("access_token", "test_token_after_retry").put("scope", "gist read:user")
            }
        }, wait = { waits += it; elapsed += it }, elapsed = { elapsed }, onNetworkWait = { statuses += it })
        assertEquals("test_token_after_retry", token)
        assertEquals(listOf(5000L, 5000L, 10_000L, 15_000L, 5000L), waits)
        assertEquals(3, statuses.count { it != null })
        assertNull(statuses.last())
    }

    @Test fun cancellingPollingStopsBeforeTheNextTokenExchange() = runBlocking {
        val enteredWait = CompletableDeferred<Unit>()
        val calls = AtomicInteger()
        val polling = launch {
            DeviceOAuth.awaitTokenWith(clientId, session(), exchange = { _, _ -> calls.incrementAndGet(); JSONObject() },
                wait = { enteredWait.complete(Unit); awaitCancellation() }, elapsed = { 0L })
        }
        withTimeout(2000) { enteredWait.await() }
        polling.cancelAndJoin()
        assertTrue(polling.isCancelled)
        assertEquals(0, calls.get())
    }

    @Test fun cancellingAnOutstandingHttpRequestCancelsTheSocketCall() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            val transport = GitHubHttpClient(apiOrigin = server.url("/").toString().trimEnd('/'))
            val call = launch { transport.api("GET", "/user", "fake_token_not_a_secret") }
            withContext(Dispatchers.IO) { assertEquals("/user", takeRequest(server).path) }
            withTimeout(2000) { call.cancelAndJoin() }
            assertTrue(call.isCancelled)
        } finally { server.shutdown() }
    }

    @Test fun cancellingAfterHeadersArriveAlsoInterruptsAStalledResponseBody() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(jsonResponse(200, JSONObject().put("login", "delayed-test-account")).setBodyDelay(3, TimeUnit.SECONDS))
            val headersArrived = CompletableDeferred<Unit>()
            val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
                .readTimeout(10, TimeUnit.SECONDS).callTimeout(10, TimeUnit.SECONDS)
                .eventListener(object : EventListener() {
                    override fun responseHeadersEnd(call: Call, response: Response) { headersArrived.complete(Unit) }
                }).build()
            val transport = GitHubHttpClient(client, apiOrigin = server.url("/").toString().trimEnd('/'))
            val reading = launch { transport.api("GET", "/user", "fake_token_not_a_secret") }
            withTimeout(2000) { headersArrived.await() }
            // Let onResponse enter the body read, so this is distinct from cancellation before headers.
            delay(100)
            withTimeout(2000) { reading.cancelAndJoin() }
            assertTrue(reading.isCancelled)
        } finally { server.shutdown() }
    }

    @Test fun patchRequestsPreserveTheirJsonBodyAndUseBearerAuthorizationOnlyForTheApi() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(jsonResponse(200, JSONObject().put("id", "0123456789abcdef")))
            val transport = GitHubHttpClient(apiOrigin = server.url("/").toString().trimEnd('/'))
            val body = JSONObject().put("description", "Révisions · Hamigo").put("files", JSONObject()
                .put("hamigo-backup.json", JSONObject().put("content", "{\"name\":\"Alex + & =\"}")))
            transport.api("PATCH", "/gists/0123456789abcdef", "fake_token_not_a_secret", body.toString())
            val request = takeRequest(server)
            assertEquals("PATCH", request.method)
            assertEquals("/gists/0123456789abcdef", request.path)
            assertEquals("Bearer fake_token_not_a_secret", request.getHeader("Authorization"))
            assertEquals("2026-03-10", request.getHeader("X-GitHub-Api-Version"))
            assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
            val sent = JSONObject(request.body.readUtf8())
            assertEquals(body.getString("description"), sent.getString("description"))
            assertEquals("{\"name\":\"Alex + & =\"}", sent.getJSONObject("files").getJSONObject("hamigo-backup.json").getString("content"))
        } finally { server.shutdown() }
    }

    @Test fun apiRedirectsAreRefusedBeforeAnyCredentialCanReachAnotherOrigin() = runBlocking {
        val origin = MockWebServer()
        val destination = MockWebServer()
        origin.start(); destination.start()
        try {
            destination.enqueue(jsonResponse(200, JSONObject().put("login", "should-not-be-requested")))
            origin.enqueue(MockResponse().setResponseCode(302).setHeader("Location", destination.url("/stolen")))
            val transport = GitHubHttpClient(apiOrigin = origin.url("/").toString().trimEnd('/'))
            try {
                transport.api("GET", "/user", "fake_token_not_a_secret")
                fail("A token-authenticated redirect must fail.")
            } catch (failure: SocialException) { assertEquals(302, failure.httpStatus) }
            assertEquals("Bearer fake_token_not_a_secret", takeRequest(origin).getHeader("Authorization"))
            assertNull(destination.takeRequest(300, TimeUnit.MILLISECONDS))
            assertEquals(0, destination.requestCount)
        } finally { origin.shutdown(); destination.shutdown() }
    }

    @Test fun missingGistPermissionIsRejectedEvenWhenGitHubSuppliesAnAccessToken() = runBlocking {
        try {
            DeviceOAuth.awaitTokenWith(clientId, session(), exchange = { _, _ -> JSONObject().put("access_token", "fake_token").put("scope", "read:user") },
                wait = {}, elapsed = { 0L })
            fail("Scope gist must be required.")
        } catch (failure: SocialException) { assertTrue(failure.message!!.contains("Gist")) }
    }

    @Test fun anAccountBackupRestoresAnotherInstallationAndPublishesOnlyTheSocialSummary() = runBlocking {
        withIsolatedPreferences {
            val fake = InMemoryGitHub()
            val first = Progress(context)
            first.name = "Alex test"
            first.setDailyGoal(45)
            first.prefs.edit().putString("friends", "[{\"private_local_friend\":true}]").commit()
            first.answer("question-first", true)
            first.complete("lesson-first")
            val firstSync = GitHubSync(context, fake)
            assertEquals("test-account", firstSync.connect("fake_token_for_local_test").login)
            val published = firstSync.synchronize(first)
            assertEquals(9, published.social.progress.xp)
            assertEquals(2, fake.gists.size)
            val backupGist = fake.gists.values.single { it.getJSONObject("files").has(GitHubSync.BACKUP_FILE_NAME) }
            val socialGist = fake.gists.values.single { it.getJSONObject("files").has(GitHubSync.FILE_NAME) }
            assertFalse(backupGist.getBoolean("public")); assertFalse(socialGist.getBoolean("public"))
            val backup = JSONObject(backupGist.getJSONObject("files").getJSONObject(GitHubSync.BACKUP_FILE_NAME).getString("content"))
            assertEquals(45, backup.getJSONObject("preferences").getInt("dailyGoal"))
            assertTrue(backup.getJSONObject("progress").getJSONObject("reviews").has("question-first"))
            assertFalse(backup.toString().contains("fake_token_for_local_test"))
            assertFalse(backup.toString().contains("private_local_friend"))
            val summary = JSONObject(socialGist.getJSONObject("files").getJSONObject(GitHubSync.FILE_NAME).getString("content"))
            assertEquals(9, summary.getInt("xp"))
            for (privateField in listOf("progress", "reviews", "preferences", "syncEvents", "friends", "token")) assertFalse(summary.has(privateField))

            // Simulate an independent installation with its own offline work before its first sign-in.
            context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit().clear().putBoolean("autoSync", false).commit()
            context.getSharedPreferences("hamigo_social", Context.MODE_PRIVATE).edit().clear().commit()
            val second = Progress(context)
            second.answer("question-second", true)
            second.complete("lesson-second")
            val secondSync = GitHubSync(context, fake)
            secondSync.connect("fake_token_for_local_test")
            val restored = secondSync.synchronize(second)
            assertTrue(restored.restored)
            assertEquals(18, second.xp)
            assertEquals(2, second.totalAnswers)
            assertEquals(setOf("lesson-first", "lesson-second"), second.completed)
            assertEquals(setOf("question-first", "question-second"), second.reviews.keys)
            assertEquals("Alex test", second.name)
            assertEquals(45, second.dailyGoal)
            assertEquals(published.social.id, restored.social.id)
            assertEquals(18, secondSync.read(restored.social.id).xp)
            assertEquals(3, fake.gists.getValue(backupGist.getString("id")).getJSONObject("files").length())
            secondSync.synchronize(second)
            assertEquals(18, second.xp)
            assertEquals(2, second.totalAnswers)
            assertEquals(2, fake.gists.size)
        }
    }

    private suspend fun withIsolatedPreferences(block: suspend () -> Unit) {
        val names = listOf("hamigo", "hamigo_social", "hamigo_secure")
        val snapshots = names.associateWith { name ->
            context.getSharedPreferences(name, Context.MODE_PRIVATE).all.mapValues { (_, value) -> if (value is Set<*>) value.toSet() else value }
        }
        ProgressSyncScheduler.cancel(context)
        try {
            names.forEach { context.getSharedPreferences(it, Context.MODE_PRIVATE).edit().clear().commit() }
            context.getSharedPreferences("hamigo", Context.MODE_PRIVATE).edit().putBoolean("autoSync", false).commit()
            block()
        } finally {
            // Preserve an existing Keystore key, so an earlier encrypted credential remains decryptable.
            ProgressSyncScheduler.cancel(context)
            snapshots.forEach { (name, values) ->
                val edit = context.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear()
                values.forEach { (key, value) -> restoreValue(edit, key, value) }
                check(edit.commit())
            }
            DailyReminder.schedule(context)
            ProgressSyncScheduler.schedule(context)
        }
    }

    private fun restoreValue(edit: SharedPreferences.Editor, key: String, value: Any?) {
        when (value) {
            is String -> edit.putString(key, value)
            is Int -> edit.putInt(key, value)
            is Long -> edit.putLong(key, value)
            is Boolean -> edit.putBoolean(key, value)
            is Float -> edit.putFloat(key, value)
            is Set<*> -> edit.putStringSet(key, value.filterIsInstance<String>().toSet())
        }
    }
    private fun jsonResponse(status: Int, body: JSONObject) = MockResponse().setResponseCode(status).setHeader("Content-Type", "application/json").setBody(body.toString())
    private fun takeRequest(server: MockWebServer): RecordedRequest = server.takeRequest(3, TimeUnit.SECONDS) ?: error("No local HTTP request received.")
    private fun decodeForm(body: String): Map<String, String> = body.split('&').associate { part ->
        val pair = part.split('=', limit = 2)
        URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
    }

    private class InMemoryGitHub : GitHubGateway {
        val gists = linkedMapOf<String, JSONObject>()
        private var serial = 0
        override suspend fun api(method: String, path: String, token: String?, body: String?): String {
            check(token == "fake_token_for_local_test")
            if (path == "/user") return JSONObject().put("login", "test-account").toString()
            if (method == "GET" && path.startsWith("/gists?")) return JSONArray(gists.values.map { JSONObject(it.toString()) }).toString()
            if (method == "GET") return gists[path.substringAfterLast('/')]?.toString() ?: throw SocialException("not found", 404)
            val payload = JSONObject(body!!)
            if (method == "POST" && path == "/gists") {
                check(!payload.getBoolean("public"))
                val id = "0123456789abcdef" + (++serial).toString().padStart(16, '0')
                val gist = JSONObject(payload.toString()).put("id", id).put("owner", JSONObject().put("login", "test-account"))
                    .put("created_at", "2026-10-04T00:00:00Z").put("html_url", "https://gist.github.com/test-account/$id")
                gists[id] = gist
                return gist.toString()
            }
            check(method == "PATCH")
            val existing = gists.getValue(path.substringAfterLast('/'))
            payload.getJSONObject("files").let { files -> files.keys().forEach { name -> existing.getJSONObject("files").put(name, files.getJSONObject(name)) } }
            existing.put("description", payload.getString("description"))
            return existing.toString()
        }
        override suspend fun rawBackup(rawUrl: String, owner: String, gist: String, fileName: String): String = error("These local fixtures are never truncated.")
    }
}
