package com.malfreyt.alexandre.hamigo.platform

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.Duration
import java.time.Instant
import java.util.UUID

/** A verified request. The author's identity comes exclusively from GitHub comment metadata. */
data class FriendRequest(
    val id: String,
    val senderGistId: String,
    val recipientGistId: String,
    val createdAt: String,
    val author: GitHubIdentity,
    val profile: ShareProgress,
    val commentId: Long
) {
    /** Namespacing prevents another valid account from copying a visible UUID to shadow a request. */
    val decisionKey: String get() = "$senderGistId:$id"
}

/** Persist this before sending, so retries after a timeout use the same UUID. */
data class OutgoingFriendRequest(
    val id: String,
    val senderGistId: String,
    val recipientGistId: String,
    val createdAt: String
)

interface FriendInboxGateway {
    suspend fun readIncoming(ownSocialGist: String, token: String, handledIds: Set<String> = emptySet()): List<FriendRequest>
    suspend fun sendRequest(ownSocialGist: String, recipientSocialGist: String, requestId: String, token: String): OutgoingFriendRequest
    suspend fun acknowledgeAccepted(request: FriendRequest, ownSocialGist: String, token: String)
    suspend fun acceptedOutgoing(ownSocialGist: String, outgoing: List<OutgoingFriendRequest>, token: String): Set<String>
}

/**
 * Gist comments are an unlisted, readable mailbox, not private messages. This transport never
 * changes either person's files or automatically accepts a relationship. Durable decisions belong
 * to the app's backup. All destinations are reconstructed on the fixed GitHub API origin.
 */
