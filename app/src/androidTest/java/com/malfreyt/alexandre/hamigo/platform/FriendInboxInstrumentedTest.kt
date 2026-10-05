package com.malfreyt.alexandre.hamigo.platform

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Local fake accounts and local HTTP only. This suite cannot send real GitHub comments. */
@RunWith(AndroidJUnit4::class)
class FriendInboxInstrumentedTest {
    private val clock = Instant.parse("2026-10-05T10:00:00Z")
    private val token = "inbox_test_token"
    private val me = GitHubIdentity("receiver", 101L)
    private val other = GitHubIdentity("sender", 202L)
    private val attacker = GitHubIdentity("attacker", 303L)
    private val mine = "aaaaa"
    private val theirs = "bbbbb"

    @Before fun requireEmulator() {
        check(Build.HARDWARE in listOf("ranchu", "goldfish") || Build.FINGERPRINT.contains("generic")) {
            "Run isolated inbox tests on an emulator, never the user's phone."
        }
    }

    @Test fun incomingVerifiesCommentAccountAgainstSocialOwnerAndIgnoresPayloadIdentity() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[mine] = mutableListOf(comment(1, uuid(1), other, theirs, mine))
        val result = FriendInbox(gateway) { clock }.readIncoming(mine, token)
        assertEquals(1, result.size)
        assertEquals(other, result.single().author)
        assertEquals("Profile sender", result.single().profile.name)
        assertEquals(1L, result.single().commentId)
        assertTrue(gateway.calls.any { it == "GET /gists/$theirs" })
    }

    @Test fun referencingSomeoneElsesSocialGistCannotSpoofItsOwner() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[mine] = mutableListOf(comment(1, uuid(1), attacker, theirs, mine))
        assertTrue(FriendInbox(gateway) { clock }.readIncoming(mine, token).isEmpty())
        assertEquals(0, gateway.posts)
    }

    @Test fun malformedUnrelatedExpiredFutureWrongDestinationAndSelfCommentsAreIgnored() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[mine] = mutableListOf(
            JSONObject().put("body", "Hello world"),
            comment(1, uuid(1), other, theirs, mine).put("body", "{}"),
            comment(2, uuid(2), other, theirs, mine, created = clock.minusSeconds(31L * 86400)),
            comment(3, uuid(3), other, theirs, mine, created = clock.plusSeconds(301)),
            comment(4, uuid(4), other, theirs, "ccccc"),
            comment(5, uuid(5), me, theirs, mine),
            comment(6, uuid(6), other, theirs, mine).put("body", "x".repeat(4097))
        )
        assertTrue(FriendInbox(gateway) { clock }.readIncoming(mine, token).isEmpty())
        assertFalse(gateway.calls.any { it == "GET /gists/$theirs" })
    }

    @Test fun renewedPayloadOnAnOldServerCommentCannotBypassExpiry() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[mine] = mutableListOf(comment(1, uuid(1), other, theirs, mine)
            .put("created_at", clock.minusSeconds(31L * 86400).toString()))
        assertTrue(FriendInbox(gateway) { clock }.readIncoming(mine, token).isEmpty())
    }

    @Test fun duplicateRequestsCollapseToOneNewestPerSender() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[mine] = mutableListOf(
            comment(1, uuid(1), other, theirs, mine, created = clock.minusSeconds(60)),
            comment(2, uuid(2), other, theirs, mine),
            comment(3, uuid(2), other, theirs, mine)
        )
        val result = FriendInbox(gateway) { clock }.readIncoming(mine, token)
        assertEquals(listOf(uuid(2)), result.map { it.id })
        assertEquals(1, gateway.calls.count { it == "GET /gists/$theirs" })
    }

    @Test fun handledRequestsAreFilteredBeforeRemoteProfileReads() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[mine] = mutableListOf(comment(1, uuid(1), other, theirs, mine))
        assertTrue(FriendInbox(gateway) { clock }.readIncoming(mine, token, setOf(uuid(1))).isEmpty())
        assertFalse(gateway.calls.any { it == "GET /gists/$theirs" })
    }

    @Test fun copiedPublicUuidCannotShadowAnotherSendersRequestOrDecision() = runBlocking {
        val gateway = FakeGitHub()
        gateway.gists["ccccc"] = gist("ccccc", attacker)
        gateway.comments[mine] = mutableListOf(comment(1, uuid(1), other, theirs, mine),
            comment(2, uuid(1), attacker, "ccccc", mine))
        val inbox = FriendInbox(gateway) { clock }
        assertEquals(2, inbox.readIncoming(mine, token).size)
        val result = inbox.readIncoming(mine, token, setOf("ccccc:${uuid(1)}"))
        assertEquals(listOf(other), result.map { it.author })
        assertEquals("$theirs:${uuid(1)}", result.single().decisionKey)
    }

    @Test fun ownGistMustBelongToAuthenticatedAccountBeforeCommentsAreRead() = runBlocking {
        val gateway = FakeGitHub()
        gateway.gists[mine] = gist(mine, other)
        expectFailure<IllegalArgumentException> { FriendInbox(gateway) { clock }.readIncoming(mine, token) }
        assertFalse(gateway.calls.any { "/comments" in it })
    }

    @Test fun gistWithoutNumericOwnerIdOrWithoutSocialFileIsNotAnInbox() = runBlocking {
        val missingId = FakeGitHub()
        missingId.gists[mine] = gist(mine, GitHubIdentity(me.login))
        expectFailure<IllegalArgumentException> { FriendInbox(missingId) { clock }.readIncoming(mine, token) }
        val backup = FakeGitHub()
        backup.gists[mine] = gist(mine, me).put("files", JSONObject().put(GitHubSync.BACKUP_FILE_NAME, JSONObject()))
        expectFailure<IllegalArgumentException> { FriendInbox(backup) { clock }.readIncoming(mine, token) }
        assertFalse(backup.calls.any { "/comments" in it })
    }

    @Test fun invalidSenderContentIsIgnoredAndDeletedSenderIsIgnored() = runBlocking {
        val gateway = FakeGitHub()
        gateway.gists[theirs] = gist(theirs, other).put("files", JSONObject().put(GitHubSync.FILE_NAME, JSONObject()))
        gateway.comments[mine] = mutableListOf(comment(1, uuid(1), other, theirs, mine))
        assertTrue(FriendInbox(gateway) { clock }.readIncoming(mine, token).isEmpty())
        gateway.gists.remove(theirs)
        assertTrue(FriendInbox(gateway) { clock }.readIncoming(mine, token).isEmpty())
    }

    @Test fun inboxReadsTheSecondPageAndNeverFollowsCommentUrls() = runBlocking {
        val gateway = FakeGitHub()
        val unrelated = (1..100).map { JSONObject().put("body", "Ordinary comment $it") }.toMutableList()
        unrelated += comment(101, uuid(1), other, theirs, mine)
            .put("url", "https://evil.example/token-destination")
        gateway.comments[mine] = unrelated
        val result = FriendInbox(gateway) { clock }.readIncoming(mine, token)
        assertEquals(1, result.size)
        assertTrue(gateway.calls.any { it.endsWith("page=2") })
        assertFalse(gateway.calls.any { "evil.example" in it })
    }

    @Test fun pendingReadAndVerificationAreBoundedTo100Accounts() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[mine] = (1..105).map { index ->
            val sender = index.toString(16).padStart(5, '0')
            val account = GitHubIdentity("account$index", index.toLong() + 1000)
            gateway.gists[sender] = gist(sender, account)
            comment(index.toLong(), uuid(index.toLong()), account, sender, mine)
        }.toMutableList()
        val result = FriendInbox(gateway) { clock }.readIncoming(mine, token)
        assertEquals(100, result.size)
        assertEquals(101, gateway.calls.count { it.startsWith("GET /gists/") && !it.contains("/comments") })
    }

    @Test fun requestIsSentWithVerifiedIdentityAndRetryIsIdempotent() = runBlocking {
        val gateway = FakeGitHub()
        val inbox = FriendInbox(gateway) { clock }
        val first = inbox.sendRequest(mine, theirs, uuid(1), token)
        val second = inbox.sendRequest(mine, theirs, uuid(1), token)
        assertEquals(first, second)
        assertEquals(1, gateway.posts)
        assertEquals(mine, first.senderGistId)
        assertEquals(theirs, first.recipientGistId)
        assertEquals(clock.toString(), first.createdAt)
    }

    @Test fun successfulPostHiddenByTimeoutIsRecoveredWithoutAnotherPost() = runBlocking {
        val gateway = FakeGitHub()
        gateway.failAfterNextPost = true
        val inbox = FriendInbox(gateway) { clock }
        expectFailure<GitHubNetworkException> { inbox.sendRequest(mine, theirs, uuid(1), token) }
        assertEquals(uuid(1), inbox.sendRequest(mine, theirs, uuid(1), token).id)
        assertEquals(1, gateway.posts)
    }

    @Test fun sameUuidWithDifferentSenderAndExpiredUuidDoNotPostAgain() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[theirs] = mutableListOf(comment(1, uuid(1), me, "ccccc", theirs))
        expectFailure<IllegalArgumentException> { FriendInbox(gateway) { clock }.sendRequest(mine, theirs, uuid(1), token) }
        gateway.comments[theirs] = mutableListOf(comment(1, uuid(1), me, mine, theirs, created = clock.minusSeconds(31L * 86400)))
        expectFailure<IllegalArgumentException> { FriendInbox(gateway) { clock }.sendRequest(mine, theirs, uuid(1), token) }
        assertEquals(0, gateway.posts)
    }

    @Test fun requestsToSelfIncludingAnotherOwnedGistAreRejected() = runBlocking {
        val gateway = FakeGitHub()
        val inbox = FriendInbox(gateway) { clock }
        expectFailure<IllegalArgumentException> { inbox.sendRequest(mine, mine, uuid(1), token) }
        gateway.gists[theirs] = gist(theirs, me)
        expectFailure<IllegalArgumentException> { inbox.sendRequest(mine, theirs, uuid(1), token) }
        assertEquals(0, gateway.posts)
    }

    @Test fun untrustedUrlsAndNonUuidIdsAreRejectedBeforeNetwork() = runBlocking {
        val gateway = FakeGitHub()
        val inbox = FriendInbox(gateway) { clock }
        expectFailure<IllegalArgumentException> { inbox.readIncoming("https://evil.example", token) }
        expectFailure<IllegalArgumentException> { inbox.sendRequest(mine, theirs, "short-id", token) }
        assertTrue(gateway.calls.isEmpty())
    }

    @Test fun moreThan1000CommentsFailsClosedWithoutPosting() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[theirs] = (1..1000).map { JSONObject().put("body", "ordinary $it") }.toMutableList()
        expectFailure<SocialException> { FriendInbox(gateway) { clock }.sendRequest(mine, theirs, uuid(1), token) }
        assertEquals(0, gateway.posts)
        assertEquals(10, gateway.calls.count { "/comments?" in it })
    }

    @Test fun acceptanceVerifiesOriginalAndIsIdempotent() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[mine] = mutableListOf(comment(1, uuid(1), other, theirs, mine))
        val inbox = FriendInbox(gateway) { clock }
        val request = inbox.readIncoming(mine, token).single()
        inbox.acknowledgeAccepted(request, mine, token)
        inbox.acknowledgeAccepted(request, mine, token)
        assertEquals(1, gateway.posts)
        assertTrue(gateway.calls.any { it == "GET /gists/$mine/comments/1" })
    }

    @Test fun changedOriginalAuthorCannotBeAcknowledged() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[mine] = mutableListOf(comment(1, uuid(1), other, theirs, mine))
        val inbox = FriendInbox(gateway) { clock }
        val request = inbox.readIncoming(mine, token).single()
        gateway.comments[mine]!![0].put("user", attacker.toJson())
        expectFailure<IllegalArgumentException> { inbox.acknowledgeAccepted(request, mine, token) }
        assertEquals(0, gateway.posts)
    }

    @Test fun outgoingAcceptanceRequiresCorrectOwnerUuidAndBothGists() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[theirs] = mutableListOf(
            comment(1, uuid(1), attacker, mine, theirs, "accepted"),
            comment(2, uuid(1), other, "ccccc", theirs, "accepted"),
            comment(3, uuid(2), other, mine, theirs, "accepted")
        )
        val inbox = FriendInbox(gateway) { clock }
        val outgoing = OutgoingFriendRequest(uuid(1), mine, theirs, clock.minusSeconds(10).toString())
        assertTrue(inbox.acceptedOutgoing(mine, listOf(outgoing), token).isEmpty())
        gateway.comments[theirs]!!.add(comment(4, uuid(1), other, mine, theirs, "accepted"))
        assertEquals(setOf(uuid(1)), inbox.acceptedOutgoing(mine, listOf(outgoing), token))
        assertEquals(0, gateway.posts)
    }

    @Test fun expiredAcceptanceCannotSatisfyAnOutgoingRequest() = runBlocking {
        val gateway = FakeGitHub()
        gateway.comments[theirs] = mutableListOf(comment(1, uuid(1), other, mine, theirs, "accepted", clock.minusSeconds(31L * 86400)))
        val outgoing = OutgoingFriendRequest(uuid(1), mine, theirs, clock.minusSeconds(10).toString())
        assertTrue(FriendInbox(gateway) { clock }.acceptedOutgoing(mine, listOf(outgoing), token).isEmpty())
    }

    @Test fun acceptanceFromRecipientClockFourMinutesBehindIsRecognized() = runBlocking {
        val gateway = FakeGitHub()
        // The destination's device clock is slow; GitHub's server date still records the real POST.
        gateway.comments[theirs] = mutableListOf(comment(1, uuid(1), other, mine, theirs, "accepted", clock.minusSeconds(240))
            .put("created_at", clock.toString()))
        val outgoing = OutgoingFriendRequest(uuid(1), mine, theirs, clock.toString())
        assertEquals(setOf(uuid(1)), FriendInbox(gateway) { clock }.acceptedOutgoing(mine, listOf(outgoing), token))
        assertEquals(0, gateway.posts)
    }

    @Test fun authorizationErrorsAndCancellationRemainVisibleToTheCaller() = runBlocking {
        val gateway = FakeGitHub()
        gateway.failure = SocialException("Access refused", 403)
        val error = expectFailure<SocialException> { FriendInbox(gateway) { clock }.readIncoming(mine, token) }
        assertEquals(403, error.httpStatus)
        gateway.failure = CancellationException("stop")
        expectFailure<CancellationException> { FriendInbox(gateway) { clock }.readIncoming(mine, token) }
        assertEquals(0, gateway.posts)
    }

    @Test fun malformedProtocolVersionOrExtraFieldsCannotEnterInbox() = runBlocking {
        val gateway = FakeGitHub()
        val extra = message(uuid(1), theirs, mine).put("senderLogin", "receiver")
        val wrongSchema = message(uuid(2), theirs, mine).put("schema", 2)
        gateway.comments[mine] = mutableListOf(
            comment(1, uuid(1), other, theirs, mine).put("body", encode(extra)),
            comment(2, uuid(2), other, theirs, mine).put("body", encode(wrongSchema))
        )
        assertTrue(FriendInbox(gateway) { clock }.readIncoming(mine, token).isEmpty())
    }

    @Test fun localHttpTransportUsesApiCommentsEndpointAndAuthorizationOnlyThere() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            server.enqueue(json(me.toJson()))
            server.enqueue(json(gist(mine, me)))
            server.enqueue(json(JSONArray().put(comment(1, uuid(1), other, theirs, mine))))
            server.enqueue(json(gist(theirs, other)))
            val gateway = GitHubHttpClient(apiOrigin = server.url("/").toString().trimEnd('/'))
            assertEquals(1, FriendInbox(gateway) { clock }.readIncoming(mine, token).size)
            val paths = mutableListOf<String>()
            repeat(4) {
                val request = server.takeRequest(5, TimeUnit.SECONDS) ?: error("Missing local request")
                paths += request.path!!
                assertEquals("Bearer $token", request.getHeader("Authorization"))
                assertEquals("GET", request.method)
            }
            assertEquals(listOf("/user", "/gists/$mine", "/gists/$mine/comments?per_page=100&page=1", "/gists/$theirs"), paths)
        } finally { server.shutdown() }
    }

    private fun uuid(index: Long) = UUID(0, index).toString()
    private fun uuid(index: Int) = uuid(index.toLong())
    private fun gist(id: String, owner: GitHubIdentity) = JSONObject().put("id", id).put("owner", owner.toJson())
        .put("files", JSONObject().put(GitHubSync.FILE_NAME, JSONObject().put("content",
            ShareProgress("Profile ${owner.login}", 12, 1, 2, updatedAt = clock.toString()).toJson())))

    private fun message(id: String, sender: String, recipient: String, type: String = "request", created: Instant = clock) =
        JSONObject().put("app", "hamigo-friends").put("schema", 1).put("type", type).put("id", id)
            .put("senderGist", sender).put("recipientGist", recipient).put("createdAt", created.toString())
    private fun encode(message: JSONObject) = "Hamigo • demande d’ajout réciproque\n\n```json\n$message\n```"
    private fun comment(commentId: Long, requestId: String, author: GitHubIdentity, sender: String, recipient: String,
                        type: String = "request", created: Instant = clock) = JSONObject().put("id", commentId)
        .put("user", author.toJson()).put("created_at", created.toString())
        .put("body", encode(message(requestId, sender, recipient, type, created)))

    private suspend inline fun <reified T : Throwable> expectFailure(block: () -> Unit): T {
        try { block() } catch (error: Throwable) {
            if (error is T) return error
            throw AssertionError("Expected ${T::class.java.simpleName}, got ${error.javaClass.simpleName}", error)
        }
        throw AssertionError("Expected ${T::class.java.simpleName}")
    }

    private fun json(value: Any) = MockResponse().setHeader("Content-Type", "application/json").setBody(value.toString())

    private inner class FakeGitHub : GitHubGateway {
        val gists = mutableMapOf(mine to gist(mine, me), theirs to gist(theirs, other))
        val comments = mutableMapOf<String, MutableList<JSONObject>>()
        val calls = mutableListOf<String>()
        var posts = 0
        var failAfterNextPost = false
        var failure: Throwable? = null
        override suspend fun api(method: String, path: String, token: String?, body: String?): String {
            require(token == this@FriendInboxInstrumentedTest.token)
            calls += "$method $path"
            failure?.let { throw it }
            if (path == "/user") return me.toJson().toString()
            val parts = path.substringBefore('?').split('/').filter(String::isNotEmpty)
            require(parts.size in 2..4 && parts[0] == "gists")
            val id = parts[1]
            if (parts.size == 2) return gists[id]?.toString() ?: throw SocialException("Not found", 404)
            require(parts[2] == "comments")
            val all = comments.getOrPut(id) { mutableListOf() }
            if (method == "GET") {
                if (parts.size == 4) return all.firstOrNull { it.getLong("id").toString() == parts[3] }?.toString()
                    ?: throw SocialException("Comment not found", 404)
                val page = path.substringAfterLast("page=").toInt()
                return JSONArray(all.drop((page - 1) * 100).take(100)).toString()
            }
            require(method == "POST")
            posts++
            val result = JSONObject().put("id", (10000 + posts).toLong()).put("user", me.toJson())
                .put("created_at", clock.toString()).put("body", JSONObject(body!!).getString("body"))
            all += result
            if (failAfterNextPost) {
                failAfterNextPost = false
                throw GitHubNetworkException("Response lost", IOException("timeout"))
            }
            return result.toString()
        }
        override suspend fun rawBackup(rawUrl: String, owner: String, gist: String, fileName: String): String =
            error("Inbox must never read backup raw URLs")
    }
}