class FriendInbox(
    private val gateway: GitHubGateway = GitHubHttp,
    private val now: () -> Instant = Instant::now
) : FriendInboxGateway {
    /** At most 100 distinct, authenticated senders. Malformed/unrelated comments are ignored. */
    override suspend fun readIncoming(ownSocialGist: String, token: String, handledIds: Set<String>): List<FriendRequest> {
        val ownId = cleanGist(ownSocialGist)
        val me = currentIdentity(token)
        val own = socialGist(ownId, token)
        requireOwner(own, me)
        val candidates = comments(ownId, token).mapNotNull { comment ->
            parseComment(comment)?.takeIf {
                it.message.type == REQUEST && it.message.id !in handledIds &&
                    "${it.message.sender}:${it.message.id}" !in handledIds && it.message.recipient == ownId &&
                    it.message.sender != ownId && it.author.id != me.id
            }
        }.sortedWith(compareByDescending<ParsedComment> { it.message.createdAt }.thenBy { it.commentId })
            .distinctBy { it.author.id to it.message.sender }.take(MAX_PENDING)
        val profiles = mutableMapOf<String, SocialGist?>()
        val verified = mutableListOf<FriendRequest>()
        for (candidate in candidates) {
            val sender = candidate.message.sender
            val gist = if (profiles.containsKey(sender)) profiles[sender] else {
                val value = try { socialGist(sender, token) }
                catch (e: CancellationException) { throw e }
                catch (e: SocialException) { if (e.httpStatus == 404) null else throw e }
                catch (_: IllegalArgumentException) { null }
                profiles[sender] = value
                value
            } ?: continue
            if (gist.owner.id != candidate.author.id) continue
            verified += candidate.request(gist.progress)
        }
        return verified.distinctBy { it.author.id }
    }

    /** Caller must keep requestId stable across retries. A failed send does not remove a local friend. */
    override suspend fun sendRequest(
        ownSocialGist: String,
        recipientSocialGist: String,
        requestId: String,
        token: String
    ): OutgoingFriendRequest = sendLock.withLock {
        val ownId = cleanGist(ownSocialGist)
        val recipientId = cleanGist(recipientSocialGist)
        require(ownId != recipientId) { "Tu ne peux pas t’envoyer une demande." }
        val id = cleanUuid(requestId)
        val me = currentIdentity(token)
        requireOwner(socialGist(ownId, token), me)
        val recipient = socialGist(recipientId, token)
        require(recipient.owner.id != me.id) { "Ce Gist appartient déjà à ton compte." }
        // Scan all bounded pages before writing: a timeout can hide a successful previous POST.
        val existing = comments(recipientId, token).mapNotNull { parseComment(it, allowExpired = true) }.firstOrNull {
            it.author.id == me.id && it.message.id == id && it.message.type == REQUEST
        }
        if (existing != null) {
            require(recent(Instant.parse(existing.message.createdAt))) { "Cette demande a expiré. Crée une nouvelle demande." }
            require(existing.message.sender == ownId && existing.message.recipient == recipientId) {
                "L’identifiant de demande est déjà utilisé pour une autre invitation."
            }
            return@withLock existing.message.outgoing()
        }
        val message = Message(REQUEST, id, ownId, recipientId, now().toString())
        val posted = postComment(recipientId, token, message)
        require(posted.author.id == me.id && posted.message == message) {
            "GitHub a renvoyé une demande inattendue. Réessaie avec le même identifiant."
        }
        message.outgoing()
    }

    /** Publish only after the recipient has explicitly accepted and durably saved the friend. */
    override suspend fun acknowledgeAccepted(request: FriendRequest, ownSocialGist: String, token: String) = sendLock.withLock {
        val ownId = cleanGist(ownSocialGist)
        require(cleanGist(request.recipientGistId) == ownId)
        val senderId = cleanGist(request.senderGistId)
        val id = cleanUuid(request.id)
        require(request.commentId > 0)
        val me = currentIdentity(token)
        requireOwner(socialGist(ownId, token), me)
        require(senderId != ownId && request.author.id != me.id)
        val sender = socialGist(senderId, token)
        require(sender.owner.id == request.author.id) { "L’auteur de la demande a changé." }
        val original = parseComment(JSONObject(gateway.api("GET", "/gists/$ownId/comments/${request.commentId}", token)))
            ?: throw SocialException("La demande n’est plus disponible ou a expiré.")
        require(original.message.type == REQUEST && original.message.id == id &&
            original.message.sender == senderId && original.message.recipient == ownId &&
            original.author.id == sender.owner.id) { "La demande ne correspond plus à cette invitation." }
        if (comments(ownId, token).mapNotNull { parseComment(it) }.any {
            it.message.type == ACCEPTED && it.message.id == id && it.message.sender == senderId &&
                it.message.recipient == ownId && it.author.id == me.id
        }) return@withLock
        val message = Message(ACCEPTED, id, senderId, ownId, now().toString())
        val posted = postComment(ownId, token, message)
        require(posted.author.id == me.id && posted.message == message) { "Confirmation GitHub inattendue." }
    }

    /** ACKs are valid only when posted by the destination Gist's actual owner, never another commenter. */
    override suspend fun acceptedOutgoing(
        ownSocialGist: String,
        outgoing: List<OutgoingFriendRequest>,
        token: String
    ): Set<String> {
        require(outgoing.size <= MAX_PENDING) { "Trop de demandes en attente." }
        val ownId = cleanGist(ownSocialGist)
        val me = currentIdentity(token)
        requireOwner(socialGist(ownId, token), me)
        val valid = outgoing.filter {
            runCatching {
                cleanUuid(it.id)
                require(cleanGist(it.senderGistId) == ownId && cleanGist(it.recipientGistId) != ownId)
                require(recent(Instant.parse(it.createdAt)))
            }.isSuccess
        }.distinctBy { it.id }
        val accepted = mutableSetOf<String>()
        for ((destination, requests) in valid.groupBy { cleanGist(it.recipientGistId) }) {
            val owner = try { socialGist(destination, token).owner }
            catch (e: CancellationException) { throw e }
            catch (e: SocialException) { if (e.httpStatus == 404) continue else throw e }
            catch (_: IllegalArgumentException) { continue }
            if (owner.id == me.id) continue
            val ids = requests.associateBy { cleanUuid(it.id) }
            for (comment in comments(destination, token)) {
                val parsed = parseComment(comment) ?: continue
                val message = parsed.message
                if (message.type == ACCEPTED && parsed.author.id == owner.id &&
                    message.sender == ownId && message.recipient == destination && message.id in ids) {
                    // Sender and recipient clocks can differ. The verified owner, unique UUID and
                    // both Gist IDs bind the ACK; parseComment already checks server time and TTL.
                    accepted += message.id
                }
            }
        }
        return accepted
    }

    private suspend fun currentIdentity(token: String): GitHubIdentity {
        require(token.isNotBlank()) { "Connecte ton compte GitHub pour gérer les demandes." }
        return GitHubIdentity.fromApi(JSONObject(gateway.api("GET", "/user", token)))
            ?.takeIf { it.id != null } ?: throw SocialException("Impossible de vérifier ton identité GitHub.")
    }

    private data class SocialGist(val owner: GitHubIdentity, val progress: ShareProgress)
    private suspend fun socialGist(id: String, token: String): SocialGist {
        val gist = JSONObject(gateway.api("GET", "/gists/${cleanGist(id)}", token))
        require(gist.optString("id").equals(id, true)) { "Gist GitHub inattendu." }
        val owner = GitHubIdentity.fromApi(gist.optJSONObject("owner"))?.takeIf { it.id != null }
            ?: throw IllegalArgumentException("Le propriétaire GitHub ne peut pas être vérifié.")
        val file = gist.optJSONObject("files")?.optJSONObject(GitHubSync.FILE_NAME)
            ?: throw IllegalArgumentException("Ce Gist ne contient pas de progression Hamigo.")
        require(!file.optBoolean("truncated", false)) { "Progression Hamigo trop volumineuse." }
        require(file.opt("content") is String) { "Contenu du Gist social absent." }
        return SocialGist(owner, ShareProgress.fromJson(file.getString("content")))
    }

    private fun requireOwner(gist: SocialGist, identity: GitHubIdentity) {
        require(gist.owner.id == identity.id) { "Le Gist social n’appartient pas au compte connecté." }
    }

    private suspend fun comments(gist: String, token: String): List<JSONObject> {
        val result = mutableListOf<JSONObject>()
        for (page in 1..MAX_PAGES) {
            val content = gateway.api("GET", "/gists/${cleanGist(gist)}/comments?per_page=$PAGE_SIZE&page=$page", token)
            require(content.length <= 2 * 1024 * 1024) { "Boîte de réception trop volumineuse." }
            val array = JSONArray(content)
            require(array.length() <= PAGE_SIZE) { "Page de commentaires GitHub inattendue." }
            for (index in 0 until array.length()) array.optJSONObject(index)?.let(result::add)
            if (array.length() < PAGE_SIZE) return result
        }
        // Never claim a POST is safe to retry when older comments could contain the same UUID.
        throw SocialException("La boîte de réception atteint la limite de 1 000 commentaires. Les demandes restent conservées.")
    }

    private suspend fun postComment(gist: String, token: String, message: Message): ParsedComment {
        val body = JSONObject().put("body", message.encode()).toString()
        return parseComment(JSONObject(gateway.api("POST", "/gists/${cleanGist(gist)}/comments", token, body)))
            ?: throw SocialException("GitHub n’a pas confirmé la demande. Réessaie avec le même identifiant.")
    }

    private data class Message(val type: String, val id: String, val sender: String, val recipient: String, val createdAt: String) {
        fun outgoing() = OutgoingFriendRequest(id, sender, recipient, createdAt)
        fun encode() = PREFIX + JSONObject().put("app", APP).put("schema", 1).put("type", type)
            .put("id", id).put("senderGist", sender).put("recipientGist", recipient).put("createdAt", createdAt) + SUFFIX
    }
    private data class ParsedComment(val message: Message, val author: GitHubIdentity, val commentId: Long) {
        fun request(profile: ShareProgress) = FriendRequest(message.id, message.sender, message.recipient,
            message.createdAt, author, profile, commentId)
    }

    private fun parseComment(comment: JSONObject, allowExpired: Boolean = false): ParsedComment? = runCatching {
        val body = comment.opt("body") as? String ?: return null
        require(body.length <= 4096 && body.startsWith(PREFIX) && body.endsWith(SUFFIX))
        val data = JSONObject(body.substring(PREFIX.length, body.length - SUFFIX.length))
        require(data.keys().asSequence().toSet() == FIELDS && data.opt("app") == APP && data.opt("schema") == 1)
        require(listOf("type", "id", "senderGist", "recipientGist", "createdAt").all { data.opt(it) is String })
        val type = data.getString("type")
        require(type == REQUEST || type == ACCEPTED)
        val id = cleanUuid(data.getString("id"))
        val sender = cleanGist(data.getString("senderGist"))
        val recipient = cleanGist(data.getString("recipientGist"))
        require(sender != recipient)
        val created = Instant.parse(data.getString("createdAt"))
        val serverCreated = Instant.parse(comment.getString("created_at"))
        require((allowExpired || recent(created) && recent(serverCreated)) &&
            Duration.between(created, serverCreated).abs() <= Duration.ofMinutes(10))
        val author = GitHubIdentity.fromApi(comment.optJSONObject("user"))?.takeIf { it.id != null } ?: return null
        val rawId = comment.opt("id")
        require(rawId is Number && rawId.toDouble() % 1.0 == 0.0 && rawId.toDouble() in 1.0..9_007_199_254_740_991.0)
        ParsedComment(Message(type, id, sender, recipient, created.toString()), author, rawId.toLong())
    }.getOrNull()

    private fun recent(instant: Instant) = instant >= now().minus(Duration.ofDays(30)) && instant <= now().plusSeconds(300)
    private fun cleanGist(value: String): String {
        require(GIST_PATTERN.matches(value)) { "Identifiant de Gist invalide." }
        return value.lowercase(java.util.Locale.ROOT)
    }
    private fun cleanUuid(value: String): String {
        require(UUID_PATTERN.matches(value)) { "Identifiant de demande invalide." }
        return UUID.fromString(value).toString()
    }

    companion object {
        const val MAX_PENDING = 100
        private const val MAX_PAGES = 10
        private const val PAGE_SIZE = 100
        private const val REQUEST = "request"
        private const val ACCEPTED = "accepted"
        private const val APP = "hamigo-friends"
        private const val PREFIX = "Hamigo • demande d’ajout réciproque\n\n```json\n"
        private const val SUFFIX = "\n```"
        private val FIELDS = setOf("app", "schema", "type", "id", "senderGist", "recipientGist", "createdAt")
        private val GIST_PATTERN = Regex("[a-fA-F0-9]{5,64}")
        private val UUID_PATTERN = Regex("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}")
        private val sendLock = Mutex()
    }
}
